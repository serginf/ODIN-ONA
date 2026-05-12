package edu.upc.essi.dtim.NextiaCore.repositories;

import edu.upc.essi.dtim.NextiaCore.datasets.Dataset;
import java.util.List;

public class DataRepository {
    private String id;
    private String repositoryName;
    private List<Dataset> datasets;
    private Boolean isVirtual;
    private String tenantId;
    private Long version;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getRepositoryName() {
        return repositoryName;
    }
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getRepositoryType() {
        return this.getClass().getSimpleName(); // Specific name of the class
    }

    public List<Dataset> getDatasets() {
        return datasets;
    }
    public void setDatasets(List<Dataset> datasets) {
        this.datasets = datasets;
    }

    public void addDataset(Dataset dataset) {
        datasets.add(dataset);
    }
    public void removeDataset(Dataset dataset) {
        datasets.remove(dataset);
    }

    public Boolean getVirtual() {
        return this.isVirtual;
    }
    public void setVirtual(final Boolean virtual) {
        this.isVirtual = virtual;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}