package dk.xam.pview;

public record Framework(String displayName, String emoji) {

    // Well-known frameworks
    public static final Framework NEXTJS = new Framework("Next.js", "⚡");
    public static final Framework VITE = new Framework("Vite", "⚡");
    public static final Framework ANGULAR = new Framework("Angular", "🅰️");
    public static final Framework REMIX = new Framework("Remix", "💿");
    public static final Framework ASTRO = new Framework("Astro", "🚀");
    public static final Framework EXPRESS = new Framework("Express", "🚂");
    public static final Framework FASTIFY = new Framework("Fastify", "⚡");
    public static final Framework NUXT = new Framework("Nuxt", "💚");
    public static final Framework DJANGO = new Framework("Django", "🎸");
    public static final Framework FASTAPI = new Framework("FastAPI", "⚡");
    public static final Framework FLASK = new Framework("Flask", "🌶️");
    public static final Framework RAILS = new Framework("Rails", "🛤️");
    public static final Framework PUMA = new Framework("Puma", "🐆");
    public static final Framework GO = new Framework("Go", "🐹");
    public static final Framework CARGO = new Framework("Rust/Cargo", "🦀");
    public static final Framework SPRING_BOOT = new Framework("Spring Boot", "🍃");
    public static final Framework QUARKUS = new Framework("Quarkus", "🔮");
    public static final Framework MICRONAUT = new Framework("Micronaut", "🔬");

    public static Framework unknown(String name) {
        return new Framework(name, "📦");
    }
}
