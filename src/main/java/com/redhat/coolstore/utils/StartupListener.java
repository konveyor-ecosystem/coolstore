package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;

public class StartupListener {

    @Inject
    Logger log;

    public void postStart(ApplicationLifecycleEvent evt) {
        log.info("AppListener(postStart)");
    }

    public void preStop(ApplicationLifecycleEvent evt) {
        log.info("AppListener(preStop)");
    }

}
