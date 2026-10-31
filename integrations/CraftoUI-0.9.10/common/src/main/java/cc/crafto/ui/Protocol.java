package cc.crafto.ui;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Shared, byte-for-byte protocol for Fabric custom payloads and Bukkit plugin messaging. */
public final class Protocol {
    private Protocol() {}
    public static final String CHANNEL = "craftoui:wire";
    public static final int VERSION = 1, MAX_BYTES = 28 * 1024, HEADER = 6;
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public enum Kind { HELLO, WELCOME, REQUEST, OPEN, ACTION, RESULT, PATCH, CLOSE, NATIVE_MENU, CONTRACTS, BACK, DIRECTORY, LAUNCH, DASHBOARD, COMMANDS, CIVIC, BUILD_PREVIEW, PLACEMENT }
    public record Message(Kind kind, JsonObject body) {}
    public static byte[] encode(Kind kind, JsonObject body) {
        validateTree(body, 0);
        byte[] json = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_BYTES - HEADER) throw new IllegalArgumentException("Payload exceeds limit");
        return ByteBuffer.allocate(HEADER + json.length).put((byte)VERSION).put((byte)kind.ordinal()).putInt(json.length).put(json).array();
    }
    public static Message decode(byte[] bytes) {
        if (bytes.length < HEADER || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid payload size");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        if (b.get() != VERSION) throw new IllegalArgumentException("Unsupported protocol");
        int kind = Byte.toUnsignedInt(b.get()), size = b.getInt();
        if (kind >= Kind.values().length || size != b.remaining()) throw new IllegalArgumentException("Invalid frame");
        try {
            String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(b).toString();
            // Reject deep input before the JSON parser can allocate a recursive tree.
            int depth = 0; boolean quoted = false, escape = false;
            for (char c : raw.toCharArray()) {
                if (escape) { escape = false; continue; }
                if (quoted && c == '\\') { escape = true; continue; }
                if (c == '"') quoted = !quoted;
                if (!quoted && (c == '[' || c == '{') && ++depth > 24) throw new IllegalArgumentException("JSON too deep");
                if (!quoted && (c == ']' || c == '}')) depth--;
            }
            JsonReader reader = new JsonReader(new StringReader(raw));
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT || !element.isJsonObject()) throw new IllegalArgumentException("Expected one JSON object");
            validateTree(element, 0);
            return new Message(Kind.values()[kind], element.getAsJsonObject());
        } catch (Exception e) { throw new IllegalArgumentException("Malformed CraftoUI message", e); }
    }
    public static void validateTree(JsonElement node, int depth) {
        if (depth > 24) throw new IllegalArgumentException("JSON too deep");
        if (node.isJsonObject()) {
            if (node.getAsJsonObject().size() > 256) throw new IllegalArgumentException("Too many fields");
            node.getAsJsonObject().entrySet().forEach(e -> {
                if (e.getKey().length() > 128) throw new IllegalArgumentException("Key too long");
                validateTree(e.getValue(), depth+1);
            });
        } else if (node.isJsonArray()) {
            if (node.getAsJsonArray().size() > 256) throw new IllegalArgumentException("Array too long");
            node.getAsJsonArray().forEach(e -> validateTree(e, depth+1));
        } else if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString() && node.getAsString().length() > 4096) {
            throw new IllegalArgumentException("String too long");
        }
    }
    public static JsonObject object(Object... pairs) {
        JsonObject o = new JsonObject();
        for (int i=0; i<pairs.length; i+=2) o.add((String)pairs[i], JSON.toJsonTree(pairs[i+1]));
        return o;
    }
    public static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key); return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }
    public static int integer(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
    public static boolean bool(JsonObject o, String key, boolean fallback) { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
}
