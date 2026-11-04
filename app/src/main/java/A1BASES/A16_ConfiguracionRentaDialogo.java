package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;

/**
 * A16_ConfiguracionRentaDialogo — (3-nov, renta R2, pedido de Jorge): opción "4. Configuración
 * renta fiscal" de F2. Muestra y guarda país, moneda e inicio del año fiscal (el fin se calcula:
 * inicio + 1 año − 1 día), lista el catálogo de renglones de renta con su vigencia e importa una
 * plantilla CSV desde la carpeta Balance (cambio de formulario o de país). Después de guardar o
 * importar, las cuentas sin renglón vigente reciben el renglón por defecto (nunca se toca uno
 * vigente que el usuario ya eligió). Mismo patrón simple que A13-A15: AlertDialog armado en código.
 */
public class A16_ConfiguracionRentaDialogo {

    private static final String TAG = "A16_ConfigRenta";
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
    private static final int[] DIAS_MES = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private TextView catalogo_XTv;
    private TextView finAnioFiscal_XTv;

    /** @param alCambiar se llama después de guardar o importar (para refrescar F2). */
    public void mostrar(Context context, Runnable alCambiar) {
        if (context == null) return;

        String pais = "CO", moneda = "COP";
        int mes = 1, dia = 1;
        SQLiteDatabase db = abrir(context);
        try {
            Cursor c = db.rawQuery("SELECT pais, moneda, inicio_anio_fiscal_mes, inicio_anio_fiscal_dia FROM " +
                    A1_1_AyudanteBD.TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
            if (c.moveToFirst()) {
                pais = c.getString(0);
                moneda = c.getString(1);
                mes = c.getInt(2);
                dia = c.getInt(3);
            }
            c.close();
        } finally {
            db.close();
        }

        LinearLayout raiz = new LinearLayout(context);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(32, 16, 32, 16);

        raiz.addView(titulo(context, "País (código, ej. CO)"));
        EditText pais_XEt = campo(context, pais, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        raiz.addView(pais_XEt);

        raiz.addView(titulo(context, "Moneda (ej. COP)"));
        EditText moneda_XEt = campo(context, moneda, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        raiz.addView(moneda_XEt);

        raiz.addView(titulo(context, "Inicio del año fiscal (mes y día)"));
        LinearLayout filaInicio = new LinearLayout(context);
        filaInicio.setOrientation(LinearLayout.HORIZONTAL);
        Spinner mes_XSp = new Spinner(context);
        mes_XSp.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, MESES));
        mes_XSp.setSelection(Math.max(0, Math.min(11, mes - 1)));
        EditText dia_XEt = campo(context, String.valueOf(dia), InputType.TYPE_CLASS_NUMBER);
        dia_XEt.setMinEms(3);
        filaInicio.addView(mes_XSp);
        filaInicio.addView(dia_XEt);
        raiz.addView(filaInicio);

        finAnioFiscal_XTv = new TextView(context);
        finAnioFiscal_XTv.setPadding(0, 8, 0, 8);
        raiz.addView(finAnioFiscal_XTv);
        mostrarFinAnioFiscal(mes, dia);
        mes_XSp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                mostrarFinAnioFiscal(pos + 1, entero(dia_XEt.getText().toString(), 1));
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        raiz.addView(titulo(context, "Catálogo de renglones de renta"));
        catalogo_XTv = new TextView(context);
        catalogo_XTv.setTextSize(13);
        raiz.addView(catalogo_XTv);
        mostrarCatalogo(context);

        Button importar_XBt = new Button(context);
        importar_XBt.setText("Importar CSV (plantilla o fotos)");
        importar_XBt.setOnClickListener(v -> elegirPlantilla(context, alCambiar));
        raiz.addView(importar_XBt);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(raiz);

        new AlertDialog.Builder(context)
                .setTitle("Configuración renta fiscal")
                .setView(scroll)
                .setPositiveButton("Guardar", (d, w) -> guardar(context,
                        pais_XEt.getText().toString(), moneda_XEt.getText().toString(),
                        mes_XSp.getSelectedItemPosition() + 1, dia_XEt.getText().toString(), alCambiar))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void guardar(Context context, String pais, String moneda, int mes, String diaTexto, Runnable alCambiar) {
        pais = pais.trim().toUpperCase(java.util.Locale.ROOT);
        moneda = moneda.trim().toUpperCase(java.util.Locale.ROOT);
        int dia = entero(diaTexto, -1);
        if (pais.isEmpty() || moneda.isEmpty()) {
            Toast.makeText(context, "Falta el país o la moneda; no se guardó", Toast.LENGTH_LONG).show();
            return;
        }
        if (dia < 1 || dia > DIAS_MES[mes - 1]) {
            Toast.makeText(context, "Día de inicio no válido para " + MESES[mes - 1] + "; no se guardó",
                    Toast.LENGTH_LONG).show();
            return;
        }
        SQLiteDatabase db = abrir(context);
        try {
            ContentValues cv = new ContentValues();
            cv.put("pais", pais);
            cv.put("moneda", moneda);
            cv.put("inicio_anio_fiscal_mes", mes);
            cv.put("inicio_anio_fiscal_dia", dia);
            db.update(A1_1_AyudanteBD.TABLE_CONFIGURACION_APP, cv, "config_id = 1", null);
            int asignadas = A1_1_AyudanteBD.asignarRenglonYSoportePendientes(db);
            Toast.makeText(context, "Configuración guardada" +
                    (asignadas > 0 ? "; " + asignadas + " cuentas recibieron renglón por defecto" : ""),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo guardar la configuración: " + e.getMessage(), e);
            Toast.makeText(context, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.close();
        }
        if (alCambiar != null) alCambiar.run();
    }

    // ── Importar plantilla ──────────────────────────────────────────────

    /** Lista los CSV de la carpeta Balance cuyo encabezado empieza por "pais,". */
    private void elegirPlantilla(Context context, Runnable alCambiar) {
        List<File> plantillas = new ArrayList<>();
        File[] carpetas = {
                new File(Environment.getExternalStorageDirectory(), "Balance"),
                context.getExternalFilesDir(null) == null ? null
                        : new File(context.getExternalFilesDir(null), "Balance")};
        for (File carpeta : carpetas) {
            if (carpeta == null || !carpeta.isDirectory()) continue;
            File[] archivos = carpeta.listFiles((dir, nombre) -> nombre.toLowerCase().endsWith(".csv"));
            if (archivos == null) continue;
            for (File f : archivos) {
                String primera = primeraLinea(f);
                // Plantilla de renglones ("pais,...") o respaldo de fotos anuales ("anio_fiscal,...").
                if (primera != null && (primera.trim().toLowerCase().startsWith("pais,")
                        || primera.trim().toLowerCase().startsWith("anio_fiscal,"))) plantillas.add(f);
            }
        }
        if (plantillas.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle("Importar plantilla")
                    .setMessage("No hay plantillas ni fotos en la carpeta Balance.\n\nUna plantilla es un CSV cuya " +
                            "primera fila es:\n" + String.join(",", A1_1_AyudanteBD.COLUMNAS_PLANTILLA_RENTA) +
                            "\n\nEl respaldo de fotos anuales es el archivo 423_FotoRentaCierre (sin_depurar o depurada).")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] nombres = new String[plantillas.size()];
        for (int i = 0; i < nombres.length; i++) nombres[i] = plantillas.get(i).getName();
        new AlertDialog.Builder(context)
                .setTitle("Elija la plantilla")
                .setItems(nombres, (d, i) -> confirmarImportacion(context, plantillas.get(i), alCambiar))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarImportacion(Context context, File archivo, Runnable alCambiar) {
        String primera = primeraLinea(archivo);
        if (primera != null && primera.trim().toLowerCase().startsWith("anio_fiscal,")) {
            new AlertDialog.Builder(context)
                    .setTitle("Restaurar " + archivo.getName())
                    .setMessage("Se restaurarán las fotos anuales de renta del archivo. Las fotos del mismo " +
                            "año y cuenta que ya existan se reemplazan. ¿Continuar?")
                    .setPositiveButton("Restaurar", (d, w) -> restaurarFotos(context, archivo))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("Importar " + archivo.getName())
                .setMessage("Se agregarán los renglones de la plantilla. Si trae un año \"vigente desde\" " +
                        "nuevo, los renglones anteriores de ese país quedarán vigentes hasta el año " +
                        "anterior, y las cuentas sin renglón vigente recibirán el renglón por defecto.\n\n" +
                        "Los renglones que ya existen no se duplican. ¿Continuar?")
                .setPositiveButton("Importar", (d, w) -> importar(context, archivo, alCambiar))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void importar(Context context, File archivo, Runnable alCambiar) {
        SQLiteDatabase db = abrir(context);
        try {
            String texto = leerTexto(archivo);
            db.beginTransaction();
            int renglones, cuentas;
            try {
                renglones = A1_1_AyudanteBD.importarPlantillaRenta(db, texto);
                cuentas = A1_1_AyudanteBD.asignarRenglonYSoportePendientes(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            new AlertDialog.Builder(context)
                    .setTitle("Plantilla importada")
                    .setMessage("Renglones nuevos: " + renglones + "\nCuentas con renglón asignado por defecto: " + cuentas)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo importar la plantilla: " + e.getMessage(), e);
            Toast.makeText(context, "Error al importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.close();
        }
        mostrarCatalogo(context);
        if (alCambiar != null) alCambiar.run();
    }

    private void restaurarFotos(Context context, File archivo) {
        SQLiteDatabase db = abrir(context);
        try {
            String texto = leerTexto(archivo);
            db.beginTransaction();
            int filas;
            try {
                filas = A1_1_AyudanteBD.importarFotosRenta(db, texto);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            Toast.makeText(context, "Fotos restauradas: " + filas + " filas", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron restaurar las fotos: " + e.getMessage(), e);
            Toast.makeText(context, "Error al restaurar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.close();
        }
    }

    // ── Vistas y utilidades ─────────────────────────────────────────────

    private void mostrarCatalogo(Context context) {
        if (catalogo_XTv == null) return;
        StringBuilder sb = new StringBuilder();
        SQLiteDatabase db = abrir(context);
        try {
            Cursor c = db.rawQuery("SELECT pais, formulario, codigo, nombre, naturaleza, vigente_desde, vigente_hasta FROM " +
                    A1_1_AyudanteBD.TABLE_RENGLON_RENTA + " ORDER BY pais, vigente_desde DESC, orden", null);
            while (c.moveToNext()) {
                sb.append(c.getString(0)).append(" ").append(c.isNull(1) ? "" : c.getString(1))
                        .append(" · ").append(c.getString(2)).append(" ").append(c.getString(3))
                        .append(" (").append(c.getString(4)).append(") · ")
                        .append(c.isNull(5) ? "?" : c.getString(5)).append("–")
                        .append(c.isNull(6) ? "vigente" : c.getString(6)).append("\n");
            }
            c.close();
        } finally {
            db.close();
        }
        catalogo_XTv.setText(sb.length() == 0 ? "(sin renglones)" : sb.toString().trim());
    }

    private void mostrarFinAnioFiscal(int mes, int dia) {
        if (finAnioFiscal_XTv == null) return;
        if (dia < 1 || dia > DIAS_MES[mes - 1]) {
            finAnioFiscal_XTv.setText("Fin del año fiscal: (día no válido)");
            return;
        }
        // Fin = el día anterior al inicio, un año después.
        int mesFin = mes, diaFin = dia - 1;
        if (diaFin == 0) {
            mesFin = mes == 1 ? 12 : mes - 1;
            diaFin = DIAS_MES[mesFin - 1];
        }
        finAnioFiscal_XTv.setText("Fin del año fiscal: " + diaFin + " de " + MESES[mesFin - 1].toLowerCase()
                + (mes == 1 && dia == 1 ? " del mismo año" : " del año siguiente"));
    }

    private static TextView titulo(Context context, String texto) {
        TextView t = new TextView(context);
        t.setText(texto);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 16, 0, 4);
        return t;
    }

    private static EditText campo(Context context, String valor, int tipo) {
        EditText e = new EditText(context);
        e.setText(valor);
        e.setInputType(tipo);
        return e;
    }

    private static int entero(String texto, int porDefecto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (Exception e) {
            return porDefecto;
        }
    }

    private static SQLiteDatabase abrir(Context context) {
        return new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF)
                .getWritableDatabase();
    }

    private static String primeraLinea(File f) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String linea = br.readLine();
            return linea == null ? null : linea.replace("﻿", "");
        } catch (Exception e) {
            return null;
        }
    }

    private static String leerTexto(File f) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) sb.append(linea.replace("﻿", "")).append("\n");
        }
        return sb.toString();
    }
}
