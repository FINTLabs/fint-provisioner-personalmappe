package no.fint.personalmappe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.fint.model.administrasjon.organisasjon.Organisasjonselement;
import no.fint.model.administrasjon.personal.Personalressurs;
import no.fint.model.felles.Person;
import no.fint.model.felles.kompleksedatatyper.Personnavn;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.GraphQLPersonalmappe;
import no.fint.personalmappe.model.GraphQLQuery;
import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.repository.FintRepository;
import no.fint.personalmappe.repository.MongoDBRepository;
import no.fint.personalmappe.utilities.PersonnelUtilities;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class ProvisionServiceTest {
    @Value("${fint.endpoints.personalmappe}")
    private URI personalmappeEndpoint;

    @Value("${fint.endpoints.graphql}")
    private URI graphqlEndpoint;

    @Autowired
    private ProvisionService provisionService;

    @Autowired
    private MongoDBRepository mongoDBRepository;

    @MockBean
    private FintRepository fintRepository;

    @Before
    public void setup() {
        provisionService.getAdministrativeEnheter().put("org-id", "organisasjonsid");

        mongoDBRepository.deleteAll();
    }

    @Test
    public void getPersonalmappeResource_ValidGraphQLPersonalmappe_ReturnPersonalmappeResourceMono() throws IOException {
        OrganisationProperties.Organisation organisation = new OrganisationProperties.Organisation();
        organisation.setPersonalressurskategori(new String[]{"F", "M"});
        organisation.setAdministrativeEnheter(new String[]{"123"});

        GraphQLPersonalmappe graphQLPersonalmappe = new ObjectMapper().findAndRegisterModules().readValue(getClass().getClassLoader().getResource("graphqlpersonalmappe.json"), GraphQLPersonalmappe.class);

        GraphQLQuery graphQLQuery = new GraphQLQuery(ProvisionService.GRAPHQL_QUERY, Collections.singletonMap("brukernavn", "brukernavn"));

        when(fintRepository.post("org-id", GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)).thenReturn(Mono.just(graphQLPersonalmappe));

        Mono<PersonalmappeResource> personalmappeResource = provisionService.getPersonalmappeResource("org-id", "brukernavn", organisation);

        StepVerifier
                .create(personalmappeResource)
                .expectNextMatches(resource -> resource.getNavn().getFornavn().equals("fornavn") &&
                        resource.getNavn().getMellomnavn().equals("mellomnavn") &&
                        resource.getNavn().getEtternavn().equals("etternavn") &&
                        resource.getPart().contains(new PartsinformasjonResource()) &&
                        resource.getTittel().equals("DUMMY") &&
                        resource.getPerson().contains(Link.with(Person.class, "fodselsnummer", "fodselsnummer")) &&
                        resource.getPersonalressurs().contains(Link.with(Personalressurs.class, "brukernavn", "brukernavn")) &&
                        resource.getArbeidssted().contains(Link.with(Organisasjonselement.class, "organisasjonsid", "organisasjonsid")) &&
                        resource.getLeder().contains(Link.with(Personalressurs.class, "brukernavn", "brukernavn-leder")))
                .verifyComplete();
    }

    @Test
    public void getPersonalmappeResource_InvalidLeaderGraphQLPersonalmappe_ReturnEmptyMono() throws IOException {
        OrganisationProperties.Organisation organisation = new OrganisationProperties.Organisation();
        organisation.setPersonalressurskategori(new String[]{"F", "M"});

        GraphQLPersonalmappe graphQLPersonalmappe = new ObjectMapper().findAndRegisterModules().readValue(getClass().getClassLoader().getResource("graphqlpersonalmappe.json"), GraphQLPersonalmappe.class);
        graphQLPersonalmappe.getResult().getPersonalressurs().getArbeidsforhold().forEach(arbeidsforhold -> {
            arbeidsforhold.getArbeidssted().getLeder().getBrukernavn().setIdentifikatorverdi("brukernavn");
            arbeidsforhold.getArbeidssted().getOverordnet().getLeder().getBrukernavn().setIdentifikatorverdi("brukernavn");
        });

        GraphQLQuery graphQLQuery = new GraphQLQuery(ProvisionService.GRAPHQL_QUERY, Collections.singletonMap("brukernavn", "brukernavn"));

        when(fintRepository.post("org-id", GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)).thenReturn(Mono.just(graphQLPersonalmappe));

        Mono<PersonalmappeResource> personalmappeResource = provisionService.getPersonalmappeResource("org-id", "brukernavn", organisation);

        StepVerifier
                .create(personalmappeResource)
                .verifyComplete();
    }

    @Test
    public void getPersonalmappeResource_InvalidAdministrativeUnitGraphQLPersonalmappe_ReturnEmptyMono() throws IOException {
        OrganisationProperties.Organisation organisation = new OrganisationProperties.Organisation();
        organisation.setPersonalressurskategori(new String[]{"F", "M"});
        organisation.setAdministrativeEnheter(new String[]{"3"});

        GraphQLPersonalmappe graphQLPersonalmappe = new ObjectMapper().findAndRegisterModules().readValue(getClass().getClassLoader().getResource("graphqlpersonalmappe.json"), GraphQLPersonalmappe.class);
        graphQLPersonalmappe.getResult().getPersonalressurs().getArbeidsforhold().forEach(arbeidsforhold -> {
            arbeidsforhold.getArbeidssted().getOrganisasjonsId().setIdentifikatorverdi("3");
            arbeidsforhold.getArbeidssted().getOverordnet().getOrganisasjonsId().setIdentifikatorverdi("3");
        });

        GraphQLQuery graphQLQuery = new GraphQLQuery(ProvisionService.GRAPHQL_QUERY, Collections.singletonMap("brukernavn", "brukernavn"));

        when(fintRepository.post("org-id", GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint)).thenReturn(Mono.just(graphQLPersonalmappe));

        Mono<PersonalmappeResource> personalmappeResource = provisionService.getPersonalmappeResource("org-id", "brukernavn", organisation);

        StepVerifier
                .create(personalmappeResource)
                .verifyComplete();
    }

    @Test
    public void getPersonalmappeResource_InvalidGraphQLPersonalmappe_ReturnEmptyMono() {
        OrganisationProperties.Organisation organisation = new OrganisationProperties.Organisation();
        organisation.setPersonalressurskategori(new String[]{"F", "M"});

        GraphQLQuery graphQLQuery = new GraphQLQuery(ProvisionService.GRAPHQL_QUERY, Collections.singletonMap("brukernavn", "brukernavn"));

        when(fintRepository.post("org-id", GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint))
                .thenReturn(Mono.just(new GraphQLPersonalmappe()));

        Mono<PersonalmappeResource> test = provisionService.getPersonalmappeResource("org-id", "brukernavn", organisation);

        StepVerifier
                .create(test)
                .verifyComplete();
    }

    @Test
    public void getPersonalmappeResource_ExceptionThrown_ReturnEmptyMono() {
        OrganisationProperties.Organisation organisation = new OrganisationProperties.Organisation();
        organisation.setPersonalressurskategori(new String[]{"F", "M"});

        GraphQLQuery graphQLQuery = new GraphQLQuery(ProvisionService.GRAPHQL_QUERY, Collections.singletonMap("brukernavn", "brukernavn"));

        when(fintRepository.post("org-id", GraphQLPersonalmappe.class, graphQLQuery, graphqlEndpoint))
                .thenReturn(Mono.error(new WebClientResponseException(HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE.name(), null, null, null)));

        Mono<PersonalmappeResource> test = provisionService.getPersonalmappeResource("org-id", "brukernavn", organisation);

        StepVerifier
                .create(test)
                .verifyComplete();
    }

    @Test
    public void provision_NewPersonalmappeResource_ResourceIsCreated() {
        PersonalmappeResource personalmappeResource = newPersonalmappeResource();
        String id = getId(personalmappeResource);

        when(fintRepository.postForEntity("org-id", personalmappeResource, personalmappeEndpoint))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("status-uri")).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.CREATED).location(URI.create("resource-uri")).build()));

        provisionService.provision("org-id", personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        Assert.assertTrue(mongoDBPersonalmappe.isPresent());
        Assert.assertEquals(mongoDBPersonalmappe.get().getId(), id);
        Assert.assertEquals(mongoDBPersonalmappe.get().getUsername(), "brukernavn");
        Assert.assertEquals(mongoDBPersonalmappe.get().getOrgId(), "org-id");
        Assert.assertEquals(mongoDBPersonalmappe.get().getAssociation(), URI.create("resource-uri"));
        Assert.assertEquals(mongoDBPersonalmappe.get().getStatus(), HttpStatus.CREATED);
        Assert.assertNotNull(mongoDBPersonalmappe.get().getCreatedDate());
        Assert.assertNotNull(mongoDBPersonalmappe.get().getLastModifiedDate());
    }

    @Test
    public void provision_ExisitingPersonalmappeResource_ResourceIsUpdated() {
        PersonalmappeResource personalmappeResource = newPersonalmappeResource();
        String id = getId(personalmappeResource);

        MongoDBPersonalmappe oldMongoDBPersonalmappe = newMongoDBPersonalmappe(id);
        mongoDBRepository.save(oldMongoDBPersonalmappe);

        when(fintRepository.putForEntity("org-id", personalmappeResource, URI.create("resource-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("status-uri")).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.CREATED).location(URI.create("resource-uri")).build()));

        provisionService.provision("org-id", personalmappeResource);

        Optional<MongoDBPersonalmappe> newMongoDBPersonalmappe = mongoDBRepository.findById(id);

        Assert.assertTrue(newMongoDBPersonalmappe.isPresent());
        Assert.assertEquals(newMongoDBPersonalmappe.get().getId(), id);
        Assert.assertEquals(newMongoDBPersonalmappe.get().getUsername(), "brukernavn");
        Assert.assertEquals(newMongoDBPersonalmappe.get().getOrgId(), "org-id");
        Assert.assertEquals(newMongoDBPersonalmappe.get().getAssociation(), URI.create("resource-uri"));
        Assert.assertEquals(newMongoDBPersonalmappe.get().getStatus(), HttpStatus.CREATED);
        Assert.assertNotNull(newMongoDBPersonalmappe.get().getCreatedDate());
        Assert.assertNotNull(newMongoDBPersonalmappe.get().getLastModifiedDate());
    }

    @Test
    public void provision_FailedPersonalmappeResource_ResourceIsCreated() {
        PersonalmappeResource personalmappeResource = newPersonalmappeResource();
        String id = getId(personalmappeResource);

        MongoDBPersonalmappe failedMongoDBPersonalmappe = newMongoDBPersonalmappe(id);
        failedMongoDBPersonalmappe.setAssociation(null);
        failedMongoDBPersonalmappe.setStatus(HttpStatus.GONE);
        mongoDBRepository.save(failedMongoDBPersonalmappe);

        when(fintRepository.postForEntity("org-id", personalmappeResource, personalmappeEndpoint))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("status-uri")).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.CREATED).location(URI.create("resource-uri")).build()));

        provisionService.provision("org-id", personalmappeResource);

        Optional<MongoDBPersonalmappe> newMongoDBPersonalmappe = mongoDBRepository.findById(id);

        Assert.assertTrue(newMongoDBPersonalmappe.isPresent());
        Assert.assertEquals(newMongoDBPersonalmappe.get().getId(), id);
        Assert.assertEquals(newMongoDBPersonalmappe.get().getUsername(), "brukernavn");
        Assert.assertEquals(newMongoDBPersonalmappe.get().getOrgId(), "org-id");
        Assert.assertEquals(newMongoDBPersonalmappe.get().getAssociation(), URI.create("resource-uri"));
        Assert.assertEquals(newMongoDBPersonalmappe.get().getStatus(), HttpStatus.CREATED);
        Assert.assertNotNull(newMongoDBPersonalmappe.get().getCreatedDate());
        Assert.assertNotNull(newMongoDBPersonalmappe.get().getLastModifiedDate());
    }

    @Test
    public void provision_ExceptionThrown_ResourceNotCreated() {
        PersonalmappeResource personalmappeResource = newPersonalmappeResource();
        String id = getId(personalmappeResource);

        when(fintRepository.postForEntity("org-id", personalmappeResource, personalmappeEndpoint))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("status-uri")).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build()));

        when(fintRepository.getForEntity("org-id", Object.class, URI.create("status-uri")))
                .thenReturn(Mono.error(new WebClientResponseException(HttpStatus.GONE.value(), HttpStatus.GONE.name(), null, null, null)));

        provisionService.provision("org-id", personalmappeResource);

        Optional<MongoDBPersonalmappe> mongoDBPersonalmappe = mongoDBRepository.findById(id);

        Assert.assertTrue(mongoDBPersonalmappe.isPresent());
        Assert.assertEquals(mongoDBPersonalmappe.get().getId(), id);
        Assert.assertEquals(mongoDBPersonalmappe.get().getUsername(), "brukernavn");
        Assert.assertEquals(mongoDBPersonalmappe.get().getOrgId(), "org-id");
        Assert.assertNull(mongoDBPersonalmappe.get().getAssociation());
        Assert.assertEquals(mongoDBPersonalmappe.get().getStatus(), HttpStatus.GONE);
        Assert.assertNotNull(mongoDBPersonalmappe.get().getCreatedDate());
        Assert.assertNotNull(mongoDBPersonalmappe.get().getLastModifiedDate());
    }

    private PersonalmappeResource newPersonalmappeResource() {
        PersonalmappeResource resource = new PersonalmappeResource();
        Personnavn personnavn = new Personnavn();
        personnavn.setFornavn("fornavn");
        personnavn.setMellomnavn("mellomnavn");
        personnavn.setEtternavn("etternavn");
        resource.setNavn(personnavn);
        resource.setPart(Collections.singletonList(new PartsinformasjonResource()));
        resource.setTittel("DUMMY");
        resource.addPerson(Link.with(Person.class, "fodselsnummer", "fodselsnummer"));
        resource.addPersonalressurs(Link.with(Personalressurs.class, "brukernavn", "brukernavn"));
        resource.addArbeidssted(Link.with(Organisasjonselement.class, "organisasjonsid", "organisajonsid"));
        resource.addLeder(Link.with(Personalressurs.class, "brukernavn", "brukernavn-leder"));
        return resource;
    }

    private String getId(PersonalmappeResource personalmappeResource) {
        return "org-id" + "_" + PersonnelUtilities.getNIN(personalmappeResource);
    }

    private MongoDBPersonalmappe newMongoDBPersonalmappe(String id) {
        return MongoDBPersonalmappe.builder()
                .id(id)
                .username("brukernavn")
                .orgId("org-id")
                .association(URI.create("resource-uri"))
                .status(HttpStatus.CREATED)
                .build();
    }
}
