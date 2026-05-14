package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

/**
 * Minimal CDI SE entry point for the documentation example.
 */
public final class CheckoutApplication {

    private CheckoutApplication() {
    }

    /**
     * Starts a CDI SE container, resolves the checkout service, and performs one payment.
     *
     * @param args command-line arguments, unused by this example
     */
    public static void main(String[] args) {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            CheckoutService checkoutService = container.select(CheckoutService.class).get();
            Receipt receipt = checkoutService.checkout("acct-100", 4999);
            System.out.println(receipt);
        }
    }
}
