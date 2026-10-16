import com.universalremote.app.ir.IrEncoder;
import java.util.Arrays;

/** Standalone known-frame and hostile-import tests: no Android SDK required. */
public final class IrEncoderTest {
    private static int checks = 0;
    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
    private static byte[] decode(IrEncoder.Signal signal, int width) {
        byte[] bytes = new byte[width];
        for (int i = 0; i < width * 8; i++) {
            int space = signal.pattern[3 + i * 2];
            if (space > 1000) bytes[i / 8] |= (byte) (1 << (i % 8));
        }
        return bytes;
    }
    private static void frame(String name, long address, long command, byte[] expected) {
        IrEncoder.Signal signal = IrEncoder.encode(name, address, command);
        check(signal.carrierHz == 38000, "carrier");
        check(signal.pattern.length == 3 + expected.length * 16, "duration count");
        check(Arrays.equals(decode(signal, expected.length), expected), name + " frame bytes");
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid input was accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        frame("NEC", 4, 8, new byte[] {4, (byte) 0xfb, 8, (byte) 0xf7});
        frame("Samsung32", 7, 2, new byte[] {7, 7, 2, (byte) 0xfd});
        frame("NECext", 0xbf00, 0xf20d, new byte[] {0, (byte) 0xbf, 0x0d, (byte) 0xf2});
        // Panasonic Viera power: wire bytes vendor 2002, genre 8, command 3d0, parity bd.
        frame("Kaseikyo", 0x00200280L, 0x3d0, new byte[] {2, 0x20, (byte) 0x80, 0, 0x3d, (byte) 0xbd});
        IrEncoder.Signal p = IrEncoder.pronto("0000 006D 0002 0000 0157 00AC 0015 0015");
        check(p.carrierHz == 38029, "Pronto frequency divisor");
        check(Arrays.equals(p.pattern, new int[] {9019,4523,552,552}), "Pronto microsecond conversion");
        IrEncoder.Signal repeated = IrEncoder.pronto("0000 006D 0000 0001 0015 0015");
        check(repeated.pattern.length == 2, "Repeat-only Pronto");
        int[] input = {560, 560};
        IrEncoder.Signal cloned = IrEncoder.raw(38000, input); input[0] = 1;
        check(cloned.pattern[0] == 560, "raw input is copied");
        rejects(() -> IrEncoder.raw(0, new int[] {1,1}));
        rejects(() -> IrEncoder.raw(38000, new int[] {1,-1}));
        rejects(() -> IrEncoder.raw(38000, new int[] {1000000,1000000,1}));
        rejects(() -> IrEncoder.raw(38000, new int[0]));
        rejects(() -> IrEncoder.encode("NEC", 256, 0));
        rejects(() -> IrEncoder.encode("NECext", 0, 65536));
        rejects(() -> IrEncoder.encode("Kaseikyo", -1, 0));
        rejects(() -> IrEncoder.encode("Unknown", 0, 0));
        rejects(() -> IrEncoder.pronto("0000 0000 0001 0000 0015 0015"));
        rejects(() -> IrEncoder.pronto("0100 006D 0001 0000 0015 0015"));
        rejects(() -> IrEncoder.pronto("0000 006D 0002 0000 0015 0015"));
        rejects(() -> IrEncoder.pronto("0000 006D 0001 0000 0015 ZZZZ"));
        System.out.println("IR encoder: " + checks + " checks passed.");
    }
}
