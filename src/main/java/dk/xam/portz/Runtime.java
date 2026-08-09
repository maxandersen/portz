package dk.xam.portz;

/** Language/platform runtime — detected from process name or build files. */
public record Runtime(String displayName, String emoji) {

    public static final Runtime JAVA = new Runtime("Java", "☕");
    public static final Runtime NODEJS = new Runtime("Node.js", "🟢");
    public static final Runtime PYTHON = new Runtime("Python", "🐍");
    public static final Runtime RUBY = new Runtime("Ruby", "💎");
    public static final Runtime GO = new Runtime("Go", "🐹");
    public static final Runtime RUST = new Runtime("Rust", "🦀");
    public static final Runtime PHP = new Runtime("PHP", "🐘");
    public static final Runtime DOTNET = new Runtime(".NET", "🔷");
    public static final Runtime ELIXIR = new Runtime("Elixir", "💧");
    public static final Runtime DENO = new Runtime("Deno", "🦕");
    public static final Runtime BUN = new Runtime("Bun", "🧅");
}
