package cdpai.gate.access;

import java.util.Set;

/// A correction, not the mechanism: category is decided from structural signals first (position,
/// live descendant count, whether children are themselves shells), and this list only refines an
/// otherwise-structural answer. An unrecognised program degrades to the structural answer rather
/// than to an error, so this list is deliberately small and editable rather than exhaustive.
public final class KnownPrograms {

    public static final Set<String> SESSION_ROOTS = Set.of("explorer.exe");

    public static final Set<String> TERMINAL_HOSTS = Set.of(
        "windowsterminal.exe", "tabby.exe", "wt.exe");

    public static final Set<String> SHELLS = Set.of(
        "cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "sh.exe", "zsh.exe", "fish.exe");

    public static final Set<String> AGENTS = Set.of("claude.exe");

    private KnownPrograms() {}
}
