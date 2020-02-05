package no.fint.personalmappe.repository;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.properties.OrganisationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Repository
public class FintRepository {

    private final WebClient webClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final OrganisationProperties organisationProperties;
    private final Authentication principal;

    @Getter
    private final Map<String, Long> sinceTimestampMap = Collections.synchronizedMap(new HashMap<>());

    public FintRepository(WebClient webClient, OAuth2AuthorizedClientManager authorizedClientManager, OrganisationProperties organisationProperties, Authentication principal) {
        this.webClient = webClient;
        this.authorizedClientManager = authorizedClientManager;
        this.organisationProperties = organisationProperties;
        this.principal = principal;
    }

    public <T> Mono<T> get(String orgId, Class<T> clazz, URI uri) {
        log.info("({}) fetching: {}", orgId, uri);

        return webClient.get()
                .uri(uri)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(getAuthorizedClient(orgId)))
                .retrieve()
                .bodyToMono(clazz);
    }

    public <T> Mono<T> getUpdates(String orgId, Class<T> clazz, URI uri) {
        Long since = sinceTimestampMap.getOrDefault(orgId, 0L);

        Mono<T> resources = get(orgId, clazz, UriComponentsBuilder.fromUri(uri).queryParam("sinceTimeStamp", since).build().toUri());

        sinceTimestampMap.put(orgId, Instant.now().toEpochMilli());

        return resources;
    }

    public <T> Mono<ResponseEntity<T>> getForEntity(String orgId, Class<T> clazz, URI uri) {
        return webClient.get()
                .uri(uri)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(getAuthorizedClient(orgId)))
                .retrieve()
                .toEntity(clazz);
    }

    public <T> Mono<T> post(String orgId, Class<T> clazz, Object value, URI uri) {
        return webClient.post()
                .uri(uri)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(getAuthorizedClient(orgId)))
                .bodyValue(value)
                .retrieve()
                .bodyToMono(clazz);
    }

    public <T> Mono<ResponseEntity<Void>> postForEntity(String orgId, PersonalmappeResource personalmappeResource, URI uri) {
        return webClient.post()
                .uri(uri)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(getAuthorizedClient(orgId)))
                .bodyValue(personalmappeResource)
                .retrieve()
                .toBodilessEntity();
    }

    public <T> Mono<ResponseEntity<Void>> putForEntity(String orgId, Object bodyValue, URI uri) {
        return webClient.put()
                .uri(uri)
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(getAuthorizedClient(orgId)))
                .bodyValue(bodyValue)
                .retrieve()
                .toBodilessEntity();
    }

    private OAuth2AuthorizedClient getAuthorizedClient(String orgId) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(orgId)
                .principal(principal)
                .attributes(attrs -> {
                    attrs.put(OAuth2ParameterNames.USERNAME, organisationProperties.getOrganisations().get(orgId).getUsername());
                    attrs.put(OAuth2ParameterNames.PASSWORD, organisationProperties.getOrganisations().get(orgId).getPassword());
                }).build();

        return authorizedClientManager.authorize(authorizeRequest);
    }
}