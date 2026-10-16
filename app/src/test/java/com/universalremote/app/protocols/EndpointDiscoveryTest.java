package com.universalremote.app.protocols;

import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class EndpointDiscoveryTest {
    @Test public void acceptsPrivateIpv4AndWellFormedTvHostnames() {
        for (String host : new String[]{"192.168.1.40", "10.0.0.12", "172.16.4.2", "169.254.5.3", "127.0.0.1", "lg-tv.local", "LivingRoom"}) {
            assertNotNull(new DeviceEndpoint(TvPlatform.LG_WEBOS, host, null));
        }
        assertEquals("livingroom", DeviceEndpoint.validateHost("LivingRoom"));
    }

    @Test public void rejectsUrlsHeadersPathsPublicIpsAndAmbiguousNumericAddresses() {
        for (String host : new String[]{"", "http://192.168.1.2", "192.168.1.2:3000", "tv/path", "tv?x=1", "tv#fragment",
                "tv\r\nHost:evil", "tv\n", " tv", "tv ", "evil@tv", "8.8.8.8", "0.0.0.0", "255.255.255.255",
                "192.168.1.255", "127.1", "2130706433", "192.168.01.2", "192.168.1.999", "192.168.1.-1", "tv..local", "-tv", "tv-", "::1"}) {
            try {
                new DeviceEndpoint(TvPlatform.LG_WEBOS, host, null);
                fail("Accepted invalid local host: " + host);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void rejectsCredentialControlCharacters() {
        try {
            new DeviceEndpoint(TvPlatform.LG_WEBOS, "192.168.1.2", "key\nextra");
            fail("Accepted control character in pairing key");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void discoveryUsesLocalPacketSourceAndIgnoresSpoofedLocation() throws Exception {
        String response = "HTTP/1.1 200 OK\r\nST: urn:lge-com:service:webos-second-screen:1\r\n"
                + "LOCATION: http://8.8.8.8/pwn\r\n\r\n";
        DiscoveredDevice found = SsdpDiscovery.parseResponse(response, InetAddress.getByName("192.168.1.42"));
        assertNotNull(found);
        assertEquals("192.168.1.42", found.getHost());
        assertEquals(TvPlatform.LG_WEBOS, found.getPlatform());
        assertNull(SsdpDiscovery.parseResponse(response, InetAddress.getByName("8.8.8.8")));
    }

    @Test public void discoveryRejectsMalformedAndUnrelatedResponses() throws Exception {
        InetAddress local = InetAddress.getByName("192.168.1.2");
        assertNull(SsdpDiscovery.parseResponse("NOTIFY * HTTP/1.1\r\nST: urn:lge-com:service:webos-second-screen:1\r\n", local));
        assertNull(SsdpDiscovery.parseResponse("HTTP/1.1 200 OK\r\nST: roku:ecp\r\n", local));
        assertNull(SsdpDiscovery.parseResponse("HTTP/1.1 500 Error\r\nST: urn:lge-com:service:webos-second-screen:1\r\n", local));
    }
}
