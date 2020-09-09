package no.fint.personalmappe.utilities;

import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import org.apache.commons.lang3.StringUtils;

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
        return personalmappeResource.getPersonalressurs().stream()
                .map(Link::getHref)
                .map(href -> StringUtils.substringAfterLast(href, "/"))
                .findAny()
                .orElse(null);
    }
}
