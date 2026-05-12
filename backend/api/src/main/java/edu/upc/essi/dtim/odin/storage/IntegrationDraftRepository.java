package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.odin.config.TenantContext;
import edu.upc.essi.dtim.odin.integration.IntegrationDraft;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class IntegrationDraftRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<IntegrationDraft> findByProjectId(String projectId) {
        IntegrationDraft d = em.find(IntegrationDraft.class, projectId);
        if (d == null || !TenantContext.getCurrentTenant().equals(d.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(d);
    }

    @Transactional
    public IntegrationDraft save(IntegrationDraft draft) {
        draft.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(draft);
    }

    @Transactional
    public void deleteByProjectId(String projectId) {
        IntegrationDraft d = em.find(IntegrationDraft.class, projectId);
        if (d != null && TenantContext.getCurrentTenant().equals(d.getTenantId())) {
            em.remove(d);
        }
    }
}
