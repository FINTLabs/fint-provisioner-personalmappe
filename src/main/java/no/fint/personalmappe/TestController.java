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
