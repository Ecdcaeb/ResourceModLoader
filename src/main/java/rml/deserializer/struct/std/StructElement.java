package rml.deserializer.struct.std;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class StructElement {

    public boolean isObject(){
        return false;
    }

    public StructObject getAsObject(){
        throw new UnsupportedOperationException();
    }

    public boolean isArray(){
        return false;
    }

    public StructArray getAsArray(){
        throw new UnsupportedOperationException();
    }

    public boolean isInteger(){
        return false;
    }

    public int getAsInteger(){
        throw new UnsupportedOperationException();
    }

    public boolean isString(){
        return false;
    }

    public String getAsString(){
        throw new UnsupportedOperationException();
    }

    public boolean isFloat(){
        return false;
    }

    public float getAsFloat(){
        throw new UnsupportedOperationException();
    }

    public boolean isDouble(){
        return false;
    }

    public double getAsDouble(){
        throw new UnsupportedOperationException();
    }

    public boolean isLong(){
        return false;
    }

    public long getAsLong(){
        throw new UnsupportedOperationException();
    }

    public boolean isCharacter(){
        return false;
    }

    public char getAsCharacter(){
        throw new UnsupportedOperationException();
    }

    public boolean isByte(){
        return false;
    }

    public byte getAsByte(){
        throw new UnsupportedOperationException();
    }

    public boolean isBoolean(){
        return false;
    }

    public boolean getAsBoolean(){
        throw new UnsupportedOperationException();
    }

    public boolean isShort(){
        return false;
    }

    public short getAsShort(){
        throw new UnsupportedOperationException();
    }

    public boolean isBigInteger(){
        return false;
    }

    public BigInteger getAsBigInteger(){
        throw new UnsupportedOperationException();
    }

    public boolean isBigDecimal(){
        return false;
    }

    public BigDecimal getAsBigDecimal(){
        throw new UnsupportedOperationException();
    }

    public boolean isNumber(){
        return false;
    }

    public StructNumber getAsNumber(){
        throw new UnsupportedOperationException();
    }

    public boolean isPrimitive(){
        return false;
    }

    public String getAsPrimitiveString(){
        throw new UnsupportedOperationException();
    }

    public boolean isNull(){
        return false;
    }

    public Void getAsNull(){
        throw new UnsupportedOperationException();
    }
}
