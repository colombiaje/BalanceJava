package A1BASES;

import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A10_2_GastoProrrateableIndicadores {

    private int diasPeriodo;
    private String concepto;
    private int valorTotal;
    private String fecha;
    private String documento;
    private int diaRegistro;   // día de c7_FechaYHora (se conserva solo para mostrar en tabla)
    private int diaInicial;    // día de c8_FechaInicial ← BASE del prorrateo

    public A10_2_GastoProrrateableIndicadores(int diasPeriodo, String concepto, int valorTotal,
                                              String fecha, String documento,
                                              int diaRegistro, int diaInicial) {
        this.diasPeriodo  = diasPeriodo;
        this.concepto     = concepto;
        this.valorTotal   = valorTotal;
        this.fecha        = fecha;
        this.documento    = documento;
        this.diaRegistro  = diaRegistro;  // solo para mostrar en la columna "Día"
        this.diaInicial   = diaInicial;   // base real del cálculo
    }

    /**
     * Parsea la descripción buscando el patrón [XX dias Concepto]
     * Ahora recibe fechaInicial (c8_FechaInicial, formato YYYYMMDD como int)
     * además de fecha (c7_FechaYHora) que se conserva solo para display.
     */
    public static A10_2_GastoProrrateableIndicadores parseDescripcion(
            String descripcion, int valor,
            String fecha, String documento,
            int fechaInicialYYYYMMDD) {

        if (descripcion == null || descripcion.isEmpty()) return null;

        try {
            String descripcionNormalizada = descripcion
                    .toLowerCase()
                    .replaceAll("[íÍ]", "i")
                    .replaceAll("[áÁ]", "a")
                    .replaceAll("[éÉ]", "e")
                    .replaceAll("[óÓ]", "o")
                    .replaceAll("[úÚüÜ]", "u")
                    .replaceAll("[ñÑ]", "n")
                    .replaceAll("\\s+", " ")
                    .trim();

            Pattern pattern = Pattern.compile("[\\[\\(](\\d+)\\s+dias\\s+(.+?)[\\]\\)]");
            Matcher matcher = pattern.matcher(descripcionNormalizada);

            if (matcher.find()) {
                int diasPeriodo = Integer.parseInt(matcher.group(1));
                String concepto = matcher.group(2).trim();

                // diaRegistro: día de c7_FechaYHora (solo para mostrar)
                int diaRegistro = 1;
                if (fecha != null && fecha.length() >= 10) {
                    diaRegistro = Integer.parseInt(fecha.substring(8, 10));
                }

                // diaInicial: día de c8_FechaInicial (base del prorrateo)
                // formato YYYYMMDD → los últimos 2 dígitos son el día
                int diaInicial = fechaInicialYYYYMMDD % 100;

                return new A10_2_GastoProrrateableIndicadores(
                        diasPeriodo, concepto, valor, fecha, documento,
                        diaRegistro, diaInicial);
            }

            if (descripcion.matches(".*[\\[\\(].*\\d+.*d[ií]as.*[\\]\\)].*")) {
                Log.w("GastoProrrateable", "⚠️ Formato casi correcto pero no detectado: '" + descripcion + "'");
            }

        } catch (Exception e) {
            Log.e("GastoProrrateable", "Error al parsear: " + descripcion + " → " + e.getMessage());
        }

        return null;
    }

    /**
     * v2 = v1 / d1
     */
    public float getValorDiario() {
        if (diasPeriodo == 0) return 0;
        return (float) valorTotal / diasPeriodo;
    }

    /**
     * Consumo acumulado hasta diaActual.
     * Usa diaInicial (c8_FechaInicial) como base — elimina ambigüedad.
     */
    public float getConsumoAcumulado(int diaActual) {
        int diasTranscurridos = diaActual - diaInicial + 1;
        if (diasTranscurridos <= 0) return 0;
        int diasEfectivos = Math.min(diasTranscurridos, diasPeriodo);
        return getValorDiario() * diasEfectivos;
    }

    /**
     * Días efectivos consumidos hasta diaActual.
     * Usa diaInicial (c8_FechaInicial) como base.
     */
    public int getDiasEfectivos(int diaActual) {
        int diasTranscurridos = diaActual - diaInicial + 1;
        if (diasTranscurridos <= 0) return 0;
        return Math.min(diasTranscurridos, diasPeriodo);
    }

    /**
     * Cuánto falta por consumir
     */
    public float getPendienteConsumir(int diaActual) {
        return valorTotal - getConsumoAcumulado(diaActual);
    }

    // Getters
    public int getDiasPeriodo()  { return diasPeriodo; }
    public String getConcepto()  { return concepto; }
    public int getValorTotal()   { return valorTotal; }
    public String getFecha()     { return fecha; }
    public String getDocumento() { return documento; }
    public int getDiaRegistro()  { return diaRegistro; }  // para mostrar en columna "Día"
    public int getDiaInicial()   { return diaInicial; }   // base del prorrateo

    @Override
    public String toString() {
        return "GastoProrrateable{" +
                "diasPeriodo=" + diasPeriodo +
                ", concepto='" + concepto + '\'' +
                ", valorTotal=" + valorTotal +
                ", diaRegistro=" + diaRegistro +
                ", diaInicial=" + diaInicial +
                '}';
    }
}