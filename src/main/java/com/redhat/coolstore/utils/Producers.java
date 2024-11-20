package com.redhat.coolstore.utils;

import jakarta.enterprise.context.Named;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

/**
 * This class is a producer for the Logger.
 */
public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Named
    public Logger getLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}