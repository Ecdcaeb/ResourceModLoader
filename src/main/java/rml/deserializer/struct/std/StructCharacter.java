package rml.deserializer.struct.std;

public class StructCharacter extends StructNumber{
    char char_;
    public StructCharacter(char char_){
        this.char_ = char_;
    }

    @Override
    public boolean isCharacter() {
        return true;
    }

    @Override
    public char getAsCharacter() {
        return char_;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructCharacter that)) return false;

        return char_ == that.char_;
    }

    @Override
    public int hashCode() {
        return char_;
    }

    @Override
    public String toString() {
        return Character.toString(char_);
    }

    @Override
    public double castToDouble() {
        return char_;
    }

    @Override
    public long castToLong() {
        return char_;
    }

    @Override
    public char castToCharacter() {
        return char_;
    }

    @Override
    public Character castToNumber() {
        return char_;
    }
}
