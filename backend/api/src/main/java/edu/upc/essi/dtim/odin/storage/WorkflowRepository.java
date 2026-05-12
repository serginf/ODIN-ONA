package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.queries.Workflow;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class WorkflowRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Workflow> findById(String id) {
        Workflow w = em.find(Workflow.class, id);
        if (w == null || !TenantContext.getCurrentTenant().equals(w.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(w);
    }

    @Transactional
    public Workflow save(Workflow w) {
        w.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(w);
    }
}
