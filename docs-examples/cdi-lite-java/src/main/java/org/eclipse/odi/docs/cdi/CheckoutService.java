package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Application service that demonstrates constructor injection, programmatic
 * lookup with {@link Instance}, and event publication.
 */
@ApplicationScoped
public class CheckoutService {
    private final Instance<PaymentProcessor> processors;
    private final ReceiptFormatter receiptFormatter;
    private final Event<PaymentApproved> paymentApproved;

    /**
     * Creates the checkout service.
     *
     * @param processors all payment processors, queried by qualifier at runtime
     * @param receiptFormatter formatter used to produce user-facing receipts
     * @param paymentApproved event emitter for approved payments
     */
    @Inject
    public CheckoutService(
            @Any Instance<PaymentProcessor> processors,
            ReceiptFormatter receiptFormatter,
            Event<PaymentApproved> paymentApproved) {
        this.processors = processors;
        this.receiptFormatter = receiptFormatter;
        this.paymentApproved = paymentApproved;
    }

    /**
     * Charges the account and returns a formatted receipt.
     *
     * @param accountId account identifier to charge
     * @param cents amount to charge in cents
     * @return formatted receipt for the approved payment
     */
    public Receipt checkout(String accountId, int cents) {
        Payment payment = new Payment(accountId, cents);
        PaymentProcessor processor = processors.select(CreditCard.Literal.INSTANCE).get();
        PaymentResult result = processor.charge(payment);
        paymentApproved.fire(new PaymentApproved(result.reference(), result.cents()));
        return receiptFormatter.format(result);
    }
}
