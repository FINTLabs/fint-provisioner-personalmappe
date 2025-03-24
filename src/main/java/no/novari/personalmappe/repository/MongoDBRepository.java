package no.novari.personalmappe.repository;

import no.novari.personalmappe.model.MongoDBPersonalmappe;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoDBRepository extends MongoRepository<MongoDBPersonalmappe, String> {
}
