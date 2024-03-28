package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.arc.ApplicationScoped;

@ApplicationScoped
public class StartupListener {

    @Inject
    Log log;

    void onStart(StartupEvent ev) {
        log.info("AppListener(onStart)");
    }

    void onStop(ShutdownEvent ev) {
        log.info("AppListener(onStop)");
    }

}
