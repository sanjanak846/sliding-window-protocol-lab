package com.netsim.analysis;

/** Outcome of checking one measured value against one hard requirement. */
public record ConstraintCheck(String name, String requirement, double measured, boolean satisfied, String text) { }
