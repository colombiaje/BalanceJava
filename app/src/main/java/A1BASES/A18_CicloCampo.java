package A1BASES;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

/**
 * ⭐ NUEVO (11-nov, Jorge): ciclo del botón ↻ para un campo de búsqueda de cuenta (F1
 * "Escribir cuenta" y F3_1 "Texto a buscar"), con su propia memoria guardada en preferencias.
 *
 * Toque corto: rota lleno → vacío → lo borrado → consulta anterior → vacío → …
 *   Si el usuario escribe a mano en medio del ciclo, lo escrito pasa a ser el nuevo "lleno".
 *   Los pasos que no aplican (nada borrado, o la anterior es igual a lo borrado) se saltan.
 * Toque largo: atajo a la consulta anterior.
 *
 * Cada vez que el ciclo pone un texto (o lo deja vacío) llama a {@link Accion#alPoner(String)},
 * para que la pantalla lance o quite la consulta correspondiente.
 *
 * "Consulta anterior" = la última consulta distinta de la actual: se registra al vaciar el campo
 * con el ciclo y cuando la pantalla avisa con {@link #registrarConsulta(String)} (por ejemplo, al
 * elegir una cuenta de la lista desplegable).
 *
 * Antes, en F1 este botón compartía su memoria (isCopyMode / copiedText) con la descripción de
 * Plantilla y el número del documento en Edición, y al pegar no lanzaba la consulta.
 */
public class A18_CicloCampo {

    public interface Accion { void alPoner(String texto); }

    private static final String PREF = "MemoriaCicloCampos";

    private final Context contexto;
    private final String clave;
    private final TextView campo;
    private final Accion accion;

    private String borrado;
    private String ciclo;
    private String ultima;
    private String anterior;

    public A18_CicloCampo(Context contexto, String clave, TextView campo, Accion accion) {
        this.contexto = contexto.getApplicationContext();
        this.clave = clave;
        this.campo = campo;
        this.accion = accion;
        SharedPreferences sp = prefs();
        borrado = sp.getString(clave + "_borrado", null);
        ciclo = sp.getString(clave + "_ciclo", null);
        ultima = sp.getString(clave + "_ultima", null);
        anterior = sp.getString(clave + "_anterior", null);
    }

    /** Toque corto del botón ↻. */
    public void tocar() {
        String actual = campo.getText().toString();
        boolean enCiclo = ciclo != null && ciclo.equals(actual);

        if (!enCiclo && !TextUtils.isEmpty(actual)) {               // lleno → vacío
            borrado = actual;
            registrarConsulta(actual);
            poner("", "Vacío");
            return;
        }
        boolean hayBorrado = !TextUtils.isEmpty(borrado);
        boolean hayAnterior = !TextUtils.isEmpty(anterior) && !anterior.equals(borrado);
        if (TextUtils.isEmpty(actual)) {                             // vacío → borrado / anterior
            if (hayBorrado) poner(borrado, "Lo borrado");
            else if (hayAnterior) poner(anterior, "Consulta anterior");
            else avisar("No hay nada que recuperar");
        } else if (hayBorrado && actual.equals(borrado) && hayAnterior) { // borrado → anterior
            poner(anterior, "Consulta anterior");
        } else {                                                     // anterior → vacío
            poner("", "Vacío");
        }
    }

    /** Toque largo del botón ↻: atajo a la consulta anterior. */
    public boolean tocarLargo() {
        String objetivo = !TextUtils.isEmpty(anterior) ? anterior : ultima;
        String actual = campo.getText().toString();
        if (TextUtils.isEmpty(objetivo) || objetivo.equals(actual)) {
            avisar("Aún no hay consulta anterior");
            return true;
        }
        if (!TextUtils.isEmpty(actual)) borrado = actual;
        poner(objetivo, "Consulta anterior");
        return true;
    }

    /** La pantalla avisa que se consultó este texto (p. ej. al elegirlo de la lista). */
    public void registrarConsulta(String texto) {
        if (TextUtils.isEmpty(texto)) return;
        if (ultima != null && !ultima.equals(texto)) anterior = ultima;
        ultima = texto;
        guardar();
    }

    private void poner(String texto, String aviso) {
        ciclo = texto;
        campo.setText(texto);
        if (campo instanceof android.widget.EditText) {
            ((android.widget.EditText) campo).setSelection(texto.length());
        }
        guardar();
        avisar(aviso);
        if (accion != null) accion.alPoner(texto);
    }

    private void guardar() {
        prefs().edit()
                .putString(clave + "_borrado", borrado)
                .putString(clave + "_ciclo", ciclo)
                .putString(clave + "_ultima", ultima)
                .putString(clave + "_anterior", anterior)
                .apply();
    }

    private SharedPreferences prefs() {
        return contexto.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private void avisar(String texto) {
        Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show();
    }
}
