package com.universalremote.app.protocols;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Local webOS second-screen integration, based on the manufacturer-published
 * LG Connect SDK protocol. All blocking methods belong on a worker thread.
 *
 * This implementation intentionally supports legacy cleartext LAN port 3000.
 * Secure-only TVs require separate certificate pairing; no TLS checks are bypassed.
 * It does not implement undocumented Samsung, Panasonic, or Hisense protocols.
 */
public final class NetworkRemote {
    public interface StatusListener { void onStatus(RemoteStatus status); }
    public interface CredentialListener { void onCredential(String credential); }

    private static final int PAIR_TIMEOUT_MS = 45000;
    private static final int REQUEST_TIMEOUT_MS = 8000;
    private static final String POINTER_URI = "ssap://com.webos.service.networkinput/getPointerInputSocket";
    private static final EnumSet<RemoteCommand> LG_COMMANDS = EnumSet.of(
            RemoteCommand.POWER, RemoteCommand.VOLUME_UP, RemoteCommand.VOLUME_DOWN,
            RemoteCommand.MUTE, RemoteCommand.CHANNEL_UP, RemoteCommand.CHANNEL_DOWN,
            RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT, RemoteCommand.RIGHT,
            RemoteCommand.SELECT, RemoteCommand.BACK, RemoteCommand.HOME,
            RemoteCommand.PLAY, RemoteCommand.PAUSE, RemoteCommand.STOP,
            RemoteCommand.REWIND, RemoteCommand.FAST_FORWARD,
            RemoteCommand.DIGIT_0, RemoteCommand.DIGIT_1, RemoteCommand.DIGIT_2,
            RemoteCommand.DIGIT_3, RemoteCommand.DIGIT_4, RemoteCommand.DIGIT_5,
            RemoteCommand.DIGIT_6, RemoteCommand.DIGIT_7, RemoteCommand.DIGIT_8,
            RemoteCommand.DIGIT_9);

    private final DeviceEndpoint endpoint;
    private final StatusListener statusListener;
    private final CredentialListener credentialListener;
    private final OkHttpClient client;
    private final Object stateLock = new Object();
    private final AtomicInteger nextId = new AtomicInteger();
    private volatile RemoteStatus status = new RemoteStatus(RemoteStatus.Phase.DISCONNECTED, "Not connected.");
    private volatile Attempt active;
    private volatile boolean disposed;

