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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Singleton
final class OdiAnnotationsImpl implements OdiAnnotations {
    private final BeanProvider<RuntimeMetaAnnotations> metaAnnotations;

    OdiAnnotationsImpl(BeanProvider<RuntimeMetaAnnotations> metaAnnotations) {
        this.metaAnnotations = metaAnnotations;
    }

    @Override
    public boolean isDependent(Class<? extends Annotation> annotationType) {
        return annotationType == Dependent.class;
    }

    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isScope(annotationType);
    }

    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isNormalScope(annotationType);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isQualifier(annotationType);
    }

    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isInterceptorBinding(annotationType);
    }

    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isStereotype(annotationType);
    }

    @Override
    @Nullable
    public <T1> Qualifier<T1> resolveQualifier(Annotation... annotations) {
        if (annotations == null || annotations.length == 0) {
            return null;
        }
        AnnotationMetadata annotationMetadata = annotationMetadataFromQualifierAnnotations(annotations);
        return qualifierFromQualifierAnnotations(annotationMetadata, annotations);
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotationMetadata The annotation metadata
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    @SuppressWarnings("unchecked")
    private <U> Qualifier<U> qualifierFromQualifierAnnotations(
            AnnotationMetadata annotationMetadata,
            Annotation... annotations) {
        if (annotations.length > 0) {
            List<Qualifier<U>> qualifiers = new ArrayList<>(annotations.length);
            Map<Class<? extends Annotation>, List<Annotation>> groupedAnnotations = groupQualifierAnnotations(annotations);
            for (Map.Entry<Class<? extends Annotation>, List<Annotation>> entry : groupedAnnotations.entrySet()) {
                Class<? extends Annotation> annotationClass = entry.getKey();
                if (entry.getValue().size() == 1) {
                    qualifiers.add((Qualifier<U>) AnnotationUtils.byAnnotation(annotationMetadata, annotationClass));
                } else {
                    annotationMetadata.findRepeatableAnnotation(annotationClass.getName())
                            .orElseThrow(() -> new IllegalArgumentException("Qualifier cannot be duplicated for type: " + annotationClass.getName()));
                    for (Annotation annotation : entry.getValue()) {
                        qualifiers.add((Qualifier<U>) Qualifiers.byAnnotation(
                                annotationMetadata,
                                AnnotationReflection.toAnnotationValue(annotation)
                        ));
                    }
                }
            }
            return qualifiers.size() == 1
                    ? qualifiers.get(0)
                    : Qualifiers.byQualifiers(qualifiers.toArray(new Qualifier[0]));
        }
        return null;
    }

    private AnnotationMetadata annotationMetadataFromQualifierAnnotations(Annotation[] annotations) {
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        Map<String, Integer> qualifierCounts = qualifierCounts(annotations);
        for (Annotation annotation : annotations) {
            if (metaAnnotations.get().isQualifier(annotation)) {
                if (AnnotationUtils.isAny(annotation)) {
                    annotationMetadata.addDeclaredAnnotation(Any.NAME, Collections.emptyMap());
                    annotationMetadata.addDeclaredStereotype(
                            List.of(Any.NAME),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.emptyMap()
                    );
                } else {
                    String[] nonBinding = metaAnnotations.get().getQualifierNonBinding(annotation).toArray(new String[0]);
                    AnnotationValue<Annotation> value = AnnotationReflection.toAnnotationValue(annotation);
                    String annotationName = value.getAnnotationName();
                    final Map<CharSequence, Object> values = new LinkedHashMap<>(value.getValues());
                    if (qualifierCounts.getOrDefault(annotationName, 0) > 1) {
                        String repeatableContainer = annotationMetadata.findRepeatableAnnotation(annotationName).orElse(null);
                        if (repeatableContainer != null) {
                            annotationMetadata.addDeclaredRepeatable(repeatableContainer, value);
                        } else {
                            annotationMetadata.addDeclaredAnnotation(annotationName, values);
                        }
                    } else {
                        annotationMetadata.addDeclaredAnnotation(annotationName, values);
                    }
                    annotationMetadata.addDeclaredStereotype(
                            List.of(annotationName),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.singletonMap(
                                    AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBinding
                            )
                    );
                }
            }
        }
        return annotationMetadata;
    }

    private Map<String, Integer> qualifierCounts(Annotation[] annotations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            if (metaAnnotations.get().isQualifier(annotation)) {
                counts.merge(AnnotationUtils.findAnnotationClass(annotation).getName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<Class<? extends Annotation>, List<Annotation>> groupQualifierAnnotations(Annotation[] annotations) {
        Map<Class<? extends Annotation>, List<Annotation>> grouped = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationClass = AnnotationUtils.findAnnotationClass(annotation);
            if (!isQualifier(annotationClass)) {
                throw new IllegalArgumentException("Not a valid qualifier annotation type: " + annotationClass.getName());
            }
            grouped.computeIfAbsent(annotationClass, ignored -> new ArrayList<>()).add(annotation);
        }
        return grouped;
    }
}
