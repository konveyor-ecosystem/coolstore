package com.redhat.coolstore.utils;

import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;

import jakarta.inject.Inject;
import java.util.logging.Logger;

public class StartupListener extends ApplicationLifecycleListener {

    @Inject
    Logger log;

    @Override
    public void postStart(ApplicationLifecycleEvent evt) {
        log.info(&#34;AppListener(postStart)&#34;);
    }

    @Override
    public void preStop(ApplicationLifecycleEvent evt) {
        log.info(&#34;AppListener(preStop)&#34;);
    }

}
