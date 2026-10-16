package com.universalremote.app.smartthings;

import com.universalremote.app.protocols.RemoteCommand;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Samsung's official, optional SmartThings connection. Run synchronous calls off the UI thread.
 * No application account is created. The supplied manufacturer token must have device read and
 * execute scopes; its lifecycle and encrypted storage belong to the caller.
 *
 * API: https://developer.smartthings.com/docs/api/public/
 * Capabilities: https://developer.smartthings.com/docs/devices/capabilities/capabilities-reference
 * Only published standard capabilities advertised on the main component are used. SmartThings
 * does not publish a generic standard television D-pad or input-picker command.
 */
public final class SmartThingsClient {
    private static final HttpUrl API = HttpUrl.get("https://api.smartthings.com/v1/");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_DEVICE_PAGES = 20;
    private final String token;
    private final HttpUrl base;
    private final OkHttpClient client;

    public SmartThingsClient(String token) {
        this(token, API, new OkHttpClient());
    }

    // Package-private to exercise protocol behavior with a loopback MockWebServer in unit tests.
    // Production callers cannot configure an HTTP endpoint or redirect credentials elsewhere.
    SmartThingsClient(String token, HttpUrl endpoint, OkHttpClient client) {
        if (token == null || token.length() < 1 || token.length() > 4096) {
            throw new IllegalArgumentException("Enter a valid SmartThings access token.");
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x21 || c > 0x7e) {
                throw new IllegalArgumentException("The SmartThings token contains invalid characters.");
            }
        }
        boolean production = API.equals(endpoint);
        boolean loopback = endpoint != null && ("127.0.0.1".equals(endpoint.host())
                || "localhost".equals(endpoint.host()) || "::1".equals(endpoint.host()));
        if ((!production && !loopback) || !endpoint.username().isEmpty()
                || !endpoint.password().isEmpty() || endpoint.query() != null
                || !endpoint.encodedPath().endsWith("/")) {
            throw new IllegalArgumentException("SmartThings endpoint must be the official API.");
        }
        this.token = token;
        this.base = endpoint;
        this.client = client.newBuilder()
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    /** Lists TV-capable devices only; unrelated switches and speakers are excluded. */
    public List<Device> listDevices() throws IOException {
        List<Device> devices = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        Set<String> seenPages = new LinkedHashSet<>();
        HttpUrl next = path("devices");
        for (int page = 0; next != null; page++) {
            if (page >= MAX_DEVICE_PAGES || !seenPages.add(next.toString())) {
                throw new IOException("SmartThings returned too many device pages. Try again later.");
            }
            JSONObject response = get(next);
            JSONArray items = response.optJSONArray("items");
            if (items == null) {
                throw new IOException("SmartThings returned an invalid device list.");
            }
            try {
                for (int i = 0; i < items.length(); i++) {
                    Device device = parseDevice(items.getJSONObject(i));
                    if (device.isTelevision() && seenIds.add(device.id)) devices.add(device);
                }
            } catch (JSONException | IllegalArgumentException malformed) {
                throw new IOException("SmartThings returned an invalid device list.");
            }
            JSONObject links = response.optJSONObject("_links");
            JSONObject nextLink = links == null ? null : links.optJSONObject("next");
            String href = nextLink == null ? "" : nextLink.optString("href", "");
            next = href.isEmpty() ? null : trustedDevicePage(next, href);
        }
        return Collections.unmodifiableList(devices);
    }

    /** Reconnect by ID while rechecking the device's current advertised capabilities. */
    public Device getDevice(String deviceId) throws IOException {
        validateId(deviceId);
        try {
            Device device = parseDevice(get(path("devices", deviceId)));
            if (!device.isTelevision()) throw new IOException("This SmartThings device is not a supported TV.");
            return device;
        } catch (JSONException | IllegalArgumentException malformed) {
            throw new IOException("SmartThings returned an invalid TV description.");
        }
    }

    public boolean supports(RemoteCommand command, Device device) {
        return command != null && device != null && requiredCapability(command) != null
                && device.capabilities.contains(requiredCapability(command));
    }

