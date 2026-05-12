package edu.upc.essi.dtim.odin.integration;

public class IntegrationDraft {
    private String projectId;
    private String temporalIntegratedGraphName;
    private String tenantId;
    private Long version;

    public IntegrationDraft() {}

    public IntegrationDraft(String projectId, String temporalIntegratedGraphName) {
        this.projectId = projectId;
        this.temporalIntegratedGraphName = temporalIntegratedGraphName;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTemporalIntegratedGraphName() { return temporalIntegratedGraphName; }
    public void setTemporalIntegratedGraphName(String temporalIntegratedGraphName) {
        this.temporalIntegratedGraphName = temporalIntegratedGraphName;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
