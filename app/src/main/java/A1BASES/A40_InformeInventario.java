package A1BASES;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ⭐ NUEVO (27-nov, Jorge): 📊 Informe de inventario, de solo lectura.
 *  - Por cuenta: cada cuenta con inventario, sus artículos y el cotejo contable vs inventario.
 *  - Por artículo: cada artículo (agrupado por NOMBRE) con las cuentas donde está y un total con
 *    su promedio ponderado — para comparar, p. ej., los euros en efectivo con los de ARQ.
 * Se alterna entre las dos vistas; tocar una fila abre el 📜 historial de ese artículo en esa
 * cuenta. PDF y Excel se generan en la caché y se comparten con el menú de Android.
 * Saldos actuales (sin fecha de corte, por ahora).
 */
public final class A40_InformeInventario {

    private A40_InformeInventario() { }

    static class Fila {
        long cuentaId, itemId;
        String cuenta, articulo, unidad;
        int decimales, movimientos, ultimo;
        long unidades, costo;
    }

    /** Fila ya armada para mostrar/exportar. tipo: "G" grupo, "A" artículo, "T" total. */
    static class Linea {
        String tipo;
        String[] celdas; // Nombre, Unidades, Saldo, Precio prom., Movs., Último
        Fila fila;       // solo en "A"
        Linea(String tipo, String[] celdas, Fila fila) {
            this.tipo = tipo;
            this.celdas = celdas;
            this.fila = fila;
        }
    }

    private static final String[] ENCABEZADOS = {"Nombre", "Unidades", "Saldo $", "Precio prom.", "Movs.", "Último"};

