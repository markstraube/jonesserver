package com.trading.marketdata.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the TCP connection lifecycle to IB Gateway.
 *
 * IB Gateway must be running on localhost:4001 with API enabled.
 * This component connects on startup and attempts reconnection on disconnect.
 *
 * The EJavaSignal / EReader pattern is the official IBKR threading model:
 *   - EClientSocket sends requests over the socket
 *   - EReader reads incoming messages in a dedicated thread
 *   - EJavaSignal wakes the reader thread when data arrives
 *   - IbkrWrapper receives decoded callbacks on the reader thread
 */
@Component
public class IbkrConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(IbkrConnectionManager.class);

    @Value("${ibkr.host:127.0.0.1}")
    private String host;

    @Value("${ibkr.port:4001}")
    private int port;

    @Value("${ibkr.client-id:1}")
    private int clientId;

    @Value("${ibkr.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${ibkr.reconnect-interval-ms:30000}")
    private int reconnectIntervalMs;

    private final IbkrWrapper wrapper;
    private final ApplicationEventPublisher eventPublisher;
    private volatile EJavaSignal signal;
    private volatile EClientSocket client;
    private volatile EReader reader;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger reqIdSeq = new AtomicInteger(1000);

    public IbkrConnectionManager(IbkrWrapper wrapper, ApplicationEventPublisher eventPublisher) {
        this.wrapper = wrapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void connect() {
        doConnect();
    }

    /**
     * Watchdog for reconnection. Nothing else in this class (or in IbkrWrapper's
     * connectionClosed() callback) ever re-establishes the socket after a drop —
     * previously, once IB Gateway closed the connection (nightly restart, network
     * blip, error 502 "Couldn't connect to TWS", etc.) isConnected() stayed false
     * forever and every downstream call silently fell back to Yahoo/Barchart until
     * the process was restarted by hand.
     *
     * Runs on a fixed delay; skips instantly if already connected, and the
     * reconnecting flag prevents overlapping attempts if a connect attempt is
     * still blocking past one tick of the schedule.
     */
    @Scheduled(fixedDelayString = "${ibkr.reconnect-interval-ms:30000}")
    public void reconnectWatchdog() {
        if (isConnected()) return;
        if (!reconnecting.compareAndSet(false, true)) return;
        try {
            log.warn("IB Gateway not connected — attempting reconnect...");
            doConnect();
        } finally {
            reconnecting.set(false);
        }
    }

    private void doConnect() {
        // If a previous socket is still marked connected (e.g. the reader thread hasn't
        // noticed the drop yet), force it closed first so we never end up with two live
        // EReader threads.
        EClientSocket stale = client;
        if (stale != null && stale.isConnected()) {
            try { stale.eDisconnect(); } catch (Exception ignored) { }
        }

        // Fresh signal per connection attempt — EJavaSignal/EReader are tied together,
        // reusing one across reconnects risks one reader's wakeup being consumed by
        // the other reader's thread.
        EJavaSignal newSignal = new EJavaSignal();
        EClientSocket newClient = new EClientSocket(wrapper, newSignal);
        wrapper.setClient(newClient);

        log.info("Connecting to IB Gateway at {}:{} (clientId={})", host, port, clientId);
        newClient.eConnect(host, port, clientId);

        // Give the socket a moment to establish
        long deadline = System.currentTimeMillis() + connectTimeoutMs;
        while (!newClient.isConnected() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        if (!newClient.isConnected()) {
            log.error("IB Gateway connection failed — will retry in the background. " +
                      "Check that IB Gateway is running on {}:{} with API enabled.", host, port);
            return;
        }

        // Start the reader thread — this is mandatory in the IBKR Java API
        EReader newReader = new EReader(newClient, newSignal);
        newReader.setDaemon(true);
        newReader.start();

        client = newClient;
        reader = newReader;
        signal = newSignal;

        // Message dispatch loop in a virtual thread. Deliberately closes over the local
        // newClient/newReader/newSignal (not the mutable fields) so this loop stays bound
        // to the connection it was started for, even if a later reconnect reassigns the
        // instance fields out from under it.
        Thread.ofVirtual().name("ibkr-reader-loop").start(() -> {
            while (newClient.isConnected()) {
                newSignal.waitForSignal();
                try {
                    newReader.processMsgs();
                } catch (Exception e) {
                    log.warn("IBKR reader error: {}", e.getMessage());
                }
            }
            // Only clear the shared connected flag if we're still the active connection —
            // otherwise an old dying thread could clobber a newer, already-successful reconnect.
            if (client == newClient) {
                connected.set(false);
                // Covers silent socket deaths where the connectionClosed callback never fires.
                // Can double-fire with that callback for one drop — listeners are idempotent.
                eventPublisher.publishEvent(new IbkrDisconnectedEvent("reader loop exit"));
            }
            log.warn("IB Gateway disconnected. Reconnect watchdog will retry within {} ms.", reconnectIntervalMs);
        });

        connected.set(true);
        log.info("IB Gateway connected successfully.");
        // SubscriptionManager re-establishes all Book streams. During the initial
        // @PostConstruct connect listeners may not exist yet — that case is covered by the
        // SubscriptionManager's ApplicationReadyEvent check.
        eventPublisher.publishEvent(new IbkrConnectedEvent());
    }

    @PreDestroy
    public void disconnect() {
        if (client != null && client.isConnected()) {
            log.info("Disconnecting from IB Gateway...");
            client.eDisconnect();
        }
    }

    /** Returns a unique request ID for each API call. */
    public int nextReqId() {
        return reqIdSeq.incrementAndGet();
    }

    public EClientSocket getClient() {
        return client;
    }

    public boolean isConnected() {
        return client != null && client.isConnected() && connected.get();
    }
}
