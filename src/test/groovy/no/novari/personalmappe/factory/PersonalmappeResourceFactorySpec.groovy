package no.novari.personalmappe.factory

import no.fint.model.administrasjon.organisasjon.Organisasjonselement
import no.fint.model.administrasjon.personal.Personalressurs
import no.fint.model.felles.Person
import no.fint.model.resource.Link
import no.novari.personalmappe.factory.PersonalmappeResourceFactory
import no.novari.personalmappe.model.GraphQLPersonalmappe
import no.novari.personalmappe.properties.OrganisationProperties
import spock.lang.Specification

import java.time.LocalDateTime

class PersonalmappeResourceFactorySpec extends Specification {
    PersonalmappeResourceFactory personalmappeResourceFactory = new PersonalmappeResourceFactory()

    OrganisationProperties organisationProperties = new OrganisationProperties(
            personnelResourceCategory: ['F', 'M']
    )

    def "given valid arbeidsforhold return personalmapperesource"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.navn.fornavn == 'fornavn'
        resource.navn.mellomnavn == 'mellomnavn'
        resource.navn.etternavn == 'etternavn'
        //resource.part.size() == 1
        resource.tittel == 'DUMMY'
        resource.person.first() == Link.with(Person.class, 'fodselsnummer', 'fodselsnummer')
        resource.personalressurs.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn')
        resource.arbeidssted.first() == Link.with(Organisasjonselement.class, 'organisasjonsid', 'organisasjonsid')
        resource.leder.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn-leder')
    }

    def "given arbeidsforhold with invalid administrative unit return personalmapperesource with leaders leader"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid-2'])

        then:
        resource.navn.fornavn == 'fornavn'
        resource.navn.mellomnavn == 'mellomnavn'
        resource.navn.etternavn == 'etternavn'
        //resource.part.size() == 1
        resource.tittel == 'DUMMY'
        resource.person.first() == Link.with(Person.class, 'fodselsnummer', 'fodselsnummer')
        resource.personalressurs.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn')
        resource.arbeidssted.first() == Link.with(Organisasjonselement.class, 'organisasjonsid', 'organisasjonsid-leder-leder')
        resource.leder.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn-leder-leder')
    }

    def "given arbeidsforhold with invalid leader return personalmapperesource with leaders leader"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.navn.fornavn == 'fornavn'
        resource.navn.mellomnavn == 'mellomnavn'
        resource.navn.etternavn == 'etternavn'
        //resource.part.size() == 1
        resource.tittel == 'DUMMY'
        resource.person.first() == Link.with(Person.class, 'fodselsnummer', 'fodselsnummer')
        resource.personalressurs.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn')
        resource.arbeidssted.first() == Link.with(Organisasjonselement.class, 'organisasjonsid', 'organisasjonsid-leder-leder')
        resource.leder.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn-leder-leder')
    }

    def "given arbeidsforhold with invalid personalressurskategori return null"() {
        given:
        OrganisationProperties organisationProperties = new OrganisationProperties(personnelResourceCategory: ['S', 'T'])
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder', 'brukernavn-leder-leder', 'organisasjonsid', 'X', true, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))


        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.tittel == null
    }

    def "given arbeidsforhold with invalid hovedstilling return null"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder', 'brukernavn-leder-leder', 'organisasjonsid', 'F', false, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.tittel == null
    }

    def "given arbeidsforhold with invalid gyldighetsperiode return null"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(20))

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.tittel == null
    }

    def "given two valid arbeidsforhold return personalmapperesource with earliest start date"() {
        given:
        def personalressurs = getPersonalressurs('brukernavn', 'brukernavn-leder-1', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(20))
        personalressurs.arbeidsforhold.push(getPersonalressurs('brukernavn', 'brukernavn-leder-2', 'brukernavn-leder-leder', 'organisasjonsid', 'F', true, LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(20)).arbeidsforhold.first())

        when:
        def resource = personalmappeResourceFactory.toPersonalmappeResource(personalressurs, organisationProperties, ['organisasjonsid'])

        then:
        resource.leder.first() == Link.with(Personalressurs.class, 'brukernavn', 'brukernavn-leder-1')
    }

    GraphQLPersonalmappe.Personalressurs getPersonalressurs(String brukernavn, String brukernavnLeder, String brukernavnLederLeder, String organisasjonsId,
                                                            String personalressurskategori, boolean hovedstilling, LocalDateTime start, LocalDateTime slutt) {
        return new GraphQLPersonalmappe.Personalressurs(
                arbeidsforhold: [new GraphQLPersonalmappe.Arbeidsforhold(
                        arbeidssted: new GraphQLPersonalmappe.Organisasjonselement(
                                organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: organisasjonsId),
                                leder: new GraphQLPersonalmappe.Personalressurs(
                                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                                identifikatorverdi: brukernavnLeder)),
                                overordnet: new GraphQLPersonalmappe.Organisasjonselement(
                                        organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                                identifikatorverdi: 'organisasjonsid-leder-leder'),
                                        leder: new GraphQLPersonalmappe.Personalressurs(
                                                brukernavn: new GraphQLPersonalmappe.Identifikator(
                                                        identifikatorverdi: brukernavnLederLeder)))),
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
                )]
        )
    }
}