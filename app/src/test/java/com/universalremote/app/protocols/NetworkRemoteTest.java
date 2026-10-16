package com.universalremote.app.protocols;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/** A fake local television exercises actual websocket handshakes and failures. */
public class NetworkRemoteTest {
    private MockWebServer server;
    private final List<NetworkRemote> remotes = new ArrayList<>();
    private final BlockingQueue<JSONObject> commands = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> buttons = new LinkedBlockingQueue<>();
    private final CountDownLatch registrationReceived = new CountDownLatch(1);
    private volatile JSONObject registration;
    private volatile String pointerAddress;
    private volatile boolean rejectPairing;
    private volatile boolean holdPairing;
    private volatile boolean missingClientKey;
    private volatile boolean rejectCommand;
    private volatile boolean rejectInputSocket;
    private volatile WebSocket control;

    @Before public void createTv() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/pointer")) {
                    if (rejectInputSocket) return new MockResponse().setResponseCode(403);
                    return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                        @Override public void onMessage(WebSocket socket, String message) { buttons.add(message); }
                    });
                }
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                    @Override public void onOpen(WebSocket socket, Response response) { control = socket; }
                    @Override public void onMessage(WebSocket socket, String message) {
                        try {
                            JSONObject packet = new JSONObject(message);
                            String type = packet.getString("type");
                            if (type.equals("hello")) {
                                socket.send(new JSONObject().put("type", "hello").put("payload", new JSONObject()).toString());
                            } else if (type.equals("register")) {
                                registration = packet;
                                registrationReceived.countDown();
                                if (holdPairing) return;
                                if (rejectPairing) socket.send(new JSONObject().put("type", "error")
                                        .put("id", "register_0").put("error", "403 rejected").toString());
                                else {
                                    JSONObject result = new JSONObject();
                                    if (!missingClientKey) result.put("client-key", "local-pairing-key");
                                    socket.send(new JSONObject().put("type", "registered").put("payload", result).toString());
                                }
                            } else if (type.equals("request")) {
                                String uri = packet.getString("uri");
                                JSONObject result = new JSONObject().put("returnValue", true);
                                if (uri.endsWith("getPointerInputSocket")) {
                                    result.put("socketPath", pointerAddress == null
                                            ? "ws://127.0.0.1:" + server.getPort() + "/pointer" : pointerAddress);
                                } else {
                                    commands.add(packet);
                                    if (rejectCommand) {
                                        socket.send(new JSONObject().put("type", "error").put("id", packet.getString("id"))
                                                .put("error", "403 permission denied").toString());
                                        return;
                                    }
                                    if (uri.endsWith("getMute")) result.put("mute", true);
                                }
                                socket.send(new JSONObject().put("type", "response").put("id", packet.getString("id"))
                                        .put("payload", result).toString());
                            }
                        } catch (Exception error) { throw new AssertionError(error); }
                    }
                });
            }
        });
        server.start(InetAddress.getByName("127.0.0.1"), 0);
    }

    @After public void closeTv() throws Exception {
        for (NetworkRemote remote : remotes) remote.dispose();
        server.shutdown();
    }

    private NetworkRemote remote(String key, NetworkRemote.StatusListener statuses,
                                 NetworkRemote.CredentialListener credentials) {
        DeviceEndpoint endpoint = new DeviceEndpoint(TvPlatform.LG_WEBOS, "127.0.0.1", key, server.getPort());
        NetworkRemote remote = new NetworkRemote(endpoint, statuses, credentials);
        remotes.add(remote);
        return remote;
    }

    @Test public void pairsWithTvAndSendsAcknowledgedControlAndPointerKeys() throws Exception {
        AtomicReference<String> credential = new AtomicReference<>();
        List<RemoteStatus.Phase> phases = java.util.Collections.synchronizedList(new ArrayList<>());
        NetworkRemote remote = remote(null, state -> phases.add(state.getPhase()), credential::set);
        remote.connect();
        assertEquals(RemoteStatus.Phase.READY, remote.getStatus().getPhase());
        assertEquals("local-pairing-key", credential.get());
        assertTrue(phases.contains(RemoteStatus.Phase.PAIRING));
        JSONObject manifest = registration.getJSONObject("payload").getJSONObject("manifest");
        assertFalse(manifest.has("signed"));
        assertFalse(manifest.has("signatures"));
        JSONArray permissions = manifest.getJSONArray("permissions");
        assertEquals(6, permissions.length());
        remote.send(RemoteCommand.VOLUME_UP);
        assertEquals("ssap://audio/volumeUp", takeCommand().getString("uri"));
        remote.send(RemoteCommand.UP);
        assertEquals("type:button\nname:UP\n\n", buttons.poll(2, TimeUnit.SECONDS));
        remote.send(RemoteCommand.SELECT);
        assertEquals("type:click\n\n", buttons.poll(2, TimeUnit.SECONDS));
        remote.send(RemoteCommand.DIGIT_7);
        assertEquals("type:button\nname:7\n\n", buttons.poll(2, TimeUnit.SECONDS));
    }

    @Test public void reusesStoredTvPairingKey() throws Exception {
        NetworkRemote remote = remote("previous-tv-key", null, null);
        remote.connect();
        assertEquals("previous-tv-key", registration.getJSONObject("payload").getString("client-key"));
    }

    @Test public void muteUsesCurrentTvState() throws Exception {
        NetworkRemote remote = remote(null, null, null);
        remote.connect();
        remote.send(RemoteCommand.MUTE);
        assertEquals("ssap://audio/getMute", takeCommand().getString("uri"));
        JSONObject toggle = takeCommand();
        assertEquals("ssap://audio/setMute", toggle.getString("uri"));
        assertFalse(toggle.getJSONObject("payload").getBoolean("mute"));
    }

    @Test public void rejectedPairingNeverBecomesReady() throws Exception {
        rejectPairing = true;
        NetworkRemote remote = remote(null, null, null);
        IOException error = expectIo(remote::connect);
        assertTrue(error.getMessage().contains("rejected pairing"));
        assertEquals(RemoteStatus.Phase.ERROR, remote.getStatus().getPhase());
        expectIo(() -> remote.send(RemoteCommand.UP));
    }

    @Test public void registrationWithoutClientKeyFailsHonestly() throws Exception {
        missingClientKey = true;
        NetworkRemote remote = remote(null, null, null);
        assertTrue(expectIo(remote::connect).getMessage().contains("valid pairing key"));
        assertEquals(RemoteStatus.Phase.ERROR, remote.getStatus().getPhase());
    }

    @Test public void rejectedCommandReturnsFailure() throws Exception {
        NetworkRemote remote = remote(null, null, null);
        remote.connect();
        rejectCommand = true;
        assertTrue(expectIo(() -> remote.send(RemoteCommand.CHANNEL_UP)).getMessage().contains("rejected"));
    }

    @Test public void pointerSocketFailureDoesNotReportReady() throws Exception {
        rejectInputSocket = true;
        NetworkRemote remote = remote(null, null, null);
        expectIo(remote::connect);
        assertEquals(RemoteStatus.Phase.ERROR, remote.getStatus().getPhase());
    }

    @Test public void responseCannotRedirectInputToPublicHost() throws Exception {
        pointerAddress = "ws://8.8.8.8:" + server.getPort() + "/pointer";
        NetworkRemote remote = remote(null, null, null);
        expectIo(remote::connect);
        assertEquals(RemoteStatus.Phase.ERROR, remote.getStatus().getPhase());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void responseCannotRedirectInputToAnotherLocalHost() throws Exception {
        pointerAddress = "ws://192.168.3.77:" + server.getPort() + "/pointer";
        NetworkRemote remote = remote(null, null, null);
        assertTrue(expectIo(remote::connect).getMessage().contains("did not match"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void securePointerDoesNotTriggerTlsBypassOrDowngrade() throws Exception {
        pointerAddress = "wss://127.0.0.1:" + server.getPort() + "/pointer";
        NetworkRemote remote = remote(null, null, null);
        assertTrue(expectIo(remote::connect).getMessage().contains("certificate pairing"));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void disposeUnblocksPendingPairing() throws Exception {
        holdPairing = true;
        NetworkRemote remote = remote(null, null, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IOException> pending = executor.submit(() -> expectIo(remote::connect));
            assertTrue(registrationReceived.await(3, TimeUnit.SECONDS));
            remote.dispose();
            assertNotNull(pending.get(3, TimeUnit.SECONDS));
            assertEquals(RemoteStatus.Phase.DISCONNECTED, remote.getStatus().getPhase());
        } finally { executor.shutdownNow(); }
    }

    @Test public void malformedTvResponseFailsPendingConnection() throws Exception {
        holdPairing = true;
        NetworkRemote remote = remote(null, null, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IOException> pending = executor.submit(() -> expectIo(remote::connect));
            assertTrue(registrationReceived.await(3, TimeUnit.SECONDS));
            assertTrue(control.send("this is not json"));
            assertTrue(pending.get(3, TimeUnit.SECONDS).getMessage().contains("invalid webOS response"));
        } finally { executor.shutdownNow(); }
    }

    @Test public void unsupportedButtonsAndPlatformsHaveNoPretendTransport() throws Exception {
        NetworkRemote lg = remote(null, null, null);
        assertTrue(lg.supports(RemoteCommand.SELECT));
        assertTrue(lg.supports(RemoteCommand.PAUSE));
        assertFalse(lg.supports(RemoteCommand.GUIDE));
        assertFalse(lg.supports(RemoteCommand.PLAY_PAUSE));
        NetworkRemote unsupported = new NetworkRemote(new DeviceEndpoint(TvPlatform.HISENSE_ROKU, "192.168.1.2", null), null, null);
        remotes.add(unsupported);
        assertFalse(unsupported.supports(RemoteCommand.POWER));
        expectIo(unsupported::connect);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void closedRemoteCannotBeRestarted() throws Exception {
        NetworkRemote remote = remote(null, null, null);
        remote.dispose();
        expectIo(remote::connect);
        assertEquals(0, server.getRequestCount());
    }

    private JSONObject takeCommand() throws InterruptedException {
        JSONObject command = commands.poll(2, TimeUnit.SECONDS);
        assertNotNull("Fake TV did not receive a command", command);
        return command;
    }

    private interface IoRunnable { void run() throws IOException; }
    private static IOException expectIo(IoRunnable action) throws Exception {
        try { action.run(); }
        catch (IOException expected) { return expected; }
        throw new AssertionError("Expected an IOException, not success.");
    }
}
