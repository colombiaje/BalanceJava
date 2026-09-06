package A1BASES;

public class A10_3_ValidadorProrrateable {

    public static class ResultadoValidacion {
        public boolean esValido;
        public String mensaje;
        public String sugerencia;

        public ResultadoValidacion(boolean valido, String msg, String sug) {
            this.esValido = valido;
            this.mensaje = msg;
            this.sugerencia = sug;
        }
    }

    /**
     * Valida formato de descripción prorrateable en tiempo real
     */
    public static ResultadoValidacion validarFormato(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            return new ResultadoValidacion(true, "", "");
        }

        // Si no intenta usar el formato, está ok
        if (!descripcion.contains("[") && !descripcion.contains("(")) {
            return new ResultadoValidacion(true, "", "");
        }

        // Intentó usar el formato, verificar si es correcto
        String normalizada = descripcion
                .toLowerCase()
                .replaceAll("[íÍ]", "i")
                .replaceAll("[áÁ]", "a")
                .replaceAll("[éÉ]", "e")
                .replaceAll("[óÓ]", "o")
                .replaceAll("[úÚüÜ]", "u")
                .replaceAll("[ñÑ]", "n")
                .trim();

        if (normalizada.matches(".*[\\[\\(]\\d+\\s+dias\\s+.+[\\]\\)].*")) {
            return new ResultadoValidacion(true, "✅ Formato correcto", "");
        }

        // Errores comunes
        if (!normalizada.matches(".*[\\[\\(]\\d+.*")) {
            return new ResultadoValidacion(false,
                    "❌ Falta el número de días",
                    "Ej: [15 dias Concepto]");
        }

        if (!normalizada.contains("dias")) {
            return new ResultadoValidacion(false,
                    "❌ Falta la palabra 'dias'",
                    "Ej: [15 dias Concepto]");
        }

        if (!normalizada.matches(".*[\\]\\)].*")) {
            return new ResultadoValidacion(false,
                    "❌ Falta cerrar con ] o )",
                    "Ej: [15 dias Concepto]");
        }

        return new ResultadoValidacion(false,
                "❌ Formato incorrecto",
                "Formato: [Número dias Concepto]");
    }

    /**
     * Autocorregir errores comunes
     */
    public static String autocorregir(String descripcion) {
        if (descripcion == null) return "";

        return descripcion
                .replaceAll("dÃ­as", "dias")
                .replaceAll("dÃ­as", "dias")
                .replaceAll("\\s+", " ")
                .trim();
    }
}