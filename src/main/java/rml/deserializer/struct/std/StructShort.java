package rml.deserializer.struct.std;

public class StructShort extends StructNumber{
    short s;
    public StructShort(short s){
        this.s = s;
    }

    @Override
    public boolean isShort() {
        return true;
    }

    @Override
    public short getAsShort() {
        return s;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructShort that)) return false;

        return s == that.s;
    }

    @Override
    public int hashCode() {
        return s;
    }

    @Override
    public String toString() {
        return Short.toString(s);
    }

    @Override
    public double castToDouble() {
        return s;
    }

    @Override
    public long castToLong() {
        return s;
    }

    @Override
    public short castToShort() {
        return s;
    }

    @Override
    public Character castToNumber() {
        return s;
    }
}
