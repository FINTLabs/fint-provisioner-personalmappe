package no.fint.personalmappe.policy.helper

import no.fint.model.resource.administrasjon.personal.PersonalmappeResource
import no.fint.personalmappe.TestFactory
import spock.lang.Specification

import static no.fint.personalmappe.policy.helper.LinkHelper.resource

class LinkHelperSpec extends Specification {

    PersonalmappeResource personalmappeResource;

    void setup() {
        personalmappeResource = TestFactory.createPersonalmappeResource()
    }

    def "Get identifikator value from link"() {
        when:
        def systemid = resource().apply(personalmappeResource).link("saksstatus").getValue()
        def kode = resource().apply(personalmappeResource).link("saksstatus").id("kode").getValue();

        then:
        systemid
        systemid == "B"
        kode
        kode == "12"
    }

    def "Replace identifikator value"() {
        when:
        def o = resource().apply(personalmappeResource).link("saksstatus")
        o.replaceValue("A")
        def value = o.getValue()

        then:
        value
        value == "A"
    }

    def "Systemid is not B should return false"() {
        given:
        def o = TestFactory.createPersonalmappeResource()

        when:
        def equals = resource().apply(o).link("saksstatus").isNot("B")

        then:
        !equals
    }

    def "Systemid is B should return true"() {
        given:
        def o = TestFactory.createPersonalmappeResource()

        when:
        def equals = resource().apply(o).link("saksstatus").is("B")

        then:
        equals
    }
}
