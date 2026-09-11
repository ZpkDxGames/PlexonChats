package com.antondev.chats.diagnostics;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Low-overhead in-memory observability. Never performs provider, storage or network work. */
public final class ChatDiagnostics {
    public static final String OWNERSHIP = "PAPER_CHAT_EVENT -> PLEXONCHATS -> PLEXON_CHAT_EVENT -> SINGLE_DELIVERY";

    private final LongAdder nativeObserved = new LongAdder();
    private final LongAdder publicMessagesDelivered = new LongAdder();
    private final LongAdder recipientDeliveries = new LongAdder();
    private final LongAdder customEventCancellations = new LongAdder();
    private final LongAdder formatFailures = new LongAdder();
    private final AtomicReference<String> lastReload = new AtomicReference<>("STARTUP_PENDING");
    private final AtomicReference<String> recentIntegrationFailure = new AtomicReference<>("NONE");

    public void nativeObserved() { nativeObserved.increment(); }
    public void publicDelivered(int recipients) {
        if (recipients <= 0) return;
        publicMessagesDelivered.increment();
        recipientDeliveries.add(recipients);
    }
    public void customEventCancelled() { customEventCancellations.increment(); }
    public void formatFailure() { formatFailures.increment(); }
    public void recordReload(boolean success, String detail) {
        lastReload.set((success ? "SUCCESS" : "FAILED") + (detail == null || detail.isBlank() ? "" : " — " + detail));
    }
    public void recordIntegrationFailure(String integration, Throwable error) {
        String type = error == null ? "unknown" : error.getClass().getSimpleName();
        recentIntegrationFailure.set(integration + ": " + type);
    }

    public long nativeObservedCount() { return nativeObserved.sum(); }
    public long publicMessagesDeliveredCount() { return publicMessagesDelivered.sum(); }
    public long recipientDeliveriesCount() { return recipientDeliveries.sum(); }
    public long customEventCancellationsCount() { return customEventCancellations.sum(); }
    public long formatFailuresCount() { return formatFailures.sum(); }
    public String lastReload() { return lastReload.get(); }
    public String recentIntegrationFailure() { return recentIntegrationFailure.get(); }
}
