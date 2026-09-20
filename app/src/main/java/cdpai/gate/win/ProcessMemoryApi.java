package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static cdpai.gate.client.win.Win32.downcall;

/// Raw bindings for reading another process's PEB: OpenProcess/ReadProcessMemory from kernel32,
/// NtQueryInformationProcess from ntdll. This is the native route the spike findings flagged as a
/// gap -- ProcessHandle.Info.commandLine() returns empty for other processes on Windows -- and the
/// design doc treats the full command line and working directory as part of the approval decision,
/// not decoration, so it is built for real rather than left as a known limitation.
public final class ProcessMemoryApi {

    static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
    static final SymbolLookup NTDLL = SymbolLookup.libraryLookup("ntdll.dll", Arena.global());

    public static final MethodHandle OpenProcess = downcall(KERNEL32, "OpenProcess", FunctionDescriptor.of(
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    public static final MethodHandle ReadProcessMemory = downcall(KERNEL32, "ReadProcessMemory", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    public static final MethodHandle NtQueryInformationProcess = downcall(NTDLL, "NtQueryInformationProcess",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    public static final int
        PROCESS_QUERY_LIMITED_INFORMATION = 0x1000,
        PROCESS_VM_READ = 0x0010,
        PROCESS_BASIC_INFORMATION = 0;

    private ProcessMemoryApi() {}
}
