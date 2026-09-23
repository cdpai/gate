package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;

/// The few user32 calls needed to close a browser that cdpgate did not start: find its visible
/// top-level windows and post WM_CLOSE to them, which is exactly what clicking X does. Walks
/// windows by class with FindWindowExW rather than EnumWindows, so no upcall stub is needed.
public final class User32 {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("user32.dll", Arena.global());

    static MethodHandle fn(String name, FunctionDescriptor fd) {
        return LK.downcallHandle(LIB.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    static final MethodHandle
        FindWindowExW = fn("FindWindowExW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)),
        GetWindowThreadProcessId = fn("GetWindowThreadProcessId", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)),
        IsWindowVisible = fn("IsWindowVisible", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
        GetWindow = fn("GetWindow", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT)),
        PostMessageW = fn("PostMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));

    static final int GW_OWNER = 4, WM_CLOSE = 0x0010;

    /// Visible, unowned top-level windows of `className` belonging to `pid` -- a browser's real
    /// windows, not its tooltips, popups and hidden message windows.
    public static List<MemorySegment> topLevelWindows(long pid, String className) {
        var out = new ArrayList<MemorySegment>();
        try (var arena = Arena.ofConfined()) {
            var cls = arena.allocateFrom(className, StandardCharsets.UTF_16LE);
            var owner = arena.allocate(JAVA_INT);
            var prev = MemorySegment.NULL;
            while (true) {
                var h = (MemorySegment) FindWindowExW.invoke(MemorySegment.NULL, prev, cls, MemorySegment.NULL);
                if (h.address() == 0) return out;
                prev = h;
                GetWindowThreadProcessId.invoke(h, owner);
                if (Integer.toUnsignedLong(owner.get(JAVA_INT, 0)) != pid) continue;
                if ((int) IsWindowVisible.invoke(h) == 0) continue;
                if (((MemorySegment) GetWindow.invoke(h, GW_OWNER)).address() != 0) continue;
                out.add(MemorySegment.ofAddress(h.address()));
            }
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void postClose(MemorySegment hwnd) {
        try { PostMessageW.invoke(hwnd, WM_CLOSE, 0L, 0L); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private User32() {}
}
