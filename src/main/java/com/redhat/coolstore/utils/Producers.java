
package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

public class Producers {

    @Named
    public Logger getLog() {
        return Logger.getLogger(Producers.class.getName());
    }

    @ApplicationScoped
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}