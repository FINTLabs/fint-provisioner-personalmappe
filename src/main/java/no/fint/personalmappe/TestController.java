package no.fint.personalmappe;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.service.ProvisionService;
import no.fint.personalmappe.util.Util;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class TestController {

    private final ProvisionService provisionService;
    private final MongoDBRepository mongoDBRepository;

    public TestController(ProvisionService provisionService, MongoDBRepository mongoDBRepository) {
        this.provisionService = provisionService;
        this.mongoDBRepository = mongoDBRepository;
    }

    @GetMapping("/{orgId}/provision/{limit}")
    public void getPersonalmappeResource(@PathVariable String orgId, @PathVariable int limit) {
        ProvisionService.setLIMIT(limit);
        provisionService.full();
    }

    @GetMapping("/{orgId}/get/{username}")
    public List<PersonalmappeResource> getPersonalmappeResource(@PathVariable String orgId, @PathVariable String username) {
        return provisionService.getPersonalmappeResources(orgId, username);
    }

    @PostMapping("/{orgId}/post/{username}")
    public ResponseEntity<?> postPersonalmappeResource(@PathVariable String orgId, @PathVariable String username) throws InterruptedException {
        List<PersonalmappeResource> personalmappeResources = getPersonalmappeResource(orgId, username);

        if (personalmappeResources.size() == 1) {
            provisionService.provision(orgId, personalmappeResources.get(0));
            TimeUnit.SECONDS.sleep(1);

            return mongoDBRepository.findById(String.format("%s_%s", orgId, Util.getNIN(personalmappeResources.get(0))))
                    .map(resource -> ResponseEntity.status(resource.getStatus()).body(resource.getMessage()))
                    .orElse(ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build());
        }

        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{orgId}/put")
    public ResponseEntity<?> putPersonalmappeResource(@PathVariable String orgId, @RequestBody PersonalmappeResource personalmappeResource) throws InterruptedException {
        mongoDBRepository.findById(String.format("%s_%s", orgId, Util.getNIN(personalmappeResource)))
                .ifPresent(mongoDBPersonalmappe ->
                        provisionService.provision(orgId, personalmappeResource));
        TimeUnit.SECONDS.sleep(1);

        return mongoDBRepository.findById(String.format("%s_%s", orgId, Util.getNIN(personalmappeResource)))
                .map(resource -> ResponseEntity.status(resource.getStatus()).body(resource.getMessage()))
                .orElse(ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build());
    }
}
