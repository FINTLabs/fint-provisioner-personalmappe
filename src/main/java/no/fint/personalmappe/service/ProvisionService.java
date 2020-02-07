package no.fint.personalmappe.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Identifikator;
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
import no.fint.personalmappe.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
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
    private final ResponseHandlerService responseHandlerService;
    private final OrganisationProperties organisationProperties;

    @Setter
    private static int LIMIT = 20;

    private GraphQLQuery graphQLQuery = Util.getGraphQLQuery("personalressurs.graphql");

    public ProvisionService(FintRepository fintRepository, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, ResponseHandlerService responseHandlerService) {
        this.fintRepository = fintRepository;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.responseHandlerService = responseHandlerService;
    }

    public void full() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    log.info("({}) start full provisioning", orgId);
                    fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint)
                            .blockOptional()
                            .map(PersonalressursResources::getContent).orElse(Collections.emptyList())
                            .parallelStream()
                            .map(PersonalressursResource::getBrukernavn).filter(Objects::nonNull)
                            .map(Identifikator::getIdentifikatorverdi)
                            .map(username -> getPersonalmappeResources(orgId, username))
                            .filter(personalmappeResources -> personalmappeResources.size() == 1)
                            .map(personalmappeResources -> personalmappeResources.get(0))
                            .forEach(personalmappeResource -> provision(orgId, personalmappeResource));

                    fintRepository.getSinceTimestampMap().put(orgId, Instant.now().toEpochMilli());
                });
    }

    public void delta() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    log.info("({}) start delta provisioning", orgId);
                    fintRepository.getUpdates(orgId, PersonalressursResources.class, personalressursEndpoint)
                            .blockOptional()
                            .map(PersonalressursResources::getContent).orElse(Collections.emptyList())
                            .parallelStream()
                            .map(PersonalressursResource::getBrukernavn).filter(Objects::nonNull)
                            .map(Identifikator::getIdentifikatorverdi)
                            .map(username -> getPersonalmappeResources(orgId, username))
                            .filter(personalmappeResources -> personalmappeResources.size() == 1)
                            .map(personalmappeResources -> personalmappeResources.get(0))
                            .forEach(personalmappeResource -> provision(orgId, personalmappeResource));
                });
    }

    public List<PersonalmappeResource> getPersonalmappeResources(String orgId, String username) {
        graphQLQuery.setVariables(Collections.singletonMap("brukernavn", username));

        return fintRepository.post(orgId, GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                .blockOptional()
                .map(GraphQLPersonalmappe::getResult)
                .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                .map(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold).orElse(Collections.emptyList())
                .stream()
                .filter(isActive(LocalDateTime.now()).and(isHovedstilling().and(hasPersonalressurskategori(orgId))))
                .map(PersonalmappeResourceFactory::toPersonalmappeResource)
                .filter(hasMandatoryFieldsAndRelations())
                .collect(Collectors.toList());
    }

    public void provision(String orgId, PersonalmappeResource personalmappeResource) {
        String id = orgId + "_" + Util.getNIN(personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        if (mongoDBPersonalmappe.isPresent() && mongoDBPersonalmappe.get().getStatus() == HttpStatus.CREATED) {
            fintRepository.putForEntity(orgId, personalmappeResource, mongoDBPersonalmappe.get().getAssociation())
                    .doOnSuccess(status -> responseHandlerService.handleStatus(orgId, id, status))
                    .blockOptional()
                    .ifPresent(clientResponse -> getForResource(orgId, id, clientResponse));
        } else {
            fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint)
                    .doOnSuccess(status -> responseHandlerService.handleStatus(orgId, id, status))
                    .blockOptional()
                    .ifPresent(clientResponse -> getForResource(orgId, id, clientResponse));
        }
    }

    private void getForResource(String orgId, String id, ResponseEntity<Void> status) {
        fintRepository.getForEntity(orgId, Object.class, status.getHeaders().getLocation())
                .doOnSuccess(resource -> responseHandlerService.handleResource(resource, orgId, id))
                .doOnError(WebClientResponseException.class, error -> responseHandlerService.handleError(error, orgId, id))
                .retryWhen(responseHandlerService.finalStatusPending)
                .subscribe();
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
        return arbeidsforhold -> Optional.ofNullable(arbeidsforhold.getHovedstilling())
                .orElse(false);
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
}