    /** Sends only commands backed by the selected TV's published standard capabilities. */
    public void send(RemoteCommand command, Device device) throws IOException {
        if (!supports(command, device)) {
            throw new IOException("This button is not provided by this TV's SmartThings API.");
        }
        String capability = requiredCapability(command);
        String action;
        switch (command) {
            case POWER:
                String power = attribute(device, "switch", "switch");
                if ("on".equals(power)) action = "off";
                else if ("off".equals(power)) action = "on";
                else throw new IOException("The TV's current power state is unavailable.");
                break;
            case MUTE:
                String mute = attribute(device, "audioMute", "mute");
                if ("muted".equals(mute)) action = "unmute";
                else if ("unmuted".equals(mute)) action = "mute";
                else throw new IOException("The TV's current mute state is unavailable.");
                break;
            case VOLUME_UP: action = "volumeUp"; break;
            case VOLUME_DOWN: action = "volumeDown"; break;
            case CHANNEL_UP: action = "channelUp"; break;
            case CHANNEL_DOWN: action = "channelDown"; break;
            case PLAY_PAUSE:
                String playback = attribute(device, "mediaPlayback", "playbackStatus");
                if ("playing".equals(playback)) action = "pause";
                else if ("paused".equals(playback) || "stopped".equals(playback)) action = "play";
                else throw new IOException("The TV's current playback state is unavailable.");
                break;
            case PLAY: action = "play"; break;
            case PAUSE: action = "pause"; break;
            case STOP: action = "stop"; break;
            case REWIND: action = "rewind"; break;
            case FAST_FORWARD: action = "fastForward"; break;
            default: throw new IOException("This button is not provided by this TV's SmartThings API.");
        }
        command(device, capability, action, new JSONArray());
    }

    /** Set an exact channel, where supported, without inventing digit-key commands. */
    public void setChannel(Device device, String channel) throws IOException {
        requireCapability(device, "tvChannel");
        if (channel == null || !channel.matches("[0-9]{1,5}([.-][0-9]{1,3})?")) {
            throw new IOException("Enter a channel number such as 101 or 7.1.");
        }
        command(device, "tvChannel", "setTvChannel", new JSONArray().put(channel));
    }

