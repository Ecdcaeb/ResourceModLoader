package rml.deserializer.struct.std;

public class StructByte extends StructNumber{
    byte byte_;
    public StructByte(byte byte_){
        this.byte_ = byte_;
    }

    @Override
    public boolean isByte() {
        return true;
    }

    @Override
    public byte getAsByte() {
        return byte_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructByte that)) return false;

        return byte_ == that.byte_;
    }

    @Override
    public int hashCode() {
        return byte_;
    }

    @Override
    public String toString() {
        return Byte.toString(byte_);
    }

    @Override
    public double castToDouble() {
        return byte_;
    }

    @Override
    public long castToLong() {
        return byte_;
    }

    @Override
    public byte castToByte() {
        return byte_;
    }

    @Override
    public Byte castToNumber() {
        return byte_;
    }
}
