package no.fint.personalmappe.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.LocalDateTime;

@Data
@Builder
public class MongoDBPersonalmappe {

    @Id
    private String id;
    private String username;
    private String orgId;
    private URI association;
    private HttpStatus status;
    private String message;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}
