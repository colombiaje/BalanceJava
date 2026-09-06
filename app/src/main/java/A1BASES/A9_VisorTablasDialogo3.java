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

/**
 * A9_VisorTablasDialogo — VERSIÓN 2: Visor independiente del RadioButton.
 *
 * Muestra las 3 áreas de caché con tabs clickeables:
 *   TAB 1 → AREA_CREATE   (área 1)
 *   TAB 2 → AREA_TEMPLATE (área 2)
 *   TAB 3 → AREA_UPDATE   (área 3)
 *
 * Llamada desde F1 (sin depender del RadioButton):
 *   A9_VisorTablasDialogo.newInstance()
 *       .show(getChildFragmentManager(), "visor_debug");
 *
 * Si quieres abrir en un área específica, usa:
 *   A9_VisorTablasDialogo.newInstance(areaInicial)
 */
public class A9_VisorTablasDialogo3 extends DialogFragment {

    // ─── Constantes de área (igual que en A1_1_AyudanteBD) ───────────────────
    private static final int AREA_CREATE   = 1;
    private static final int AREA_TEMPLATE = 2;
    private static final int AREA_UPDATE   = 3;

    // Etiquetas de cada tab
    private static final String[] TAB_LABELS = {"✏️ Crear", "📋 Plantilla", "🔄 Editar"};
    private static final int[]    TAB_AREAS  = {AREA_CREATE, AREA_TEMPLATE, AREA_UPDATE};

    // Colores tab activo / inactivo
    private static final int COLOR_TAB_ACTIVO   = Color.parseColor("#1976D2");
    private static final int COLOR_TAB_INACTIVO = Color.parseColor("#90CAF9");
    private static final int COLOR_HEADER_TABLA = Color.parseColor("#BBDEFB");
    private static final int COLOR_BARRA_TITULO = Color.parseColor("#1976D2");
    private static final int COLOR_SIN_DATOS    = Color.parseColor("#F5F5F5");

    // ─── Estado ───────────────────────────────────────────────────────────────
    private int areaActual = AREA_CREATE; // Tab seleccionado al abrir
    private TextView[]     tabViews   = new TextView[3];
    private LinearLayout   contenidoLayout; // Se refresca al cambiar tab

    // ─────────────────────────────────────────────────────────────────────────
    //  FACTORY METHODS
    // ─────────────────────────────────────────────────────────────────────────

    /** Abre el visor en área 1 (Create) por defecto. */
    public static A9_VisorTablasDialogo3 newInstance() {
        return newInstance(AREA_CREATE);
    }

    /**
     * Abre el visor en el área indicada.
     * @param areaInicial 1=Crear, 2=Plantilla, 3=Editar
     */
    public static A9_VisorTablasDialogo3 newInstance(int areaInicial) {
        A9_VisorTablasDialogo3 f = new A9_VisorTablasDialogo3();
        Bundle args = new Bundle();
        args.putInt("AREA_INICIAL", areaInicial);
        f.setArguments(args);
        return f;
    }

    // ─── Compatibilidad con llamada anterior desde F1 (tipoTabla + areaId) ───
    /** @deprecated Usar newInstance() o newInstance(areaInicial). Se conserva para no romper F1. */
    @Deprecated
    public static A9_VisorTablasDialogo3 newInstance(int tipoTabla, int areaId) {
        return newInstance(areaId); // Ignora tipoTabla, usa areaId como tab inicial
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            int inicial = getArguments().getInt("AREA_INICIAL", AREA_CREATE);
            // Validar rango
            areaActual = (inicial >= 1 && inicial <= 3) ? inicial : AREA_CREATE;
        }
        setStyle(DialogFragment.STYLE_NORMAL,
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // ── Raíz ─────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // ── Barra título ──────────────────────────────────────────────────────
        root.addView(construirBarraTitulo());

        // ── Tabs ──────────────────────────────────────────────────────────────
        root.addView(construirTabs());

        // ── Área de contenido (se reemplaza al cambiar tab) ───────────────────
        contenidoLayout = new LinearLayout(getContext());
        contenidoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpContenido = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        contenidoLayout.setLayoutParams(lpContenido);

        root.addView(contenidoLayout);

        // ── Carga inicial ─────────────────────────────────────────────────────
        actualizarContenido(areaActual);

        return root;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONSTRUCCIÓN DE VISTAS
    // ─────────────────────────────────────────────────────────────────────────

    private LinearLayout construirBarraTitulo() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(COLOR_BARRA_TITULO);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(30, 10, 30, 10);

        TextView tvTitulo = new TextView(getContext());
        tvTitulo.setText("🗂️ AUDITORÍA DE CACHÉ COMPLETA");
        tvTitulo.setTextSize(17);
        tvTitulo.setTextColor(Color.WHITE);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitulo.setLayoutParams(lp);

        ImageButton btnClose = new ImageButton(getContext());
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> dismiss());

