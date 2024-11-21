
package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Named;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

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
