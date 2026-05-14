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

@Qualifier
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface CreditCard {

    final class Literal extends AnnotationLiteral<CreditCard> implements CreditCard {
        public static final Literal INSTANCE = new Literal();
        private static final long serialVersionUID = 1L;

        private Literal() {
        }
    }
}
