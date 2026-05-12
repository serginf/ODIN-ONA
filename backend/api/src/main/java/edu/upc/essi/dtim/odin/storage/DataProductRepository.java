package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.queries.DataProduct;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class DataProductRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<DataProduct> findById(String id) {
        DataProduct dp = em.find(DataProduct.class, id);
        if (dp == null || !TenantContext.getCurrentTenant().equals(dp.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(dp);
    }

    @Transactional
    public DataProduct save(DataProduct dp) {
        dp.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(dp);
    }
}
