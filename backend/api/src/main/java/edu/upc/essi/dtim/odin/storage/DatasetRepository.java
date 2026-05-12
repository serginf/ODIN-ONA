package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.datasets.Dataset;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class DatasetRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Dataset> findById(String id) {
        Dataset d = em.find(Dataset.class, id);
        if (d == null || !TenantContext.getCurrentTenant().equals(d.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(d);
    }

    @Transactional
    public Dataset save(Dataset d) {
        d.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(d);
    }
}
