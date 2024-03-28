package com.redhat.coolstore.utils;

import org.jboss.logging.Logger;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

public class StartupListener {

    private static final Logger log = Logger.getLogger(StartupListener.class);

    public void onStart(StartupEvent event) {
        log.info("AppListener(onStart)");
    }

    public void onStop(ShutdownEvent event) {
        log.info("AppListener(onStop)");
    }

    public void register(BeanContainer container) {
        container.instance(StartupListener.class).get();
    }

}
