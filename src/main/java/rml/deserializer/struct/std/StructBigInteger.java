package rml.deserializer.struct.std;

import java.math.BigInteger;
import java.util.Objects;

public class StructBigInteger extends StructNumber{
    BigInteger bigInteger;
    public StructBigInteger(BigInteger bigInteger){
        this.bigInteger = bigInteger;
    }

    @Override
    public boolean isBigInteger() {
        return true;
    }

    @Override
    public BigInteger getAsBigInteger() {
        return bigInteger;
    }


    @Override
    public String toString() {
        return bigInteger.toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructBigInteger that)) return false;

        return Objects.equals(bigInteger, that.bigInteger);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(bigInteger);
    }

    @Override
    public double castToDouble() {
        return bigInteger.doubleValue();
    }

    @Override
    public long castToLong() {
        return bigInteger.longValue();
    }

    @Override
    public float castToFloat() {
        return bigInteger.floatValue();
    }

    @Override
    public int castToInteger() {
        return bigInteger.intValue();
    }

    @Override
    public byte castToByte() {
        return bigInteger.byteValue();
    }

    @Override
    public short castToShort() {
        return bigInteger.shortValue();
    }

    @Override
    public Number castToNumber() {
        return bigInteger;
    }
}
