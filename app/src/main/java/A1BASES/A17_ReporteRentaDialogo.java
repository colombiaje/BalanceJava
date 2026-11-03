package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;

/**
 * A17_ReporteRentaDialogo — (3-nov, renta R4, decisiones de Jorge): opción "6. Reporte renta
 * fiscal" de F4. Renglones de la declaración con su total, en modo resumido o detallado (las
 * cuentas de cada renglón), para un año o para todos (histórico, un año por columna). Los años
 * con foto (cierre 421) usan la foto; el año en curso sin foto se calcula con los datos de hoy y
 * se marca "provisional". Exporta CSV y PDF a la carpeta Balance y a la carpeta de cierres en
 * Drive, para el contador.
 *
 * Signo de presentación: los renglones de naturaleza Pasivo o Ingreso se muestran con el signo
 * invertido (Deudas en positivo), igual que el resumen de la foto; un saldo contrario dentro del
 * renglón resta. Los renglones calculados evalúan su fórmula ("29-30") sobre esos totales.
 */
public class A17_ReporteRentaDialogo {

    private static final String TAG = "A17_ReporteRenta";
    private static final String CLAVE_SIN_RENGLON = "\u0000sin";

    /** Una fila del reporte: un renglón (total) o una cuenta dentro de su renglón. */
    private static final class Fila {
        final boolean esRenglon;
        final String codigo, etiqueta;
        final Map<Integer, Long> valores = new HashMap<>();
        Fila(boolean esRenglon, String codigo, String etiqueta) {
            this.esRenglon = esRenglon;
            this.codigo = codigo;
            this.etiqueta = etiqueta;
        }
    }

    private final DecimalFormat formato = new DecimalFormat("#,##0");
    private TableLayout tabla;
    private List<Integer> aniosVista = new ArrayList<>();
    private final Map<Integer, Boolean> provisional = new HashMap<>();
    private List<Fila> filasVista = new ArrayList<>();
    private boolean detalladoVista = false;
    private String pais = "CO", moneda = "COP";

    public void mostrar(Context context, String folderIdDrive) {
        if (context == null) return;

        // Opciones del selector: año en curso (provisional, si no tiene foto), años con foto, todos.
        List<Integer> aniosConFoto;
        int actual;
        SQLiteDatabase db = abrir(context);
        try {
            aniosConFoto = A1_1_AyudanteBD.aniosConFotoRenta(db);
            actual = A1_1_AyudanteBD.anioFiscalActual(db);
            pais = A1_1_AyudanteBD.paisConfigurado(db);
            android.database.Cursor c = db.rawQuery("SELECT moneda FROM " +
                    A1_1_AyudanteBD.TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
            if (c.moveToFirst()) moneda = c.getString(0);
            c.close();
        } finally {
            db.close();
        }
        List<Integer> anios = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        provisional.clear();
        if (!aniosConFoto.contains(actual)) {
            anios.add(actual);
            etiquetas.add(actual + " (provisional, datos de hoy)");
            provisional.put(actual, true);
        }
        for (Integer a : aniosConFoto) {
            anios.add(a);
            etiquetas.add(a + " (foto del cierre)");
            provisional.put(a, false);
        }
        etiquetas.add("Todos los años (histórico)");

        LinearLayout raiz = new LinearLayout(context);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(24, 8, 24, 8);

        Spinner anio_XSp = new Spinner(context);
        anio_XSp.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, etiquetas));
        raiz.addView(anio_XSp);

        RadioGroup modo_XRg = new RadioGroup(context);
        modo_XRg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton resumido_XRb = new RadioButton(context);
        resumido_XRb.setText("Resumido");
        resumido_XRb.setId(View.generateViewId());
        RadioButton detallado_XRb = new RadioButton(context);
        detallado_XRb.setText("Detallado");
        detallado_XRb.setId(View.generateViewId());
        modo_XRg.addView(resumido_XRb);
        modo_XRg.addView(detallado_XRb);
        resumido_XRb.setChecked(true);
        raiz.addView(modo_XRg);

        tabla = new TableLayout(context);
        tabla.setPadding(0, 8, 0, 8);
        HorizontalScrollView horizontal = new HorizontalScrollView(context);
        horizontal.addView(tabla);
        raiz.addView(horizontal);

