package com.redhat.coolstore.utils;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/** Replaces the WebLogic ApplicationLifecycleListener. */
@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent evt) {
        log.info("AppListener(postStart)");
    }

    void onStop(@Observes ShutdownEvent evt) {
        log.info("AppListener(preStop)");
    }
}
