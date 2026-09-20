package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/// Server-only Kernel32 calls: creating pipe instances, accepting connections, and asking the
/// kernel who the connecting process actually is. Nothing here is available to a mere consumer --
/// only cdpgate itself creates the pipe, which is the point.
public final class NamedPipeApi {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle fn(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle CreateNamedPipeW = fn("CreateNamedPipeW", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ConnectNamedPipe = fn("ConnectNamedPipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle DisconnectNamedPipe = fn("DisconnectNamedPipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle GetNamedPipeClientProcessId = fn("GetNamedPipeClientProcessId",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final int
        PIPE_ACCESS_DUPLEX = 0x00000003,
        PIPE_TYPE_BYTE = 0x00000000,
        PIPE_READMODE_BYTE = 0x00000000,
        PIPE_WAIT = 0x00000000,
        PIPE_REJECT_REMOTE_CLIENTS = 0x00000008,
        PIPE_UNLIMITED_INSTANCES = 255,
        DEFAULT_BUF_SIZE = 65536,
        DEFAULT_TIMEOUT_MS = 0;

    private NamedPipeApi() {}
}
