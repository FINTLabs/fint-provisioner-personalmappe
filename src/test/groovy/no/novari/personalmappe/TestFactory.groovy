package no.novari.personalmappe

import no.fint.model.felles.kompleksedatatyper.Identifikator
import no.fint.model.resource.Link
import no.fint.model.resource.arkiv.personal.PersonalmappeResource

class TestFactory {

    static PersonalmappeResource createPersonalmappeResource() {
        def personalmappeResource = new PersonalmappeResource(
                mappeId: new Identifikator(identifikatorverdi: "2020/1"),
                offentligTittel: "Personalmappe - Olsen Ole",
                systemId: new Identifikator(identifikatorverdi: "11"),
        )
        personalmappeResource.addSaksstatus(Link.with("https://alpha.felleskomponent.no/administrasjon/arkiv/saksstatus/systemid/B"))
        personalmappeResource.addSaksstatus(Link.with("https://alpha.felleskomponent.no/administrasjon/arkiv/saksstatus/kode/12"))

        return personalmappeResource
    }
}
