package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.app.DatePickerDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import androidx.appcompat.app.AlertDialog;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

/**
 * ⭐ NUEVO (13-nov, Jorge): lista de documentos por fecha, desde el botón 📅 de F1 › Edición.
 *
 * Para qué: antes de registrar algo en una fecha, ver qué documentos ya existen ese día (y los
 * vecinos) y así no duplicar. Columnas: fecha · número · descripción corta (la del primer ítem).
 *
 *   - Orden: del más reciente al más antiguo (fecha y, dentro del día, número de mayor a menor).
 *   - "Ir a fecha": salta al día elegido y resalta sus documentos; si ese día no tiene, queda en
 *     el anterior más cercano y lo dice.
 *   - Tocar una fila: carga el documento en Edición exactamente como al elegirlo en el
 *     autocomplete del número (mismo listener).
 *   - "Nuevo en esta fecha": pasa a F1 › Nuevo con "otra fecha" ya puesta. El número del
 *     documento se toma al GUARDAR (último registrado + 1, B12), así que no choca con borradores
 *     en espera ni con respaldos sin registrar. Si Nuevo tiene un borrador en espera, no se toca:
 *     se avisa para terminarlo o limpiarlo primero.
 * Solo lectura sobre la base; no cambia el esquema.
 */
public class A25_ListaDocumentosDialogo {

    private static final int COLOR_FILA_DIA = Color.parseColor("#DCE8F5"); // estándar de información

    private final F1_CrudDocumento f1;
    private final Context context;
    private final List<String[]> filas = new ArrayList<>(); // {documento, fecha yyyymmdd, descripción}
    private int fechaElegida = 0;
    private AlertDialog dialogo;
    private TextView nota;
    private ListView lista;
    private Button nuevoEnFecha;
    private Adaptador adaptador;

    public A25_ListaDocumentosDialogo(F1_CrudDocumento f1) {
        this.f1 = f1;
        this.context = f1.getActivity();
    }

