package no.fint.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;

@Slf4j
@Service
public class SchedulingService {

    @Value("${fint.endpoints.personalressurs}")
    private URI personalressursEndpoint;

    private final OrganisationProperties organisationProperties;
    private final FintRepository fintRepository;
    private final ProvisionService provisionService;

    public SchedulingService(OrganisationProperties organisationProperties, FintRepository fintRepository, ProvisionService provisionService) {
        this.organisationProperties = organisationProperties;
        this.fintRepository = fintRepository;
        this.provisionService = provisionService;
    }

    @Scheduled(cron = "${fint.cron.bulk}")
    public void bulk() {
        organisationProperties.getOrganisations().keySet()
                .forEach(orgId -> {
                    OrganisationProperties.Organisation props = organisationProperties.getOrganisations().get(orgId);
                    if (props.isBulk()) {
                        log.trace("Bulking personalmapper for {}", orgId);
                        provisionService.provisionByOrgId(orgId, props.getBulkLimit(), fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint));
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
                        provisionService.provisionByOrgId(orgId, 0, fintRepository.getUpdates(orgId, PersonalressursResources.class, personalressursEndpoint));
                    } else {
                        log.trace("Delta is disabled for {}", orgId);
                    }
                });
    }
}
