package com.redhat.coolstore.utils;

import jakarta.enterprise.context.Named;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

/**
 * A class that uses CDI to alias Java EE resources, such as the @Resource annotation
 * 
 */
public class Producers {

    private Logger log = Logger.getLogger(Producers.class.getName());

    @Named("logger")
    public Logger getLogger(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}