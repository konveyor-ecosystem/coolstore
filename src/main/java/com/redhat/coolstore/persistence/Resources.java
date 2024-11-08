
package com.redhat.coolstore.persistence;

import javax.enterprise.inject.Inject;
import javax.inject.Qualifier;
import javax.persistence.EntityManager;

@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendedContext {
}

public class Resources {

    @Inject
    @ExtendedContext
    private EntityManager em;
}
