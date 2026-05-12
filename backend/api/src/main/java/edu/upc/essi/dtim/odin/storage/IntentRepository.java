package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.queries.Intent;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class IntentRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Intent> findById(String id) {
        Intent i = em.find(Intent.class, id);
        if (i == null || !TenantContext.getCurrentTenant().equals(i.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(i);
    }

    @Transactional
    public Intent save(Intent i) {
        i.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(i);
    }
}
