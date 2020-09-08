package no.fint.personalmappe.repository;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.exception.TokenException;
import no.fint.personalmappe.model.GraphQLQuery;
import no.fint.personalmappe.properties.OrganisationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

        Mono<T> resources = authorizedClient(organisation).flatMap(client ->
                webClient.get()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .bodyToMono(clazz)
        );

        sinceTimestamp.put(orgId, Instant.now().toEpochMilli());

        return resources;
    }

    public <T> Mono<T> getUpdates(String orgId, Class<T> clazz, URI uri) {
        Long since = sinceTimestamp.getOrDefault(orgId, 0L);

        Mono<T> resources = get(orgId, clazz, UriComponentsBuilder.fromUri(uri).queryParam("sinceTimeStamp", since).build().toUri());

        sinceTimestamp.put(orgId, Instant.now().toEpochMilli());

        return resources;
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

    public Mono<ResponseEntity<Void>> putForEntity(String orgId, PersonalmappeResource personalmappeResource, URI uri) {
        OrganisationProperties.Organisation organisation = organisationProperties.getOrganisations().get(orgId);

        return authorizedClient(organisation).flatMap(client ->
                webClient.put()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(personalmappeResource)
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
}