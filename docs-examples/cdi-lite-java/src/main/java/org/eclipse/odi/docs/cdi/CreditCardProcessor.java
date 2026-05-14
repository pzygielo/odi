package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.odi.docs.cdi.extension.PaymentGateway;

@CreditCard
@PaymentGateway("credit-card")
@ApplicationScoped
public class CreditCardProcessor implements PaymentProcessor {

    @Override
    public PaymentResult charge(Payment payment) {
        return new PaymentResult("cc-" + payment.accountId(), payment.cents());
    }
}
