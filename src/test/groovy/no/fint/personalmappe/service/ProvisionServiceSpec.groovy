package no.fint.personalmappe.service

import no.fint.model.administrasjon.organisasjon.Organisasjonselement
import no.fint.model.administrasjon.personal.Personalressurs
import no.fint.model.felles.Person
import no.fint.model.felles.kompleksedatatyper.Personnavn
import no.fint.model.resource.Link
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.factory.PersonalmappeResourceFactory
import no.fint.personalmappe.model.GraphQLPersonalmappe
import no.fint.personalmappe.model.MongoDBPersonalmappe
import no.fint.personalmappe.properties.OrganisationProperties
import no.fint.personalmappe.repository.FintRepository
import no.fint.personalmappe.repository.MongoDBRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import spock.lang.Specification

@DataMongoTest
class ProvisionServiceSpec extends Specification {
    FintRepository fintRepository = Mock()
    ResponseHandlerService responseHandlerService = Mock()
    OrganisationProperties organisationProperties = Mock()
    PersonalmappeResourceFactory personalmappeResourceFactory = Mock()
    PolicyService policyService = Mock()

    @Autowired
    MongoDBRepository mongoDBRepository

    ProvisionService provisionService

    void setup() {
        provisionService = new ProvisionService(fintRepository, responseHandlerService, personalmappeResourceFactory, organisationProperties, mongoDBRepository, policyService)
    }

    void cleanup() {
        mongoDBRepository.deleteAll()
    }

    def "run returns flux and stores document when all mandatory fields and relations are present"() {
        given:
        1 * fintRepository.post(_, _, _) >> Mono.just(newGraphQLPersonnelFolder())
        1 * personalmappeResourceFactory.toPersonalmappeResource(_, _, _) >> newPersonnelFolder('username', 'username-leader', 'workplace')
        1 * organisationProperties.getAdministrativeUnitsExcluded() >> []

        1 * organisationProperties.getOrgId() >> 'org-id'

        1 * fintRepository.postForEntity(_, _) >> Mono.just(ResponseEntity.accepted().location(URI.create('/status')).build())
        1 * responseHandlerService.pendingHandler(_, _, _) >> newMongoDbPersonnelFolder(HttpStatus.ACCEPTED)

        1 * fintRepository.getForEntity(_, _) >> Mono.just(ResponseEntity.created(URI.create('/resource')).build())
        1 * responseHandlerService.successHandler(_, _) >> newMongoDbPersonnelFolder(HttpStatus.CREATED)

        when:
        def flux = provisionService.run(['username'], 1)

        then:
        StepVerifier.create(flux)
                .expectNextCount(1)
                .verifyComplete()

        mongoDBRepository.count() == 1
    }

    def "run return empty flux if subject and leader are identical"() {
        given:
        1 * fintRepository.post(_, _, _) >> Mono.just(newGraphQLPersonnelFolder())
        1 * personalmappeResourceFactory.toPersonalmappeResource(_, _, _) >> newPersonnelFolder('username', 'username', 'workplace')

        when:
        def flux = provisionService.run(['username'], 1)

        then:
        StepVerifier.create(flux)
                .verifyComplete()

        mongoDBRepository.count() == 0
    }

    def "run returns empty flux if workplace is included in list of excluded administrative units"() {
        given:
        1 * fintRepository.post(_, _, _) >> Mono.just(newGraphQLPersonnelFolder())
        1 * personalmappeResourceFactory.toPersonalmappeResource(_, _, _) >> newPersonnelFolder('username', 'username-leader', 'workplace')
        1 * organisationProperties.getAdministrativeUnitsExcluded() >> ['workplace']

        when:
        def flux = provisionService.run(['username'], 1)

        then:
        StepVerifier.create(flux)
                .verifyComplete()

        mongoDBRepository.count() == 0
    }

    def "run returns empty flux on error"() {
        given:
        1 * fintRepository.post(_, _, _) >> Mono.just(newGraphQLPersonnelFolder())
        1 * personalmappeResourceFactory.toPersonalmappeResource(_, _, _) >> newPersonnelFolder('username', 'username-leader', 'workplace')
        1 * organisationProperties.getAdministrativeUnitsExcluded() >> []

        1 * organisationProperties.getOrgId() >> 'org-id'

        1 * fintRepository.postForEntity(_, _) >> Mono.error(new WebClientResponseException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.name(), null, null, null))

        when:
        def flux = provisionService.run(['username'], 1)

        then:
        StepVerifier.create(flux)
                .verifyComplete()

        mongoDBRepository.count() == 0
    }

    def newPersonnelFolder(String username, String usernameLeader, String workplace) {
        def resource = new PersonalmappeResource(
                navn: new Personnavn(
                        fornavn: 'fornavn',
                        mellomnavn: 'mellomnavn',
                        etternavn: 'ettternavn'
                ),
                part: [new PartsinformasjonResource()],
                tittel: 'DUMMY',
        )

        resource.addPerson(Link.with(Person.class, "fodselsnummer", "fodselsnummer"))
        resource.addPersonalressurs(Link.with(Personalressurs.class, "brukernavn", username))
        resource.addArbeidssted(Link.with(Organisasjonselement.class, "organisasjonsid", workplace))
        resource.addLeder(Link.with(Personalressurs.class, "brukernavn", usernameLeader))

        return resource
    }

    def newMongoDbPersonnelFolder(HttpStatus httpStatus) {
        return MongoDBPersonalmappe.builder()
                .id('id')
                .username('username')
                .status(httpStatus)
                .build()
    }

    def newGraphQLPersonnelFolder() {
        return new GraphQLPersonalmappe(result: new GraphQLPersonalmappe.Result(
                personalressurs: new GraphQLPersonalmappe.Personalressurs())
        )
    }
}
