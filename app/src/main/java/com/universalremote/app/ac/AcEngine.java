package com.universalremote.app.ac;

import java.util.Arrays;
import java.util.List;

/** Stateful IR encoder backed by IRremoteESP8266, with no network or account. */
public final class AcEngine {
    public static final int MODE_AUTO = 0, MODE_COOL = 1, MODE_HEAT = 2,
            MODE_DRY = 3, MODE_FAN = 4;
    public static final int FAN_AUTO = 0, FAN_LOW = 2, FAN_MEDIUM = 3, FAN_HIGH = 4;
    private static final boolean AVAILABLE;
    static {
        boolean loaded;
        try { System.loadLibrary("palmac"); loaded = true; }
        catch (UnsatisfiedLinkError e) { loaded = false; }
        AVAILABLE = loaded;
    }
    private AcEngine() { }

    public static boolean isAvailable() { return AVAILABLE; }
    public static List<Profile> profiles() { return AcProfiles.ALL; }
    public static Profile find(String id) {
        for (Profile p : profiles()) if (p.id.equals(id)) return p;
        return null;
    }

    /** The prior state must reflect the last successful transmission.
     * Supply null for the first transmission; toggle protocols then depend on
     * the appliance's actual state. Device-specific ranges are clamped upstream. */
    public static Waveform encode(Profile profile, State desired, State previous) {
        if (profile == null || desired == null)
            throw new IllegalArgumentException("Choose an AC profile and state");
        if (!AVAILABLE) throw new IllegalStateException("AC encoder unavailable on this device architecture");
        int[] result = nativeEncode(profile.protocol, profile.model, desired.values(),
                previous == null ? null : previous.values());
        if (result == null || result.length < 2)
            throw new IllegalArgumentException("AC encoder produced no waveform");
        return new Waveform(result[0], Arrays.copyOfRange(result, 1, result.length));
    }
    private static native int[] nativeEncode(String protocol, int model, int[] desired, int[] previous);

    public static final class Profile {
        public final String id, brand, name, protocol, notes;
        public final int model;
        public Profile(String id, String brand, String name, String protocol, int model, String notes) {
            this.id = id; this.brand = brand; this.name = name;
            this.protocol = protocol; this.model = model; this.notes = notes;
        }
        @Override public String toString() { return brand + " · " + name; }
    }

    public static final class State {
        public final boolean power, swing;
        public final int temperatureC, mode, fan;
        public State(boolean power, int temperatureC, int mode, int fan, boolean swing) {
            if (temperatureC < 16 || temperatureC > 32)
                throw new IllegalArgumentException("Temperature must be between 16 and 32 C");
            if (mode < MODE_AUTO || mode > MODE_FAN || fan < 0 || fan > 5)
                throw new IllegalArgumentException("Unsupported AC mode or fan speed");
            this.power = power; this.temperatureC = temperatureC;
            this.mode = mode; this.fan = fan; this.swing = swing;
        }
        private int[] values() { return new int[] {power ? 1 : 0, temperatureC, mode, fan, swing ? 1 : 0}; }
    }

    public static final class Waveform {
        public final int carrierHz;
        public final int[] patternUs;
        private Waveform(int carrierHz, int[] patternUs) {
            this.carrierHz = carrierHz; this.patternUs = patternUs;
            long duration = 0;
            if (carrierHz < 20000 || carrierHz > 60000 || patternUs.length < 3 || patternUs.length > 2000)
                throw new IllegalArgumentException("Invalid IR waveform");
            for (int timing : patternUs) {
                if (timing <= 0 || timing > 1000000) throw new IllegalArgumentException("Invalid IR duration");
                duration += timing;
            }
            if (duration > 1900000) throw new IllegalArgumentException("IR command is too long");
        }
    }
}
