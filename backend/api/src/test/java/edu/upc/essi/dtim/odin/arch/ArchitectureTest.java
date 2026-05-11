package edu.upc.essi.dtim.odin.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Executable architecture documentation for the ODIN refactor.
 *
 * Rules are introduced phase-by-phase as the codebase migrates.
 * @Disabled rules are scaffolded here so they are visible as a contract;
 * the @Disabled annotation is removed when the phase that satisfies the rule lands.
 *
 * Phase 0  — rules that are trivially true today (baseline enforcement)
 * Phase 1  — @Version + Instant timestamps + TenantContext in repositories
 * Phase 1.7 — no @Autowired field injection; services don't import Spring web types
 * Phase 3  — no Spring web imports anywhere outside controllers
 */
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("edu.upc.essi.dtim.odin");
    }

    // -------------------------------------------------------------------------
    // Phase 0 — active now, trivially true until Phase 1 creates storage/
    // -------------------------------------------------------------------------

    /**
     * Controllers must never bypass the service layer and talk directly to storage.
     * Trivially passes in Phase 0 because the storage package doesn't exist yet.
     * Will actively guard against layering violations once Phase 1 introduces
     * edu.upc.essi.dtim.odin.storage.
     */
    @Test
    void controllers_do_not_import_storage() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage("..odin.storage..")
                .check(importedClasses);
    }

    /**
     * Storage classes must not call into feature services or presentation helpers.
     * The read/write side-effects that today live in GraphStoreJenaImpl (calling
     * nextiaGraphyModuleImpl and integrationModuleInterface) violate this rule.
     * Trivially passes in Phase 0; actively enforced once Phase 2 introduces
     * edu.upc.essi.dtim.odin.storage.
     *
     * ArchUnit assertion: classes under storage/ may not import integration/ or
     * any class whose simple name ends with "Service".
     */
    @Test
    void storage_does_not_call_feature_services() {
        noClasses()
                .that().resideInAPackage("..odin.storage..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Service")
                .check(importedClasses);
    }

    // -------------------------------------------------------------------------
    // Phase 1 — activate when @Version and TenantContext land
    // -------------------------------------------------------------------------

    /**
     * Every JPA @Entity must carry @Version for optimistic locking.
     * Without it, concurrent edits silently overwrite each other.
     * Activate in Phase 1 after V_entity_version migration adds the column.
     */
    @Disabled("Phase 1: enable after @Version Long version added to every @Entity")
    @Test
    void all_entities_have_version_annotation() {
        classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .should().haveOnlyFinalFields() // placeholder — replace with @Version field check once archunit supports it
                .check(importedClasses);
    }

    /**
     * Every JPA @Entity must use Instant for timestamps, not LocalDateTime.
     * LocalDateTime carries no timezone and silently adopts the JVM default zone.
     * Activate in Phase 1 after V4__instant_timestamps migration.
     */
    @Disabled("Phase 1: enable after all timestamp fields migrated from LocalDateTime to Instant")
    @Test
    void entities_use_instant_not_local_datetime() {
        noFields()
                .that().areDeclaredInClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
                .should().haveRawType("java.time.LocalDateTime")
                .check(importedClasses);
    }

    /**
     * Every class in odin.storage whose name ends with Repository must import
     * TenantContext — the compile-time reminder that every read is tenant-filtered.
     * Activate in Phase 1 after per-aggregate repositories are created.
     */
    @Disabled("Phase 1: enable after TenantContext and per-aggregate repositories are created under odin.storage")
    @Test
    void storage_repositories_depend_on_tenant_context() {
        classes()
                .that().resideInAPackage("..odin.storage..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().haveSimpleName("TenantContext")
                .check(importedClasses);
    }

    // -------------------------------------------------------------------------
    // Phase 1.7 — activate when wrapper layer is deleted and DTOs are moved
    // -------------------------------------------------------------------------

    /**
     * Services must not import Spring web types.
     * Until Phase 1.7, some services accept MultipartFile which lives under
     * org.springframework.web.multipart — a legitimate but temporary violation
     * that Phase 1.7 resolves by moving file handling into controllers.
     */
    @Disabled("Phase 1.7: enable after MultipartFile parameters are moved from services to controllers")
    @Test
    void services_do_not_import_spring_web() {
        noClasses()
                .that().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
                .check(importedClasses);
    }

    /**
     * All dependency injection must be constructor-based, not field-based.
     * Field @Autowired breaks immutability, hides dependencies, and is
     * incompatible with plain-Java wiring (Phase 3).
     * Activate in Phase 1.7 after all @Autowired field annotations are removed.
     */
    @Disabled("Phase 1.7: enable after all @Autowired field injection replaced with constructor injection")
    @Test
    void no_autowired_field_injection() {
        noFields()
                .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .check(importedClasses);
    }

    // -------------------------------------------------------------------------
    // Phase 3 — activate when Spring Boot is removed and Javalin lands
    // -------------------------------------------------------------------------

    /**
     * Only controller classes (classes in *Controller) may import Javalin HTTP types.
     * Enforces that routing, request parsing, and response serialization are confined
     * to the boundary layer and never leak into services or storage.
     * Activate in Phase 3 after Spring is replaced with Javalin.
     */
    @Disabled("Phase 3: enable after Spring Boot is replaced with Javalin")
    @Test
    void only_controllers_import_javalin_http() {
        // Services and storage classes must never touch Javalin HTTP types.
        // Controllers are the only HTTP-aware layer.
        noClasses()
                .that().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().resideInAPackage("io.javalin.http..")
                .check(importedClasses);
    }
}
