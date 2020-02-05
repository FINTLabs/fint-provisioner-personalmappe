package no.fint.personalmappe.repository;

import no.fint.personalmappe.model.MongoDBPersonalmappe;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoDBRepository extends MongoRepository<MongoDBPersonalmappe, String> {
}
