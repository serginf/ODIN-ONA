package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.graph.jena.GraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.IntegratedGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.LocalGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.WorkflowGraphJenaImpl;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

@Repository
public class GraphRepository {

    @PersistenceContext
    private EntityManager em;

    public LocalGraphJenaImpl findLocalGraph(String graphName) {
        return em.find(LocalGraphJenaImpl.class, graphName);
    }

    public IntegratedGraphJenaImpl findIntegratedGraph(String graphName) {
        return em.find(IntegratedGraphJenaImpl.class, graphName);
    }

    public WorkflowGraphJenaImpl findWorkflowGraph(String graphName) {
        return em.find(WorkflowGraphJenaImpl.class, graphName);
    }

    @Transactional
    public <T extends GraphJenaImpl> T save(T graph) {
        return em.merge(graph);
    }

    @Transactional
    public void delete(GraphJenaImpl graph) {
        GraphJenaImpl managed = em.find(GraphJenaImpl.class, graph.getGraphName());
        if (managed != null) {
            em.remove(managed);
        }
    }
}
