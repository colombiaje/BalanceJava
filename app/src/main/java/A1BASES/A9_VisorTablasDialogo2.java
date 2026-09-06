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

public class A9_VisorTablasDialogo2 extends DialogFragment {

    private int areaIdFiltro;

    // Solo pedimos el area_id; las tablas son fijas ahora
    // DENTRO DE A9_VisorTablasDialogo.java
    public static A9_VisorTablasDialogo2 newInstance(int tipoTabla, int areaId) {
        A9_VisorTablasDialogo2 fragment = new A9_VisorTablasDialogo2();
        Bundle args = new Bundle();
        args.putInt("TIPO_TABLA", tipoTabla); // El 2 que enviamos
        args.putInt("AREA_ID", areaId);       // El área actual
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // 🛡️ CAMBIO: Usa exactamente la misma llave que en newInstance
            areaIdFiltro = getArguments().getInt("AREA_ID");
        }
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 1. Contenedor principal vertical
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.WHITE);

        // --- BARRA DE TÍTULO Y BOTÓN CERRAR ---
        LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setBackgroundColor(Color.parseColor("#1976D2")); // Azul oscuro
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(30, 10, 30, 10);

        TextView tvTitulo = new TextView(getContext());
        tvTitulo.setText("AUDITORÍA DE CACHÉ | Área: " + areaIdFiltro);
        tvTitulo.setTextSize(18);
        tvTitulo.setTextColor(Color.WHITE);
        tvTitulo.setTypeface(null, Typeface.BOLD);

        // Peso 1 para que el título ocupe el espacio y empuje el botón a la derecha
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitulo.setLayoutParams(titleParams);

        // Nuevo Botón Cerrar (X)
        ImageButton btnClose = new ImageButton(getContext());
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); // Icono nativo X
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> dismiss()); // Cierra el diálogo

        titleBar.addView(tvTitulo);
        titleBar.addView(btnClose);
        mainLayout.addView(titleBar);

        // --- CONTENEDOR DE SCROLL VERTICAL (para todo el contenido) ---
        ScrollView verticalScroll = new ScrollView(getContext());
        verticalScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(10, 10, 10, 10);

        // ─────────────────────────────────────────────────────────────────
        // TABLA 1: CACHE ENCABEZADO (Aquí iría según tu imagen)
        // ─────────────────────────────────────────────────────────────────
        crearSeccionTabla(contentLayout, A1_1_AyudanteBD.TABLE_CACHE_HEADER, "1. Datos del Formulario (HEADER)");

        // Espacio entre tablas
        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 40)); // 40dp de alto
        contentLayout.addView(spacer);

        // ─────────────────────────────────────────────────────────────────
        // TABLA 2: CACHE REGISTROS (La que ya veías)
        // ─────────────────────────────────────────────────────────────────
        crearSeccionTabla(contentLayout, A1_1_AyudanteBD.TABLE_CACHE_RECORDS, "2. Detalle Contable (RECORDS)");

        verticalScroll.addView(contentLayout);
        mainLayout.addView(verticalScroll);

        return mainLayout;
    }

    /**
     * Helper para crear un título de sección y una tabla con scroll horizontal
     */
    private void crearSeccionTabla(LinearLayout contenedorParent, String tableName, String sectionTitle) {
        // Título de Sección
        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(sectionTitle);
        tvLabel.setTextSize(16);
        tvLabel.setPadding(10, 10, 10, 10);
        tvLabel.setTextColor(Color.BLACK);
        tvLabel.setTypeface(null, Typeface.BOLD);
        contenedorParent.addView(tvLabel);

        // Contenedor de Scroll Horizontal
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(getContext());
        TableLayout tablaLayout = new TableLayout(getContext());
        tablaLayout.setLayoutParams(new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, // Ancho: Todo el espacio
                ViewGroup.LayoutParams.WRAP_CONTENT  // Alto: Lo que necesite el contenido
        ));

        cargarDatos(tablaLayout, tableName);

        horizontalScroll.addView(tablaLayout);
        contenedorParent.addView(horizontalScroll);
    }

    private void cargarDatos(TableLayout tabla, String tablaTarget) {
        // 🛡️ Usa tus constantes para que conecte a la BD real
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                getContext(),
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF
        );

        SQLiteDatabase db = helper.getReadableDatabase();

        String query = "SELECT * FROM " + tablaTarget + " WHERE area_id = ? ORDER BY 1 DESC";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(areaIdFiltro)});

        if (c != null) {
            String[] columnas = c.getColumnNames();

            // 1. CABECERA
            TableRow rowHeader = new TableRow(getContext());
            rowHeader.setBackgroundColor(Color.parseColor("#BBDEFB")); // Azul claro
            for (String col : columnas) {
                rowHeader.addView(crearCelda(col, true));
            }
            tabla.addView(rowHeader);

            // 2. CUERPO
            while (c.moveToNext()) {
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
    }

    private TextView crearCelda(String texto, boolean esCabecera) {
        TextView tv = new TextView(getContext());
        tv.setText(texto != null ? texto : "");
        tv.setPadding(20, 20, 20, 20);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.BLACK);
        // Borde native de Android
        tv.setBackgroundResource(android.R.drawable.editbox_background);

        if (esCabecera) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        return tv;
    }
}