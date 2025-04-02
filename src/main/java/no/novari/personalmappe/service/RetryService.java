package no.novari.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.novari.personalmappe.model.MongoDBPersonalmappe;
import no.novari.personalmappe.properties.OrganisationProperties;
import no.novari.personalmappe.repository.MongoDBRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RetryService {

    private final ProvisionService provisionService;
    private final OrganisationProperties organisationProperties;
    private final MongoDBRepository mongoDBRepository;

    public RetryService(ProvisionService provisionService, MongoDBRepository mongoDBRepository, OrganisationProperties organisationProperties) {
        this.provisionService = provisionService;
        this.mongoDBRepository = mongoDBRepository;
        this.organisationProperties = organisationProperties;
    }

    @Scheduled(cron = "${fint.cron.retry}")
    public void run() {
        if (organisationProperties.isRetry()) {
            retry();
        }
    }

    public void retry() {

        List<String> usernames = mongoDBRepository.findAll()
                .stream()
                .filter(documents ->
                        documents.getStatus().equals(HttpStatus.INTERNAL_SERVER_ERROR) &&
                                documents.getOrgId().equals(organisationProperties.getOrgId()))
                .map(MongoDBPersonalmappe::getUsername)
                .toList();

        log.info("As an extraordinary service from Arkivlaget, we're retrying provision of {} users. Cross your fingers.",
                usernames.size());

        provisionService.run(usernames, usernames.size())
                .subscribe(log::trace);
    }
}
