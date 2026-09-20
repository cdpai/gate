package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static cdpai.gate.client.win.Win32.downcall;

/// Advapi32 FFM bindings: resolving the current user's SID and turning an SDDL string into a
/// security descriptor, so the pipe's DACL can be restricted to this Windows user only.
///
/// Every handle here is built with Win32.downcall, so its first argument at every call site is a
/// capture-state segment (Win32.newCaptureSegment / Win32.lastErrorFrom).
public final class Advapi32 {

    static final SymbolLookup LIB = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());

    public static final MethodHandle OpenProcessToken = downcall(LIB, "OpenProcessToken", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle GetTokenInformation = downcall(LIB, "GetTokenInformation", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ConvertSidToStringSidW = downcall(LIB, "ConvertSidToStringSidW",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle ConvertStringSecurityDescriptorToSecurityDescriptorW =
        downcall(LIB, "ConvertStringSecurityDescriptorToSecurityDescriptorW", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final int
        TOKEN_QUERY = 0x0008,
        TokenUser = 1,
        SDDL_REVISION_1 = 1;

    private Advapi32() {}
}
