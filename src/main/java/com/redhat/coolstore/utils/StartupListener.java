package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

public class StartupListener {

    @Inject
    Logger log;

    void onStart(StartupEvent ev) {
        log.info("AppListener(postStart)");
    }

    void onStop(ShutdownEvent ev) {
        log.info("AppListener(preStop)");
    }

}
