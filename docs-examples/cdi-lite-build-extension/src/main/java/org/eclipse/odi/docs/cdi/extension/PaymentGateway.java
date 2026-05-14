package org.eclipse.odi.docs.cdi.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation that the build-compatible extension registers as a CDI
 * qualifier during the discovery phase.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface PaymentGateway {
    /**
     * Identifies the logical payment gateway.
     *
     * @return the logical gateway name
     */
    String value();
}
