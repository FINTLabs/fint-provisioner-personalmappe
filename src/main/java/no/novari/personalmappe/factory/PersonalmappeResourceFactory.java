package no.novari.personalmappe.factory;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.administrasjon.organisasjon.Organisasjonselement;
import no.fint.model.administrasjon.personal.Personalressurs;
import no.fint.model.felles.Person;
import no.fint.model.felles.kompleksedatatyper.Personnavn;
import no.fint.model.resource.Link;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import no.novari.personalmappe.model.GraphQLPersonalmappe;
import no.novari.personalmappe.properties.OrganisationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@Component
@Slf4j
public class PersonalmappeResourceFactory {

    public PersonalmappeResource toPersonalmappeResource(GraphQLPersonalmappe.Personalressurs personalressurs, OrganisationProperties organisationProperties, List<String> administrativEnheter) {

        PersonalmappeResource personalmappeResource = new PersonalmappeResource();

        Optional<GraphQLPersonalmappe.Arbeidsforhold> arbeidsforhold = Optional.ofNullable(personalressurs)
                .map(GraphQLPersonalmappe.Personalressurs::getArbeidsforhold)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(forhold -> validArbeidsforhold().test(forhold, organisationProperties.getPersonnelResourceCategory()))
                .min(Comparator.comparing(forhold -> Optional.ofNullable(forhold.getGyldighetsperiode())
                        .map(GraphQLPersonalmappe.Periode::getStart)
                        .orElse(LocalDateTime.MAX)));

        arbeidsforhold.ifPresent(forhold -> {
            getNavn(forhold).ifPresent(personalmappeResource::setNavn);
            getPerson(forhold).map(Link.apply(Person.class, "fodselsnummer")).ifPresent(personalmappeResource::addPerson);
            getPersonalressurs(forhold).map(Link.apply(Personalressurs.class, "brukernavn")).ifPresent(personalmappeResource::addPersonalressurs);

            getLeder(forhold).ifPresent(leder -> {
                if (getPersonalressurs(forhold).filter(leder::equalsIgnoreCase).isPresent() || !getArbeidssted(forhold).filter(administrativEnheter::contains).isPresent()) {
                    getLedersLeder(forhold).map(Link.apply(Personalressurs.class, "brukernavn")).ifPresent(personalmappeResource::addLeder);
                    getLedersArbeidssted(forhold).map(Link.apply(Organisasjonselement.class, "organisasjonsid")).ifPresent(personalmappeResource::addArbeidssted);
                } else {
                    personalmappeResource.addLeder(Link.with(Personalressurs.class, "brukernavn", leder));
                    getArbeidssted(forhold).map(Link.apply(Organisasjonselement.class, "organisasjonsid")).ifPresent(personalmappeResource::addArbeidssted);
                }
            });

            // TODO? personalmappeResource.setPart(Collections.singletonList(new PartResource()));
            personalmappeResource.setTittel("DUMMY");
        });

        return personalmappeResource;
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

    private Optional<Personnavn> getNavn(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
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

    private Optional<String> getLeder(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getLeder)
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private Optional<String> getArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private Optional<String> getLedersLeder(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getLeder)
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private Optional<String> getLedersArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private BiPredicate<GraphQLPersonalmappe.Arbeidsforhold, String[]> validArbeidsforhold() {
        return (arbeidsforhold, personalressurskategori) -> isActive().test(arbeidsforhold, LocalDateTime.now()) &&
                isHovedstilling().test(arbeidsforhold) && hasPersonalressurskategori().test(arbeidsforhold, personalressurskategori);
    }

    private BiPredicate<GraphQLPersonalmappe.Arbeidsforhold, LocalDateTime> isActive() {
        return (arbeidsforhold, now) -> {
            if (arbeidsforhold == null || arbeidsforhold.getGyldighetsperiode() == null) return false;

            if (arbeidsforhold.getGyldighetsperiode().getSlutt() == null) {
                return now.plusWeeks(2).isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            } else {
                return now.isBefore(arbeidsforhold.getGyldighetsperiode().getSlutt())
                        && now.plusWeeks(2).isAfter(arbeidsforhold.getGyldighetsperiode().getStart());
            }
        };
    }

    private Predicate<GraphQLPersonalmappe.Arbeidsforhold> isHovedstilling() {
        return arbeidsforhold -> Optional.ofNullable(arbeidsforhold)
                .map(GraphQLPersonalmappe.Arbeidsforhold::getHovedstilling)
                .orElse(false);
    }

    private BiPredicate<GraphQLPersonalmappe.Arbeidsforhold, String[]> hasPersonalressurskategori() {
        return (arbeidsforhold, personalressurskategori) -> Arrays.stream(personalressurskategori)
                .anyMatch(category -> Optional.ofNullable(arbeidsforhold)
                        .map(GraphQLPersonalmappe.Arbeidsforhold::getPersonalressurs)
                        .map(GraphQLPersonalmappe.Personalressurs::getPersonalressurskategori)
                        .map(GraphQLPersonalmappe.Personalressurskategori::getKode)
                        .filter(category::equals)
                        .isPresent());
    }
}