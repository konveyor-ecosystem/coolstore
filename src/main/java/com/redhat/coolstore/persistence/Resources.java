
package com.redhat.coolstore.persistence;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;

@Dependent
public class Resources {

    @Inject
    private EntityManager em;

    @Named
    public EntityManager getEntityManager() {
        return em;
    }
}
