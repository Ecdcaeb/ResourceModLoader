package rml.deserializer.struct.std;

public abstract class StructBoolean extends StructNumber{
    public static StructBoolean of(boolean b){
        return b ? TRUE : FALSE;
    }

    public static final StructBoolean TRUE = new StructBoolean(){
        @Override
        public boolean getAsBoolean() {
            return true;
        }

        @Override
        public String toString() {
            return "true";
        }

        @Override
        public boolean castToBoolean() {
            return true;
        }

        @Override
        public long castToLong() {
            return 1;
        }

        @Override
        public double castToDouble() {
            return 1;
        }

        @Override
        public Number castToNumber() {
            return 1;
        }
    };

    public static final StructBoolean FALSE = new StructBoolean(){
        @Override
        public boolean getAsBoolean() {
            return false;
        }

        @Override
        public String toString() {
            return "false";
        }

        @Override
        public boolean castToBoolean() {
            return false;
        }

        @Override
        public long castToLong() {
            return 0;
        }

        @Override
        public double castToDouble() {
            return 0;
        }

        @Override
        public Number castToNumber() {
            return 0;
        }
    };

    private StructBoolean(){
        super();
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
