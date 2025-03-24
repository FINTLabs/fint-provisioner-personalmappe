package no.novari.personalmappe.policy.editor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolicyModel {
    private String json;
    private String js;
    private PersonalmappeResource result;
}
