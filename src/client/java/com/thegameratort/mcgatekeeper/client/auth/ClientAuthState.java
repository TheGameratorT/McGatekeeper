package com.thegameratort.mcgatekeeper.client.auth;

public class ClientAuthState {

    private static boolean awaitingAdmin = false;
    private static long startMs = 0L;
    private static int timeoutSeconds = 0;

    public static void setAwaitingAdmin(int timeoutSeconds) {
        awaitingAdmin = true;
        startMs = System.currentTimeMillis();
        ClientAuthState.timeoutSeconds = timeoutSeconds;
    }

    public static boolean isAwaitingAdmin() {
        return awaitingAdmin;
    }

    public static long getStartMs() {
        return startMs;
    }

    public static int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static void clear() {
        awaitingAdmin = false;
        startMs = 0L;
        timeoutSeconds = 0;
    }
}
