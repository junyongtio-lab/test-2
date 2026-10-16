package com.universalremote.app.protocols;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A host, never a user-provided URL. LAN DNS results are checked on every connection. */
public final class DeviceEndpoint {
    private final TvPlatform platform;
    private final String host;
    private final String credential;
    private final int port;

    public DeviceEndpoint(TvPlatform platform, String host, String credential) {
        this(platform, host, credential, defaultPort(platform));
    }

    // A package-private override lets fake TV tests use ephemeral local ports.
    DeviceEndpoint(TvPlatform platform, String host, String credential, int port) {
        if (platform == null) throw new IllegalArgumentException("Choose a TV platform.");
        this.platform = platform;
        this.host = validateHost(host);
        if (credential != null && (credential.length() > 4096 || containsControl(credential))) {
            throw new IllegalArgumentException("Invalid TV pairing credential.");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid TV port.");
        this.credential = credential == null || credential.isEmpty() ? null : credential;
        this.port = port;
    }

    private static int defaultPort(TvPlatform platform) {
        if (platform == null) return 0;
        switch (platform) {
            case LG_WEBOS: return 3000;
            case SAMSUNG_TIZEN: return 8001;
            case PANASONIC_VIERA: return 55000;
            case HISENSE_ROKU: return 8060;
            default: throw new IllegalArgumentException("Unknown platform.");
        }
    }

    public TvPlatform getPlatform() { return platform; }
    public String getHost() { return host; }
    public String getCredential() { return credential; }
    public int getPort() { return port; }

    public static String validateHost(String input) {
        if (input == null || input.isEmpty() || input.length() > 253 || containsControl(input)
                || !input.matches("[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException("Enter a local IPv4 address or TV hostname, without http://, a port, or a path.");
        }
        String host = input.toLowerCase(Locale.US);
        if (host.matches("[0-9.]+")) {
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Enter a complete local IPv4 address.");
            byte[] raw = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (parts[i].isEmpty() || parts[i].length() > 3
                        || (parts[i].length() > 1 && parts[i].startsWith("0"))) {
                    throw new IllegalArgumentException("Enter IPv4 numbers without leading zeroes.");
                }
                int value;
                try { value = Integer.parseInt(parts[i]); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid IPv4 address."); }
                if (value > 255) throw new IllegalArgumentException("Invalid IPv4 address.");
                raw[i] = (byte) value;
            }
            try {
                if (!isLocalAddress(InetAddress.getByAddress(raw))) {
                    throw new IllegalArgumentException("Use the TV's private local network address.");
                }
            } catch (UnknownHostException impossible) { throw new IllegalArgumentException("Invalid address."); }
        } else {
            for (String label : host.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                    throw new IllegalArgumentException("Invalid TV hostname.");
                }
            }
        }
        return host;
    }

    static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127) return true;
        }
        return false;
    }

    public static boolean isLocalAddress(InetAddress address) {
        if (!(address instanceof Inet4Address) || address.isAnyLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        // The TV is a unicast endpoint; reject conventional directed broadcasts.
        if ((b[3] & 255) == 255) return false;
        return address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress();
    }

    /** Reject public and mixed public/private DNS answers, including rebinding. */
    static List<InetAddress> resolveLocal(String hostname) throws UnknownHostException {
        try { validateHost(hostname); }
        catch (IllegalArgumentException e) { throw new UnknownHostException("Invalid local TV hostname."); }
        InetAddress[] answers = InetAddress.getAllByName(hostname);
        List<InetAddress> local = new ArrayList<>();
        for (InetAddress answer : answers) {
            if (!(answer instanceof Inet4Address)) continue;
            if (!isLocalAddress(answer)) throw new UnknownHostException("The TV hostname resolves outside the local network.");
            local.add(answer);
        }
        if (local.isEmpty()) throw new UnknownHostException("No local IPv4 address found for the TV.");
        return local;
    }

    static boolean sameLocalTarget(String first, String second) throws IOException {
        List<InetAddress> left = resolveLocal(first);
        List<InetAddress> right = resolveLocal(second);
        for (InetAddress a : left) if (right.contains(a)) return true;
        return false;
    }
}
