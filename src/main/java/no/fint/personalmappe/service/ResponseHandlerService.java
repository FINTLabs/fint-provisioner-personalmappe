package no.fint.personalmappe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResources;
import no.fint.personalmappe.exception.FinalStatusPendingException;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.utilities.PersonnelUtilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.retry.Retry;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;

@Slf4j
@Service
public class ResponseHandlerService {
    @Value("${fint.status-pending.backoff:1}")
    private long backoff;

    @Value("${fint.status-pending.max-backoff:5}")
    private long maxBackoff;

    @Value("${fint.status-pending.timeout:30}")
    private long timeout;

    @Getter
    private Retry<?> finalStatusPending;

    @PostConstruct
    public void init() {
        finalStatusPending = Retry.anyOf(FinalStatusPendingException.class)
                .exponentialBackoff(Duration.ofSeconds(backoff), Duration.ofMinutes(maxBackoff))
                .timeout(Duration.ofMinutes(timeout))
                .doOnRetry(exception -> log.info("{}", exception));
    }

    public MongoDBPersonalmappe pendingHandler(String orgId, String id, PersonalmappeResource personalmappeResource) {
        return MongoDBPersonalmappe.builder()
                .id(id)
                .orgId(orgId)
                .username(PersonnelUtilities.getUsername(personalmappeResource))
                .leader(PersonnelUtilities.getLeader(personalmappeResource))
                .workplace(PersonnelUtilities.getWorkplace(personalmappeResource))
                .status(HttpStatus.ACCEPTED)
                .build();
    }

    public MongoDBPersonalmappe pendingHandler(MongoDBPersonalmappe mongoDBPersonalmappe, PersonalmappeResource personalmappeResource) {
        mongoDBPersonalmappe.setUsername(PersonnelUtilities.getUsername(personalmappeResource));
        mongoDBPersonalmappe.setLeader(PersonnelUtilities.getLeader(personalmappeResource));
        mongoDBPersonalmappe.setWorkplace(PersonnelUtilities.getWorkplace(personalmappeResource));
        mongoDBPersonalmappe.setStatus(HttpStatus.ACCEPTED);
        mongoDBPersonalmappe.setMessage(null);

        return mongoDBPersonalmappe;
    }

    public MongoDBPersonalmappe successHandler(MongoDBPersonalmappe mongoDBPersonalmappe, ResponseEntity<Object> responseEntity) {
        mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
        mongoDBPersonalmappe.setAssociation(responseEntity.getHeaders().getLocation());
        mongoDBPersonalmappe.setMessage(null);

        return mongoDBPersonalmappe;
    }

    public MongoDBPersonalmappe errorHandler(WebClientResponseException response, MongoDBPersonalmappe mongoDBPersonalmappe) {
        switch (response.getStatusCode()) {
            case CONFLICT:
                PersonalmappeResources personalmappeResources = new PersonalmappeResources();
                try {
                    personalmappeResources = new ObjectMapper().readValue(response.getResponseBodyAsString(), PersonalmappeResources.class);
                } catch (JsonProcessingException e) {
                    mongoDBPersonalmappe.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                    mongoDBPersonalmappe.setMessage(e.getMessage());
                }

                if (personalmappeResources.getTotalItems() == 1) {
                    mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
                    mongoDBPersonalmappe.setAssociation(getSelfLink(personalmappeResources.getContent().get(0)));
                    mongoDBPersonalmappe.setMessage(null);
                } else {
                    mongoDBPersonalmappe.setStatus(HttpStatus.CONFLICT);
                    mongoDBPersonalmappe.setMessage("More than one personalmappe in conflict");
                }

                break;
            case BAD_REQUEST:
                mongoDBPersonalmappe.setStatus(HttpStatus.BAD_REQUEST);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                break;
            case INTERNAL_SERVER_ERROR:
                mongoDBPersonalmappe.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                break;
            case GONE:
                mongoDBPersonalmappe.setStatus(HttpStatus.GONE);
                mongoDBPersonalmappe.setMessage(null);
                break;
            default:
                mongoDBPersonalmappe.setStatus(response.getStatusCode());
                mongoDBPersonalmappe.setMessage(response.getMessage());
                break;
        }

        return mongoDBPersonalmappe;
    }

    private URI getSelfLink(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getSelfLinks().stream()
                .map(Link::getHref)
                .map(URI::create)
                .findAny()
                .orElse(null);
    }
}