package A1BASES;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;

/**
 * A15_DesgloseLiquidezDialogo — (31-oct, pedido de Jorge): muestra cómo se compone el saldo por
 * nivel de liquidez, en tres niveles con su subtotal:
 *
 *   Nivel de liquidez (Inmediata, Requiere trámite, ...)
 *     └ Tipo de cuenta (Efectivo y bancos, ...)
 *         └ Cuenta (Caja, Banco Bbva, ...)
 *
 * Se abre al tocar "Disponible inmediato" en Indicadores y el resumen de liquidez del Informe.
 * Solo cuentas con saldo distinto de cero; los tipos "No aplica" no se muestran. Mismo patrón
 * simple que A13/A14: AlertDialog + TableLayout de solo lectura, consulta en el hilo de UI.
 */
public class A15_DesgloseLiquidezDialogo {

    private static final class Linea {
        String liquidez, tipo, cuenta;
        long saldo;
    }

    public void mostrar(Context context) {
        if (context == null) return;

        List<Linea> lineas = consultar(context);
        DecimalFormat formato = new DecimalFormat("#,##0");

        TableLayout tabla = new TableLayout(context);
        tabla.setPadding(12, 12, 12, 12);
        tabla.setColumnStretchable(0, true);

        if (lineas.isEmpty()) {
            tabla.addView(fila(context, "No hay saldos con nivel de liquidez.", "", 0));
        } else {
            // Totales por nivel y por (nivel, tipo), para escribir cada subtotal ANTES de su detalle.
            Map<String, Long> totalNivel = new LinkedHashMap<>();
            Map<String, Long> totalTipo = new LinkedHashMap<>();
            for (Linea l : lineas) {
                totalNivel.put(l.liquidez, valor(totalNivel, l.liquidez) + l.saldo);
                String claveTipo = l.liquidez + "|" + l.tipo;
                totalTipo.put(claveTipo, valor(totalTipo, claveTipo) + l.saldo);
            }

            String nivelActual = null, tipoActual = null;
            for (Linea l : lineas) {
                if (!l.liquidez.equals(nivelActual)) {
                    tabla.addView(fila(context, l.liquidez,
                            "$ " + formato.format(totalNivel.get(l.liquidez)), 1));
                    nivelActual = l.liquidez;
                    tipoActual = null;
                }
                if (!l.tipo.equals(tipoActual)) {
                    tabla.addView(fila(context, "   " + l.tipo,
                            "$ " + formato.format(totalTipo.get(l.liquidez + "|" + l.tipo)), 2));
                    tipoActual = l.tipo;
                }
                tabla.addView(fila(context, "         " + l.cuenta,
                        "$ " + formato.format(l.saldo), 3));
            }
        }

        HorizontalScrollView scrollHorizontal = new HorizontalScrollView(context);
        scrollHorizontal.addView(tabla);
        ScrollView scrollVertical = new ScrollView(context);
        scrollVertical.addView(scrollHorizontal);

        new AlertDialog.Builder(context)
                .setTitle("Composición por liquidez (valores en "
                        + A1_1_AyudanteBD.monedaConfigurada(context) + ")") // ⭐ (12-nov)
                .setView(scrollVertical)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private static long valor(Map<String, Long> mapa, String clave) {
        Long v = mapa.get(clave);
        return v != null ? v : 0L;
    }

    /** nivel 1 = liquidez, 2 = tipo de cuenta, 3 = cuenta. */
    private TableRow fila(Context context, String texto, String valor, int nivel) {
        TableRow fila = new TableRow(context);
        if (nivel == 1) fila.setBackgroundColor(Color.parseColor("#AF87F6"));
        else if (nivel == 2) fila.setBackgroundColor(Color.parseColor("#E1BEE7"));
        else fila.setBackgroundColor(Color.parseColor("#FDFDF9"));

        TextView tvTexto = new TextView(context);
        tvTexto.setText(texto);
        tvTexto.setPadding(12, 8, 24, 8);
        TextView tvValor = new TextView(context);
        tvValor.setText(valor);
        tvValor.setPadding(12, 8, 12, 8);
        tvValor.setGravity(android.view.Gravity.END);
        if (nivel == 1) {
            tvTexto.setTypeface(null, Typeface.BOLD);
            tvValor.setTypeface(null, Typeface.BOLD);
            tvTexto.setTextColor(Color.WHITE);
            tvValor.setTextColor(Color.WHITE);
        } else if (nivel == 2) {
            tvTexto.setTypeface(null, Typeface.BOLD);
            tvValor.setTypeface(null, Typeface.BOLD);
        }
        fila.addView(tvTexto);
        fila.addView(tvValor);
        return fila;
    }

    private List<Linea> consultar(Context context) {
        List<Linea> resultado = new ArrayList<>();
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = helper.getReadableDatabase();

        // Orden: el de A1_1_AyudanteBD.NIVELES_LIQUIDEZ (de más a menos disponible), luego el
        // orden del catálogo de tipos y la cuenta A-Z.
        StringBuilder ordenNivel = new StringBuilder("CASE tc.liquidez ");
        String[] niveles = A1_1_AyudanteBD.NIVELES_LIQUIDEZ;
        for (int i = 0; i < niveles.length; i++) {
            ordenNivel.append("WHEN '").append(niveles[i].replace("'", "''"))
                    .append("' THEN ").append(i).append(' ');
        }
        ordenNivel.append("ELSE 99 END");

        Cursor c = db.rawQuery(
                "SELECT tc.liquidez, tc.nombre, c.Cuenta, SUM(t.c5_Valor) AS saldo " +
                        "FROM transacciones t " +
                        "JOIN cuentas c ON c.cuenta_id = t.cuenta_id " +
                        "JOIN " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA +
                        " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id " +
                        "WHERE tc.liquidez IS NOT NULL AND tc.liquidez <> 'No aplica' " +
                        "GROUP BY c.cuenta_id HAVING saldo <> 0 " +
                        "ORDER BY " + ordenNivel + ", tc.tipo_cuenta_id, c.Cuenta COLLATE NOCASE",
                null);
        try {
            while (c.moveToNext()) {
                Linea l = new Linea();
                l.liquidez = c.getString(0);
                l.tipo = c.getString(1);
                l.cuenta = c.getString(2);
                l.saldo = c.getLong(3);
                resultado.add(l);
            }
        } finally {
            c.close();
            db.close();
        }
        return resultado;
    }
}
