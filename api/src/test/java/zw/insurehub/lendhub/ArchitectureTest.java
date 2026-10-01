package zw.insurehub.lendhub;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** Architecture rules are tests (docs/05-test-strategy.md): module boundaries, no wall-clock in business code. */
class ArchitectureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(LendHubApplication.class);
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("zw.insurehub.lendhub");

    @Test
    void modules_have_no_cycles_and_only_use_each_others_public_api() {
        MODULES.verify();
    }

    @Test
    void business_code_never_reads_the_wall_clock_for_dates() {
        noClasses().that().doNotHaveFullyQualifiedName("zw.insurehub.lendhub.eod.EodScheduler")
                .should().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now", java.time.ZoneId.class)
                .because("domain code uses the BusinessDateProvider so EOD and tests control time")
                .check(CLASSES);
    }

    @Test
    void money_maths_never_uses_floating_point_types_in_entities() {
        noClasses().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Double")
                .check(CLASSES);
    }

    @Test
    void controllers_are_not_used_by_other_classes() {
        noClasses().that().haveSimpleNameNotEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
                .because("controllers are adapters; services must not depend on them")
                .check(CLASSES);
    }

    @Test
    void writes_module_documentation() {
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml().writeModuleCanvases();
    }
}
