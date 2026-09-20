package cdpai.gate.trial;

import cdpai.gate.win.RemoteProcessInfo;

/// Ground-truth check for RemoteProcessInfo against REAL, already-running external processes
/// (given by pid) -- not a process spawned by this trial itself. A process launched via
/// ProcessBuilder from inside a sandboxed test harness turned out to be a bad test subject: its
/// child inherits restrictions that make ReadProcessMemory fail with ERROR_PARTIAL_COPY even
/// against its own address space's offset 0, which has nothing to do with RemoteProcessInfo's own
/// offsets. Confirmed correct instead against claude.exe (recovered its own real launch command
/// line verbatim, including --session-id) and explorer.exe/Tabby.exe/cmd.exe.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.RemoteProcessInfoTrial <pid>
public class RemoteProcessInfoTrial {
    public static void main(String[] args) {
        var pid = Long.parseLong(args[0]);
        System.out.println("pid=" + pid);
        System.out.println("commandLine     : " + RemoteProcessInfo.commandLine(pid).orElse("(unavailable)"));
        System.out.println("currentDirectory: " + RemoteProcessInfo.currentDirectory(pid).orElse("(unavailable)"));
    }
}
