package cdpai.gate.posix;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

import cdpai.gate.ConsumerServer;
import cdpai.gate.win.PeerIdentity;

import static cdpai.gate.posix.Libc.*;

/// cdpgate's consumer endpoint on macOS: a Unix domain socket in a directory only this user can
/// enter, the counterpart of the Windows pipe whose DACL admits only this user. Who connected is
/// asked of the kernel (LOCAL_PEERPID), never taken from the client -- java.nio cannot do that,
/// which is the one reason this end is native and the client end is not. LOCAL_PEERCRED is checked
/// as well: a peer of another uid is refused even if the directory's permissions were loosened.
public final class UnixSocketServer implements ConsumerServer {

    final Path path;
    final int listenFd;
    final int myUid;

    public UnixSocketServer(Path path) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac"))
            throw new IllegalStateException("the socket server is written for macOS only (peer pid via LOCAL_PEERPID)");
        this.path = path;
        try {
            myUid = (int) getuid.invoke();
            var dir = path.getParent();
            Files.createDirectories(dir);
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
            // Only reached when no cdpgate answered on this socket (GateApp checks first), so a
            // file here is left over from a crash and is replaced.
            Files.deleteIfExists(path);
            listenFd = bindAndListen(path);
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (RuntimeException e) { throw e; }
        catch (Throwable t) { throw unchecked(t); }
    }

    static int bindAndListen(Path path) throws Throwable {
        var bytes = path.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= SUN_PATH_MAX) throw new IllegalStateException("socket path too long for sockaddr_un: " + path);
        try (var a = Arena.ofConfined()) {
            var cap = newCapture(a);
            var fd = (int) socket.invoke(cap, AF_UNIX, SOCK_STREAM, 0);
            if (fd < 0) throw fail("socket", cap);
            var addr = a.allocate(SOCKADDR_UN_SIZE);
            addr.fill((byte) 0);
            addr.set(ValueLayout.JAVA_BYTE, 0, (byte) SOCKADDR_UN_SIZE);
            addr.set(ValueLayout.JAVA_BYTE, 1, (byte) AF_UNIX);
            MemorySegment.copy(bytes, 0, addr, ValueLayout.JAVA_BYTE, 2, bytes.length);
            if ((int) bind.invoke(cap, fd, addr, SOCKADDR_UN_SIZE) != 0) { var e = fail("bind(" + path + ")", cap); close.invoke(cap, fd); throw e; }
            if ((int) listen.invoke(cap, fd, 16) != 0) { var e = fail("listen", cap); close.invoke(cap, fd); throw e; }
            return fd;
        }
    }

    /// Blocks until a client connects. A peer that is not this user, or that has exited before it
    /// can be identified, is dropped and the wait continues.
    @Override public Accepted accept() {
        while (true) {
            int fd;
            long pid;
            int uid;
            try (var a = Arena.ofConfined()) {
                var cap = newCapture(a);
                fd = (int) Libc.accept.invoke(cap, listenFd, MemorySegment.NULL, MemorySegment.NULL);
                if (fd < 0) {
                    if (errno(cap) == EINTR) continue;
                    throw fail("accept", cap);
                }
                var len = a.allocate(ValueLayout.JAVA_INT);
                var pidBox = a.allocate(ValueLayout.JAVA_INT);
                len.set(ValueLayout.JAVA_INT, 0, 4);
                if ((int) getsockopt.invoke(cap, fd, SOL_LOCAL, LOCAL_PEERPID, pidBox, len) != 0) { drop(fd, "LOCAL_PEERPID", cap); continue; }
                pid = Integer.toUnsignedLong(pidBox.get(ValueLayout.JAVA_INT, 0));
                var cred = a.allocate(XUCRED_SIZE);
                len.set(ValueLayout.JAVA_INT, 0, XUCRED_SIZE);
                if ((int) getsockopt.invoke(cap, fd, SOL_LOCAL, LOCAL_PEERCRED, cred, len) != 0) { drop(fd, "LOCAL_PEERCRED", cap); continue; }
                uid = cred.get(ValueLayout.JAVA_INT, XUCRED_UID_OFFSET);
                if (uid != myUid) { System.err.println("cdpgate: refused a connection from uid " + uid + " (pid " + pid + ")"); close.invoke(cap, fd); continue; }
                // A write to a consumer that has gone must fail with EPIPE, not raise SIGPIPE.
                var one = a.allocate(ValueLayout.JAVA_INT);
                one.set(ValueLayout.JAVA_INT, 0, 1);
                setsockopt.invoke(cap, fd, SOL_SOCKET, SO_NOSIGPIPE, one, 4);
            } catch (RuntimeException e) { throw e; }
            catch (Throwable t) { throw unchecked(t); }
            try {
                return new Accepted(PeerIdentity.resolve(pid), FdIo.socket(fd));
            } catch (IllegalStateException gone) {
                System.err.println("cdpgate: " + gone.getMessage());
                FdIo.socket(fd).close();
            }
        }
    }

    static void drop(int fd, String what, MemorySegment cap) throws Throwable {
        System.err.println("cdpgate: dropped a connection, " + what + ": " + describe(errno(cap)));
        close.invoke(cap, fd);
    }
}
