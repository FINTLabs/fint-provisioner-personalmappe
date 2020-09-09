package no.fint.personalmappe.factory;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.administrasjon.organisasjon.Organisasjonselement;
import no.fint.model.administrasjon.personal.Personalressurs;
import no.fint.model.felles.Person;
import no.fint.model.felles.kompleksedatatyper.Personnavn;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.GraphQLPersonalmappe;
import no.fint.personalmappe.properties.OrganisationProperties;
import no.fint.personalmappe.utilities.PersonnelUtilities;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@Slf4j
@Component
public class PersonalmappeResourceFactory {
    private final OrganisationProperties organisationProperties;

    public PersonalmappeResourceFactory(OrganisationProperties organisationProperties) {
        this.organisationProperties = organisationProperties;
    }

    public Mono<PersonalmappeResource> toPersonalmappeResource(String orgId, GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold,
                                                               Collection<String> administrativEnheter) {
        if (validArbeidsforhold().test(orgId, arbeidsforhold)) {
            PersonalmappeResource resource = new PersonalmappeResource();

            getNavn(arbeidsforhold).ifPresent(resource::setNavn);

            getPerson(arbeidsforhold).map(Link.apply(Person.class, "fodselsnummer")).ifPresent(resource::addPerson);

            getPersonalressurs(arbeidsforhold).ifPresent(personalressurs -> {
                resource.addPersonalressurs(Link.with(Personalressurs.class, "brukernavn", personalressurs));

                getLeder(arbeidsforhold).ifPresent(leder -> {
                    Optional<String> arbeidssted = getArbeidssted(arbeidsforhold);

                    if (personalressurs.equalsIgnoreCase(leder) ||
                            !administrativEnheter.contains(arbeidssted.orElse(null))) {
                        getLedersLeder(arbeidsforhold).map(Link.apply(Personalressurs.class, "brukernavn")).ifPresent(resource::addLeder);
                        getLedersArbeidssted(arbeidsforhold).map(Link.apply(Organisasjonselement.class, "organisasjonsid")).ifPresent(resource::addArbeidssted);
                    } else {
                        resource.addLeder(Link.with(Personalressurs.class, "brukernavn", leder));
                        arbeidssted.map(Link.apply(Organisasjonselement.class, "organisasjonsid")).ifPresent(resource::addArbeidssted);
                    }
                });
            });

            resource.setPart(Collections.singletonList(new PartsinformasjonResource()));
            resource.setTittel("DUMMY");

            if (validPersonalmappeResource().test(resource)) {
                return Mono.just(resource);
            }
        }
        return Mono.empty();
    }

    private Optional<String> getPersonalressurs(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private Optional<String> getPerson(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                .map(GraphQLPersonalmappe.Personalressurs::getPerson)
                .map(GraphQLPersonalmappe.Person::getFodselsnummer)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private static Optional<Personnavn> getNavn(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                .map(GraphQLPersonalmappe.Personalressurs::getPerson)
                .map(GraphQLPersonalmappe.Person::getNavn)
                .map(navn -> {
                    Personnavn personnavn = new Personnavn();
                    personnavn.setFornavn(navn.getFornavn());
                    personnavn.setMellomnavn(navn.getMellomnavn());
                    personnavn.setEtternavn(navn.getEtternavn());
                    return personnavn;
                });
    }

    private static Optional<String> getLeder(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getLeder)
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private static Optional<String> getArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private static Optional<String> getLedersLeder(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getLeder)
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private static Optional<String> getLedersArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    public BiPredicate<String, GraphQLPersonalmappe.Arbeidsforhold> validArbeidsforhold() {
        return (orgId, arbeidsforhold) ->
                isActive().test(LocalDateTime.now(), arbeidsforhold) && isHovedstilling().test(arbeidsforhold) && hasPersonalressurskategori().test(orgId, arbeidsforhold);
    }

    private BiPredicate<LocalDateTime, GraphQLPersonalmappe.Arbeidsforhold> isActive() {
        return (now, arbeidsforhold) -> {
            if (arbeidsforhold == null || arbeidsforhold.getGyldighetsperiode() == null) return false;

            if (arbeidsforhold.getGyldighetsperiode().getSlutt() == null) {
                return now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            } else {
                return now.isBefore(arbeidsforhold.getGyldighetsperiode().getSlutt())
                        && now.isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            }
        };
    }

    private Predicate<GraphQLPersonalmappe.Arbeidsforhold> isHovedstilling() {
        return arbeidsforhold -> Optional.ofNullable(arbeidsforhold)
                .map(GraphQLPersonalmappe.Arbeidsforhold::getHovedstilling)
                .orElse(false);
    }

    private BiPredicate<String, GraphQLPersonalmappe.Arbeidsforhold> hasPersonalressurskategori() {
        return (orgId, arbeidsforhold) -> organisationProperties.getOrganisations().get(orgId).getPersonalressurskategori()
                .contains(Optional.ofNullable(arbeidsforhold)
                        .map(GraphQLPersonalmappe.Arbeidsforhold::getPersonalressurs)
                        .map(GraphQLPersonalmappe.Personalressurs::getPersonalressurskategori)
                        .map(GraphQLPersonalmappe.Personalressurskategori::getKode)
                        .orElse(null));
    }

    public Predicate<PersonalmappeResource> validPersonalmappeResource() {
        return personalmappeResource -> hasMandatoryFieldsAndRelations().test(personalmappeResource) &&
                hasValidLeader().test(personalmappeResource);
    }

    private Predicate<PersonalmappeResource> hasMandatoryFieldsAndRelations() {
        return personalmappeResource -> (!personalmappeResource.getPersonalressurs().isEmpty()
                && !personalmappeResource.getPerson().isEmpty()
                && !personalmappeResource.getArbeidssted().isEmpty()
                && !personalmappeResource.getLeder().isEmpty()
                && Objects.nonNull(personalmappeResource.getNavn()));
    }

    private Predicate<PersonalmappeResource> hasValidLeader() {
        return personalmappeResource -> {
            if (personalmappeResource.getPersonalressurs().contains(personalmappeResource.getLeder().stream().findAny().orElse(null))) {
                log.trace("Identical subject and leader for personalmappe: {}", PersonnelUtilities.getUsername(personalmappeResource));
                return false;
            }
            return true;
        };
    }
}
