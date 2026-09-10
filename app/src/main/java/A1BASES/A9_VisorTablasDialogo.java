package A1BASES;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import B_FRAGMENTS.F1_CrudDocumento;

/**
 * A9_VisorTablasDialogo — VERSIÓN 3
 *
 * NOVEDADES:
 *  - 4 tabs: Crear | Plantilla | Editar | En Espera (área auxiliar)
 *  - Colores por área según drawables de la app:
 *      Crear      → amarillo  (#FFC107)
 *      Plantilla  → teal/verde(@drawable/bg_template → #009688)
 *      Editar     → lila      (#9C27B0)
 *      En Espera  → gris-azul (#455A64)
 *  - Ventana desplazada: no cubre toda la pantalla (margen superior + izquierdo)
 *  - Independiente del RadioButton
 */
public class A9_VisorTablasDialogo extends DialogFragment {

    // ── Áreas ────────────────────────────────────────────────────────────────
    private static final int AREA_CREATE    = 1;
    private static final int AREA_TEMPLATE  = 2;
    private static final int AREA_UPDATE    = 3;
    private static final int AREA_ESPERA    = 4; // ← área auxiliar "En Espera"

    // ── Tabs ─────────────────────────────────────────────────────────────────
    private static final String[] TAB_LABELS = {
            "✏️ Crear",
            "📋 Plantilla",
            "🔄 Editar",
            "⏳ Espera"
    };
    private static final int[] TAB_AREAS = {
            AREA_CREATE, AREA_TEMPLATE, AREA_UPDATE, AREA_ESPERA
    };

    // ── Colores por área (aproximación programática a tus drawables) ──────────
    //    Crear      → amarillo dorado  (bg_yelow_square)
    //    Plantilla  → teal             (bg_template)
    //    Editar     → lila/púrpura     (bg_lilac_square)
    //    En Espera  → gris pizarra     (auxiliar)
    private static final int[] COLOR_TAB_ACTIVO = {
            Color.parseColor("#F9A825"), // Crear     — amarillo oscuro
            //Color.parseColor("#00796B"), // Plantilla — teal oscuro#FA94B7
            Color.parseColor("#FA94B7"), // Plantilla
            Color.parseColor("#7B1FA2"), // Editar    — lila oscuro
            Color.parseColor("#37474F")  // Espera    — gris pizarra
    };
    private static final int[] COLOR_TAB_INACTIVO = {
            Color.parseColor("#FFF9C4"), // Crear     — amarillo muy claro
            Color.parseColor("#B2DFDB"), // Plantilla — teal muy claro
            Color.parseColor("#E1BEE7"), // Editar    — lila muy claro
            Color.parseColor("#CFD8DC")  // Espera    — gris muy claro
    };
    private static final int[] COLOR_TAB_TEXTO_ACTIVO = {
            Color.parseColor("#212121"), // Crear     — texto oscuro (sobre amarillo)
            Color.WHITE,                 // Plantilla
            Color.WHITE,                 // Editar
            Color.WHITE                  // Espera
    };
    private static final int[] COLOR_TAB_TEXTO_INACTIVO = {
            Color.parseColor("#5D4037"), // Crear
            Color.parseColor("#004D40"), // Plantilla
            Color.parseColor("#4A148C"), // Editar
            Color.parseColor("#263238")  // Espera
    };

    // Cabecera de tabla por área
    private static final int[] COLOR_HEADER = {
            Color.parseColor("#FFF176"), // Crear
            Color.parseColor("#80CBC4"), // Plantilla
            Color.parseColor("#CE93D8"), // Editar
            Color.parseColor("#B0BEC5")  // Espera
    };

    // ── Estado ───────────────────────────────────────────────────────────────
    private int           areaActual   = AREA_CREATE;
    private TextView[]    tabViews     = new TextView[TAB_AREAS.length];
    private LinearLayout  contenidoLayout;

    // ─────────────────────────────────────────────────────────────────────────
    //  FACTORY METHODS
    // ─────────────────────────────────────────────────────────────────────────

    public static A9_VisorTablasDialogo newInstance() {
        return newInstance(AREA_CREATE);
    }

