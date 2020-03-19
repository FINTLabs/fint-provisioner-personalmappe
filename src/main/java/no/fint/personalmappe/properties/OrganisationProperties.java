package no.fint.personalmappe.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties("fint")
public class OrganisationProperties {

    private Map<String, Organisation> organisations = new HashMap<>();

    @Data
    public static class Organisation {
        private String username;
        private String password;
        private String registration;
        private int bulkLimit;
        private boolean bulk;
        private boolean delta;
        private List<String> personalressurskategori;
        private List<String> transformationScripts;
    }
}