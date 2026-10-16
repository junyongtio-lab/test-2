import com.universalremote.app.ir.IrEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

/** Build-time adapter: tab-separated protocol, address hex integer, command hex integer. */
public final class EncodeForCatalog {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            String[] fields = line.split("\\t");
            IrEncoder.Signal result = IrEncoder.encode(fields[0],
                Long.parseLong(fields[1], 16), Long.parseLong(fields[2], 16));
            System.out.println(Arrays.toString(result.pattern));
        }
    }
}
