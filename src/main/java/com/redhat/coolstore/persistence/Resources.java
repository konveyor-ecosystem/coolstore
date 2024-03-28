package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
public class Resources {

    @PersistenceContext
    private EntityManager em;

    @Inject
    @Named("myEntityManager")
    public void setEntityManager(EntityManager em) {
        this.em = em;
    }

    public EntityManager getEm() {
        return em;
    }
}
