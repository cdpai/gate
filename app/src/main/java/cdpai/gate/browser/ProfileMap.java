package cdpai.gate.browser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/// Which live CDP browserContextId is which on-disk profile. Context ids are opaque and change on
/// every launch, so the binding is learned: cdpgate opens each profile itself, one at a time, and
/// records the one new context each hand-off produces. A context nobody opened through cdpgate
/// (the user picked a profile from the browser's own menu) stays unbound until identified, and a
/// profile-scoped grant never reaches an unbound context -- unknown fails closed.
public final class ProfileMap {

    public record Entry(String dir, String name, String contextId, boolean inferred) {}

    final ConcurrentHashMap<String, String> dirByContext = new ConcurrentHashMap<>();
    final Set<String> inferred = ConcurrentHashMap.newKeySet();
    volatile List<BrowserProfile> profiles = List.of();

    public void setProfiles(List<BrowserProfile> all) { profiles = List.copyOf(all); }

    public List<BrowserProfile> profiles() { return profiles; }

    public void bind(String contextId, String dir, boolean guessed) {
        dirByContext.values().remove(dir);
        dirByContext.put(contextId, dir);
        if (guessed) inferred.add(contextId); else inferred.remove(contextId);
    }

    public void unbind(String contextId) { dirByContext.remove(contextId); inferred.remove(contextId); }

    public void clear() { dirByContext.clear(); inferred.clear(); }

    public String dirOf(String contextId) { return contextId == null ? null : dirByContext.get(contextId); }

    public Optional<String> contextOf(String dir) {
        return dirByContext.entrySet().stream().filter(e -> e.getValue().equals(dir)).map(Map.Entry::getKey).findFirst();
    }

    public boolean isBound(String contextId) { return dirByContext.containsKey(contextId); }

    /// A profile as a consumer names it -- display name or folder, case-insensitive -- to its folder.
    public Optional<String> resolve(String nameOrDir) {
        return profiles.stream().filter(p -> p.dir().equalsIgnoreCase(nameOrDir) || p.name().equalsIgnoreCase(nameOrDir))
            .map(BrowserProfile::dir).findFirst();
    }

    public String nameOf(String dir) {
        return profiles.stream().filter(p -> p.dir().equals(dir)).map(BrowserProfile::name).findFirst().orElse(dir);
    }

    public List<Entry> entries() {
        return profiles.stream().map(p -> {
            var ctx = contextOf(p.dir()).orElse(null);
            return new Entry(p.dir(), p.name(), ctx, ctx != null && inferred.contains(ctx));
        }).toList();
    }

    public long loadedCount() { return dirByContext.size(); }
}
