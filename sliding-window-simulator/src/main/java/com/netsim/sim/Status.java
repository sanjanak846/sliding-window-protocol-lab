package com.netsim.sim;

/** How a simulation run ended. Only COMPLETED and ABORTED_RETRY_LIMIT yield meaningful metrics. */
public enum Status {
    COMPLETED(true, "All packets acknowledged"),
    ABORTED_RETRY_LIMIT(true, "Session aborted: a packet exceeded the retry limit"),
    ABORTED_EVENT_LIMIT(false, "Simulation aborted: event limit reached (metrics invalid)"),
    STALLED(false, "Simulation stalled: no pending events but transfer incomplete (metrics invalid)");

    private final boolean valid;
    private final String description;

    Status(boolean valid, String description) { this.valid = valid; this.description = description; }

    public boolean isValid() { return valid; }
    public String description() { return description; }
}
