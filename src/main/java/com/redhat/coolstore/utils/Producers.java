
package com.redhat.coolstore.utils;

import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;
import java.util.logging.Logger;

public class Producers {

    Logger log = Logger.getLogger(Producers.class.getName());

    @Named
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

}