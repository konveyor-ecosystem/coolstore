package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {

    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

    public Logger getLog() {
        return Logger.getLogger(Producers.class.getName());
    }
}
