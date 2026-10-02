package cdpai.gate.client;

import java.nio.file.Path;

/// Where cdpgate listens, per platform. Windows: the named pipe `\.\pipe\<name>`. Elsewhere: a
/// Unix domain socket `<name>.sock` in `~/cdpai/gate/run/`, a directory only this user can enter
/// -- the same "this user and nobody else" that the pipe's DACL gives on Windows. The name stays
/// the setting both sides already share (`[gate] pipe` in settings.toml).
public final class GatePlatform {

    public static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    public static Path runDir() { return Path.of(System.getProperty("user.home"), "cdpai", "gate", "run"); }

    public static Path socketPath(String name) { return runDir().resolve(name + ".sock"); }

    public static String describe(String name) { return WINDOWS ? "\\\\.\\pipe\\" + name : socketPath(name).toString(); }

    private GatePlatform() {}
}
