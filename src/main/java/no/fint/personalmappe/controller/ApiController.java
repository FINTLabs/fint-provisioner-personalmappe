package no.fint.personalmappe.controller;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.service.FileService;
import no.fint.personalmappe.service.ProvisionService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController("/api")
public class ApiController {
    private final MongoDBRepository mongoDBRepository;
    private final OrganisationProperties organisationProperties;
    private final ProvisionService provisionService;
    private final FileService fileService;

    public ApiController(MongoDBRepository mongoDBRepository, OrganisationProperties organisationProperties, ProvisionService provisionService, FileService fileService) {
        this.mongoDBRepository = mongoDBRepository;
        this.organisationProperties = organisationProperties;
        this.provisionService = provisionService;
        this.fileService = fileService;
    }

    @GetMapping("/provisioning/state")
    public ResponseEntity<List<MongoDBPersonalmappe>> getProvisioningState() {
        return ResponseEntity.ok(mongoDBRepository
                .findAll(Sort.by(Sort.Direction.DESC, "lastModifiedDate"))
                .stream()
                .filter(pm -> pm.getOrgId().equals(organisationProperties.getOrgId()) &&
                        pm.getLastModifiedDate().isAfter(LocalDateTime.now().minusDays(
                                Optional.of(organisationProperties.getHistoryLimit())
                                        .filter(limit -> limit > 0)
                                        .orElse(365))))
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/provisioning/download/{status}")
    public ResponseEntity<Resource> getFile(@PathVariable(value = "status") String status, @RequestParam String searchValue) {
        InputStream inputStream = fileService.getFile(organisationProperties.getOrgId(), status, searchValue);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=personalmapper.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/provisioning/username/{username}")
    public Mono<PersonalmappeResource> getPersonalmappeResourceAbleToBeProvisioned(@PathVariable String username) {
        return provisionService.getPersonalmappeResource(username);
    }

    @PostMapping("/provisioning/username/{username}")
    public void provisionPersonalmappeByUsername(@PathVariable String username) {
        provisionService.single(username);
    }

    @PostMapping("/provisioning/limit/{limit}")
    public void provisionLimitedNumberOfPersonalmapper(@PathVariable int limit) {
        provisionService.bulk(limit);
    }
}
