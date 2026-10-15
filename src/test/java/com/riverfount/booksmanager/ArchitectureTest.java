package com.riverfount.booksmanager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Ensures the hexagonal architecture's layer separation (RNF01, RNF02):
 * the domain is pure Java, and dependencies always point inward. It also
 * ensures that every controller declares who may call it (RNF08).
 */
@AnalyzeClasses(packagesOf = BooksManagerApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "lombok..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainShouldNotDependOnApplicationOrAdapter = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationShouldNotDependOnAdapter = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true);

    /** A new controller that does not decide who may call it fails the build (see AccessRules). */
    @ArchTest
    static final ArchRule everyControllerDeclaresItsAccess = AccessRules.everyControllerDeclaresItsAccess();
}
