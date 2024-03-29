package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

@Dependent
public class Resources {

    @Inject
    Instance<EntityManager> em;

    @Inject
    public EntityManager getEntityManager() {
        return em.get();
    }
}
