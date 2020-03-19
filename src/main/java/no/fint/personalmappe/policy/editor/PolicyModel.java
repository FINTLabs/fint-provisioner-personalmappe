package no.fint.personalmappe.policy.editor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolicyModel {
    private String json;
    private String js;
    private PersonalmappeResource result;
}
