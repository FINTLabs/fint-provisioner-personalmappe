package no.fint.personalmappe.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mongodb.MongoBulkWriteException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.resource.FintLinks;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.arkiv.AdministrativEnhetResource;
import no.fint.model.resource.administrasjon.arkiv.AdministrativEnhetResources;
import no.fint.model.resource.administrasjon.arkiv.ArkivressursResources;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.exception.FinalStatusPendingException;
import no.fint.personalmappe.factory.PersonalmappeResourceFactory;
import no.fint.personalmappe.model.GraphQLPersonalmappe;
import no.fint.personalmappe.model.GraphQLQuery;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.utilities.GraphQLUtilities;
import no.fint.personalmappe.utilities.PersonnelUtilities;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProvisionService {
    @Value("${fint.endpoints.personalressurs}")
    private URI personalressursEndpoint;

    @Value("${fint.endpoints.personalmappe}")
    private URI personalmappeEndpoint;

    @Value("${fint.endpoints.arkivressurs}")
    private URI arkivressursEndpoint;

    @Value("${fint.endpoints.administrativ-enhet}")
    private URI administrativEnhetEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    public static final String GRAPHQL_QUERY = GraphQLUtilities.getGraphQLQuery("personalressurs.graphql");

    @Getter
    private final Multimap<String, String> administrativeEnheter = ArrayListMultimap.create();

    private final FintRepository fintRepository;
    private final ResponseHandlerService responseHandlerService;
    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;
    private final PersonalmappeResourceFactory personalmappeResourceFactory;
    private final PolicyService policyService;

    public ProvisionService(FintRepository fintRepository, ResponseHandlerService responseHandlerService, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, PersonalmappeResourceFactory personalmappeResourceFactory, PolicyService policyService) {
        this.fintRepository = fintRepository;
        this.responseHandlerService = responseHandlerService;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.personalmappeResourceFactory = personalmappeResourceFactory;
        this.policyService = policyService;
    }

    public void bulkProvisionByOrgId(String orgId, int limit) {
        Mono<PersonalressursResources> personalressursResources = fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint);
        Mono<AdministrativEnhetResources> administrativEnhetResources = fintRepository.get(orgId, AdministrativEnhetResources.class, administrativEnhetEndpoint);

        provisionByOrgId(orgId, limit, personalressursResources, administrativEnhetResources);
    }

    public void deltaProvisionByOrgId(String orgId) {
        Mono<PersonalressursResources> personalressursResources = fintRepository.getUpdates(orgId, PersonalressursResources.class, personalressursEndpoint);
        Mono<AdministrativEnhetResources> administrativEnhetResources = fintRepository.getUpdates(orgId, AdministrativEnhetResources.class, administrativEnhetEndpoint);

        provisionByOrgId(orgId, 0, personalressursResources, administrativEnhetResources);
    }

    private void provisionByOrgId(String orgId, int limit, Mono<PersonalressursResources> personalressursResourcesMono, Mono<AdministrativEnhetResources> administrativEnhetResourcesMono) {
        final OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        if (organisation == null) {
            log.error("No configuration for {}", orgId);
            return;
        }

        List<AdministrativEnhetResource> administrativEnhetResources = administrativEnhetResourcesMono
                .flatMapIterable(AdministrativEnhetResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        administrativEnhetResources.stream()
                .map(AdministrativEnhetResource::getSystemId)
                .map(Identifikator::getIdentifikatorverdi)
                .forEach(id -> administrativeEnheter.put(orgId, id));

        List<PersonalressursResource> personalressursResources = personalressursResourcesMono
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        if (organisation.isArkivressurs()) {
            log.info("{}: Updating Arkivressurs objects...", orgId);
            updateArkivressurs(orgId, personalressursResources);
        }

        List<String> usernames = personalressursResources
                .stream()
                .map(PersonalressursResource::getBrukernavn)
                .filter(Objects::nonNull)
                .map(Identifikator::getIdentifikatorverdi)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        log.trace("Start provisioning {} of {} users", (limit == 0 ? usernames.size() : limit), usernames.size());

        Flux.fromIterable(usernames)
                .limitRequest(limit == 0 ? usernames.size() : limit)
                .delayElements(Duration.ofMillis(1000))
                .flatMap(username -> getPersonalmappeResource(orgId, username))
                .subscribe(personalmappeResource -> provision(orgId, personalmappeResource));
    }

    private void updateArkivressurs(String orgId, List<PersonalressursResource> personalressursList) {
        final Set<String> selfLinks = personalressursList.stream().map(FintLinks::getSelfLinks).flatMap(List::stream).map(Link::getHref).collect(Collectors.toSet());
        fintRepository.get(orgId, ArkivressursResources.class, arkivressursEndpoint)
                .flatMapMany(r -> Flux.fromStream(r.getContent().stream()))
                .filter(a -> a.getPersonalressurs().stream().map(Link::getHref).anyMatch(selfLinks::contains))
                .flatMap(arkivressurs ->
                        arkivressurs
                                .getSelfLinks()
                                .stream()
                                .map(Link::getHref)
                                .filter(StringUtils::isNotBlank)
                                .map(s -> UriComponentsBuilder.fromUriString(s).build().toUri())
                                .findAny()
                                .map(uri -> fintRepository.putForEntity(orgId, arkivressurs, uri))
                                .orElseGet(Mono::empty))
                .onErrorContinue((e, r) -> log.info("{}: Error on {}", orgId, r))
                .handle(transformNullable(r -> r.getHeaders().getLocation()))
                .delayElements(Duration.ofSeconds(10))
                .flatMap(uri -> fintRepository.headForEntity(orgId, uri))
                .onErrorContinue((e, r) -> log.info("{}: Error on {}", orgId, r))
                .doOnNext(it -> log.info("{}: Arkivressurs: {} {}", orgId, it.getStatusCode(), it.getHeaders().getLocation()))
                .count()
                .subscribe(it -> log.info("{}: Updated {} Arkivressurs objects.", orgId, it));
    }

    public static <T, U> BiConsumer<U, SynchronousSink<T>> transformNullable(Function<U, T> mapper) {
        return (element, sink) -> Optional.ofNullable(mapper.apply(element)).ifPresent(sink::next);
    }

    public Mono<PersonalmappeResource> getPersonalmappeResource(String orgId, String username) {
        GraphQLQuery graphQLQuery = new GraphQLQuery(GRAPHQL_QUERY, Collections.singletonMap("brukernavn", username));

        return fintRepository.post(orgId, GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                .onErrorResume(error -> {
                    log.error("{} - {}", username, error.getMessage());
                    return Mono.empty();
                })
                .map(GraphQLPersonalmappe::getResult)
                .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                .flatMapIterable(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold)
                .onErrorResume(error -> {
                    log.error("{} - {}", username, error.getMessage());
                    return Flux.empty();
                })
                .flatMap(arbeidsforhold -> personalmappeResourceFactory.toPersonalmappeResource(orgId, arbeidsforhold, administrativeEnheter.get(orgId)))
                .singleOrEmpty();
    }

    public void provision(String orgId, PersonalmappeResource personalmappeResource) {
        String id = orgId + "_" + PersonnelUtilities.getNIN(personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        mongoDBPersonalmappe
                .map(dbPersonalmappe -> update(orgId, personalmappeResource, dbPersonalmappe))
                .orElseGet(() -> create(orgId, id, personalmappeResource))
                .onErrorResume(throwable -> Mono.empty())
                .doOnNext(this::save)
                .subscribe(dbPersonalmappe -> log.trace(dbPersonalmappe.getUsername()));
    }

    public void doTransformation(String orgId, PersonalmappeResource personalmappeResource) {
        if (organisationProperties.getOrganisations().containsKey(orgId)) {
            final List<String> transformationScripts = organisationProperties.getOrganisations().get(orgId).getTransformationScripts();
            if (transformationScripts != null) {
                transformationScripts.forEach(script -> policyService.transform(script, personalmappeResource));
            }
        }
    }

    private Mono<MongoDBPersonalmappe> create(String orgId, String id, PersonalmappeResource personalmappeResource) {
        doTransformation(orgId, personalmappeResource);

        return fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint)
                .flatMap(responseEntity -> {
                    MongoDBPersonalmappe mongoDBPersonalmappe = responseHandlerService.pendingHandler(orgId, id, personalmappeResource);

                    return status(orgId, mongoDBPersonalmappe, responseEntity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("{} - {}", PersonnelUtilities.getUsername(personalmappeResource), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> update(String orgId, PersonalmappeResource personalmappeResource, MongoDBPersonalmappe mongoDBPersonalmappe) {
        doTransformation(orgId, personalmappeResource);

        Mono<ResponseEntity<Void>> responseEntity;

        if (mongoDBPersonalmappe.getAssociation() == null) {
            responseEntity = fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint);
        } else {
            responseEntity = fintRepository.putForEntity(orgId, personalmappeResource, mongoDBPersonalmappe.getAssociation());
        }

        return responseEntity
                .flatMap(entity -> {
                    MongoDBPersonalmappe dbPersonalmappe = responseHandlerService.pendingHandler(mongoDBPersonalmappe, personalmappeResource);

                    return status(orgId, dbPersonalmappe, entity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("{} - {}", PersonnelUtilities.getUsername(personalmappeResource), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> status(String orgId, MongoDBPersonalmappe mongoDBPersonalmappe, ResponseEntity<Void> responseEntity) {
        return fintRepository.getForEntity(orgId, Object.class, responseEntity.getHeaders().getLocation())
                .map(entity -> {
                    if (entity.getStatusCode().is3xxRedirection()) {
                        return responseHandlerService.successHandler(mongoDBPersonalmappe, entity);
                    } else {
                        throw new FinalStatusPendingException();
                    }
                })
                .retryWhen(Retry.withThrowable(responseHandlerService.getFinalStatusPending()))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.just(responseHandlerService.errorHandler(ex, mongoDBPersonalmappe)))
                .onErrorResume(ex -> Mono.just(mongoDBPersonalmappe));
    }

    private void save(MongoDBPersonalmappe mongoDBPersonalmappe) {
        try {
            mongoDBRepository.save(mongoDBPersonalmappe);
        } catch (OptimisticLockingFailureException | MongoBulkWriteException e) {
            log.error("{} -> {}", e.getMessage(), mongoDBPersonalmappe);
        }
    }
}
