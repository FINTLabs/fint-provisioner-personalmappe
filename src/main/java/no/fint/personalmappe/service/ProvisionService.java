package no.fint.personalmappe.service;

import lombok.Builder;
import lombok.Data;
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
import no.fint.personalmappe.utilities.GraphQLUtilities;
import no.fint.personalmappe.utilities.NINUtilities;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
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

    private final GraphQLQuery graphQLQuery = GraphQLUtilities.getGraphQLQuery("personalressurs.graphql");

    public ProvisionService(FintRepository fintRepository, OrganisationProperties organisationProperties, MongoDBRepository mongoDBRepository, ResponseHandlerService responseHandlerService) {
        this.fintRepository = fintRepository;
        this.organisationProperties = organisationProperties;
        this.mongoDBRepository = mongoDBRepository;
        this.responseHandlerService = responseHandlerService;
    }

    @Scheduled(cron = "${fint.cron.bulk}")
    public void bulk() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    OrganisationProperties.Organisation props = organisationProperties.getOrganisations().get(orgId);
                    if (props.isBulk()) {
                        log.trace("Bulking personalmapper for {}", orgId);
                        provisionByOrgId(orgId, props.getBulkLimit(), fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint));
                    }
                    else {
                        log.trace("Bulk is disabled for {}", orgId);
                    }
                });
    }

    @Scheduled(cron = "${fint.cron.delta}")
    public void delta() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    OrganisationProperties.Organisation props = organisationProperties.getOrganisations().get(orgId);
                    if (props.isDelta()) {
                        log.trace("Delta personalmapper for {}", orgId);
                        provisionByOrgId(orgId, 0, fintRepository.getUpdates(orgId, PersonalressursResources.class, personalressursEndpoint));
                    }
                    else {
                        log.trace("Delta is disabled for {}", orgId);
                    }
                });
    }

    public void provisionByOrgId(String orgId, int limit, Mono<PersonalressursResources> personalressursResources) {
        List<String> usernames = personalressursResources
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(resource -> Optional.ofNullable(resource.getBrukernavn())
                        .map(Identifikator::getIdentifikatorverdi)
                        .filter(StringUtils::isAlpha)
                        .orElse("ZZZ")))
                .map(PersonalressursResource::getBrukernavn)
                .filter(Objects::nonNull)
                .map(Identifikator::getIdentifikatorverdi)
                .distinct()
                .collect(Collectors.toList());

        usernames.stream()
                .limit(limit == 0 ? usernames.size() : limit)
                .map(username -> getPersonalmappeResource(orgId, username))
                .filter(Objects::nonNull)
                .forEach(personalmappeResourceWithUsername ->
                        provision(orgId, personalmappeResourceWithUsername.getUsername(),
                                personalmappeResourceWithUsername.personalmappeResource));
    }

    public PersonalmappeResourceWithUsername getPersonalmappeResource(String orgId, String username) {
        graphQLQuery.setVariables(Collections.singletonMap("brukernavn", username));

        List<PersonalmappeResource> personalmappeResources =
                fintRepository.post(orgId, GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)
                        .blockOptional()
                        .map(GraphQLPersonalmappe::getResult)
                        .map(GraphQLPersonalmappe.Result::getPersonalressurs)
                        .map(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold)
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(isActive(LocalDateTime.now()).and(isHovedstilling().and(hasPersonalressurskategori(orgId))))
                        .map(PersonalmappeResourceFactory::toPersonalmappeResource)
                        .filter(hasMandatoryFieldsAndRelations())
                        .collect(Collectors.toList());

        log.trace("{} {}", username, personalmappeResources.size());

        return (personalmappeResources.size() == 1 ?
                PersonalmappeResourceWithUsername.builder()
                        .username(username)
                        .personalmappeResource(personalmappeResources.get(0))
                        .build() : null);
    }

    @Data
    @Builder
    public static class PersonalmappeResourceWithUsername {
        private String username;
        private PersonalmappeResource personalmappeResource;
    }

    public void provision(String orgId, String username, PersonalmappeResource personalmappeResource) {
        String id = orgId + "_" + NINUtilities.getNIN(personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        if (mongoDBPersonalmappe.isPresent() && mongoDBPersonalmappe.get().getStatus() == HttpStatus.CREATED) {
            fintRepository.putForEntity(orgId, personalmappeResource, mongoDBPersonalmappe.get().getAssociation())
                    .doOnSuccess(status -> responseHandlerService.handleStatus(orgId, id, username, status))
                    .blockOptional()
                    .ifPresent(clientResponse -> getForResource(orgId, id, username, clientResponse));
        } else {
            fintRepository.postForEntity(orgId, personalmappeResource, personalmappeEndpoint)
                    .doOnSuccess(status -> responseHandlerService.handleStatus(orgId, id, username, status))
                    .blockOptional()
                    .ifPresent(clientResponse -> getForResource(orgId, id, username, clientResponse));
        }
    }

    private void getForResource(String orgId, String id, String username, ResponseEntity<Void> status) {
        fintRepository.getForEntity(orgId, Object.class, status.getHeaders().getLocation())
                .doOnSuccess(resource -> responseHandlerService.handleResource(resource, orgId, id, username))
                .doOnError(WebClientResponseException.class, error -> responseHandlerService.handleError(error, orgId, id, username))
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
}