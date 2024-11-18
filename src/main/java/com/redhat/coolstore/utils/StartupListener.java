
package com.redhat.coolstore.utils;

import jakarta.inject.Inject; // Updated import statement for dependency injection
import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;

import java.util.logging.Logger;

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