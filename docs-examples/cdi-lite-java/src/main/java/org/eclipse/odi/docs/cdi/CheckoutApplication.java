package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

public final class CheckoutApplication {

    private CheckoutApplication() {
    }

    public static void main(String[] args) {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            CheckoutService checkoutService = container.select(CheckoutService.class).get();
            Receipt receipt = checkoutService.checkout("acct-100", 4999);
            System.out.println(receipt);
        }
    }
}
