# Singly Linked List Visualizer (Train Consist / Orbital Metro Edition)

A cinematic, interactive Singly Linked List visualizer designed as a silver metro train in orbit. Built as a single-file Java application with zero external dependencies, ready for local execution and cloud deployment.

---

## Features
- **Train Theme**: Locomotive = `HEAD`, coaches = `Node`, couplers = `next` pointer, buffer stop = `NULL`.
- **Per-Visitor Isolation**: Each visitor gets their own isolated in-memory linked list via an `X-Session-Id` header (auto-evicted after 30 minutes of inactivity).
- **Zero External Dependencies**: Standard JDK 17+ HTTP server (`com.sun.net.httpserver.HttpServer`) and custom JSON serializer/parser.
- **Cloud-Ready**: Listens on `0.0.0.0`, configures port dynamically via `PORT` environment variable, provides a `/health` endpoint, and skips desktop browser launching when deployed as a server.

---

## Running Locally

### Option 1: Direct Single-File Execution (Java 17+)
```bash
# Run with default port 8080
java TrainLinkedListVisualizer.java
```

### Option 2: Custom Port via Environment Variable
```bash
# Linux / macOS
PORT=9090 java TrainLinkedListVisualizer.java

# Windows PowerShell
$env:PORT="9090"; java TrainLinkedListVisualizer.java

# Windows CMD
set PORT=9090 && java TrainLinkedListVisualizer.java
```
Access the application at `http://localhost:9090`.

---

## Docker Deployment

### 1. Build the Docker Image
```bash
docker build -t train-linked-list-visualizer .
```

### 2. Run the Container
```bash
docker run -p 8080:8080 -e PORT=8080 train-linked-list-visualizer
```
Access at `http://localhost:8080` (or `http://localhost:8080/health`).

---

## Cloud Deployment (Render, Railway, Fly.io, Cloud Run)

This container is pre-configured for cloud platforms:
- **Port Detection**: Automatically reads `$PORT` provided by container environments (e.g. Render, Railway, Fly.io, Google Cloud Run, Heroku).
- **Host Binding**: Binds to `0.0.0.0` across all container interfaces.
- **Health Check**: `GET /health` returns HTTP `200 ok`.
- **Stateless Cloud Friendly**: Memory-safe session management with automatic idle cleanup.
