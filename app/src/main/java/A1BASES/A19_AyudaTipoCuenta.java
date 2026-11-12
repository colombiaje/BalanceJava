package A1BASES;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.appcompat.app.AlertDialog;

/**
 * ⭐ NUEVO (12-nov, repaso final del modelo, brecha 3 — opción A + ideas b y c de Jorge):
 * ayuda en lenguaje común para cada tipo de cuenta, pensada para quien no sabe contabilidad.
 *
 *   - ayuda(id): una línea que F2 muestra debajo del spinner de Tipo cuenta.
 *   - esAvanzado(id): tipos que requieren conocimiento técnico; se resaltan en color (ámbar)
 *     en la lista del spinner y en la línea de ayuda.
 *   - mostrarGuia(...): el botón "i" abre la guía de todos los tipos, con el elegido marcado.
 *
 * Los textos van por tipo_cuenta_id, que es fijo: el catálogo definitivo (BD v16) se siembra con
 * los mismos ids en todas las instalaciones y la v30 los conservó. Solo presentación: no se
 * guarda nada en la base.
 */
public final class A19_AyudaTipoCuenta {

    /** Ámbar oscuro: llama la atención sin parecer un error. */
    public static final int COLOR_AVANZADO = Color.parseColor("#B26A00");

    private static final Map<Long, String> AYUDA = new HashMap<>();
    private static final Set<Long> AVANZADOS = new HashSet<>();

    static {
        poner(1, "Dinero en caja, cuentas de ahorro y corrientes, y billeteras digitales. Disponible de inmediato.");
        poner(2, "CDT y otras inversiones de corto plazo que puedes redimir en menos de un año.");
        poner(3, "Participaciones en fondos de inversión o fiduciarias. Retirar el dinero requiere un trámite.");
        poner(4, "Dinero que otras personas o entidades te deben: préstamos que hiciste, anticipos, arriendos por cobrar.");
        poner(5, "Dinero en dólares, euros u otra divisa, en efectivo o en cuentas. Se registra ya convertido a tu moneda.");
        poner(6, "Dinero que te deben en otra divisa. Se registra ya convertido a tu moneda.");
        poner(7, "Inversiones fuera del país que no piensas retirar durante el próximo año.");
        poner(8, "Lo que debes en tus tarjetas de crédito.");
        poner(9, "Gastos del hogar ya causados y todavía sin pagar: servicios, administración, cuotas.");
        poner(10, "Dinero que le debes a personas o a terceros que no son entidades financieras.");
        poner(11, "Deudas en otra divisa (tarjetas o personas). Se registran ya convertidas a tu moneda.");
        poner(12, "Créditos que se pagan en más de un año: hipotecario, vehículo, libranza.");
        avanzado(13, "Control de bienes o derechos que no son parte de tu patrimonio (por ejemplo, algo que administras de otra persona). No suma en tu balance; va siempre con una cuenta de orden acreedora.");
        avanzado(14, "Contrapartida de la cuenta de orden deudora: registra el mismo control por el otro lado. No suma en tu balance.");
        avanzado(15, "Tu patrimonio al empezar a llevar la app: lo que tenías menos lo que debías. Normalmente solo lo usa el saldo inicial.");
        avanzado(16, "Donde el cierre anual acumula los ingresos menos los gastos de años anteriores. Normalmente lo maneja el cierre, no tú.");
        poner(17, "Entradas que aumentan tu patrimonio: salario, honorarios, pensión, arriendos, intereses.");
        poner(18, "Salidas que disminuyen tu patrimonio: mercado, servicios, transporte, salud, impuestos.");
        avanzado(19, "Solo si vendes productos: lo que te costó la mercancía que vendiste. Para uso personal no se necesita.");
        avanzado(20, "Solo si fabricas algo para vender: materiales, mano de obra y gastos de producción.");
        poner(21, "Divisas que tienes en tu poder pero debes entregar a otra persona. Se registran ya convertidas a tu moneda.");
        avanzado(22, "Corrector: resta a tus cuentas por cobrar la parte que crees que no te van a pagar. Su saldo va en negativo dentro del activo.");
        avanzado(23, "Corrector: resta al valor de bienes como vehículos o equipos su desgaste acumulado. Su saldo va en negativo dentro del activo.");
    }

