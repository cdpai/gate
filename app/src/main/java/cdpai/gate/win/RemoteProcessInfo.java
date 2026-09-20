package cdpai.gate.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static cdpai.gate.client.win.Kernel32.CloseHandle;
import static cdpai.gate.client.win.Win32.newCaptureSegment;
import static cdpai.gate.win.ProcessMemoryApi.*;

/// Reads another process's full command line and current working directory out of its PEB.
/// Best-effort throughout: the approval window is a human decision aid, not the security boundary
/// (identity itself is the kernel-attested PID, resolved elsewhere) -- so any failure here degrades
/// to Optional.empty() rather than throwing.
///
/// x64 only, and only for a target process that is itself 64-bit -- a WOW64 (32-bit) target has a
/// 32-bit PEB with different offsets and is not handled; it simply reads back empty.
///
/// PROCESS_BASIC_INFORMATION is the one struct here that winternl.h actually documents: Reserved1
/// (8), PebBaseAddress (8) at offset 8, Reserved2[2] (16), UniqueProcessId (8), Reserved3 (8) --
/// 48 bytes. Everything past the PEB pointer is undocumented but has been stable for two decades
/// and is the same layout Process Hacker and Sysinternals tools read: PEB.ProcessParameters at
/// offset 0x20; within RTL_USER_PROCESS_PARAMETERS, CurrentDirectory.DosPath (a UNICODE_STRING) at
/// offset 0x38 and CommandLine (a UNICODE_STRING) at offset 0x70.
public final class RemoteProcessInfo {

    static final long PEB_PROCESS_PARAMETERS_OFFSET = 0x20;
    static final long CURRENT_DIRECTORY_OFFSET = 0x38;
    static final long COMMAND_LINE_OFFSET = 0x70;

    public static Optional<String> commandLine(long pid) { return readParam(pid, COMMAND_LINE_OFFSET); }
    public static Optional<String> currentDirectory(long pid) { return readParam(pid, CURRENT_DIRECTORY_OFFSET); }

    static Optional<String> readParam(long pid, long unicodeStringOffset) {
        try (var arena = Arena.ofConfined()) {
            var capture = newCaptureSegment(arena);
            var proc = (MemorySegment) OpenProcess.invoke(capture,
                PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, 0, (int) pid);
            if (proc.address() == 0) return Optional.empty();
            try {
                var peb = pebAddress(arena, capture, proc);
                if (peb.isEmpty()) return Optional.empty();
                var params = readPointer(arena, capture, proc, peb.get().address() + PEB_PROCESS_PARAMETERS_OFFSET);
                if (params.isEmpty()) return Optional.empty();
                return readUnicodeString(arena, capture, proc, params.get().address() + unicodeStringOffset);
            } finally {
                try { CloseHandle.invoke(proc); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) { return Optional.empty(); }
    }

    static Optional<MemorySegment> pebAddress(Arena arena, MemorySegment capture, MemorySegment proc) throws Throwable {
        var pbi = arena.allocate(48);
        var status = (int) NtQueryInformationProcess.invoke(capture, proc, PROCESS_BASIC_INFORMATION,
            pbi, (int) pbi.byteSize(), MemorySegment.NULL);
        if (status != 0) return Optional.empty();
        var peb = pbi.get(ValueLayout.ADDRESS, 8);
        return peb.address() == 0 ? Optional.empty() : Optional.of(peb);
    }

    static Optional<MemorySegment> readPointer(Arena arena, MemorySegment capture, MemorySegment proc, long address) throws Throwable {
        var buf = arena.allocate(8);
        var ok = (int) ReadProcessMemory.invoke(capture, proc, MemorySegment.ofAddress(address), buf, 8L, MemorySegment.NULL);
        if (ok == 0) return Optional.empty();
        var ptr = buf.get(ValueLayout.ADDRESS, 0);
        return ptr.address() == 0 ? Optional.empty() : Optional.of(ptr);
    }

    /// UNICODE_STRING { USHORT Length; USHORT MaximumLength; PVOID Buffer; } -- 16 bytes on x64
    /// once the compiler's pointer alignment padding is included.
    static Optional<String> readUnicodeString(Arena arena, MemorySegment capture, MemorySegment proc, long address) throws Throwable {
        var header = arena.allocate(16);
        if ((int) ReadProcessMemory.invoke(capture, proc, MemorySegment.ofAddress(address), header, 16L, MemorySegment.NULL) == 0)
            return Optional.empty();
        var length = Short.toUnsignedInt(header.get(ValueLayout.JAVA_SHORT, 0));
        var bufferPtr = header.get(ValueLayout.ADDRESS, 8);
        if (length == 0 || bufferPtr.address() == 0 || length > 32768) return Optional.empty();

        var text = arena.allocate(length);
        if ((int) ReadProcessMemory.invoke(capture, proc, bufferPtr, text, length, MemorySegment.NULL) == 0)
            return Optional.empty();
        return Optional.of(new String(text.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE));
    }

    private RemoteProcessInfo() {}
}
