package rml.compiler;

/** A compiler diagnostic tied to a source file and, when known, a one-based line. */
public final class CompileException extends RuntimeException {
    private final String code;
    private final String source;
    private final int line;

    public CompileException(String code, String source, int line, String message) {
        this(code, source, line, message, null);
    }

    public CompileException(String code, String source, int line, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.source = source;
        this.line = line;
    }

    public String getCode() { return code; }
    public String getSource() { return source; }
    public int getLine() { return line; }

    @Override
    public String toString() {
        return source + (line > 0 ? ":" + line : "") + " [" + code + "] " + getMessage();
    }
}
