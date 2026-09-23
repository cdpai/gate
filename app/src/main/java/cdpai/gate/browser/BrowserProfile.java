package cdpai.gate.browser;

/// One on-disk profile: its folder under User Data ("Default", "Profile 3") and the name the user
/// gave it in the browser ("Work").
public record BrowserProfile(String dir, String name) {}
