package no.fint.personalmappe.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
import no.fint.personalmappe.factory.PersonalmappeResourceFactory;
import no.fint.personalmappe.model.GraphQLPersonalmappe;
import no.fint.personalmappe.model.GraphQLQuery;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.utilities.GraphQLUtilities;
import no.fint.personalmappe.utilities.NINUtilities;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProvisionService {

    @Value("${fint.endpoints.personalmappe}")
    private URI personalmappeEndpoint;

    @Value("${fint.endpoints.arkivressurs}")
    private URI arkivressursEndpoint;

    @Value("${fint.endpoints.administrativ-enhet}")
    private URI administrativEnhetEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    private static final String GRAPHQL_QUERY = GraphQLUtilities.getGraphQLQuery("personalressurs.graphql");

    private final Multimap<String, String> administrativeEnheter = ArrayListMultimap.create();

    private final FintRepository fintRepository;
    private final MongoDBRepository mongoDBRepository;
    private final ResponseHandlerService responseHandlerService;
    private final OrganisationProperties organisationProperties;
    private final PolicyService policyService;

    public ProvisionService(FintRepository fintRepository, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, ResponseHandlerService responseHandlerService, PolicyService policyService) {
        this.fintRepository = fintRepository;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.responseHandlerService = responseHandlerService;
        this.policyService = policyService;
    }

    public void provisionByOrgId(String orgId, int limit, Mono<PersonalressursResources> personalressursResources) {
        final OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);
        if (organisation == null) {
            log.error("No configuration for {}", orgId);
            return;
        }

        if (administrativeEnheter.get(orgId).isEmpty()) {
            updateAdministrativeEnheter(orgId);
        }

        final List<PersonalressursResource> personalressursList = personalressursResources
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        if (organisation.isArkivressurs()) {
            log.info("{}: Updating Arkivressurs objects...", orgId);
            updateArkivressurs(orgId, personalressursList);
        }

        List<String> usernames = personalressursList
                .stream()
                .map(PersonalressursResource::getBrukernavn)
                .filter(Objects::nonNull)
                .map(Identifikator::getIdentifikatorverdi)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        log.trace("Start provisioning {} of {} users", (limit == 0 ? usernames.size() : limit), usernames.size());

        Flux.fromIterable(usernames)
                .limitRequest(limit == 0 ? usernames.size() : limit)
                .delayElements(Duration.ofMillis(500))
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
                .onErrorContinue((e,r) -> log.info("{}: Error on {}", orgId, r))
                .handle(transformNullable(r -> r.getHeaders().getLocation()))
                .delayElements(Duration.ofSeconds(10))
                .flatMap(uri -> fintRepository.headForEntity(orgId, uri))
                .onErrorContinue((e,r) -> log.info("{}: Error on {}", orgId, r))
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
                .onErrorResume(it -> {
                    log.error("{} - {}", username, it.getMessage());
                    return Mono.empty();
                })
                .map(GraphQLPersonalmappe::getResult)
                .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                .flatMapIterable(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold)
                .onErrorResume(it -> Flux.empty())
                .filter(isActive(LocalDateTime.now()).and(isHovedstilling()).and(hasPersonalressurskategori(orgId)))
                .map(arbeidsforhold -> Optional.ofNullable(arbeidsforhold)
                        .map(GraphQLPersonalmappe.Arbeidsforhold::getArbeidssted)
                        .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                        .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi)
                        .map(id -> PersonalmappeResourceFactory.toPersonalmappeResource(arbeidsforhold, administrativeEnheter.get(orgId).contains(id)))
                        .orElseGet(PersonalmappeResource::new))
                .filter(hasMandatoryFieldsAndRelations().and(hasValidLeader()))
                .singleOrEmpty()
                .doOnNext(it -> log.trace(username));
    }

    public void provision(String orgId, PersonalmappeResource personalmappeResource) {
        String id = orgId + "_" + NINUtilities.getNIN(personalmappeResource);

        String username = getUsername(personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        if (mongoDBPersonalmappe.isPresent()) {
            if (mongoDBPersonalmappe.get().getAssociation() == null) {
                onFailedCreate(orgId, personalmappeResource, mongoDBPersonalmappe.get());
            } else {
                onUpdate(orgId, personalmappeResource, mongoDBPersonalmappe.get());
            }
        } else {
            onCreate(orgId, personalmappeResource, id, username);
        }
    }

    public void doTransformation(String orgId, PersonalmappeResource personalmappeResource) {
        if (organisationProperties.getOrganisations().containsKey(orgId)) {
            final List<String> transformationScripts = organisationProperties.getOrganisations().get(orgId).getTransformationScripts();
            if (transformationScripts != null) {
                transformationScripts.forEach(script -> policyService.transform(script, personalmappeResource));
            }
        }
    }

    private void onCreate(String orgId, PersonalmappeResource personalmappeResource, String id, String username) {
        doTransformation(orgId, personalmappeResource);
        fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint)
                .doOnSuccess(responseEntity -> {
                    MongoDBPersonalmappe mongoDBPersonalmappe = responseHandlerService.handleStatusOnNew(orgId, id, username);
                    getForResource(mongoDBPersonalmappe, responseEntity.getHeaders().getLocation());
                })
                .subscribe();
    }

    private void onFailedCreate(String orgId, PersonalmappeResource personalmappeResource, MongoDBPersonalmappe mongoDBPersonalmappe) {
        fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint)
                .doOnSuccess(responseEntity -> {
                    responseHandlerService.handleStatus(mongoDBPersonalmappe);
                    getForResource(mongoDBPersonalmappe, responseEntity.getHeaders().getLocation());
                })
                .subscribe();
    }

    private void onUpdate(String orgId, PersonalmappeResource personalmappeResource, MongoDBPersonalmappe mongoDBPersonalmappe) {
        doTransformation(orgId, personalmappeResource);
        fintRepository.putForEntity(orgId, personalmappeResource, mongoDBPersonalmappe.getAssociation())
                .doOnSuccess(responseEntity -> {
                    responseHandlerService.handleStatus(mongoDBPersonalmappe);
                    getForResource(mongoDBPersonalmappe, responseEntity.getHeaders().getLocation());
                })
                .subscribe();
    }

    private void getForResource(MongoDBPersonalmappe mongoDBPersonalmappe, URI location) {
        fintRepository.getForEntity(mongoDBPersonalmappe.getOrgId(), Object.class, location)
                .doOnSuccess(responseEntity -> responseHandlerService.handleResource(responseEntity, mongoDBPersonalmappe))
                .doOnError(WebClientResponseException.class,
                        clientResponse -> responseHandlerService.handleError(clientResponse, mongoDBPersonalmappe))
                .retryWhen(responseHandlerService.finalStatusPending)
                .subscribe();
    }

    public Predicate<GraphQLPersonalmappe.Arbeidsforhold> isActive(LocalDateTime now) {
        return arbeidsforhold -> {
            if (arbeidsforhold == null || arbeidsforhold.getGyldighetsperiode() == null) return false;

            if (arbeidsforhold.getGyldighetsperiode().getSlutt() == null) {
                return now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            } else {
                return now.isBefore(arbeidsforhold.getGyldighetsperiode().getSlutt())
                        && now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            }
        };
    }

    public Predicate<GraphQLPersonalmappe.Arbeidsforhold> isHovedstilling() {
        return arbeidsforhold -> Optional.ofNullable(arbeidsforhold)
                .map(GraphQLPersonalmappe.Arbeidsforhold::getHovedstilling)
                .orElse(false);
    }

    public Predicate<GraphQLPersonalmappe.Arbeidsforhold> hasPersonalressurskategori(String orgId) {
        return arbeidsforhold -> organisationProperties.getOrganisations().get(orgId).getPersonalressurskategori()
                .contains(Optional.ofNullable(arbeidsforhold)
                        .map(GraphQLPersonalmappe.Arbeidsforhold::getPersonalressurs)
                        .map(GraphQLPersonalmappe.Personalressurs::getPersonalressurskategori)
                        .map(GraphQLPersonalmappe.Personalressurskategori::getKode)
                        .orElse(null));
    }

    public Predicate<PersonalmappeResource> hasMandatoryFieldsAndRelations() {
        return personalmappeResource -> (!personalmappeResource.getPersonalressurs().isEmpty()
                && !personalmappeResource.getPerson().isEmpty()
                && !personalmappeResource.getArbeidssted().isEmpty()
                && !personalmappeResource.getLeder().isEmpty()
                && Objects.nonNull(personalmappeResource.getNavn()));
    }

    public Predicate<PersonalmappeResource> hasValidLeader() {
        return personalmappeResource -> {
            if (personalmappeResource.getPersonalressurs().contains(personalmappeResource.getLeder().stream().findAny().orElse(null))) {
                log.trace("Identical subject and leader for personalmappe: {}", getUsername(personalmappeResource));
                return false;
            }
            return true;
        };
    }

    private String getUsername(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getPersonalressurs().stream()
                .map(Link::getHref)
                .map(href -> StringUtils.substringAfterLast(href, "/"))
                .findAny()
                .orElse(null);
    }

    public void updateAdministrativeEnheter(String orgId) {
        if (administrativeEnheter.containsKey(orgId)) {
            administrativeEnheter.get(orgId).clear();
        }

        fintRepository.get(orgId, AdministrativEnhetResources.class, administrativEnhetEndpoint)
                .flatMapIterable(AdministrativEnhetResources::getContent)
                .toStream()
                .map(AdministrativEnhetResource::getSystemId)
                .map(Identifikator::getIdentifikatorverdi)
                .forEach(id -> administrativeEnheter.put(orgId, id));
    }
}
