package rml.deserializer.struct.std;

public class StructInteger extends StructNumber{
    int int_;
    public StructInteger(int int_){
        this.int_ = int_;
    }

    @Override
    public boolean isInteger() {
        return true;
    }

    @Override
    public int getAsInteger() {
        return int_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructInteger that)) return false;

        return int_ == that.int_;
    }

    @Override
    public int hashCode() {
        return int_;
    }

    @Override
    public String toString() {
        return Integer.toString(int_);
    }

    @Override
    public double castToDouble() {
        return int_;
    }

    @Override
    public long castToLong() {
        return int_;
    }

    @Override
    public int castToInteger() {
        return int_;
    }

    @Override
    public Character castToNumber() {
        return int_;
    }
}
