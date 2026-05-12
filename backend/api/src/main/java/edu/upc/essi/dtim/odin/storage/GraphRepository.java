package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.graph.jena.GraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.IntegratedGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.LocalGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.WorkflowGraphJenaImpl;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

@Repository
public class GraphRepository {

    @PersistenceContext
    private EntityManager em;

    public LocalGraphJenaImpl findLocalGraph(String graphName) {
        LocalGraphJenaImpl g = em.find(LocalGraphJenaImpl.class, graphName);
        return tenantMatches(g) ? g : null;
    }

    public IntegratedGraphJenaImpl findIntegratedGraph(String graphName) {
        IntegratedGraphJenaImpl g = em.find(IntegratedGraphJenaImpl.class, graphName);
        return tenantMatches(g) ? g : null;
    }

    public WorkflowGraphJenaImpl findWorkflowGraph(String graphName) {
        WorkflowGraphJenaImpl g = em.find(WorkflowGraphJenaImpl.class, graphName);
        return tenantMatches(g) ? g : null;
    }

    @Transactional
    public <T extends GraphJenaImpl> T save(T graph) {
        graph.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(graph);
    }

    @Transactional
    public void delete(GraphJenaImpl graph) {
        GraphJenaImpl managed = em.find(GraphJenaImpl.class, graph.getGraphName());
        if (tenantMatches(managed)) {
            em.remove(managed);
        }
    }

    private boolean tenantMatches(GraphJenaImpl g) {
        return g != null && TenantContext.getCurrentTenant().equals(g.getTenantId());
    }
}