        bar.addView(tvTitulo);
        bar.addView(btnClose);
        return bar;
    }

    private LinearLayout construirTabs() {
        LinearLayout tabRow = new LinearLayout(getContext());
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setBackgroundColor(Color.parseColor("#E3F2FD"));

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int area = TAB_AREAS[i];
            TextView tab = new TextView(getContext());
            tab.setText(TAB_LABELS[i]);
            tab.setTextSize(14);
            tab.setTypeface(null, Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(20, 28, 20, 28);
            tab.setTextColor(Color.WHITE);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(4, 6, 4, 0);
            tab.setLayoutParams(lp);
            tab.setBackgroundColor(area == areaActual ? COLOR_TAB_ACTIVO : COLOR_TAB_INACTIVO);

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
        return tabRow;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LÓGICA DE NAVEGACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private void actualizarTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean activo = TAB_AREAS[i] == areaActual;
            tabViews[i].setBackgroundColor(activo ? COLOR_TAB_ACTIVO : COLOR_TAB_INACTIVO);
        }
    }

    private void actualizarContenido(int area) {
        if (contenidoLayout == null) return;
        contenidoLayout.removeAllViews();

        // Badge de área
        TextView tvBadge = new TextView(getContext());
        tvBadge.setText("  Área " + area + " — " + nombreArea(area));
        tvBadge.setTextSize(13);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setBackgroundColor(COLOR_TAB_ACTIVO);
        tvBadge.setPadding(20, 10, 20, 10);
        contenidoLayout.addView(tvBadge);

        // Scroll vertical para las dos tablas
        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(10, 10, 10, 10);

        // TABLA 1 — HEADER
        crearSeccionTabla(inner,
                A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                "1. Datos del Formulario (HEADER)",
                area);

        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 32));
        inner.addView(spacer);

        // TABLA 2 — RECORDS
        crearSeccionTabla(inner,
                A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                "2. Detalle Contable (RECORDS)",
                area);

        scroll.addView(inner);
        contenidoLayout.addView(scroll);
    }

    private String nombreArea(int area) {
        switch (area) {
            case AREA_CREATE:   return "Crear documento";
            case AREA_TEMPLATE: return "Plantilla";
            case AREA_UPDATE:   return "Editar / Eliminar";
            default:            return "Desconocida";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TABLAS
    // ─────────────────────────────────────────────────────────────────────────

    private void crearSeccionTabla(LinearLayout parent, String tableName,
                                   String titulo, int area) {
        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(titulo);
        tvLabel.setTextSize(15);
        tvLabel.setPadding(10, 14, 10, 6);
        tvLabel.setTextColor(Color.parseColor("#0D47A1"));
        tvLabel.setTypeface(null, Typeface.BOLD);
        parent.addView(tvLabel);

        HorizontalScrollView hScroll = new HorizontalScrollView(getContext());
        TableLayout tabla = new TableLayout(getContext());
        tabla.setLayoutParams(new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean tieneDatos = cargarDatos(tabla, tableName, area);

        if (!tieneDatos) {
            // Mensaje visual cuando no hay caché para esa área
            TextView tvVacio = new TextView(getContext());
            tvVacio.setText("  ⚠️ Sin caché guardado para esta área");
            tvVacio.setTextSize(13);
            tvVacio.setTextColor(Color.parseColor("#9E9E9E"));
            tvVacio.setPadding(20, 16, 20, 16);
            tvVacio.setBackgroundColor(COLOR_SIN_DATOS);
            parent.addView(tvVacio);
            return;
        }

        hScroll.addView(tabla);
        parent.addView(hScroll);
    }

    /**
     * @return true si la consulta devolvió al menos 1 fila.
     */
    private boolean cargarDatos(TableLayout tabla, String tablaTarget, int area) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                getContext(),
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);

        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + tablaTarget + " WHERE area_id = ? ORDER BY 1 DESC",
                new String[]{String.valueOf(area)});

        boolean tieneDatos = false;

        if (c != null) {
            String[] columnas = c.getColumnNames();

            // Cabecera
            TableRow rowHeader = new TableRow(getContext());
            rowHeader.setBackgroundColor(COLOR_HEADER_TABLA);
            for (String col : columnas) {
                rowHeader.addView(crearCelda(col, true));
            }
            tabla.addView(rowHeader);

            // Filas
            while (c.moveToNext()) {
                tieneDatos = true;
                TableRow row = new TableRow(getContext());
                for (int i = 0; i < columnas.length; i++) {
                    row.addView(crearCelda(c.getString(i), false));
                }
                tabla.addView(row);
            }
            c.close();
        }

        db.close();
        helper.close();
        return tieneDatos;
    }

    private TextView crearCelda(String texto, boolean esCabecera) {
        TextView tv = new TextView(getContext());
        tv.setText(texto != null ? texto : "—");
        tv.setPadding(20, 16, 20, 16);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundResource(android.R.drawable.editbox_background);
        if (esCabecera) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }
}