package rml.runtime;

/** A lifecycle failure with a stable machine-readable code and optional context. */
public final class ContentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String packId;
    private final String unitId;

    public ContentException(String code, String packId, String unitId, String message) {
        this(code, packId, unitId, message, null);
    }

    public ContentException(String code, String packId, String unitId, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.packId = packId;
        this.unitId = unitId;
    }

    public String getCode() {
        return code;
    }

    public String getPackId() {
        return packId;
    }

    public String getUnitId() {
        return unitId;
    }
}
