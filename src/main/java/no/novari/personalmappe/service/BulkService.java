package no.novari.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.novari.personalmappe.properties.OrganisationProperties;
import no.novari.personalmappe.repository.FintRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
public class BulkService {
    @Value("${fint.endpoints.personnel-resource}")
    private URI personnelResourceEndpoint;

    private final ProvisionService provisionService;
    private final FintRepository fintRepository;
    private final OrganisationProperties organisationProperties;

    public BulkService(ProvisionService provisionService, FintRepository fintRepository, OrganisationProperties organisationProperties) {
        this.provisionService = provisionService;
        this.fintRepository = fintRepository;
        this.organisationProperties = organisationProperties;
    }

    @Scheduled(cron = "${fint.cron.bulk}")
    public void run() {
        if (organisationProperties.isBulk()) {
            bulk(organisationProperties.getBulkLimit());
        }
    }

    public void bulk(long bulkLimit) {
        provisionService.updateAdministrativeUnitSystemIds();

        fintRepository.get(PersonalressursResources.class, personnelResourceEndpoint)
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .subscribe(hardWorkers -> {
                    if (hardWorkers.isEmpty()) {
                        throw new IllegalArgumentException("No personalressurs found");
                    }

                    if (organisationProperties.isArchiveResource()) {
                        log.info("Updating Archive resources...");

                        provisionService.updateArchiveResource(hardWorkers);
                    }

                    List<String> usernames = provisionService.getUsernames(hardWorkers);

                    long limit = (bulkLimit == 0 ? usernames.size() : bulkLimit);
                    log.info("Bulk provision {} of {} users", limit, usernames.size());

                    provisionService.run(usernames, limit).subscribe(log::trace);
                });
    }
}
