package A1BASES;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import A2QueryBD.A22_QueryManager;
import B_FRAGMENTS.F1_CrudDocumento;

/**
 * A11_AuditoriaClasificacionDialogo
 *
 * Muestra las transacciones cuyo Grupo1/Grupo2 guardado (copia interna en
 * "transacciones") ya no coincide con el valor actual y autoritativo de
 * "cuentas". Esta era la causa raíz de que una misma cuenta apareciera
 * partida en varias filas en el Informe (Informes).
 *
 * Desde que obtenerSumaNetoCuentaPorCuenta() lee Grupo1/Grupo2 siempre
 * desde "cuentas" (LEFT JOIN), el Informe ya no se parte visualmente por
 * esta causa — pero el dato viejo/desalineado sigue existiendo en
 * "transacciones" hasta que se corrige el registro puntual. Este diálogo
 * es la señal de alerta (a propósito, NO oculta el problema) para que se
 * pueda ubicar y corregir cada caso.
 *
 * Se abre igual desde F3_1_VerInformePrincipal (Informes) y desde
 * F1_CrudDocumento (botón junto al de respaldo de caché, cacheBackup_XBt).
 *
 * Cada fila es "clickable": lleva directo a corregir ese documento en
 * F1_CrudDocumento / Área 3 (updateDelete), SIN pasar por el autocomplete
 * documentoABuscarParaEditar_XATv (ese sigue igual para su uso normal —
 * ver abrirDocumentoParaCorregir()).
 */
public class A11_AuditoriaClasificacionDialogo extends DialogFragment {

