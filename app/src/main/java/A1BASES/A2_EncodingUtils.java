package A1BASES;

import java.io.UnsupportedEncodingException;

public class A2_EncodingUtils {

    /**
     * Convierte texto a UTF-8 antes de guardar en BD
     */
    public static String toUTF8(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            byte[] bytes = text.getBytes("UTF-8");
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return text;
        }
    }

    /**
     * Recupera texto desde BD asegurando UTF-8
     */
    public static String fromDatabase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            // Intenta corregir si viene mal codificado
            byte[] bytes = text.getBytes("ISO-8859-1");
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return text;
        }
    }

    /**
     * Limpia y normaliza texto para español
     */
    public static String normalizeSpanishText(String text) {
        if (text == null) return null;

        try {
            // Corrige la doble codificación UTF-8
            byte[] bytes = text.getBytes("ISO-8859-1");
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return text;
        }
    }
}