    public NetworkRemote(DeviceEndpoint endpoint, StatusListener statusListener,
                         CredentialListener credentialListener) {
        if (endpoint == null) throw new IllegalArgumentException("Choose a TV.");
        this.endpoint = endpoint;
        this.statusListener = statusListener;
        this.credentialListener = credentialListener;
        client = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .dns(DeviceEndpoint::resolveLocal)
                .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    public RemoteStatus getStatus() { return status; }

    public boolean supports(RemoteCommand command) {
        return endpoint.getPlatform() == TvPlatform.LG_WEBOS && command != null
                && LG_COMMANDS.contains(command);
    }

    /** Waits for real TV registration and the input socket, never just a TCP connection. */
    public void connect() throws IOException {
        Attempt previous;
        Attempt attempt = new Attempt();
        synchronized (stateLock) {
            if (disposed) throw new IOException("This remote has been closed.");
            previous = active;
            active = attempt;
        }
        closeAttempt(previous, new IOException("Connection replaced."));
        update(attempt, RemoteStatus.Phase.CONNECTING, "Connecting to LG webOS on your local network…");
        try {
            if (endpoint.getPlatform() != TvPlatform.LG_WEBOS) {
                throw new IOException("A public manufacturer LAN remote API is not enabled for this TV. Use a supported official integration or infrared.");
            }
            HttpUrl url = new HttpUrl.Builder().scheme("http").host(endpoint.getHost())
                    .port(endpoint.getPort()).build();
            attempt.control = client.newWebSocket(new Request.Builder().url(url).build(), controlListener(attempt));
            await(attempt, attempt.registered, PAIR_TIMEOUT_MS,
                    "No pairing confirmation. Check the TV's mobile connection setting, accept its prompt, and verify this model supports local port 3000. Secure-only webOS models are not supported here.");
            JSONObject pointer = request(attempt, POINTER_URI, new JSONObject());
            String socketPath = pointer.optString("socketPath", "");
            Request pointerRequest = new Request.Builder().url(validatePointerUrl(socketPath)).build();
            attempt.input = client.newWebSocket(pointerRequest, inputListener(attempt));
            await(attempt, attempt.inputOpened, REQUEST_TIMEOUT_MS, "The TV did not open its remote input connection.");
            ensureActive(attempt);
            attempt.ready = true;
            update(attempt, RemoteStatus.Phase.READY,
                    "Connected to LG webOS. Power turns the TV off; use its physical remote to wake it.");
        } catch (IOException failure) {
            fail(attempt, failure);
            throw failure;
        }
    }

    /** A successful pointer send means queued delivery; the TV does not acknowledge button presses. */
    public void send(RemoteCommand command) throws IOException {
        Attempt attempt = active;
        if (attempt == null || !attempt.ready) throw new IOException("Connect to the TV first.");
        ensureActive(attempt);
        if (!supports(command)) throw new IOException("This button is not exposed by this TV's supported official integration.");
        switch (command) {
            case POWER: request(attempt, "ssap://system/turnOff", new JSONObject()); return;
            case VOLUME_UP: request(attempt, "ssap://audio/volumeUp", new JSONObject()); return;
            case VOLUME_DOWN: request(attempt, "ssap://audio/volumeDown", new JSONObject()); return;
            case CHANNEL_UP: request(attempt, "ssap://tv/channelUp", new JSONObject()); return;
            case CHANNEL_DOWN: request(attempt, "ssap://tv/channelDown", new JSONObject()); return;
            case MUTE:
                // Read the actual TV state rather than guessing a mute toggle locally.
                JSONObject muteState = request(attempt, "ssap://audio/getMute", new JSONObject());
                if (!muteState.has("mute") || !(muteState.opt("mute") instanceof Boolean)) {
                    throw new IOException("The TV did not report its mute state.");
                }
                try {
                    request(attempt, "ssap://audio/setMute", new JSONObject().put("mute", !muteState.optBoolean("mute")));
                } catch (JSONException impossible) { throw new IOException("Unable to prepare mute command.", impossible); }
                return;
            case PLAY: request(attempt, "ssap://media.controls/play", new JSONObject()); return;
            case PAUSE: request(attempt, "ssap://media.controls/pause", new JSONObject()); return;
            case STOP: request(attempt, "ssap://media.controls/stop", new JSONObject()); return;
            case REWIND: request(attempt, "ssap://media.controls/rewind", new JSONObject()); return;
            case FAST_FORWARD: request(attempt, "ssap://media.controls/fastForward", new JSONObject()); return;
            case SELECT: sendInput(attempt, "type:click\n\n"); return;
            default:
                String key = command.name().startsWith("DIGIT_")
                        ? command.name().substring(6) : command.name();
                sendInput(attempt, "type:button\nname:" + key + "\n\n");
        }
    }

    public void dispose() {
        Attempt previous;
        synchronized (stateLock) {
            if (disposed) return;
            disposed = true;
            previous = active;
            active = null;
            status = new RemoteStatus(RemoteStatus.Phase.DISCONNECTED, "Disconnected.");
        }
        closeAttempt(previous, new IOException("Disconnected."));
        client.dispatcher().cancelAll();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
        notifyStatus(status);
    }

    private WebSocketListener controlListener(final Attempt attempt) {
        return new WebSocketListener() {
            @Override public void onOpen(WebSocket socket, Response response) {
                if (!isActive(attempt)) { socket.cancel(); return; }
                attempt.control = socket;
                try {
                    JSONObject payload = new JSONObject().put("sdkVersion", "1.0")
                            .put("appId", "com.universalremote.app").put("appName", "Universal Remote")
                            .put("deviceModel", "Android").put("OSVersion", "Android");
                    JSONObject hello = new JSONObject().put("type", "hello").put("id", "hello_0").put("payload", payload);
                    if (!socket.send(hello.toString())) fail(attempt, new IOException("Unable to greet the TV."));
                } catch (JSONException impossible) { fail(attempt, new IOException("Unable to prepare TV pairing.", impossible)); }
            }

            @Override public void onMessage(WebSocket socket, String message) {
                if (!isActive(attempt)) return;
                if (message.length() > 131072) { fail(attempt, new IOException("The TV sent an oversized response.")); return; }
                try {
                    JSONObject packet = new JSONObject(message);
                    String type = packet.optString("type");
                    if (type.equals("hello")) {
                        if (!attempt.registrationSent) sendRegistration(attempt, socket);
                    } else if (type.equals("registered")) {
                        if (!attempt.registrationSent) throw new IOException("Unexpected TV registration response.");
                        JSONObject payload = packet.optJSONObject("payload");
                        String key = payload == null ? "" : payload.optString("client-key", "");
                        if (key.isEmpty() || key.length() > 4096 || DeviceEndpoint.containsControl(key)) {
                            throw new IOException("The TV did not provide a valid pairing key.");
                        }
                        if (!attempt.paired) {
                            attempt.paired = true;
                            if (credentialListener != null && isActive(attempt)) {
                                try { credentialListener.onCredential(key); }
                                catch (RuntimeException ignored) { /* A UI callback must not terminate networking. */ }
                            }
                            attempt.registered.countDown();
                        }
                    } else if (type.equals("error")) {
                        Pending pending = attempt.pending.remove(packet.optString("id"));
                        if (pending != null) pending.finish(null, new IOException("The TV rejected this command. Check permissions and TV settings."));
                        else if (!attempt.paired) fail(attempt, new IOException("The TV rejected pairing. Accept its connection prompt or remove the saved pairing in TV settings and try again."));
                    } else if (type.equals("response")) {
                        Pending pending = attempt.pending.remove(packet.optString("id"));
                        if (pending != null) {
                            JSONObject payload = packet.optJSONObject("payload");
                            if (payload == null || (payload.has("returnValue") && !payload.optBoolean("returnValue"))) {
                                pending.finish(null, new IOException("The TV reported that the command failed."));
                            } else pending.finish(payload, null);
                        } else if (!attempt.paired) {
                            update(attempt, RemoteStatus.Phase.PAIRING, "Accept Universal Remote's connection prompt on the TV. No app account is needed.");
                        }
                    }
                } catch (JSONException malformed) {
                    fail(attempt, new IOException("The device returned an invalid webOS response."));
                } catch (IOException error) { fail(attempt, error); }
            }

            @Override public void onClosing(WebSocket socket, int code, String reason) {
                socket.close(code, null);
                fail(attempt, new IOException("The TV closed the connection. It may be off or have revoked access."));
            }

            @Override public void onClosed(WebSocket socket, int code, String reason) {
                fail(attempt, new IOException("The TV disconnected."));
            }

            @Override public void onFailure(WebSocket socket, Throwable cause, Response response) {
                fail(attempt, new IOException("Cannot reach this LG TV on local port 3000. Check its IP address, Wi-Fi, power, mobile connection setting, and model compatibility."));
            }
        };
    }

    private void sendRegistration(Attempt attempt, WebSocket socket) throws JSONException, IOException {
        attempt.registrationSent = true;
        JSONArray permissions = new JSONArray();
        for (String permission : new String[]{"CONTROL_AUDIO", "CONTROL_INPUT_MEDIA_PLAYBACK",
                "CONTROL_POWER", "CONTROL_INPUT_TV", "CONTROL_INPUT_JOYSTICK", "CONTROL_MOUSE_AND_KEYBOARD"}) {
            permissions.put(permission);
        }
        JSONObject manifest = new JSONObject().put("manifestVersion", 1).put("permissions", permissions);
        JSONObject payload = new JSONObject().put("manifest", manifest).put("pairingType", "PROMPT");
        if (endpoint.getCredential() != null) payload.put("client-key", endpoint.getCredential());
        JSONObject registration = new JSONObject().put("type", "register").put("id", "register_0").put("payload", payload);
        update(attempt, RemoteStatus.Phase.PAIRING, "Accept Universal Remote's connection prompt on the TV. No app account is needed.");
        if (!socket.send(registration.toString())) throw new IOException("Unable to send TV pairing request.");
    }

    private WebSocketListener inputListener(final Attempt attempt) {
        return new WebSocketListener() {
            @Override public void onOpen(WebSocket socket, Response response) {
                if (!isActive(attempt)) { socket.cancel(); return; }
                attempt.input = socket;
                attempt.inputOpened.countDown();
            }
            @Override public void onClosing(WebSocket socket, int code, String reason) {
                socket.close(code, null);
                fail(attempt, new IOException("The TV closed its button connection. Reconnect to continue."));
            }
            @Override public void onClosed(WebSocket socket, int code, String reason) {
                fail(attempt, new IOException("The TV's button connection disconnected."));
            }
            @Override public void onFailure(WebSocket socket, Throwable cause, Response response) {
                fail(attempt, new IOException("Cannot open the TV's remote button connection."));
            }
        };
    }

    private HttpUrl validatePointerUrl(String raw) throws IOException {
        if (raw.isEmpty() || raw.length() > 8192 || DeviceEndpoint.containsControl(raw) || raw.indexOf('\\') >= 0) {
            throw new IOException("The TV supplied an invalid input connection address.");
        }
        try {
            URI uri = new URI(raw);
            if (!"ws".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() != endpoint.getPort()
                    || uri.getRawPath() == null || !uri.getRawPath().startsWith("/")) {
                throw new IOException("The TV requested an unsupported input connection. Secure-only models require certificate pairing.");
            }
            String target = DeviceEndpoint.validateHost(uri.getHost());
            if (!target.equals(endpoint.getHost()) && !DeviceEndpoint.sameLocalTarget(target, endpoint.getHost())) {
                throw new IOException("The TV's input address did not match the selected TV.");
            }
            // Retain the selected host and LAN DNS filter, never a response-controlled destination.
            HttpUrl parsed = HttpUrl.parse("http" + raw.substring(raw.indexOf(':')));
            if (parsed == null) throw new IOException("Invalid TV input address.");
            return parsed.newBuilder().host(endpoint.getHost()).build();
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            throw new IOException("The TV supplied an invalid input connection address.");
        }
    }

    private JSONObject request(Attempt attempt, String uri, JSONObject payload) throws IOException {
        ensureActive(attempt);
        if (!attempt.paired || attempt.control == null) throw new IOException("TV pairing is incomplete.");
        String id = "command_" + nextId.incrementAndGet();
        Pending pending = new Pending();
        attempt.pending.put(id, pending);
        try {
            JSONObject packet = new JSONObject().put("id", id).put("type", "request")
                    .put("uri", uri).put("payload", payload);
            if (!attempt.control.send(packet.toString())) throw new IOException("The TV connection is no longer writable.");
            await(attempt, pending.done, REQUEST_TIMEOUT_MS, "The TV did not acknowledge this command.");
            if (pending.error != null) throw pending.error;
            if (pending.response == null) throw new IOException("The TV returned no command result.");
            return pending.response;
        } catch (JSONException impossible) { throw new IOException("Unable to prepare remote command.", impossible); }
        finally { attempt.pending.remove(id); }
    }

    private void sendInput(Attempt attempt, String message) throws IOException {
        ensureActive(attempt);
        if (attempt.input == null || !attempt.input.send(message)) throw new IOException("The TV's button connection is unavailable. Reconnect to continue.");
    }

    private void await(Attempt attempt, CountDownLatch latch, int timeoutMs, String timeoutMessage) throws IOException {
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) throw new IOException(timeoutMessage);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("TV operation cancelled.");
        }
        ensureActive(attempt);
    }

    private boolean isActive(Attempt attempt) { return !disposed && active == attempt && attempt.error == null; }

    private void ensureActive(Attempt attempt) throws IOException {
        if (attempt.error != null) throw attempt.error;
        if (!isActive(attempt)) throw new IOException("TV operation cancelled.");
    }

    private void fail(Attempt attempt, IOException error) {
        RemoteStatus failureStatus;
        synchronized (stateLock) {
            if (active != attempt || disposed || attempt.error != null) return;
            attempt.error = error;
            attempt.ready = false;
            failureStatus = new RemoteStatus(RemoteStatus.Phase.ERROR, error.getMessage());
            status = failureStatus;
        }
        // Publish failure before releasing workers waiting for the handshake.
        closeAttempt(attempt, error);
        notifyStatus(failureStatus);
    }

    private void closeAttempt(Attempt attempt, IOException error) {
        if (attempt == null) return;
        if (attempt.error == null) attempt.error = error;
        attempt.ready = false;
        attempt.registered.countDown();
        attempt.inputOpened.countDown();
        for (Map.Entry<String, Pending> entry : attempt.pending.entrySet()) entry.getValue().finish(null, error);
        attempt.pending.clear();
        if (attempt.control != null) attempt.control.cancel();
        if (attempt.input != null) attempt.input.cancel();
    }

    private void update(Attempt attempt, RemoteStatus.Phase phase, String message) {
        synchronized (stateLock) {
            if (active != attempt || disposed) return;
            if (attempt.error != null && phase != RemoteStatus.Phase.ERROR) return;
            status = new RemoteStatus(phase, message);
        }
        notifyStatus(status);
    }

    private void notifyStatus(RemoteStatus current) {
        if (statusListener != null) {
            try { statusListener.onStatus(current); }
            catch (RuntimeException ignored) { /* UI lifecycle changes must not break cleanup. */ }
        }
    }

    private static final class Attempt {
        final CountDownLatch registered = new CountDownLatch(1);
        final CountDownLatch inputOpened = new CountDownLatch(1);
        final Map<String, Pending> pending = new ConcurrentHashMap<>();
        volatile WebSocket control;
        volatile WebSocket input;
        volatile boolean registrationSent;
        volatile boolean paired;
        volatile boolean ready;
        volatile IOException error;
    }

    private static final class Pending {
        final CountDownLatch done = new CountDownLatch(1);
        volatile JSONObject response;
        volatile IOException error;
        void finish(JSONObject response, IOException error) {
            this.response = response;
            this.error = error;
            done.countDown();
        }
    }
}
