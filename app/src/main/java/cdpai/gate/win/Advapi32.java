package cdpai.gate.win;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/// Advapi32 FFM bindings: resolving the current user's SID and turning an SDDL string into a
/// security descriptor, so the pipe's DACL can be restricted to this Windows user only.
public final class Advapi32 {

    static final Linker LK = Linker.nativeLinker();
    static final SymbolLookup LIB = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());
    static final SymbolLookup K32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());

    static MethodHandle fn(SymbolLookup lib, String name, FunctionDescriptor fd) {
        return LK.downcallHandle(lib.find(name).orElseThrow(() -> new RuntimeException("no " + name)), fd);
    }

    public static final MethodHandle GetCurrentProcess = fn(K32, "GetCurrentProcess",
        FunctionDescriptor.of(ValueLayout.ADDRESS));
    public static final MethodHandle OpenProcessToken = fn(LIB, "OpenProcessToken", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle GetTokenInformation = fn(LIB, "GetTokenInformation", FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    public static final MethodHandle ConvertSidToStringSidW = fn(LIB, "ConvertSidToStringSidW",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    public static final MethodHandle ConvertStringSecurityDescriptorToSecurityDescriptorW =
        fn(LIB, "ConvertStringSecurityDescriptorToSecurityDescriptorW", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static final int
        TOKEN_QUERY = 0x0008,
        TokenUser = 1,
        SDDL_REVISION_1 = 1;

    private Advapi32() {}
}
