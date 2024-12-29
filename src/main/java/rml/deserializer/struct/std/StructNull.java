package rml.deserializer.struct.std;

public class StructNull extends StructElement{
    public static final StructNull NULL = new StructNull();
    private StructNull(){}

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public Void getAsNull() {
        return null;
    }

    @Override
    public String toString() {
        return "null";
    }
}
