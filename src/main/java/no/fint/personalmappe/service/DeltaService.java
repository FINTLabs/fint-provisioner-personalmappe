package no.fint.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
public class DeltaService {
    @Value("${fint.endpoints.personnel-resource}")
    private URI personnelResourceEndpoint;

    private final ProvisionService provisionService;
    private final FintRepository fintRepository;
    private final OrganisationProperties organisationProperties;

    public DeltaService(ProvisionService provisionService, FintRepository fintRepository, OrganisationProperties organisationProperties) {
        this.provisionService = provisionService;
        this.fintRepository = fintRepository;
        this.organisationProperties = organisationProperties;
    }

    @Scheduled(cron = "${fint.cron.delta}")
    public void run() {
        if (organisationProperties.isDelta()) {
            delta();
        }
    }

    public void delta() {
        if (provisionService.getAdministrativeUnitSystemIds().isEmpty()) {
            provisionService.updateAdministrativeUnitSystemIds();
        }

        List<PersonalressursResource> personnelResources = fintRepository.getUpdates(PersonalressursResources.class, personnelResourceEndpoint)
                .flatMapIterable(PersonalressursResources::getContent)
                .collectList()
                .blockOptional()
                .orElseThrow(IllegalArgumentException::new);

        List<String> usernames = provisionService.getUsernames(personnelResources);

        log.info("Delta provision {} users", usernames.size());

        provisionService.run(usernames, usernames.size()).subscribe(log::trace);
    }
}