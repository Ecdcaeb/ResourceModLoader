package rml.deserializer.struct.std;

import java.math.BigDecimal;
import java.util.Objects;

public class StructBigDecimal extends StructNumber{
    BigDecimal bigDecimal;
    public StructBigDecimal(BigDecimal bigDecimal){
        this.bigDecimal = bigDecimal;
    }

    @Override
    public boolean isBigDecimal() {
        return true;
    }

    @Override
    public BigDecimal getAsBigDecimal() {
        return bigDecimal;
    }

    @Override
    public String toString() {
        return bigDecimal.toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructBigDecimal that)) return false;
        return Objects.equals(bigDecimal, that.bigDecimal);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(bigDecimal);
    }

    @Override
    public double castToDouble() {
        return bigDecimal.doubleValue();
    }

    @Override
    public long castToLong() {
        return bigDecimal.longValue();
    }

    @Override
    public float castToFloat() {
        return bigDecimal.floatValue();
    }

    @Override
    public int castToInteger() {
        return bigDecimal.intValue();
    }

    @Override
    public byte castToByte() {
        return bigDecimal.byteValue();
    }

    @Override
    public short castToShort() {
        return bigDecimal.shortValue();
    }

    @Override
    public Number castToNumber() {
        return bigDecimal;
    }
}
