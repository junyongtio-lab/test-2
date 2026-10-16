package com.universalremote.app.protocols;

/** Discovery is a hint; pairing still verifies the TV's protocol response. */
public final class DiscoveredDevice {
    private final TvPlatform platform;
    private final String host;
    private final String name;

    public DiscoveredDevice(TvPlatform platform, String host, String name) {
        if (platform == null) throw new IllegalArgumentException("Missing TV platform.");
        this.platform = platform;
        this.host = DeviceEndpoint.validateHost(host);
        this.name = name;
    }

    public TvPlatform getPlatform() { return platform; }
    public String getHost() { return host; }
    public String getName() { return name; }
}
