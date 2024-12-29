package rml.deserializer.struct.std;

public class StructFloat extends StructNumber{
    float float_;
    public StructFloat(float float_){
        this.float_ = float_;
    }

    @Override
    public boolean isFloat() {
        return true;
    }

    @Override
    public float getAsFloat() {
        return float_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructFloat that)) return false;

        return Float.compare(float_, that.float_) == 0;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(float_);
    }

    @Override
    public String toString() {
        return Float.toString(float_);
    }

    @Override
    public double castToDouble() {
        return float_;
    }

    @Override
    public long castToLong() {
        return (long) float_;
    }

    @Override
    public float castToFloat() {
        return float_;
    }

    @Override
    public Character castToNumber() {
        return float_;
    }
}
