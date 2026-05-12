package edu.upc.essi.dtim.NextiaCore.queries;

import java.util.LinkedList;
import java.util.List;

public class Intent {
    String intentID;
    String intentName;
    DataProduct dataProduct;
    String problem;
    List<Workflow> workflows;
    private String tenantId;
    private Long version;

    public Intent() {}

    public Intent(String intentName, String problem) {
        this.intentName = intentName;
        this.problem = problem;
        //this.workflows = new LinkedList<>();
    }

    public String getIntentID() { return intentID; }
    public void setIntentID(String intentID) { this.intentID = intentID; }

    public String getIntentName() { return intentName; }
    public void setIntentName(String intentName) { this.intentName = intentName; }

    public DataProduct getDataProduct() { return dataProduct; }
    public void setDataProduct(DataProduct dataProduct) { this.dataProduct = dataProduct; }

    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }

    public List<Workflow> getWorkflows() { return workflows; }
    public void setWorkflows(List<Workflow> workflows) { this.workflows = workflows; }
    public void addWorkflow(Workflow workflow) {
        if (this.workflows == null) {
            this.workflows = new LinkedList<>();
        }
        this.workflows.add(workflow);
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
