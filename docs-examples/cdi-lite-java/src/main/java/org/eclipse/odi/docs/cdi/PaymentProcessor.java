package org.eclipse.odi.docs.cdi;

/**
 * Contract implemented by payment processor beans.
 */
public interface PaymentProcessor {

    /**
     * Charges a payment.
     *
     * @param payment payment request
     * @return approved payment result
     */
    PaymentResult charge(Payment payment);
}
