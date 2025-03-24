package no.novari.personalmappe.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.LocalDateTime;

@Data
@Builder
public class MongoDBPersonalmappe {

    @Id
    private String id;
    private String username;
    private String leader;
    private String workplace;
    private String orgId;
    private URI association;
    private HttpStatus status;
    private String message;

    @Version
    private long version;

    @CreatedDate
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}
