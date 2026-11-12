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
 *   - CORTA: frase breve que F2 muestra debajo del spinner (pareto 90/10); AYUDA: texto completo.
 *   - esAvanzado(id): tipos que requieren conocimiento técnico; se resaltan en morado
 *     en la lista del spinner y en la línea de ayuda.
 *   - mostrarGuia(...): el botón "i" abre la guía de todos los tipos, con el elegido marcado.
 *
 * Los textos van por tipo_cuenta_id, que es fijo: el catálogo definitivo (BD v16) se siembra con
 * los mismos ids en todas las instalaciones y la v30 los conservó. Solo presentación: no se
 * guarda nada en la base.
 */
public final class A19_AyudaTipoCuenta {

    // ⭐ CAMBIO (12-nov, Jorge): estándar de información de la app (res/values/colors.xml:
    // info_fondo, info_texto, info_avanzado) — dos colores sobre azul claro, sin negro. El ámbar
    // de la primera versión no se leía sobre el fondo rosado de F2.
    public static final int COLOR_FONDO = A20_Informacion.COLOR_FONDO;
    public static final int COLOR_TEXTO = A20_Informacion.COLOR_TEXTO;
    public static final int COLOR_AVANZADO = A20_Informacion.COLOR_AVANZADO;

    private static final Map<Long, String> AYUDA = new HashMap<>();
    // ⭐ NUEVO (12-nov, Jorge, "pareto 90/10"): frase corta para la línea de F2; el texto
    // completo de AYUDA queda para la guía del botón "i".
    private static final Map<Long, String> CORTA = new HashMap<>();
    private static final Set<Long> AVANZADOS = new HashSet<>();

    static {
        poner(1, "Dinero disponible ya: caja, cuentas, billeteras.",
                "Dinero en caja, cuentas de ahorro y corrientes, y billeteras digitales. Disponible de inmediato.");
        poner(2, "CDT e inversiones a menos de un año.",
                "CDT y otras inversiones de corto plazo que puedes redimir en menos de un año.");
        poner(3, "Fondos de inversión y fiduciarias.",
                "Participaciones en fondos de inversión o fiduciarias. Retirar el dinero requiere un trámite.");
        poner(4, "Dinero que te deben.",
                "Dinero que otras personas o entidades te deben: préstamos que hiciste, anticipos, arriendos por cobrar.");
        poner(5, "Dinero en otra divisa, ya convertido.",
                "Dinero en dólares, euros u otra divisa, en efectivo o en cuentas. Se registra ya convertido a tu moneda.");
        poner(6, "Te deben en otra divisa, ya convertido.",
                "Dinero que te deben en otra divisa. Se registra ya convertido a tu moneda.");
        poner(7, "Inversiones en el exterior a más de un año.",
                "Inversiones fuera del país que no piensas retirar durante el próximo año.");
        poner(8, "Lo que debes en tarjetas.",
                "Lo que debes en tus tarjetas de crédito.");
        poner(9, "Gastos del hogar pendientes de pago.",
                "Gastos del hogar ya causados y todavía sin pagar: servicios, administración, cuotas.");
        poner(10, "Lo que debes a personas.",
                "Dinero que le debes a personas o a terceros que no son entidades financieras.");
        poner(11, "Deudas en otra divisa, ya convertidas.",
                "Deudas en otra divisa (tarjetas o personas). Se registran ya convertidas a tu moneda.");
        poner(12, "Créditos a más de un año.",
                "Créditos que se pagan en más de un año: hipotecario, vehículo, libranza.");
        avanzado(13, "Control de bienes ajenos; no suma.",
                "Control de bienes o derechos que no son parte de tu patrimonio (por ejemplo, algo que administras de otra persona). No suma en tu balance; va siempre con una cuenta de orden acreedora.");
        avanzado(14, "Contrapartida de la orden deudora; no suma.",
                "Contrapartida de la cuenta de orden deudora: registra el mismo control por el otro lado. No suma en tu balance.");
        avanzado(15, "Patrimonio inicial; solo para el saldo inicial.",
                "Tu patrimonio al empezar a llevar la app: lo que tenías menos lo que debías. Normalmente solo lo usa el saldo inicial.");
        avanzado(16, "Lo acumula el cierre anual.",
                "Donde el cierre anual acumula los ingresos menos los gastos de años anteriores. Normalmente lo maneja el cierre, no tú.");
        poner(17, "Lo que entra: salario, pensión, arriendos.",
                "Entradas que aumentan tu patrimonio: salario, honorarios, pensión, arriendos, intereses.");
        poner(18, "Lo que sale: mercado, servicios, salud.",
                "Salidas que disminuyen tu patrimonio: mercado, servicios, transporte, salud, impuestos.");
        avanzado(19, "Solo si vendes productos.",
                "Solo si vendes productos: lo que te costó la mercancía que vendiste. Para uso personal no se necesita.");
        avanzado(20, "Solo si fabricas para vender.",
                "Solo si fabricas algo para vender: materiales, mano de obra y gastos de producción.");
        poner(21, "Divisas que debes entregar a otro.",
                "Divisas que tienes en tu poder pero debes entregar a otra persona. Se registran ya convertidas a tu moneda.");
        avanzado(22, "Resta lo que no te van a pagar.",
                "Corrector: resta a tus cuentas por cobrar la parte que crees que no te van a pagar. Su saldo va en negativo dentro del activo.");
        avanzado(23, "Resta el desgaste de los bienes.",
                "Corrector: resta al valor de bienes como vehículos o equipos su desgaste acumulado. Su saldo va en negativo dentro del activo.");
    }

    private A19_AyudaTipoCuenta() {
    }

    private static void poner(long id, String corta, String texto) {
        CORTA.put(id, corta);
        AYUDA.put(id, texto);
    }

    private static void avanzado(long id, String corta, String texto) {
        poner(id, corta, texto);
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

    /** Escribe la frase corta del tipo en la línea de F2, con el estándar de información. */
    public static void ponerAyuda(TextView linea, Long tipoCuentaId) {
        if (linea == null) return;
        linea.setBackgroundColor(COLOR_FONDO);
        String corta = tipoCuentaId != null ? CORTA.get(tipoCuentaId) : null;
        if (corta == null) {
            linea.setText("Toca \"i\" para ver cada tipo.");
            linea.setTextColor(COLOR_TEXTO);
        } else if (esAvanzado(tipoCuentaId)) {
            linea.setText("◆ Avanzado · " + corta);
            linea.setTextColor(COLOR_AVANZADO);
        } else {
            linea.setText(corta);
            linea.setTextColor(COLOR_TEXTO);
        }
    }

    /**
     * Adaptador del spinner de Tipo cuenta: igual al anterior (mismo layout y mismos textos, que
     * el resto de F2 sigue leyendo con getSelectedItem()), pero los tipos avanzados en morado.
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
                    // Los demás, con el color de dato de siempre (la lista es un dato, no
                    // información); la vista se recicla, por eso se fija en ambos casos.
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
        // ⭐ (12-nov) estándar de información: fondo azul claro, texto azul rey, avanzados en morado.
        // Los avanzados conservan su morado (span propio sobre el azul rey de A20).
        new AlertDialog.Builder(context)
                .setTitle("Tipos de cuenta")
                .setView(A20_Informacion.vista(context, sb))
                .setPositiveButton("Entendido", null)
                .show();
    }
}
