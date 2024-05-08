package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.runtime.annotations.Template;

@Template
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes StartupEvent ev) {
        log.info("AppListener(postStart)");
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("AppListener(preStop)");
    }

}
