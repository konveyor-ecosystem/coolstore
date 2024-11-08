
package com.redhat.coolstore.utils;

import jakarta.enterprise.context.Singleton;
import jakarta.inject.Named;
import java.util.logging.Logger;

public class Producers {

    @Singleton
    @Named("logger")
    public Logger produceLog() {
        return Logger.getLogger(Producers.class.getName());
    }

}
