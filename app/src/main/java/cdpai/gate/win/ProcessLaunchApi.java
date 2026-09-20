package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static cdpai.gate.client.win.Win32.downcall;

/// Kernel32 calls needed to launch Vivaldi and own its fd3/fd4 pipe pair -- the same mechanism
/// PipeSpike.java proved works (CreateProcessW + an MSVCRT inherited-fd table via
/// STARTUPINFOW.lpReserved2, the way libuv hands fds to a Node child). ProcessBuilder cannot do
/// this: it only ever wires up stdio, never arbitrary inherited handles.
///
/// CreatePipe and CreateProcessW are built with Win32.downcall, so their first argument at every
/// call site is a capture-state segment (Win32.newCaptureSegment / Win32.lastErrorFrom).
/// SetHandleInformation/TerminateProcess/WaitForSingleObject failures are not diagnosed here, so
/// they stay plain.
public final class ProcessLaunchApi {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle plain(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle CreatePipe = downcall(LIB, "CreatePipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    public static final MethodHandle CreateProcessW = downcall(LIB, "CreateProcessW", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final MethodHandle SetHandleInformation = plain("SetHandleInformation", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    public static final MethodHandle TerminateProcess = plain("TerminateProcess", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    public static final MethodHandle WaitForSingleObject = plain("WaitForSingleObject", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    public static final int
        HANDLE_FLAG_INHERIT = 0x1,
        FOPEN = 0x01, FPIPE = 0x08,             // MSVCRT fd-table flags
        CREATE_NO_WINDOW = 0x08000000,
        INFINITE = -1;

    private ProcessLaunchApi() {}
}
