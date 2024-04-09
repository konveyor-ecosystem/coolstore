package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.persistence.EntityManager;

@Default
@Dependent
public class Resources {

    @Inject
    @ExtendedContext
    private EntityManager em;

    @Named("ExtendedContext")
    @ExtendedContext
    public EntityManager getEntityManager() {
        return em;
    }
}

@Qualifier
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendedContext {
}
