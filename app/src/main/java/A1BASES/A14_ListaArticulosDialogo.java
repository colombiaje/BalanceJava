package A1BASES;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.List;

import androidx.appcompat.app.AlertDialog;

/**
 * A14_ListaArticulosDialogo — Tanda 6 (31-oct, pedido de Jorge): lista de los artículos activos
 * de una cuenta con inventario, con su unidad y su saldo actual (unidades y costo en COP).
 * Se abre con el botón "Ver artículos" de los diálogos de alta de artículos (F2_Cuentas al
 * crear/modificar una cuenta, y B12_DocumentPersistence al registrar una transacción).
 *
 * Al tocar un artículo se abre su historial de movimientos (A13_HistorialInventarioDialogo,
 * filtrado por ese artículo). Mismo patrón simple que A13: AlertDialog + TableLayout de solo
 * lectura, consulta en el hilo de UI (volumen pequeño: los artículos de una sola cuenta).
 */
public class A14_ListaArticulosDialogo {

    public void mostrar(Context context, long cuentaId, String nombreCuenta) {
        if (context == null) return;

        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = helper.getReadableDatabase();

        LinearLayout contenedor = new LinearLayout(context);
        contenedor.setOrientation(LinearLayout.VERTICAL);

        TableLayout tabla = new TableLayout(context);
        tabla.setPadding(12, 12, 12, 12);
        tabla.addView(fila(context, new String[]{"Artículo", "Unidad", "Saldo unid.", "Saldo COP"},
                true, false));

        try {
            A12_InventarioHelper inventario = new A12_InventarioHelper();
            List<A12_InventarioHelper.ItemInventario> items =
                    inventario.listarItemsActivosPorCuenta(db, cuentaId);
            if (items.isEmpty()) {
                TableRow vacio = new TableRow(context);
                TextView aviso = new TextView(context);
                aviso.setText("Esta cuenta todavía no tiene artículos.");
                aviso.setPadding(8, 16, 8, 16);
                vacio.addView(aviso);
                tabla.addView(vacio);
            } else {
                boolean colorAlterno = false;
                for (A12_InventarioHelper.ItemInventario item : items) {
                    long[] saldo = inventario.obtenerSaldoUnidadesYCosto(db, item.itemId);
                    TableRow filaItem = fila(context, new String[]{
                            item.nombre,
                            item.unidad != null ? item.unidad : "",
                            String.valueOf(saldo[0]),
                            String.valueOf(saldo[1])
                    }, false, colorAlterno);
                    final long itemId = item.itemId;
                    final String nombreArticulo = item.nombre;
                    filaItem.setClickable(true);
                    filaItem.setOnClickListener(v -> new A13_HistorialInventarioDialogo()
                            .mostrar(context, cuentaId, nombreCuenta, itemId, nombreArticulo));
                    tabla.addView(filaItem);
                    colorAlterno = !colorAlterno;
                }
                TextView pista = new TextView(context);
                pista.setText("Toca un artículo para ver su historial.");
                pista.setTypeface(null, Typeface.ITALIC);
                pista.setPadding(24, 0, 24, 12);
                contenedor.addView(pista);
            }
        } finally {
            db.close();
        }

        HorizontalScrollView scrollHorizontal = new HorizontalScrollView(context);
        scrollHorizontal.addView(tabla);
        contenedor.addView(scrollHorizontal, 0);
        ScrollView scrollVertical = new ScrollView(context);
        scrollVertical.addView(contenedor);

        new AlertDialog.Builder(context)
                .setTitle("Artículos de \"" + nombreCuenta + "\"")
                .setView(scrollVertical)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private TableRow fila(Context context, String[] valores, boolean encabezado, boolean colorAlterno) {
        TableRow fila = new TableRow(context);
        if (encabezado) {
            fila.setBackgroundColor(Color.parseColor("#AF87F6"));
        } else {
            fila.setBackgroundColor(colorAlterno ? Color.parseColor("#F4D7F5") : Color.parseColor("#FDFDF9"));
        }
        for (String valor : valores) {
            TextView tv = new TextView(context);
            tv.setText(valor);
            tv.setPadding(12, encabezado ? 8 : 10, 12, encabezado ? 8 : 10);
            tv.setGravity(Gravity.CENTER);
            if (encabezado) {
                tv.setTypeface(null, Typeface.BOLD);
                tv.setTextColor(Color.WHITE);
            }
            fila.addView(tv);
        }
        return fila;
    }
}
