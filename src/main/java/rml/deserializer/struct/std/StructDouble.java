package rml.deserializer.struct.std;

public class StructDouble extends StructNumber{
    double double_;
    public StructDouble(double double_){
        this.double_ = double_;
    }

    @Override
    public boolean isDouble() {
        return true;
    }

    @Override
    public double getAsDouble() {
        return double_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructDouble that)) return false;

        return Double.compare(double_, that.double_) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(double_);
    }

    @Override
    public String toString() {
        return Double.toString(double_);
    }

    @Override
    public double castToDouble() {
        return double_;
    }

    @Override
    public long castToLong() {
        return (long) double_;
    }

    @Override
    public Character castToNumber() {
        return double_;
    }
}
