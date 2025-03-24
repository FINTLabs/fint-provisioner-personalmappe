package no.novari.personalmappe.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties("organisation")
public class OrganisationProperties {

    private String orgId;
    private String registration;
    private String username;
    private String password;
    private long bulkLimit;
    private int historyLimit;
    private boolean bulk;
    private boolean delta;
    private boolean retry;
    private boolean archiveResource;
    private String[] personnelResourceCategory;
    private String[] administrativeUnitsExcluded;
    private List<String> transformationScripts;
}
