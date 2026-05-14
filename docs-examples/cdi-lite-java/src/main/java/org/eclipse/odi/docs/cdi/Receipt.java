package org.eclipse.odi.docs.cdi;

/**
 * User-facing payment receipt.
 *
 * @param reference payment reference
 * @param total formatted total amount
 */
public record Receipt(String reference, String total) {
}
