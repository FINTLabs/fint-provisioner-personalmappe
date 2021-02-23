package no.fint.personalmappe.repository

import com.fasterxml.jackson.databind.ObjectMapper
import no.fint.model.resource.AbstractCollectionResources
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.model.resource.administrasjon.personal.PersonalressursResources
import no.fint.personalmappe.model.GraphQLPersonalmappe
import no.fint.personalmappe.model.GraphQLQuery
import no.fint.personalmappe.properties.OrganisationProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification

class FintRepositorySpec extends Specification {
    MockWebServer mockWebServer = new MockWebServer()
    WebClient webClient

    ReactiveOAuth2AuthorizedClientManager authorizedClientManager = Stub(ReactiveOAuth2AuthorizedClientManager) {
        authorize(_ as OAuth2AuthorizeRequest) >> Mono.just(Mock(OAuth2AuthorizedClient))
    }

    OrganisationProperties organisationProperties = new OrganisationProperties(
                username: _ as String, password: _ as String, registration: _ as String, orgId: 'mock')

    FintRepository fintRepository

    void setup() {
        webClient = WebClient.builder().build()
        fintRepository = new FintRepository(webClient, authorizedClientManager, organisationProperties, Mock(Authentication))
    }

    def "get() for given type returns resources of given type"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(getPersonalressursResources()))
                .setHeader('content-type', 'application/json')
                .setResponseCode(200))

        when:
        def resources = fintRepository.get(PersonalressursResources.class, URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.totalItems == 1
    }

    def "getUpdates() for given type returns resources of given type"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setBody('{ "lastUpdated": 1234 }')
                .setHeader('content-type', 'application/json')
                .setResponseCode(200))
        mockWebServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(getPersonalressursResources()))
                .setHeader('content-type', 'application/json')
                .setResponseCode(200))

        when:
        def resources = fintRepository.getUpdates(PersonalressursResources.class, URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.totalItems == 1
        fintRepository.sinceTimestamp['mock'] == 1234
    }

    def "getForEntity() returns response entity"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader('location', 'link.to.location')
                .setResponseCode(303))

        when:
        def resources = fintRepository.getForEntity(Object.class, URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.statusCodeValue == 303
        resources.headers.getLocation().toString() == 'link.to.location'
    }

    def "post() returns response entity"() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(getGraphQLPersonalmappe()))
                .setHeader('content-type', 'application/json')
                .setResponseCode(200))

        when:
        def resources = fintRepository.post(GraphQLPersonalmappe.class, new GraphQLQuery(), URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.result.personalressurs.ansattnummer.identifikatorverdi == '12345'
    }

    def "postForEntity() returns response entity"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader('location', 'link.to.location')
                .setResponseCode(202))

        when:
        def resources = fintRepository.postForEntity(new PersonalmappeResource(), URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.statusCodeValue == 202
        resources.headers.getLocation().toString() == 'link.to.location'
    }

    def "putForEntity() returns response entity"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader('location', 'link.to.location')
                .setResponseCode(202))

        when:
        def resources = fintRepository.putForEntity(new PersonalmappeResource(), URI.create(mockWebServer.url("/").toString())).block()

        then:
        resources.statusCodeValue == 202
        resources.headers.getLocation().toString() == 'link.to.location'
    }

    PersonalressursResources getPersonalressursResources() {
        return new PersonalressursResources(
                embedded: new AbstractCollectionResources.EmbeddedResources(
                        entries: [
                                new PersonalressursResources()
                        ]
                ))
    }

    GraphQLPersonalmappe getGraphQLPersonalmappe() {
        return new GraphQLPersonalmappe(
                result: new GraphQLPersonalmappe.Result(
                        personalressurs: new GraphQLPersonalmappe.Personalressurs(
                                ansattnummer: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '12345'),
                                brukernavn: null,
                                person: null,
                                arbeidsforhold: [],
                                personalressurskategori: null
                        )
                )
        )
    }
}
