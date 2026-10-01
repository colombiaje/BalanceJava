package A1BASES;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;

/**
 * A13_HistorialInventarioDialogo — Tanda 5 (1-oct, pedido de Jorge): historial básico de
 * movimientos de inventario de una cuenta, con su saldo acumulado después de cada uno.
 *
 * A propósito, DELIBERADAMENTE básico (palabras de Jorge: "aunque básico y perfeccionable
 * después") — una sola tabla cronológica con todos los artículos de la cuenta mezclados
 * (columna "Artículo" los distingue), en vez de una pantalla nueva con pestañas por artículo,
 * gráficas, filtros de fecha, etc. Reutiliza el mismo patrón que A9_VisorTablasDialogo
 * (AlertDialog + TableLayout dentro de Scroll/HorizontalScrollView) en vez de crear un
 * Fragment nuevo, por ser la forma más simple de mostrar una tabla de solo lectura.
 *
 * El saldo acumulado (unidades y costo) se recalcula en memoria, en Java, iterando las filas
 * en orden cronológico (transaccion_id ASC, que por ser AUTOINCREMENT equivale a orden de
 * inserción) y acumulando POR ARTÍCULO (item_id) — igual criterio que
 * A12_InventarioHelper.obtenerSaldoInventario (SUM simple sobre todas las filas del artículo,
 * sin cadena de dependencias entre filas), pero mostrando el saldo tras CADA fila en vez de
 * solo el total final.
 */
public class A13_HistorialInventarioDialogo {

    private static final class SaldoAcumulado {
        long unidades = 0;
        long costo = 0;
    }

    /** Una fila del historial, ya con su saldo acumulado calculado. */
    private static final class FilaHistorial {
        String fecha;
        String documento;
        String articulo;
        long unidades;       // con signo: + entrada, - salida
        long costoMovimiento; // con signo, igual que unidades
        double precioInformativo;
        long saldoUnidadesDespues;
        long saldoCostoDespues;
    }

    /**
     * Consulta y muestra el historial de la cuenta en un AlertDialog. Pensado para llamarse
     * desde un click listener (ver F2_Cuentas) — hace la consulta en el hilo de UI, pero el
     * volumen esperado (movimientos de inventario de una sola cuenta) es pequeño, consistente
     * con el resto de consultas "básicas" de la app que tampoco usan hilos aparte.
     */
    public void mostrar(Context context, long cuentaId, String nombreCuenta) {
        if (context == null) return;

        java.util.List<FilaHistorial> filas = consultarHistorial(context, cuentaId);

        ScrollView scrollVertical = new ScrollView(context);
        TableLayout tabla = new TableLayout(context);
        tabla.setPadding(12, 12, 12, 12);

        tabla.addView(filaEncabezado(context));

        if (filas.isEmpty()) {
            TableRow vacio = new TableRow(context);
            TextView aviso = new TextView(context);
            aviso.setText("Esta cuenta todavía no tiene movimientos de inventario registrados.");
            aviso.setPadding(8, 16, 8, 16);
            vacio.addView(aviso);
            tabla.addView(vacio);
        } else {
            boolean colorAlterno = false;
            for (FilaHistorial f : filas) {
                tabla.addView(filaDatos(context, f, colorAlterno));
                colorAlterno = !colorAlterno;
            }
        }

        HorizontalScrollView scrollHorizontal = new HorizontalScrollView(context);
        scrollHorizontal.addView(tabla);
        scrollVertical.addView(scrollHorizontal);

        new AlertDialog.Builder(context)
                .setTitle("Historial de inventario — " + nombreCuenta)
                .setView(scrollVertical)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private TableRow filaEncabezado(Context context) {
        TableRow fila = new TableRow(context);
        fila.setBackgroundColor(Color.parseColor("#AF87F6"));
        String[] titulos = {
                "Fecha", "Documento", "Artículo", "Unidades",
                "Saldo unid.", "Costo movto.", "Precio inf.", "Saldo COP"
        };
        for (String titulo : titulos) {
            TextView tv = new TextView(context);
            tv.setText(titulo);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(Color.WHITE);
            tv.setPadding(12, 8, 12, 8);
            tv.setGravity(Gravity.CENTER);
            fila.addView(tv);
        }
        return fila;
    }

    private TableRow filaDatos(Context context, FilaHistorial f, boolean colorAlterno) {
        TableRow fila = new TableRow(context);
        fila.setBackgroundColor(colorAlterno ? Color.parseColor("#F4D7F5") : Color.parseColor("#FDFDF9"));
        String[] valores = {
                f.fecha != null ? f.fecha : "",
                f.documento != null ? f.documento : "",
                f.articulo != null ? f.articulo : "",
                String.valueOf(f.unidades),
                String.valueOf(f.saldoUnidadesDespues),
                String.valueOf(f.costoMovimiento),
                String.valueOf(f.precioInformativo),
                String.valueOf(f.saldoCostoDespues)
        };
        for (String valor : valores) {
            TextView tv = new TextView(context);
            tv.setText(valor);
            tv.setPadding(12, 6, 12, 6);
            tv.setGravity(Gravity.CENTER);
            fila.addView(tv);
        }
        return fila;
    }

    private java.util.List<FilaHistorial> consultarHistorial(Context context, long cuentaId) {
        java.util.List<FilaHistorial> resultado = new java.util.ArrayList<>();

        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(
                context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = helper.getReadableDatabase();

        Map<Long, SaldoAcumulado> saldoPorArticulo = new HashMap<>();

        Cursor c = db.rawQuery(
                "SELECT t.transaccion_id, t.c7_FechaYhora, t.c1_Documento, " +
                        "       i.item_id, i.nombre, ti.unidades, ti.costo_total, ti.precio_unitario " +
                        "FROM transacciones_inventario ti " +
                        "JOIN transacciones t ON t.transaccion_id = ti.transaccion_id " +
                        "JOIN items_inventario i ON i.item_id = ti.item_id " +
                        "WHERE i.cuenta_id = ? " +
                        "ORDER BY t.transaccion_id ASC",
                new String[]{String.valueOf(cuentaId)});
        try {
            while (c.moveToNext()) {
                long itemId = c.getLong(3);
                SaldoAcumulado saldo = saldoPorArticulo.get(itemId);
                if (saldo == null) {
                    saldo = new SaldoAcumulado();
                    saldoPorArticulo.put(itemId, saldo);
                }

                FilaHistorial f = new FilaHistorial();
                f.fecha = c.getString(1);
                f.documento = c.getString(2);
                f.articulo = c.getString(4);
                f.unidades = c.getLong(5);
                f.costoMovimiento = c.getLong(6);
                f.precioInformativo = c.getDouble(7);

                saldo.unidades += f.unidades;
                saldo.costo += f.costoMovimiento;
                f.saldoUnidadesDespues = saldo.unidades;
                f.saldoCostoDespues = saldo.costo;

                resultado.add(f);
            }
        } finally {
            c.close();
            db.close();
        }

        return resultado;
    }
}
