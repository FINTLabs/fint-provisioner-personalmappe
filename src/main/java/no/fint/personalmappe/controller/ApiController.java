package no.fint.personalmappe.controller;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.service.FileService;
import no.fint.personalmappe.service.ProvisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController("/api")
public class ApiController {
    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;
    private final ProvisionService provisionService;

    @Autowired
    private FileService fileService;

    public ApiController(MongoDBRepository mongoDBRepository, OrganisationProperties organisationProperties, ProvisionService provisionService, FileService fileService) {
        this.mongoDBRepository = mongoDBRepository;
        this.organisationProperties = organisationProperties;
        this.provisionService = provisionService;
    }

    @GetMapping("/provisioning/state")
    public ResponseEntity<List<MongoDBPersonalmappe>> getProvisioningState(@RequestHeader("x-orgid") String orgId) {
        return ResponseEntity.ok(mongoDBRepository
                .findAll(Sort.by(Sort.Direction.DESC, "lastModifiedDate"))
                .stream()
                .filter(pm -> pm.getOrgId().equals(orgId) &&
                        pm.getLastModifiedDate().isAfter(LocalDateTime.now().minusDays(5)))
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/provisioning/organisation")
    public ResponseEntity<List<String>> getProvisioningOrganisations() {
        return ResponseEntity.ok(new ArrayList<>(organisationProperties.getOrganisations().keySet()));
    }

    @GetMapping("/provisioning/download/{status}")
    public ResponseEntity<Resource> getFile(@RequestHeader("x-orgid") String orgId, @PathVariable(value = "status") String status, @RequestParam String searchValue) throws IOException {
        String filename = "personalmapper.xlsx";
        InputStreamResource file = new InputStreamResource(fileService.getFile(orgId, status, searchValue));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(file);
    }

    @GetMapping("/provisioning/username/{username}")
    public Mono<PersonalmappeResource> getPersonalmappeResourceAbleToBeProvisioned(@RequestHeader("x-orgid") String orgId, @PathVariable String username) {
        return provisionService.getPersonalmappeResource(orgId, username, organisationProperties.getOrganisations().get(orgId));
    }

    @PostMapping("/provisioning/username/{username}")
    public Mono<ResponseEntity<PersonalmappeResource>> provisionPersonalmappeByUsername(@RequestHeader("x-orgid") String orgId, @PathVariable String username) {
        return provisionService.getPersonalmappeResource(orgId, username, organisationProperties.getOrganisations().get(orgId))
                .map(personalmappeResource -> {
                    provisionService.provision(orgId, personalmappeResource);

                    return ResponseEntity.ok().body(personalmappeResource);
                }).switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/provisioning/limit/{limit}")
    public void provisionLimitedNumberOfPersonalmapper(@RequestHeader("x-orgid") String orgId, @PathVariable int limit) {
        provisionService.bulkProvisionByOrgId(orgId, limit);
    }
}
