package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

@Dependent
public class Resources {

    @Inject
    private Instance<EntityManager> em;

    @Inject
    public Resources(Instance<EntityManager> em) {
        this.em = em;
    }

    public EntityManager getEntityManager() {
        return em.get();
    }
}
