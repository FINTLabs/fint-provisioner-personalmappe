package no.novari.personalmappe.repository;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import no.novari.personalmappe.model.GraphQLQuery;
import no.novari.personalmappe.model.LastUpdated;
import no.novari.personalmappe.properties.OrganisationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
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

    private final Map<String, Long> sinceTimestamp = Collections.synchronizedMap(new HashMap<>());

    public FintRepository(WebClient webClient, ReactiveOAuth2AuthorizedClientManager authorizedClientManager, OrganisationProperties organisationProperties, Authentication principal) {
        this.webClient = webClient;
        this.authorizedClientManager = authorizedClientManager;
        this.organisationProperties = organisationProperties;
        this.principal = principal;
    }

    public <T> Mono<T> get(Class<T> clazz, URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.get()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .bodyToMono(clazz)
        );
    }

    public <T> Mono<T> getUpdates(Class<T> clazz, URI uri) {
        Long since = sinceTimestamp.getOrDefault(organisationProperties.getOrgId(), 0L);

        return get(LastUpdated.class, UriComponentsBuilder.fromUri(uri).pathSegment("last-updated").build().toUri())
                .flatMap(lastUpdated -> {
                    sinceTimestamp.put(organisationProperties.getOrgId(), lastUpdated.getLastUpdated());

                    return get(clazz, UriComponentsBuilder.fromUri(uri).queryParam("sinceTimeStamp", since).build().toUri());
                });
    }

    public <T> Mono<ResponseEntity<T>> getForEntity(Class<T> clazz, URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.get()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .toEntity(clazz)
        );
    }

    public <T> Mono<T> post(Class<T> clazz, GraphQLQuery graphQLQuery, URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.post()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(graphQLQuery)
                        .retrieve()
                        .bodyToMono(clazz)
        );
    }

    public Mono<ResponseEntity<Void>> postForEntity(PersonalmappeResource personalmappeResource, URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.post()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(personalmappeResource)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public <T> Mono<ResponseEntity<Void>> putForEntity(T resource, URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.put()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .bodyValue(resource)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    private Mono<OAuth2AuthorizedClient> authorizedClient() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(organisationProperties.getRegistration())
                .principal(principal)
                .attributes(attrs -> {
                    attrs.put(OAuth2ParameterNames.USERNAME, organisationProperties.getUsername());
                    attrs.put(OAuth2ParameterNames.PASSWORD, organisationProperties.getPassword());
                }).build();

        return authorizedClientManager.authorize(authorizeRequest);
    }

    public Mono<ResponseEntity<Void>> headForEntity(URI uri) {
        return authorizedClient().flatMap(client ->
                webClient.head()
                        .uri(uri)
                        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                        .retrieve()
                        .toBodilessEntity());
    }
}
