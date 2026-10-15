package com.riverfount.booksmanager;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PublicEndpoint;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.List;
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
            List.of(AdminOnly.class, AdminOrOperator.class, PublicEndpoint.class);

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
