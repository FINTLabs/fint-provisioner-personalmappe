package no.fint.personalmappe.repository;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.personalmappe.policy.model.TransformationPolicy;
import no.fint.personalmappe.properties.OrganisationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class PolicyRepository {

    @Value("${fint.azure.storage.connection-string}")
    private String azureStorageConnectionString;

    @Value(("${fint.azure.storage.container:fint-personalmappe-policies}"))
    private String azureStorageContainer;

    @Getter
    private Map<String, List<TransformationPolicy>> policyMap = new HashMap<>();

    private BlobContainerClient blobContainerClient;
    private final OrganisationProperties organisationProperties;

    public PolicyRepository(OrganisationProperties organisationProperties) {
        this.organisationProperties = organisationProperties;
    }

    @PostConstruct
    public void init() {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(azureStorageConnectionString).buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(azureStorageContainer);
    }

    @Scheduled(cron = "${fint.policy.refresh:0/10 * * ? * *}")
    public void refresh() {

        organisationProperties.getOrganisations().keySet().forEach(org -> {
            log.info("Refreshing policies for {}", org);
            List<TransformationPolicy> transformationPolicies = new ArrayList<>();
            blobContainerClient.listBlobsByHierarchy(org + "/").forEach(blobItem ->
            {
                log.info("  Adding policy {}", getPolicyName(blobItem));
                transformationPolicies.add(createPolicy(blobItem));
            });
            policyMap.put(org, transformationPolicies);
            log.info("Refreshed {} policies for {}", transformationPolicies.size(), org);
        });

    }

    private TransformationPolicy createPolicy(BlobItem blobItem) {
        TransformationPolicy transformationPolicy = new TransformationPolicy();
        transformationPolicy.setName(getPolicyName(blobItem));
        transformationPolicy.setPolicy(getPolicyContent(blobItem.getName()));

        return transformationPolicy;
    }

    private String getPolicyName(BlobItem blobItem) {
        return StringUtils.removeEndIgnoreCase(blobItem.getName(), ".js");
    }

    private String getPolicyContent(String name) {
        ByteArrayOutputStream downloadStream = new ByteArrayOutputStream();
        blobContainerClient.getBlobClient(name).download(downloadStream);

        return new String(downloadStream.toByteArray());
    }

}
