package com.netsim.sim;

/**
 * Internal event kinds. The declaration order IS the tie-break order for events with the same
 * timestamp: arrivals are processed before timers, so an ACK arriving exactly when a timer expires
 * prevents a spurious timeout.
 */
public enum EventType { DATA_ARRIVAL, ACK_ARRIVAL, TRANSMIT, TIMER }
