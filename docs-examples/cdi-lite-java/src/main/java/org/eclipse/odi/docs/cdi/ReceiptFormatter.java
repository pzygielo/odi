package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.Locale;

@ApplicationScoped
public class ReceiptFormatter {
    private final String currency;

    @Inject
    public ReceiptFormatter(@Named("currency") String currency) {
        this.currency = currency;
    }

    public Receipt format(PaymentResult result) {
        String total = String.format(Locale.ROOT, "%s %.2f", currency, result.cents() / 100.0);
        return new Receipt(result.reference(), total);
    }
}
