package no.novari.personalmappe.utilities;

import no.fint.model.resource.Link;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class PersonnelUtilities {
    public static String getNIN(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getPerson().stream()
                .map(Link::getHref)
                .map(href -> StringUtils.substringAfterLast(href, "/"))
                .map(PersonnelUtilities::maskId)
                .findAny()
                .orElse(null);
    }

    private static String maskId(String nin) {
        try {
            return Long.toString((Long.parseLong(nin) / 100), 36);
        } catch (NumberFormatException e) {
            return nin;
        }
    }


    public static String getUsername(PersonalmappeResource personalmappeResource) {
        return getIdentifier(personalmappeResource.getPersonalressurs());
    }

    public static String getLeader(PersonalmappeResource personalmappeResource) {
        return getIdentifier(personalmappeResource.getLeder());
    }

    public static String getWorkplace(PersonalmappeResource personalmappeResource) {
        return getIdentifier(personalmappeResource.getArbeidssted());
    }

    private static String getIdentifier(List<Link> links) {
        return links.stream()
                .map(Link::getHref)
                .map(href -> StringUtils.substringAfterLast(href, "/"))
                .findAny()
                .orElse(null);
    }
}
