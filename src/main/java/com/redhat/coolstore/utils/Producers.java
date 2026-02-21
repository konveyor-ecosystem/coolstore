package com.redhat.coolstore.utils;

import jakarta.inject.Named;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Named
    public Logger getLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}