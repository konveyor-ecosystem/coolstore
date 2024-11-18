
package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

/**
 * This class is a producer for the Logger.
 */
public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Produces
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}
