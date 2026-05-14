package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckoutApplicationTest {

    @Test
    void checkoutServiceResolvesAndProcessesPayment() {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            CheckoutService checkoutService = container.select(CheckoutService.class).get();
            PaymentResources paymentResources = container.select(PaymentResources.class).get();

            Receipt receipt = checkoutService.checkout("acct-100", 4999);

            assertEquals("cc-acct-100", receipt.reference());
            assertEquals("USD 49.99", receipt.total());
            assertEquals(1, paymentResources.approvedPayments());
        }
    }

    @Test
    void currentCdiBeanContainerIsAvailable() {
        try (SeContainer ignored = SeContainerInitializer.newInstance().initialize()) {
            assertNotNull(CDI.current().getBeanContainer());
        }
    }

    @Test
    void processorClassesStayOffTheRuntimeClasspath() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName(
                        "org.eclipse.odi.cdi.processor.CdiUtil",
                        false,
                        CheckoutApplicationTest.class.getClassLoader()
                )
        );
    }
}
