package cdpai.gate.browser;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import cdpai.gate.client.GatePlatform;

/// Which profiles the browser has loaded right now, read off the disk instead of guessed. A loaded
/// profile's network service holds its cookie database open without sharing, so opening that file for
/// reading fails exactly while the profile is loaded (measured 2026-10-15: Default held, every other
/// profile free, matching `last_active_profiles`). The probe itself allows every kind of sharing and
/// closes at once, so it never stands in the way of the browser's own handle. Answers null where this
/// evidence does not exist (not Windows), so the caller knows it has none.
final class LoadedProfiles {

    static Set<String> scan(Path userDataDir, Collection<String> dirs) {
        if (!GatePlatform.WINDOWS) return null;
        var loaded = new HashSet<String>();
        for (var d : dirs) {
            var p = userDataDir.resolve(d);
            if (held(p.resolve("Network").resolve("Cookies")) || held(p.resolve("Cookies"))) loaded.add(d);
        }
        return loaded;
    }

    static boolean held(Path f) {
        if (!Files.isRegularFile(f)) return false;
        try (var c = FileChannel.open(f, StandardOpenOption.READ)) { return false; }
        catch (AccessDeniedException | NoSuchFileException e) { return false; }
        catch (FileSystemException e) { return true; }   // "being used by another process"
        catch (IOException e) { return false; }
    }
}
