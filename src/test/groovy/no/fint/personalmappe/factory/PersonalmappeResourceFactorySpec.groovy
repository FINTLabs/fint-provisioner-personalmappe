package no.fint.personalmappe.factory

import groovy.util.logging.Slf4j
import no.fint.model.administrasjon.organisasjon.Organisasjonselement
import no.fint.model.administrasjon.personal.Personalressurs
import no.fint.model.felles.Person
import no.fint.model.felles.kompleksedatatyper.Personnavn
import no.fint.model.resource.Link
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.model.GraphQLPersonalmappe
import no.fint.personalmappe.properties.OrganisationProperties
import reactor.test.StepVerifier
import spock.lang.Specification

import java.time.LocalDateTime

@Slf4j
class PersonalmappeResourceFactorySpec extends Specification {

    OrganisationProperties organisationProperties = Mock {
        getOrganisations() >> ['orgId': new OrganisationProperties.Organisation(
                personalressurskategori: ['F', 'M']
        )]
    }

    PersonalmappeResourceFactory personalmappeResourceFactory = new PersonalmappeResourceFactory(organisationProperties)

    def "given valid arbeidsforhold return true"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def valid = personalmappeResourceFactory.validArbeidsforhold().test('orgId', arbeidsforhold)

        then:
        valid
    }

    def "given arbeidsforhold with invalid personalressurskategori return false"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'Q', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def valid = personalmappeResourceFactory.validArbeidsforhold().test('orgId', arbeidsforhold)

        then:
        !valid
    }

    def "given arbeidsforhold with invalid hovedstilling return false"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', false,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def valid = personalmappeResourceFactory.validArbeidsforhold().test('orgId', arbeidsforhold)

        then:
        !valid
    }

    def "given arbeidsforhold with invalid gyldighetsperiode return false"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', true,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1))

        when:
        def valid = personalmappeResourceFactory.validArbeidsforhold().test('orgId', arbeidsforhold)

        then:
        !valid
    }

    def "given valid personalmapperesource return true"() {
        given:
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId', 'brukernavn-leder')

        when:
        def valid = personalmappeResourceFactory.validPersonalmappeResource().test(personalmappe)

        then:
        valid
    }

    def "given personalmapperesource with invalid leder return false"() {
        given:
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId', 'brukernavn')

        when:
        def valid = personalmappeResourceFactory.validPersonalmappeResource().test(personalmappe)

        then:
        !valid
    }

    def "given personalmapperesource with missing fields or attributes return false"() {
        given:
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId', 'brukernavn-leder')
        personalmappe.personalressurs.clear()

        when:
        def valid = personalmappeResourceFactory.validPersonalmappeResource().test(personalmappe)

        then:
        !valid
    }

    def "given valid arbeidsforhold return personalmapperesource"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId', 'brukernavn-leder')

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource('orgId', arbeidsforhold, ['organisasjonsId'])

        then:
        StepVerifier.create(resource).expectNext(personalmappe).verifyComplete()
    }

    def "given invalid arbeidsforhold return empty mono"() {
        given:
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource('orgId', arbeidsforhold, ['organisasjonsId'])

        then:
        StepVerifier.create(resource).verifyComplete()
    }

    def "given valid arbeidsforhold and invalid administrativenhet return personalmapperesource with leders leder"() {
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn-leder', 'F', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId-leder', 'brukernavn-leders-leder')

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource('orgId', arbeidsforhold, [null])

        then:
        StepVerifier.create(resource).expectNext(personalmappe).verifyComplete()
    }

    def "given valid arbeidsforhold and personalressurs is leder return personalmapperesource with leders leder"() {
        def arbeidsforhold = getArbeidsforhold('brukernavn', 'brukernavn','F', true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))
        def personalmappe = getPersonalmappeResource('brukernavn', 'organisasjonsId-leder', 'brukernavn-leders-leder')

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource('orgId', arbeidsforhold, [])

        then:
        StepVerifier.create(resource).expectNext(personalmappe).verifyComplete()
    }

    PersonalmappeResource getPersonalmappeResource(String brukernavn, String organisajonsId, String brukernavnLeder) {
        PersonalmappeResource resource = new PersonalmappeResource()
        Personnavn personnavn = new Personnavn()
        personnavn.setFornavn('fornavn')
        personnavn.setMellomnavn('mellomnavn')
        personnavn.setEtternavn('etternavn')
        resource.setNavn(personnavn)
        resource.setPart(Collections.singletonList(new PartsinformasjonResource()))
        resource.setTittel("DUMMY")
        resource.addPerson(Link.with(Person.class, 'fodselsnummer', 'fodselsnummer'))
        resource.addPersonalressurs(Link.with(Personalressurs.class, 'brukernavn', brukernavn))
        resource.addArbeidssted(Link.with(Organisasjonselement.class, 'organisasjonsid', organisajonsId))
        resource.addLeder(Link.with(Personalressurs.class, 'brukernavn', brukernavnLeder))
        return resource
    }

    GraphQLPersonalmappe.Arbeidsforhold getArbeidsforhold(String brukernavn, String brukernavnLeder, String personalressurskategori, boolean hovedstilling,
    LocalDateTime start, LocalDateTime slutt) {
        return new GraphQLPersonalmappe.Arbeidsforhold(
                arbeidssted: new GraphQLPersonalmappe.Organisasjonselement(
                        organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: 'organisasjonsId'),
                        leder: new GraphQLPersonalmappe.Personalressurs(
                                brukernavn: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: brukernavnLeder)),
                        overordnet: new GraphQLPersonalmappe.Organisasjonselement(
                                organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: 'organisasjonsId-leder'),
                                leder: new GraphQLPersonalmappe.Personalressurs(
                                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                                identifikatorverdi: 'brukernavn-leders-leder')))),
                personalressurs: new GraphQLPersonalmappe.Personalressurs(
                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: brukernavn),
                        person: new GraphQLPersonalmappe.Person(
                                fodselsnummer: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: 'fodselsnummer'),
                                navn: new GraphQLPersonalmappe.Navn(
                                        fornavn: 'fornavn',
                                        mellomnavn: 'mellomnavn',
                                        etternavn: 'etternavn'
                                )
                        ),
                        personalressurskategori: new GraphQLPersonalmappe.Personalressurskategori(
                                kode: personalressurskategori
                        )
                ),
                gyldighetsperiode: new GraphQLPersonalmappe.Periode(
                        start: start,
                        slutt: slutt
                ),
                hovedstilling: hovedstilling
        )
    }
}
