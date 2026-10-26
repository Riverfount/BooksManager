package com.riverfount.booksmanager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PublicEndpoint;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rule behind RNF08: every controller says who may call it. A controller
 * (or each of its endpoint methods) carries exactly one of {@link AdminOnly},
 * {@link AdminOrOperator} or {@link PublicEndpoint}, so a new controller that
 * forgets to decide fails the build instead of being open to every logged-in
 * user by accident.
 */
final class AccessRules {

    private static final List<Class<? extends Annotation>> MARKERS =
            List.of(AdminOnly.class, AdminOrOperator.class, PublicEndpoint.class, ReaderOnly.class);

    private AccessRules() {
    }

    static ArchRule everyControllerDeclaresItsAccess() {
        return classes().that().areAnnotatedWith(RestController.class)
                .should(declareWhoMayCallEveryEndpoint())
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> declareWhoMayCallEveryEndpoint() {
        return new ArchCondition<>("declare, exactly once, who may call each of their endpoints") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                var onClass = markersOf(controller);
                if (onClass > 1) {
                    events.add(SimpleConditionEvent.violated(controller, controller.getName()
                            + " declares more than one access marker on the class"));
                }
                for (JavaMethod method : controller.getMethods()) {
                    if (!isEndpoint(method)) {
                        continue;
                    }
                    var onMethod = markersOf(method);
                    if (onMethod > 1) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " declares more than one access marker"));
                    } else if (onClass == 0 && onMethod == 0) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " is an endpoint with no access declared (@AdminOnly, @AdminOrOperator or "
                                + "@PublicEndpoint, on the method or on its controller)"));
                    }
                }
            }
        };
    }

    private static final String READER_PREFIX = "/api/v1/me";

    /**
     * The routes of the reader (Phase 6) are exactly the ones under {@code /api/v1/me}: an endpoint marked
     * {@code @ReaderOnly} lives there, and nothing under it is marked for anybody else, so the security chain (which
     * opens that path to the reader alone) and the markers cannot drift apart. The path is the class mapping joined to
     * the method mapping; a route "/api/v1/measure" is not under "/api/v1/me".
     */
    static ArchRule readerRoutesAreExactlyTheOnesUnderMe() {
        return classes().that().areAnnotatedWith(RestController.class)
                .should(markReaderRoutesAndOnlyThem())
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> markReaderRoutesAndOnlyThem() {
        return new ArchCondition<>("mark as @ReaderOnly the endpoints under " + READER_PREFIX + ", and only them") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                var classPaths = pathsOf(controller.getAnnotations());
                for (JavaMethod method : controller.getMethods()) {
                    if (!isEndpoint(method)) {
                        continue;
                    }
                    var methodMarked = markersOf(method) > 0;
                    var forReader = method.isAnnotatedWith(ReaderOnly.class)
                            || (!methodMarked && controller.isAnnotatedWith(ReaderOnly.class));
                    for (var classPath : classPaths) {
                        for (var methodPath : pathsOf(method.getAnnotations())) {
                            var route = join(classPath, methodPath);
                            if (forReader != isUnderReaderPrefix(route)) {
                                events.add(SimpleConditionEvent.violated(method, method.getFullName() + " serves "
                                        + route + (forReader ? ", which is not under " + READER_PREFIX
                                                + " but is @ReaderOnly" : ", which is under " + READER_PREFIX
                                                + " but is not @ReaderOnly")));
                            }
                        }
                    }
                }
            }
        };
    }

    private static boolean isUnderReaderPrefix(String route) {
        return route.equals(READER_PREFIX) || route.startsWith(READER_PREFIX + "/");
    }

    private static String join(String classPath, String methodPath) {
        var joined = ("/" + classPath + "/" + methodPath).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    /** The paths of the first mapping annotation (RequestMapping or one built on it, such as GetMapping); "" if none. */
    private static List<String> pathsOf(Set<? extends JavaAnnotation<?>> annotations) {
        for (var annotation : annotations) {
            var mapping = annotation.getRawType().isEquivalentTo(RequestMapping.class)
                    || annotation.getRawType().isAnnotatedWith(RequestMapping.class);
            if (!mapping) {
                continue;
            }
            var paths = new ArrayList<String>();
            for (var property : new String[] {"value", "path"}) {
                annotation.tryGetExplicitlyDeclaredProperty(property).ifPresent(value -> {
                    for (var item : (Object[]) value) {
                        paths.add(String.valueOf(item));
                    }
                });
            }
            return paths.isEmpty() ? List.of("") : paths;
        }
        return List.of("");
    }

    private static boolean isEndpoint(JavaMethod method) {
        return method.isAnnotatedWith(RequestMapping.class) || method.isMetaAnnotatedWith(RequestMapping.class);
    }

    private static long markersOf(JavaClass controller) {
        return MARKERS.stream().filter(controller::isAnnotatedWith).count();
    }

    private static long markersOf(JavaMethod method) {
        return MARKERS.stream().filter(method::isAnnotatedWith).count();
    }
}
