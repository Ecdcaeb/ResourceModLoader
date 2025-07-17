package rml.loader.api.utils;

public class PrimitiveHelper {
    public static boolean[] cast(Boolean[] booleans) {
        boolean[] booleans1 = new boolean[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            booleans1[i] = booleans[i];
        }
        return booleans1;
    }

    public static double[] cast(Double[] booleans) {
        double[] booleans1 = new double[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            booleans1[i] = booleans[i];
        }
        return booleans1;
    }

    public static int[] cast(Integer[] booleans) {
        int[] booleans1 = new int[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            booleans1[i] = booleans[i];
        }
        return booleans1;
    }
}
