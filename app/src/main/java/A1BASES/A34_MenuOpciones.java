package A1BASES;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jj.appbalancev31.R;

/**
 * ⭐ NUEVO (22-nov, Jorge): submenú de opciones con el mismo estilo de los menús de F2 y F4
 * (botones verdes numerados, bg_opcion_accion), para que el usuario vea que son opciones de
 * menú y no una lista de archivos. Debajo de cada opción, una línea corta que la explica.
 */
public final class A34_MenuOpciones {

    public interface AlElegir {
        void opcion(int indice);
    }

    private A34_MenuOpciones() { }

    public static void mostrar(Context context, String titulo, String[] opciones, String[] explicaciones,
                               AlElegir alElegir) {
        mostrar(context, titulo, null, opciones, explicaciones, "Cancelar", alElegir);
    }

    /** ⭐ (22-nov): con un texto arriba de las opciones y el texto del botón de cierre. */
    public static void mostrar(Context context, String titulo, String encabezado, String[] opciones,
                               String[] explicaciones, String textoCerrar, AlElegir alElegir) {
        mostrar(context, titulo, encabezado, opciones, explicaciones, null, textoCerrar, alElegir);
    }

    /** ⭐ (22-nov, Jorge): además, un texto al pie, debajo de las opciones y antes del botón de cierre. */
    public static void mostrar(Context context, String titulo, String encabezado, String[] opciones,
                               String[] explicaciones, String pie, String textoCerrar, AlElegir alElegir) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout caja = new LinearLayout(context);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), 0);
        AlertDialog dialogo = new AlertDialog.Builder(context)
                .setTitle(titulo)
                .setView(caja)
                .setNegativeButton(textoCerrar, null)
                .create();
        if (encabezado != null) {
            TextView arriba = new TextView(context);
            arriba.setText(encabezado);
            arriba.setTextSize(15);
            arriba.setTextColor(Color.parseColor("#1F3FA6"));
            caja.addView(arriba);
        }
        for (int i = 0; i < opciones.length; i++) {
            final int indice = i;
            TextView boton = new TextView(context);
            boton.setText((i + 1) + ". " + opciones[i]);
            boton.setTextSize(15);
            boton.setTypeface(Typeface.DEFAULT_BOLD);
            boton.setTextColor(Color.parseColor("#1F3FA6"));
            boton.setGravity(Gravity.CENTER);
            boton.setBackgroundResource(R.drawable.bg_opcion_accion);
            boton.setPadding((int) (10 * d), (int) (10 * d), (int) (10 * d), (int) (10 * d));
            boton.setOnClickListener(v -> {
                dialogo.dismiss();
                alElegir.opcion(indice);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (8 * d);
            caja.addView(boton, lp);
            if (explicaciones != null && i < explicaciones.length && explicaciones[i] != null) {
                TextView nota = new TextView(context);
                nota.setText(explicaciones[i]);
                nota.setTextSize(13);
                nota.setTextColor(Color.parseColor("#1F3FA6"));
                nota.setPadding((int) (6 * d), (int) (2 * d), (int) (6 * d), 0);
                caja.addView(nota);
            }
        }
        if (pie != null) {
            TextView abajo = new TextView(context);
            abajo.setText(pie);
            abajo.setTextSize(15);
            abajo.setTextColor(Color.parseColor("#1F3FA6"));
            abajo.setPadding(0, (int) (14 * d), 0, 0);
            caja.addView(abajo);
        }
        dialogo.show();
    }
}
