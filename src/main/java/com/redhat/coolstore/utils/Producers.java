package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Producers {

    Logger log = Logger.getLogger(Producers.class);

    @Named("produceLog")
    public Logger getProduceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }
}