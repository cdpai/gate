package cdpai.gate.posix;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.*;

/// The libSystem calls cdpgate needs on macOS, bound through FFM -- no native build, the same
/// approach the Windows side takes with kernel32. Every binding that can fail captures errno AT
/// the call (captureCallState), for the reason Win32.java records about GetLastError: a later
/// read can see a value some JVM housekeeping left there instead. Such handles take the capture
/// segment as their FIRST argument.
///
/// Constants and struct shapes are the macOS (XNU) ones. Linux differs in several (SOL_SOCKET,
/// sockaddr_un, peer credentials, posix_spawn types), and is not handled.
public final class Libc {

    static final Linker LINKER = Linker.nativeLinker();
    static final SymbolLookup LIBC = LINKER.defaultLookup();
    static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle ERRNO = CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));
    static final Linker.Option[] CAPTURE = { Linker.Option.captureCallState("errno") };

    public static final int AF_UNIX = 1, SOCK_STREAM = 1;
    public static final int SOL_SOCKET = 0xffff, SO_NOSIGPIPE = 0x1022;
    public static final int SOL_LOCAL = 0, LOCAL_PEERCRED = 0x001, LOCAL_PEERPID = 0x002;
    public static final int SHUT_RDWR = 2;
    public static final int EINTR = 4;
    public static final int SIGKILL = 9;
    public static final short POSIX_SPAWN_SETSIGDEF = 0x04, POSIX_SPAWN_SETSIGMASK = 0x08, POSIX_SPAWN_CLOEXEC_DEFAULT = 0x4000;
    public static final int O_RDONLY = 0, O_WRONLY = 1;
    /// sockaddr_un on macOS: sun_len (1) sun_family (1) sun_path[104].
    public static final int SUN_PATH_MAX = 104, SOCKADDR_UN_SIZE = 106;
    /// struct xucred: cr_version (4) cr_uid (4) cr_ngroups (2, padded to 4) cr_groups[16] (64).
    public static final int XUCRED_SIZE = 76, XUCRED_UID_OFFSET = 4;
    /// posix_spawn_file_actions_t and posix_spawnattr_t are single pointers on macOS; generous
    /// room is allocated anyway so a wrong guess fails loudly elsewhere rather than corrupting memory.
    public static final long SPAWN_OPAQUE_SIZE = 128;

    public static final MethodHandle socket = c("socket", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    public static final MethodHandle bind = c("bind", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    public static final MethodHandle listen = c("listen", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    public static final MethodHandle accept = c("accept", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    public static final MethodHandle getsockopt = c("getsockopt", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    public static final MethodHandle setsockopt = c("setsockopt", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    public static final MethodHandle read = c("read", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
    public static final MethodHandle write = c("write", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
    public static final MethodHandle close = c("close", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    public static final MethodHandle shutdown = c("shutdown", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    public static final MethodHandle pipe = c("pipe", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    public static final MethodHandle waitpid = c("waitpid", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    public static final MethodHandle kill = c("kill", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));

    // posix_spawn and friends return the error number instead of setting errno: no capture.
    public static final MethodHandle posix_spawn = p("posix_spawn", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    public static final MethodHandle fa_init = p("posix_spawn_file_actions_init", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    public static final MethodHandle fa_destroy = p("posix_spawn_file_actions_destroy", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    public static final MethodHandle fa_adddup2 = p("posix_spawn_file_actions_adddup2", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    /// mode_t is 16 bits on macOS.
    public static final MethodHandle fa_addopen = p("posix_spawn_file_actions_addopen", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_SHORT));
    public static final MethodHandle attr_init = p("posix_spawnattr_init", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    public static final MethodHandle attr_destroy = p("posix_spawnattr_destroy", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    public static final MethodHandle attr_setflags = p("posix_spawnattr_setflags", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_SHORT));
    public static final MethodHandle attr_setsigmask = p("posix_spawnattr_setsigmask", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    public static final MethodHandle attr_setsigdefault = p("posix_spawnattr_setsigdefault", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    /// int posix_spawnattr_setbinpref_np(posix_spawnattr_t*, size_t count, cpu_type_t *pref, size_t *ocount)
    public static final MethodHandle attr_setbinpref = p("posix_spawnattr_setbinpref_np", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));
    public static final int CPU_TYPE_ARM64 = 0x0100000C, CPU_TYPE_X86_64 = 0x01000007;
    public static final MethodHandle getuid =p("getuid", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle strerror = p("strerror", FunctionDescriptor.of(ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(256, JAVA_BYTE)), JAVA_INT));

    static MethodHandle c(String name, FunctionDescriptor fd) {
        return LINKER.downcallHandle(LIBC.find(name).orElseThrow(() -> new IllegalStateException("no " + name)), fd, CAPTURE);
    }

    static MethodHandle p(String name, FunctionDescriptor fd) {
        return LINKER.downcallHandle(LIBC.find(name).orElseThrow(() -> new IllegalStateException("no " + name)), fd);
    }

    public static MemorySegment newCapture(Arena arena) { return arena.allocate(CAPTURE_LAYOUT); }

    public static int errno(MemorySegment capture) { return (int) ERRNO.get(capture, 0L); }

    public static String describe(int err) {
        try {
            var s = (MemorySegment) strerror.invoke(err);
            return s.getString(0) + " (errno " + err + ")";
        } catch (Throwable t) { return "errno " + err; }
    }

    public static RuntimeException fail(String what, MemorySegment capture) {
        return new IllegalStateException(what + " failed: " + describe(errno(capture)));
    }

    public static RuntimeException failCode(String what, int code) {
        return new IllegalStateException(what + " failed: " + describe(code));
    }

    /// Unwraps the Throwable a MethodHandle.invoke declares into an unchecked exception.
    public static RuntimeException unchecked(Throwable t) {
        return t instanceof RuntimeException r ? r : t instanceof Error e ? new IllegalStateException(e) : new IllegalStateException(t);
    }

    private Libc() {}
}
