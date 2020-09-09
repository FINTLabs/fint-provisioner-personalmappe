package no.fint.personalmappe.repository;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.GraphQLQuery;
import no.fint.personalmappe.model.LastUpdated;
import no.fint.personalmappe.properties.OrganisationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Repository
public class FintRepository {
    private final WebClient webClient;
    private final ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    private final OrganisationProperties organisationProperties;
    private final Authentication principal;

    @Getter
    private final Map<String, Long> sinceTimestamp = Collections.synchronizedMap(new HashMap<>());

    public FintRepository(WebClient webClient, ReactiveOAuth2AuthorizedClientManager authorizedClientManager, OrganisationProperties organisationProperties, Authentication principal) {
        this.webClient = webClient;
        this.authorizedClientManager = authorizedClientManager;
        this.organisationProperties = organisationProperties;
        this.principal = principal;
    }

    public <T> Mono<T> get(String orgId, Class<T> clazz, URI uri) {
        log.trace("({}) fetching: {}", orgId, uri);

        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.get()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .bodyToMono(clazz)
        );
    }

    public <T> Mono<T> getUpdates(String orgId, Class<T> clazz, URI uri) {
        Long since = sinceTimestamp.getOrDefault(orgId, 0L);

        get(orgId, LastUpdated.class, UriComponentsBuilder.fromUri(uri).pathSegment("last-updated").build().toUri())
                .blockOptional()
                .ifPresent(upd -> sinceTimestamp.put(orgId, upd.getLastUpdated()));

        return get(orgId, clazz, UriComponentsBuilder.fromUri(uri).queryParam("sinceTimeStamp", since).build().toUri());
    }

    public <T> Mono<ResponseEntity<T>> getForEntity(String orgId, Class<T> clazz, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.get()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .toEntity(clazz)
        );
    }

    public <T> Mono<T> post(String orgId, Class<T> clazz, GraphQLQuery graphQLQuery, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.post()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(graphQLQuery)
                        .retrieve()
                        .bodyToMono(clazz)
        );
    }

    public Mono<ResponseEntity<Void>> postForEntity(String orgId, PersonalmappeResource personalmappeResource, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.post()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(personalmappeResource)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public <T> Mono<ResponseEntity<Void>> putForEntity(String orgId, T resource, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.put()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(resource)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    private Mono<OAuth2AuthorizedClient> authorizedClient(OrganisationProperties.Organisation organisation) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(organisation.getRegistration())
                .principal(principal)
                .attributes(attrs -> {
                    attrs.put(OAuth2ParameterNames.USERNAME, organisation.getUsername());
                    attrs.put(OAuth2ParameterNames.PASSWORD, organisation.getPassword());
                }).build();

        return authorizedClientManager.authorize(authorizeRequest);
    }

    public Mono<ResponseEntity<Void>> headForEntity(String orgId, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.head()
                        .uri(uri)
                        .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .toBodilessEntity());
    }
}
