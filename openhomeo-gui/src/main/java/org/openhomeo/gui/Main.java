package org.openhomeo.gui;

/**
 * Standard JavaFX Fat-Jar / Classpath Launcher Entry Point.
 *
 * Prevents the JVM launcher from enforcing module-path JavaFX requirements
 * when launching OpenHomeo from standard classpath fat jars or batch scripts.
 */
public class Main {
    public static void main(String[] args) {
        OpenHomeoApp.main(args);
    }
}
