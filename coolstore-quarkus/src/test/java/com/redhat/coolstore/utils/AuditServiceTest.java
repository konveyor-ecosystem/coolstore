package com.redhat.coolstore.utils;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AuditServiceTest {

    @Test
    void testAuditServiceWritesToFilesystem(@TempDir Path tempDir) throws Exception {
        AuditService auditService = new AuditService();

        // Set the logDirectory via field injection simulation
        Field logDirField = AuditService.class.getDeclaredField("logDirectory");
        logDirField.setAccessible(true);
        logDirField.set(auditService, tempDir.toString());

        auditService.init();
        auditService.logEvent("ORDER_PLACED", "ORD-001", "Test order placed");

        Path logFile = tempDir.resolve("audit.log");
        assertTrue(Files.exists(logFile), "Audit log file should exist");

        String content = Files.readString(logFile);
        assertTrue(content.contains("ORDER_PLACED"), "Log should contain event type");
        assertTrue(content.contains("ORD-001"), "Log should contain entity ID");
        assertTrue(content.contains("Test order placed"), "Log should contain details");
    }

    @Test
    void testLogOrderEvent(@TempDir Path tempDir) throws Exception {
        AuditService auditService = new AuditService();

        Field logDirField = AuditService.class.getDeclaredField("logDirectory");
        logDirField.setAccessible(true);
        logDirField.set(auditService, tempDir.toString());

        auditService.init();
        auditService.logOrderEvent("ORD-001", "saved", "Order persisted successfully");

        Path logFile = tempDir.resolve("audit.log");
        String content = Files.readString(logFile);
        assertTrue(content.contains("ORDER_SAVED"), "Log should contain ORDER_SAVED event");
        assertTrue(content.contains("ORD-001"), "Log should contain order ID");
    }
}
