package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;

/** audit-logging-library 2.0.0: record ctor + StreamableAuditLogger TCP + logEventAsync. */
class AuditLoggerTest {

    static AuditEvent event() {
        return new AuditEvent(Instant.now(), "ORDER_CREATED", "user@example.com", null, "coolstore",
                "OrderService", "CREATE_ORDER", "order/1", AuditResult.SUCCESS, "ok",
                Map.of("items", 1), "corr-1", null, null);
    }

    @Test
    void streamsEventOverTcp() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> {
                try (Socket s = server.accept();
                     BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    return r.readLine();
                } catch (Exception e) {
                    return "ERROR " + e;
                }
            });
            AuditConfiguration cfg = new AuditConfiguration();
            cfg.setStreamProtocol("TCP");
            cfg.setStreamHost("localhost");
            cfg.setStreamPort(server.getLocalPort());
            StreamableAuditLogger logger = new StreamableAuditLogger(cfg);
            logger.logEventAsync(event()).get(10, TimeUnit.SECONDS);
            String line = received.get(10, TimeUnit.SECONDS);
            logger.close();
            assertTrue(line != null && line.contains("ORDER_CREATED"), "got: " + line);
        }
    }

    @Test
    void producerToleratesMissingCollector() {
        com.redhat.coolstore.service.AuditLoggerProducer p = new com.redhat.coolstore.service.AuditLoggerProducer();
        assertDoesNotThrow(() -> p.publish(event()));
    }
}
