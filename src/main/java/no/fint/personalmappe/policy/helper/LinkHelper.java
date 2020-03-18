package no.fint.personalmappe.policy.helper;

import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.exception.UnableToGetLink;

import java.util.function.Function;

public class LinkHelper {

    private PersonalmappeResource personalmappeResource;
    private String property;
    private String identificator;

    public LinkHelper(PersonalmappeResource personalmappeResource) {
        this.personalmappeResource = personalmappeResource;
        this.identificator = "systemid";
    }

    public static Function<PersonalmappeResource, LinkHelper> resource() {
        return LinkHelper::new;
    }

    public LinkHelper link(String link) {
        this.property = link;
        return this;
    }

    public LinkHelper id(String identificator) {
        this.identificator = identificator;
        return this;
    }

    public String getLink() {
        return personalmappeResource
                .getLinks()
                .get(property)
                .stream()
                .filter((link -> link.getHref().contains(identificator)))
                .findFirst()
                .orElseThrow(UnableToGetLink::new)
                .getHref();
    }

    public String getValue() {
        String url = getLink();
        return url.substring(url.lastIndexOf("/") + 1);
    }

    public void replaceValue(String value) {
        String link = getLink();
        String newLink = link.substring(0, link.lastIndexOf("/") + 1) + value;
        personalmappeResource.getLinks().get(property).removeIf(l -> l.getHref().equals(link));
        personalmappeResource.getLinks().get(property).add(Link.with(newLink));
    }

    public boolean is(String value) {
        return getValue().equals(value);
    }

    public boolean isNot(String value) {
        return !is(value);
    }
}
