package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;
import javax.inject.Singleton;
import javax.inject.Named;

public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Singleton
    @Named("logger")
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}
