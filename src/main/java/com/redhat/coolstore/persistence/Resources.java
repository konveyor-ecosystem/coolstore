package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;

@Dependent
public class Resources {

    private EntityManager em;

    @Inject
    public void setEntityManager(EntityManager em) {
        this.em = em;
    }

    @Named
    public EntityManager getEntityManager() {
        return em;
    }
}