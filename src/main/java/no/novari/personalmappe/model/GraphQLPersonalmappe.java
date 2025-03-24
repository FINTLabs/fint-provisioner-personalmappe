package no.novari.personalmappe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GraphQLPersonalmappe {

    @JsonProperty("data")
    private Result result;

    @Data
    public static class Result {
        private Personalressurs personalressurs;
    }

    @Data
    public static class Personalressurs {
        private Identifikator ansattnummer;
        private Identifikator brukernavn;
        private Person person;
        private List<Arbeidsforhold> arbeidsforhold;
        private Personalressurskategori personalressurskategori;
    }

    @Data
    public static class Person {
        private Identifikator fodselsnummer;
        private Navn navn;
    }

    @Data
    public static class Arbeidsforhold {
        private Organisasjonselement arbeidssted;
        private Personalressurs personalressurs;
        private Periode gyldighetsperiode;
        private Boolean hovedstilling;
    }

    @Data
    public static class Organisasjonselement {
        private Identifikator organisasjonsId;
        private Identifikator organisasjonsKode;
        private Personalressurs leder;
        private Organisasjonselement overordnet;
    }

    @Data
    public static class Identifikator {
        private String identifikatorverdi;
    }

    @Data
    public static class Navn {
        private String fornavn;
        private String mellomnavn;
        private String etternavn;
    }

    @Data
    public static class Periode {
        private LocalDateTime start;
        private LocalDateTime slutt;
    }

    @Data
    public static class Personalressurskategori {
        private String kode;
    }
}

