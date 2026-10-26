package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PublicEndpoint;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves that the architecture rule behind RNF08 really rejects what it must:
 * it is run against small controllers written for this test, so a rule that
 * silently accepts everything would be caught here.
 */
class AccessRulesTest {

    @AdminOrOperator
    @RestController
    static class DeclaredOnTheClass {
        @GetMapping("/a")
        String a() {
            return "a";
        }

        @PostMapping("/b")
        String b() {
            return "b";
        }
    }

    @RestController
    static class DeclaredOnEveryMethod {
        @AdminOnly
        @GetMapping("/a")
        String a() {
            return "a";
        }

        @PublicEndpoint
        @PostMapping("/b")
        String b() {
            return "b";
        }
    }

    @AdminOrOperator
    @RestController
    static class ClassDefaultWithAStricterMethod {
        @GetMapping("/a")
        String a() {
            return "a";
        }

        @AdminOnly
        @PostMapping("/b")
        String b() {
            return "b";
        }
    }

    @RestController
    static class NothingDeclared {
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    @RestController
    static class OneMethodForgotten {
        @AdminOnly
        @GetMapping("/a")
        String a() {
            return "a";
        }

        @RequestMapping("/b")
        String b() {
            return "b";
        }
    }

    @AdminOnly
    @PublicEndpoint
    @RestController
    static class TwoMarkersOnTheClass {
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    @RestController
    static class TwoMarkersOnAMethod {
        @AdminOnly
        @AdminOrOperator
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    @RestController
    static class OnlyAHelperIsUnmarked {
        @AdminOnly
        @GetMapping("/a")
        String a() {
            return helper();
        }

        String helper() {
            return "not an endpoint";
        }
    }

    static class NotAController {
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    private static EvaluationResult evaluate(Class<?>... controllers) {
        return AccessRules.everyControllerDeclaresItsAccess().evaluate(new ClassFileImporter().importClasses(controllers));
    }

    @Test
    void shouldAcceptAMarkerOnTheClassOnEveryMethodOrAClassDefaultWithAStricterMethod() {
        assertThat(evaluate(DeclaredOnTheClass.class).hasViolation()).isFalse();
        assertThat(evaluate(DeclaredOnEveryMethod.class).hasViolation()).isFalse();
        assertThat(evaluate(ClassDefaultWithAStricterMethod.class).hasViolation()).isFalse();
        assertThat(evaluate(OnlyAHelperIsUnmarked.class).hasViolation()).isFalse();
    }

    @Test
    void shouldRejectAControllerThatDeclaresNothing() {
        var result = evaluate(NothingDeclared.class);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails().toString()).contains("NothingDeclared");
    }

    @Test
    void shouldRejectAControllerWhereOneEndpointWasForgotten() {
        var result = evaluate(OneMethodForgotten.class);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails().toString()).contains("OneMethodForgotten.b");
    }

    @Test
    void shouldRejectMoreThanOneMarkerOnTheSameElement() {
        assertThat(evaluate(TwoMarkersOnTheClass.class).hasViolation()).isTrue();
        assertThat(evaluate(TwoMarkersOnAMethod.class).hasViolation()).isTrue();
    }

    @Test
    void shouldIgnoreClassesThatAreNotControllers() {
        assertThat(evaluate(NotAController.class).hasViolation()).isFalse();
    }

    @Test
    void shouldReportEveryOffenderNotJustTheFirst() {
        var result = evaluate(NothingDeclared.class, OneMethodForgotten.class);

        var details = result.getFailureReport().getDetails().toString();
        assertThat(details).contains("NothingDeclared").contains("OneMethodForgotten");
    }

    // the routes of the reader (Phase 6): exactly the ones under /api/v1/me, and only for the reader

    @ReaderOnly
    @RestController
    @RequestMapping("/api/v1/me/loans")
    static class ReaderRoutes {
        @GetMapping
        String a() {
            return "a";
        }

        @PostMapping("/{id}/renew")
        String b() {
            return "b";
        }
    }

