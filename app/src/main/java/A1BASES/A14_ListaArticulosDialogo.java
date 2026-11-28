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

    private AlertDialog listaAbierta; // ⭐ (26-nov): para refrescarla tras cambiar decimales

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
        tabla.addView(fila(context, new String[]{"Artículo", "Unidad", "Dec.", "Saldo unid.",
                        "Saldo " + A1_1_AyudanteBD.monedaConfigurada(db), // ⭐ (12-nov) antes "Saldo COP" fijo
                        "Precio prom."}, // ⭐ (26-nov, Jorge)
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
                            String.valueOf(item.decimales),
                            A12_InventarioHelper.formatear(saldo[0], item.decimales),
                            String.valueOf(saldo[1]),
                            saldo[0] > 0
                                    ? String.format(java.util.Locale.US, "%.3f", A12_InventarioHelper.precioPorUnidad(
                                            saldo[1], saldo[0], item.decimales))
                                    : "—"
                    }, false, colorAlterno);
                    final long itemId = item.itemId;
                    final String nombreArticulo = item.nombre;
                    filaItem.setClickable(true);
                    // ⭐ (26-nov, BD v33): un artículo SIN movimientos ofrece también cambiar sus
                    // decimales (con movimientos cambiaría la escala de lo ya guardado).
                    final boolean sinMovimientos = !A12_InventarioHelper.tieneMovimientos(db, itemId);
                    final int decimalesActuales = item.decimales;
                    filaItem.setOnClickListener(v -> {
                        // ⭐ (27-nov, Jorge): siempre un menú. Renombrar sirve con o sin movimientos
                        // (el informe 📊 "por artículo" agrupa por nombre); decimales y eliminar,
                        // solo sin movimientos.
                        List<String> opciones = new java.util.ArrayList<>();
                        opciones.add("Ver historial");
                        opciones.add("Renombrar");
                        if (sinMovimientos) {
                            opciones.add("Cambiar decimales (hoy " + decimalesActuales + ")");
                            opciones.add("Eliminar artículo");
                        }
                        Runnable refrescar = () -> {
                            if (listaAbierta != null) listaAbierta.dismiss();
                            mostrar(context, cuentaId, nombreCuenta);
                        };
                        new AlertDialog.Builder(context)
                                .setTitle(nombreArticulo)
                                .setItems(opciones.toArray(new String[0]), (d, w) -> {
                                    if (w == 0) {
                                        new A13_HistorialInventarioDialogo()
                                                .mostrar(context, cuentaId, nombreCuenta, itemId, nombreArticulo);
                                    } else if (w == 1) {
                                        renombrarArticulo(context, itemId, nombreArticulo, refrescar);
                                    } else if (w == 2) {
                                        cambiarDecimales(context, itemId, nombreArticulo, decimalesActuales, refrescar);
                                    } else {
                                        eliminarArticulo(context, itemId, nombreArticulo, refrescar);
                                    }
                                })
                                .show();
                    });
                    tabla.addView(filaItem);
                    colorAlterno = !colorAlterno;
                }
                TextView pista = new TextView(context);
                pista.setText("Toca un artículo: historial, renombrar (y, si no tiene movimientos, decimales o eliminar).");
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

        listaAbierta = new AlertDialog.Builder(context)
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

    /** ⭐ (26-nov, BD v33): cambia los decimales de un artículo sin movimientos. */
    private void cambiarDecimales(Context context, long itemId, String nombre, int actuales, Runnable despues) {
        android.widget.Spinner sp = A38_InventarioPendienteDialogo.spinnerDecimales(context);
        sp.setSelection(actuales);
        LinearLayout caja = new LinearLayout(context);
        int p = (int) (18 * context.getResources().getDisplayMetrics().density);
        caja.setPadding(p, p / 2, p, 0);
        caja.addView(sp);
        new AlertDialog.Builder(context)
                .setTitle("Decimales de \"" + nombre + "\"")
                .setView(caja)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    SQLiteDatabase db = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                            A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getWritableDatabase();
                    try {
                        A12_InventarioHelper.cambiarDecimales(db, itemId, sp.getSelectedItemPosition());
                        android.widget.Toast.makeText(context, "✓ " + nombre + ": " + sp.getSelectedItemPosition()
                                + " decimales", android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    } finally {
                        db.close();
                    }
                    if (despues != null) despues.run();
                })
                .show();
    }

    /** ⭐ (27-nov, Jorge): elimina un artículo SIN movimientos (p. ej. creado por error). */
    private void eliminarArticulo(Context context, long itemId, String nombre, Runnable despues) {
        new AlertDialog.Builder(context)
                .setTitle("Eliminar \"" + nombre + "\"")
                .setMessage("No tiene movimientos de inventario. ¿Eliminarlo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> {
                    SQLiteDatabase db = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                            A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getWritableDatabase();
                    try {
                        if (A12_InventarioHelper.tieneMovimientos(db, itemId)) {
                            throw new IllegalStateException("El artículo ya tiene movimientos: no se puede eliminar.");
                        }
                        db.delete("items_inventario", "item_id = ?", new String[]{String.valueOf(itemId)});
                        android.widget.Toast.makeText(context, "✓ \"" + nombre + "\" eliminado",
                                android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    } finally {
                        db.close();
                    }
                    if (despues != null) despues.run();
                })
                .show();
    }

    /** ⭐ (27-nov, Jorge): renombra un artículo (no cambia sus movimientos). */
    private void renombrarArticulo(Context context, long itemId, String actual, Runnable despues) {
        android.widget.EditText et = new android.widget.EditText(context);
        et.setText(actual);
        et.setSelectAllOnFocus(true);
        LinearLayout caja = new LinearLayout(context);
        int p = (int) (18 * context.getResources().getDisplayMetrics().density);
        caja.setPadding(p, p / 2, p, 0);
        caja.addView(et, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(context)
                .setTitle("Renombrar \"" + actual + "\"")
                .setMessage("En 📊 Informe \"por artículo\" se agrupan los artículos con el mismo nombre.")
                .setView(caja)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nuevo = et.getText().toString().trim();
                    if (nuevo.isEmpty() || nuevo.equals(actual)) return;
                    SQLiteDatabase db = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                            A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getWritableDatabase();
                    try {
                        android.content.ContentValues v = new android.content.ContentValues();
                        v.put("nombre", nuevo);
                        db.update("items_inventario", v, "item_id = ?", new String[]{String.valueOf(itemId)});
                        android.widget.Toast.makeText(context, "✓ Ahora se llama \"" + nuevo + "\"",
                                android.widget.Toast.LENGTH_SHORT).show();
                    } catch (android.database.sqlite.SQLiteConstraintException e) {
                        android.widget.Toast.makeText(context, "Ya hay un artículo \"" + nuevo + "\" en esta cuenta.",
                                android.widget.Toast.LENGTH_LONG).show();
                    } finally {
                        db.close();
                    }
                    if (despues != null) despues.run();
                })
                .show();
    }
}
