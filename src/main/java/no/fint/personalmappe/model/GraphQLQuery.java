package no.fint.personalmappe.model;

import lombok.Data;

@Data
public class GraphQLQuery {

    private Object variables;
    private String query;
}
