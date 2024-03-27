package com.redhat.coolstore.utils;

import jakarta.inject.Named;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {

    private Logger log = Logger.getLogger(Producers.class.getName());

    @Named
    public Logger getLog() {
        return log;
    }

    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}