package com.universalremote.app.ir;

/** Infrared waveforms use alternating mark/space durations in microseconds, beginning with a mark. */
public final class IrEncoder {
    private IrEncoder() {}

    public static final class Signal {
        public final int carrierHz;
        public final int[] pattern;
        private Signal(int carrierHz, int[] pattern) {
            this.carrierHz = carrierHz;
            this.pattern = pattern.clone();
        }
    }

    /** Validate untrusted imported data before passing it to Android ConsumerIrManager. */
    public static Signal raw(int carrierHz, int[] pattern) {
        if (carrierHz < 20000 || carrierHz > 60000)
            throw new IllegalArgumentException("IR carrier must be between 20,000 and 60,000 Hz.");
        if (pattern == null || pattern.length < 2 || pattern.length > 2000)
            throw new IllegalArgumentException("IR pattern must contain 2 to 2,000 durations.");
        long total = 0;
        for (int duration : pattern) {
            if (duration <= 0 || duration > 1000000)
                throw new IllegalArgumentException("IR durations must be positive microseconds.");
            total += duration;
        }
        if (total > 2000000)
            throw new IllegalArgumentException("One IR command must be at most two seconds.");
        return new Signal(carrierHz, pattern);
    }

    /** Parsed Flipper .ir values are unsigned integers stored in little-endian hexadecimal bytes. */
    public static Signal encode(String protocol, long address, long command) {
        int[] bytes;
        switch (protocol) {
            case "NEC":
                range(address, 0xff, "NEC address"); range(command, 0xff, "NEC command");
                bytes = new int[] {(int) address, ((int) address) ^ 255, (int) command, ((int) command) ^ 255};
                return pulseDistance(bytes, 9000, 4500, 560, 560, 1690);
            case "NECext":
                range(address, 0xffff, "NECext address"); range(command, 0xffff, "NECext command");
                bytes = new int[] {(int) address & 255, (int) (address >> 8), (int) command & 255, (int) (command >> 8)};
                return pulseDistance(bytes, 9000, 4500, 560, 560, 1690);
            case "Samsung32":
                range(address, 0xff, "Samsung32 address"); range(command, 0xff, "Samsung32 command");
                bytes = new int[] {(int) address, (int) address, (int) command, ((int) command) ^ 255};
                return pulseDistance(bytes, 4500, 4500, 550, 550, 1650);
            case "Kaseikyo":
                range(address, 0x3ffffff, "Kaseikyo address"); range(command, 0x3ff, "Kaseikyo command");
                int vendor = (int) ((address >> 8) & 0xffff);
                int foldedVendor = (vendor & 255) ^ (vendor >> 8);
                int parity = (foldedVendor & 15) ^ (foldedVendor >> 4);
                int genre1 = (int) ((address >> 4) & 15);
                int genre2 = (int) (address & 15);
                int id = (int) ((address >> 24) & 3);
                bytes = new int[6];
                bytes[0] = vendor & 255;
                bytes[1] = vendor >> 8;
                bytes[2] = parity | (genre1 << 4);
                bytes[3] = genre2 | (((int) command & 15) << 4);
                bytes[4] = (id << 6) | (int) (command >> 4);
                bytes[5] = bytes[2] ^ bytes[3] ^ bytes[4];
                return pulseDistance(bytes, 3456, 1728, 432, 432, 1296);
            default:
                throw new IllegalArgumentException("Unsupported parsed IR protocol: " + protocol);
        }
    }

    /** Import a learned Pronto 0000 command. One tap sends its intro, or one repeat if no intro exists. */
    public static Signal pronto(String hex) {
        if (hex == null || hex.trim().isEmpty()) throw new IllegalArgumentException("Pronto code is empty.");
        String[] words = hex.trim().split("\\s+");
        if (words.length < 6 || words.length > 2004)
            throw new IllegalArgumentException("Invalid Pronto command length.");
        int[] values = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            if (!words[i].matches("[0-9a-fA-F]{4}"))
                throw new IllegalArgumentException("Pronto values must be four hexadecimal digits.");
            values[i] = Integer.parseInt(words[i], 16);
        }
        if (values[0] != 0 || values[1] == 0)
            throw new IllegalArgumentException("Only learned Pronto 0000 commands are supported.");
        int intro = values[2] * 2, repeat = values[3] * 2;
        if (4 + intro + repeat != words.length || intro + repeat == 0)
            throw new IllegalArgumentException("Pronto burst counts do not match the command.");
        double periodUs = values[1] * 0.241246;
        int count = intro > 0 ? intro : repeat;
        int[] pattern = new int[count];
        for (int i = 0; i < count; i++) pattern[i] = (int) Math.round(values[4 + i] * periodUs);
        return raw((int) Math.round(1000000.0 / periodUs), pattern);
    }

    private static void range(long value, long max, String label) {
        if (value < 0 || value > max) throw new IllegalArgumentException(label + " is out of range.");
    }

    private static Signal pulseDistance(int[] bytes, int headerMark, int headerSpace,
                                         int bitMark, int zeroSpace, int oneSpace) {
        int[] pattern = new int[3 + bytes.length * 16];
        pattern[0] = headerMark; pattern[1] = headerSpace;
        int cursor = 2;
        for (int value : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[cursor++] = bitMark;
                pattern[cursor++] = (value & (1 << bit)) == 0 ? zeroSpace : oneSpace;
            }
        }
        pattern[cursor] = bitMark;
        return raw(38000, pattern);
    }
}
