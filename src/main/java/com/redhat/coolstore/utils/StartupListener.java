package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import jakarta.application.ApplicationLifecycleEvent;
import jakarta.application.ApplicationLifecycleListener;
import jakarta.util.logging.Logger;

public class StartupListener extends ApplicationLifecycleListener {

    @Inject
    Logger log;

    @Override
    public void postStart(ApplicationLifecycleEvent evt) {
        log.info("AppListener(postStart)");
    }

    @Override
    public void preStop(ApplicationLifecycleEvent evt) {
        log.info("AppListener(preStop)");
    }

}
