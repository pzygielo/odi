package org.eclipse.odi.cdispec;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@OdiTest
@Disabled("BUG: InjectionPoint metadata runtime support pending in core: https://github.com/micronaut-projects/micronaut-core/pull/6058")
public class InjectionPointTest {

    @Test
    void testInjectionPoints(SomeService someService) {
        someService.toString();
    }

    @Test
    void tesDependentInjectionPoints(SomeWithRefService someService) {
    }

}

@Dependent
class SomeService extends AbstractService {

    SomeService(InjectionPoint constructorEventInjectionPoint) {
        super(constructorEventInjectionPoint);
    }
}

@Dependent
class SomeWithRefService extends AbstractService {

    private final DependentService constructorInjectService;
    @Inject
    private DependentService fieldInjectService;
    private DependentService methodInjectService;

    SomeWithRefService(InjectionPoint constructorEventInjectionPoint, DependentService constructorInjectService) {
        super(constructorEventInjectionPoint);
        this.constructorInjectService = constructorInjectService;
    }

    @Inject
    public void setMethodInjectService(DependentService methodInjectService) {
        this.methodInjectService = methodInjectService;
    }

    public DependentService getConstructorInjectService() {
        return constructorInjectService;
    }

    public DependentService getFieldInjectService() {
        return fieldInjectService;
    }

    public DependentService getMethodInjectService() {
        return methodInjectService;
    }
}

@Dependent
class DependentService extends AbstractService {

    DependentService(InjectionPoint constructorEventInjectionPoint) {
        super(constructorEventInjectionPoint);
    }
}

abstract class AbstractService {

    private final InjectionPoint constructorEventInjectionPoint;
    @Inject
    private InjectionPoint fieldEventInjectionPoint;
    @Inject
    private io.micronaut.inject.InjectionPoint micronautFieldEventInjectionPoint;
    private InjectionPoint methodEventInjectionPoint;

    AbstractService(InjectionPoint constructorEventInjectionPoint) {
        this.constructorEventInjectionPoint = constructorEventInjectionPoint;
    }

    @Inject
    public void setMethodEventInjectionPoint(InjectionPoint methodEventInjectionPoint) {
        this.methodEventInjectionPoint = methodEventInjectionPoint;
    }

    public InjectionPoint getConstructorEventInjectionPoint() {
        return constructorEventInjectionPoint;
    }

    public InjectionPoint getFieldEventInjectionPoint() {
        return fieldEventInjectionPoint;
    }

    public InjectionPoint getMethodEventInjectionPoint() {
        return methodEventInjectionPoint;
    }
}
