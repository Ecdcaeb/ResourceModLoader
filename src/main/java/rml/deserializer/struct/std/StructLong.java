package rml.deserializer.struct.std;

public class StructLong extends StructNumber{
    long long_;
    public StructLong(long long_){
        this.long_ = long_;
    }

    @Override
    public boolean isLong() {
        return true;
    }

    @Override
    public long getAsLong() {
        return long_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructLong that)) return false;

        return long_ == that.long_;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(long_);
    }

    @Override
    public String toString() {
        return Long.toString(long_);
    }

    @Override
    public double castToDouble() {
        return long_;
    }

    @Override
    public long castToLong() {
        return long_;
    }

    @Override
    public Character castToNumber() {
        return long_;
    }
}
