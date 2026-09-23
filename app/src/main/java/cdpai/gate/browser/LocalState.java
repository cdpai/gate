package cdpai.gate.browser;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/// The browser's own profile inventory, read from `<userDataDir>\Local State`: every profile
/// folder with its display name, which were open at last exit, and which was used last. CDP cannot
/// tell us any of this -- it names profiles only by per-launch opaque context ids.
public record LocalState(List<BrowserProfile> profiles, List<String> lastActive, String lastUsed) {

    public static LocalState read(Path userDataDir) {
        try {
            var file = userDataDir.resolve("Local State");
            if (!Files.exists(file)) return new LocalState(List.of(), List.of(), "Default");
            var p = new ObjectMapper().readTree(Files.readString(file)).path("profile");
            var profiles = new ArrayList<BrowserProfile>();
            p.path("info_cache").properties().forEach(e ->
                profiles.add(new BrowserProfile(e.getKey(), e.getValue().path("name").asText(e.getKey()))));
            var active = new ArrayList<String>();
            p.path("last_active_profiles").forEach(n -> active.add(n.asText()));
            return new LocalState(profiles, active, p.path("last_used").asText("Default"));
        } catch (Exception e) { throw new IllegalStateException("cannot read Local State in " + userDataDir, e); }
    }

    /// The profiles to open at launch, in order, ending with the one used last so that it is
    /// also the one in front when the hand-offs finish.
    public List<String> launchOrder() {
        var order = new ArrayList<String>();
        lastActive.stream().filter(d -> !d.equals(lastUsed) && exists(d)).forEach(order::add);
        order.add(exists(lastUsed) || profiles.isEmpty() ? lastUsed : profiles.getFirst().dir());
        return order;
    }

    boolean exists(String dir) { return profiles.stream().anyMatch(p -> p.dir().equals(dir)); }
}
