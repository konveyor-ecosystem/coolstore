package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

public class StartupListener {

    @Inject
    Logger log;

    void onStart(StartupEvent startupEvent) {
        log.info("AppListener(started)");
    }

    void onStop(ShutdownEvent shutdownEvent) {
        log.info("AppListener(stopped)");
    }
}
