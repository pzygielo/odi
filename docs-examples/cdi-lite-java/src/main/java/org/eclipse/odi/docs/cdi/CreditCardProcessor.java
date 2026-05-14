package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.odi.docs.cdi.extension.PaymentGateway;

/**
 * Credit-card payment processor selected by the CDI qualifier and the
 * extension-defined gateway qualifier.
 */
@CreditCard
@PaymentGateway("credit-card")
@ApplicationScoped
public class CreditCardProcessor implements PaymentProcessor {

    /**
     * Creates the credit-card processor.
     */
    public CreditCardProcessor() {
    }

    @Override
    public PaymentResult charge(Payment payment) {
        return new PaymentResult("cc-" + payment.accountId(), payment.cents());
    }
}
