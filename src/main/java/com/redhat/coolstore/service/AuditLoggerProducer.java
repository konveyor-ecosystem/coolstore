package com.redhat.coolstore.service;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.service.StreamableAuditLogger;

/**
 * Lazily creates the audit-logging-library 2.0.0 StreamableAuditLogger (TCP) and never lets a
 * missing collector break the application (rules audit-logging-0002..0005).
 */
@ApplicationScoped
public class AuditLoggerProducer {

    private static final Logger LOG = Logger.getLogger(AuditLoggerProducer.class.getName());

    @ConfigProperty(name = "audit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "audit.stream.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "audit.stream.port", defaultValue = "5170")
    int port;

    private volatile StreamableAuditLogger logger;
    private volatile boolean failed;

    StreamableAuditLogger logger() {
        if (!enabled || failed) {
            return null;
        }
        if (logger == null) {
            synchronized (this) {
                if (logger == null && !failed) {
                    try {
                        AuditConfiguration config = new AuditConfiguration();
                        config.setStreamProtocol("TCP");
                        config.setStreamHost(host);
                        config.setStreamPort(port);
                        logger = new StreamableAuditLogger(config);
                    } catch (Exception | LinkageError e) {
                        failed = true;
                        LOG.log(Level.WARNING, "Audit collector " + host + ":" + port
                                + " unavailable, audit events will be skipped: " + e.getMessage());
                    }
                }
            }
        }
        return logger;
    }

    public void publish(AuditEvent event) {
        try {
            StreamableAuditLogger l = logger();
            if (l == null) {
                return;
            }
            l.logEventAsync(event).exceptionally(t -> {
                LOG.log(Level.WARNING, "Failed to stream audit event: " + t.getMessage());
                return null;
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to stream audit event: " + e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        if (logger != null) {
            try {
                logger.close();
            } catch (Exception e) {
                LOG.fine("Error closing audit logger: " + e.getMessage());
            }
        }
    }
}