    private static SQLiteDatabase abrir(Context ctx) {
        return new A1_1_AyudanteBD(ctx, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getReadableDatabase();
    }

    static List<Fila> consultar(SQLiteDatabase db) {
        List<Fila> l = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT cu.cuenta_id, cu.Cuenta, i.item_id, i.nombre, IFNULL(i.unidad, ''),"
                + " IFNULL(i.decimales, 0), IFNULL(SUM(ti.unidades), 0), IFNULL(SUM(ti.costo_total), 0),"
                + " COUNT(ti.transaccion_id), IFNULL(MAX(" + A12_InventarioHelper.SQL_FECHA_LINEA + "), 0)"
                + " FROM items_inventario i JOIN cuentas cu ON cu.cuenta_id = i.cuenta_id"
                + " LEFT JOIN transacciones_inventario ti ON ti.item_id = i.item_id"
                + " LEFT JOIN transacciones t ON t.transaccion_id = ti.transaccion_id"
                + " WHERE i.activo = 1 GROUP BY i.item_id"
                + " ORDER BY cu.Cuenta COLLATE NOCASE, i.nombre COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                Fila f = new Fila();
                f.cuentaId = c.getLong(0);
                f.cuenta = c.getString(1);
                f.itemId = c.getLong(2);
                f.articulo = c.getString(3);
                f.unidad = c.getString(4);
                f.decimales = c.getInt(5);
                f.unidades = c.getLong(6);
                f.costo = c.getLong(7);
                f.movimientos = c.getInt(8);
                f.ultimo = c.getInt(9);
                l.add(f);
            }
        }
        return l;
    }

    private static BigDecimal real(long interno, int dec) {
        return BigDecimal.valueOf(interno, dec);
    }

    private static String precio(long costo, BigDecimal unidadesReales) {
        if (unidadesReales.signum() == 0) return "—";
        return BigDecimal.valueOf(Math.abs(costo)).divide(unidadesReales.abs(), 3, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fecha(int f) {
        if (f < 19000101) return "—";
        return (f % 100) + "/" + ((f / 100) % 100) + "/" + (f / 10000);
    }

    private static String nombreArticulo(Fila f) {
        return f.articulo + (f.unidad == null || f.unidad.isEmpty() ? "" : " (" + f.unidad + ")");
    }

    static List<Linea> porCuenta(SQLiteDatabase db, List<Fila> filas) {
        Map<Long, List<Fila>> grupos = new LinkedHashMap<>();
        for (Fila f : filas) {
            if (!grupos.containsKey(f.cuentaId)) grupos.put(f.cuentaId, new ArrayList<>());
            grupos.get(f.cuentaId).add(f);
        }
        A12_InventarioHelper h = new A12_InventarioHelper();
        List<Linea> l = new ArrayList<>();
        for (Map.Entry<Long, List<Fila>> e : grupos.entrySet()) {
            long[] k = h.comparacionContableInventario(db, e.getKey());
            long dif = k[0] - k[1];
            l.add(new Linea("G", new String[]{e.getValue().get(0).cuenta, "", String.valueOf(k[0]),
                    dif == 0 ? "✓ cuadra" : "Dif. " + dif, k[2] > 0 ? k[2] + " 📦" : "", ""}, null));
            for (Fila f : e.getValue()) {
                BigDecimal u = real(f.unidades, f.decimales);
                l.add(new Linea("A", new String[]{"   " + nombreArticulo(f), u.toPlainString(),
                        String.valueOf(f.costo), precio(f.costo, u), String.valueOf(f.movimientos), fecha(f.ultimo)}, f));
            }
        }
        return l;
    }

    static List<Linea> porArticulo(List<Fila> filas) {
        Map<String, List<Fila>> grupos = new LinkedHashMap<>();
        List<Fila> ordenadas = new ArrayList<>(filas);
        java.util.Collections.sort(ordenadas, (a, b) -> {
            int x = a.articulo.trim().compareToIgnoreCase(b.articulo.trim());
            return x != 0 ? x : a.cuenta.compareToIgnoreCase(b.cuenta);
        });
        for (Fila f : ordenadas) {
            String clave = f.articulo.trim().toLowerCase(Locale.ROOT);
            if (!grupos.containsKey(clave)) grupos.put(clave, new ArrayList<>());
            grupos.get(clave).add(f);
        }
        List<Linea> l = new ArrayList<>();
        for (List<Fila> g : grupos.values()) {
            l.add(new Linea("G", new String[]{g.get(0).articulo.trim(), "", "", "", "", ""}, null));
            BigDecimal totalU = BigDecimal.ZERO;
            long totalC = 0;
            int totalM = 0, ultimo = 0;
            for (Fila f : g) {
                BigDecimal u = real(f.unidades, f.decimales);
                totalU = totalU.add(u);
                totalC += f.costo;
                totalM += f.movimientos;
                ultimo = Math.max(ultimo, f.ultimo);
                l.add(new Linea("A", new String[]{"   " + f.cuenta + (f.unidad == null || f.unidad.isEmpty() ? "" : " (" + f.unidad + ")"),
                        u.toPlainString(), String.valueOf(f.costo), precio(f.costo, u),
                        String.valueOf(f.movimientos), fecha(f.ultimo)}, f));
            }
            if (g.size() > 1) {
                l.add(new Linea("T", new String[]{"   Total", totalU.toPlainString(), String.valueOf(totalC),
                        precio(totalC, totalU), String.valueOf(totalM), fecha(ultimo)}, null));
            }
        }
        return l;
    }

    // ─────────────────────────────────────────────
    //  Pantalla
    // ─────────────────────────────────────────────
    public static void mostrar(Context ctx) {
        final boolean[] porArticulo = {false};
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout raiz = new LinearLayout(ctx);
        raiz.setOrientation(LinearLayout.VERTICAL);
        LinearLayout barra = new LinearLayout(ctx);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER);
        Button alternar = boton(ctx, "⇄ Por artículo");
        Button pdf = boton(ctx, "PDF");
        Button excel = boton(ctx, "Excel");
        barra.addView(alternar);
        barra.addView(pdf);
        barra.addView(excel);
        raiz.addView(barra);

        TableLayout tabla = new TableLayout(ctx);
        tabla.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
        HorizontalScrollView h = new HorizontalScrollView(ctx);
        h.addView(tabla);
        ScrollView v = new ScrollView(ctx);
        v.addView(h);
        raiz.addView(v);

        Runnable pintar = () -> {
            List<Linea> lineas = armar(ctx, porArticulo[0]);
            tabla.removeAllViews();
            tabla.addView(fila(ctx, ENCABEZADOS, Color.parseColor("#AF87F6"), true, Color.WHITE));
            if (lineas.isEmpty()) {
                TableRow r = new TableRow(ctx);
                TextView t = new TextView(ctx);
                t.setText("No hay artículos de inventario.");
                t.setPadding(8, 16, 8, 16);
                r.addView(t);
                tabla.addView(r);
            }
            boolean alterno = false;
            for (Linea li : lineas) {
                TableRow r;
                if ("G".equals(li.tipo)) {
                    r = fila(ctx, li.celdas, Color.parseColor("#FFE0B2"), true, Color.BLACK);
                } else if ("T".equals(li.tipo)) {
                    r = fila(ctx, li.celdas, Color.parseColor("#E1BEE7"), true, Color.BLACK);
                } else {
                    r = fila(ctx, li.celdas, alterno ? Color.parseColor("#F3E5F5") : Color.WHITE, false, Color.BLACK);
                    alterno = !alterno;
                    final Fila f = li.fila;
                    r.setOnClickListener(x -> new A13_HistorialInventarioDialogo()
                            .mostrar(ctx, f.cuentaId, f.cuenta, f.itemId, f.articulo));
                }
                tabla.addView(r);
            }
            alternar.setText(porArticulo[0] ? "⇄ Por cuenta" : "⇄ Por artículo");
        };
        alternar.setOnClickListener(x -> {
            porArticulo[0] = !porArticulo[0];
            pintar.run();
        });
        pdf.setOnClickListener(x -> exportarPdf(ctx));
        excel.setOnClickListener(x -> exportarExcel(ctx));
        pintar.run();

        new AlertDialog.Builder(ctx)
                .setTitle("📊 Informe de inventario")
                .setView(raiz)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private static List<Linea> armar(Context ctx, boolean porArticulo) {
        SQLiteDatabase db = abrir(ctx);
        try {
            List<Fila> filas = consultar(db);
            return porArticulo ? porArticulo(filas) : porCuenta(db, filas);
        } finally {
            db.close();
        }
    }

    private static Button boton(Context ctx, String texto) {
        Button b = new Button(ctx);
        b.setText(texto);
        b.setAllCaps(false);
        b.setBackgroundResource(com.jj.appbalancev31.R.drawable.bg_opcion_accion);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(8, 4, 8, 4);
        b.setLayoutParams(p);
        return b;
    }

    private static TableRow fila(Context ctx, String[] celdas, int fondo, boolean negrita, int color) {
        TableRow r = new TableRow(ctx);
        r.setBackgroundColor(fondo);
        for (int i = 0; i < celdas.length; i++) {
            TextView t = new TextView(ctx);
            t.setText(celdas[i]);
            t.setPadding(10, 8, 10, 8);
            t.setTextColor(color);
            t.setGravity(i == 0 ? Gravity.START : Gravity.END);
            if (negrita) t.setTypeface(Typeface.DEFAULT_BOLD);
            r.addView(t);
        }
        return r;
    }

    // ─────────────────────────────────────────────
    //  Exportar y compartir
    // ─────────────────────────────────────────────
    private static String sello() {
        return new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
    }

    private static void compartir(Context ctx, File archivo, String tipo) {
        Uri uri = FileProvider.getUriForFile(ctx, "com.jj.appbalancev31.fileprovider", archivo);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(tipo);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, "Informe de inventario");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "Compartir informe de inventario"));
    }

    private static File carpeta(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "informes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static void exportarExcel(Context ctx) {
        try {
            SQLiteDatabase db = abrir(ctx);
            List<Linea> pc, pa;
            try {
                List<Fila> filas = consultar(db);
                pc = porCuenta(db, filas);
                pa = porArticulo(filas);
            } finally {
                db.close();
            }
            List<A35_Xlsx.Hoja> hojas = new ArrayList<>();
            hojas.add(hoja("Por cuenta", "Cuenta / artículo", pc));
            hojas.add(hoja("Por artículo", "Artículo / cuenta", pa));
            File f = new File(carpeta(ctx), "informe_inventario_" + sello() + ".xlsx");
            A35_Xlsx.escribir(f, hojas);
            compartir(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            Toast.makeText(ctx, "No se generó el Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static A35_Xlsx.Hoja hoja(String nombre, String primera, List<Linea> lineas) {
        A35_Xlsx.Hoja h = new A35_Xlsx.Hoja();
        h.nombre = nombre;
        h.inmovilizarColumnaA = true;
        h.decimalesComoNumero = true;
        h.columnas.add("Tipo");
        h.columnas.add(primera);
        for (int i = 1; i < ENCABEZADOS.length; i++) h.columnas.add(ENCABEZADOS[i]);
        for (int i = 0; i < h.columnas.size(); i++) h.editable.add(false);
        for (Linea li : lineas) {
            String[] f = new String[ENCABEZADOS.length + 1];
            f[0] = "G".equals(li.tipo) ? "Grupo" : "T".equals(li.tipo) ? "Total" : "Detalle";
            for (int i = 0; i < li.celdas.length; i++) f[i + 1] = li.celdas[i].trim();
            h.filas.add(f);
        }
        return h;
    }

    static void exportarPdf(Context ctx) {
        PdfDocument pdf = new PdfDocument();
        try {
            SQLiteDatabase db = abrir(ctx);
            List<Linea> pc, pa;
            try {
                List<Fila> filas = consultar(db);
                pc = porCuenta(db, filas);
                pa = porArticulo(filas);
            } finally {
                db.close();
            }
            final int ancho = 595, alto = 842, margen = 32, linea = 15;
            final int[] xCol = {margen, 250, 320, 395, 470, 563}; // derecha de cada columna (excepto la 0)
            Paint texto = new Paint();
            texto.setTextSize(9.5f);
            texto.setColor(Color.BLACK);
            Paint negrita = new Paint(texto);
            negrita.setTypeface(Typeface.DEFAULT_BOLD);
            Paint grande = new Paint(negrita);
            grande.setTextSize(13f);
            int[] pagina = {0};
            int[] y = {0};
            PdfDocument.Page[] p = {null};
            Canvas[] c = {null};
            String hoy = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(new java.util.Date());
            Runnable nueva = () -> {
                if (p[0] != null) pdf.finishPage(p[0]);
                pagina[0]++;
                p[0] = pdf.startPage(new PdfDocument.PageInfo.Builder(ancho, alto, pagina[0]).create());
                c[0] = p[0].getCanvas();
                y[0] = margen + 12;
                c[0].drawText("Informe de inventario · " + hoy + " · página " + pagina[0], margen, alto - margen + 14, texto);
            };
            nueva.run();
            for (int s = 0; s < 2; s++) {
                List<Linea> lineas = s == 0 ? pc : pa;
                if (y[0] > alto - margen - 80) nueva.run();
                c[0].drawText(s == 0 ? "Por cuenta" : "Por artículo", margen, y[0], grande);
                y[0] += 18;
                for (int k = 0; k < ENCABEZADOS.length; k++) {
                    String t = ENCABEZADOS[k];
                    c[0].drawText(t, k == 0 ? margen : xCol[k] - negrita.measureText(t), y[0], negrita);
                }
                y[0] += 4;
                c[0].drawLine(margen, y[0], ancho - margen, y[0], texto);
                y[0] += linea;
                for (Linea li : lineas) {
                    if (y[0] > alto - margen - 10) nueva.run();
                    Paint pinta = "A".equals(li.tipo) ? texto : negrita;
                    for (int k = 0; k < li.celdas.length; k++) {
                        String t = li.celdas[k];
                        if (k == 0) {
                            while (t.length() > 3 && pinta.measureText(t) > xCol[1] - 70 - margen) t = t.substring(0, t.length() - 2);
                            c[0].drawText(t, margen, y[0], pinta);
                        } else {
                            c[0].drawText(t, xCol[k] - pinta.measureText(t), y[0], pinta);
                        }
                    }
                    y[0] += linea;
                }
                y[0] += 14;
            }
            pdf.finishPage(p[0]);
            File f = new File(carpeta(ctx), "informe_inventario_" + sello() + ".pdf");
            try (FileOutputStream out = new FileOutputStream(f)) {
                pdf.writeTo(out);
            }
            compartir(ctx, f, "application/pdf");
        } catch (Exception e) {
            Toast.makeText(ctx, "No se generó el PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pdf.close();
        }
    }
}
