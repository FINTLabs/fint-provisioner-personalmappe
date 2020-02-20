package no.fint.personalmappe;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalressursResources;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.service.ProvisionService;
import no.fint.personalmappe.utilities.NINUtilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class TestController {

    private final ProvisionService provisionService;
    private final MongoDBRepository mongoDBRepository;
    private final FintRepository fintRepository;

    @Value("${fint.endpoints.personalressurs}")
    private URI personalressursEndpoint;

    public TestController(ProvisionService provisionService, MongoDBRepository mongoDBRepository, FintRepository fintRepository) {
        this.provisionService = provisionService;
        this.mongoDBRepository = mongoDBRepository;
        this.fintRepository = fintRepository;
    }

    @GetMapping("/{orgId}/provision/{limit}")
    public void getPersonalmappeResource(@PathVariable String orgId, @PathVariable int limit) {
        provisionService.provisionByOrgId(
                orgId,
                limit,
                fintRepository.get(orgId, PersonalressursResources.class, personalressursEndpoint)
        );
    }

    @GetMapping("/{orgId}/{username}")
    public ProvisionService.PersonalmappeResourceWithUsername getPersonalmappeResource(@PathVariable String orgId, @PathVariable String username) {
        return provisionService.getPersonalmappeResource(orgId, username);
    }

    @PostMapping("/{orgId}/{username}")
    public ResponseEntity<?> postPersonalmappeResource(@PathVariable String orgId, @PathVariable String username) throws InterruptedException {
        ProvisionService.PersonalmappeResourceWithUsername personalmappeResource = getPersonalmappeResource(orgId, username);

        if (personalmappeResource == null) {
            return ResponseEntity.notFound().build();
        }

        provisionService.provision(orgId, username, personalmappeResource.getPersonalmappeResource());
        TimeUnit.SECONDS.sleep(1);

        return mongoDBRepository.findById(orgId + "_" + NINUtilities.getNIN(personalmappeResource.getPersonalmappeResource()))
                .map(resource -> ResponseEntity.status(resource.getStatus()).body(resource.getMessage()))
                .orElse(ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build());
    }

    @PostMapping("/post")
    public ResponseEntity<?> dummyPost() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("http://localhost:8080/tjenester/personalmappe/status")).build();
    }

    @PutMapping("/put")
    public ResponseEntity<?> dummyPut() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("http://localhost:8080/tjenester/personalmappe/status")).build();
    }

    @GetMapping("/status")
    public ResponseEntity<?> dummyStatus() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("http://localhost:8080/tjenester/personalmappe/put")).build();
    }
}
