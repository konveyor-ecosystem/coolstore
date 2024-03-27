package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Named;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class Resources {

    @Inject
    private EntityManager em;

    @Named
    public EntityManager getEntityManager() {
        return em;
    }
}