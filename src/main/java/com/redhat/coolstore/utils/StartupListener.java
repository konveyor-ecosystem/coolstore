package com.redhat.coolstore.utils;

import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    @Startup
    void postStart() {
        log.info("AppListener(postStart)");
    }

    @Shutdown
    void preStop() {
        log.info("AppListener(preStop)");
    }

}
