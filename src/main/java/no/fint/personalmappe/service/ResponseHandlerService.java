package no.fint.personalmappe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoBulkWriteException;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResources;
import no.fint.personalmappe.exception.FinalStatusPendingException;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.repository.MongoDBRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
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

    @Value("${fint.status-pending.fixed-backoff:5}")
    private long fixedBackoff;

    @Value("${fint.status-pending.max-retry:2}")
    private long maxRetries;

    private final MongoDBRepository mongoDBRepository;
    public Retry<?> finalStatusPending;

    @PostConstruct
    public void init() {
        finalStatusPending = Retry.anyOf(FinalStatusPendingException.class)
                .fixedBackoff(Duration.ofSeconds(fixedBackoff))
                .retryMax(maxRetries)
                .doOnRetry(exception -> log.info("{}", exception));
    }

    public ResponseHandlerService(MongoDBRepository mongoDBRepository) {
        this.mongoDBRepository = mongoDBRepository;
    }

    private MongoDBPersonalmappe save(MongoDBPersonalmappe mongoDBPersonalmappe) {
        try {
            return mongoDBRepository.save(mongoDBPersonalmappe);
        } catch (OptimisticLockingFailureException | MongoBulkWriteException e) {
            log.error("{} -> {}", e.getMessage(), mongoDBPersonalmappe);
        }
        return mongoDBPersonalmappe;
    }

    public MongoDBPersonalmappe handleStatusOnNew(String orgId, String id, String username, ResponseEntity<Void> status) {
        return save(MongoDBPersonalmappe.builder()
                .id(id)
                .username(username)
                .orgId(orgId)
                .status(HttpStatus.ACCEPTED)
                .association(status.getHeaders().getLocation())
                .build());
    }

    public void handleStatus(MongoDBPersonalmappe mongoDBPersonalmappe, ResponseEntity<Void> status) {
        mongoDBPersonalmappe.setStatus(HttpStatus.ACCEPTED);
        mongoDBPersonalmappe.setAssociation(status.getHeaders().getLocation());
        save(mongoDBPersonalmappe);
    }

    public void handleResource(ResponseEntity<Object> getForResource, MongoDBPersonalmappe mongoDBPersonalmappe) {
        if (getForResource.getStatusCode().is3xxRedirection()) {
              mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
              mongoDBPersonalmappe.setAssociation(getForResource.getHeaders().getLocation());
              save(mongoDBPersonalmappe);
        } else {
            throw new FinalStatusPendingException();
        }
    }

    public void handleError(WebClientResponseException response, MongoDBPersonalmappe mongoDBPersonalmappe) {
        switch (response.getStatusCode()) {
            case CONFLICT:
                PersonalmappeResources personalmappeResources = new PersonalmappeResources();
                try {
                    personalmappeResources = new ObjectMapper().readValue(response.getResponseBodyAsString(), PersonalmappeResources.class);
                } catch (JsonProcessingException e) {
                    log.error("{}", e.getMessage());
                }

                if (personalmappeResources.getTotalItems() == 1) {
                    mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
                    mongoDBPersonalmappe.setAssociation(getSelfLink(personalmappeResources.getContent().get(0)));
                    save(mongoDBPersonalmappe);
                } else {
                    mongoDBPersonalmappe.setStatus(HttpStatus.CONFLICT);
                    mongoDBPersonalmappe.setMessage("More than one personalmappe in conflict");
                    save(mongoDBPersonalmappe);
                }
                break;
            case BAD_REQUEST:
                mongoDBPersonalmappe.setStatus(HttpStatus.BAD_REQUEST);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                save(mongoDBPersonalmappe);
                break;
            case INTERNAL_SERVER_ERROR:
                mongoDBPersonalmappe.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                save(mongoDBPersonalmappe);
                break;
            case GONE:
                mongoDBPersonalmappe.setStatus(HttpStatus.GONE);
                save(mongoDBPersonalmappe);
                break;
            default:
                log.error("{} {}", response.getStatusCode(), response.getResponseBodyAsString());
                break;
        }
    }

    private URI getSelfLink(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getSelfLinks().stream()
                .map(Link::getHref)
                .map(URI::create)
                .findAny()
                .orElse(null);
    }
}
