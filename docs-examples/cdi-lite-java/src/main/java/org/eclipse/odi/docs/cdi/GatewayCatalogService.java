package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.odi.docs.cdi.extension.PaymentCatalog;
import org.eclipse.odi.docs.cdi.extension.PaymentGateway;

import java.util.Set;

@ApplicationScoped
public class GatewayCatalogService {
    private final PaymentProcessor processor;
    private final PaymentCatalog catalog;

    @Inject
    public GatewayCatalogService(
            @PaymentGateway("credit-card") PaymentProcessor processor,
            PaymentCatalog catalog) {
        this.processor = processor;
        this.catalog = catalog;
    }

    public Set<String> gateways() {
        return catalog.gateways();
    }

    public String chargeReference(String accountId, int cents) {
        return processor.charge(new Payment(accountId, cents)).reference();
    }
}
