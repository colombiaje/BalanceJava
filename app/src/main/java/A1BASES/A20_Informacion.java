package A1BASES;

import android.content.Context;
import android.graphics.Color;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ⭐ NUEVO (12-nov, Jorge): piezas comunes de la "interfaz de información" de toda la app.
 *
 * 1) Estándar de colores (los mismos de res/values/colors.xml): fondo azul claro, texto azul rey
 *    y morado para lo avanzado o lo que pide atención; sin negro. El botón "i" es verde. Toda
 *    explicación que se abre con un "i" se arma con vista(), para que el usuario la reconozca
 *    de inmediato como información.
 *
 * 2) Moneda de cada país (pedido de Jorge: país y moneda deben corresponder; no puede quedar
 *    CO con EUR). Si el país está en la tabla, su moneda es obligatoria; si no está, la moneda
 *    se escribe a mano. Códigos ISO 3166 (país) e ISO 4217 (moneda).
 */
public final class A20_Informacion {

    public static final int COLOR_FONDO = Color.parseColor("#DCE8F5");
    public static final int COLOR_TEXTO = Color.parseColor("#1F3FA6");
    public static final int COLOR_AVANZADO = Color.parseColor("#6A1B9A");

    private static final Map<String, String> MONEDA_POR_PAIS = new HashMap<>();

    static {
        String[][] tabla = {
                {"CO", "COP"}, {"EC", "USD"}, {"PA", "USD"}, {"SV", "USD"}, {"US", "USD"},
                {"PR", "USD"}, {"MX", "MXN"}, {"PE", "PEN"}, {"CL", "CLP"}, {"AR", "ARS"},
                {"BR", "BRL"}, {"UY", "UYU"}, {"PY", "PYG"}, {"BO", "BOB"}, {"VE", "VES"},
                {"CR", "CRC"}, {"GT", "GTQ"}, {"HN", "HNL"}, {"NI", "NIO"}, {"DO", "DOP"},
                {"CU", "CUP"}, {"CA", "CAD"}, {"GB", "GBP"}, {"CH", "CHF"}, {"AU", "AUD"},
                {"JP", "JPY"}, {"ES", "EUR"}, {"DE", "EUR"}, {"FR", "EUR"}, {"IT", "EUR"},
                {"PT", "EUR"}, {"NL", "EUR"}, {"BE", "EUR"}, {"AT", "EUR"}, {"IE", "EUR"},
                {"FI", "EUR"}, {"GR", "EUR"}, {"LU", "EUR"}
        };
        for (String[] fila : tabla) MONEDA_POR_PAIS.put(fila[0], fila[1]);
    }

    private A20_Informacion() {
    }

    /** Moneda obligatoria del país, o null si el país no está en la tabla. */
    public static String monedaDePais(String pais) {
        if (pais == null) return null;
        return MONEDA_POR_PAIS.get(pais.trim().toUpperCase(Locale.ROOT));
    }

    /** Explicación con el estándar de información, lista para AlertDialog.setView(). */
    public static ScrollView vista(Context context, CharSequence texto) {
        TextView tv = new TextView(context);
        tv.setText(texto);
        tv.setTextSize(15);
        tv.setTextColor(COLOR_TEXTO);
        tv.setBackgroundColor(COLOR_FONDO);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad / 2, pad, pad);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(COLOR_FONDO);
        scroll.addView(tv);
        return scroll;
    }
}
