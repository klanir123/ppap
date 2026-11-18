package com.msg.ppap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;

/** Hexagonal architecture rules inside each module (CLAUDE.md, ADR-0006). */
@AnalyzeClasses(packages = "com.msg.ppap", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("com.msg.ppap.*.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "software.amazon..")
            .because("the domain must stay pure Java")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that()
            .resideInAPackage("com.msg.ppap.*.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.msg.ppap.*.application..", "com.msg.ppap.*.adapter..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("com.msg.ppap.*.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.msg.ppap.*.adapter..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule hexagonal = JMoleculesArchitectureRules.ensureHexagonal();

    @ArchTest
    static final ArchRule ddd = JMoleculesDddRules.all();
}
