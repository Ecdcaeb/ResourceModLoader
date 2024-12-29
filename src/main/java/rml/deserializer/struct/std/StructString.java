package rml.deserializer.struct.std;

import java.util.Objects;

public class StructString extends StructElement{
    String string;
    public StructString(String s){
        this.string = s;
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public String getAsString() {
        return string;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StructString that)) return false;

        return Objects.equals(string, that.string);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(string);
    }
}
