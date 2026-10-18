package com.netsim.ui;

/** Plain main class (does not extend Application) so the app also starts from a fat classpath / IDE run config. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) { MainApp.main(args); }
}
