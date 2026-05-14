package org.eclipse.odi.docs.cdi;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Qualifier used to select the credit-card implementation of {@link PaymentProcessor}.
 */
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface CreditCard {

    /**
     * Runtime literal used for programmatic lookup with {@code Instance.select(...)}.
     */
    final class Literal extends AnnotationLiteral<CreditCard> implements CreditCard {
        /**
         * Singleton literal instance.
         */
        public static final Literal INSTANCE = new Literal();
        private static final long serialVersionUID = 1L;

        private Literal() {
        }
    }
}
