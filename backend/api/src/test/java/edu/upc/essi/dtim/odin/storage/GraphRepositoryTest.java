package edu.upc.essi.dtim.odin.storage;

import edu.upc.essi.dtim.NextiaCore.graph.jena.IntegratedGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.LocalGraphJenaImpl;
import edu.upc.essi.dtim.NextiaCore.graph.jena.WorkflowGraphJenaImpl;
import edu.upc.essi.dtim.odin.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    GraphRepository repo;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("tenant-A");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- findLocalGraph ---

    @Test
    void findLocalGraph_ownTenant_returnsGraph() {
        LocalGraphJenaImpl g = localGraph("g1", "tenant-A");
        when(em.find(LocalGraphJenaImpl.class, "g1")).thenReturn(g);

        assertNotNull(repo.findLocalGraph("g1"));
    }

    @Test
    void findLocalGraph_differentTenant_returnsNull() {
        LocalGraphJenaImpl g = localGraph("g1", "tenant-B");
        when(em.find(LocalGraphJenaImpl.class, "g1")).thenReturn(g);

        assertNull(repo.findLocalGraph("g1"));
    }

    @Test
    void findLocalGraph_notFound_returnsNull() {
        when(em.find(LocalGraphJenaImpl.class, "missing")).thenReturn(null);

        assertNull(repo.findLocalGraph("missing"));
    }

    // --- findIntegratedGraph ---

    @Test
    void findIntegratedGraph_differentTenant_returnsNull() {
        IntegratedGraphJenaImpl g = new IntegratedGraphJenaImpl();
        g.setGraphName("ig1");
        g.setTenantId("tenant-B");
        when(em.find(IntegratedGraphJenaImpl.class, "ig1")).thenReturn(g);

        assertNull(repo.findIntegratedGraph("ig1"));
    }

    // --- findWorkflowGraph ---

    @Test
    void findWorkflowGraph_ownTenant_returnsGraph() {
        WorkflowGraphJenaImpl g = new WorkflowGraphJenaImpl();
        g.setGraphName("wg1");
        g.setTenantId("tenant-A");
        when(em.find(WorkflowGraphJenaImpl.class, "wg1")).thenReturn(g);

        assertNotNull(repo.findWorkflowGraph("wg1"));
    }

    // --- save stamps tenant ---

    @Test
    void save_stampsCurrentTenant() {
        LocalGraphJenaImpl g = new LocalGraphJenaImpl();
        g.setGraphName("g2");
        when(em.merge(any(LocalGraphJenaImpl.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalGraphJenaImpl saved = repo.save(g);

        assertEquals("tenant-A", saved.getTenantId());
    }

    // --- delete ignores cross-tenant ---

    @Test
    void delete_differentTenant_doesNotRemove() {
        LocalGraphJenaImpl g = localGraph("g3", "tenant-B");
        when(em.find(any(), eq("g3"))).thenReturn(g);

        repo.delete(g);

        verify(em, never()).remove(any());
    }

    // helpers

    private LocalGraphJenaImpl localGraph(String name, String tenant) {
        LocalGraphJenaImpl g = new LocalGraphJenaImpl();
        g.setGraphName(name);
        g.setTenantId(tenant);
        return g;
    }
}
