package cdpai.gate.client.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/// Kernel32 FFM bindings shared by both ends of the pipe: opening/reading/writing a HANDLE.
/// Server-only calls (CreateNamedPipeW, ConnectNamedPipe, GetNamedPipeClientProcessId) live in
/// the app module, since only cdpgate itself creates pipes.
public final class Kernel32 {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle fn(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle CreateFileW = fn("CreateFileW", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ReadFile = fn("ReadFile", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle WriteFile = fn("WriteFile", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle CloseHandle = fn("CloseHandle", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle GetLastError = fn("GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT));
    public static final MethodHandle LocalFree = fn("LocalFree", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle Sleep = fn("Sleep", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));

    public static final int
        GENERIC_READ = 0x80000000, GENERIC_WRITE = 0x40000000,
        OPEN_EXISTING = 3;

    public static final long INVALID_HANDLE_BITS = -1L;

    public static boolean isInvalid(MemorySegment handle) {
        return handle.address() == INVALID_HANDLE_BITS;
    }

    public static RuntimeException lastError(String what) {
        try { return new RuntimeException(what + " failed, GetLastError=" + (int) GetLastError.invoke()); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private Kernel32() {}
}
