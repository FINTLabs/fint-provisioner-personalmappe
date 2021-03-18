package no.fint.personalmappe.service


import no.fint.model.resource.arkiv.personal.PersonalmappeResource
import no.fint.personalmappe.TestFactory
import spock.lang.Specification

class PolicyServiceSpec extends Specification {

    String simplePolicy = "function simplePolicy(object) {return object;}"
    PersonalmappeResource personalmappeResource
    PolicyService service

    void setup() {
        personalmappeResource = TestFactory.createPersonalmappeResource()
        service = new PolicyService()
        service.init()
    }

    def "Simple policy should return unmodified object"() {
        when:
        def result = service.transform(simplePolicy, personalmappeResource)

        then:
        result
        result.mappeId.identifikatorverdi == "2020/1"
    }

    def "Title policy should return object with modified offentligTittel"() {
        given:
        def titlePolicy = "function titlePolicy(object) {" +
                "object.setOffentligTittel('Test');" +
                "return object;" +
                "}"
        when:
        def result = service.transform(titlePolicy, personalmappeResource)

        then:
        result
        result.offentligTittel == "Test"

    }

    def "Saksstatus policy should return object with modified saksstatus"() {
        given:
        def saksstatusPolicy = "function titlePolicy(object) {" +
                "resource(object).link('saksstatus').id('kode').replaceValue('99');" +
                "return object;" +
                "}"
        when:
        def result = service.transform(saksstatusPolicy, personalmappeResource)

        then:
        result
        result.links['saksstatus'].stream().filter({ l -> l.getHref().endsWith("/99") }).count()
    }

    def "Get function name"() {
        when:
        def name = service.getFunctionName(simplePolicy)

        then:
        name == "simplePolicy"
    }

    def "If policy don't starts with keyword function exception should be thrown"() {
        given:
        def failedPolicy = "(o) { return o;}"

        when:
        service.validateFunctionSignature(failedPolicy)

        then:
        thrown(NoSuchMethodException)
    }

    def "If policy has multiple function parameters exception should be thrown"() {
        given:
        def failedPolicy = "function (o, r) { return o;}"

        when:
        service.validateFunctionSignature(failedPolicy)

        then:
        thrown(NoSuchMethodException)
    }
}
