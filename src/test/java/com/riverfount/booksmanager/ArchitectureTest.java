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

    /**
     * The loan module uses the catalog's public ports, never the other way round: a catalog
     * that called the loan would make the two services depend on each other, and the
     * application would not start. The packages are anchored at the module root, so an adapter
     * of the loan that implements a port of the catalog (loan.adapter.out.catalog) is not taken for
     * the catalog.
     */
    @ArchTest
    static final ArchRule catalogShouldNotDependOnLoan = noClasses()
            .that().resideInAPackage("com.riverfount.booksmanager.catalog..")
            .should().dependOnClassesThat().resideInAPackage("com.riverfount.booksmanager.loan..")
            .allowEmptyShould(true);

    /** Same one-way dependency for the user module: the loan calls its public ports, never the reverse. */
    @ArchTest
    static final ArchRule userShouldNotDependOnLoan = noClasses()
            .that().resideInAPackage("com.riverfount.booksmanager.user..")
            .should().dependOnClassesThat().resideInAPackage("com.riverfount.booksmanager.loan..")
            .allowEmptyShould(true);

    /**
     * The audit module is a leaf: the other modules report to it through its public ports, and it asks them nothing
     * directly. What it needs to know about a user (the login) it asks through its own outbound port, which the user
     * module implements (user.adapter.out.audit), so the dependency stays one way and the beans cannot form a cycle.
     */
    @ArchTest
    static final ArchRule auditShouldNotDependOnTheOtherModules = noClasses()
            .that().resideInAPackage("com.riverfount.booksmanager.audit..")
            .should().dependOnClassesThat().resideInAnyPackage("com.riverfount.booksmanager.catalog..",
                    "com.riverfount.booksmanager.loan..", "com.riverfount.booksmanager.user..",
                    "com.riverfount.booksmanager.security..")
            .allowEmptyShould(true);

    /** A new controller that does not decide who may call it fails the build (see AccessRules). */
    @ArchTest
    static final ArchRule everyControllerDeclaresItsAccess = AccessRules.everyControllerDeclaresItsAccess();

    /** The reader's routes are exactly the ones under /api/v1/me, and only for the reader (see AccessRules). */
    @ArchTest
    static final ArchRule readerRoutesAreExactlyTheOnesUnderMe = AccessRules.readerRoutesAreExactlyTheOnesUnderMe();
}
