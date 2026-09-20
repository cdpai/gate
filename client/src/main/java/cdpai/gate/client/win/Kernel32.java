package cdpai.gate.client.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static cdpai.gate.client.win.Win32.*;

/// Kernel32 FFM bindings shared by both ends of the pipe: opening/reading/writing a HANDLE.
/// Server-only calls (CreateNamedPipeW, ConnectNamedPipe, GetNamedPipeClientProcessId) live in
/// the app module, since only cdpgate itself creates pipes.
///
/// Every handle here whose failure is diagnosed via GetLastError is built with Win32.downcall,
/// so its FIRST argument at every call site is a capture-state segment (Win32.newCaptureSegment),
/// read back with Win32.lastErrorFrom -- see Win32's own class doc for why a bare separate
/// GetLastError() call is not safe to rely on.
public final class Kernel32 {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle plain(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle CreateFileW = downcall(LIB, "CreateFileW", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ReadFile = downcall(LIB, "ReadFile", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle WriteFile = downcall(LIB, "WriteFile", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle GetOverlappedResult = downcall(LIB, "GetOverlappedResult", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    public static final MethodHandle CreateEventW = downcall(LIB, "CreateEventW", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    /// Cancels pending I/O for a handle from ANY thread, without closing it -- the safe way to
    /// wake a thread blocked in GetOverlappedResult from elsewhere (e.g. a grant-expiry sweep),
    /// as opposed to closing the handle out from under the thread that owns it.
    public static final MethodHandle CancelIoEx = downcall(LIB, "CancelIoEx", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    // Best-effort cleanup/query calls whose failures are ignored or which never fail meaningfully
    // here -- no capture-state needed.
    public static final MethodHandle CloseHandle = plain("CloseHandle", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle LocalFree = plain("LocalFree", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle Sleep = plain("Sleep", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
    public static final MethodHandle GetCurrentProcess = plain("GetCurrentProcess",
        FunctionDescriptor.of(ValueLayout.ADDRESS));

    public static final int
        GENERIC_READ = 0x80000000, GENERIC_WRITE = 0x40000000,
        OPEN_EXISTING = 3,
        FILE_FLAG_OVERLAPPED = 0x40000000,
        ERROR_IO_PENDING = 997;

    public static final long INVALID_HANDLE_BITS = -1L;

    public static boolean isInvalid(MemorySegment handle) {
        return handle.address() == INVALID_HANDLE_BITS;
    }

    public static RuntimeException lastError(String what, MemorySegment captureSegment) {
        return new RuntimeException(what + " failed, GetLastError=" + lastErrorFrom(captureSegment));
    }

    /// A manual-reset event for one OVERLAPPED I/O operation. The system resets it to
    /// non-signaled itself whenever a new ReadFile/WriteFile/ConnectNamedPipe reuses the
    /// OVERLAPPED structure it's attached to, so callers don't need to reset it by hand.
    public static MemorySegment createEvent(Arena arena) {
        var capture = newCaptureSegment(arena);
        try {
            var h = (MemorySegment) CreateEventW.invoke(capture, MemorySegment.NULL, 1, 0, MemorySegment.NULL);
            if (isInvalid(h) || h.address() == 0) throw lastError("CreateEventW", capture);
            return h;
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    private Kernel32() {}
}
