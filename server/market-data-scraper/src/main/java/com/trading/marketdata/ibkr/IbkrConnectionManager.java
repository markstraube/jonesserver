package com.trading.marketdata.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final IbkrWrapper wrapper;
    private final EJavaSignal signal = new EJavaSignal();
    private EClientSocket client;
    private EReader reader;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger reqIdSeq = new AtomicInteger(1000);

    public IbkrConnectionManager(IbkrWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @PostConstruct
    public void connect() {
        client = new EClientSocket(wrapper, signal);
        wrapper.setClient(client);

        log.info("Connecting to IB Gateway at {}:{} (clientId={})", host, port, clientId);
        client.eConnect(host, port, clientId);

        // Give the socket a moment to establish
        long deadline = System.currentTimeMillis() + connectTimeoutMs;
        while (!client.isConnected() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        if (!client.isConnected()) {
            log.error("IB Gateway connection failed — scraper will run without IBKR data. " +
                      "Check that IB Gateway is running on {}:{} with API enabled.", host, port);
            return;
        }

        // Start the reader thread — this is mandatory in the IBKR Java API
        reader = new EReader(client, signal);
        reader.setDaemon(true);
        reader.start();

        // Message dispatch loop in a virtual thread
        Thread.ofVirtual().name("ibkr-reader-loop").start(() -> {
            while (client.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    log.warn("IBKR reader error: {}", e.getMessage());
                }
            }
            connected.set(false);
            log.warn("IB Gateway disconnected.");
        });

        connected.set(true);
        log.info("IB Gateway connected successfully.");
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
