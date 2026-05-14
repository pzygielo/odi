package org.eclipse.odi.docs.cdi.extension;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PaymentCatalog {
    private final Set<String> gateways;

    private PaymentCatalog(Set<String> gateways) {
        this.gateways = Collections.unmodifiableSet(new LinkedHashSet<>(gateways));
    }

    public Set<String> gateways() {
        return gateways;
    }

    public boolean contains(String gateway) {
        return gateways.contains(gateway);
    }

    public static final class Creator implements SyntheticBeanCreator<PaymentCatalog> {
        @Override
        public PaymentCatalog create(Instance<Object> lookup, Parameters params) {
            String[] gatewayNames = params.get("gateways", String[].class);
            return new PaymentCatalog(new LinkedHashSet<>(Arrays.asList(gatewayNames)));
        }
    }
}
