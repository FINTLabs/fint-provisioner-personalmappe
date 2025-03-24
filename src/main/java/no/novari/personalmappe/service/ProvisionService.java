package no.novari.personalmappe.service;

import com.mongodb.MongoBulkWriteException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.resource.FintLinks;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.arkiv.noark.AdministrativEnhetResource;
import no.fint.model.resource.arkiv.noark.AdministrativEnhetResources;
import no.fint.model.resource.arkiv.noark.ArkivressursResources;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import no.novari.personalmappe.exception.FinalStatusPendingException;
import no.novari.personalmappe.factory.PersonalmappeResourceFactory;
import no.novari.personalmappe.model.GraphQLPersonalmappe;
import no.novari.personalmappe.model.GraphQLQuery;
import no.novari.personalmappe.model.MongoDBPersonalmappe;
import no.novari.personalmappe.properties.OrganisationProperties;
import no.novari.personalmappe.repository.FintRepository;
import no.novari.personalmappe.repository.MongoDBRepository;
import no.novari.personalmappe.utilities.GraphQLUtilities;
import no.novari.personalmappe.utilities.PersonnelUtilities;
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
    @Value("${fint.endpoints.personnel-folder}")
    private URI personnelFolderEndpoint;

    @Value("${fint.endpoints.archive-resource}")
    private URI archiveResourceEndpoint;

    @Value("${fint.endpoints.administrative-unit}")
    private URI administrativeUnitEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    public static final String GRAPHQL_QUERY = GraphQLUtilities.getGraphQLQuery("personalressurs.graphql");

    @Getter
    private List<String> administrativeUnitSystemIds = new ArrayList<>();

    private final FintRepository fintRepository;
    private final ResponseService responseService;
    private final MongoDBRepository mongoDBRepository;
    private final PersonalmappeResourceFactory personalmappeResourceFactory;
    private final OrganisationProperties organisationProperties;
    private final PolicyService policyService;

    public ProvisionService(FintRepository fintRepository, ResponseService responseService, PersonalmappeResourceFactory personalmappeResourceFactory, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, PolicyService policyService) {
        this.fintRepository = fintRepository;
        this.responseService = responseService;
        this.personalmappeResourceFactory = personalmappeResourceFactory;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.policyService = policyService;
    }

    public void provisionOne(String username) {
        if (administrativeUnitSystemIds.isEmpty()) {
            updateAdministrativeUnitSystemIds();
        }

        run(Collections.singletonList(username), 1).subscribe(log::trace);
    }

    public Mono<PersonalmappeResource> getOne(String username) {
        if (administrativeUnitSystemIds.isEmpty()) {
            updateAdministrativeUnitSystemIds();
        }

        return getPersonnelFolder(username);
    }

    public Flux<String> run(List<String> usernames, long limit) {
        return Flux.fromIterable(usernames)
                .limitRequest(limit)
                .delayElements(Duration.ofMillis(1000))
                .concatMap(this::getPersonnelFolder)
                .flatMap(this::updatePersonnelFolder)
                .doOnNext(this::save)
                .map(MongoDBPersonalmappe::getUsername)
                .doOnComplete(() -> log.info("Provisioning of {} user(s) have now completed.", usernames.size()));
    }

    private Mono<PersonalmappeResource> getPersonnelFolder(String username) {
        GraphQLQuery graphQLQuery = new GraphQLQuery(GRAPHQL_QUERY, Collections.singletonMap("brukernavn", username));
        log.trace("Let´s get personal folder for {}", username);
        return fintRepository.post(GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                .map(graphQLPersonnelFolder -> Optional.ofNullable(graphQLPersonnelFolder.getResult())
                        .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                        .map(personnelResource -> personalmappeResourceFactory.toPersonalmappeResource(personnelResource, organisationProperties, administrativeUnitSystemIds))
                        .orElseGet(PersonalmappeResource::new))
                .filter(validPersonnelFolder())
                .onErrorResume(error -> {
                    log.error("Error getting personnel folder for {} with error message: {}", username, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<MongoDBPersonalmappe> updatePersonnelFolder(PersonalmappeResource personnelFolder) {
        log.debug("Update personal folder for {}", PersonnelUtilities.getUsername(personnelFolder));
        String orgId = organisationProperties.getOrgId();

        String id = orgId + "_" + PersonnelUtilities.getNIN(personnelFolder);

        Optional<MongoDBPersonalmappe> mongoDBPersonnelFolder = mongoDBRepository.findById(id);

        return mongoDBPersonnelFolder
                .map(dbPersonnelFolder -> update(personnelFolder, dbPersonnelFolder))
                .orElseGet(() -> create(orgId, id, personnelFolder))
                .onErrorResume(throwable -> Mono.empty());
    }

    private Mono<MongoDBPersonalmappe> create(String orgId, String id, PersonalmappeResource personnelFolder) {
        log.debug("Create personal folder for {}", PersonnelUtilities.getUsername(personnelFolder));
        doTransformation(personnelFolder);

        return fintRepository.postForEntity(personnelFolder, personnelFolderEndpoint)
                .flatMap(responseEntity -> {
                    MongoDBPersonalmappe mongoDBPersonalmappe = responseService.pending(orgId, id, personnelFolder);

                    return status(mongoDBPersonalmappe, responseEntity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("Error creating personnel folder for {} with error message: {}", PersonnelUtilities.getUsername(personnelFolder), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> update(PersonalmappeResource personnelFolder, MongoDBPersonalmappe mongoDBPersonnelFolder) {
        doTransformation(personnelFolder);

        Mono<ResponseEntity<Void>> responseEntity;

        if (mongoDBPersonnelFolder.getAssociation() == null) {
            responseEntity = fintRepository.postForEntity(personnelFolder, personnelFolderEndpoint);
        } else {
            responseEntity = fintRepository.putForEntity(personnelFolder, mongoDBPersonnelFolder.getAssociation());
        }

        return responseEntity
                .flatMap(entity -> {
                    MongoDBPersonalmappe dbPersonalmappe = responseService.pending(mongoDBPersonnelFolder, personnelFolder);

                    return status(dbPersonalmappe, entity);
                })
                .doOnError(WebClientResponseException.class, clientResponse -> log.error("Error updating personnel folder for {} with error message: {}", PersonnelUtilities.getUsername(personnelFolder), clientResponse.getMessage()));
    }

    private Mono<MongoDBPersonalmappe> status(MongoDBPersonalmappe mongoDBPersonnelFolder, ResponseEntity<Void> responseEntity) {
        return fintRepository.getForEntity(Object.class, responseEntity.getHeaders().getLocation())
                .map(entity -> {
                    if (entity.getStatusCode().equals(HttpStatus.ACCEPTED)) {
                        throw new FinalStatusPendingException();
                    }

                    return responseService.success(mongoDBPersonnelFolder, entity);
                })
                .retryWhen(Retry.backoff(10, Duration.ofSeconds(1))
                        .filter(FinalStatusPendingException.class::isInstance)
                        .doAfterRetry(exception -> log.info("{}", exception)))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.just(responseService.error(ex, mongoDBPersonnelFolder)));
    }

    private Predicate<PersonalmappeResource> validPersonnelFolder() {
        log.debug("Validate personal folder...");
        return personnelFolder -> {
            if (Objects.nonNull(personnelFolder.getNavn()) &&
                    !personnelFolder.getPersonalressurs().isEmpty() &&
                    !personnelFolder.getPerson().isEmpty() &&
                    !personnelFolder.getArbeidssted().isEmpty() &&
                    !personnelFolder.getLeder().isEmpty()) {
                log.debug("...personal folder is OK.");
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
            log.trace(" ..invalid personal folder.");
            return false;
        };
    }

    private void save(MongoDBPersonalmappe mongoDBPersonnelFolder) {
        try {
            mongoDBRepository.save(mongoDBPersonnelFolder);
        } catch (OptimisticLockingFailureException | MongoBulkWriteException e) {
            log.error("Error saving to database {} -> {}", e.getMessage(), mongoDBPersonnelFolder);
        }
    }

    public void doTransformation(PersonalmappeResource personalmappeResource) {
        final List<String> transformationScripts = organisationProperties.getTransformationScripts();
        if (transformationScripts != null) {
            transformationScripts.forEach(script -> policyService.transform(script, personalmappeResource));
        }
    }

    public void updateArchiveResource(List<PersonalressursResource> personalressursList) {
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

    public void updateAdministrativeUnitSystemIds() {
        fintRepository.get(AdministrativEnhetResources.class, administrativeUnitEndpoint)
                .flatMapIterable(AdministrativEnhetResources::getContent)
                .flatMapIterable(AdministrativEnhetResource::getOrganisasjonselement)
                .map(Link::getHref)
                .map(id -> StringUtils.substringAfterLast(id, "/"))
                .collectList()
                .filter(ids -> !ids.isEmpty())
                .subscribe(systemIds -> {
                    if (systemIds.isEmpty()) {
                        throw new IllegalArgumentException("No administrativ enhet found");
                    }

                    administrativeUnitSystemIds = systemIds;
                });
    }

    public List<String> getUsernames(List<PersonalressursResource> personnelResources) {
        return personnelResources
                .stream()
                .map(PersonalressursResource::getBrukernavn)
                .filter(Objects::nonNull)
                .map(Identifikator::getIdentifikatorverdi)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }
}