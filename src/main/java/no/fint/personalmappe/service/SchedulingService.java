package no.fint.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.personalmappe.properties.OrganisationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SchedulingService {
    private final OrganisationProperties organisationProperties;
    private final ProvisionService provisionService;

    public SchedulingService(OrganisationProperties organisationProperties, ProvisionService provisionService) {
        this.organisationProperties = organisationProperties;
        this.provisionService = provisionService;
    }

    @Scheduled(cron = "${fint.cron.bulk}")
    public void bulk() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    provisionService.getAdministrativeEnheter().get(orgId).clear();
                    OrganisationProperties.Organisation props = organisationProperties.getOrganisations().get(orgId);
                    if (props.isBulk()) {
                        log.trace("Bulk personalmapper for {}", orgId);
                        provisionService.bulkProvisionByOrgId(orgId, props.getBulkLimit());
                    } else {
                        log.trace("Bulk is disabled for {}", orgId);
                    }
                });
    }

    @Scheduled(cron = "${fint.cron.delta}")
    public void delta() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    OrganisationProperties.Organisation props = organisationProperties.getOrganisations().get(orgId);
                    if (props.isDelta()) {
                        log.trace("Delta personalmapper for {}", orgId);
                        provisionService.deltaProvisionByOrgId(orgId);
                    } else {
                        log.trace("Delta is disabled for {}", orgId);
                    }
                });
    }
}
