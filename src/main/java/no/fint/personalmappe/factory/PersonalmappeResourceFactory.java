package no.fint.personalmappe.factory;

import no.fint.model.administrasjon.organisasjon.Organisasjonselement;
import no.fint.model.administrasjon.personal.Personalressurs;
import no.fint.model.felles.Person;
import no.fint.model.felles.kompleksedatatyper.Personnavn;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.arkiv.PartsinformasjonResource;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.GraphQLPersonalmappe;

import java.util.Collections;
import java.util.Optional;

public final class PersonalmappeResourceFactory {

    private PersonalmappeResourceFactory() {
    }

    public static PersonalmappeResource toPersonalmappeResource(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold, Boolean administrativEnhet) {
        PersonalmappeResource resource = new PersonalmappeResource();

        getPerson(arbeidsforhold).ifPresent(resource::addPerson);
        getNavn(arbeidsforhold).ifPresent(resource::setNavn);

        getPersonalressurs(arbeidsforhold).ifPresent(personalressurs -> {

            resource.addPersonalressurs(Link.with(Personalressurs.class, "brukernavn", personalressurs));

            getLeder(arbeidsforhold).ifPresent(leder -> {

                if (personalressurs.equalsIgnoreCase(leder) || !administrativEnhet) {
                    getLedersLeder(arbeidsforhold).ifPresent(resource::addLeder);
                    getLedersArbeidssted(arbeidsforhold).ifPresent(resource::addArbeidssted);

                } else {
                    resource.addLeder(Link.with(Personalressurs.class, "brukernavn", leder));
                    getArbeidssted(arbeidsforhold).ifPresent(resource::addArbeidssted);
                }
            });
        });

        resource.setPart(Collections.singletonList(new PartsinformasjonResource()));
        resource.setTittel("DUMMY");
        return resource;
    }

    private static Optional<String> getPersonalressurs(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi);
    }

    private static Optional<Link> getPerson(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getPersonalressurs())
                .map(GraphQLPersonalmappe.Personalressurs::getPerson)
                .map(GraphQLPersonalmappe.Person::getFodselsnummer)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi)
                .map(Link.apply(Person.class, "fodselsnummer"));
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

    private static Optional<Link> getArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi)
                .map(Link.apply(Organisasjonselement.class, "organisasjonsid"));
    }

    private static Optional<Link> getLedersLeder(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getLeder)
                .map(GraphQLPersonalmappe.Personalressurs::getBrukernavn)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi)
                .map(Link.apply(Personalressurs.class, "brukernavn"));
    }

    private static Optional<Link> getLedersArbeidssted(GraphQLPersonalmappe.Arbeidsforhold arbeidsforhold) {
        return Optional.ofNullable(arbeidsforhold.getArbeidssted())
                .map(GraphQLPersonalmappe.Organisasjonselement::getOverordnet)
                .map(GraphQLPersonalmappe.Organisasjonselement::getOrganisasjonsId)
                .map(GraphQLPersonalmappe.Identifikator::getIdentifikatorverdi)
                .map(Link.apply(Organisasjonselement.class, "organisasjonsid"));
    }
}
