package A1BASES;

import android.graphics.Color;
import android.widget.Button;

import com.jj.appbalancev31.R;

/**
 * ⭐ NUEVO (16-nov, Jorge): estilo único de los botones de acción creados por código, igual al
 * de los layouts (bg_boton_accion: verde #A1CA26, letra azul rey). Las acciones delicadas
 * (borrar) van en rojo suave (bg_boton_delicado, letra blanca).
 */
public final class A31_EstiloBoton {

    private A31_EstiloBoton() {}

    public static void accion(Button b) {
        if (b == null) return;
        b.setBackgroundResource(R.drawable.bg_boton_accion);
        b.setBackgroundTintList(null);
        b.setTextColor(Color.parseColor("#1F3FA6"));
    }

    public static void delicado(Button b) {
        if (b == null) return;
        b.setBackgroundResource(R.drawable.bg_boton_delicado);
        b.setBackgroundTintList(null);
        b.setTextColor(Color.WHITE);
    }
}
