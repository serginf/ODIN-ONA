package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.odin.config.TenantContext;
import edu.upc.essi.dtim.odin.exception.ElementNotFoundException;
import edu.upc.essi.dtim.odin.projects.pojo.Project;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Project> findById(String id) {
        Project p = em.find(Project.class, id);
        if (p == null || !TenantContext.getCurrentTenant().equals(p.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public Page<Project> findAll(int offset, int limit) {
        String tenant = TenantContext.getCurrentTenant();
        List<Project> items = em.createQuery(
                "SELECT p FROM Project p WHERE p.tenantId = :t", Project.class)
                .setParameter("t", tenant)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        long total = em.createQuery(
                "SELECT COUNT(p) FROM Project p WHERE p.tenantId = :t", Long.class)
                .setParameter("t", tenant)
                .getSingleResult();
        return new Page<>(items, total, offset, limit);
    }

    public List<Project> findAll() {
        return em.createQuery(
                "SELECT p FROM Project p WHERE p.tenantId = :t", Project.class)
                .setParameter("t", TenantContext.getCurrentTenant())
                .getResultList();
    }

    public long countAll() {
        return em.createQuery(
                "SELECT COUNT(p) FROM Project p WHERE p.tenantId = :t", Long.class)
                .setParameter("t", TenantContext.getCurrentTenant())
                .getSingleResult();
    }

    @Transactional
    public Project save(Project p) {
        p.setTenantId(TenantContext.getCurrentTenant());
        return em.merge(p);
    }

    @Transactional
    public void delete(String id) {
        Project p = em.find(Project.class, id);
        if (p == null || !TenantContext.getCurrentTenant().equals(p.getTenantId())) {
            throw new ElementNotFoundException("Project not found: " + id);
        }
        em.remove(p);
    }
}
