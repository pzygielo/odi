package org.eclipse.odi.docs.cdi;

/**
 * Payment command passed to a {@link PaymentProcessor}.
 *
 * @param accountId account identifier to charge
 * @param cents amount to charge in cents
 */
public record Payment(String accountId, int cents) {
}