    private A19_AyudaTipoCuenta() {
    }

    private static void poner(long id, String texto) {
        AYUDA.put(id, texto);
    }

    private static void avanzado(long id, String texto) {
        AYUDA.put(id, texto);
        AVANZADOS.add(id);
    }

    public static boolean esAvanzado(Long tipoCuentaId) {
        return tipoCuentaId != null && AVANZADOS.contains(tipoCuentaId);
    }

    /** Texto de ayuda del tipo, o "" si no hay tipo o no tiene texto. */
    public static String ayuda(Long tipoCuentaId) {
        if (tipoCuentaId == null) return "";
        String t = AYUDA.get(tipoCuentaId);
        return t != null ? t : "";
    }

    /** Escribe la ayuda del tipo en la línea de F2 (ámbar y con aviso si es avanzado). */
    public static void ponerAyuda(TextView linea, Long tipoCuentaId) {
        if (linea == null) return;
        String texto = ayuda(tipoCuentaId);
        if (texto.isEmpty()) {
            linea.setText("Elige un tipo; toca \"i\" para ver qué significa cada uno.");
            linea.setTextColor(Color.parseColor("#555555"));
        } else if (esAvanzado(tipoCuentaId)) {
            linea.setText("◆ Tipo avanzado (requiere conocimiento contable). " + texto);
            linea.setTextColor(COLOR_AVANZADO);
        } else {
            linea.setText(texto);
            linea.setTextColor(Color.parseColor("#555555"));
        }
    }

    /**
     * Adaptador del spinner de Tipo cuenta: igual al anterior (mismo layout y mismos textos, que
     * el resto de F2 sigue leyendo con getSelectedItem()), pero los tipos avanzados en ámbar.
     */
    public static ArrayAdapter<String> adaptador(Context context, String[] nombres, long[] ids) {
        return new ArrayAdapter<String>(context,
                android.R.layout.simple_list_item_multiple_choice, nombres) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return colorear(super.getView(position, convertView, parent), position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return colorear(super.getDropDownView(position, convertView, parent), position);
            }

            private View colorear(View v, int position) {
                if (v instanceof TextView) {
                    Long id = position > 0 && position < ids.length ? ids[position] : null;
                    ((TextView) v).setTextColor(esAvanzado(id) ? COLOR_AVANZADO : Color.BLACK);
                }
                return v;
            }
        };
    }

    /** Guía del botón "i": todos los tipos en el orden del spinner, con el elegido marcado. */
    public static void mostrarGuia(Context context, String[] nombres, long[] ids, Long seleccionado) {
        if (context == null || nombres == null || ids == null) return;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("Elige el tipo que mejor describa la cuenta. Los marcados con ◆ requieren "
                + "conocimiento contable; si no estás seguro, no los uses.\n");
        for (int i = 1; i < nombres.length && i < ids.length; i++) {
            long id = ids[i];
            boolean elegido = seleccionado != null && seleccionado == id;
            boolean avanzado = AVANZADOS.contains(id);
            sb.append("\n");
            int inicio = sb.length();
            sb.append(elegido ? "▶ " : "").append(avanzado ? "◆ " : "").append(nombres[i]);
            sb.setSpan(new StyleSpan(Typeface.BOLD), inicio, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (avanzado) {
                sb.setSpan(new ForegroundColorSpan(COLOR_AVANZADO), inicio, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            String texto = ayuda(id);
            if (!texto.isEmpty()) sb.append("\n").append(texto);
            sb.append("\n");
        }
        new AlertDialog.Builder(context)
                .setTitle("Tipos de cuenta")
                .setMessage(sb)
                .setPositiveButton("Entendido", null)
                .show();
    }
}
