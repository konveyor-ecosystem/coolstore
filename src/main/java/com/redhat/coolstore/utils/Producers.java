package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Named;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

/**
 * This class is a helper class that provides a logger producer.
 */
public class Producers {

    @Named
    @ApplicationScoped
    public Logger getLogger() {
        return Logger.getLogger(Producers.class.getName());
    }

    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}