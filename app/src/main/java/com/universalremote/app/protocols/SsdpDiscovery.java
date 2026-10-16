package com.universalremote.app.protocols;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One standards-based multicast search, without scanning host or port ranges. */
public final class SsdpDiscovery {
    private SsdpDiscovery() { }

    public static List<DiscoveredDevice> discover(int timeoutMs) throws IOException {
        if (timeoutMs < 100 || timeoutMs > 15000) throw new IllegalArgumentException("Invalid discovery timeout.");
        Map<String, DiscoveredDevice> devices = new LinkedHashMap<>();
        byte[] search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"
                + "MAN: \"ssdp:discover\"\r\nMX: 2\r\n"
                + "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        long deadline = System.nanoTime() + timeoutMs * 1000000L;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(false);
            socket.send(new DatagramPacket(search, search.length,
                    InetAddress.getByName("239.255.255.250"), 1900));
            byte[] buffer = new byte[8192];
            // Cap traffic processing even on a hostile or busy local network.
            int packets = 0;
            while (System.nanoTime() < deadline && packets++ < 256) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Discovery cancelled.");
                int left = (int) Math.max(1, (deadline - System.nanoTime()) / 1000000L);
                socket.setSoTimeout(Math.min(400, left));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); }
                catch (SocketTimeoutException timeout) { continue; }
                DiscoveredDevice device = parseResponse(
                        new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.US_ASCII),
                        packet.getAddress());
                if (device != null) devices.put(device.getHost(), device);
            }
        }
        return new ArrayList<>(devices.values());
    }

    static DiscoveredDevice parseResponse(String response, InetAddress source) {
        if (!DeviceEndpoint.isLocalAddress(source) || response.length() > 8192) return null;
        String[] lines = response.split("\r?\n");
        if (lines.length == 0 || !lines[0].matches("HTTP/1\\.[01] 200(?: .*)?")) return null;
        boolean webOs = false;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon < 0) continue;
            String key = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            String value = lines[i].substring(colon + 1).trim().toLowerCase(Locale.US);
            if (key.equals("st") && value.equals("urn:lge-com:service:webos-second-screen:1")) webOs = true;
        }
        if (!webOs) return null;
        // Ignore LOCATION entirely: an SSDP packet must never redirect a request.
        return new DiscoveredDevice(TvPlatform.LG_WEBOS, source.getHostAddress(), "LG webOS TV");
    }
}
