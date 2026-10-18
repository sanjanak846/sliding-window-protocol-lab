package com.netsim.sim;

/** Optional observer notified of every logged event as it is generated. */
@FunctionalInterface
public interface SimulationListener {
    void onEvent(SimulationEvent event);
}
