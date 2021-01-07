package no.fint.personalmappe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
public class TestController {
    private int count = 0;

    @PostMapping("/post")
    public ResponseEntity<?> dummyPost() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("http://localhost:8080/status")).build();
    }

    @PutMapping("/put")
    public ResponseEntity<?> dummyPut() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("http://localhost:8080/status")).build();
    }

    @GetMapping("/status")
    public ResponseEntity<?> dummyStatus() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("http://localhost:8080/put")).build();
    }

    /*
    @GetMapping("/status")
    public ResponseEntity<?> dummyStatus() {
        count++;

        if (count < 7) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } else {
            count = 0;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).location(URI.create("http://localhost:8080/put")).build();
        }
    }

     */
}
