package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class PaymentResources {
    private final AtomicInteger approvedPayments = new AtomicInteger();

    @Produces
    @Named("currency")
    String currencyCode() {
        return "USD";
    }

    void onPaymentApproved(@Observes PaymentApproved event) {
        approvedPayments.incrementAndGet();
    }

    public int approvedPayments() {
        return approvedPayments.get();
    }
}
