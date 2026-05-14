package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class CheckoutService {
    private final Instance<PaymentProcessor> processors;
    private final ReceiptFormatter receiptFormatter;
    private final Event<PaymentApproved> paymentApproved;

    @Inject
    public CheckoutService(
            @Any Instance<PaymentProcessor> processors,
            ReceiptFormatter receiptFormatter,
            Event<PaymentApproved> paymentApproved) {
        this.processors = processors;
        this.receiptFormatter = receiptFormatter;
        this.paymentApproved = paymentApproved;
    }

    public Receipt checkout(String accountId, int cents) {
        Payment payment = new Payment(accountId, cents);
        PaymentProcessor processor = processors.select(CreditCard.Literal.INSTANCE).get();
        PaymentResult result = processor.charge(payment);
        paymentApproved.fire(new PaymentApproved(result.reference(), result.cents()));
        return receiptFormatter.format(result);
    }
}
