package cdpai.gate.win;

import java.lang.foreign.*;

import static cdpai.gate.client.win.Kernel32.*;
import static cdpai.gate.client.win.Win32.*;
import static cdpai.gate.win.Advapi32.*;

/// Resolves the SID of the Windows user running this process, as an "S-1-5-..." string,
/// so the pipe's SDDL can name exactly that user rather than "everyone" or "owner".
public final class CurrentUserSid {

    public static String resolve() {
        try (var arena = Arena.ofConfined()) {
            var capture = newCaptureSegment(arena);
            var hProcess = (MemorySegment) GetCurrentProcess.invoke();
            var hTokenBox = arena.allocate(ValueLayout.ADDRESS);
            if ((int) OpenProcessToken.invoke(capture, hProcess, TOKEN_QUERY, hTokenBox) == 0)
                throw lastError("OpenProcessToken", capture);
            var hToken = hTokenBox.get(ValueLayout.ADDRESS, 0);

            var buf = arena.allocate(256);
            var retLen = arena.allocate(ValueLayout.JAVA_INT);
            if ((int) GetTokenInformation.invoke(capture, hToken, TokenUser, buf, 256, retLen) == 0)
                throw lastError("GetTokenInformation(TokenUser)", capture);

            var psid = buf.get(ValueLayout.ADDRESS, 0);  // TOKEN_USER.User.Sid is the first field
            var strBox = arena.allocate(ValueLayout.ADDRESS);
            if ((int) ConvertSidToStringSidW.invoke(capture, psid, strBox) == 0)
                throw lastError("ConvertSidToStringSidW", capture);
            var wstr = strBox.get(ValueLayout.ADDRESS, 0);

            var sid = readWideString(wstr);
            LocalFree.invoke(wstr);
            CloseHandle.invoke(hToken);
            return sid;
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    static String readWideString(MemorySegment wstr) {
        var sb = new StringBuilder();
        for (var i = 0; ; i += 2) {
            var c = wstr.reinterpret(i + 2).get(ValueLayout.JAVA_CHAR, i);
            if (c == 0) return sb.toString();
            sb.append(c);
        }
    }

    private CurrentUserSid() {}
}
