package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.odin.config.TenantContext;
import edu.upc.essi.dtim.odin.exception.ElementNotFoundException;
import edu.upc.essi.dtim.odin.projects.pojo.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    ProjectRepository repo;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("tenant-A");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findById_ownTenant_returnsEntity() {
        Project p = projectWithTenant("p1", "tenant-A");
        when(em.find(Project.class, "p1")).thenReturn(p);

        assertTrue(repo.findById("p1").isPresent());
    }

    @Test
    void findById_differentTenant_returnsEmpty() {
        Project p = projectWithTenant("p1", "tenant-B");
        when(em.find(Project.class, "p1")).thenReturn(p);

        assertTrue(repo.findById("p1").isEmpty());
    }

    @Test
    void findById_null_returnsEmpty() {
        when(em.find(Project.class, "missing")).thenReturn(null);

        assertTrue(repo.findById("missing").isEmpty());
    }

    @Test
    void save_stampsCurrentTenant() {
        Project p = new Project();
        p.setProjectId("p2");
        when(em.merge(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project saved = repo.save(p);

        assertEquals("tenant-A", saved.getTenantId());
    }

    @Test
    void delete_differentTenant_throws() {
        Project p = projectWithTenant("p1", "tenant-B");
        when(em.find(Project.class, "p1")).thenReturn(p);

        assertThrows(ElementNotFoundException.class, () -> repo.delete("p1"));
    }

    @Test
    void delete_notFound_throws() {
        when(em.find(Project.class, "missing")).thenReturn(null);

        assertThrows(ElementNotFoundException.class, () -> repo.delete("missing"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_queryUsesTenantParameter() {
        TypedQuery<Project> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Project.class))).thenReturn(query);
        when(query.setParameter(eq("t"), eq("tenant-A"))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<Project> result = repo.findAll();

        verify(query).setParameter("t", "tenant-A");
        assertNotNull(result);
    }

    private Project projectWithTenant(String id, String tenant) {
        Project p = new Project();
        p.setProjectId(id);
        p.setTenantId(tenant);
        return p;
    }
}
