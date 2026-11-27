package A1BASES;

import android.app.AlertDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐ NUEVO (25-nov, Jorge): diálogo del repaso de inventario hacia atrás. Se abre desde
 * Transacciones › 📦 Pendientes al tocar una línea de una cuenta con inventario que todavía no
 * tiene artículo. Solo AGREGA el dato de inventario (artículo, unidades, costo) a esa línea; la
 * transacción no se toca (ver A12_InventarioHelper.asignarInventarioALineaExistente).
 */
public final class A38_InventarioPendienteDialogo {

    private A38_InventarioPendienteDialogo() { }

    private static SQLiteDatabase abrir(Context ctx) {
        return new A1_1_AyudanteBD(ctx, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getWritableDatabase();
    }

    public static void mostrar(Context ctx, long cuentaId, String nombreCuenta,
                               A12_InventarioHelper.LineaPendiente linea, Runnable alGuardar) {
        A12_InventarioHelper helper = new A12_InventarioHelper();
        List<A12_InventarioHelper.ItemInventario> items;
        SQLiteDatabase db = abrir(ctx);
        try {
            items = helper.listarItemsActivosPorCuenta(db, cuentaId);
        } finally {
            db.close();
        }
        if (items.isEmpty()) {
            new AlertDialog.Builder(ctx)
                    .setTitle("\"" + nombreCuenta + "\" no tiene artículos")
                    .setMessage("Crea al menos un artículo (por ejemplo, el nombre de la acción o la divisa) para asignarlo a sus líneas.")
                    .setPositiveButton("Crear artículo", (d, w) -> crearArticulo(ctx, cuentaId, nombreCuenta,
                            () -> mostrar(ctx, cuentaId, nombreCuenta, linea, alGuardar)))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        boolean salida = linea.valor < 0;
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout caja = new LinearLayout(ctx);
        caja.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * d);
        caja.setPadding(p, (int) (8 * d), p, 0);

        TextView info = new TextView(ctx);
        info.setText("Documento " + linea.documento + " · " + linea.fecha + "\n" + linea.descripcion
                + "\nValor de la línea: " + linea.valor + "  →  " + (salida ? "SALIDA" : "ENTRADA"));
        info.setTextColor(Color.parseColor("#1F3FA6"));
        info.setTextSize(15);
        caja.addView(info);

        TextView lArt = new TextView(ctx);
        lArt.setText("\nArtículo");
        caja.addView(lArt);
        Spinner articulo = new Spinner(ctx);
        List<String> nombres = new ArrayList<>();
        for (A12_InventarioHelper.ItemInventario it : items) {
            nombres.add(it.nombre + (it.unidad == null || it.unidad.isEmpty() ? "" : " (" + it.unidad + ")"));
        }
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, nombres);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        articulo.setAdapter(ad);
        caja.addView(articulo);

        TextView lUni = new TextView(ctx);
        lUni.setText("Unidades");
        caja.addView(lUni);
        EditText unidades = new EditText(ctx);
        unidades.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        unidades.setHint("Cantidad (sin signo)");
        caja.addView(unidades);

        TextView saldos = new TextView(ctx);
        saldos.setTextSize(14);
        caja.addView(saldos);

        // ⭐ NUEVO (27-nov, Jorge, BD v34): omitir lo pasado ya compensado.
        android.widget.Button omitir = new android.widget.Button(ctx);
        omitir.setText("Omitir (sin inventario)…");
        omitir.setAllCaps(false);
        caja.addView(omitir);

        Runnable recalcular = () -> {
            A12_InventarioHelper.ItemInventario it = items.get(articulo.getSelectedItemPosition());
            SQLiteDatabase dbl = abrir(ctx);
            try {
                long[] s = helper.obtenerSaldoUnidadesYCosto(dbl, it.itemId);
                int dec = it.decimales;
                StringBuilder t = new StringBuilder("\nSaldo anterior: " + A12_InventarioHelper.formatear(s[0], dec)
                        + " unidades · $ " + s[1]);
                long u = 0;
                String txt = unidades.getText().toString().trim();
                if (!txt.isEmpty()) {
                    try {
                        u = A12_InventarioHelper.aInterno(txt, dec);
                    } catch (Exception e) {
                        t.append("\n⚠ ").append(e.getMessage());
                    }
                }
                if (u > 0) {
                    long costo;
                    try {
                        costo = salida ? -helper.calcularCostoSalida(dbl, it.itemId, u) : Math.abs(linea.valor);
                        t.append("\nEste movimiento: ").append(A12_InventarioHelper.formatear(salida ? -u : u, dec))
                                .append(" unidades · $ ").append(costo);
                        t.append("\nNuevo saldo: ").append(A12_InventarioHelper.formatear(s[0] + (salida ? -u : u), dec))
                                .append(" unidades · $ ").append(s[1] + costo);
                        if (salida && costo != linea.valor) {
                            t.append("\n(El costo de la salida es ").append(-costo).append("; la línea dice ")
                                    .append(-linea.valor).append(". La línea no se cambia.)");
                        }
                    } catch (IllegalStateException e) {
                        t.append("\n⚠ ").append(e.getMessage());
                    }
                }
                saldos.setText(t.toString());
            } finally {
                dbl.close();
            }
        };
        articulo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int i, long l) { recalcular.run(); }
            @Override public void onNothingSelected(AdapterView<?> a) { }
        });
        unidades.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { recalcular.run(); }
            @Override public void afterTextChanged(Editable e) { }
        });

        AlertDialog dialogo = new AlertDialog.Builder(ctx)
                .setTitle("📦 Inventario de " + nombreCuenta)
                .setView(caja)
                .setPositiveButton("Guardar", null)
                .setNeutralButton("Nuevo artículo", null)
                .setNegativeButton("Cancelar", null)
                .create();
        omitir.setOnClickListener(v -> {
            int anteriores;
            SQLiteDatabase dbo = abrir(ctx);
            try {
                anteriores = helper.pendientesAnteriores(dbo, cuentaId, linea);
            } finally {
                dbo.close();
            }
            List<String> opciones = new ArrayList<>();
            opciones.add("Omitir solo esta línea (" + linea.valor + ")");
            if (anteriores > 0) opciones.add("Omitir las " + anteriores + " pendientes más antiguas que esta");
            new AlertDialog.Builder(ctx)
                    .setTitle("Omitir líneas")
                    .setItems(opciones.toArray(new String[0]), (dd, w) -> {
                        SQLiteDatabase dbw = abrir(ctx);
                        int n;
                        try {
                            n = helper.omitir(dbw, cuentaId, linea, w == 0);
                        } catch (Exception e) {
                            Toast.makeText(ctx, "No se omitió: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        } finally {
                            dbw.close();
                        }
                        Toast.makeText(ctx, "✓ " + n + " línea(s) omitida(s): quedan sin inventario. "
                                + "Mantén presionado 📦 para restaurarlas.", Toast.LENGTH_LONG).show();
                        dialogo.dismiss();
                        if (alGuardar != null) alGuardar.run();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
        dialogo.setOnShowListener(x -> {
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialogo.dismiss();
                crearArticulo(ctx, cuentaId, nombreCuenta, () -> mostrar(ctx, cuentaId, nombreCuenta, linea, alGuardar));
            });
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                A12_InventarioHelper.ItemInventario it = items.get(articulo.getSelectedItemPosition());
                long u;
                try {
                    u = A12_InventarioHelper.aInterno(unidades.getText().toString(), it.decimales);
                } catch (Exception e) {
                    Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (u <= 0) {
                    Toast.makeText(ctx, "Escribe las unidades (mayor que 0).", Toast.LENGTH_SHORT).show();
                    return;
                }
                SQLiteDatabase dbw = abrir(ctx);
                try {
                    String motivo = helper.motivoNoAsignable(dbw, cuentaId, linea, it.itemId);
                    if (motivo != null) {
                        new AlertDialog.Builder(ctx).setTitle("Todavía no").setMessage(motivo)
                                .setPositiveButton("Entendido", null).show();
                        return;
                    }
                    dbw.beginTransaction();
                    try {
                        helper.asignarInventarioALineaExistente(dbw, linea, it.itemId, u);
                        dbw.setTransactionSuccessful();
                    } finally {
                        dbw.endTransaction();
                    }
                } catch (Exception e) {
                    Toast.makeText(ctx, "No se guardó: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                } finally {
                    dbw.close();
                }
                Toast.makeText(ctx, "✓ Inventario asignado: " + it.nombre + ", "
                        + A12_InventarioHelper.formatear(salida ? -u : u, it.decimales) + " unidades",
                        Toast.LENGTH_LONG).show();
                dialogo.dismiss();
                if (alGuardar != null) alGuardar.run();
            });
            recalcular.run();
        });
        dialogo.show();
    }

    /** Alta rápida de un artículo de la cuenta (nombre y unidad). */
    private static void crearArticulo(Context ctx, long cuentaId, String nombreCuenta, Runnable despues) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout caja = new LinearLayout(ctx);
        caja.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * d);
        caja.setPadding(p, (int) (8 * d), p, 0);
        EditText nombre = new EditText(ctx);
        nombre.setHint("Nombre del artículo (ej. Ecopetrol, USD)");
        caja.addView(nombre);
        EditText unidad = new EditText(ctx);
        unidad.setHint("Unidad (ej. acciones, dólares)");
        caja.addView(unidad);
        Spinner decimales = spinnerDecimales(ctx);
        caja.addView(decimales);
        new AlertDialog.Builder(ctx)
                .setTitle("Nuevo artículo de " + nombreCuenta)
                .setView(caja)
                .setPositiveButton("Crear", (dd, w) -> {
                    String n = nombre.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(ctx, "Falta el nombre del artículo.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SQLiteDatabase db = abrir(ctx);
                    try {
                        new A12_InventarioHelper().insertarItemInventario(db, cuentaId, n, unidad.getText().toString().trim(),
                                decimales.getSelectedItemPosition());
                    } catch (Exception e) {
                        Toast.makeText(ctx, "No se creó: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    } finally {
                        db.close();
                    }
                    if (despues != null) despues.run();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * ⭐ (26-nov, Jorge): selector de decimales de las unidades; la posición es el número de
     * decimales (0 a 3). Monedas: 3.
     */
    public static Spinner spinnerDecimales(Context ctx) {
        Spinner sp = new Spinner(ctx);
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, new String[]{
                "Unidades enteras (0 decimales)", "1 decimal", "2 decimales", "3 decimales (monedas)"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        return sp;
    }
}
