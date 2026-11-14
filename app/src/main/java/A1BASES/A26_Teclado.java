package A1BASES;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * ⭐ NUEVO (14-nov, Jorge): manejo único del teclado para toda la app. Antes había tres copias
 * de "ocultar teclado" (F1, F2, F3_2) con comportamientos distintos (la de F2 no hacía nada si
 * ningún campo tenía el foco) y un "ver teclado" con la condición al revés.
 */
public final class A26_Teclado {

    private A26_Teclado() {}

    /** Oculta el teclado aunque ningún campo tenga el foco. */
    public static void ocultar(Activity actividad) {
        if (actividad == null) return;
        View vista = actividad.getCurrentFocus();
        if (vista == null) vista = actividad.getWindow().getDecorView();
        InputMethodManager imm = (InputMethodManager) actividad.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && vista != null) imm.hideSoftInputFromWindow(vista.getWindowToken(), 0);
    }

    /** Pone el foco en el campo y muestra el teclado (después de que el campo esté listo). */
    public static void mostrar(View campo) {
        if (campo == null) return;
        campo.requestFocus();
        campo.post(() -> {
            InputMethodManager imm = (InputMethodManager) campo.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /**
     * Para dispatchTouchEvent de la actividad: al tocar fuera de cualquier campo de texto, se
     * cierra el teclado. Si el toque cae en otro campo, no se toca (ese campo lo mantiene).
     */
    public static void ocultarSiTocaFueraDeCampos(Activity actividad, MotionEvent evento) {
        if (actividad == null || evento.getAction() != MotionEvent.ACTION_DOWN) return;
        if (!(actividad.getCurrentFocus() instanceof EditText)) return;
        View raiz = actividad.getWindow().getDecorView();
        if (!hayCampoEn(raiz, (int) evento.getRawX(), (int) evento.getRawY())) ocultar(actividad);
    }

    private static boolean hayCampoEn(View vista, int x, int y) {
        if (vista == null || vista.getVisibility() != View.VISIBLE) return false;
        Rect r = new Rect();
        if (!vista.getGlobalVisibleRect(r) || !r.contains(x, y)) return false;
        if (vista instanceof EditText) return true;
        if (vista instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) vista;
            for (int i = grupo.getChildCount() - 1; i >= 0; i--) {
                if (hayCampoEn(grupo.getChildAt(i), x, y)) return true;
            }
        }
        return false;
    }
}
