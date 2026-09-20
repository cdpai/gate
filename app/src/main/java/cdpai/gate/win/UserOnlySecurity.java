package cdpai.gate.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import static cdpai.gate.client.win.Kernel32.LocalFree;
import static cdpai.gate.client.win.Kernel32.lastError;
import static cdpai.gate.client.win.Win32.newCaptureSegment;
import static cdpai.gate.win.Advapi32.*;

/// Builds a SECURITY_ATTRIBUTES whose DACL grants full access to this Windows user only, and to
/// nobody else -- not even other processes of a different user on the same machine. This is what
/// makes the pipe kernel-enforced rather than merely "not advertised": the DACL is checked before
/// any handshake, which a TCP socket bound to loopback cannot do.
public final class UserOnlySecurity {

    /// D: a DACL follows. P: protected, so no inherited ACEs can widen it later.
    /// (A;;GA;;;SID): Allow this SID Generic-All access.
    public static MemorySegment buildSecurityAttributes(Arena arena) {
        var sid = CurrentUserSid.resolve();
        var sddl = "D:P(A;;GA;;;" + sid + ")";
        var sddlSeg = arena.allocateFrom(sddl, StandardCharsets.UTF_16LE);

        var sdBox = arena.allocate(ValueLayout.ADDRESS);
        var capture = newCaptureSegment(arena);
        try {
            if ((int) ConvertStringSecurityDescriptorToSecurityDescriptorW.invoke(
                    capture, sddlSeg, SDDL_REVISION_1, sdBox, MemorySegment.NULL) == 0)
                throw lastError("ConvertStringSecurityDescriptorToSecurityDescriptorW", capture);
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }

        // SECURITY_ATTRIBUTES { DWORD nLength; LPVOID lpSecurityDescriptor; BOOL bInheritHandle; } -- 24 bytes on x64
        var sa = arena.allocate(24);
        sa.set(ValueLayout.JAVA_INT, 0, 24);
        sa.set(ValueLayout.ADDRESS, 8, sdBox.get(ValueLayout.ADDRESS, 0));
        sa.set(ValueLayout.JAVA_INT, 16, 0);
        return sa;
    }

    /// The DACL is copied into the pipe object's own kernel security descriptor at creation time,
    /// so our copy can be freed right after CreateNamedPipeW returns.
    public static void freeSecurityDescriptor(MemorySegment securityAttributes) {
        var sd = securityAttributes.get(ValueLayout.ADDRESS, 8);
        try { LocalFree.invoke(sd); } catch (Throwable ignored) {}
    }

    private UserOnlySecurity() {}
}
