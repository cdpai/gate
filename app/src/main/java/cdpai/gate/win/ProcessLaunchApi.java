package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/// Kernel32 calls needed to launch Vivaldi and own its fd3/fd4 pipe pair -- the same mechanism
/// PipeSpike.java proved works (CreateProcessW + an MSVCRT inherited-fd table via
/// STARTUPINFOW.lpReserved2, the way libuv hands fds to a Node child). ProcessBuilder cannot do
/// this: it only ever wires up stdio, never arbitrary inherited handles.
public final class ProcessLaunchApi {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle fn(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle CreatePipe = fn("CreatePipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    public static final MethodHandle SetHandleInformation = fn("SetHandleInformation", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    public static final MethodHandle CreateProcessW = fn("CreateProcessW", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle TerminateProcess = fn("TerminateProcess", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    public static final MethodHandle WaitForSingleObject = fn("WaitForSingleObject", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    public static final int
        HANDLE_FLAG_INHERIT = 0x1,
        FOPEN = 0x01, FPIPE = 0x08,             // MSVCRT fd-table flags
        CREATE_NO_WINDOW = 0x08000000,
        INFINITE = -1;

    private ProcessLaunchApi() {}
}