        Runnable refrescar = () -> {
            int pos = anio_XSp.getSelectedItemPosition();
            List<Integer> elegidos = new ArrayList<>();
            if (pos >= 0 && pos < anios.size()) {
                elegidos.add(anios.get(pos));
            } else {
                elegidos.addAll(anios);
                Collections.sort(elegidos);
            }
            construir(context, elegidos, detallado_XRb.isChecked());
        };
        anio_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) { refrescar.run(); }
            @Override
            public void onNothingSelected(AdapterView<?> p) { }
        });
        modo_XRg.setOnCheckedChangeListener((g, id) -> refrescar.run());
        refrescar.run();

        ScrollView scroll = new ScrollView(context);
        scroll.addView(raiz);

        new AlertDialog.Builder(context)
                .setTitle("Reporte renta fiscal (" + pais + ", " + moneda + ")")
                .setView(scroll)
                .setPositiveButton("Exportar CSV y PDF", (d, w) -> exportar(context, folderIdDrive))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ── Construcción del reporte ────────────────────────────────────────

    private void construir(Context context, List<Integer> anios, boolean detallado) {
        aniosVista = anios;
        detalladoVista = detallado;
        // Filas de renglón por código (en el orden del catálogo, años de menor a mayor); las
        // cuentas de cada renglón, a continuación de él.
        LinkedHashMap<String, Fila> renglones = new LinkedHashMap<>();
        Map<String, LinkedHashMap<String, Fila>> cuentasPorRenglon = new HashMap<>();
        SQLiteDatabase db = abrir(context);
        try {
            for (Integer anio : anios) {
                List<Object[]> catalogo = A1_1_AyudanteBD.renglonesDelAnio(db, anio);
                Map<Long, Object[]> porId = new HashMap<>();
                for (Object[] r : catalogo) {
                    porId.put((Long) r[0], r);
                    String codigo = (String) r[1];
                    if (!renglones.containsKey(codigo)) {
                        renglones.put(codigo, new Fila(true, codigo, codigo + " · " + r[2]));
                    }
                }
                Map<String, Long> totalPresentado = new HashMap<>();
                for (Object[] s : A1_1_AyudanteBD.saldosRentaDelAnio(db, anio, !Boolean.TRUE.equals(provisional.get(anio)))) {
                    Object[] r = s[2] == null ? null : porId.get((Long) s[2]);
                    String codigo;
                    long valor = (Long) s[3];
                    if (r == null) {
                        codigo = CLAVE_SIN_RENGLON;
                        if (!renglones.containsKey(codigo)) {
                            renglones.put(codigo, new Fila(true, codigo, "Sin renglón asignado"));
                        }
                    } else {
                        codigo = (String) r[1];
                        if (A1_1_AyudanteBD.esNaturalezaDePresentacionNegativa((String) r[3])) valor = -valor;
                    }
                    totalPresentado.put(codigo, valor + (totalPresentado.containsKey(codigo) ? totalPresentado.get(codigo) : 0));
                    LinkedHashMap<String, Fila> cuentas = cuentasPorRenglon.get(codigo);
                    if (cuentas == null) {
                        cuentas = new LinkedHashMap<>();
                        cuentasPorRenglon.put(codigo, cuentas);
                    }
                    String nombre = String.valueOf(s[1]);
                    Fila fc = cuentas.get(nombre);
                    if (fc == null) {
                        fc = new Fila(false, codigo, nombre);
                        cuentas.put(nombre, fc);
                    }
                    fc.valores.put(anio, valor);
                }
                for (Object[] r : catalogo) {
                    String codigo = (String) r[1];
                    long total = "Calculado".equals(r[3])
                            ? A1_1_AyudanteBD.evaluarFormulaRenta((String) r[4], totalPresentado)
                            : (totalPresentado.containsKey(codigo) ? totalPresentado.get(codigo) : 0);
                    renglones.get(codigo).valores.put(anio, total);
                }
                if (totalPresentado.containsKey(CLAVE_SIN_RENGLON)) {
                    renglones.get(CLAVE_SIN_RENGLON).valores.put(anio, totalPresentado.get(CLAVE_SIN_RENGLON));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "No se pudo construir el reporte: " + e.getMessage(), e);
        } finally {
            db.close();
        }
        // "Sin renglón" al final.
        if (renglones.containsKey(CLAVE_SIN_RENGLON)) renglones.put(CLAVE_SIN_RENGLON, renglones.remove(CLAVE_SIN_RENGLON));

        filasVista = new ArrayList<>();
        for (Fila r : renglones.values()) {
            filasVista.add(r);
            if (detallado && cuentasPorRenglon.containsKey(r.codigo)) {
                filasVista.addAll(cuentasPorRenglon.get(r.codigo).values());
            }
        }
        pintar(context);
    }

    private void pintar(Context context) {
        tabla.removeAllViews();
        TableRow encabezado = new TableRow(context);
        encabezado.addView(celda(context, "Renglón / cuenta", true, false));
        for (Integer a : aniosVista) {
            encabezado.addView(celda(context, a + (Boolean.TRUE.equals(provisional.get(a)) ? " (prov.)" : ""), true, true));
        }
        tabla.addView(encabezado);
        if (filasVista.isEmpty()) {
            TableRow vacia = new TableRow(context);
            vacia.addView(celda(context, "No hay datos para este año.", false, false));
            tabla.addView(vacia);
            return;
        }
        for (Fila f : filasVista) {
            TableRow fila = new TableRow(context);
            fila.addView(celda(context, f.esRenglon ? f.etiqueta : "      " + f.etiqueta, f.esRenglon, false));
            for (Integer a : aniosVista) {
                Long v = f.valores.get(a);
                fila.addView(celda(context, v == null ? "" : "$ " + formato.format(v), f.esRenglon, true));
            }
            if (f.esRenglon) fila.setBackgroundColor(Color.parseColor("#DCE8F5"));
            tabla.addView(fila);
        }
    }

    private static TextView celda(Context context, String texto, boolean negrita, boolean derecha) {
        TextView t = new TextView(context);
        t.setText(texto);
        t.setPadding(10, 6, 16, 6);
        t.setTextColor(Color.BLACK);
        if (negrita) t.setTypeface(null, Typeface.BOLD);
        if (derecha) t.setGravity(android.view.Gravity.END);
        return t;
    }

    // ── Exportar ────────────────────────────────────────────────────────

    private void exportar(Context context, String folderIdDrive) {
        if (filasVista.isEmpty()) {
            Toast.makeText(context, "No hay datos para exportar", Toast.LENGTH_LONG).show();
            return;
        }
        String nombreCsv = A9_2_BackupFile.CSV_REPORTE_RENTA.getFileName();
        String nombrePdf = A9_2_BackupFile.PDF_REPORTE_RENTA.getFileName();
        File carpeta = new File(Environment.getExternalStorageDirectory(), "Balance");
        boolean csvOk = escribirCsv(new File(carpeta, nombreCsv));
        boolean pdfOk = escribirPdf(new File(carpeta, nombrePdf));
        if (csvOk) A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombreCsv, folderIdDrive);
        if (pdfOk) A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombrePdf, folderIdDrive);
        Toast.makeText(context, (csvOk && pdfOk) ? "Reporte exportado (CSV y PDF) a Balance y Drive"
                : "Error al exportar " + (csvOk ? "el PDF" : pdfOk ? "el CSV" : "el reporte"), Toast.LENGTH_LONG).show();
    }

    /** Formato largo (una fila por año y concepto), cómodo para tablas dinámicas en Sheets. */
    private boolean escribirCsv(File archivo) {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(archivo, false))) {
            w.write("anio_fiscal,estado,codigo,renglon,cuenta,valor\n");
            String renglonActual = "";
            for (Fila f : filasVista) {
                if (f.esRenglon) renglonActual = f.etiqueta;
                for (Integer a : aniosVista) {
                    Long v = f.valores.get(a);
                    if (v == null) continue;
                    w.write(a + "," + (Boolean.TRUE.equals(provisional.get(a)) ? "provisional" : "foto") + "," +
                            (CLAVE_SIN_RENGLON.equals(f.codigo) ? "" : limpio(f.codigo)) + "," + limpio(renglonActual) + "," +
                            (f.esRenglon ? "" : limpio(f.etiqueta)) + "," + v + "\n");
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al escribir el CSV del reporte: " + e.getMessage(), e);
            return false;
        }
    }

    /** Tabla en texto, hoja A4 vertical, varias páginas si hace falta. */
    private boolean escribirPdf(File archivo) {
        PdfDocument pdf = new PdfDocument();
        try {
            final int ancho = 595, alto = 842, margen = 36, alturaLinea = 14;
            Paint texto = new Paint();
            texto.setTextSize(9.5f);
            texto.setColor(Color.BLACK);
            Paint negrita = new Paint(texto);
            negrita.setTypeface(Typeface.DEFAULT_BOLD);
            Paint titulo = new Paint(negrita);
            titulo.setTextSize(13f);
            int columnas = aniosVista.size();
            int anchoValor = 80;
            int xValores = ancho - margen - columnas * anchoValor;

            List<String[]> lineas = new ArrayList<>();
            for (Fila f : filasVista) {
                String[] l = new String[columnas + 2];
                l[0] = f.esRenglon ? "R" : "C";
                l[1] = f.etiqueta;
                for (int i = 0; i < columnas; i++) {
                    Long v = f.valores.get(aniosVista.get(i));
                    l[i + 2] = v == null ? "" : formato.format(v);
                }
                lineas.add(l);
            }

            int pagina = 0, i = 0;
            while (i < lineas.size() || pagina == 0) {
                pagina++;
                PdfDocument.Page p = pdf.startPage(new PdfDocument.PageInfo.Builder(ancho, alto, pagina).create());
                Canvas c = p.getCanvas();
                int y = margen + 14;
                c.drawText("Reporte de renta fiscal — " + pais + " — valores en " + moneda, margen, y, titulo);
                y += 16;
                c.drawText((detalladoVista ? "Detallado" : "Resumido") + " · generado " +
                        new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).format(new java.util.Date()) +
                        " · página " + pagina, margen, y, texto);
                y += 20;
                c.drawText("Renglón / cuenta", margen, y, negrita);
                for (int k = 0; k < columnas; k++) {
                    Integer a = aniosVista.get(k);
                    String t = a + (Boolean.TRUE.equals(provisional.get(a)) ? " prov." : "");
                    c.drawText(t, xValores + (k + 1) * anchoValor - negrita.measureText(t), y, negrita);
                }
                y += 6;
                c.drawLine(margen, y, ancho - margen, y, texto);
                y += alturaLinea;
                while (i < lineas.size() && y < alto - margen - 20) {
                    String[] l = lineas.get(i);
                    Paint pinta = "R".equals(l[0]) ? negrita : texto;
                    String etiqueta = "R".equals(l[0]) ? l[1] : "    " + l[1];
                    while (etiqueta.length() > 3 && pinta.measureText(etiqueta) > xValores - margen - 8) {
                        etiqueta = etiqueta.substring(0, etiqueta.length() - 2);
                    }
                    c.drawText(etiqueta, margen, y, pinta);
                    for (int k = 0; k < columnas; k++) {
                        String v = l[k + 2];
                        c.drawText(v, xValores + (k + 1) * anchoValor - pinta.measureText(v), y, pinta);
                    }
                    y += alturaLinea;
                    i++;
                }
                if (i >= lineas.size()) {
                    y += 10;
                    c.drawText("prov. = año en curso calculado con los datos del día; sin foto del cierre.",
                            margen, Math.min(y, alto - margen), texto);
                }
                pdf.finishPage(p);
                if (lineas.isEmpty()) break;
            }
            try (FileOutputStream salida = new FileOutputStream(archivo, false)) {
                pdf.writeTo(salida);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al escribir el PDF del reporte: " + e.getMessage(), e);
            return false;
        } finally {
            pdf.close();
        }
    }

    private static String limpio(String v) {
        return v == null ? "" : v.replace(",", " ").replace("\n", " ");
    }

    private static SQLiteDatabase abrir(Context context) {
        return new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF)
                .getWritableDatabase();
    }
}
