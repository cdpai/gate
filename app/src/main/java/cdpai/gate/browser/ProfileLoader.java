package cdpai.gate.browser;

import java.util.ArrayList;
import java.util.HashSet;

import cdpai.gate.hub.TargetInventory;

/// Opens profiles in the running browser and learns which context each one is. Opening is a
/// hand-off: `vivaldi.exe --profile-directory=X` reaches the running browser through its process
/// singleton, which loads X; the one new context that appears is X. A profile the user opens from
/// the browser's own menu appears unannounced -- it is identified afterwards from `Local State`'s
/// `last_used`, marked as inferred, and until then no profile-scoped grant can reach it.
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
        var udd = settings.effectiveUserDataDir();
        var deadline = System.currentTimeMillis() + IDENTIFY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && inventory.hasContext(ctx) && !profiles.isBound(ctx)) {
            try {
                var last = LocalState.read(udd).lastUsed();
                if (profiles.contextOf(last).isEmpty()) { profiles.bind(ctx, last, true); return; }
            } catch (Exception ignored) {}
            sleep(2000);
        }
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