    public void mostrar() {
        if (context == null) return;
        cargar();

        LinearLayout raiz = new LinearLayout(context);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        raiz.setPadding(pad, pad / 2, pad, 0);

        LinearLayout botones = new LinearLayout(context);
        botones.setOrientation(LinearLayout.HORIZONTAL);
        Button irAFecha = boton("Ir a fecha");
        irAFecha.setOnClickListener(v -> elegirFecha());
        nuevoEnFecha = boton("Nuevo en esta fecha");
        nuevoEnFecha.setEnabled(false);
        nuevoEnFecha.setAlpha(0.5f);
        nuevoEnFecha.setOnClickListener(v -> nuevoEnFechaElegida());
        botones.addView(irAFecha, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMarginStart(dp(6));
        botones.addView(nuevoEnFecha, lp);
        raiz.addView(botones);

        nota = new TextView(context);
        nota.setTextSize(13);
        nota.setTextColor(A20_Informacion.COLOR_TEXTO);
        nota.setBackgroundColor(A20_Informacion.COLOR_FONDO);
        nota.setPadding(dp(6), dp(3), dp(6), dp(3));
        nota.setText(filas.size() + " documentos, del más reciente al más antiguo. "
                + "Toca uno para editarlo.");
        LinearLayout.LayoutParams lpNota = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpNota.topMargin = dp(6);
        raiz.addView(nota, lpNota);

        raiz.addView(fila("Fecha", "# Doc", "Descripción", true));

        lista = new ListView(context);
        adaptador = new Adaptador();
        lista.setAdapter(adaptador);
        lista.setOnItemClickListener((p, v, posicion, id) -> abrirEnEdicion(filas.get(posicion)[0]));
        raiz.addView(lista, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        dialogo = new AlertDialog.Builder(context)
                .setTitle("Documentos por fecha")
                .setView(raiz)
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void cargar() {
        filas.clear();
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null,
                version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        // Descripción corta = la del primer ítem del documento (índice documento+ítem).
        Cursor c = db.rawQuery("SELECT t.c1_Documento, MAX(t.c8_FechaInicial) AS fecha, " +
                "(SELECT d.c6_Descripcion FROM transacciones d WHERE d.c1_Documento = t.c1_Documento " +
                "ORDER BY CAST(d.c2_ItemDoc AS INTEGER) LIMIT 1) AS descripcion " +
                "FROM transacciones t GROUP BY t.c1_Documento " +
                "ORDER BY fecha DESC, CAST(t.c1_Documento AS INTEGER) DESC", null);
        try {
            while (c.moveToNext()) {
                filas.add(new String[]{c.getString(0), c.getString(1),
                        c.isNull(2) ? "" : c.getString(2)});
            }
        } finally {
            c.close();
            db.close();
        }
    }

    private void elegirFecha() {
        Calendar hoy = Calendar.getInstance();
        new DatePickerDialog(context, (vista, anio, mes, dia) -> {
            fechaElegida = anio * 10000 + (mes + 1) * 100 + dia;
            int indice = -1;
            boolean hayEseDia = false;
            for (int i = 0; i < filas.size(); i++) {
                int f = entero(filas.get(i)[1]);
                if (f <= fechaElegida) {
                    indice = i;
                    hayEseDia = f == fechaElegida;
                    break;
                }
            }
            int cuantos = 0;
            for (String[] fila : filas) if (entero(fila[1]) == fechaElegida) cuantos++;
            if (hayEseDia) {
                nota.setText(cuantos + (cuantos == 1 ? " documento" : " documentos") + " el "
                        + fechaTexto(fechaElegida) + " (resaltados). Toca uno para editarlo.");
            } else if (indice >= 0) {
                nota.setText("No hay documentos el " + fechaTexto(fechaElegida)
                        + ". Se muestra el anterior más cercano.");
            } else {
                nota.setText("No hay documentos el " + fechaTexto(fechaElegida)
                        + " ni antes de esa fecha.");
            }
            adaptador.notifyDataSetChanged();
            if (indice >= 0) lista.setSelection(Math.max(0, indice - 2));
            nuevoEnFecha.setText("Nuevo el " + fechaTexto(fechaElegida));
            nuevoEnFecha.setEnabled(true);
            nuevoEnFecha.setAlpha(1f);
        }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Igual que elegir el número en el autocomplete de Edición (mismo listener). */
    private void abrirEnEdicion(String documento) {
        if (dialogo != null) dialogo.dismiss();
        if (f1.documentoABuscarParaEditar_XATv == null) return;
        f1.documentoABuscarParaEditar_XATv.setText(documento);
        f1.documentoABuscarParaEditar_XATv.dismissDropDown();
        if (!TextUtils.isEmpty(documento)
                && f1.documentoABuscarParaEditar_XATv.getOnItemClickListener() != null) {
            f1.documentoABuscarParaEditar_XATv.getOnItemClickListener()
                    .onItemClick(null, null, 0, 0);
        }
    }

    private void nuevoEnFechaElegida() {
        if (fechaElegida == 0) return;
        if (f1.existenBackupsPara(R.id.create_XRb)) {
            new AlertDialog.Builder(context)
                    .setTitle("Nuevo tiene un borrador")
                    .setView(A20_Informacion.vista(context,
                            "En F1 › Nuevo hay un documento en espera sin guardar. Termínalo o "
                                    + "límpialo primero; así no se mezcla con el que quieres crear el "
                                    + fechaTexto(fechaElegida) + "."))
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        final int fecha = fechaElegida;
        if (dialogo != null) dialogo.dismiss();
        // Mismo camino que tocar el botón de área "Nuevo" (B13_NavigationManager).
        f1.optionsDoc_XRg.check(R.id.create_XRb);
        // "Otra fecha" ya puesta, como si se hubiera elegido en el calendario (CalendarioUnit).
        f1.otherDateAssignedInCreateNew_Int = fecha;
        if (f1.otherDateInCreateNew_XTv != null) f1.otherDateInCreateNew_XTv.setText("" + fecha);
        new AlertDialog.Builder(context)
                .setTitle("Nuevo el " + fechaTexto(fecha))
                .setView(A20_Informacion.vista(context,
                        "El documento nuevo queda con fecha " + fechaTexto(fecha) + ". Su número "
                                + "se asigna al guardar: el último registrado + 1."))
                .setPositiveButton("Entendido", null)
                .show();
    }

    private static int entero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String fechaTexto(int f) {
        if (f < 10000101) return String.valueOf(f);
        return String.format("%02d-%02d-%04d", f % 100, (f / 100) % 100, f / 10000);
    }

    private Button boton(String texto) {
        Button b = new Button(context);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#2E7D32"));
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private LinearLayout fila(String fecha, String doc, String descripcion, boolean encabezado) {
        LinearLayout f = new LinearLayout(context);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setPadding(dp(4), dp(6), dp(4), dp(6));
        f.addView(celda(fecha, encabezado, Gravity.START), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        f.addView(celda(doc, encabezado, Gravity.END), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        LinearLayout.LayoutParams lpDesc = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f);
        lpDesc.setMarginStart(dp(10));
        f.addView(celda(descripcion, encabezado, Gravity.START), lpDesc);
        if (encabezado) f.setBackgroundColor(Color.parseColor("#C5D3EE"));
        return f;
    }

    private TextView celda(String texto, boolean negrita, int gravedad) {
        TextView t = new TextView(context);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(A20_Informacion.COLOR_TEXTO);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(gravedad);
        if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density);
    }

    private class Adaptador extends BaseAdapter {
        @Override
        public int getCount() {
            return filas.size();
        }

        @Override
        public Object getItem(int i) {
            return filas.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            String[] d = filas.get(i);
            int fecha = entero(d[1]);
            LinearLayout v = fila(fechaTexto(fecha), d[0], d[2], false);
            v.setBackgroundColor(fecha == fechaElegida && fechaElegida != 0
                    ? COLOR_FILA_DIA : Color.TRANSPARENT);
            return v;
        }
    }
}
