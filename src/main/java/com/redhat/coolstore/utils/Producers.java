package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    private Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

    @Named
    public Logger getProduceLog() {
        return produceLog(null);
    }

}
