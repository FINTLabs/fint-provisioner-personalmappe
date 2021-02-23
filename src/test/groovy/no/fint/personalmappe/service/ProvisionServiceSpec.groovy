package no.fint.personalmappe.service

import no.fint.model.administrasjon.organisasjon.Organisasjonselement
import no.fint.model.administrasjon.personal.Personalressurs
import no.fint.model.felles.Person
import no.fint.model.felles.kompleksedatatyper.Personnavn
import no.fint.model.resource.Link
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.properties.OrganisationProperties
import no.fint.personalmappe.repository.FintRepository
import no.fint.personalmappe.repository.MongoDBRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import spock.lang.Specification

@DataMongoTest
class ProvisionServiceSpec extends Specification {
    FintRepository fintRepository = Mock()
    ResponseHandlerService responseHandlerService = Mock()
    OrganisationProperties organisationProperties = Mock()

    @Autowired
    MongoDBRepository mongoDBRepository

    ProvisionService provisionService

    void setup() {
        provisionService = new ProvisionService(fintRepository, responseHandlerService, organisationProperties, mongoDBRepository, Mock(PolicyService))
    }

    void cleanup() {
        mongoDBRepository.deleteAll()
    }

    def "validPersonnelFolder returns true if all mandatory fields are present"() {
        when:
        def test = provisionService.validPersonnelFolder().test(newPersonnelFolder('brukernavn-leder'))

        then:
        1 * organisationProperties.getAdministrativeUnitsExcluded() >> []
        test
    }

    def "validPersonnelFolder returns false if subject and leader are the same"() {
        when:
        def test = provisionService.validPersonnelFolder().test(newPersonnelFolder('brukernavn'))

        then:
        !test
    }

    def "validPersonnelFolder returns false if workplace is included in list of excluded administrative units"() {
        when:
        def test = provisionService.validPersonnelFolder().test(newPersonnelFolder('brukernavn-leder'))

        then:
        1 * organisationProperties.getAdministrativeUnitsExcluded() >> ['organisasjonsid']
        !test
    }

    def newPersonnelFolder(String username) {
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
        resource.addPersonalressurs(Link.with(Personalressurs.class, "brukernavn", "brukernavn"))
        resource.addArbeidssted(Link.with(Organisasjonselement.class, "organisasjonsid", "organisasjonsid"))
        resource.addLeder(Link.with(Personalressurs.class, "brukernavn", username))

        return resource
    }
}
