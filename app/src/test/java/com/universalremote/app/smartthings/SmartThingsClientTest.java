package com.universalremote.app.smartthings;

import com.universalremote.app.protocols.RemoteCommand;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/** Contract fixtures follow Samsung's public API and published capability names. */
public class SmartThingsClientTest {
    private MockWebServer server;
    private SmartThingsClient client;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new SmartThingsClient("test-token", server.url("/v1/"), new OkHttpClient());
    }

    @After public void tearDown() throws Exception { server.shutdown(); }

    @Test public void listsOnlyMainComponentTvDevices() throws Exception {
        JSONObject tv = description("tv-1", "Living room", "switch", "tvChannel", "audioVolume");
        JSONObject switchOnly = description("light-1", "Lamp", "switch");
        JSONObject speaker = description("speaker-1", "Speaker", "audioVolume", "mediaPlayback");
        JSONObject childTv = description("child-1", "Child component");
        childTv.getJSONArray("components").put(new JSONObject().put("id", "child")
                .put("capabilities", new JSONArray().put(new JSONObject().put("id", "tvChannel"))));
        server.enqueue(json(new JSONObject().put("items", new JSONArray().put(tv).put(switchOnly).put(speaker).put(childTv))));
        List<SmartThingsClient.Device> result = client.listDevices();
        assertEquals(1, result.size());
        assertEquals("tv-1", result.get(0).getId());
        assertEquals("Living room", result.get(0).getLabel());
        assertTrue(client.supports(RemoteCommand.POWER, result.get(0)));
        assertTrue(client.supports(RemoteCommand.VOLUME_UP, result.get(0)));
        assertFalse(client.supports(RemoteCommand.HOME, result.get(0)));
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/devices", request.getPath());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));
    }

    @Test public void followsSameOriginPaginationAndDeduplicatesDevices() throws Exception {
        JSONObject first = new JSONObject().put("items", new JSONArray().put(description("tv-1", "One", "tvChannel")))
                .put("_links", new JSONObject().put("next", new JSONObject().put("href", server.url("/v1/devices?page=2").toString())));
        server.enqueue(json(first));
        server.enqueue(json(new JSONObject().put("items", new JSONArray()
                .put(description("tv-1", "One", "tvChannel"))
                .put(description("tv-2", "Two", "mediaInputSource")))));
        assertEquals(2, client.listDevices().size());
        server.takeRequest();
        assertEquals("/v1/devices?page=2", server.takeRequest().getPath());
    }

    @Test public void rejectsCrossOriginPaginationBeforeSendingCredentials() throws Exception {
        server.enqueue(json(new JSONObject().put("items", new JSONArray()).put("_links",
                new JSONObject().put("next", new JSONObject().put("href", "https://example.com/v1/devices")))));
        IOException error = expectIOException(() -> client.listDevices());
        assertTrue(error.getMessage().contains("untrusted"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void rejectsPathChangingPagination() throws Exception {
        server.enqueue(json(new JSONObject().put("items", new JSONArray()).put("_links",
                new JSONObject().put("next", new JSONObject().put("href", "/v1/other")))));
        assertTrue(expectIOException(() -> client.listDevices()).getMessage().contains("untrusted"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void rejectsRedirectWithoutForwardingToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/stolen")));
        assertTrue(expectIOException(() -> client.listDevices()).getMessage().contains("redirected"));
        assertEquals(1, server.getRequestCount());
        assertEquals("/v1/devices", server.takeRequest().getPath());
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
    }

    @Test public void powerReadsLiveStateBeforeToggling() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"switch\":{\"value\":\"on\"}}"));
        server.enqueue(new MockResponse().setBody("{}"));
        client.send(RemoteCommand.POWER, device("switch", "tvChannel"));
        assertEquals("/v1/devices/tv-1/components/main/capabilities/switch/status", server.takeRequest().getPath());
        assertCommand(server.takeRequest(), "switch", "off");
    }

    @Test public void muteReadsLiveStateBeforeToggling() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"mute\":{\"value\":\"muted\"}}"));
        server.enqueue(new MockResponse().setBody("{}"));
        client.send(RemoteCommand.MUTE, device("audioMute", "tvChannel"));
        assertEquals("/v1/devices/tv-1/components/main/capabilities/audioMute/status", server.takeRequest().getPath());
        assertCommand(server.takeRequest(), "audioMute", "unmute");
    }

    @Test public void unknownToggleStateNeverSendsAGuessedCommand() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"switch\":{\"value\":\"unknown\"}}"));
        assertTrue(expectIOException(() -> client.send(RemoteCommand.POWER, device("switch", "tvChannel")))
                .getMessage().contains("current power state"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void sendsPublishedCapabilityCommands() throws Exception {
        SmartThingsClient.Device tv = device("tvChannel", "audioVolume", "mediaPlayback");
        RemoteCommand[] buttons = { RemoteCommand.VOLUME_UP, RemoteCommand.CHANNEL_DOWN,
                RemoteCommand.REWIND, RemoteCommand.FAST_FORWARD, RemoteCommand.STOP };
        String[] capabilities = { "audioVolume", "tvChannel", "mediaPlayback", "mediaPlayback", "mediaPlayback" };
        String[] commands = { "volumeUp", "channelDown", "rewind", "fastForward", "stop" };
        for (int i = 0; i < buttons.length; i++) {
            server.enqueue(new MockResponse().setBody("{}"));
            client.send(buttons[i], tv);
            assertCommand(server.takeRequest(), capabilities[i], commands[i]);
        }
    }

    @Test public void missingCapabilitiesAndDpadFailWithoutNetwork() throws Exception {
        SmartThingsClient.Device tv = device("tvChannel");
        for (RemoteCommand command : new RemoteCommand[]{RemoteCommand.HOME, RemoteCommand.UP, RemoteCommand.SELECT,
                RemoteCommand.SOURCE, RemoteCommand.VOLUME_UP, RemoteCommand.DIGIT_1}) {
            assertFalse(client.supports(command, tv));
            expectIOException(() -> client.send(command, tv));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test public void exactChannelUsesSetTvChannel() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        client.setChannel(device("tvChannel"), "7.1");
        RecordedRequest request = server.takeRequest();
        JSONObject command = new JSONObject(request.getBody().readUtf8()).getJSONArray("commands").getJSONObject(0);
        assertEquals("setTvChannel", command.getString("command"));
        assertEquals("7.1", command.getJSONArray("arguments").getString(0));
    }

    @Test public void sourceRequiresAdvertisedValue() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"supportedInputSources\":{\"value\":[\"HDMI1\",\"TV\"]}}"));
        expectIOException(() -> client.setInputSource(device("mediaInputSource"), "HDMI2"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void advertisedSourceUsesSetInputSource() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"supportedInputSources\":{\"value\":[\"HDMI1\",\"TV\"]}}"));
        server.enqueue(new MockResponse().setBody("{}"));
        client.setInputSource(device("mediaInputSource"), "HDMI1");
        assertEquals("/v1/devices/tv-1/components/main/capabilities/mediaInputSource/status", server.takeRequest().getPath());
        JSONObject command = new JSONObject(server.takeRequest().getBody().readUtf8()).getJSONArray("commands").getJSONObject(0);
        assertEquals("mediaInputSource", command.getString("capability"));
        assertEquals("setInputSource", command.getString("command"));
        assertEquals("HDMI1", command.getJSONArray("arguments").getString(0));
    }

    @Test public void authorizationErrorsNeverExposeResponseOrToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("test-token private upstream details"));
        IOException error = expectIOException(() -> client.listDevices());
        assertTrue(error.getMessage().contains("expired"));
        assertFalse(error.getMessage().contains("test-token"));
        assertFalse(error.getMessage().contains("private"));
    }

    @Test public void rejectsOversizedAndMalformedResponses() throws Exception {
        char[] huge = new char[1024 * 1024 + 1];
        Arrays.fill(huge, ' ');
        server.enqueue(new MockResponse().setBody(new String(huge)));
        assertTrue(expectIOException(() -> client.listDevices()).getMessage().contains("size limit"));
        server.enqueue(new MockResponse().setBody("not JSON"));
        assertTrue(expectIOException(() -> client.listDevices()).getMessage().contains("invalid response"));
    }

    @Test public void reconnectFetchesCurrentCapabilities() throws Exception {
        server.enqueue(json(description("tv-1", "Bedroom", "tvChannel", "audioMute")));
        SmartThingsClient.Device tv = client.getDevice("tv-1");
        assertEquals("Bedroom", tv.getLabel());
        assertEquals("/v1/devices/tv-1", server.takeRequest().getPath());
        assertTrue(client.supports(RemoteCommand.MUTE, tv));
    }

    @Test public void arbitraryEndpointsAndHeaderInjectionAreRejected() {
        try {
            new SmartThingsClient("test-token", HttpUrl.get("http://example.com/v1/"), new OkHttpClient());
            fail("Remote endpoint override accepted");
        } catch (IllegalArgumentException expected) { /* expected */ }
        try {
            new SmartThingsClient("test\r\nInjected: yes");
            fail("Invalid token accepted");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }

    private static JSONObject description(String id, String label, String... capabilities) throws Exception {
        JSONArray list = new JSONArray();
        for (String capability : capabilities) list.put(new JSONObject().put("id", capability).put("version", 1));
        return new JSONObject().put("deviceId", id).put("label", label).put("type", "OCF")
                .put("components", new JSONArray().put(new JSONObject().put("id", "main").put("capabilities", list)));
    }

    private static SmartThingsClient.Device device(String... capabilities) {
        return new SmartThingsClient.Device("tv-1", "Samsung TV", "OCF", new LinkedHashSet<>(Arrays.asList(capabilities)));
    }

    private static MockResponse json(JSONObject value) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(value.toString());
    }

    private static void assertCommand(RecordedRequest request, String capability, String name) throws Exception {
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/devices/tv-1/commands", request.getPath());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));
        JSONObject command = new JSONObject(request.getBody().readUtf8()).getJSONArray("commands").getJSONObject(0);
        assertEquals("main", command.getString("component"));
        assertEquals(capability, command.getString("capability"));
        assertEquals(name, command.getString("command"));
    }

    private interface IoAction { void run() throws IOException; }

    private static IOException expectIOException(IoAction action) {
        try { action.run(); fail("Expected IOException"); }
        catch (IOException expected) { return expected; }
        throw new AssertionError("Unreachable");
    }
}
