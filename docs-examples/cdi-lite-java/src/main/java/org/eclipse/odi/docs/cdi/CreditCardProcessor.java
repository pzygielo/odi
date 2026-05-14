package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;

@CreditCard
@ApplicationScoped
public class CreditCardProcessor implements PaymentProcessor {

    @Override
    public PaymentResult charge(Payment payment) {
        return new PaymentResult("cc-" + payment.accountId(), payment.cents());
    }
}
