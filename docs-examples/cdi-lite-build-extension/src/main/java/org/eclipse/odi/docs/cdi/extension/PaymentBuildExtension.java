package org.eclipse.odi.docs.cdi.extension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;

import java.util.Set;
import java.util.TreeSet;

/**
 * Build-compatible extension used by the documentation example.
 *
 * <p>The extension shows the usual CDI Lite extension flow: discovery registers
 * extension-provided annotations, registration observes application beans, and
 * synthesis contributes runtime beans derived from build-time metadata.</p>
 */
public class PaymentBuildExtension implements BuildCompatibleExtension {
    private final Set<String> gateways = new TreeSet<>();

    /**
     * Creates the example build-compatible extension.
     */
    public PaymentBuildExtension() {
    }

    /**
     * Promotes {@link PaymentGateway} to a CDI qualifier before bean discovery
     * completes, so application injection points can select gateway-specific
     * {@code PaymentProcessor} beans with the annotation.
     *
     * @param metaAnnotations mutable build-time meta-annotation registry
     */
    @Discovery
    void registerPaymentGatewayQualifier(MetaAnnotations metaAnnotations) {
        metaAnnotations.addQualifier(PaymentGateway.class);
    }

    /**
     * Collects every class bean annotated with {@link PaymentGateway}. The
     * collected values are later passed to a synthetic runtime bean.
     *
     * @param bean bean metadata exposed by the registration phase
     */
    @Registration(types = Object.class)
    void collectPaymentGatewayBeans(BeanInfo bean) {
        if (bean.isClassBean() && bean.declaringClass().hasAnnotation(PaymentGateway.class)) {
            gateways.add(bean.declaringClass()
                    .annotation(PaymentGateway.class)
                    .value()
                    .asString());
        }
    }

    /**
     * Registers a synthetic {@link PaymentCatalog} bean containing the gateway
     * names collected during registration.
     *
     * @param components synthetic component registry
     */
    @Synthesis
    void registerPaymentCatalog(SyntheticComponents components) {
        components.addBean(PaymentCatalog.class)
                .type(PaymentCatalog.class)
                .scope(ApplicationScoped.class)
                .qualifier(Default.class)
                .withParam("gateways", gateways.toArray(String[]::new))
                .createWith(PaymentCatalog.Creator.class);
    }
}
