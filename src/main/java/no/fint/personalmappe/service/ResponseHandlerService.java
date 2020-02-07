package no.fint.personalmappe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResources;
import no.fint.personalmappe.exception.FinalStatusPendingException;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.repository.MongoDBRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.retry.Retry;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Service
public class ResponseHandlerService {

    private final MongoDBRepository mongoDBRepository;

    public ResponseHandlerService(MongoDBRepository mongoDBRepository) {
        this.mongoDBRepository = mongoDBRepository;
    }

    public void handleStatus(String orgId, String id, ResponseEntity<Void> status) {
        mongoDBRepository.save(MongoDBPersonalmappe.builder()
                .id(id)
                .orgId(orgId)
                .status(HttpStatus.ACCEPTED)
                .association(status.getHeaders().getLocation())
                .build());
    }

    public void handleResource(ResponseEntity<Object> getForResource, String orgId, String id) {
        if (getForResource.getStatusCode().is3xxRedirection()) {
            mongoDBRepository.save(MongoDBPersonalmappe.builder()
                    .id(id)
                    .orgId(orgId)
                    .status(HttpStatus.CREATED)
                    .association(getForResource.getHeaders().getLocation())
                    .build());
        } else {
            throw new FinalStatusPendingException();
        }
    }

    public void handleError(WebClientResponseException response, String orgId, String id) {
        switch (response.getStatusCode()) {
            case CONFLICT:
                PersonalmappeResources personalmappeResources = new PersonalmappeResources();
                try {
                    personalmappeResources = new ObjectMapper().readValue(response.getResponseBodyAsString(), PersonalmappeResources.class);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                if (personalmappeResources.getTotalItems() == 1) {
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .association(getSelfLink(personalmappeResources.getContent().get(0)))
                            .status(HttpStatus.CREATED)
                            .build());
                } else {
                    mongoDBRepository.save(MongoDBPersonalmappe.builder()
                            .id(id)
                            .orgId(orgId)
                            .status(HttpStatus.CONFLICT)
                            .message("More than one personalmappe in conflict")
                            .build());
                }
                break;
            case BAD_REQUEST:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.BAD_REQUEST)
                        .message(response.getResponseBodyAsString())
                        .build());
                break;
            case INTERNAL_SERVER_ERROR:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message(response.getResponseBodyAsString())
                        .build());
                break;
            case GONE:
                mongoDBRepository.save(MongoDBPersonalmappe.builder()
                        .id(id)
                        .orgId(orgId)
                        .status(HttpStatus.GONE)
                        .build());
                break;
            default:
                log.error("{} {}", response.getStatusCode(), response.getResponseBodyAsString());
                break;
        }
    }

    /*
    TODO - set reasonable values
     */
    public Retry<?> finalStatusPending = Retry.anyOf(FinalStatusPendingException.class)
            .fixedBackoff(Duration.ofSeconds(5))
            .retryMax(2)
            .doOnRetry(exception -> log.info("{}", exception));

    private URI getSelfLink(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getSelfLinks().stream()
                .map(Link::getHref)
                .map(URI::create)
                .findAny()
                .orElse(null);
    }
}
