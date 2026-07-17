package com.kyra.app.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundaries (kyra-doc README §3):
 * a module may only reference another module through its {@code api} package.
 */
class ModuleBoundaryTest {

    private static final List<String> MODULES = List.of(
            "identity", "account", "market", "order", "matching", "settlement",
            "marketdata", "wallet", "risk", "compliance", "fee", "admin",
            "notification", "liquidity");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kyra");
    }

    @Test
    void modulesOnlyTalkThroughApiPackages() {
        for (String module : MODULES) {
            String internal = "com.kyra." + module + ".domain..";
            String infra = "com.kyra." + module + ".infra..";
            noClasses()
                    .that().resideOutsideOfPackage("com.kyra." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(internal, infra)
                    .because("only com.kyra." + module + ".api is public to other modules")
                    .check(classes);
        }
    }

    @Test
    void noModuleDependsOnTheAppLayer() {
        noClasses()
                .that().resideOutsideOfPackage("com.kyra.app..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.kyra.app..")
                .because("kyra-app wires modules; modules must not know the app")
                .check(classes);
    }

    @Test
    void moneyNeverUsesFloatingPoint() {
        noClasses()
                .that().resideInAPackage("com.kyra..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Random")
                .because("randomness in domain logic breaks deterministic replay; "
                        + "inject a seeded source where needed")
                .check(classes);
    }
}