    /** Source values come from this TV's advertised status, never a guessed input list. */
    public List<String> inputSources(Device device) throws IOException {
        requireCapability(device, "mediaInputSource");
        JSONObject status = get(path("devices", device.id, "components", "main", "capabilities", "mediaInputSource", "status"));
        JSONObject attribute = status.optJSONObject("supportedInputSources");
        JSONArray values = attribute == null ? null : attribute.optJSONArray("value");
        if (values == null) throw new IOException("This TV did not publish its supported input sources.");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String source = values.optString(i, "");
            if (!source.isEmpty() && source.length() <= 128 && !result.contains(source)) result.add(source);
        }
        return Collections.unmodifiableList(result);
    }

    public void setInputSource(Device device, String source) throws IOException {
        if (source == null || !inputSources(device).contains(source)) {
            throw new IOException("Choose an input source published by this TV.");
        }
        command(device, "mediaInputSource", "setInputSource", new JSONArray().put(source));
    }

    private String attribute(Device device, String capability, String name) throws IOException {
        JSONObject status = get(path("devices", device.id, "components", "main", "capabilities", capability, "status"));
        JSONObject value = status.optJSONObject(name);
        return value == null ? "" : value.optString("value", "");
    }

    private void command(Device device, String capability, String name, JSONArray arguments) throws IOException {
        requireCapability(device, capability);
        try {
            JSONObject command = new JSONObject().put("component", "main")
                    .put("capability", capability).put("command", name).put("arguments", arguments);
            JSONObject body = new JSONObject().put("commands", new JSONArray().put(command));
            execute(new Request.Builder().url(path("devices", device.id, "commands"))
                    .post(RequestBody.create(JSON, body.toString())));
        } catch (JSONException malformed) {
            throw new IOException("Cannot construct the SmartThings command.");
        }
    }

    private JSONObject get(HttpUrl url) throws IOException {
        String raw = execute(new Request.Builder().url(url).get());
        try {
            return new JSONObject(raw);
        } catch (JSONException malformed) {
            throw new IOException("SmartThings returned an invalid response.");
        }
    }

    private String execute(Request.Builder request) throws IOException {
        request.header("Authorization", "Bearer " + token).header("Accept", "application/json");
        try (Response response = client.newCall(request.build()).execute()) {
            int status = response.code();
            if (!response.isSuccessful()) {
                if (status == 401) throw new IOException("SmartThings token expired or is invalid. Reconnect Samsung.");
                if (status == 403) throw new IOException("SmartThings permission denied. The token needs device read and execute permission.");
                if (status == 404) throw new IOException("The SmartThings TV or capability is unavailable.");
                if (status == 429) throw new IOException("SmartThings is rate limiting requests. Wait before retrying.");
                if (status >= 300 && status < 400) throw new IOException("SmartThings redirected the request; no token was forwarded.");
                throw new IOException("SmartThings request failed (HTTP " + status + ").");
            }
            ResponseBody body = response.body();
            if (body == null) return "";
            if (body.contentLength() > MAX_RESPONSE_BYTES) throw new IOException("SmartThings response exceeded the size limit.");
            BufferedSource source = body.source();
            source.request(MAX_RESPONSE_BYTES + 1);
            if (source.buffer().size() > MAX_RESPONSE_BYTES) throw new IOException("SmartThings response exceeded the size limit.");
            return source.readUtf8();
        }
    }

    private HttpUrl trustedDevicePage(HttpUrl current, String href) throws IOException {
        HttpUrl next = current.resolve(href);
        if (next == null || !next.scheme().equals(base.scheme()) || !next.host().equals(base.host())
                || next.port() != base.port() || !next.username().isEmpty() || !next.password().isEmpty()
                || !next.encodedPath().equals(path("devices").encodedPath())) {
            throw new IOException("SmartThings returned an untrusted device-list link; no token was forwarded.");
        }
        return next;
    }

    private HttpUrl path(String... segments) {
        HttpUrl.Builder builder = base.newBuilder();
        for (String segment : segments) builder.addPathSegment(segment);
        return builder.build();
    }

    private static Device parseDevice(JSONObject json) throws JSONException {
        Set<String> capabilities = new LinkedHashSet<>();
        JSONArray components = json.optJSONArray("components");
        if (components != null) for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);
            if (!"main".equals(component.optString("id"))) continue;
            JSONArray listed = component.optJSONArray("capabilities");
            if (listed == null) continue;
            for (int c = 0; c < listed.length(); c++) {
                String name = listed.getJSONObject(c).optString("id", "");
                if (!name.isEmpty()) capabilities.add(name);
            }
        }
        String id = json.getString("deviceId");
        String label = json.optString("label", json.optString("name", "Samsung TV"));
        return new Device(id, label.isEmpty() ? "Samsung TV" : label, json.optString("type", ""), capabilities);
    }

    private static void requireCapability(Device device, String capability) throws IOException {
        if (device == null || !device.capabilities.contains(capability)) {
            throw new IOException("This capability is not published by this TV's SmartThings API.");
        }
    }

    private static String requiredCapability(RemoteCommand command) {
        switch (command) {
            case POWER: return "switch";
            case MUTE: return "audioMute";
            case VOLUME_UP: case VOLUME_DOWN: return "audioVolume";
            case CHANNEL_UP: case CHANNEL_DOWN: return "tvChannel";
            case PLAY_PAUSE: case PLAY: case PAUSE: case STOP:
            case REWIND: case FAST_FORWARD: return "mediaPlayback";
            default: return null;
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9-]{1,128}")) {
            throw new IllegalArgumentException("Invalid SmartThings device ID.");
        }
    }

    public static final class Device {
        private final String id;
        private final String label;
        private final String type;
        private final Set<String> capabilities;

        public Device(String id, String label, String type, Set<String> capabilities) {
            validateId(id);
            if (capabilities == null) throw new IllegalArgumentException("Missing SmartThings capabilities.");
            this.id = id;
            this.label = label == null || label.isEmpty() ? "Samsung TV" : label;
            this.type = type == null ? "" : type;
            this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getType() { return type; }
        public Set<String> getCapabilities() { return capabilities; }
        public boolean isTelevision() {
            return capabilities.contains("tvChannel") || capabilities.contains("mediaInputSource");
        }
        @Override public String toString() { return label; }
    }
}
