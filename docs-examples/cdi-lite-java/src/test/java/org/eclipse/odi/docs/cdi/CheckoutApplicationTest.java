package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.odi.docs.cdi.extension.PaymentCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
    void buildCompatibleExtensionRegistersQualifierAndSyntheticBean() {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            GatewayCatalogService gatewayCatalogService = container.select(GatewayCatalogService.class).get();
            PaymentCatalog paymentCatalog = container.select(PaymentCatalog.class).get();

            assertEquals(Set.of("credit-card"), gatewayCatalogService.gateways());
            assertEquals(Set.of("credit-card"), paymentCatalog.gateways());
            assertEquals("cc-acct-200", gatewayCatalogService.chargeReference("acct-200", 1299));
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
