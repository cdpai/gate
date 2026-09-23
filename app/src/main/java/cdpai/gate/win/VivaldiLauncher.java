package cdpai.gate.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cdpai.gate.client.win.PipeIo;

import static cdpai.gate.client.win.Kernel32.CloseHandle;
import static cdpai.gate.client.win.Kernel32.lastError;
import static cdpai.gate.client.win.Win32.newCaptureSegment;
import static cdpai.gate.win.ProcessLaunchApi.*;

/// Launches Vivaldi with --remote-debugging-pipe and owns the resulting fd3/fd4 pair -- the sole
/// link to the browser, per the design. Ported from the PRP's own spike (PipeSpike.java), which
/// proved Java can own inherited handles this way; this version is parameterized and long-lived
/// rather than a single hardcoded trial.
///
/// Deliberately never passes --enable-automation: that flag is what sets navigator.webdriver,
/// and the spike findings measured that nothing here needs it -- Browser.getBrowserCommandLine
/// is the one CDP call that's refused without it, so the browser's command line must be read
/// from the OS instead, never from CDP.
public final class VivaldiLauncher {

    public static VivaldiProcess launch(String exePath, String userDataDir) {
        return launch(exePath, userDataDir, userDataDir == null ? List.of() : List.of("about:blank"));
    }

    public static VivaldiProcess launch(String exePath, String userDataDir, List<String> extraArgs) {
        // Scratch only: everything allocated here is done being useful the moment CreateProcessW
        // returns, so it is closed before this method returns rather than held for the process's
        // lifetime like PipeIo's own buffers are.
        try (var arena = Arena.ofConfined()) {
            return launch(arena, exePath, userDataDir, extraArgs);
        }
    }

    static VivaldiProcess launch(Arena arena, String exePath, String userDataDir, List<String> extraArgs) {
        // SECURITY_ATTRIBUTES { DWORD nLength; LPVOID lpSD; BOOL bInheritHandle; } -- 24 bytes on x64
        var sa = arena.allocate(24);
        sa.set(ValueLayout.JAVA_INT, 0, 24);
        sa.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        sa.set(ValueLayout.JAVA_INT, 16, 1);   // inheritable

        var hParentWrite = arena.allocate(ValueLayout.ADDRESS);  // we write -> child fd3
        var hChildRead   = arena.allocate(ValueLayout.ADDRESS);
        var hChildWrite  = arena.allocate(ValueLayout.ADDRESS);  // child fd4 -> we read
        var hParentRead  = arena.allocate(ValueLayout.ADDRESS);

        var capture = newCaptureSegment(arena);
        try {
            if ((int) CreatePipe.invoke(capture, hChildRead, hParentWrite, sa, 0) == 0)
                throw lastError("CreatePipe A", capture);
            if ((int) CreatePipe.invoke(capture, hParentRead, hChildWrite, sa, 0) == 0)
                throw lastError("CreatePipe B", capture);

            // our own ends must NOT be inherited by the child
            SetHandleInformation.invoke(hParentWrite.get(ValueLayout.ADDRESS, 0), HANDLE_FLAG_INHERIT, 0);
            SetHandleInformation.invoke(hParentRead.get(ValueLayout.ADDRESS, 0), HANDLE_FLAG_INHERIT, 0);
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }

        var fdTable = buildMsvcrtFdTable(arena, hChildRead.get(ValueLayout.ADDRESS, 0),
            hChildWrite.get(ValueLayout.ADDRESS, 0));

        // STARTUPINFOW is 104 bytes on x64; lpReserved2 at 72, cbReserved2 at 66
        var si = arena.allocate(104);
        si.fill((byte) 0);
        si.set(ValueLayout.JAVA_INT, 0, 104);
        si.set(ValueLayout.JAVA_SHORT, 66, (short) fdTable.byteSize());
        si.set(ValueLayout.ADDRESS, 72, fdTable);

        var pi = arena.allocate(24);
        var cmdline = arena.allocateFrom(commandLine(exePath, userDataDir, extraArgs), StandardCharsets.UTF_16LE);

        int ok;
        try {
            ok = (int) CreateProcessW.invoke(capture, MemorySegment.NULL, cmdline, MemorySegment.NULL,
                MemorySegment.NULL, 1, CREATE_NO_WINDOW, MemorySegment.NULL, MemorySegment.NULL, si, pi);
        } catch (Throwable t) { throw new RuntimeException(t); }
        if (ok == 0) throw lastError("CreateProcessW(" + exePath + ")", capture);

        // the child now holds its own inherited duplicates of these; our copies are just leaked
        // handle-table slots from here on
        try {
            CloseHandle.invoke(hChildRead.get(ValueLayout.ADDRESS, 0));
            CloseHandle.invoke(hChildWrite.get(ValueLayout.ADDRESS, 0));
        } catch (Throwable ignored) {}

        var processHandle = pi.get(ValueLayout.ADDRESS, 0);
        var pid = Integer.toUnsignedLong(pi.get(ValueLayout.JAVA_INT, 16));
        var cdp = new PipeIo(hParentRead.get(ValueLayout.ADDRESS, 0), hParentWrite.get(ValueLayout.ADDRESS, 0));
        return new VivaldiProcess(pid, processHandle, cdp);
    }

    static String commandLine(String exePath, String userDataDir, List<String> extraArgs) {
        var parts = new java.util.ArrayList<>(List.of("--remote-debugging-pipe", "--no-first-run", "--no-default-browser-check"));
        if (userDataDir != null) parts.add(quoteIfNeeded("--user-data-dir=" + userDataDir));
        extraArgs.forEach(a -> parts.add(quoteIfNeeded(a)));
        return "\"" + exePath + "\" " + String.join(" ", parts);
    }

    static String quoteIfNeeded(String arg) {
        return arg.contains(" ") && !arg.startsWith("\"") ? "\"" + arg + "\"" : arg;
    }

    static MemorySegment buildMsvcrtFdTable(Arena arena, MemorySegment childRead, MemorySegment childWrite) {
        var n = 5;
        var blob = arena.allocate(4 + n + 8L * n);
        blob.set(ValueLayout.JAVA_INT, 0, n);
        for (var i = 0; i < 3; i++) {                       // fd 0,1,2 unused
            blob.set(ValueLayout.JAVA_BYTE, 4 + i, (byte) 0);
            blob.set(ValueLayout.JAVA_LONG_UNALIGNED, 4 + n + 8L * i, -1L);   // INVALID_HANDLE_VALUE
        }
        blob.set(ValueLayout.JAVA_BYTE, 4 + 3, (byte) (FOPEN | FPIPE));
        blob.set(ValueLayout.JAVA_LONG_UNALIGNED, 4 + n + 8L * 3, childRead.address());
        blob.set(ValueLayout.JAVA_BYTE, 4 + 4, (byte) (FOPEN | FPIPE));
        blob.set(ValueLayout.JAVA_LONG_UNALIGNED, 4 + n + 8L * 4, childWrite.address());
        return blob;
    }

    private VivaldiLauncher() {}
}
