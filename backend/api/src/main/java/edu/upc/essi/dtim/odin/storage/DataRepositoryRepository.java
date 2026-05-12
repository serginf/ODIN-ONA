package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.repositories.DataRepository;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public class DataRepositoryRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<DataRepository> findById(String id) {
        DataRepository r = em.find(DataRepository.class, id);
        if (r == null || !TenantContext.getCurrentTenant().equals(r.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(r);
    }

    @Transactional
    public DataRepository save(DataRepository r) {
        r.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(r);
    }
}
