package no.fint.personalmappe.util;

import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.model.GraphQLQuery;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Util {

    private Util() {
    }

    public static GraphQLQuery getGraphQLQuery(String filename) {
        String query = null;

        try (InputStream inputStream = Util.class.getClassLoader().getResourceAsStream(filename)) {
            query = graphQLToStringConverter(Objects.requireNonNull(inputStream));
        } catch (IOException | NullPointerException ex) {
            ex.printStackTrace();
        }

        GraphQLQuery graphQLQuery = new GraphQLQuery();
        graphQLQuery.setQuery(query);

        return graphQLQuery;
    }

    private static String graphQLToStringConverter(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        for (int i; 0 < (i = inputStream.read(buffer)); ) {
            byteArrayOutputStream.write(buffer, 0, i);
        }
        byteArrayOutputStream.close();

        return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String getNIN(PersonalmappeResource personalmappeResource) {
        return personalmappeResource.getPerson().stream()
                .map(Link::getHref)
                .map(href -> StringUtils.substringAfterLast(href, "/"))
                .findAny()
                .orElse(null);
    }
}