package no.novari.personalmappe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.Link;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import no.novari.personalmappe.model.MongoDBPersonalmappe;
import no.novari.personalmappe.utilities.PersonnelUtilities;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
public class ResponseService {

    public MongoDBPersonalmappe pending(String orgId, String id, PersonalmappeResource personalmappeResource) {
        return MongoDBPersonalmappe.builder()
                .id(id)
                .orgId(orgId)
                .username(PersonnelUtilities.getUsername(personalmappeResource))
                .leader(PersonnelUtilities.getLeader(personalmappeResource))
                .workplace(PersonnelUtilities.getWorkplace(personalmappeResource))
                .status(HttpStatus.ACCEPTED)
                .build();
    }

    public MongoDBPersonalmappe pending(MongoDBPersonalmappe mongoDBPersonalmappe, PersonalmappeResource personalmappeResource) {
        mongoDBPersonalmappe.setUsername(PersonnelUtilities.getUsername(personalmappeResource));
        mongoDBPersonalmappe.setLeader(PersonnelUtilities.getLeader(personalmappeResource));
        mongoDBPersonalmappe.setWorkplace(PersonnelUtilities.getWorkplace(personalmappeResource));
        mongoDBPersonalmappe.setStatus(HttpStatus.ACCEPTED);
        mongoDBPersonalmappe.setMessage(null);

        return mongoDBPersonalmappe;
    }

    public MongoDBPersonalmappe success(MongoDBPersonalmappe mongoDBPersonalmappe, ResponseEntity<Object> responseEntity) {
        mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
        mongoDBPersonalmappe.setAssociation(responseEntity.getHeaders().getLocation());
        mongoDBPersonalmappe.setMessage(null);

        return mongoDBPersonalmappe;
    }

    public MongoDBPersonalmappe error(WebClientResponseException response, MongoDBPersonalmappe mongoDBPersonalmappe) {
        switch (response.getStatusCode()) {
            case CONFLICT:
                try {
                    PersonalmappeResource personalmappeResource = new ObjectMapper().readValue(response.getResponseBodyAsString(), PersonalmappeResource.class);
                    mongoDBPersonalmappe.setStatus(HttpStatus.CREATED);
                    mongoDBPersonalmappe.setAssociation(getSelfLink(personalmappeResource));
                    mongoDBPersonalmappe.setMessage(null);
                } catch (JsonProcessingException e) {
                    mongoDBPersonalmappe.setStatus(INTERNAL_SERVER_ERROR);
                    mongoDBPersonalmappe.setMessage(e.getMessage());
                }

                break;
            case BAD_REQUEST:
                mongoDBPersonalmappe.setStatus(BAD_REQUEST);
                mongoDBPersonalmappe.setAssociation(null);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                break;
            case INTERNAL_SERVER_ERROR:
                mongoDBPersonalmappe.setStatus(INTERNAL_SERVER_ERROR);
                mongoDBPersonalmappe.setMessage(response.getResponseBodyAsString());
                break;
            case GONE:
                mongoDBPersonalmappe.setStatus(HttpStatus.GONE);
                mongoDBPersonalmappe.setMessage(null);
                break;
            default:
                mongoDBPersonalmappe.setStatus((HttpStatus) response.getStatusCode());
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