    public static A9_VisorTablasDialogo newInstance(int areaInicial) {
        A9_VisorTablasDialogo f = new A9_VisorTablasDialogo();
        Bundle args = new Bundle();
        args.putInt("AREA_INICIAL", areaInicial);
        f.setArguments(args);
        return f;
    }

    /** @deprecated Retrocompatibilidad con llamada anterior (tipoTabla, areaId). */
    @Deprecated
    public static A9_VisorTablasDialogo newInstance(int tipoTabla, int areaId) {
        return newInstance(areaId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            int i = getArguments().getInt("AREA_INICIAL", AREA_CREATE);
            areaActual = (i >= 1 && i <= 4) ? i : AREA_CREATE;
        }
        // Sin barra de acción, sin fullscreen — controlamos tamaño en onStart()
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        Window w = getDialog() != null ? getDialog().getWindow() : null;
        if (w == null) return;

        // ── C) Desplazamiento: ocupa ~88% ancho y ~80% alto,
        //       alineado abajo-derecha para dejar ver la UI base ──────────────
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width  = (int) (getResources().getDisplayMetrics().widthPixels  * 0.88f);
        lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.80f);

        // Gravedad: esquina inferior-derecha con margen
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = 16;  // margen derecho en px (pequeño; el sistema lo interpreta como offset)
        lp.y = 40;  // margen inferior en px
        w.setAttributes(lp);
        w.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.addView(construirBarraTitulo());
        root.addView(construirTabs());

        contenidoLayout = new LinearLayout(getContext());
        contenidoLayout.setOrientation(LinearLayout.VERTICAL);
        contenidoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(contenidoLayout);

        actualizarContenido(areaActual);
        return root;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BARRA TÍTULO
    // ─────────────────────────────────────────────────────────────────────────

