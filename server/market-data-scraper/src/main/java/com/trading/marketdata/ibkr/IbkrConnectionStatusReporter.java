package com.trading.marketdata.ibkr;

import com.trading.marketdata.service.CollectorStatusRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Exposes the shared IBKR transport as a first-class operational dependency. */
@Component
public class IbkrConnectionStatusReporter {
    private final IbkrConnectionManager connection;
    private final CollectorStatusRegistry registry;
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private volatile Instant lastConnect;
    private volatile Instant lastDisconnect;
    private volatile String lastReason;

    public IbkrConnectionStatusReporter(IbkrConnectionManager connection, CollectorStatusRegistry registry) {
        this.connection = connection;
        this.registry = registry;
    }

    @PostConstruct
    public void initialize() { publish(); }

    @EventListener(IbkrConnectedEvent.class)
    public void connected(IbkrConnectedEvent ignored) {
        lastConnect = Instant.now();
        reconnectAttempts.set(0);
        lastReason = null;
        publish();
    }

    @EventListener(IbkrDisconnectedEvent.class)
    public void disconnected(IbkrDisconnectedEvent event) {
        lastDisconnect = Instant.now();
        lastReason = event.reason();
        reconnectAttempts.incrementAndGet();
        publish();
    }

    @Scheduled(fixedDelayString = "${collector-status.ibkr-refresh-ms:30000}")
    public void refresh() {
        if (!connection.isConnected()) reconnectAttempts.incrementAndGet();
        publish();
    }

    private void publish() {
        boolean connected = connection.isConnected();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("host", connection.getHost());
        metrics.put("port", connection.getPort());
        metrics.put("clientId", connection.getClientId());
        metrics.put("connected", connected);
        metrics.put("reconnectAttempts", reconnectAttempts.get());
        if (lastConnect != null) metrics.put("lastConnect", lastConnect);
        if (lastDisconnect != null) metrics.put("lastDisconnect", lastDisconnect);
        registry.reportGlobal("ibkrConnection", connected ? "OK" : "ERROR",
                connected ? "COMPLETED" : "FAILED", metrics,
                connected ? null : (lastReason == null ? "IBKR connection unavailable" : lastReason));
    }
}
