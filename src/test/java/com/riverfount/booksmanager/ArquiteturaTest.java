package com.riverfount.booksmanager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garante a separação de camadas da arquitetura hexagonal (RNF01, RNF02):
 * o domínio é Java puro, e as dependências sempre apontam para dentro.
 */
@AnalyzeClasses(packagesOf = BooksManagerApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioNaoDependeDeFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "lombok..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dominioNaoDependeDeAplicacaoNemDeAdaptador = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule aplicacaoNaoDependeDeAdaptador = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true);
}
