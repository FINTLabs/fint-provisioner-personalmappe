package no.fint.personalmappe.service;

import com.mongodb.MongoBulkWriteException;
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
import org.springframework.http.HttpStatus;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProvisionService {
    @Value("${fint.endpoints.personnel-resource}")
    private URI personnelResourceEndpoint;

    @Value("${fint.endpoints.personnel-folder}")
    private URI personnelFolderEndpoint;

    @Value("${fint.endpoints.archive-resource}")
    private URI archiveResourceEndpoint;

    @Value("${fint.endpoints.administrative-unit}")
    private URI administrativeUnitEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    public static final String GRAPHQL_QUERY = GraphQLUtilities.getGraphQLQuery("personalressurs.graphql");

    private List<String> administrativeUnitSystemIds = new ArrayList<>();

    private final FintRepository fintRepository;
    private final ResponseHandlerService responseHandlerService;
    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;
    private final PolicyService policyService;

    public ProvisionService(FintRepository fintRepository, ResponseHandlerService responseHandlerService, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, PolicyService policyService) {
        this.fintRepository = fintRepository;
        this.responseHandlerService = responseHandlerService;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.policyService = policyService;
    }

    public void bulk(int bulkLimit) {
        updateAdministrativeUnitSystemIds();

        List<PersonalressursResource> personnelResources = fintRepository.get(PersonalressursResources.class, personnelResourceEndpoint)
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        if (organisationProperties.isArchiveResource()) {
            log.info("Updating Archive resources...");

            updateArchiveResource(personnelResources);
        }

        List<String> usernames = getUsernames(personnelResources);

        int limit = (bulkLimit == 0 ? usernames.size() : bulkLimit);

        log.info("Bulk provision {} of {} users", limit, usernames.size());

        Flux.fromIterable(usernames)
                .limitRequest(limit)
                .delayElements(Duration.ofMillis(1000))
                .flatMap(this::getPersonalmappeResource)
                .subscribe(this::provision);
    }

    public void delta() {
        if (administrativeUnitSystemIds.isEmpty()) {
            updateAdministrativeUnitSystemIds();
        }

        List<PersonalressursResource> personnelResources = fintRepository.getUpdates(PersonalressursResources.class, personnelResourceEndpoint)
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        List<String> usernames = getUsernames(personnelResources);

        log.info("Delta provision {} users", usernames.size());

        Flux.fromIterable(usernames)
                .delayElements(Duration.ofMillis(1000))
                .flatMap(this::getPersonalmappeResource)
                .subscribe(this::provision);
    }

    public void single(String username) {
        if (administrativeUnitSystemIds.isEmpty()) {
            updateAdministrativeUnitSystemIds();
        }

        getPersonalmappeResource(username).subscribe(this::provision);
    }

    public Mono<PersonalmappeResource> getPersonalmappeResource(String username) {
        GraphQLQuery graphQLQuery = new GraphQLQuery(GRAPHQL_QUERY, Collections.singletonMap("brukernavn", username));

        return fintRepository.post(GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                .map(graphQLPersonnelFolder -> Optional.ofNullable(graphQLPersonnelFolder.getResult())
                        .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                        .map(personnelResource -> PersonalmappeResourceFactory.toPersonalmappeResource(personnelResource, organisationProperties, administrativeUnitSystemIds))
                        .orElseGet(PersonalmappeResource::new))
                .filter(validPersonnelFolder())
                .onErrorResume(error -> {
                    log.error("{} - {}", username, error.getMessage());
                    return Mono.empty();
                });
    }

    public void provision(PersonalmappeResource personalmappeResource) {
        String id = organisationProperties.getOrgId() + "_" + PersonnelUtilities.getNIN(personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        mongoDBPersonalmappe
                .map(dbPersonalmappe -> update(personalmappeResource, dbPersonalmappe))
                .orElseGet(() -> create(id, personalmappeResource))
                .onErrorResume(throwable -> Mono.empty())
                .doOnNext(this::save)
                .map(MongoDBPersonalmappe::getUsername)
                .subscribe(log::trace);
    }

    private Mono<MongoDBPersonalmappe> create(String id, PersonalmappeResource personalmappeResource) {
        doTransformation(personalmappeResource);

        return fintRepository.postForEntity(personalmappeResource, personnelFolderEndpoint)
                .flatMap(responseEntity -> {
                    MongoDBPersonalmappe mongoDBPersonalmappe = responseHandlerService.pendingHandler(organisationProperties.getOrgId(), id, personalmappeResource);

                    return status(mongoDBPersonalmappe, responseEntity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("{} - {}", PersonnelUtilities.getUsername(personalmappeResource), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> update(PersonalmappeResource personalmappeResource, MongoDBPersonalmappe mongoDBPersonalmappe) {
        doTransformation(personalmappeResource);

        Mono<ResponseEntity<Void>> responseEntity;

        if (mongoDBPersonalmappe.getAssociation() == null) {
            responseEntity = fintRepository.postForEntity(personalmappeResource, personnelFolderEndpoint);
        } else {
            responseEntity = fintRepository.putForEntity(personalmappeResource, mongoDBPersonalmappe.getAssociation());
        }

        return responseEntity
                .flatMap(entity -> {
                    MongoDBPersonalmappe dbPersonalmappe = responseHandlerService.pendingHandler(mongoDBPersonalmappe, personalmappeResource);

                    return status(dbPersonalmappe, entity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("{} - {}", PersonnelUtilities.getUsername(personalmappeResource), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> status(MongoDBPersonalmappe mongoDBPersonalmappe, ResponseEntity<Void> responseEntity) {
        return fintRepository.getForEntity(Object.class, responseEntity.getHeaders().getLocation())
                .map(entity -> {
                    if (entity.getStatusCode().equals(HttpStatus.ACCEPTED)) {
                        throw new FinalStatusPendingException();
                    }

                    return responseHandlerService.successHandler(mongoDBPersonalmappe, entity);
                })
                .retryWhen(Retry.withThrowable(responseHandlerService.getFinalStatusPending()))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.just(responseHandlerService.errorHandler(ex, mongoDBPersonalmappe)));
    }

    public Predicate<PersonalmappeResource> validPersonnelFolder() {
        return personnelFolder -> {
            if (Objects.nonNull(personnelFolder.getNavn()) &&
                    !personnelFolder.getPersonalressurs().isEmpty() &&
                    !personnelFolder.getPerson().isEmpty() &&
                    !personnelFolder.getArbeidssted().isEmpty() &&
                    !personnelFolder.getLeder().isEmpty()) {

                Optional<Link> identical = personnelFolder.getPersonalressurs().stream()
                        .filter(link -> personnelFolder.getLeder().contains(link))
                        .findAny();

                if (identical.isPresent()) {
                    log.trace("Identical subject and leader for personnel folder: {}", PersonnelUtilities.getUsername(personnelFolder));

                    return false;
                }

                Set<String> excluded = Arrays.stream(organisationProperties.getAdministrativeUnitsExcluded()).collect(Collectors.toSet());

                return excluded.isEmpty() || personnelFolder.getArbeidssted().stream()
                        .map(Link::getHref)
                        .map(href -> StringUtils.substringAfterLast(href, "/"))
                        .noneMatch(excluded::contains);
            }

            return false;
        };
    }

    private void save(MongoDBPersonalmappe mongoDBPersonalmappe) {
        try {
            mongoDBRepository.save(mongoDBPersonalmappe);
        } catch (OptimisticLockingFailureException | MongoBulkWriteException e) {
            log.error("{} -> {}", e.getMessage(), mongoDBPersonalmappe);
        }
    }

    public void doTransformation(PersonalmappeResource personalmappeResource) {
        final List<String> transformationScripts = organisationProperties.getTransformationScripts();
        if (transformationScripts != null) {
            transformationScripts.forEach(script -> policyService.transform(script, personalmappeResource));
        }
    }

    private void updateArchiveResource(List<PersonalressursResource> personalressursList) {
        final Set<String> selfLinks = personalressursList.stream().map(FintLinks::getSelfLinks).flatMap(List::stream).map(Link::getHref).collect(Collectors.toSet());
        fintRepository.get(ArkivressursResources.class, archiveResourceEndpoint)
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
                                .map(uri -> fintRepository.putForEntity(arkivressurs, uri))
                                .orElseGet(Mono::empty))
                .onErrorContinue((e, r) -> log.info("Error on {}", r))
                .handle(transformNullable(r -> r.getHeaders().getLocation()))
                .delayElements(Duration.ofSeconds(10))
                .flatMap(fintRepository::headForEntity)
                .onErrorContinue((e, r) -> log.info("Error on {}", r))
                .doOnNext(it -> log.info("Arkivressurs: {} {}", it.getStatusCode(), it.getHeaders().getLocation()))
                .count()
                .subscribe(it -> log.info("Updated {} Arkivressurs objects.", it));
    }

    public static <T, U> BiConsumer<U, SynchronousSink<T>> transformNullable(Function<U, T> mapper) {
        return (element, sink) -> Optional.ofNullable(mapper.apply(element)).ifPresent(sink::next);
    }

    private void updateAdministrativeUnitSystemIds() {
        administrativeUnitSystemIds = fintRepository.get(AdministrativEnhetResources.class, administrativeUnitEndpoint)
                .flatMapIterable(AdministrativEnhetResources::getContent)
                .map(AdministrativEnhetResource::getSystemId)
                .map(Identifikator::getIdentifikatorverdi)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);
    }

    private List<String> getUsernames(List<PersonalressursResource> personnelResources) {
        return personnelResources
                .stream()
                .map(PersonalressursResource::getBrukernavn)
                .filter(Objects::nonNull)
                .map(Identifikator::getIdentifikatorverdi)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }
}
