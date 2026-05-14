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

public class PaymentBuildExtension implements BuildCompatibleExtension {
    private final Set<String> gateways = new TreeSet<>();

    @Discovery
    void registerPaymentGatewayQualifier(MetaAnnotations metaAnnotations) {
        metaAnnotations.addQualifier(PaymentGateway.class);
    }

    @Registration(types = Object.class)
    void collectPaymentGatewayBeans(BeanInfo bean) {
        if (bean.isClassBean() && bean.declaringClass().hasAnnotation(PaymentGateway.class)) {
            gateways.add(bean.declaringClass()
                    .annotation(PaymentGateway.class)
                    .value()
                    .asString());
        }
    }

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
