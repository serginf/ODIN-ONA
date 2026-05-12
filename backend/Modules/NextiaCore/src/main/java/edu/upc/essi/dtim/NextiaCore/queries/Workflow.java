package edu.upc.essi.dtim.NextiaCore.queries;

import edu.upc.essi.dtim.NextiaCore.graph.WorkflowGraph;


public class Workflow {
    private String workflowID;
    private String workflowName;
    private WorkflowGraph workflowGraph;
    private String tenantId;
    private Long version;

    public String getWorkflowID() { return workflowID; }
    public void setWorkflowID(String workflowID) { this.workflowID = workflowID; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public WorkflowGraph getWorkflowGraph() { return workflowGraph; }
    public void setWorkflowGraph(WorkflowGraph workflowGraph) { this.workflowGraph = workflowGraph; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
