package no.fint.personalmappe.factory

import no.fint.model.administrasjon.organisasjon.Organisasjonselement
import no.fint.model.administrasjon.personal.Personalressurs
import no.fint.model.resource.Link
import no.fint.personalmappe.model.GraphQLPersonalmappe
import spock.lang.Specification

import java.time.LocalDateTime

class PersonalmappeResourceFactorySpec extends Specification {

    def "return PersonalmappeResource given GraphQLPersonalmappe"() {
        when:
        def resource = PersonalmappeResourceFactory.toPersonalmappeResource(getArbeidsforhold('brukernavn'))

        then:
        resource.getArbeidssted().first() == Link.with(Organisasjonselement.class, "organisasjonsid", "arbeidssted")
        resource.getLeder().first() == Link.with(Personalressurs.class, "brukernavn", "brukernavn-leder")
    }

    def "return PersonalmappeResource given GraphQLPersonalmappe and leders leder and arbeidssted if personalressurs is leder"() {
        when:
        def resource = PersonalmappeResourceFactory.toPersonalmappeResource(getArbeidsforhold('brukernavn-leder'))

        then:
        resource.getArbeidssted().first() == Link.with(Organisasjonselement.class, "organisasjonsid", "arbeidssted-leder")
        resource.getLeder().first() == Link.with(Personalressurs.class, "brukernavn", "brukernavn-leder-leder")
    }

    static GraphQLPersonalmappe.Arbeidsforhold getArbeidsforhold(String brukernavn) {
        return new GraphQLPersonalmappe.Arbeidsforhold(
                arbeidssted: new GraphQLPersonalmappe.Organisasjonselement(
                        organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: 'arbeidssted'),
                        leder: new GraphQLPersonalmappe.Personalressurs(
                                brukernavn: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: 'brukernavn-leder')),
                        overordnet: new GraphQLPersonalmappe.Organisasjonselement(
                                organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: 'arbeidssted-leder'),
                                leder: new GraphQLPersonalmappe.Personalressurs(
                                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                                identifikatorverdi: 'brukernavn-leder-leder')))),
                personalressurs: new GraphQLPersonalmappe.Personalressurs(
                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: brukernavn),
                        person: new GraphQLPersonalmappe.Person(
                                fodselsnummer: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: 'person'),
                                navn: new GraphQLPersonalmappe.Navn(
                                        fornavn: 'fornavn',
                                        mellomnavn: 'mellomnavn',
                                        etternavn: 'etternavn'
                                )
                        ),
                        personalressurskategori: new GraphQLPersonalmappe.Personalressurskategori(
                                kode: 'F'
                        )
                ),
                gyldighetsperiode: new GraphQLPersonalmappe.Periode(
                        start: LocalDateTime.now().minusDays(1),
                        slutt: LocalDateTime.now().plusDays(1)
                ),
                hovedstilling: true
        )
    }
}
