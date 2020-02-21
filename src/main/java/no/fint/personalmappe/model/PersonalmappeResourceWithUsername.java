package no.fint.personalmappe.model;

import lombok.Builder;
import lombok.Data;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;

@Data
@Builder
public class PersonalmappeResourceWithUsername {
    private String username;
    private PersonalmappeResource personalmappeResource;
}
