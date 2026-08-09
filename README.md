# portz

A beautiful CLI tool to inspect and manage processes listening on your machine's ports.

Java port of [port-viewer](https://github.com/iamEtornam/port-viewer) (Rust) — built with [Quarkus](https://quarkus.io), [aesh](https://github.com/aeshell/aesh), and [tamboui](https://tamboui.dev).

## Quick Start

```sh
# Run instantly via jbang (requires Java 25)
jbang portz@maxandersen

# Or with the alias
jbang ports@maxandersen
```

## Features

- **`portz`** — list listening ports with process info, framework detection, git branch
- **`portz -p <port>`** — detailed process card with kill prompt
- **`portz ps`** — process-centric view with CPU%, memory, deduped by PID
- **`portz watch`** — live monitoring with in-place redraw, ghost rows for killed processes
- **`portz clean`** — find and interactively kill orphaned/zombie dev processes
- **`portz completion`** — generate shell completions (bash, zsh, fish, pwsh)

### Extras

- **Port grouping** — multiple ports per process collapsed into one row (`--no-group` to expand)
- **Framework detection** — Node.js (Next.js, Vite, Express…), Python (Django, Flask…), Ruby (Rails), Go, Rust, plus Java frameworks (Spring Boot, Quarkus, Micronaut) via `pom.xml`/`build.gradle`
- **Docker service mapping** — PostgreSQL, Redis, MongoDB, Kafka, LocalStack…
- **Smart rendering** — terminal-width-aware tables, ANSI-aware column widths, path collapsing, `~` home shortening
- **`NO_COLOR`** support — respects [no-color.org](https://no-color.org/) convention
- **Cross-platform** — macOS, Linux, Windows (CWD via oshi FFM)

## Usage

```sh
portz                     # list dev ports (filtered)
portz --all               # list all ports including system services
portz -p 8080             # detail view for port 8080
portz ps                  # process view with CPU/memory
portz ps --all            # all processes
portz watch               # live monitoring (Ctrl+C to exit)
portz clean               # find and kill orphaned processes
portz completion zsh      # generate zsh completions
portz --no-group          # one row per port (no grouping)
```

## Install

### jbang (recommended)

```sh
jbang portz@maxandersen
```

Requires Java 25. jbang will download it automatically if needed.

### Build from source

```sh
git clone https://github.com/maxandersen/portz.git
cd portz
./mvnw package -DskipTests
java --enable-native-access=ALL-UNNAMED -jar target/quarkus-app/quarkus-run.jar
```

### Shell completions

```sh
# Auto-detects your shell
portz completion | source

# Or specify explicitly
portz completion bash > ~/.local/share/bash-completion/completions/portz
portz completion zsh > ~/.zsh/completions/_portz && compinit
portz completion fish > ~/.config/fish/completions/portz.fish
```

## Tech Stack

| Component | Role |
|---|---|
| [Quarkus](https://quarkus.io) + [aesh](https://github.com/aeshell/aesh) | CLI framework, command parsing, shell I/O |
| [tamboui](https://tamboui.dev) | ANSI-aware table rendering, InlineDisplay, BBCode markup |
| [oshi](https://github.com/oshi/oshi) (FFM) | Windows process CWD via Foreign Function & Memory API |
| Virtual threads | Concurrent process data collection |

## Credits

Inspired by and ported from [port-viewer](https://github.com/iamEtornam/port-viewer) by [Etornam](https://github.com/iamEtornam) — a blazing-fast Rust CLI for port inspection. This Java port adds Java framework detection, tamboui-based rendering, port grouping, watch mode ghost rows, and cross-platform support via standard Java APIs.

## License

MIT
