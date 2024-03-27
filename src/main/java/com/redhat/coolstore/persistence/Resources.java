package com.redhat.coolstore.persistence;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Dependent
public class Resources {

    @PersistenceContext
    private EntityManager em;

    @Inject
    public EntityManager getEntityManager() {
        return em;
    }
}