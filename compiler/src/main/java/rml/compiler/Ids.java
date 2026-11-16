package rml.compiler;

/** Platform-independent namespaced identifiers used by the source language. */
public final class Ids {
    private Ids() { }

    public static String parse(String value) {
        if (value == null) throw new IllegalArgumentException("Identifier is null");
        int separator = value.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : value.substring(0, separator);
        String path = separator < 0 ? value : value.substring(separator + 1);
        if (namespace.isEmpty() || path.isEmpty() || path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        for (int i = 0; i < namespace.length(); i++) {
            if (!nameCharacter(namespace.charAt(i))) throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i);
            if (!nameCharacter(character) && character != '/') throw new IllegalArgumentException("Invalid identifier: " + value);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Invalid identifier path: " + value);
            }
        }
        return namespace + ":" + path;
    }

    public static String namespace(String id) { return id.substring(0, id.indexOf(':')); }
    public static String path(String id) { return id.substring(id.indexOf(':') + 1); }

    private static boolean nameCharacter(char character) {
        return character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                || character == '_' || character == '-' || character == '.';
    }
}
