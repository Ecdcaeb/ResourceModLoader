package rml.deserializer.struct.std;

public abstract class StructNumber extends StructPrimitive{
    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    public StructNumber getAsNumber() {
        return this;
    }

    public int castToInteger(){
        return (int) castToLong();
    }
    public abstract double castToDouble();
    public abstract long castToLong();
    public float castToFloat(){
        return (float) castToDouble();
    }
    public short castToShort(){
        return (short) castToLong();
    }
    public byte castToByte(){
        return (byte) castToLong();
    }
    public char castToCharacter(){
        return (char) castToInteger();
    }
    public boolean castToBoolean(){
        return castToInteger() != 0;
    }
    public String castToString(){
        return toString();
    }
    public abstract Number castToNumber();
}
