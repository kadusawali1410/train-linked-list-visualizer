// ============================================================================
// Run with: java TrainLinkedListVisualizer.java
// Requires: Java 17+ (No external libraries, build tools, or npm needed)
// Open in browser: http://localhost:8080
// ============================================================================

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TrainLinkedListVisualizer {

    // ==========================================
    // ===== BACKEND (LL) =======================
    // ==========================================

    public static class Step {
        public String type;       // CREATE_NODE, MOVE_CURRENT, SET_NEXT, MOVE_HEAD, HIGHLIGHT, UNLINK, REMOVE_NODE, FOUND, NOT_FOUND
        public int nodeIndex;     // index involved (-1 for none/null/buffer)
        public String text;       // short descriptive announcement
        public String codeLine;   // code line matching step
        public String pointer;    // active pointer: HEAD, CURRENT, NEW, TARGET, NULL

        public Step(String type, int nodeIndex, String text, String codeLine, String pointer) {
            this.type = type;
            this.nodeIndex = nodeIndex;
            this.text = text;
            this.codeLine = codeLine;
            this.pointer = pointer;
        }
    }

    public static class Result {
        public boolean success;
        public String message;
        public List<String> nodes;
        public int size;
        public List<Step> steps;

        public Result(boolean success, String message, List<String> nodes, int size, List<Step> steps) {
            this.success = success;
            this.message = message;
            this.nodes = nodes;
            this.size = size;
            this.steps = steps;
        }
    }

    public static class LL {
        public class Node {
            public String data;
            public Node next;

            public Node(String data) {
                this.data = data;
                this.next = null;
            }
        }

        public Node head = null;
        public int size = 0;

        public List<String> toList() {
            List<String> list = new ArrayList<>();
            Node curr = head;
            while (curr != null) {
                list.add(curr.data);
                curr = curr.next;
            }
            return list;
        }

        public synchronized Result addFirst(String data) {
            if (data == null || data.trim().isEmpty()) {
                data = "A";
            }
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("CREATE_NODE", -1, "Constructing new Coach with cargo '" + data + "'", "Node newNode = new Node(\"" + data + "\");", "NEW"));

            Node newNode = new Node(data);
            if (head == null) {
                steps.add(new Step("SET_NEXT", 0, "newNode.next = null (coupled to Buffer Stop)", "newNode.next = null;", "NEW"));
                head = newNode;
                steps.add(new Step("MOVE_HEAD", 0, "HEAD locomotive coupled to new front coach", "head = newNode;", "HEAD"));
            } else {
                steps.add(new Step("SET_NEXT", 0, "newNode.next = head (coupled to former coach 0)", "newNode.next = head;", "NEW"));
                newNode.next = head;
                head = newNode;
                steps.add(new Step("MOVE_HEAD", 0, "head = newNode (HEAD locomotive moved to new front coach)", "head = newNode;", "HEAD"));
            }
            size++;
            return new Result(true, "Added coach '" + data + "' at beginning (HEAD)", toList(), size, steps);
        }

        public synchronized Result addLast(String data) {
            if (data == null || data.trim().isEmpty()) {
                data = "Z";
            }
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("CREATE_NODE", size, "Constructing new Coach with cargo '" + data + "'", "Node newNode = new Node(\"" + data + "\");", "NEW"));
            Node newNode = new Node(data);

            if (head == null) {
                head = newNode;
                steps.add(new Step("MOVE_HEAD", 0, "Train was empty; HEAD locomotive attached to coach 0", "head = newNode;", "HEAD"));
                size++;
                return new Result(true, "Appended coach '" + data + "' as first coach of empty train", toList(), size, steps);
            }

            Node current = head;
            steps.add(new Step("MOVE_CURRENT", 0, "Inspecting train from head: Node current = head", "Node current = head;", "CURRENT"));
            int idx = 0;
            while (current.next != null) {
                current = current.next;
                idx++;
                steps.add(new Step("MOVE_CURRENT", idx, "Advancing: current = current.next (coach #" + idx + ")", "current = current.next;", "CURRENT"));
            }

            steps.add(new Step("SET_NEXT", idx, "Coupling coach #" + idx + " to new coach: current.next = newNode", "current.next = newNode;", "CURRENT"));
            current.next = newNode;
            size++;

            return new Result(true, "Appended coach '" + data + "' at end of train (index " + (size - 1) + ")", toList(), size, steps);
        }

        public synchronized Result addAt(String data, int index) {
            if (data == null || data.trim().isEmpty()) {
                data = "M";
            }
            if (index <= 0) {
                Result r = addFirst(data);
                r.message = "Notice: index " + index + " <= 0; added at beginning (HEAD). " + r.message;
                return r;
            }
            if (index >= size) {
                Result r = addLast(data);
                r.message = "Notice: index " + index + " >= size (" + (size - 1) + "); appended at end. " + r.message;
                return r;
            }

            List<Step> steps = new ArrayList<>();
            steps.add(new Step("CREATE_NODE", index, "Constructing new Coach with cargo '" + data + "' for slot #" + index, "Node newNode = new Node(\"" + data + "\");", "NEW"));
            Node newNode = new Node(data);

            Node current = head;
            steps.add(new Step("MOVE_CURRENT", 0, "Initializing search for insertion point: Node current = head", "Node current = head;", "CURRENT"));

            for (int i = 0; i < index - 1; i++) {
                current = current.next;
                steps.add(new Step("MOVE_CURRENT", i + 1, "Advancing: current = current.next (coach #" + (i + 1) + ")", "current = current.next;", "CURRENT"));
            }

            steps.add(new Step("SET_NEXT", index, "Linking new coach to following coach: newNode.next = current.next", "newNode.next = current.next;", "NEW"));
            newNode.next = current.next;

            steps.add(new Step("SET_NEXT", index - 1, "Rerouting coupler from coach #" + (index - 1) + " to new coach: current.next = newNode", "current.next = newNode;", "CURRENT"));
            current.next = newNode;

            size++;
            return new Result(true, "Inserted coach '" + data + "' at index " + index, toList(), size, steps);
        }

        public synchronized Result deleteFirst() {
            List<Step> steps = new ArrayList<>();
            if (head == null) {
                return new Result(false, "Train is already empty! Cannot delete first coach.", toList(), size, steps);
            }

            String oldData = head.data;
            steps.add(new Step("HIGHLIGHT", 0, "Targeting first coach #0 ('" + oldData + "')", "Node temp = head;", "HEAD"));

            if (head.next != null) {
                steps.add(new Step("MOVE_HEAD", 1, "HEAD locomotive moves to next coach: head = head.next", "head = head.next;", "HEAD"));
            } else {
                steps.add(new Step("MOVE_HEAD", -1, "HEAD locomotive decouples: train will now be empty", "head = null;", "HEAD"));
            }

            steps.add(new Step("REMOVE_NODE", 0, "Coach #0 ('" + oldData + "') shunted off the main line", "temp.next = null;", "TARGET"));
            head = head.next;
            size--;

            return new Result(true, "Removed first coach '" + oldData + "' from HEAD", toList(), size, steps);
        }

        public synchronized Result deleteLast() {
            List<Step> steps = new ArrayList<>();
            if (head == null) {
                return new Result(false, "Train is already empty! Cannot delete last coach.", toList(), size, steps);
            }

            if (head.next == null) {
                String oldData = head.data;
                steps.add(new Step("HIGHLIGHT", 0, "Only coach on track (index 0, cargo='" + oldData + "')", "Node temp = head;", "HEAD"));
                steps.add(new Step("MOVE_HEAD", -1, "Train will become empty: head = null", "head = null;", "HEAD"));
                steps.add(new Step("REMOVE_NODE", 0, "Uncoupling lone coach '" + oldData + "'", "head = null;", "TARGET"));
                head = null;
                size = 0;
                return new Result(true, "Removed the only coach '" + oldData + "'. Train is now empty.", toList(), size, steps);
            }

            Node current = head;
            steps.add(new Step("MOVE_CURRENT", 0, "Locating coach before caboose: Node current = head", "Node current = head;", "CURRENT"));
            int idx = 0;
            while (current.next.next != null) {
                current = current.next;
                idx++;
                steps.add(new Step("MOVE_CURRENT", idx, "Advancing: current = current.next (coach #" + idx + ")", "current = current.next;", "CURRENT"));
            }

            int targetIdx = idx + 1;
            String targetData = current.next.data;
            steps.add(new Step("HIGHLIGHT", targetIdx, "Targeting caboose coach #" + targetIdx + " ('" + targetData + "')", "Node target = current.next;", "TARGET"));
            steps.add(new Step("UNLINK", idx, "Uncoupling caboose: current.next = null", "current.next = null;", "CURRENT"));
            steps.add(new Step("REMOVE_NODE", targetIdx, "Coach #" + targetIdx + " ('" + targetData + "') removed from track", "target.next = null;", "TARGET"));

            current.next = null;
            size--;

            return new Result(true, "Removed last coach '" + targetData + "' at index " + targetIdx, toList(), size, steps);
        }

        public synchronized Result deleteAt(int index) {
            List<Step> steps = new ArrayList<>();
            if (head == null) {
                return new Result(false, "Train is empty! Cannot delete coach at index " + index + ".", toList(), size, steps);
            }
            if (index < 0 || index >= size) {
                return new Result(false, "Invalid index: " + index + ". Valid coach index range is 0 to " + (size - 1) + ".", toList(), size, steps);
            }

            if (index == 0) {
                return deleteFirst();
            }
            if (index == size - 1) {
                return deleteLast();
            }

            Node current = head;
            steps.add(new Step("MOVE_CURRENT", 0, "Traversing towards coach before target: Node current = head", "Node current = head;", "CURRENT"));

            for (int i = 0; i < index - 1; i++) {
                current = current.next;
                steps.add(new Step("MOVE_CURRENT", i + 1, "Advancing: current = current.next (coach #" + (i + 1) + ")", "current = current.next;", "CURRENT"));
            }

            Node target = current.next;
            String targetData = target.data;
            steps.add(new Step("HIGHLIGHT", index, "Found target coach #" + index + " ('" + targetData + "')", "Node target = current.next;", "TARGET"));
            steps.add(new Step("UNLINK", index - 1, "Rerouting coupler past coach #" + index + ": current.next = current.next.next", "current.next = current.next.next;", "CURRENT"));
            steps.add(new Step("REMOVE_NODE", index, "Coach #" + index + " ('" + targetData + "') detached and shunted off rails", "target.next = null;", "TARGET"));

            current.next = current.next.next;
            size--;

            return new Result(true, "Deleted coach '" + targetData + "' at index " + index, toList(), size, steps);
        }

        public synchronized Result search(String data) {
            List<Step> steps = new ArrayList<>();
            if (head == null) {
                steps.add(new Step("NOT_FOUND", -1, "Train is empty. Search query '" + data + "' not found.", "return -1; // empty list", "NULL"));
                return new Result(false, "Train is empty. Cargo '" + data + "' not found.", toList(), size, steps);
            }

            Node current = head;
            int idx = 0;
            while (current != null) {
                steps.add(new Step("MOVE_CURRENT", idx, "Inspecting coach #" + idx + " (cargo = '" + current.data + "')", "if (current.data.equals(\"" + data + "\"))", "CURRENT"));
                if (current.data.equals(data)) {
                    steps.add(new Step("FOUND", idx, "CARGO LOCATED! Coach #" + idx + " carries '" + data + "'!", "return index; // FOUND AT " + idx, "FOUND"));
                    return new Result(true, "Cargo '" + data + "' found at coach #" + idx + "!", toList(), size, steps);
                }
                current = current.next;
                idx++;
            }

            steps.add(new Step("NOT_FOUND", -1, "Reached NULL (Buffer Stop). Cargo '" + data + "' was not found on this train.", "return -1; // NOT FOUND", "NULL"));
            return new Result(false, "Cargo '" + data + "' not found in any coach.", toList(), size, steps);
        }

        public synchronized Result traverse() {
            List<Step> steps = new ArrayList<>();
            if (head == null) {
                steps.add(new Step("NOT_FOUND", -1, "Platform is clear. Train has 0 coaches.", "// empty train", "HEAD"));
                return new Result(true, "Platform is empty. Train has 0 coaches.", toList(), size, steps);
            }

            Node current = head;
            int idx = 0;
            List<String> visited = new ArrayList<>();
            while (current != null) {
                visited.add(current.data);
                steps.add(new Step("MOVE_CURRENT", idx, "Station Inspector visiting Coach #" + idx + " ('" + current.data + "')", "System.out.print(current.data + \" -> \");", "CURRENT"));
                steps.add(new Step("HIGHLIGHT", idx, "Coach #" + idx + " verified and logged", "current = current.next;", "CURRENT"));
                current = current.next;
                idx++;
            }

            steps.add(new Step("HIGHLIGHT", -1, "Reached NULL (End of Track). Inspection journey complete!", "System.out.println(\"NULL\");", "NULL"));
            String path = String.join(" -> ", visited) + " -> NULL";
            return new Result(true, "Inspection journey complete! Consist: " + path, toList(), size, steps);
        }

        public synchronized Result clear() {
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("UNLINK", 0, "Decoupling all coaches: head = null", "head = null; size = 0;", "HEAD"));
            steps.add(new Step("REMOVE_NODE", -1, "All coaches cleared into depot siding", "// All nodes garbage collected", "NULL"));
            head = null;
            size = 0;
            return new Result(true, "Platform cleared. New empty train initialized.", toList(), size, steps);
        }

        public synchronized Result loadSample() {
            clear();
            addLast("A");
            addLast("B");
            addLast("C");
            addLast("D");
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("CREATE_NODE", 0, "Sample train consist loaded: Coaches A, B, C, D", "sampleConsist();", "HEAD"));
            return new Result(true, "Sample train loaded with coaches: A, B, C, D", toList(), size, steps);
        }

        public synchronized void printList() {
            Node current = head;
            System.out.print("[HEAD] -> ");
            while (current != null) {
                System.out.print("[" + current.data + "] -> ");
                current = current.next;
            }
            System.out.println("NULL (size=" + size + ")");
        }
    }

    // Session tracking: per-visitor LL with last-accessed timestamp
    static class Session {
        final LL list = new LL();
        volatile long lastAccessed = System.currentTimeMillis();
    }

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-cleanup");
        t.setDaemon(true);
        return t;
    });

    private static LL getSessionList(HttpExchange exchange) {
        String sessionId = exchange.getRequestHeaders().getFirst("X-Session-Id");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default";
        }
        Session session = sessions.computeIfAbsent(sessionId, k -> new Session());
        session.lastAccessed = System.currentTimeMillis();
        return session.list;
    }

    // ==========================================
    // ===== SERVER =============================
    // ==========================================

    public static void main(String[] args) {
        String portEnv = System.getenv("PORT");
        boolean isServerEnv = (portEnv != null && !portEnv.trim().isEmpty());
        int port = 8080;
        if (isServerEnv) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT env variable: " + portEnv + ", defaulting to 8080");
                port = 8080;
            }
        } else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = null;
        if (isServerEnv) {
            // When PORT is explicitly configured, bind strictly to 0.0.0.0:PORT without fallback
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            } catch (IOException e) {
                System.err.println("Failed to bind to 0.0.0.0:" + port + ": " + e.getMessage());
                return;
            }
        } else {
            // Local dev mode: bind to 0.0.0.0 with fallback ports if 8080 is in use
            for (int p = port; p <= port + 10; p++) {
                try {
                    server = HttpServer.create(new InetSocketAddress("0.0.0.0", p), 0);
                    port = p;
                    break;
                } catch (java.net.BindException be) {
                    // Port occupied, try next
                } catch (IOException e) {
                    System.err.println("Failed to start server: " + e.getMessage());
                    return;
                }
            }
            if (server == null) {
                System.err.println("Failed to bind to any port in range " + port + " - " + (port + 10));
                return;
            }
        }

        // Periodic cleanup: remove sessions idle for > 30 minutes
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long maxIdleMillis = 30L * 60L * 1000L; // 30 minutes
            sessions.entrySet().removeIf(entry -> (now - entry.getValue().lastAccessed) > maxIdleMillis);
        }, 5, 5, TimeUnit.MINUTES);

        // Health check endpoint for cloud load balancers & monitoring
        server.createContext("/health", exchange -> {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                sendCors(exchange);
                return;
            }
            byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Frontend endpoint
        server.createContext("/", new FrontendHandler());

        // API endpoints
        server.createContext("/api/list", exchange -> handleApi(exchange, list -> 
            new Result(true, "Train retrieved", list.toList(), list.size, Collections.emptyList())));

        server.createContext("/api/addFirst", exchange -> handleApiWithBody(exchange, (list, body) -> {
            String data = parseJsonField(body, "data");
            return list.addFirst(data != null ? data : "A");
        }));

        server.createContext("/api/addLast", exchange -> handleApiWithBody(exchange, (list, body) -> {
            String data = parseJsonField(body, "data");
            return list.addLast(data != null ? data : "Z");
        }));

        server.createContext("/api/addAt", exchange -> handleApiWithBody(exchange, (list, body) -> {
            String data = parseJsonField(body, "data");
            int index = parseJsonInt(body, "index", 0);
            return list.addAt(data != null ? data : "M", index);
        }));

        server.createContext("/api/deleteFirst", exchange -> handleApi(exchange, LL::deleteFirst));
        server.createContext("/api/deleteLast", exchange -> handleApi(exchange, LL::deleteLast));

        server.createContext("/api/deleteAt", exchange -> handleApiWithBody(exchange, (list, body) -> {
            int index = parseJsonInt(body, "index", 0);
            return list.deleteAt(index);
        }));

        server.createContext("/api/search", exchange -> handleApiWithBody(exchange, (list, body) -> {
            String data = parseJsonField(body, "data");
            return list.search(data != null ? data : "");
        }));

        server.createContext("/api/traverse", exchange -> handleApi(exchange, LL::traverse));
        server.createContext("/api/clear", exchange -> handleApi(exchange, LL::clear));
        server.createContext("/api/loadSample", exchange -> handleApi(exchange, LL::loadSample));

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        String url = "http://localhost:" + port;
        System.out.println("==================================================================");
        System.out.println("  * TRAIN LINKED LIST VISUALIZER - RAILWAY MEMORY STATION *");
        System.out.println("  Server is LIVE at: " + url + " (listening on 0.0.0.0:" + port + ")");
        System.out.println("  Health check: " + url + "/health");
        System.out.println("  Press Ctrl+C to terminate.");
        System.out.println("==================================================================");

        // Do not try to open a browser automatically when running on a server / PORT env set
        if (!isServerEnv) {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Throwable ignored) {
                // Headless or unsupported Desktop environments safely ignored
            }
        }
    }

    private static void handleApi(HttpExchange exchange, java.util.function.Function<LL, Result> action) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            sendCors(exchange);
            return;
        }
        LL sessionList = getSessionList(exchange);
        Result res = action.apply(sessionList);
        sessionList.printList();
        sendJsonResponse(exchange, res);
    }

    private static void handleApiWithBody(HttpExchange exchange, java.util.function.BiFunction<LL, String, Result> action) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            sendCors(exchange);
            return;
        }
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        LL sessionList = getSessionList(exchange);
        Result res = action.apply(sessionList, body);
        sessionList.printList();
        sendJsonResponse(exchange, res);
    }

    private static void sendCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Session-Id");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void sendJsonResponse(HttpExchange exchange, Result res) throws IOException {
        String json = toJson(res);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Session-Id");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Mini JSON Serializer
    private static String toJson(Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":").append(r.success).append(",");
        sb.append("\"message\":\"").append(escapeJson(r.message)).append("\",");
        sb.append("\"size\":").append(r.size).append(",");
        sb.append("\"nodes\":[");
        for (int i = 0; i < r.nodes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(r.nodes.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"steps\":[");
        for (int i = 0; i < r.steps.size(); i++) {
            if (i > 0) sb.append(",");
            Step s = r.steps.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(escapeJson(s.type)).append("\",");
            sb.append("\"nodeIndex\":").append(s.nodeIndex).append(",");
            sb.append("\"text\":\"").append(escapeJson(s.text)).append("\",");
            sb.append("\"codeLine\":\"").append(escapeJson(s.codeLine)).append("\",");
            sb.append("\"pointer\":\"").append(escapeJson(s.pointer)).append("\"");
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // Mini JSON Parser
    private static String parseJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx == -1) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = start + 1;
            boolean escaped = false;
            StringBuilder sb = new StringBuilder();
            while (end < json.length()) {
                char c = json.charAt(end);
                if (escaped) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 't') sb.append('\t');
                    else sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
                end++;
            }
            return sb.toString();
        } else {
            int end = start;
            while (end < json.length() && (Character.isLetterOrDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '+')) {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    private static int parseJsonInt(String json, String field, int defaultValue) {
        String val = parseJsonField(json, field);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static class FrontendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = getHtmlBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ==========================================
    // ===== FRONTEND (HTML/CSS/JS) =============
    // ==========================================
        private static final String HTML_PAGE_PART1 = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>orbital.metro // linked list</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=Syne:wght@700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
<style>
  :root {
    --bg-space: #030712;
    --navy-deep: #060d1f;
    --navy-card: rgba(15, 23, 42, 0.72);
    --border-glass: rgba(255, 255, 255, 0.12);
    --silver-100: #f8fafc;
    --silver-300: #cbd5e1;
    --silver-500: #94a3b8;
    --silver-700: #475569;
    --neon-cyan: #00f2fe;
    --neon-pink: #ff007f;
    --neon-amber: #f59e0b;
    --neon-emerald: #10b981;
    --neon-red: #ef4444;
    --earth-blue: #0284c7;
    --font-heading: 'Syne', system-ui, -apple-system, sans-serif;
    --font-main: 'Space Grotesk', system-ui, -apple-system, sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
  }

  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    -webkit-font-smoothing: antialiased;
  }

  body {
    background: var(--bg-space);
    color: var(--silver-100);
    font-family: var(--font-main);
    height: 100vh;
    width: 100vw;
    overflow: hidden;
    position: relative;
    user-select: none;
    cursor: none; /* Custom cursor replaces native cursor */
  }

  @media (pointer: coarse) {
    body { cursor: auto; }
  }

  /* CANVAS BACKGROUND (STARS & WARP) */
  #starCanvas {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;
    pointer-events: none;
  }

  /* EARTH HORIZON CURVE */
  .earth-horizon-wrap {
    position: absolute;
    bottom: -46vh;
    left: -15vw;
    right: -15vw;
    height: 80vh;
    border-radius: 50% 50% 0 0;
    background: radial-gradient(ellipse at 50% 0%, #032b69 0%, #02183a 45%, #010a1a 80%, transparent 100%);
    box-shadow: 0 -14px 45px rgba(56, 189, 248, 0.45), 0 -2px 14px rgba(255, 255, 255, 0.85), inset 0 10px 40px rgba(56, 189, 248, 0.25);
    z-index: 1;
    pointer-events: none;
    transition: transform 1.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .earth-aurora {
    position: absolute;
    top: 0;
    left: 10%;
    right: 10%;
    height: 45px;
    background: radial-gradient(ellipse at 50% 0%, rgba(16, 185, 129, 0.3) 0%, rgba(6, 182, 212, 0.2) 40%, transparent 80%);
    filter: blur(10px);
    animation: auroraBreathe 6s ease-in-out infinite alternate;
  }

  @keyframes auroraBreathe {
    0% { opacity: 0.4; transform: scaleX(0.9); }
    100% { opacity: 0.85; transform: scaleX(1.1); }
  }

  .earth-cities-svg {
    position: absolute;
    top: 15px;
    left: 0;
    width: 100%;
    height: 120px;
    opacity: 0.75;
  }

  /* ELEVATED PLATFORM AT BOTTOM-LEFT */
  .elevated-platform {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 380px;
    height: 140px;
    z-index: 2;
    pointer-events: none;
    transition: transform 1.2s cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .platform-slab {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 90px;
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
    clip-path: polygon(0 20%, 90% 65%, 100% 100%, 0 100%);
    box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.7);
    border-top: 2px solid #334155;
  }

  .platform-tactile-strip {
    position: absolute;
    top: 22px;
    left: 0;
    width: 90%;
    height: 12px;
    background: repeating-linear-gradient(90deg, #f59e0b 0 14px, #0284c7 14px 28px);
    box-shadow: 0 0 10px rgba(245, 158, 11, 0.3);
    clip-path: polygon(0 0, 100% 50%, 98% 100%, 0 100%);
  }

  .canopy-silhouette {
    position: absolute;
    top: 0;
    left: 0;
    width: 220px;
    height: 110px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  .canopy-beam {
    width: 180px;
    height: 8px;
    background: #090d16;
    border-radius: 0 4px 4px 0;
    box-shadow: 0 2px 8px rgba(0,0,0,0.8);
  }

  .exit-sign {
    background: #022c22;
    color: #34d399;
    border: 1px solid #10b981;
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 1px;
    padding: 3px 8px;
    border-radius: 4px;
    width: fit-content;
    margin-left: 20px;
    margin-top: 6px;
    box-shadow: 0 0 14px rgba(16, 185, 129, 0.6), inset 0 0 4px rgba(16, 185, 129, 0.4);
    animation: neonFlicker 4s infinite alternate;
  }

  @keyframes neonFlicker {
    0%, 100% { opacity: 1; }
    85% { opacity: 0.95; }
    86% { opacity: 0.4; }
    87% { opacity: 0.9; }
    92% { opacity: 0.3; }
    93% { opacity: 1; }
  }

  /* RAILS IN PERSPECTIVE */
  .track-bed {
    position: absolute;
    bottom: 85px;
    left: 0;
    right: 0;
    height: 36px;
    z-index: 2;
    pointer-events: none;
    transition: transform 1.2s cubic-bezier(0.2, 0.8, 0.2, 1);
  }

  .track-rail-steel {
    position: absolute;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(180deg, #f8fafc 0%, #94a3b8 50%, #475569 100%);
    box-shadow: 0 0 8px rgba(255, 255, 255, 0.4), 0 2px 4px rgba(0,0,0,0.8);
  }
  .track-rail-steel.top { top: 6px; }
  .track-rail-steel.bottom { bottom: 6px; }

  .track-ties {
    position: absolute;
    inset: 0;
    background: repeating-linear-gradient(90deg, transparent, transparent 24px, rgba(30, 41, 59, 0.9) 24px, rgba(15, 23, 42, 0.95) 30px);
  }

  /* TOP NAVIGATION */
  .top-bar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    padding: 16px 28px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 20;
    transition: opacity 0.6s ease;
  }

  .nav-tag {
    font-family: var(--font-mono);
    font-size: 13px;
    color: var(--silver-300);
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(15, 23, 42, 0.6);
    padding: 6px 14px;
    border-radius: 20px;
    border: 1px solid var(--border-glass);
    backdrop-filter: blur(10px);
  }

  .tag-dot {
    width: 7px;
    height: 7px;
    background: var(--neon-emerald);
    border-radius: 50%;
    box-shadow: 0 0 8px var(--neon-emerald);
  }

  .top-right-group {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .chip-pill {
    font-family: var(--font-mono);
    font-size: 12px;
    padding: 5px 12px;
    border-radius: 16px;
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid var(--border-glass);
    backdrop-filter: blur(10px);
    color: var(--silver-300);
  }
  .chip-pill.highlight {
    color: var(--neon-cyan);
    border-color: rgba(0, 242, 254, 0.3);
  }

  /* MAIN FULL-SCREEN CONTAINER */
  .main-stage {
    position: relative;
    width: 100vw;
    height: 100vh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    z-index: 10;
  }

  /* CENTRAL HEADLINE */
  .hero-headline {
    text-align: center;
    margin-bottom: 24px;
    z-index: 5;
    transition: opacity 0.8s ease, transform 0.8s ease;
  }

  .hero-headline h1 {
    font-family: var(--font-heading);
    font-size: clamp(32px, 4.5vw, 54px);
    font-weight: 800;
    letter-spacing: -0.5px;
    background: linear-gradient(135deg, #ffffff 20%, #cbd5e1 60%, #94a3b8 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    text-shadow: 0 10px 30px rgba(0,0,0,0.5);
    line-height: 1.1;
  }

  .hero-headline p {
    font-size: 14px;
    color: var(--neon-cyan);
    font-weight: 500;
    margin-top: 6px;
    letter-spacing: 1px;
    text-transform: lowercase;
  }

  /* 6 PLATFORM CARDS IN AN ARC / ORBIT */
  .platform-arc {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 16px;
    margin-bottom: 30px;
    z-index: 8;
    perspective: 1000px;
    transition: opacity 0.5s ease;
  }

  @media (max-width: 960px) {
    .platform-arc {
      overflow-x: auto;
      max-width: 95vw;
      padding: 10px 10px;
      justify-content: flex-start;
      scroll-snap-type: x mandatory;
    }
  }

  .platform-card {
    width: 160px;
    background: var(--navy-card);
    border: 1px solid var(--border-glass);
    border-radius: 18px;
    padding: 16px 14px;
    backdrop-filter: blur(18px);
    box-shadow: 0 12px 30px rgba(0, 0, 0, 0.45);
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 0.35s ease, opacity 0.35s ease, border-color 0.35s ease;
    transform-origin: center bottom;
    position: relative;
    flex-shrink: 0;
  }

  /* Arc Tilts */
  .card-1 { transform: translateY(16px) rotate(-9deg); }
  .card-2 { transform: translateY(4px) rotate(-4.5deg); }
  .card-3 { transform: translateY(-4px) rotate(-1.5deg); }
  .card-4 { transform: translateY(-4px) rotate(1.5deg); }
  .card-5 { transform: translateY(4px) rotate(4.5deg); }
  .card-6 { transform: translateY(16px) rotate(9deg); }

  .platform-card:hover {
    transform: translateY(-16px) scale(1.12) rotate(0deg) !important;
    border-color: var(--neon-cyan) !important;
    box-shadow: 0 20px 40px rgba(0, 242, 254, 0.25), 0 0 20px rgba(255, 0, 127, 0.25) !important;
    z-index: 25;
  }

  .platform-arc.has-hover .platform-card:not(:hover) {
    opacity: 0.45;
    filter: blur(0.5px);
  }

  .platform-card.dimmed {
    opacity: 0.35 !important;
    pointer-events: none;
  }

  .card-icon-wrap {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.05);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 10px;
    transition: transform 0.3s ease;
  }

  .platform-card:hover .card-icon-wrap {
    transform: scale(1.15);
  }

  .card-icon-svg {
    width: 24px;
    height: 24px;
  }

  .card-tag {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--silver-500);
    letter-spacing: 1px;
    margin-bottom: 4px;
    text-transform: uppercase;
  }

  .card-title {
    font-family: var(--font-heading);
    font-size: 15px;
    font-weight: 700;
    color: var(--silver-100);
    line-height: 1.2;
    margin-bottom: 4px;
  }

  .card-real-name {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--neon-cyan);
    font-weight: 500;
  }

  /* HORIZONTAL TRAIN TRACK VIEWPORT (CENTER OF SCREEN) */
  .train-stage-container {
    position: relative;
    width: 100%;
    max-width: 1100px;
    height: 150px;
    margin: 0 auto;
    overflow-x: auto;
    overflow-y: visible;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9;
    padding: 0 40px;
    scroll-behavior: smooth;
  }

  .train-stage-container::-webkit-scrollbar {
    display: none;
  }

  .train-consist {
    display: inline-flex;
    align-items: flex-end;
    position: relative;
    transition: transform 0.6s cubic-bezier(0.2, 0.8, 0.2, 1), opacity 0.5s ease;
  }

  /* SIGNAL LIGHT / SENSOR MARKER (CENTER OF TRACK) */
  .signal-center-marker {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
    z-index: 3;
    opacity: 0.3;
    transition: opacity 0.4s ease, transform 0.4s ease;
  }

  .signal-beacon {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--neon-cyan);
    box-shadow: 0 0 12px var(--neon-cyan);
  }

  .signal-line {
    width: 2px;
    height: 60px;
    background: linear-gradient(180deg, var(--neon-cyan), transparent);
  }

  .signal-center-marker.active {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.2);
  }

  /* SILVER METRO TRAIN COMPONENTS */
  /* 1. LOCOMOTIVE ENGINE (HEAD) */
  .silver-engine {
    width: 135px;
    height: 74px;
    background: linear-gradient(180deg, #f8fafc 0%, #cbd5e1 35%, #94a3b8 70%, #475569 100%);
    border: 2px solid #cbd5e1;
    border-radius: 10px 24px 4px 4px;
    position: relative;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 6px;
    flex-shrink: 0;
    transition: transform 0.4s ease, box-shadow 0.4s ease;
  }

  /* Corrugated side panels */
  .corrugated-panel {
    position: absolute;
    bottom: 12px;
    left: 8px;
    right: 8px;
    height: 22px;
    background: repeating-linear-gradient(180deg, transparent 0 3px, rgba(15, 23, 42, 0.15) 3px 5px);
    border-radius: 2px;
  }

  .engine-windshield {
    position: absolute;
    top: 8px;
    right: 12px;
    width: 28px;
    height: 24px;
    background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
    border: 1.5px solid #38bdf8;
    border-radius: 4px;
    box-shadow: inset 0 0 8px rgba(0,0,0,0.5);
  }

  .engine-destination-board {
    position: absolute;
    top: 6px;
    left: 12px;
    background: #020617;
    border: 1px solid var(--neon-amber);
    color: var(--neon-amber);
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 1px;
    padding: 2px 6px;
    border-radius: 3px;
    box-shadow: 0 0 8px rgba(245, 158, 11, 0.5);
  }

  .engine-headlights {
    position: absolute;
    left: -6px;
    bottom: 20px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .headlight-lamp {
    width: 8px;
    height: 10px;
    background: #fef08a;
    border-radius: 4px 0 0 4px;
    box-shadow: -12px 0 24px 8px rgba(254, 240, 138, 0.85);
  }

  .engine-wheels {
    display: flex;
    justify-content: space-around;
    width: 100%;
    margin-top: 2px;
  }

  .silver-wheel {
    width: 18px;
    height: 18px;
    background: radial-gradient(circle, #f1f5f9 25%, #475569 35%, #0f172a 100%);
    border: 1.5px solid #94a3b8;
    border-radius: 50%;
    box-shadow: 0 2px 4px rgba(0,0,0,0.7);
  }

  /* 2. SILVER COACH (NODE) */
  .silver-coach {
    display: flex;
    flex-direction: column;
    align-items: center;
    position: relative;
    flex-shrink: 0;
    transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1), filter 0.4s ease;
  }

  .coach-body-silver {
    width: 125px;
    height: 74px;
    background: linear-gradient(180deg, #f8fafc 0%, #cbd5e1 35%, #94a3b8 70%, #475569 100%);
    border: 2px solid #cbd5e1;
    border-radius: 8px;
    position: relative;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 6px;
    transition: all 0.3s ease;
  }

  .coach-destination-board {
    background: #020617;
    border: 1px solid var(--neon-amber);
    color: var(--neon-amber);
    font-family: var(--font-mono);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.5px;
    padding: 2px 6px;
    border-radius: 4px;
    text-align: center;
    box-shadow: 0 0 8px rgba(245, 158, 11, 0.4);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .coach-windows-row {
    display: flex;
    gap: 6px;
    margin-top: 4px;
    padding: 0 4px;
  }

  .coach-window-glass {
    flex: 1;
    height: 16px;
    background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
    border: 1px solid #475569;
    border-radius: 3px;
    box-shadow: inset 0 0 6px rgba(254, 215, 170, 0.4);
  }

  .coach-door-seam {
    position: absolute;
    bottom: 10px;
    left: 50%;
    transform: translateX(-50%);
    width: 22px;
    height: 30px;
    border-left: 1px solid #475569;
    border-right: 1px solid #475569;
  }

  .coach-index-badge {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    color: var(--silver-300);
    background: rgba(15, 23, 42, 0.85);
    border: 1px solid var(--border-glass);
    padding: 2px 8px;
    border-radius: 10px;
    margin-top: 6px;
    box-shadow: 0 2px 6px rgba(0,0,0,0.5);
  }

  /* 3. COUPLER (NEXT POINTER) */
  .silver-coupler {
    width: 42px;
    height: 74px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    position: relative;
    flex-shrink: 0;
  }

  .coupler-bar {
    width: 100%;
    height: 6px;
    background: linear-gradient(180deg, #94a3b8 0%, #475569 100%);
    border-radius: 2px;
    position: relative;
    box-shadow: 0 0 8px rgba(0, 242, 254, 0.4);
    transition: all 0.3s ease;
  }

  .coupler-arrow-neon {
    position: absolute;
    right: 2px;
    top: 50%;
    transform: translateY(-50%);
    width: 0;
    height: 0;
    border-top: 5px solid transparent;
    border-bottom: 5px solid transparent;
    border-left: 8px solid var(--neon-cyan);
    filter: drop-shadow(0 0 4px var(--neon-cyan));
  }

  .silver-coupler.unlinked .coupler-bar {
    background: var(--neon-red);
    box-shadow: 0 0 10px var(--neon-red);
    opacity: 0.3;
  }

  /* 4. BUFFER STOP (NULL) */
  .buffer-stop-unit {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-left: 4px;
    flex-shrink: 0;
  }

  .silver-buffer-stop {
    width: 62px;
    height: 74px;
    background: repeating-linear-gradient(-45deg, #ef4444, #ef4444 8px, #ffffff 8px, #ffffff 16px);
    border: 2px solid #991b1b;
    border-radius: 6px;
    position: relative;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }

  .buffer-stop-light {
    position: absolute;
    top: -10px;
    width: 12px;
    height: 12px;
    background: #ef4444;
    border-radius: 50%;
    box-shadow: 0 0 14px #ef4444;
  }

  .buffer-stop-plate {
    background: #020617;
    border: 1px solid #ef4444;
    color: #ef4444;
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 800;
    padding: 2px 6px;
    border-radius: 3px;
    letter-spacing: 1px;
  }

  .silver-buffer-stop.shake {
    animation: shakeBuffer 0.5s ease-in-out;
  }

  @keyframes shakeBuffer {
    0%, 100% { transform: translateX(0); }
    20%, 60% { transform: translateX(-8px); }
    40%, 80% { transform: translateX(8px); }
  }

  /* POINTER LABELS FLOATING ABOVE COACHES */
  .pointer-badge {
    position: absolute;
    top: -38px;
    left: 50%;
    transform: translateX(-50%);
    padding: 3px 10px;
    border-radius: 12px;
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    white-space: nowrap;
    z-index: 15;
    animation: bouncePointer 1.2s infinite alternate ease-in-out;
  }

  @keyframes bouncePointer {
    0% { transform: translateX(-50%) translateY(0); }
    100% { transform: translateX(-50%) translateY(-4px); }
  }

  .pointer-badge.head {
    background: #0284c7;
    color: #fff;
    box-shadow: 0 0 12px #38bdf8;
    border: 1px solid #38bdf8;
  }
  .pointer-badge.current {
    background: var(--neon-amber);
    color: #000;
    box-shadow: 0 0 14px var(--neon-amber);
    border: 1px solid #fde68a;
  }
  .pointer-badge.new-node {
    background: var(--neon-emerald);
    color: #000;
    box-shadow: 0 0 14px var(--neon-emerald);
    border: 1px solid #6ee7b7;
  }
  .pointer-badge.found {
    background: var(--neon-emerald);
    color: #fff;
    box-shadow: 0 0 20px var(--neon-emerald);
  }

  /* POPPING ANIMATION (JOYRIDE & SEARCH) */
  .silver-coach.pop {
    transform: translateY(-22px) scale(1.22) !important;
    z-index: 20;
    filter: drop-shadow(0 0 25px var(--neon-cyan)) drop-shadow(0 0 10px var(--neon-pink)) !important;
  }
  .silver-coach.pop .coach-body-silver {
    border-color: var(--neon-cyan) !important;
  }

  .silver-coach.found-glow {
    transform: translateY(-16px) scale(1.15) !important;
    filter: drop-shadow(0 0 30px var(--neon-emerald)) !important;
  }
  .silver-coach.found-glow .coach-body-silver {
    border-color: var(--neon-emerald) !important;
  }

  /* COACH DROP IN & REMOVE ANIMATIONS */
  .silver-coach.dropping {
    animation: dropInCoach 0.55s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  }

  @keyframes dropInCoach {
    0% { transform: translateY(-120px) scale(0.6); opacity: 0; }
    100% { transform: translateY(0) scale(1); opacity: 1; }
  }

  .silver-coach.removing {
    animation: fallOffCoach 0.65s ease-in forwards;
  }

  @keyframes fallOffCoach {
    0% { transform: translateY(0) rotate(0deg); opacity: 1; }
    50% { transform: translateY(30px) rotate(12deg); opacity: 0.7; }
    100% { transform: translateY(120px) rotate(28deg); opacity: 0; }
  }

  /* SPARKLE PARTICLES CONTAINER */
  .sparkle-particle {
    position: absolute;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--neon-cyan);
    box-shadow: 0 0 8px var(--neon-cyan);
    pointer-events: none;
    animation: sparkleFly 0.6s ease-out forwards;
  }

  @keyframes sparkleFly {
    0% { transform: translate(0, 0) scale(1); opacity: 1; }
    100% { transform: translate(var(--tx), var(--ty)) scale(0); opacity: 0; }
  }

  /* SPEECH-BUBBLE CAPTION UNDER TRACK */
  .speech-bubble-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 14px;
    z-index: 8;
  }

  .speech-bubble {
    background: rgba(15, 23, 42, 0.75);
    border: 1px solid var(--border-glass);
    border-radius: 20px;
    padding: 8px 18px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 500;
    color: var(--silver-100);
    backdrop-filter: blur(12px);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
    transition: all 0.3s ease;
  }

  .speech-bubble .bubble-icon {
    font-size: 14px;
  }

  .speech-bubble .bubble-text {
    font-family: var(--font-mono);
    color: var(--neon-cyan);
  }

  /* SIGNAL SPINNER NEAR TRACK */
  .track-spinner {
    width: 14px;
    height: 14px;
    border: 2px solid rgba(0, 242, 254, 0.2);
    border-top-color: var(--neon-cyan);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    display: none;
  }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  /* BOTTOM PILL BUTTONS */
  .bottom-pills {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 24px;
    z-index: 8;
    transition: opacity 0.5s ease;
  }

  .pill-btn {
    background: rgba(15, 23, 42, 0.7);
    color: var(--silver-100);
    border: 1px solid var(--border-glass);
    padding: 8px 20px;
    font-family: var(--font-main);
    font-size: 13px;
    font-weight: 600;
    border-radius: 24px;
    cursor: pointer;
    backdrop-filter: blur(12px);
    transition: all 0.25s ease;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .pill-btn:hover:not(:disabled) {
    background: rgba(30, 41, 59, 0.9);
    border-color: var(--neon-cyan);
    transform: translateY(-2px);
    box-shadow: 0 6px 18px rgba(0, 242, 254, 0.25);
  }

  .pill-btn:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }

  /* FLOATING TICKET MODAL */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(2, 6, 23, 0.65);
    backdrop-filter: blur(8px);
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 100;
    animation: fadeIn 0.2s ease;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .ticket-modal {
    background: rgba(15, 23, 42, 0.88);
    border: 1px solid rgba(255, 255, 255, 0.18);
    border-radius: 24px;
    padding: 24px 28px;
    width: 90%;
    max-width: 440px;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.7), 0 0 30px rgba(0, 242, 254, 0.15);
    backdrop-filter: blur(24px);
    position: relative;
    animation: scaleModal 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  @keyframes scaleModal {
    from { transform: scale(0.9); opacity: 0; }
    to { transform: scale(1); opacity: 1; }
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 18px;
  }

  .modal-title-group {
    display: flex;
    flex-direction: column;
  }

  .modal-platform-tag {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    color: var(--neon-cyan);
    letter-spacing: 1px;
    text-transform: uppercase;
  }

  .modal-heading {
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 700;
    color: #fff;
  }

  .modal-close-btn {
    background: rgba(255, 255, 255, 0.08);
    border: none;
    color: var(--silver-300);
    width: 32px;
    height: 32px;
    border-radius: 50%;
    font-size: 14px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;
  }
  .modal-close-btn:hover {
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
  }

  .modal-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .input-label-stylish {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 12px;
    color: var(--silver-300);
    font-weight: 600;
  }

  .input-stylish {
    background: rgba(2, 6, 23, 0.8);
    border: 1px solid var(--border-glass);
    border-radius: 12px;
    padding: 10px 14px;
    font-family: var(--font-mono);
    font-size: 15px;
    color: #fff;
    outline: none;
    transition: border-color 0.2s;
  }
  .input-stylish:focus {
    border-color: var(--neon-cyan);
    box-shadow: 0 0 10px rgba(0, 242, 254, 0.3);
  }

  .preset-chips-row {
    display: flex;
    gap: 6px;
    margin-top: 4px;
  }

  .preset-chip {
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(255, 255, 255, 0.1);
    color: var(--silver-300);
    font-family: var(--font-mono);
    font-size: 11px;
    padding: 3px 8px;
    border-radius: 6px;
    cursor: pointer;
    font-weight: 600;
    transition: all 0.2s;
  }
  .preset-chip:hover {
    background: rgba(0, 242, 254, 0.15);
    color: var(--neon-cyan);
    border-color: var(--neon-cyan);
  }

  .delete-mode-selector {
    display: flex;
    gap: 8px;
    background: rgba(2, 6, 23, 0.6);
    padding: 4px;
    border-radius: 12px;
    border: 1px solid var(--border-glass);
  }

  .delete-mode-tab {
    flex: 1;
    text-align: center;
    padding: 8px;
    font-size: 12px;
    font-weight: 700;
    border-radius: 8px;
    cursor: pointer;
    color: var(--silver-500);
    transition: all 0.2s;
  }
  .delete-mode-tab.active {
    background: rgba(239, 68, 68, 0.2);
    color: var(--neon-red);
    border: 1px solid rgba(239, 68, 68, 0.4);
  }

  .modal-action-btn {
    background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
    border: 1px solid #38bdf8;
    color: #fff;
    padding: 12px;
    border-radius: 14px;
    font-family: var(--font-heading);
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.5px;
    cursor: pointer;
    margin-top: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    box-shadow: 0 4px 18px rgba(2, 132, 199, 0.4);
    transition: all 0.25s ease;
  }
  .modal-action-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(2, 132, 199, 0.6);
  }
  .modal-action-btn.delete {
    background: linear-gradient(135deg, #991b1b 0%, #7f1d1d 100%);
    border-color: #ef4444;
    box-shadow: 0 4px 18px rgba(239, 68, 68, 0.4);
  }
  .modal-action-btn.delete:hover {
    box-shadow: 0 8px 24px rgba(239, 68, 68, 0.6);
  }

  /* TOAST ALERT CAPSULE */
  .toast-capsule {
    position: fixed;
    bottom: 24px;
    right: 24px;
    background: rgba(15, 23, 42, 0.95);
    border: 1px solid var(--neon-pink);
    color: #fff;
    padding: 10px 18px;
    border-radius: 20px;
    font-family: var(--font-mono);
    font-size: 13px;
    display: none;
    align-items: center;
    gap: 10px;
    box-shadow: 0 8px 25px rgba(255, 0, 127, 0.35);
    backdrop-filter: blur(14px);
    z-index: 200;
    animation: slideToast 0.3s ease;
  }

  @keyframes slideToast {
    from { transform: translateX(40px); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
  }

  /* OPENING SEQUENCE ELEMENTS */
  .blackout-screen {
    position: fixed;
    inset: 0;
    background: #020617;
    z-index: 90;
    pointer-events: none;
    transition: opacity 0.8s ease;
  }

  .portal-flash {
    position: fixed;
    inset: 0;
    background: radial-gradient(circle, rgba(255,255,255,0.95) 0%, rgba(56,189,248,0.4) 60%, transparent 100%);
    opacity: 0;
    pointer-events: none;
    z-index: 95;
    transition: opacity 0.6s ease;
  }

  /* CUSTOM CURSOR & INITIAL LOADER */
  .custom-cursor {
    position: fixed;
    top: 0;
    left: 0;
    pointer-events: none;
    z-index: 1000;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    transform: translate(-50%, -50%);
    mix-blend-mode: screen;
  }

  @media (pointer: coarse) {
    .custom-cursor { display: none !important; }
  }

  .cursor-dot {
    width: 6px;
    height: 6px;
    background: #fff;
    border-radius: 50%;
    position: absolute;
    box-shadow: 0 0 8px #fff;
  }

  .cursor-ring {
    width: 44px;
    height: 44px;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: width 0.25s, height 0.25s, transform 0.25s, border-color 0.25s;
  }

  .cursor-svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
  }

  .ring-bg {
    fill: none;
    stroke: rgba(255, 255, 255, 0.15);
    stroke-width: 2.5;
  }

  .ring-progress {
    fill: none;
    stroke: var(--neon-cyan);
    stroke-width: 2.5;
    stroke-dasharray: 113.1;
    stroke-dashoffset: 113.1;
    stroke-linecap: round;
    transition: stroke-dashoffset 0.05s linear;
  }

  .cursor-percent {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: #fff;
  }

  .cursor-label {
    position: absolute;
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: 700;
    color: #000;
    background: var(--neon-cyan);
    padding: 1px 5px;
    border-radius: 4px;
    white-space: nowrap;
    opacity: 0;
    transform: scale(0.8);
    transition: opacity 0.2s, transform 0.2s;
  }

  .cursor-init-text {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--neon-cyan);
    letter-spacing: 1px;
    margin-top: 14px;
    white-space: nowrap;
    text-shadow: 0 0 8px rgba(0, 242, 254, 0.6);
  }

  .cursor-mini-train {
    display: none;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    filter: drop-shadow(0 0 8px var(--neon-cyan));
  }

  .mini-steam-trail {
    position: absolute;
    left: -8px;
    top: 6px;
    width: 6px;
    height: 6px;
    background: rgba(255, 255, 255, 0.6);
    border-radius: 50%;
    filter: blur(1px);
    animation: steamPuff 0.6s infinite ease-out;
  }

  @keyframes steamPuff {
    0% { transform: scale(0.6) translateX(0); opacity: 0.8; }
    100% { transform: scale(1.6) translateX(-12px); opacity: 0; }
  }

  /* HOVER CURSOR STATES */
  .custom-cursor.hovering .cursor-ring {
    width: 60px;
    height: 60px;
  }
  .custom-cursor.hovering .cursor-label {
    opacity: 1;
    transform: scale(1);
  }
  .custom-cursor.hovering .ring-progress {
    stroke: var(--neon-pink);
  }

  .custom-cursor.train-active .cursor-dot,
  .custom-cursor.train-active .cursor-ring,
  .custom-cursor.train-active .cursor-init-text {
    display: none !important;
  }
  .custom-cursor.train-active .cursor-mini-train {
    display: block !important;
  }

  /* PORTAL BURST ANIMATION */
  .portal-burst {
    transform: scale(35) !important;
    opacity: 0 !important;
    transition: transform 0.7s cubic-bezier(0.1, 0.9, 0.2, 1), opacity 0.7s ease !important;
  }

  /* PREFERS-REDUCED-MOTION */
  @media (prefers-reduced-motion: reduce) {
    * {
      animation-duration: 0.01ms !important;
      transition-duration: 0.01ms !important;
    }
  }
</style>""";

    private static final String HTML_PAGE_PART2 = """

</head>
<body>

  <!-- STARS & WARP CANVAS -->
  <canvas id="starCanvas"></canvas>

  <!-- EARTH HORIZON CURVE -->
  <div class="earth-horizon-wrap" id="earthHorizon">
    <div class="earth-aurora"></div>
    <svg class="earth-cities-svg" viewBox="0 0 1000 120" preserveAspectRatio="none">
      <defs>
        <radialGradient id="cityGlow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#fbbf24" stop-opacity="0.9"/>
          <stop offset="100%" stop-color="#f59e0b" stop-opacity="0"/>
        </radialGradient>
      </defs>
      <!-- Scattered golden city dots on Earth -->
      <circle cx="220" cy="30" r="4" fill="url(#cityGlow)"/>
      <circle cx="228" cy="34" r="2" fill="#fbbf24"/>
      <circle cx="215" cy="38" r="3" fill="#f59e0b"/>
      <circle cx="480" cy="22" r="5" fill="url(#cityGlow)"/>
      <circle cx="492" cy="28" r="2.5" fill="#fbbf24"/>
      <circle cx="475" cy="32" r="3" fill="#f59e0b"/>
      <circle cx="750" cy="35" r="4" fill="url(#cityGlow)"/>
      <circle cx="760" cy="40" r="2" fill="#fbbf24"/>
      <circle cx="742" cy="44" r="3" fill="#f59e0b"/>
      <circle cx="340" cy="42" r="3" fill="url(#cityGlow)"/>
      <circle cx="620" cy="36" r="3.5" fill="url(#cityGlow)"/>
    </svg>
  </div>

  <!-- ELEVATED METRO PLATFORM (BOTTOM-LEFT PERSPECTIVE) -->
  <div class="elevated-platform" id="stationPlatform">
    <div class="canopy-silhouette">
      <div class="canopy-beam"></div>
      <div class="exit-sign">[ EXIT ↗ ]</div>
    </div>
    <div class="platform-slab"></div>
    <div class="platform-tactile-strip"></div>
  </div>

  <!-- STEEL RAILS ACROSS BOTTOM OF TRACK -->
  <div class="track-bed" id="trackBed">
    <div class="track-ties"></div>
    <div class="track-rail-steel top"></div>
    <div class="track-rail-steel bottom"></div>
  </div>

  <!-- TOP BAR -->
  <header class="top-bar" id="topBar">
    <div class="nav-tag">
      <span class="tag-dot"></span>
      <span>orbital.metro // linkedlist.exe</span>
    </div>
    <div class="top-right-group">
      <span class="chip-pill highlight" id="chipCoaches">coaches: 0</span>
      <span class="chip-pill">v2.4 [2026]</span>
    </div>
  </header>

  <!-- MAIN STAGE -->
  <main class="main-stage">
    <!-- HERO HEADLINE (INSIDE ARC) -->
    <div class="hero-headline" id="heroHeadline">
      <h1>ride the linked list</h1>
      <p>next stop: everywhere &bull; memory junction</p>
    </div>

    <!-- 6 PLATFORM CARDS IN AN ARC -->
    <div class="platform-arc" id="platformArc">
      <!-- 1. Front Seat (addFirst) -->
      <div class="platform-card card-1" onclick="openPlatform(1)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#00f2fe" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"></polyline>
            <path d="M19 12H9"></path>
          </svg>
        </div>
        <div class="card-tag">Platform 01</div>
        <div class="card-title">Front Seat</div>
        <div class="card-real-name">addFirst()</div>
      </div>

      <!-- 2. Tail End (addLast) -->
      <div class="platform-card card-2" onclick="openPlatform(2)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6"></polyline>
            <path d="M5 12h10"></path>
          </svg>
        </div>
        <div class="card-tag">Platform 02</div>
        <div class="card-title">Tail End</div>
        <div class="card-real-name">addLast()</div>
      </div>

      <!-- 3. Pick a Spot (addAt) -->
      <div class="platform-card card-3" onclick="openPlatform(3)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#38bdf8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="16"></line>
            <line x1="8" y1="12" x2="16" y2="12"></line>
          </svg>
        </div>
        <div class="card-tag">Platform 03</div>
        <div class="card-title">Pick a Spot</div>
        <div class="card-real-name">addAt(pos)</div>
      </div>

      <!-- 4. Drop Off (delete) -->
      <div class="platform-card card-4" onclick="openPlatform(4)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#ff007f" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="6" cy="6" r="3"></circle>
            <circle cx="6" cy="18" r="3"></circle>
            <line x1="20" y1="4" x2="8.12" y2="15.88"></line>
            <line x1="14.47" y1="14.48" x2="20" y2="20"></line>
            <line x1="8.12" y1="8.12" x2="12" y2="12"></line>
          </svg>
        </div>
        <div class="card-tag">Platform 04</div>
        <div class="card-title">Drop Off</div>
        <div class="card-real-name">delete()</div>
      </div>

      <!-- 5. Spot It (search) -->
      <div class="platform-card card-5" onclick="openPlatform(5)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
        </div>
        <div class="card-tag">Platform 05</div>
        <div class="card-title">Spot It</div>
        <div class="card-real-name">search()</div>
      </div>

      <!-- 6. Joyride (traverse) -->
      <div class="platform-card card-6" onclick="openPlatform(6)" onmouseenter="onCardHover('board')" onmouseleave="onCardLeave()">
        <div class="card-icon-wrap">
          <svg class="card-icon-svg" viewBox="0 0 24 24" fill="none" stroke="#f43f5e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
          </svg>
        </div>
        <div class="card-tag">Platform 06</div>
        <div class="card-title">Joyride</div>
        <div class="card-real-name">traverse()</div>
      </div>
    </div>

    <!-- HORIZONTAL TRACK & SILVER METRO TRAIN -->
    <div class="train-stage-container" id="trainStageContainer">
      <!-- CENTER SIGNAL SENSOR FOR POP EFFECT -->
      <div class="signal-center-marker" id="signalCenterMarker">
        <div class="signal-beacon"></div>
        <div class="signal-line"></div>
      </div>

      <!-- TRAIN CONSIST -->
      <div class="train-consist" id="trainAssembly">
        <!-- Rendered dynamically by JS -->
      </div>
    </div>

    <!-- SPEECH-BUBBLE CAPTION UNDER TRACK -->
    <div class="speech-bubble-wrap" id="speechWrap">
      <div class="speech-bubble" id="speechBubble">
        <span class="bubble-icon">💬</span>
        <span class="bubble-text" id="speechText">ready for dispatch</span>
      </div>
      <div class="track-spinner" id="trackSpinner"></div>
    </div>

    <!-- BOTTOM PILL CONTROLS -->
    <div class="bottom-pills" id="bottomPills">
      <button class="pill-btn" id="btnSample" onclick="loadSampleTrain()" onmouseenter="onBtnHover('load')" onmouseleave="onCardLeave()">
        <span>⚡</span> Load sample train
      </button>
      <button class="pill-btn" id="btnClear" onclick="clearTrain()" onmouseenter="onBtnHover('reset')" onmouseleave="onCardLeave()">
        <span>🗑️</span> New Train
      </button>
    </div>
  </main>

  <!-- FLOATING TICKET MODAL -->
  <div class="modal-backdrop" id="modalBackdrop" onclick="closeModal(event)">
    <div class="ticket-modal" id="ticketModal" onclick="event.stopPropagation()">
      <div class="modal-header">
        <div class="modal-title-group">
          <span class="modal-platform-tag" id="modalTag">PLATFORM 01</span>
          <h2 class="modal-heading" id="modalTitle">Front Seat</h2>
        </div>
        <button class="modal-close-btn" onclick="closeModalDirect()">✕</button>
      </div>
      <div class="modal-body" id="modalBody">
        <!-- Injected dynamically -->
      </div>
    </div>
  </div>

  <!-- TOAST ERROR CAPSULE -->
  <div class="toast-capsule" id="toastCapsule">
    <span id="toastIcon">⚠️</span>
    <span id="toastText">oops wrong platform</span>
  </div>

  <!-- OPENING SEQUENCE OVERLAYS -->
  <div class="blackout-screen" id="blackoutScreen"></div>
  <div class="portal-flash" id="portalFlash"></div>

  <!-- CUSTOM CURSOR -->
  <div class="custom-cursor" id="customCursor">
    <div class="cursor-dot" id="cursorDot"></div>
    <div class="cursor-ring" id="cursorRing">
      <svg class="cursor-svg" viewBox="0 0 44 44">
        <circle class="ring-bg" cx="22" cy="22" r="18"></circle>
        <circle class="ring-progress" id="ringProgress" cx="22" cy="22" r="18"></circle>
      </svg>
      <span class="cursor-percent" id="cursorPercent">0%</span>
      <span class="cursor-label" id="cursorLabel">board</span>
    </div>
    <div class="cursor-mini-train" id="cursorMiniTrain">
      <svg viewBox="0 0 28 18" width="28" height="18">
        <rect x="2" y="4" width="18" height="11" rx="2" fill="#e2e8f0" stroke="#94a3b8" stroke-width="1"/>
        <path d="M20 7 L25 10 L25 15 L20 15 Z" fill="#cbd5e1"/>
        <rect x="4" y="6" width="6" height="4" rx="1" fill="#0284c7"/>
        <circle cx="7" cy="15" r="2" fill="#00f2fe"/>
        <circle cx="15" cy="15" r="2" fill="#00f2fe"/>
        <circle cx="22" cy="15" r="1.5" fill="#f59e0b"/>
      </svg>
      <div class="mini-steam-trail"></div>
    </div>
    <div class="cursor-init-text" id="cursorInitText">initializing orbit...</div>
  </div>

<script>
  // STATE MANAGEMENT
  let currentNodes = [];
  let isAnimating = false;
  let activePlatformId = 1;
  let deleteMode = 'first'; // 'first', 'last', 'position'
  const stepDelay = 750; // ms per step

  // CANVAS STARFIELD & WARP LOGIC
  const canvas = document.getElementById('starCanvas');
  const ctx = canvas.getContext('2d');
  let stars = [];
  let warpActive = false;
  let warpMultiplier = 0.05;

  function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  }
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  // Initialize 150 stars
  for (let i = 0; i < 150; i++) {
    stars.push({
      x: Math.random() * window.innerWidth,
      y: Math.random() * (window.innerHeight * 0.75),
      r: Math.random() * 1.5 + 0.5,
      alpha: Math.random(),
      speed: Math.random() * 0.02 + 0.01
    });
  }

  function renderStars() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const cx = canvas.width / 2;
    const cy = canvas.height / 2;

    for (let i = 0; i < stars.length; i++) {
      const s = stars[i];
      s.alpha += s.speed;
      const a = 0.3 + 0.7 * Math.abs(Math.sin(s.alpha));

      if (warpActive) {
        ctx.strokeStyle = "rgba(186, 230, 253, " + a + ")";
        ctx.lineWidth = s.r * 1.2;
        ctx.beginPath();
        ctx.moveTo(s.x, s.y);
        ctx.lineTo(s.x + (s.x - cx) * warpMultiplier, s.y + (s.y - cy) * warpMultiplier);
        ctx.stroke();
      } else {
        ctx.fillStyle = "rgba(255, 255, 255, " + a + ")";
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    requestAnimationFrame(renderStars);
  }
  requestAnimationFrame(renderStars);

  // CUSTOM CURSOR LERP LOGIC
  const cursor = document.getElementById('customCursor');
  const cursorRing = document.getElementById('cursorRing');
  const cursorLabel = document.getElementById('cursorLabel');
  let mouseX = window.innerWidth / 2;
  let mouseY = window.innerHeight / 2;
  let ringX = window.innerWidth / 2;
  let ringY = window.innerHeight / 2;
  let cursorHasMoved = false;

  window.addEventListener('mousemove', (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
    cursorHasMoved = true;
  });

  function updateCursor() {
    ringX += (mouseX - ringX) * 0.18;
    ringY += (mouseY - ringY) * 0.18;
    cursor.style.transform = "translate(" + ringX + "px, " + ringY + "px)";
    requestAnimationFrame(updateCursor);
  }
  requestAnimationFrame(updateCursor);

  function onCardHover(label) {
    cursor.classList.add('hovering');
    cursorLabel.innerText = label;
    document.getElementById('platformArc').classList.add('has-hover');
  }

  function onBtnHover(label) {
    cursor.classList.add('hovering');
    cursorLabel.innerText = label;
  }

  function onCardLeave() {
    cursor.classList.remove('hovering');
    document.getElementById('platformArc').classList.remove('has-hover');
  }

  // OPENING SEQUENCE
  let loadProgress = 0;
  let backendLoaded = false;
  const ringProgress = document.getElementById('ringProgress');
  const cursorPercent = document.getElementById('cursorPercent');

  function tickLoader() {
    if (loadProgress < 100) {
      loadProgress += (backendLoaded ? 2.5 : 1.2);
      if (loadProgress > 100) loadProgress = 100;

      const offset = 113.1 * (1 - loadProgress / 100);
      ringProgress.style.strokeDashoffset = offset;
      cursorPercent.innerText = Math.floor(loadProgress) + "%";

      requestAnimationFrame(tickLoader);
    } else {
      triggerPortalWarp();
    }
  }

  // SESSION ID (PER-VISITOR ORBITAL LIST)
  let sessionId = sessionStorage.getItem('orbital_train_session');
  if (!sessionId) {
    sessionId = 'sess_' + Math.random().toString(36).substring(2) + '_' + Date.now();
    sessionStorage.setItem('orbital_train_session', sessionId);
  }

  // Initial backend fetch
  async function initApp() {
    try {
      const res = await fetch('/api/list', {
        headers: { 'X-Session-Id': sessionId }
      });
      const data = await res.json();
      currentNodes = data.nodes || [];
      renderTrain(currentNodes);
      backendLoaded = true;
    } catch (e) {
      backendLoaded = true;
      showToast("connection issue, using local orbit");
    }
  }

  window.addEventListener('DOMContentLoaded', () => {
    initApp();
    requestAnimationFrame(tickLoader);
  });

  // STEP 2: TRANSITION AT 100%
  function triggerPortalWarp() {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Portal burst
    cursorRing.classList.add('portal-burst');
    document.getElementById('cursorInitText').style.display = 'none';

    if (!prefersReducedMotion) {
      warpActive = true;
      document.getElementById('portalFlash').style.opacity = '1';
      setTimeout(() => {
        document.getElementById('portalFlash').style.opacity = '0';
      }, 350);

      setTimeout(() => {
        warpActive = false;
      }, 650);
    }

    // Blackout fade
    setTimeout(() => {
      document.getElementById('blackoutScreen').style.opacity = '0';
    }, 250);

    // Slide up Earth & Platform
    document.getElementById('earthHorizon').style.transform = 'translateY(0)';
    document.getElementById('stationPlatform').style.transform = 'translateY(0)';
    document.getElementById('trackBed').style.transform = 'translateY(0)';

    // Train glides in
    const trainAssembly = document.getElementById('trainAssembly');
    trainAssembly.style.transform = 'translateX(60vw)';
    setTimeout(() => {
      trainAssembly.style.transition = 'transform 1.1s cubic-bezier(0.2, 0.8, 0.2, 1)';
      trainAssembly.style.transform = 'translateX(0)';
    }, 300);

    // Cards bounce in along arc
    const cards = document.querySelectorAll('.platform-card');
    cards.forEach((card, idx) => {
      card.style.opacity = '0';
      card.style.transform = card.style.transform + ' scale(0.6)';
      setTimeout(() => {
        card.style.transition = 'transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.5s ease';
        card.style.opacity = '1';
        card.style.transform = ''; // Reset to class transform
      }, 450 + idx * 90);
    });

    // Reset Cursor to normal mode
    setTimeout(() => {
      cursorRing.classList.remove('portal-burst');
      cursorPercent.style.display = 'none';
      ringProgress.style.display = 'none';
      cursorRing.style.width = '36px';
      cursorRing.style.height = '36px';
      cursorRing.style.border = '1.5px solid rgba(0, 242, 254, 0.6)';
      cursorRing.style.borderRadius = '50%';
      document.getElementById('speechText').innerText = 'platform 1 track ready &bull; all aboard';
    }, 1100);
  }

  // RENDER TRAIN CONSIST
  function renderTrain(nodes) {
    const assembly = document.getElementById('trainAssembly');
    assembly.innerHTML = '';
    const count = nodes.length;

    document.getElementById('chipCoaches').innerText = "coaches: " + count;

    if (count === 0) {
      document.getElementById('speechText').innerText = "no coaches yet bestie, add one below ↓";
    }

    // 1. ENGINE HEAD
    const engine = document.createElement('div');
    engine.className = 'silver-engine';
    engine.id = 'trainEngine';
    engine.innerHTML = `
      <div class="pointer-badge head">HEAD</div>
      <div class="engine-destination-board">HEAD</div>
      <div class="engine-windshield"></div>
      <div class="corrugated-panel"></div>
      <div class="engine-headlights">
        <div class="headlight-lamp"></div>
        <div class="headlight-lamp"></div>
      </div>
      <div class="engine-wheels">
        <div class="silver-wheel"></div>
        <div class="silver-wheel"></div>
        <div class="silver-wheel"></div>
      </div>
    `;
    assembly.appendChild(engine);

    // 2. HEAD COUPLER
    const headCoupler = document.createElement('div');
    headCoupler.className = 'silver-coupler';
    headCoupler.id = 'coupler-head';
    headCoupler.innerHTML = `
      <div class="coupler-bar"><div class="coupler-arrow-neon"></div></div>
    `;
    assembly.appendChild(headCoupler);

    // 3. COACHES (NODES)
    for (let i = 0; i < nodes.length; i++) {
      const val = nodes[i];

      const coach = document.createElement('div');
      coach.className = 'silver-coach';
      coach.id = 'coach-' + i;

      coach.innerHTML = `
        <div class="coach-body-silver">
          <div class="coach-destination-board">${escapeHtml(val)}</div>
          <div class="coach-windows-row">
            <div class="coach-window-glass"></div>
            <div class="coach-window-glass"></div>
            <div class="coach-window-glass"></div>
          </div>
          <div class="coach-door-seam"></div>
          <div class="corrugated-panel"></div>
        </div>
        <div class="coach-index-badge">${i}</div>
      `;
      assembly.appendChild(coach);

      // Coupler to next or buffer
      const coupler = document.createElement('div');
      coupler.className = 'silver-coupler';
      coupler.id = 'coupler-' + i;
      coupler.innerHTML = `
        <div class="coupler-bar"><div class="coupler-arrow-neon"></div></div>
      `;
      assembly.appendChild(coupler);
    }

    // 4. BUFFER STOP (NULL)
    const bufferStop = document.createElement('div');
    bufferStop.className = 'buffer-stop-unit';
    bufferStop.id = 'bufferUnit';
    bufferStop.innerHTML = `
      <div class="silver-buffer-stop" id="bufferBox">
        <div class="buffer-stop-light"></div>
        <div class="buffer-stop-plate">NULL</div>
      </div>
    `;
    assembly.appendChild(bufferStop);
  }

  function escapeHtml(text) {
    if (!text) return "";
    return text.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function showToast(msg) {
    const toast = document.getElementById('toastCapsule');
    document.getElementById('toastText').innerText = msg;
    toast.style.display = 'flex';
    setTimeout(() => { toast.style.display = 'none'; }, 3200);
  }

  // MODAL PLATFORM OPEN / CLOSE
  const PLATFORMS_DATA = {
    1: { tag: "PLATFORM 01", title: "Front Seat", real: "addFirst()" },
    2: { tag: "PLATFORM 02", title: "Tail End", real: "addLast()" },
    3: { tag: "PLATFORM 03", title: "Pick a Spot", real: "addAt(pos)" },
    4: { tag: "PLATFORM 04", title: "Drop Off", real: "delete()" },
    5: { tag: "PLATFORM 05", title: "Spot It", real: "search()" },
    6: { tag: "PLATFORM 06", title: "Joyride", real: "traverse()" }
  };

  function openPlatform(id) {
    if (isAnimating) return;
    activePlatformId = id;
    const p = PLATFORMS_DATA[id];
    document.getElementById('modalTag').innerText = p.tag + " // " + p.real;
    document.getElementById('modalTitle').innerText = p.title;

    const body = document.getElementById('modalBody');
    body.innerHTML = '';

    if (id === 1) {
      body.innerHTML = `
        <label class="input-label-stylish">
          Coach Data / Cargo:
          <input type="text" id="mInputData" class="input-stylish" value="X" maxlength="10" placeholder="e.g. X" autofocus>
          <div class="preset-chips-row">
            <span class="preset-chip" onclick="setModalData('X')">X</span>
            <span class="preset-chip" onclick="setModalData('VIP')">VIP</span>
            <span class="preset-chip" onclick="setModalData('777')">777</span>
            <span class="preset-chip" onclick="setModalData('NEO')">NEO</span>
          </div>
        </label>
        <button class="modal-action-btn" onclick="submitPlatform()">
          <span>Couple at Head</span> <span>➔</span>
        </button>
      `;
    } else if (id === 2) {
      body.innerHTML = `
        <label class="input-label-stylish">
          Coach Data / Cargo:
          <input type="text" id="mInputData" class="input-stylish" value="Z" maxlength="10" placeholder="e.g. Z" autofocus>
          <div class="preset-chips-row">
            <span class="preset-chip" onclick="setModalData('Z')">Z</span>
            <span class="preset-chip" onclick="setModalData('CABOOSE')">CABOOSE</span>
            <span class="preset-chip" onclick="setModalData('999')">999</span>
          </div>
        </label>
        <button class="modal-action-btn" onclick="submitPlatform()">
          <span>Append to Tail</span> <span>➔</span>
        </button>
      `;
    } else if (id === 3) {
      body.innerHTML = `
        <label class="input-label-stylish">
          Coach Data / Cargo:
          <input type="text" id="mInputData" class="input-stylish" value="Y" maxlength="10" placeholder="e.g. Y" autofocus>
        </label>
        <label class="input-label-stylish">
          Position Index:
          <input type="number" id="mInputIndex" class="input-stylish" value="2" min="0" max="50">
        </label>
        <button class="modal-action-btn" onclick="submitPlatform()">
          <span>Insert into Orbit</span> <span>➔</span>
        </button>
      `;
    } else if (id === 4) {
      body.innerHTML = `
        <div class="delete-mode-selector">
          <div class="delete-mode-tab active" id="tabDelFirst" onclick="setDeleteMode('first')">First [Head]</div>
          <div class="delete-mode-tab" id="tabDelLast" onclick="setDeleteMode('last')">Last [Tail]</div>
          <div class="delete-mode-tab" id="tabDelPos" onclick="setDeleteMode('position')">At Position</div>
        </div>
        <div id="delPosInputWrap" style="display:none;">
          <label class="input-label-stylish">
            Index to Delete:
            <input type="number" id="mInputIndex" class="input-stylish" value="0" min="0" max="50">
          </label>
        </div>
        <button class="modal-action-btn delete" onclick="submitPlatform()">
          <span>Drop Off Coach</span> <span>✂</span>
        </button>
      `;
    } else if (id === 5) {
      body.innerHTML = `
        <label class="input-label-stylish">
          Search Query:
          <input type="text" id="mInputData" class="input-stylish" value="Y" maxlength="10" placeholder="e.g. Y" autofocus>
        </label>
        <button class="modal-action-btn" onclick="submitPlatform()">
          <span>Scan Orbit</span> <span>🔍</span>
        </button>
      `;
    } else if (id === 6) {
      body.innerHTML = `
        <p style="font-size:13px; color:var(--silver-300); line-height:1.5;">
          The train drives across the platform in perspective, popping each coach at the center sensor until reaching NULL.
        </p>
        <button class="modal-action-btn" onclick="submitPlatform()">
          <span>Start Joyride</span> <span>⚡</span>
        </button>
      `;
    }

    document.getElementById('modalBackdrop').style.display = 'flex';
  }

  function setModalData(val) {
    const el = document.getElementById('mInputData');
    if (el) el.value = val;
  }

  function setDeleteMode(mode) {
    deleteMode = mode;
    document.getElementById('tabDelFirst').classList.toggle('active', mode === 'first');
    document.getElementById('tabDelLast').classList.toggle('active', mode === 'last');
    document.getElementById('tabDelPos').classList.toggle('active', mode === 'position');
    const wrap = document.getElementById('delPosInputWrap');
    if (wrap) wrap.style.display = (mode === 'position') ? 'block' : 'none';
  }

  function closeModal(e) {
    if (e.target.id === 'modalBackdrop') {
      document.getElementById('modalBackdrop').style.display = 'none';
    }
  }

  function closeModalDirect() {
    document.getElementById('modalBackdrop').style.display = 'none';
  }

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeModalDirect();
    if (e.key === 'Enter') {
      const modal = document.getElementById('modalBackdrop');
      if (modal.style.display === 'flex') submitPlatform();
    }
  });

  // SUBMIT PLATFORM ACTION
  async function submitPlatform() {
    const id = activePlatformId;
    closeModalDirect();

    if (id === 1) {
      const data = (document.getElementById('mInputData') ? document.getElementById('mInputData').value.trim() : 'X') || 'X';
      executeAddFirst(data);
    } else if (id === 2) {
      const data = (document.getElementById('mInputData') ? document.getElementById('mInputData').value.trim() : 'Z') || 'Z';
      executeAddLast(data);
    } else if (id === 3) {
      const data = (document.getElementById('mInputData') ? document.getElementById('mInputData').value.trim() : 'Y') || 'Y';
      const idx = parseInt(document.getElementById('mInputIndex').value, 10);
      executeAddAt(data, isNaN(idx) ? 0 : idx);
    } else if (id === 4) {
      if (deleteMode === 'first') executeDeleteFirst();
      else if (deleteMode === 'last') executeDeleteLast();
      else {
        const idx = parseInt(document.getElementById('mInputIndex').value, 10);
        executeDeleteAt(isNaN(idx) ? 0 : idx);
      }
    } else if (id === 5) {
      const query = (document.getElementById('mInputData') ? document.getElementById('mInputData').value.trim() : 'Y') || 'Y';
      executeSearch(query);
    } else if (id === 6) {
      executeJoyride();
    }
  }

  // API POST HELPER
  async function apiCall(endpoint, body = {}) {
    setSpinner(true);
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Session-Id': sessionId
        },
        body: JSON.stringify(body)
      });
      setSpinner(false);
      return await res.json();
    } catch (e) {
      setSpinner(false);
      showToast("server unreachable");
      return null;
    }
  }

  function setSpinner(show) {
    document.getElementById('trackSpinner').style.display = show ? 'block' : 'none';
  }

  function setControlsDisabled(disabled) {
    isAnimating = disabled;
    document.getElementById('btnSample').disabled = disabled;
    document.getElementById('btnClear').disabled = disabled;
    const cards = document.querySelectorAll('.platform-card');
    cards.forEach(c => c.classList.toggle('dimmed', disabled));

    if (disabled) {
      cursor.classList.add('train-active');
    } else {
      cursor.classList.remove('train-active');
    }
  }

  // 1. ADD FIRST
  async function executeAddFirst(val) {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "allocating coach '" + val + "' at head...";
    const data = await apiCall('/api/addFirst', { data: val });
    if (!data) { setControlsDisabled(false); return; }
    playStepAnimations(data.steps, data.nodes, "coupled '" + val + "' to head");
  }

  // 2. ADD LAST
  async function executeAddLast(val) {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "allocating coach '" + val + "' at tail...";
    const data = await apiCall('/api/addLast', { data: val });
    if (!data) { setControlsDisabled(false); return; }
    playStepAnimations(data.steps, data.nodes, "appended '" + val + "' to tail");
  }

  // 3. ADD AT
  async function executeAddAt(val, idx) {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "inserting coach '" + val + "' at index " + idx + "...";
    const data = await apiCall('/api/addAt', { data: val, index: idx });
    if (!data) { setControlsDisabled(false); return; }
    playStepAnimations(data.steps, data.nodes, data.message);
  }

  // 4. DELETE FIRST
  async function executeDeleteFirst() {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "uncoupling head coach...";
    const data = await apiCall('/api/deleteFirst');
    if (!data) { setControlsDisabled(false); return; }
    if (!data.success) {
      showToast(data.message);
      document.getElementById('speechText').innerText = data.message;
      setControlsDisabled(false);
      return;
    }
    playStepAnimations(data.steps, data.nodes, "head coach uncoupled");
  }

  // 5. DELETE LAST
  async function executeDeleteLast() {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "uncoupling caboose coach...";
    const data = await apiCall('/api/deleteLast');
    if (!data) { setControlsDisabled(false); return; }
    if (!data.success) {
      showToast(data.message);
      document.getElementById('speechText').innerText = data.message;
      setControlsDisabled(false);
      return;
    }
    playStepAnimations(data.steps, data.nodes, "last coach uncoupled");
  }

  // 6. DELETE AT
  async function executeDeleteAt(idx) {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "locating coach at index " + idx + "...";
    const data = await apiCall('/api/deleteAt', { index: idx });
    if (!data) { setControlsDisabled(false); return; }
    if (!data.success) {
      showToast(data.message);
      document.getElementById('speechText').innerText = data.message;
      setControlsDisabled(false);
      return;
    }
    playStepAnimations(data.steps, data.nodes, data.message);
  }

  // 7. SAMPLE TRAIN
  async function loadSampleTrain() {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "loading sample consist A, B, C, D...";
    const data = await apiCall('/api/loadSample');
    if (!data) { setControlsDisabled(false); return; }
    currentNodes = data.nodes;
    renderTrain(currentNodes);
    document.getElementById('speechText').innerText = "sample train online bestie &bull; A -> B -> C -> D -> NULL";
    setControlsDisabled(false);
  }

  // 8. CLEAR TRAIN
  async function clearTrain() {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "clearing platform tracks...";
    const data = await apiCall('/api/clear');
    if (!data) { setControlsDisabled(false); return; }
    currentNodes = [];
    renderTrain(currentNodes);
    document.getElementById('speechText').innerText = "platform is empty, no train yet. add one below ↓";
    setControlsDisabled(false);
  }

  // ANIMATION STEPS RUNNER
  function playStepAnimations(steps, finalNodes, completeMessage) {
    if (!steps || steps.length === 0) {
      currentNodes = finalNodes;
      renderTrain(currentNodes);
      setControlsDisabled(false);
      return;
    }

    let i = 0;
    function runNext() {
      if (i >= steps.length) {
        currentNodes = finalNodes;
        renderTrain(currentNodes);
        document.getElementById('speechText').innerText = completeMessage;
        setControlsDisabled(false);
        return;
      }

      const s = steps[i];
      i++;
      document.getElementById('speechText').innerText = s.text;
      applyStepEffect(s);

      setTimeout(runNext, stepDelay);
    }
    runNext();
  }

  function applyStepEffect(step) {
    // Clear previous pointers
    const existing = document.querySelectorAll('.pointer-badge.dynamic');
    existing.forEach(e => e.remove());

    const idx = step.nodeIndex;

    if (step.type === 'CREATE_NODE') {
      createSparklesAtTrack();
    } else if (step.type === 'MOVE_CURRENT') {
      const coach = document.getElementById('coach-' + idx);
      if (coach) {
        attachBadge(coach, 'CURRENT', 'current');
        coach.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
      }
    } else if (step.type === 'SET_NEXT') {
      const coupler = document.getElementById('coupler-' + idx);
      if (coupler) {
        coupler.classList.remove('unlinked');
        const bar = coupler.querySelector('.coupler-bar');
        if (bar) {
          bar.style.boxShadow = '0 0 16px var(--neon-cyan)';
          setTimeout(() => { if (bar) bar.style.boxShadow = ''; }, stepDelay * 0.7);
        }
      }
    } else if (step.type === 'MOVE_HEAD') {
      const coach0 = document.getElementById('coach-0');
      if (coach0) {
        attachBadge(coach0, 'HEAD', 'head');
      }
    } else if (step.type === 'UNLINK') {
      const coupler = document.getElementById('coupler-' + idx);
      if (coupler) coupler.classList.add('unlinked');
    } else if (step.type === 'REMOVE_NODE') {
      const coach = document.getElementById('coach-' + idx);
      if (coach) coach.classList.add('removing');
    }
  }

  function attachBadge(parent, label, typeClass) {
    const b = document.createElement('div');
    b.className = 'pointer-badge dynamic ' + typeClass;
    b.innerText = label;
    parent.appendChild(b);
  }

  function createSparklesAtTrack() {
    const wrap = document.getElementById('trainStageContainer');
    const rect = wrap.getBoundingClientRect();
    for (let i = 0; i < 8; i++) {
      const sp = document.createElement('div');
      sp.className = 'sparkle-particle';
      sp.style.left = (rect.width / 2 + (Math.random() * 80 - 40)) + 'px';
      sp.style.top = (rect.height / 2 + (Math.random() * 40 - 20)) + 'px';
      sp.style.setProperty('--tx', (Math.random() * 60 - 30) + 'px');
      sp.style.setProperty('--ty', (Math.random() * -60 - 20) + 'px');
      wrap.appendChild(sp);
      setTimeout(() => sp.remove(), 600);
    }
  }

  // SEARCH WITH POP EFFECT
  async function executeSearch(query) {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "scanning coaches for cargo '" + query + "'...";
    const data = await apiCall('/api/search', { data: query });
    if (!data) { setControlsDisabled(false); return; }

    const steps = data.steps || [];
    let i = 0;

    function runSearchStep() {
      if (i >= steps.length) {
        document.getElementById('speechText').innerText = data.message;
        setControlsDisabled(false);
        return;
      }

      const s = steps[i];
      i++;
      document.getElementById('speechText').innerText = s.text;

      // Pop visited coach
      if (s.nodeIndex >= 0) {
        const coach = document.getElementById('coach-' + s.nodeIndex);
        if (coach) {
          coach.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
          if (s.type === 'FOUND') {
            coach.classList.add('found-glow');
            attachBadge(coach, 'FOUND!', 'found');
          } else {
            coach.classList.add('pop');
            setTimeout(() => coach.classList.remove('pop'), stepDelay * 0.7);
          }
        }
      } else if (s.type === 'NOT_FOUND') {
        const buf = document.getElementById('bufferBox');
        if (buf) {
          buf.classList.add('shake');
          setTimeout(() => buf.classList.remove('shake'), 800);
        }
      }

      setTimeout(runSearchStep, stepDelay);
    }
    runSearchStep();
  }

  // JOYRIDE (TRAVERSE) CINEMATIC ANIMATION
  async function executeJoyride() {
    setControlsDisabled(true);
    document.getElementById('speechText').innerText = "launching joyride sequence...";
    const data = await apiCall('/api/traverse');
    if (!data) { setControlsDisabled(false); return; }

    const nodes = data.nodes || [];
    if (nodes.length === 0) {
      document.getElementById('speechText').innerText = "no coaches to joyride, platform empty bestie";
      showToast("train has 0 coaches");
      setControlsDisabled(false);
      return;
    }

    // Dim platform cards & activate center signal beacon
    const signalMarker = document.getElementById('signalCenterMarker');
    signalMarker.classList.add('active');

    const trainAssembly = document.getElementById('trainAssembly');
    const container = document.getElementById('trainStageContainer');
    const containerCenter = container.offsetWidth / 2;

    // Slide in from right
    trainAssembly.style.transition = 'transform 0.8s cubic-bezier(0.2, 0.8, 0.2, 1)';
    trainAssembly.style.transform = 'translateX(55vw)';
    document.getElementById('speechText').innerText = "express metro inbound from orbit... ⚡";

    await sleep(850);

    // Each coach reaches center and POPS
    for (let i = 0; i < nodes.length; i++) {
      const coach = document.getElementById('coach-' + i);
      if (!coach) continue;

      // Calculate translation so coach i aligns with containerCenter
      const coachCenterInAssembly = coach.offsetLeft + coach.offsetWidth / 2;
      const targetTranslateX = containerCenter - coachCenterInAssembly;

      trainAssembly.style.transition = 'transform 0.65s cubic-bezier(0.2, 0.8, 0.2, 1)';
      trainAssembly.style.transform = "translateX(" + targetTranslateX + "px)";

      await sleep(650);

      // Pop the coach!
      coach.classList.add('pop');
      attachBadge(coach, 'CURRENT • Coach #' + i, 'current');
      document.getElementById('speechText').innerText = "visiting coach #" + i + " ['" + nodes[i] + "']";

      await sleep(700);

      coach.classList.remove('pop');
      const b = coach.querySelector('.pointer-badge.dynamic');
      if (b) b.remove();
    }

    // Move past the buffer stop and exit off the left edge
    const buffer = document.getElementById('bufferUnit');
    if (buffer) {
      const bufferCenterInAssembly = buffer.offsetLeft + buffer.offsetWidth / 2;
      const bufferTranslateX = containerCenter - bufferCenterInAssembly;
      trainAssembly.style.transition = 'transform 0.6s cubic-bezier(0.2, 0.8, 0.2, 1)';
      trainAssembly.style.transform = "translateX(" + bufferTranslateX + "px)";
      document.getElementById('speechText').innerText = "reached NULL buffer stop (end of track)";
      await sleep(650);
    }

    // Exit off left edge completely
    trainAssembly.style.transition = 'transform 0.8s ease-in';
    trainAssembly.style.transform = 'translateX(-100vw)';
    await sleep(800);

    // Announce result in bubble
    const pathStr = nodes.join(" -> ") + " -> NULL";
    document.getElementById('speechText').innerText = "joyride complete: " + pathStr + " 🚀";

    // Return train to resting position from right
    trainAssembly.style.transition = 'none';
    trainAssembly.style.transform = 'translateX(60vw)';
    await sleep(50);
    trainAssembly.style.transition = 'transform 0.9s cubic-bezier(0.2, 0.8, 0.2, 1)';
    trainAssembly.style.transform = 'translateX(0)';

    await sleep(950);

    signalMarker.classList.remove('active');
    setControlsDisabled(false);
  }

  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
</script>
</body>
</html>
""";

    private static byte[] getHtmlBytes() {
        byte[] b1 = HTML_PAGE_PART1.getBytes(StandardCharsets.UTF_8);
        byte[] b2 = HTML_PAGE_PART2.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, bytes, 0, b1.length);
        System.arraycopy(b2, 0, bytes, b1.length, b2.length);
        return bytes;
    }
}
