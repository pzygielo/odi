package org.eclipse.odi.docs.cdi;

/**
 * Result returned by a payment processor.
 *
 * @param reference payment processor reference
 * @param cents charged amount in cents
 */
public record PaymentResult(String reference, int cents) {
}
