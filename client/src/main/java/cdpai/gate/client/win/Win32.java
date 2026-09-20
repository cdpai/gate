package cdpai.gate.client.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/// Builds Win32 downcalls that capture GetLastError ATOMICALLY at the point of the call, via
/// Linker.Option.captureCallState. A separate, later GetLastError() downcall is unreliable: any
/// JVM housekeeping between the failing call and that second call (a safepoint, JIT bookkeeping)
/// can run its own Win32 calls on the same thread and clobber the real error first. Reproduced
/// 2026-09-20 as "GetLastError=0" on calls that were, per other evidence, genuinely failing for a
/// real reason -- every binding in this codebase now goes through here rather than a bare
/// downcallHandle plus a bolted-on GetLastError call.
public final class Win32 {

    static final Linker LINKER = Linker.nativeLinker();
    static final Linker.Option[] CAPTURE = { Linker.Option.captureCallState("GetLastError") };
    static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CAPTURE_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("GetLastError"));

    /// Every returned handle's FIRST parameter is the capture-state segment (see
    /// newCaptureSegment/lastErrorFrom) -- callers pass it as arg 0, ahead of the real arguments.
    public static MethodHandle downcall(SymbolLookup lib, String name, FunctionDescriptor fd) {
        var addr = lib.find(name).orElseThrow(() -> new RuntimeException("no " + name));
        return LINKER.downcallHandle(addr, fd, CAPTURE);
    }

    public static MemorySegment newCaptureSegment(Arena arena) {
        return arena.allocate(CAPTURE_LAYOUT);
    }

    public static int lastErrorFrom(MemorySegment captureSegment) {
        return (int) LAST_ERROR.get(captureSegment, 0L);
    }

    private Win32() {}
}
