package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces shared payment resources and observes approved-payment events.
 */
@ApplicationScoped
public class PaymentResources {
    private final AtomicInteger approvedPayments = new AtomicInteger();

    /**
     * Creates payment resources for the example.
     */
    public PaymentResources() {
    }

    @Produces
    @Named("currency")
    String currencyCode() {
        return "USD";
    }

    void onPaymentApproved(@Observes PaymentApproved event) {
        approvedPayments.incrementAndGet();
    }

    /**
     * Returns how many payment approval events this bean observed.
     *
     * @return number of observed payment approvals
     */
    public int approvedPayments() {
        return approvedPayments.get();
    }
}
