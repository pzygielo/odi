package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.Locale;

/**
 * Formats payment results using a produced currency code.
 */
@ApplicationScoped
public class ReceiptFormatter {
    private final String currency;

    /**
     * Creates a receipt formatter.
     *
     * @param currency currency code produced by {@link PaymentResources}
     */
    @Inject
    public ReceiptFormatter(@Named("currency") String currency) {
        this.currency = currency;
    }

    /**
     * Formats a payment result as a receipt.
     *
     * @param result approved payment result
     * @return formatted receipt
     */
    public Receipt format(PaymentResult result) {
        String total = String.format(Locale.ROOT, "%s %.2f", currency, result.cents() / 100.0);
        return new Receipt(result.reference(), total);
    }
}
