package rml.deserializer.struct.std;

public abstract class StructPrimitive extends StructElement{
    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public abstract String toString();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public String getAsPrimitiveString() {
        return toString();
    }
}