    @ReaderOnly
    @RestController
    @RequestMapping("/api/v1/me")
    static class ReaderRoot {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @RestController
    @RequestMapping("/api/v1/me")
    static class ReaderMarkerOnTheMethods {
        @ReaderOnly
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    @AdminOrOperator
    @RestController
    @RequestMapping("/api/v1/measure")
    static class SimilarPrefixButNotTheReaderRoutes {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @ReaderOnly
    @RestController
    @RequestMapping("/api/v1/loans")
    static class ReaderOutsideTheReaderRoutes {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @ReaderOnly
    @RestController
    @RequestMapping("/api/v1/measure")
    static class ReaderOnASimilarPrefix {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @AdminOrOperator
    @RestController
    @RequestMapping("/api/v1/me/loans")
    static class AdministrativeUnderTheReaderRoutes {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @PublicEndpoint
    @RestController
    @RequestMapping("/api/v1/me/open")
    static class PublicUnderTheReaderRoutes {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @AdminOnly
    @RestController
    @RequestMapping(path = "/api/v1/me/p")
    static class AdministrativeUnderTheReaderRoutesByPath {
        @GetMapping
        String a() {
            return "a";
        }
    }

    @AdminOrOperator
    @RestController
    @RequestMapping("/api/v1/readers")
    static class ReaderMethodInAnAdministrativeController {
        @GetMapping
        String a() {
            return "a";
        }

        @ReaderOnly
        @GetMapping("/mine")
        String b() {
            return "b";
        }
    }

    @AdminOrOperator
    @RestController
    @RequestMapping("/api/v1/me")
    static class AdministrativeMethodUnderTheReaderRoutes {
        @AdminOnly
        @GetMapping("/a")
        String a() {
            return "a";
        }
    }

    private static EvaluationResult evaluateReaderRoutes(Class<?>... controllers) {
        return AccessRules.readerRoutesAreExactlyTheOnesUnderMe()
                .evaluate(new ClassFileImporter().importClasses(controllers));
    }

    @Test
    void shouldCountTheReaderMarkerAsDeclaringTheAccess() {
        assertThat(evaluate(ReaderRoutes.class).hasViolation()).isFalse();
        assertThat(evaluate(ReaderMarkerOnTheMethods.class).hasViolation()).isFalse();
    }

    @Test
    void shouldAcceptTheReaderMarkerOnlyUnderTheReaderRoutes() {
        assertThat(evaluateReaderRoutes(ReaderRoutes.class, ReaderRoot.class, ReaderMarkerOnTheMethods.class)
                .hasViolation()).isFalse();
        assertThat(evaluateReaderRoutes(SimilarPrefixButNotTheReaderRoutes.class).hasViolation()).isFalse();
        assertThat(evaluateReaderRoutes(DeclaredOnTheClass.class, NothingDeclared.class).hasViolation()).isFalse();
    }

    @Test
    void shouldRejectTheReaderMarkerOutsideTheReaderRoutes() {
        for (var controller : new Class<?>[] {ReaderOutsideTheReaderRoutes.class, ReaderOnASimilarPrefix.class,
                ReaderMethodInAnAdministrativeController.class}) {
            var result = evaluateReaderRoutes(controller);

            assertThat(result.hasViolation()).as(controller.getSimpleName()).isTrue();
            assertThat(result.getFailureReport().getDetails().toString()).contains(controller.getSimpleName());
        }
    }

    @Test
    void shouldRejectAnythingThatIsNotForTheReaderUnderTheReaderRoutes() {
        for (var controller : new Class<?>[] {AdministrativeUnderTheReaderRoutes.class,
                PublicUnderTheReaderRoutes.class, AdministrativeUnderTheReaderRoutesByPath.class,
                AdministrativeMethodUnderTheReaderRoutes.class}) {
            var result = evaluateReaderRoutes(controller);

            assertThat(result.hasViolation()).as(controller.getSimpleName()).isTrue();
            assertThat(result.getFailureReport().getDetails().toString()).contains(controller.getSimpleName());
        }
    }
}
