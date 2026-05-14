package org.eclipse.odi.docs.cdi;

public interface PaymentProcessor {

    PaymentResult charge(Payment payment);
}
