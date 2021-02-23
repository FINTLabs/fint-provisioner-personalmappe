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
        if (organisationProperties.isBulk()) {
            provisionService.bulk(organisationProperties.getBulkLimit());
        } else {
            log.info("Bulk is disabled");
        }
    }

    @Scheduled(cron = "${fint.cron.delta}")
    public void delta() {
        if (organisationProperties.isDelta()) {
            provisionService.delta();
        } else {
            log.info("Delta is disabled");
        }
    }
}