    private LinearLayout construirBarraTitulo() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#263238")); // gris muy oscuro
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 10, 16, 10);

        TextView tvTitulo = new TextView(getContext());
        tvTitulo.setText("🗂️  AUDITORÍA DE CACHÉ");
        tvTitulo.setTextSize(16);
        tvTitulo.setTextColor(Color.WHITE);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageButton btnClose = new ImageButton(getContext());
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setColorFilter(Color.WHITE);
        btnClose.setOnClickListener(v -> dismiss());

        bar.addView(tvTitulo);
        bar.addView(btnClose);
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TABS  (desplazamiento horizontal si no caben)
    // ─────────────────────────────────────────────────────────────────────────

    private HorizontalScrollView construirTabs() {
        HorizontalScrollView hsTabs = new HorizontalScrollView(getContext());
        hsTabs.setFillViewport(true);
        hsTabs.setBackgroundColor(Color.parseColor("#ECEFF1"));

        LinearLayout tabRow = new LinearLayout(getContext());
        tabRow.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx  = i;
            final int area = TAB_AREAS[i];

            TextView tab = new TextView(getContext());
            tab.setText(TAB_LABELS[i]);
            tab.setTextSize(13);
            tab.setTypeface(null, Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(24, 26, 24, 26);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(3, 5, 3, 0);
            tab.setLayoutParams(lp);

            aplicarEstiloTab(tab, idx, area == areaActual);

            tab.setOnClickListener(v -> {
                if (areaActual != area) {
                    areaActual = area;
                    actualizarTabs();
                    actualizarContenido(area);
                }
            });

            tabViews[i] = tab;
            tabRow.addView(tab);
        }

        hsTabs.addView(tabRow);
        return hsTabs;
    }

    private void aplicarEstiloTab(TextView tab, int idx, boolean activo) {
        tab.setBackgroundColor(activo ? COLOR_TAB_ACTIVO[idx] : COLOR_TAB_INACTIVO[idx]);
        tab.setTextColor(activo ? COLOR_TAB_TEXTO_ACTIVO[idx] : COLOR_TAB_TEXTO_INACTIVO[idx]);
    }

    private void actualizarTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            aplicarEstiloTab(tabViews[i], i, TAB_AREAS[i] == areaActual);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONTENIDO POR ÁREA
    // ─────────────────────────────────────────────────────────────────────────

    private void actualizarContenido(int area) {
        if (contenidoLayout == null) return;
        contenidoLayout.removeAllViews();

        int idx = indiceDe(area);

        // Badge de área con color de su tab activo
        TextView tvBadge = new TextView(getContext());
        tvBadge.setText("  " + TAB_LABELS[idx] + "  —  " + nombreArea(area));
        tvBadge.setTextSize(12);
        tvBadge.setTextColor(COLOR_TAB_TEXTO_ACTIVO[idx]);
        tvBadge.setBackgroundColor(COLOR_TAB_ACTIVO[idx]);
        tvBadge.setPadding(16, 8, 16, 8);
        contenidoLayout.addView(tvBadge);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(8, 8, 8, 8);

        if (area == AREA_ESPERA) {
            // ── B) Área "En Espera" ─────────────────────────────────────────
            // Muestra la misma tabla de RECORDS pero filtrada con area_id = AREA_ESPERA (4)
            // Si tu BD guarda el "en espera" con otro identificador, cambia el valor aquí.
            crearSeccionTabla(inner,
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    "📌 Encabezado En Espera",
                    area, idx);
            inner.addView(spacer());
            crearSeccionTabla(inner,
                    A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    "📌 Registros En Espera",
                    area, idx);

            // Nota informativa
            TextView tvNota = new TextView(getContext());
            tvNota.setText("ℹ️  El área 'En Espera' guarda el documento #N pausado\n" +
                    "    mientras editas otro en el área 3 (Editar/Eliminar).");
            tvNota.setTextSize(11);
            tvNota.setTextColor(Color.parseColor("#546E7A"));
            tvNota.setPadding(12, 12, 12, 4);
            inner.addView(tvNota);

        } else {
            // ── Áreas normales 1, 2, 3 ────────────────────────────────────────
            crearSeccionTabla(inner,
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    "1. Datos del Formulario (HEADER)",
                    area, idx);
            inner.addView(spacer());
            crearSeccionTabla(inner,
                    A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    "2. Detalle Contable (RECORDS)",
                    area, idx);
        }

        inner.addView(spacer());
        inner.addView(construirBarraAcciones(area, idx));

        scroll.addView(inner);
        contenidoLayout.addView(scroll);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ACCIONES (Escenario C) — "Editar esta área" y "Borrar backup"
    // ─────────────────────────────────────────────────────────────────────────

    private LinearLayout construirBarraAcciones(int area, int idx) {
        LinearLayout fila = new LinearLayout(getContext());
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setPadding(8, 4, 8, 12);

        boolean hayBackup = A5_CacheManager.existeCache(getContext(), area);

        // "Editar" en Crear/Plantilla/Editar salta directo a esa área.
        // "En Espera" no tiene RadioButton propio — en su lugar ofrece el
        // mismo intercambio slot 3 ↔ slot 4 que ya hace el botón verde.
        if (area != AREA_ESPERA) {
            Button btEditar = new Button(getContext());
            btEditar.setText("✏️ Editar esta área");
            btEditar.setEnabled(hayBackup);
            LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lpE.setMargins(4, 0, 4, 0);
            btEditar.setLayoutParams(lpE);
            btEditar.setOnClickListener(v -> editarArea(area));
            fila.addView(btEditar);
        } else {
            Button btIntercambiar = new Button(getContext());
            btIntercambiar.setText("🔄 Enviar a Área 3");
            btIntercambiar.setEnabled(hayBackup);
            LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lpI.setMargins(4, 0, 4, 0);
            btIntercambiar.setLayoutParams(lpI);
            btIntercambiar.setOnClickListener(v -> enviarEsperaAArea3());
            fila.addView(btIntercambiar);
        }

        Button btBorrar = new Button(getContext());
        btBorrar.setText("🗑️ Borrar backup");
        btBorrar.setEnabled(hayBackup);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpB.setMargins(4, 0, 4, 0);
        btBorrar.setLayoutParams(lpB);
        btBorrar.setOnClickListener(v -> confirmarBorrarArea(area));
        fila.addView(btBorrar);

        return fila;
    }

    private void editarArea(int area) {
        Fragment padre = getParentFragment();
        if (padre instanceof F1_CrudDocumento) {
            ((F1_CrudDocumento) padre).irAAreaYRestaurarDesdeVisor(area);
        }
        dismiss();
    }

    private void enviarEsperaAArea3() {
        Fragment padre = getParentFragment();
        if (padre instanceof F1_CrudDocumento) {
            ((F1_CrudDocumento) padre).irAEsperaYIntercambiarDesdeVisor();
        }
        dismiss();
    }

    private void confirmarBorrarArea(int area) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Borrar backup")
                .setMessage("El backup de \"" + nombreArea(area) +
                        "\" se eliminará y no se podrá recuperar. ¿Deseas continuar?")
                .setPositiveButton("BORRAR", (dialog, which) -> {
                    A5_CacheManager.eliminar(getContext(), area);

                    Fragment padre = getParentFragment();
                    if (padre instanceof F1_CrudDocumento) {
                        F1_CrudDocumento f1 = (F1_CrudDocumento) padre;
                        // Si esa misma área está visible ahora mismo en F1, se limpia
                        // también en pantalla — no solo en la caché.
                        f1.limpiarAreaVisibleSiCoincide(area);
                        if (area == AREA_ESPERA) {
                            f1.actualizarVisibilidadBotonVerde();
                        }
                    }
                    actualizarContenido(area); // refresca para mostrar "sin caché"
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TABLAS
    // ─────────────────────────────────────────────────────────────────────────

    private void crearSeccionTabla(LinearLayout parent, String tableName,
                                   String titulo, int area, int idx) {
        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(titulo);
        tvLabel.setTextSize(14);
        tvLabel.setPadding(8, 12, 8, 4);
        tvLabel.setTextColor(COLOR_TAB_ACTIVO[idx]);
        tvLabel.setTypeface(null, Typeface.BOLD);
        parent.addView(tvLabel);

        TableLayout tabla = new TableLayout(getContext());
        tabla.setLayoutParams(new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean tieneDatos = cargarDatos(tabla, tableName, area, idx);

        if (!tieneDatos) {
            TextView tvVacio = new TextView(getContext());
            tvVacio.setText("  ⚠️  Sin caché guardado para esta área");
            tvVacio.setTextSize(12);
            tvVacio.setTextColor(Color.parseColor("#9E9E9E"));
            tvVacio.setPadding(16, 12, 16, 12);
            tvVacio.setBackgroundColor(Color.parseColor("#F5F5F5"));
            parent.addView(tvVacio);
            return;
        }

        HorizontalScrollView hScroll = new HorizontalScrollView(getContext());
        hScroll.addView(tabla);
        parent.addView(hScroll);
    }

    private boolean cargarDatos(TableLayout tabla, String tablaTarget,
                                int area, int idx) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                getContext(),
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);

        SQLiteDatabase db  = helper.getReadableDatabase();
        Cursor         c   = db.rawQuery(
                "SELECT * FROM " + tablaTarget + " WHERE area_id = ? ORDER BY 1 DESC",
                new String[]{String.valueOf(area)});

        boolean tieneDatos = false;

        if (c != null) {
            String[] cols = c.getColumnNames();

            // Cabecera
            TableRow rowH = new TableRow(getContext());
            rowH.setBackgroundColor(COLOR_HEADER[idx]);
            for (String col : cols) rowH.addView(crearCelda(col, true, idx));
            tabla.addView(rowH);

            // Filas
            while (c.moveToNext()) {
                tieneDatos = true;
                TableRow row = new TableRow(getContext());
                for (int i = 0; i < cols.length; i++) {
                    row.addView(crearCelda(c.getString(i), false, idx));
                }
                tabla.addView(row);
            }
            c.close();
        }

        db.close();
        helper.close();
        return tieneDatos;
    }

    private TextView crearCelda(String texto, boolean esCabecera, int idx) {
        TextView tv = new TextView(getContext());
        tv.setText(texto != null ? texto : "—");
        tv.setPadding(18, 14, 18, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundResource(android.R.drawable.editbox_background);
        if (esCabecera) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(COLOR_TAB_ACTIVO[idx]);
        }
        return tv;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private int indiceDe(int area) {
        for (int i = 0; i < TAB_AREAS.length; i++) {
            if (TAB_AREAS[i] == area) return i;
        }
        return 0;
    }

    private String nombreArea(int area) {
        switch (area) {
            case AREA_CREATE:   return "Crear documento";
            case AREA_TEMPLATE: return "Plantilla";
            case AREA_UPDATE:   return "Editar / Eliminar";
            case AREA_ESPERA:   return "Documento en espera";
            default:            return "Desconocida";
        }
    }

    private View spacer() {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 28));
        return v;
    }
}