package no.fint.personalmappe;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.service.ProvisionService;
import no.fint.personalmappe.utilities.NINUtilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController("/api")
public class ApiController {

    @Value("${fint.endpoints.personalressurs}")
    private URI personalressursEndpoint;

    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;
    private final ProvisionService provisionService;
    private final FintRepository fintRepository;

    public ApiController(MongoDBRepository mongoDBRepository,
                         OrganisationProperties organisationProperties,
                         ProvisionService provisionService,
                         FintRepository fintRepository) {
        this.mongoDBRepository = mongoDBRepository;
        this.organisationProperties = organisationProperties;
        this.provisionService = provisionService;
        this.fintRepository = fintRepository;
    }

    @GetMapping("/provisioning/state")
    public ResponseEntity<List<MongoDBPersonalmappe>> getProvisioningState(@RequestHeader("x-orgid") String orgId) {
        return ResponseEntity.ok(
                mongoDBRepository
                        .findAll(Sort.by(Sort.Direction.DESC, "lastModifiedDate"))
                        .stream()
                        .filter(pm -> pm.getOrgId().equals(orgId))
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/provisioning/organisation")
    public ResponseEntity<List<String>> getProvisioningOrganisations() {
        return ResponseEntity.ok(new ArrayList<>(organisationProperties.getOrganisations().keySet()));
    }

    public PersonalmappeResource getPersonalmappeResourceAbleToBeProvisioned(String orgId, String username) {
        return provisionService.getPersonalmappeResource(orgId, username);
    }

    @PostMapping("/provisioning/username/{username}")
    public ResponseEntity<?> provisionPersonalmappeByUsername(@RequestHeader("x-orgid") String orgId, @PathVariable String username) throws InterruptedException {
        PersonalmappeResource personalmappeResource = getPersonalmappeResourceAbleToBeProvisioned(orgId, username);

        if (personalmappeResource == null) {
            return ResponseEntity.notFound().build();
        }

        provisionService.provision(orgId, personalmappeResource);
        TimeUnit.SECONDS.sleep(1);

        return mongoDBRepository.findById(orgId + "_" + NINUtilities.getNIN(personalmappeResource))
                .map(resource -> ResponseEntity.status(resource.getStatus()).body(resource.getMessage()))
                .orElse(ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build());
    }

    @PostMapping("/provisioning/limit/{limit}")
    public void provisionLimitedNumberOfPersonalmapper(@RequestHeader("x-orgid") String orgId, @PathVariable int limit) {
        provisionService.provisionByOrgId(
                orgId,
                limit,
                fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint)
        );
    }


}
