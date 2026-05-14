package org.eclipse.odi.docs.cdi;

/**
 * CDI event payload emitted after a payment is approved.
 *
 * @param reference payment reference
 * @param cents approved amount in cents
 */
public record PaymentApproved(String reference, int cents) {
}
