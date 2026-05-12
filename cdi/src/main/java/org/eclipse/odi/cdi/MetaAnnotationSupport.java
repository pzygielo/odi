/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.odi.cdi;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Singleton;

final class MetaAnnotationSupport {
    public static final String META_ANNOTATION_SCOPE = AnnotationUtil.SCOPE;
    public static final String META_ANNOTATION_SINGLETON = AnnotationUtil.SINGLETON;
    public static final String META_ANNOTATION_NAMED = AnnotationUtil.NAMED;
    public static final String META_ANNOTATION_QUALIFIER = AnnotationUtil.QUALIFIER;

    private MetaAnnotationSupport() {
    }

    static Class<? extends Annotation> resolveDeclaredScope(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            return Dependent.class;
        }
        final List<String> stereotypes = annotationMetadata.getAnnotationNamesByStereotype(Stereotype.class);
        final List<String> declaredAnnotations = new ArrayList<>(annotationMetadata.getDeclaredAnnotationNames());
        final List<String> scopeStereotypes = new ArrayList<>(annotationMetadata.getDeclaredAnnotationNamesByStereotype(
                META_ANNOTATION_SCOPE));
        purgeInternalScopes(stereotypes, scopeStereotypes);
        String n = scopeStereotypes.stream()
                .filter(declaredAnnotations::contains)
                .findFirst()
                .orElseGet(() -> scopeStereotypes.isEmpty() ? null : scopeStereotypes.iterator().next());
        if (n == null) {
            scopeStereotypes.addAll(annotationMetadata.getAnnotationNamesByStereotype(META_ANNOTATION_SCOPE));
            purgeInternalScopes(stereotypes, scopeStereotypes);
            if (!scopeStereotypes.isEmpty()) {
                n = scopeStereotypes.iterator().next();
            }
        }
        Class<? extends Annotation> scope;
        if (n == null) {
            scope = annotationMetadata
                    .getAnnotationTypeByStereotype(META_ANNOTATION_SCOPE)
                    .orElseGet(() ->
                        annotationMetadata.getAnnotationTypeByStereotype(NormalScope.class)
                                .orElse(Dependent.class)
                    );
        } else if (META_ANNOTATION_SINGLETON.equals(n)) {
            scope = Singleton.class;
        } else {
            scope = annotationMetadata
                    .getAnnotationType(n)
                    .orElse(Dependent.class);
        }
        return scope;
    }

    private static void purgeInternalScopes(List<String> stereotypes, List<String> scopeStereotypes) {
        // deal with stereotypes with scopes
        scopeStereotypes.removeAll(stereotypes);
        // filter internal annotations
        scopeStereotypes.remove("io.micronaut.runtime.context.scope.ScopedProxy");
    }
}
