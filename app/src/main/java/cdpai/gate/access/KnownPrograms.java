package cdpai.gate.access;

import java.util.Set;

/// A correction, not the mechanism: category is decided from structural signals first (position,
/// live descendant count, whether children are themselves shells), and this list only refines an
/// otherwise-structural answer. An unrecognised program degrades to the structural answer rather
/// than to an error, so this list is deliberately small and editable rather than exhaustive.
///
/// Names are the image's file name, lower-cased: `explorer.exe` on Windows, `zsh` on macOS, so
/// one list serves both.
public final class KnownPrograms {

    public static final Set<String> SESSION_ROOTS = Set.of("explorer.exe", "launchd");

    public static final Set<String> TERMINAL_HOSTS = Set.of(
        "windowsterminal.exe", "tabby.exe", "wt.exe", "terminal", "iterm2", "tabby", "warp", "ghostty", "alacritty", "kitty");

    public static final Set<String> SHELLS = Set.of(
        "cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "sh.exe", "zsh.exe", "fish.exe",
        "zsh", "bash", "sh", "fish", "dash", "pwsh", "login");

    public static final Set<String> AGENTS = Set.of("claude.exe", "claude");

    /// Claude Code's native installer on macOS runs `~/.local/share/claude/versions/<version>`, so
    /// the image's own name is a version number; the folder is what says what it is.
    public static boolean isAgent(AncestryNode n) {
        return AGENTS.contains(n.imageName()) || n.imagePath().replace('\\', '/').contains("/claude/versions/");
    }

    private KnownPrograms() {}
}
