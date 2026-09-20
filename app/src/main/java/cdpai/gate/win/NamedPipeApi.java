package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static cdpai.gate.client.win.Win32.downcall;

/// Server-only Kernel32 calls: creating pipe instances, accepting connections, and asking the
/// kernel who the connecting process actually is. Nothing here is available to a mere consumer --
/// only cdpgate itself creates the pipe, which is the point.
///
/// Every handle here is built with Win32.downcall, so its first argument at every call site is a
/// capture-state segment (Win32.newCaptureSegment / Win32.lastErrorFrom).
public final class NamedPipeApi {

    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    public static final MethodHandle CreateNamedPipeW = downcall(LIB, "CreateNamedPipeW", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ConnectNamedPipe = downcall(LIB, "ConnectNamedPipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle DisconnectNamedPipe = downcall(LIB, "DisconnectNamedPipe", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle GetNamedPipeClientProcessId = downcall(LIB, "GetNamedPipeClientProcessId",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final int
        PIPE_ACCESS_DUPLEX = 0x00000003,
        PIPE_TYPE_BYTE = 0x00000000,
        PIPE_READMODE_BYTE = 0x00000000,
        PIPE_WAIT = 0x00000000,
        PIPE_REJECT_REMOTE_CLIENTS = 0x00000008,
        PIPE_UNLIMITED_INSTANCES = 255,
        DEFAULT_BUF_SIZE = 65536,
        DEFAULT_TIMEOUT_MS = 0,
        ERROR_PIPE_CONNECTED = 535;

    private NamedPipeApi() {}
}
