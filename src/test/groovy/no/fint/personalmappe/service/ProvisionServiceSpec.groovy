package no.fint.personalmappe.service

import no.fint.model.felles.kompleksedatatyper.Personnavn
import no.fint.model.resource.Link
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.model.GraphQLPersonalmappe
import no.fint.personalmappe.properties.OrganisationProperties
import no.fint.personalmappe.repository.FintRepository
import no.fint.personalmappe.repository.MongoDBRepository
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDateTime

class ProvisionServiceSpec extends Specification {
    FintRepository fintRepository
    MongoDBRepository mongoDBRepository
    ResponseHandlerService responseHandlerService
    OrganisationProperties organisationProperties
    ProvisionService provisionService
    PolicyService policyService

    void setup() {
        fintRepository = Mock()
        mongoDBRepository = Mock()
        responseHandlerService = Mock()
        organisationProperties = Mock()
        policyService = Mock()

        provisionService = new ProvisionService(fintRepository, organisationProperties, mongoDBRepository, responseHandlerService, policyService)
    }

    def "getPersonalmappeResources() returns list of personalmappeResource"() {
        when:
        def resources = provisionService.getPersonalmappeResource(_ as String, _ as String)

        then:
        1 * fintRepository.post(_ as String, GraphQLPersonalmappe.class, _, _) >> Mono.just(getGraphQLPersonalmappe())
    }

    def "isActive() returns true when now is within gyldighetsperiode"() {
        when:
        def valid = provisionService.isActive(LocalDateTime.now()).test(getGraphQLPersonalmappeArbeidsforhold())

        then:
        valid
    }

    def "isHovedstilling() returns true when hovedstilling is true"() {
        when:
        def valid = provisionService.isHovedstilling().test(getGraphQLPersonalmappeArbeidsforhold())

        then:
        valid
    }

    def "hasPersonalressurskategori() returns true when personalressurskategori is present"() {
        when:
        def valid = provisionService.hasPersonalressurskategori(_ as String).test(getGraphQLPersonalmappeArbeidsforhold())

        then:
        1 * organisationProperties.getOrganisations() >> [(_ as String): new OrganisationProperties.Organisation(
                username: _ as String, password: _ as String, registration: _ as String, personalressurskategori: ['F'])]
        valid
    }

    def "hasMandatoryFieldsAndRelations() returns true when all mandatory fields and relations are present"() {
        when:
        def valid = provisionService.hasMandatoryFieldsAndRelations().test(getPersonalmappeResource())

        then:
        valid
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