    public static A11_AuditoriaClasificacionDialogo newInstance() {
        return new A11_AuditoriaClasificacionDialogo();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        Window w = getDialog() != null ? getDialog().getWindow() : null;
        if (w == null) return;

        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width  = (int) (getResources().getDisplayMetrics().widthPixels  * 0.92f);
        lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.80f);
        lp.gravity = Gravity.CENTER;
        w.setAttributes(lp);
        w.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                              @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {

        Context context = getContext();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.addView(construirBarraTitulo(context));

        TextView tvNota = new TextView(context);
        tvNota.setText("Estas transacciones tienen guardada una clasificación (Grupo1/Grupo2) " +
                "distinta a la que hoy tiene su cuenta en \"Cuentas\". Las dos últimas columnas " +
                "(\"Cuentas\") son el valor correcto — corrige la transacción para que quede igual.");
        tvNota.setTextSize(12);
        tvNota.setTextColor(Color.parseColor("#546E7A"));
        tvNota.setPadding(20, 12, 20, 6);
        root.addView(tvNota);

        ArrayList<String[]> desalineadas = new ArrayList<>();
        if (context != null) {
            desalineadas = new A22_QueryManager(context).queryTransaccionesDesalineadas();
        }

        if (desalineadas.isEmpty()) {
            TextView tvOk = new TextView(context);
            tvOk.setText("✅ Sin inconsistencias: todas las transacciones coinciden con \"Cuentas\".");
            tvOk.setTextSize(14);
            tvOk.setTypeface(null, Typeface.BOLD);
            tvOk.setTextColor(Color.parseColor("#2E7D32"));
            tvOk.setPadding(20, 30, 20, 30);
            tvOk.setGravity(Gravity.CENTER);
            root.addView(tvOk);
        } else {
            TextView tvCount = new TextView(context);
            tvCount.setText("⚠️  " + desalineadas.size() + " transacción(es) desalineada(s):");
            tvCount.setTextSize(13);
            tvCount.setTypeface(null, Typeface.BOLD);
            tvCount.setTextColor(Color.parseColor("#D84315"));
            tvCount.setPadding(20, 4, 20, 8);
            root.addView(tvCount);

            TextView tvAyuda = new TextView(context);
            tvAyuda.setText("Toca una fila para corregir ese documento.");
            tvAyuda.setTextSize(11);
            tvAyuda.setTypeface(null, Typeface.ITALIC);
            tvAyuda.setTextColor(Color.parseColor("#78909C"));
            tvAyuda.setPadding(20, 0, 20, 6);
            root.addView(tvAyuda);

            ScrollView scrollVertical = new ScrollView(context);
            scrollVertical.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

            HorizontalScrollView scrollHorizontal = new HorizontalScrollView(context);

            TableLayout tabla = new TableLayout(context);
            tabla.setLayoutParams(new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            String[] encabezados = {
                    "Doc.", "Item", "Cuenta",
                    "G1\n(transacción)", "G2\n(transacción)",
                    "G1\n(Cuentas)", "G2\n(Cuentas)"
            };
            TableRow filaEncabezado = new TableRow(context);
            filaEncabezado.setBackgroundColor(Color.parseColor("#FFCCBC"));
            for (String h : encabezados) {
                filaEncabezado.addView(crearCelda(context, h, true));
            }
            tabla.addView(filaEncabezado);

            for (String[] fila : desalineadas) {
                TableRow row = new TableRow(context);
                for (String valor : fila) {
                    row.addView(crearCelda(context, valor, false));
                }
                row.setClickable(true);
                row.setOnClickListener(v -> abrirDocumentoParaCorregir(fila));
                tabla.addView(row);
            }

            scrollHorizontal.addView(tabla);
            scrollVertical.addView(scrollHorizontal);
            root.addView(scrollVertical);
        }

        return root;
    }

    private LinearLayout construirBarraTitulo(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#263238"));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 10, 16, 10);

        TextView tvTitulo = new TextView(context);
        tvTitulo.setText("✅ Auditoría de Clasificación (Grupo1/Grupo2)");
        tvTitulo.setTextSize(15);
        tvTitulo.setTextColor(Color.WHITE);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageButton btnClose = new ImageButton(context);
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setColorFilter(Color.WHITE);
        btnClose.setOnClickListener(v -> dismiss());

        bar.addView(tvTitulo);
        bar.addView(btnClose);
        return bar;
    }

    /**
     * Lleva directo a corregir el documento de la fila tocada, sin pasar
     * por el autocomplete documentoABuscarParaEditar_XATv (ese sigue
     * funcionando igual para cuando el usuario elige un documento a mano).
     *
     * fila[0] es c1_Documento y fila[1] es c2_ItemDoc (ver
     * A21_OptimizedQuery.obtenerTransaccionesDesalineadas()) — con ambos se
     * puede aterrizar directo en el registro con el error, no solo en el
     * documento.
     *
     * Dos casos, según de dónde se abrió este diálogo:
     *  a) Ya estamos DENTRO de F1_CrudDocumento (se abrió desde su propio
     *     botón) — se le pide a esa misma instancia que cargue el
     *     documento (y abra el ítem) en Área 3, sin cerrar/reabrir el
     *     fragmento.
     *  b) Se abrió desde F3_1_VerInformePrincipal (Informes) — no hay
     *     ningún F1_CrudDocumento en pantalla todavía, así que se abre uno
     *     nuevo con el documento ya indicado, igual que hace
     *     F3_2_VerItemTransaccion.abrirFragmentoConDocumento(). En este
     *     caso solo se pasa el documento (el bundle de Canal D no lleva
     *     ítem); el detalle del ítem puntual se ve mejor abriendo primero
     *     el diálogo desde dentro de F1_CrudDocumento.
     *
     * En ambos casos se reutiliza tal cual la lógica de Canal D
     * (F1_CrudDocumento.recibirBundleDeVerItemTransaction) que YA sabe
     * cargar un documento directo en Área 3 y manejar el caso "hay backup
     * pendiente" — por eso el bundle usa la misma bandera
     * "fromVerItemTransaccion" con la que ya se probó ese camino; no se
     * tocó esa lógica protegida (Canal D) para nada de esto.
     */
    private void abrirDocumentoParaCorregir(String[] fila) {
        if (fila == null || fila.length == 0) return;
        String numeroDocumento = fila[0];
        String numeroItem = fila.length > 1 ? fila[1] : null;
        if (numeroDocumento == null || numeroDocumento.isEmpty()) return;

        Fragment padre = getParentFragment();
        if (padre instanceof F1_CrudDocumento) {
            ((F1_CrudDocumento) padre).cargarDocumentoDesdeAuditoria(numeroDocumento, numeroItem);
            dismiss();
            return;
        }

        if (getActivity() == null) return;

        Bundle bundle = new Bundle();
        bundle.putString("keyDocumentNumber", numeroDocumento);
        bundle.putBoolean("fromVerItemTransaccion", true);

        Fragment fragment = new F1_CrudDocumento();
        fragment.setArguments(bundle);

        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(com.jj.appbalancev31.R.id.contenedor_fragments_f0_Xf, fragment)
                .addToBackStack(null)
                .commit();

        dismiss();
    }

    private TextView crearCelda(Context context, String texto, boolean esCabecera) {
        TextView tv = new TextView(context);
        tv.setText(texto != null ? texto : "—");
        tv.setPadding(16, 12, 16, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundResource(android.R.drawable.editbox_background);
        tv.setTextSize(12);
        if (esCabecera) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        return tv;
    }
}
