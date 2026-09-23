package cdpai.gate.trial;

/// Pass/fail bookkeeping for the plain-main trials.
public final class Checks {

    static int passed, failed;

    public static void check(String what, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    public static int summary() {
        System.out.println(passed + " passed, " + failed + " failed");
        return failed == 0 ? 0 : 1;
    }

    private Checks() {}
}
