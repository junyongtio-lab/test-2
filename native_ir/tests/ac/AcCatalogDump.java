import com.universalremote.app.ac.AcEngine;

/** Exposes the compiled catalogue to the portable native integration tests. */
public final class AcCatalogDump {
    public static void main(String[] args) {
        for (AcEngine.Profile profile : AcEngine.profiles()) {
            System.out.println(profile.id + "\t" + profile.protocol + "\t" + profile.model);
        }
    }
}
