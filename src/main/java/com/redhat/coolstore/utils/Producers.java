package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Named;
import java.util.logging.Logger;

public class Producers {

    @Named("producerLog")
    Logger log = Logger.getLogger(Producers.class.getName());

    public Logger produceLog(Instance<Logger> loggers) {
        return loggers.get();
    }
}
