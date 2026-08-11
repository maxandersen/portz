#!/usr/bin/env bash
# Spins up fake dev servers across multiple frameworks, captures portz SVGs for README.
# Usage: ./screenshots/demo.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEMO_DIR=$(mktemp -d)
PIDS=()
WIDTH=140

cleanup() {
    echo "Cleaning up..."
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    rm -rf "$DEMO_DIR"
}
trap cleanup EXIT

# Build portz
echo "Building portz..."
cd "$PROJECT_DIR"
./mvnw -q package -DskipTests

PORTZ="java -jar $PROJECT_DIR/target/quarkus-app/quarkus-run.jar"

# --- Helper: start a Java server socket in a fake project dir ---
start_java_server() {
    local port=$1 dir=$2
    java -cp /dev/null -Duser.dir="$dir" \
        -e "new java.net.ServerSocket($port).accept()" 2>/dev/null &
    # Fallback: use jbang inline
    return 1
}

# Listeners per runtime
start_java_listener() {
    local port=$1 dir=$2 name=$3
    cat > "$dir/Listener.java" <<JAVA
import java.net.ServerSocket;
public class Listener {
    public static void main(String[] args) throws Exception {
        var ss = new ServerSocket(Integer.parseInt(args[0]));
        System.out.println("Listening on port " + args[0]);
        Thread.currentThread().join();
    }
}
JAVA
    javac "$dir/Listener.java"
    (cd "$dir" && java -cp . Listener "$port") &
    PIDS+=($!)
    echo "  Started $name on :$port (PID $!) [java]"
}

start_node_listener() {
    local port=$1 dir=$2 name=$3
    (cd "$dir" && node -e "require('net').createServer(c=>c.end()).listen($port, ()=>console.log('Listening on port $port'))") &
    PIDS+=($!)
    echo "  Started $name on :$port (PID $!) [node]"
}

start_python_listener() {
    local port=$1 dir=$2 name=$3
    (cd "$dir" && python3 -c "import socket,time; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); s.bind(('',${port})); s.listen(); print('Listening on port ${port}'); time.sleep(9999)") &
    PIDS+=($!)
    echo "  Started $name on :$port (PID $!) [python3]"
}

# --- Set up fake project directories with framework markers ---

echo "Setting up demo projects..."

# 1. Quarkus project
QUARKUS_DIR="$DEMO_DIR/my-quarkus-app"
mkdir -p "$QUARKUS_DIR/src/main/java"
cat > "$QUARKUS_DIR/pom.xml" <<'XML'
<project><modelVersion>4.0.0</modelVersion>
<groupId>demo</groupId><artifactId>my-quarkus-app</artifactId><version>1.0</version>
<dependencies><dependency><groupId>io.quarkus</groupId><artifactId>quarkus-core</artifactId><version>3.0.0</version></dependency></dependencies>
</project>
XML
git -C "$QUARKUS_DIR" init -q 2>/dev/null && git -C "$QUARKUS_DIR" checkout -b main -q 2>/dev/null || true

# 2. Spring Boot project
SPRING_DIR="$DEMO_DIR/spring-petclinic"
mkdir -p "$SPRING_DIR/src/main/java"
cat > "$SPRING_DIR/pom.xml" <<'XML'
<project><modelVersion>4.0.0</modelVersion>
<groupId>demo</groupId><artifactId>spring-petclinic</artifactId><version>1.0</version>
<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.0.0</version></parent>
</project>
XML
git -C "$SPRING_DIR" init -q 2>/dev/null && git -C "$SPRING_DIR" checkout -b develop -q 2>/dev/null || true

# 3. Node.js / Next.js project
NEXT_DIR="$DEMO_DIR/my-nextjs-app"
mkdir -p "$NEXT_DIR"
cat > "$NEXT_DIR/package.json" <<'JSON'
{"name":"my-nextjs-app","dependencies":{"next":"14.0.0","react":"18.0.0"}}
JSON
git -C "$NEXT_DIR" init -q 2>/dev/null && git -C "$NEXT_DIR" checkout -b main -q 2>/dev/null || true

# 4. Python / FastAPI project
FASTAPI_DIR="$DEMO_DIR/fastapi-service"
mkdir -p "$FASTAPI_DIR"
cat > "$FASTAPI_DIR/requirements.txt" <<'TXT'
fastapi==0.100.0
uvicorn==0.23.0
TXT
git -C "$FASTAPI_DIR" init -q 2>/dev/null && git -C "$FASTAPI_DIR" checkout -b main -q 2>/dev/null || true

# 5. Micronaut project
MICRONAUT_DIR="$DEMO_DIR/micronaut-api"
mkdir -p "$MICRONAUT_DIR/src/main/java"
cat > "$MICRONAUT_DIR/build.gradle" <<'GRADLE'
plugins { id 'io.micronaut.application' version '4.0.0' }
dependencies { implementation 'io.micronaut:micronaut-http-server-netty' }
GRADLE
git -C "$MICRONAUT_DIR" init -q 2>/dev/null && git -C "$MICRONAUT_DIR" checkout -b main -q 2>/dev/null || true

# --- Start listeners ---
echo "Starting demo servers..."

start_java_listener 28080 "$QUARKUS_DIR" "Quarkus"
start_java_listener 28081 "$SPRING_DIR" "Spring Boot"
start_node_listener 23000 "$NEXT_DIR" "Next.js"
start_python_listener 28000 "$FASTAPI_DIR" "FastAPI"
start_java_listener 28082 "$MICRONAUT_DIR" "Micronaut"

# Wait for all listeners to be ready
sleep 2

# --- Capture screenshots ---
echo ""
echo "Capturing screenshots..."

# Default view
$PORTZ --save "$SCRIPT_DIR/default.svg" --width $WIDTH
echo "  ✓ default.svg"

# With --all
$PORTZ --all --save "$SCRIPT_DIR/all.svg" --width $WIDTH
echo "  ✓ all.svg"

# With --parent
$PORTZ --parent --save "$SCRIPT_DIR/parent.svg" --width $WIDTH
echo "  ✓ parent.svg"

# With --no-compact
$PORTZ --no-compact --save "$SCRIPT_DIR/no-compact.svg" --width $WIDTH
echo "  ✓ no-compact.svg"

# With --no-group
$PORTZ --no-group --save "$SCRIPT_DIR/no-group.svg" --width $WIDTH
echo "  ✓ no-group.svg"

echo ""
echo "Done! SVGs saved to $SCRIPT_DIR/"
ls -la "$SCRIPT_DIR"/*.svg
