package no.fint.personalmappe.service

import no.fint.model.felles.kompleksedatatyper.Personnavn
import no.fint.model.resource.Link
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.factory.PersonalmappeResourceFactory
import no.fint.personalmappe.model.GraphQLPersonalmappe
import no.fint.personalmappe.properties.OrganisationProperties
import no.fint.personalmappe.repository.FintRepository
import no.fint.personalmappe.repository.MongoDBRepository
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDateTime

class ProvisionServiceSpec extends Specification {
    FintRepository fintRepository = Mock()
    MongoDBRepository mongoDBRepository = Mock()
    ResponseHandlerService responseHandlerService = Mock()
    OrganisationProperties organisationProperties = Mock()
    PolicyService policyService = Mock()
    PersonalmappeResourceFactory personalmappeResourceFactory = Mock()

    ProvisionService provisionService = new ProvisionService(fintRepository, organisationProperties, mongoDBRepository, responseHandlerService, personalmappeResourceFactory, policyService)

    def "getPersonalmappeResources() returns list of personalmappeResource"() {
        when:
        def resources = provisionService.getPersonalmappeResource(_ as String, _ as String)

        then:
        1 * fintRepository.post(_ as String, GraphQLPersonalmappe.class, _, _) >> Mono.just(getGraphQLPersonalmappe())
    }

    GraphQLPersonalmappe getGraphQLPersonalmappe() {
        return new GraphQLPersonalmappe(
                result: new GraphQLPersonalmappe.Result(
                        personalressurs: new GraphQLPersonalmappe.Personalressurs(
                                arbeidsforhold: [getGraphQLPersonalmappeArbeidsforhold()]
                        )
                )
        )
    }

    PersonalmappeResource getPersonalmappeResource() {
        PersonalmappeResource personalmappeResource = new PersonalmappeResource()
        personalmappeResource.setNavn(new Personnavn())
        personalmappeResource.addPerson(Link.with('link.to.person'))
        personalmappeResource.addPersonalressurs(Link.with('link.to.personalressurs'))
        personalmappeResource.addArbeidssted(Link.with('link.to.arbeidssted'))
        personalmappeResource.addLeder(Link.with('link.to.leder'))
        return personalmappeResource
    }

    GraphQLPersonalmappe.Arbeidsforhold getGraphQLPersonalmappeArbeidsforhold() {
        return new GraphQLPersonalmappe.Arbeidsforhold(
                arbeidssted: new GraphQLPersonalmappe.Organisasjonselement(
                        organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: '111'),
                        leder: new GraphQLPersonalmappe.Personalressurs(
                                brukernavn: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '222')),
                        overordnet: new GraphQLPersonalmappe.Organisasjonselement(
                                organisasjonsId: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '333'),
                                leder: new GraphQLPersonalmappe.Personalressurs(
                                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                                identifikatorverdi: '444')))),
                personalressurs: new GraphQLPersonalmappe.Personalressurs(
                        brukernavn: new GraphQLPersonalmappe.Identifikator(
                                identifikatorverdi: '555'),
                        person: new GraphQLPersonalmappe.Person(
                                fodselsnummer: new GraphQLPersonalmappe.Identifikator(
                                        identifikatorverdi: '666'),
                                navn: new GraphQLPersonalmappe.Navn(
                                        fornavn: 'a',
                                        mellomnavn: 'b',
                                        etternavn: 'c'
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
