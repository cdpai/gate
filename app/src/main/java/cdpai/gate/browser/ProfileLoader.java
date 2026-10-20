package cdpai.gate.browser;

import java.util.ArrayList;
import java.util.HashSet;

import cdpai.gate.hub.TargetInventory;

/// Opens profiles in the running browser and learns which context each one is. Opening is a
/// hand-off: `vivaldi.exe --profile-directory=X` reaches the running browser through its process
/// singleton, which loads X; the one new context that appears is X. A profile the user opens from
/// the browser's own menu appears unannounced -- it is identified afterwards from which profile's
/// cookie store the browser holds open ([LoadedProfiles]), and until then no profile-scoped grant
/// can reach it.
final class ProfileLoader {

    static final long HANDOFF_TIMEOUT_MS = 20_000, IDENTIFY_TIMEOUT_MS = 40_000;

    final GateSettings settings;
    final ProfileMap profiles;
    final TargetInventory inventory;
    volatile boolean handingOff, startupHold;

    ProfileLoader(GateSettings settings, ProfileMap profiles, TargetInventory inventory) {
        this.settings = settings;
        this.profiles = profiles;
        this.inventory = inventory;
    }

    /// Binds the first profile, launched directly with `--profile-directory`, to the first context seen.
    boolean bindFirst(String dir) {
        var deadline = System.currentTimeMillis() + HANDOFF_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            var ctx = inventory.contexts();
            if (!ctx.isEmpty()) { profiles.bind(ctx.iterator().next(), dir, false); return true; }
            sleep(200);
        }
        return false;
    }

    void holdIdentification(boolean hold) { startupHold = hold; }

    synchronized boolean open(String dir) {
        if (profiles.contextOf(dir).isPresent()) return true;
        handingOff = true;
        try {
            var before = new HashSet<>(inventory.contexts());
            new ProcessBuilder(handoff(dir, null)).start();
            var deadline = System.currentTimeMillis() + HANDOFF_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                for (var ctx : inventory.contexts())
                    if (!before.contains(ctx) && !profiles.isBound(ctx)) { profiles.bind(ctx, dir, false); return true; }
                sleep(200);
            }
            return false;
        } catch (Exception e) {
            System.err.println("cdpgate: could not open profile " + dir + ": " + e.getMessage());
            return false;
        } finally { handingOff = false; }
    }

    /// Opens `url` in profile `dir` by hand-off and answers with the id of the page that appears there.
    java.util.concurrent.CompletableFuture<String> openTab(String dir, String url) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var ctx = profiles.contextOf(dir).orElse(null);
            var before = new HashSet<>(inventory.view().keySet());
            try { new ProcessBuilder(handoff(dir, url)).start(); } catch (Exception e) { throw new IllegalStateException(e); }
            var deadline = System.currentTimeMillis() + HANDOFF_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                for (var e : inventory.view().entrySet())
                    if (!before.contains(e.getKey()) && e.getValue().type().equals("page") && e.getValue().browserContextId() != null
                        && e.getValue().browserContextId().equals(ctx)) return e.getKey();
                sleep(150);
            }
            return null;
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    java.util.List<String> handoff(String dir, String url) {
        var cmd = new ArrayList<String>();
        cmd.add(settings.browserExe());
        if (settings.userDataDir() != null) cmd.add("--user-data-dir=" + settings.userDataDir());
        cmd.add("--profile-directory=" + dir);
        if (url != null) cmd.add(url);
        return cmd;
    }

    void onContextAppeared(String ctx) {
        if (handingOff || startupHold || profiles.isBound(ctx)) return;
        Thread.ofVirtual().start(() -> identify(ctx));
    }

    void identify(String ctx) {
        var deadline = System.currentTimeMillis() + IDENTIFY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && inventory.hasContext(ctx) && !profiles.isBound(ctx)) {
            if (tryIdentify(ctx)) return;
            sleep(2000);
        }
    }

    /// Binds `ctx` when the evidence names exactly one profile for it: the one profile the browser has
    /// loaded that no context is bound to, while `ctx` is the one unbound context. Anything less stays
    /// unbound (fails closed). `last_used` is no evidence -- the browser writes it late, and on
    /// 2026-10-15 that stale value put the Home label on the Work window during a live session -- so it
    /// is used only where the disk gives no evidence at all.
    synchronized boolean tryIdentify(String ctx) {
        if (profiles.isBound(ctx)) return true;
        try {
            var udd = settings.effectiveUserDataDir();
            var loaded = LoadedProfiles.scan(udd, profiles.profiles().stream().map(BrowserProfile::dir).toList());
            if (loaded == null) {
                var last = LocalState.read(udd).lastUsed();
                if (profiles.contextOf(last).isEmpty()) { profiles.bind(ctx, last, true); return true; }
                return false;
            }
            var free = loaded.stream().filter(d -> profiles.contextOf(d).isEmpty()).toList();
            var unbound = inventory.contexts().stream().filter(c -> !profiles.isBound(c)).toList();
            if (free.size() == 1 && unbound.size() == 1 && unbound.getFirst().equals(ctx)) {
                profiles.bind(ctx, free.getFirst(), false);
                System.err.println("cdpgate: identified context " + ctx + " as " + profiles.nameOf(free.getFirst()) + " (its cookie store is the one loaded and unclaimed)");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    final java.util.Map<String, Integer> misses = new java.util.concurrent.ConcurrentHashMap<>();

    /// Run every few seconds while the browser is up. A binding whose profile the browser no longer
    /// has loaded, twice running, is wrong (the profile closed and its context was reused, or an old
    /// guess) and is dropped; every unbound context is then given another chance to be identified.
    void reconcile() {
        if (handingOff || startupHold) return;
        var loaded = LoadedProfiles.scan(settings.effectiveUserDataDir(), profiles.profiles().stream().map(BrowserProfile::dir).toList());
        if (loaded == null) return;
        for (var e : java.util.List.copyOf(profiles.dirByContext.entrySet())) {
            if (loaded.contains(e.getValue())) { misses.remove(e.getKey()); continue; }
            if (misses.merge(e.getKey(), 1, Integer::sum) < 2) continue;
            misses.remove(e.getKey());
            profiles.unbind(e.getKey());
            System.err.println("cdpgate: context " + e.getKey() + " was labelled " + profiles.nameOf(e.getValue()) + ", which is not loaded; label dropped");
        }
        for (var ctx : inventory.contexts()) if (!profiles.isBound(ctx)) tryIdentify(ctx);
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
