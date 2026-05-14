package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.odi.docs.cdi.extension.PaymentCatalog;
import org.eclipse.odi.docs.cdi.extension.PaymentGateway;

import java.util.Set;

/**
 * Application bean that consumes extension-provided CDI metadata.
 *
 * <p>The constructor proves that {@link PaymentGateway} was registered as a
 * qualifier at build time. The injected {@link PaymentCatalog} proves that the
 * extension synthesized a runtime bean from the beans it saw during
 * registration.</p>
 */
@ApplicationScoped
public class GatewayCatalogService {
    private final PaymentProcessor processor;
    private final PaymentCatalog catalog;

    /**
     * Creates a service using the extension-defined qualifier and synthetic bean.
     *
     * @param processor selected with the extension-defined qualifier
     * @param catalog synthetic bean created by the build-compatible extension
     */
    @Inject
    public GatewayCatalogService(
            @PaymentGateway("credit-card") PaymentProcessor processor,
            PaymentCatalog catalog) {
        this.processor = processor;
        this.catalog = catalog;
    }

    /**
     * Returns the gateway names collected by the extension.
     *
     * @return gateway names collected by the extension
     */
    public Set<String> gateways() {
        return catalog.gateways();
    }

    /**
     * Charges through the processor selected by {@link PaymentGateway}.
     *
     * @param accountId account identifier used by the example processor
     * @param cents amount to charge
     * @return payment reference produced by the selected processor
     */
    public String chargeReference(String accountId, int cents) {
        return processor.charge(new Payment(accountId, cents)).reference();
    }
}
