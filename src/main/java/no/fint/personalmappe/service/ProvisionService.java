package no.fint.personalmappe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResources;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.factory.PersonalmappeResourceFactory;
import no.fint.personalmappe.exception.FinalStatusPendingException;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.util.Util;
import no.fint.personalmappe.model.GraphQLPersonalmappe;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.model.GraphQLQuery;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProvisionService {

    @Value("${fint.endpoints.personalressurs}")
    private URI personalressursEndpoint;

    @Value("${fint.endpoints.personalmappe}")
    private URI personalmappeEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    private final FintRepository fintRepository;
    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;

    private static final GraphQLQuery graphQLQuery = Util.getGraphQLQuery("personalressurs.graphql");

    @Setter
    private static int LIMIT = 20;

    public ProvisionService(FintRepository fintRepository, MongoDBRepository mongoDBRepository, OrganisationProperties organisationProperties) {
        this.fintRepository = fintRepository;
        this.mongoDBRepository = mongoDBRepository;
        this.organisationProperties = organisationProperties;
    }

    //@Scheduled(cron = "0 23 * * * MON-FRI")
    public void runFull() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    log.info("({}) start full provisioning", orgId);

                    fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint)
                            .blockOptional()
                            .ifPresent(personalressursResources -> {
                                log.info("({}) found {} personalressurser", orgId, personalressursResources.getTotalItems());
                                createPersonalmappeResources(orgId, personalressursResources);
                            });

                    log.info("({}) end full provisioning", orgId);

                    fintRepository.getSinceTimestampMap().put(orgId, Instant.now().toEpochMilli());
                });
    }

    //@Scheduled(cron = "0 7-17 * * * MON-FRI")
    public void runDelta() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    log.info("({}) start delta provisioning", orgId);

                    fintRepository.getUpdates(orgId, PersonalressursResources.class, personalressursEndpoint)
                            .blockOptional()
                            .ifPresent(personalressursResources -> {
                                log.info("({}) found {} personalressurser", orgId, personalressursResources.getTotalItems());
                                createPersonalmappeResources(orgId, personalressursResources);
                            });

                    log.info("({}) end delta provisioning", orgId);
                });
    }

    private void createPersonalmappeResources(String orgId, PersonalressursResources personalressursResources) {
        personalressursResources.getContent()
                .sort(Comparator.comparing(resource -> Optional.ofNullable(resource.getBrukernavn())
                        .map(Identifikator::getIdentifikatorverdi)
                        .filter(StringUtils::isAlpha)
                        .orElse("ZZZ")));

        personalressursResources.getContent().stream().limit(LIMIT)
                .map(PersonalressursResource::getBrukernavn)
                .map(Identifikator::getIdentifikatorverdi)
                .forEach(username -> {
                    List<PersonalmappeResource> personalmappeResources = getPersonalmappeResources(orgId, username);
                    if (personalmappeResources.size() == 1) {
                        PersonalmappeResource personalmappeResource = personalmappeResources.get(0);
                        String id = String.format("%s_%s", orgId, Util.getNIN(personalmappeResource));
                        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);
                        if (mongoDBPersonalmappe.isPresent() && mongoDBPersonalmappe.get().getStatus() == HttpStatus.CREATED) {
                            updatePersonalmappeResource(orgId, personalmappeResource, mongoDBPersonalmappe.get().getAssociation());
                        } else {
                            createPersonalmappeResource(orgId, personalmappeResource);
                        }
                    } else {
                        log.debug("({}) {} personalmapper generated for employee {}", orgId, personalmappeResources.size(), username);
                    }
                });
    }

    public void createPersonalmappeResource(String orgId, PersonalmappeResource resource) {
        fintRepository.postForEntity(orgId, resource, personalmappeEndpoint)
                .doOnSuccess(getStatus -> {
                    String id = String.format("%s_%s", orgId, Util.getNIN(resource));
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .status(HttpStatus.ACCEPTED)
                            .association(getStatus.getHeaders().getLocation())
                            .build());
                    fintRepository.getForEntity(orgId, Object.class, getStatus.getHeaders().getLocation())
                            .doOnSuccess(clientResponse -> handleSuccess(clientResponse, orgId, id))
                            .doOnError(WebClientResponseException.class, clientResponse -> handleError(clientResponse, orgId, id))
                            .retryWhen(statusPending)
                            .subscribe();
                })
                .doOnError(WebClientResponseException.class, clientResponse ->
                        log.error("{} {}", clientResponse.getStatusCode(), clientResponse.getResponseBodyAsString()))
                .subscribe();
    }

    public void updatePersonalmappeResource(String orgId, PersonalmappeResource resource, URI uri) {
        fintRepository.putForEntity(orgId, resource, uri)
                .doOnSuccess(getStatus -> {
                    String id = String.format("%s_%s", orgId, Util.getNIN(resource));
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .status(HttpStatus.ACCEPTED)
                            .association(getStatus.getHeaders().getLocation())
                            .build());
                    fintRepository.getForEntity(orgId, Object.class, getStatus.getHeaders().getLocation())
                            .doOnSuccess(clientResponse -> handleSuccess(clientResponse, orgId, id))
                            .doOnError(WebClientResponseException.class, clientResponse -> handleError(clientResponse, orgId, id))
                            .retryWhen(statusPending)
                            .subscribe();
                })
                .doOnError(WebClientResponseException.class, clientResponse ->
                        log.debug("{} {}", clientResponse.getStatusCode(), clientResponse.getResponseBodyAsString()))
                .subscribe();
    }

    private void handleSuccess(ResponseEntity<Object> getForResource, String orgId, String id) {
        if (getForResource.getStatusCode().is3xxRedirection()) {
            mongoDBRepository.save(MongoDBPersonalmappe.builder()
                    .id(id)
                    .orgId(orgId)
                    .status(HttpStatus.CREATED)
                    .association(getForResource.getHeaders().getLocation())
                    .build());
        } else {
            throw new FinalStatusPendingException();
        }
    }

    private void handleError(WebClientResponseException clientResponse, String orgId, String id) {
        switch (clientResponse.getStatusCode()) {
            case CONFLICT:
                PersonalmappeResources personalmappeResources = new PersonalmappeResources();
                try {
                    personalmappeResources = new ObjectMapper().readValue(clientResponse.getResponseBodyAsString(), PersonalmappeResources.class);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                if (personalmappeResources.getTotalItems() == 1) {
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .association(getSelfLink(personalmappeResources.getContent().get(0)))
                            .status(HttpStatus.CREATED)
                            .build());
                } else {
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .status(HttpStatus.CONFLICT)
                            .message("More than one personalmappe in conflict")
                            .build());
                }
                break;
            case BAD_REQUEST:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.BAD_REQUEST)
                        .message(clientResponse.getResponseBodyAsString())
                        .build());
                break;
            case INTERNAL_SERVER_ERROR:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message(clientResponse.getResponseBodyAsString())
                        .build());
                break;
            case GONE:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.GONE)
                        .build());
                break;
            default:
                log.error("{} {}", clientResponse.getStatusCode(), clientResponse.getResponseBodyAsString());
                break;
        }
    }

    /*
    TODO - set reasonable values
     */
    private Retry<?> statusPending = Retry.anyOf(FinalStatusPendingException.class)
            .fixedBackoff(Duration.ofSeconds(5))
            .retryMax(10)
            .doOnRetry(exception -> log.info("{}", exception));

    public List<PersonalmappeResource> getPersonalmappeResources(String orgId, String username) {
        graphQLQuery.setVariables(Collections.singletonMap("brukernavn", username));

        return fintRepository.post(orgId, GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                .blockOptional()
                .map(GraphQLPersonalmappe::getResult)
                .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                .map(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold)
                .orElseGet(Collections::emptyList)
                .stream().filter(isActive(LocalDateTime.now()).and(isHovedstilling().and(hasPersonalressurskategori(orgId))))
                .map(PersonalmappeResourceFactory::toPersonalmappeResource)
                .filter(hasMandatoryFieldsAndRelations())
                .collect(Collectors.toList());
    }

    private Predicate<GraphQLPersonalmappe.Arbeidsforhold> isActive(LocalDateTime now) {
        return arbeidsforhold -> {
            if (arbeidsforhold.getGyldighetsperiode() == null) return false;

            if (arbeidsforhold.getGyldighetsperiode().getSlutt() == null) {
                return now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            } else {
                return now.isBefore(arbeidsforhold.getGyldighetsperiode().getSlutt())
                        && now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            }
        };
    }

    private Predicate<GraphQLPersonalmappe.Arbeidsforhold> isHovedstilling() {
        return GraphQLPersonalmappe.Arbeidsforhold::getHovedstilling;
    }

    private Predicate<GraphQLPersonalmappe.Arbeidsforhold> hasPersonalressurskategori(String orgId) {
        return arbeidsforhold -> organisationProperties.getOrganisations().get(orgId).getPersonalressurskategori()
                .contains(Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                        .map(GraphQLPersonalmappe.Personalressurs::getPersonalressurskategori)
                        .map(GraphQLPersonalmappe.Personalressurskategori::getKode)
                        .orElse(null));
    }

    private Predicate<PersonalmappeResource> hasMandatoryFieldsAndRelations() {
        return personalmappeResource -> (!personalmappeResource.getPersonalressurs().isEmpty()
                && !personalmappeResource.getPerson().isEmpty()
                && !personalmappeResource.getArbeidssted().isEmpty()
                && !personalmappeResource.getLeder().isEmpty()
                && Objects.nonNull(personalmappeResource.getNavn()));
    }

    private URI getSelfLink(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getSelfLinks().stream()
                .map(Link::getHref)
                .map(URI::create)
                .findAny()
                .orElse(null);
    }
}