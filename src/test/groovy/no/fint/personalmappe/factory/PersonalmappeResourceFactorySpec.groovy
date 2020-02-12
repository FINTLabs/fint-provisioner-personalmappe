package no.fint.personalmappe.factory

import no.fint.personalmappe.model.GraphQLPersonalmappe
import spock.lang.Specification

class PersonalmappeResourceFactorySpec extends Specification {

    def "toPersonalmappeResource() returns PersonalmappeResource given GraphQLPersonalmappe"() {
        when:
        def resource = PersonalmappeResourceFactory.toPersonalmappeResource(getGraphQLPersonalmappeArbeidsforhold())

        then:
        resource.getNavn().fornavn == 'a'
    }

    GraphQLPersonalmappe.Arbeidsforhold getGraphQLPersonalmappeArbeidsforhold() {
        return new GraphQLPersonalmappe.Arbeidsforhold(
                arbeidssted: new GraphQLPersonalmappe.Organisasjonselement(
                        organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: '111'),
                        leder: new GraphQLPersonalmappe.Personalressurs(
                                brukernavn: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '222')
                        )),
                personalressurs: new GraphQLPersonalmappe.Personalressurs(
                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: '333'),
                        person: new GraphQLPersonalmappe.Person(
                                fodselsnummer: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '444'),
                                navn: new GraphQLPersonalmappe.Navn(
                                        fornavn: 'a',
                                        mellomnavn: 'b',
                                        etternavn: 'c'
                                )
                        )
                )
        )
    }
}
