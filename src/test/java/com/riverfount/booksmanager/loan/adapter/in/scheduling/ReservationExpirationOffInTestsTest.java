package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * The tests of the application must not fire the expiration of reservations by themselves (RF19): in the whole
 * application context of the tests the routine has no job, and scheduling is not even enabled.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReservationExpirationOffInTestsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theRoutineShouldBeOffInTheTestProfile() {
        assertThat(context.getBeanNamesForType(ReservationExpirationJob.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }
}
