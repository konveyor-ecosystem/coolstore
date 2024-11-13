
package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.inject.Named;

@Dependent
public class Resources {

    @PersistenceContext
    private EntityManager em;

    @Named
    public EntityManager getEntityManager() {
        return em;
    }
}
