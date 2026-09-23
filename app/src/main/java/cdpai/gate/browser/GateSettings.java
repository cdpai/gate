package cdpai.gate.browser;

import java.nio.file.*;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/// `~/cdpai/gate/settings.toml`, written with defaults on first run. `userDataDir` empty means the
/// browser's own default -- for a standalone Vivaldi that is `<install>\User Data`, which is the
/// user's real profile set. Only a trial passes a scratch folder here.
public record GateSettings(String browserExe, String userDataDir, String pipe) {

    static final String DEFAULT_EXE = "C:\\user\\Apps\\Vivaldi\\Application\\vivaldi.exe";

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), "cdpai", "gate", "settings.toml");
    }

    public static GateSettings load() { return load(defaultPath()); }

    public static GateSettings load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "[browser]\nexe = '" + DEFAULT_EXE + "'\n"
                    + "# empty = the browser's own default profile folder (your real profiles)\nuserDataDir = ''\n\n"
                    + "[gate]\npipe = 'cdpai-gate'\n");
            }
            var t = new TomlMapper().readTree(Files.readString(file));
            var udd = t.path("browser").path("userDataDir").asText("");
            return new GateSettings(t.path("browser").path("exe").asText(DEFAULT_EXE),
                udd.isBlank() ? null : udd, t.path("gate").path("pipe").asText("cdpai-gate"));
        } catch (Exception e) { throw new IllegalStateException("cannot read " + file, e); }
    }

    public GateSettings withUserDataDir(String dir) { return new GateSettings(browserExe, dir, pipe); }

    public GateSettings withPipe(String p) { return new GateSettings(browserExe, userDataDir, p); }

    /// Where the profiles actually live: the configured folder, else a standalone install's own
    /// `User Data` beside `Application`, else the per-user default.
    public Path effectiveUserDataDir() { return userDataDir != null ? Path.of(userDataDir) : defaultUserDataDir(); }

    /// Where the browser keeps profiles when started with no --user-data-dir at all.
    public Path defaultUserDataDir() {
        var standalone = Path.of(browserExe).getParent().getParent().resolve("User Data");
        if (Files.isDirectory(standalone)) return standalone;
        return Path.of(System.getenv("LOCALAPPDATA"), "Vivaldi", "User Data");
    }
}
