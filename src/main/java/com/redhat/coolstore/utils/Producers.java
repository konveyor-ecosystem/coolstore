package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {

    Logger log;

    @Inject
    public void setLog(Instance<Logger> logger) {
        this.log = logger.get();
    }

    @Singleton
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}
