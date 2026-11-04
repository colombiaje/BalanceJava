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
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import androidx.appcompat.app.AlertDialog;

/**
 * A17_ReporteRentaDialogo — renta fiscal como RECORRIDO GUIADO (4-nov, diseño de Jorge).
 * Entrada única: opción "6. Reporte renta fiscal" de F4 (la antigua opción 7 de depuración quedó
 * dentro de este recorrido).
 *
 *   1. Detalle del año, sin depurar (foto del cierre; o datos de hoy si el año aún no tiene
 *      cierre). Opciones al final: Depurar saldos contrarios · Ver resumen · Exportar y salir ·
 *      Cancelar.
 *   2. Depurar saldos contrarios: cada cuenta con saldo contrario a su tipo, con un destino
 *      sugerido. Opciones: Comparativo actual · Comparativo entre años · Cancelar. Seguir adelante
 *      guarda las decisiones (en la foto; si el año no tiene cierre, solo para este recorrido).
 *   3a. Comparativo actual: sin depurar vs depurada, diferencia y %.
 *   3b. Comparativo entre años: año anterior (foto depurada) vs este año (depurado), dif. y %.
 *       Opciones en 3a y 3b: Exportar y salir · Volver a depurar · Salir sin exportar.
 *
 * Un solo título por pantalla, sin subtítulos; las explicaciones, con el botón "i". Exportar crea
 * PDF y CSV (el CSV con más datos que el PDF) en la carpeta Balance y en Drive (carpeta de cierres).
 *
 * Signo: los renglones de naturaleza Pasivo o Ingreso se presentan con el signo invertido (Deudas
 * en positivo); los calculados evalúan su fórmula ("29-30"). Un saldo contrario resta en su renglón.
 */
public class A17_ReporteRentaDialogo {

    private static final String TAG = "A17_ReporteRenta";
    private static final String SIN = "\u0000sin";

    private static final class Cuenta {
        final long id;
        final String nombre;
        long valor;
        Cuenta(long id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    private static final class Renglon {
        final String codigo, nombre, naturaleza, formula;
        long total;
        final LinkedHashMap<String, Cuenta> cuentas = new LinkedHashMap<>();
        Renglon(String codigo, String nombre, String naturaleza, String formula) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.naturaleza = naturaleza;
            this.formula = formula;
        }
        String etiqueta() {
            return SIN.equals(codigo) ? "Sin renglón asignado" : codigo + " · " + nombre;
        }
    }

    /** Datos de una cuenta para el CSV: saldo contable, naturaleza, renglones y decisión. */
    private static final class Info {
        long saldo;
        String naturaleza, codigoSinDepurar, codigoDepurado, decision;
    }

    private static final class Datos {
        final LinkedHashMap<String, Renglon> renglones = new LinkedHashMap<>();
        final LinkedHashMap<Long, Info> info = new LinkedHashMap<>();
        boolean vacio = true;
    }

    /** Una fila de un comparativo. */
    private static final class FilaComp {
        final boolean esRenglon;
        final String etiqueta;
        final Long a, b;
        FilaComp(boolean esRenglon, String etiqueta, Long a, Long b) {
            this.esRenglon = esRenglon;
            this.etiqueta = etiqueta;
            this.a = a;
            this.b = b;
        }
    }

    private final DecimalFormat formato = new DecimalFormat("#,##0");
    private final DecimalFormat formatoPct = new DecimalFormat("#,##0.0");
    private Context context;
    private String folderIdDrive;
    private int anio;
    private boolean conFoto;
    private String pais = "CO", moneda = "COP";
    /** Decisiones de depuración de este recorrido si el año no tiene foto (cuenta → renglón). */
    private final Map<Long, Long> decisionesMemoria = new HashMap<>();

    public void mostrar(Context context, String folderIdDrive) {
        if (context == null) return;
        this.context = context;
        this.folderIdDrive = folderIdDrive;
        SQLiteDatabase db = abrir();
        try {
            int actual = A1_1_AyudanteBD.anioFiscalActual(db);
            List<Integer> fotos = A1_1_AyudanteBD.aniosConFotoRenta(db);
            // El año a declarar: la foto más reciente si es de este año o del anterior (el cierre
            // anual se hace al empezar el año); si no, el año en curso, aún sin cierre.
            if (!fotos.isEmpty() && fotos.get(0) >= actual - 1) {
                anio = fotos.get(0);
                conFoto = true;
            } else {
                anio = actual;
                conFoto = false;
            }
            pais = A1_1_AyudanteBD.paisConfigurado(db);
            android.database.Cursor c = db.rawQuery("SELECT moneda FROM " +
                    A1_1_AyudanteBD.TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
            if (c.moveToFirst()) moneda = c.getString(0);
            c.close();
        } finally {
            db.close();
        }
        pantallaDetalle();
    }

    private String nombreAnio() {
        return anio + (conFoto ? " foto" : " sin cierre");
    }

    // ── 1. Detalle sin depurar ──────────────────────────────────────────

    private void pantallaDetalle() {
        Datos sinDepurar = calcularActual(false);
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Detalle sin depurar",
                (conFoto
                        ? "Los saldos congelados en el cierre del año " + anio + ", cada cuenta en su renglón original. "
                        : "El año " + anio + " todavía no tiene cierre: estos son los saldos con los datos de hoy, "
                        + "cada cuenta en su renglón original. ")
                        + "Un saldo contrario (ej.: un banco sobregirado) resta dentro de su renglón; se corrige en "
                        + "\"Depurar saldos contrarios\"."));
        raiz.addView(tablaDetalle(sinDepurar));
        AlertDialog[] dialogo = new AlertDialog[1];
        raiz.addView(botones(
                new String[]{"Depurar saldos contrarios", "Ver resumen", "Exportar y salir", "Cancelar"},
                new Runnable[]{
                        () -> { dialogo[0].dismiss(); pantallaDepuracion(); },
                        () -> verResumen(sinDepurar),
                        () -> { exportarDetalle(sinDepurar); dialogo[0].dismiss(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " sin depurar", raiz);
    }

    private void verResumen(Datos datos) {
        TableLayout t = new TableLayout(context);
        t.setPadding(24, 12, 24, 12);
        for (Renglon r : datos.renglones.values()) {
            TableRow fila = new TableRow(context);
            fila.addView(celda(r.etiqueta(), true, false));
            fila.addView(celda("$ " + formato.format(r.total), true, true));
            t.addView(fila);
        }
        new AlertDialog.Builder(context)
                .setTitle("Renta fiscal " + nombreAnio() + " resumen")
                .setView(t)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    // ── 2. Depuración ───────────────────────────────────────────────────

    private void pantallaDepuracion() {
        // Contrarias: {cuenta_id, nombre, saldo, renglón asignado, naturaleza del renglón, propuesto, motivo}
        List<Object[]> contrarias = new ArrayList<>();
        List<Object[]> renglones;
        SQLiteDatabase db = abrir();
        try {
            renglones = A1_1_AyudanteBD.renglonesAsignablesEnAnio(db, anio);
            if (conFoto) {
                for (Object[] f : A1_1_AyudanteBD.cuentasConSaldoContrario(db, anio)) {
                    Long propuesto;
                    String motivo;
                    if (f[6] != null && f[5] != null) {
                        propuesto = (Long) f[5];
                        motivo = "decidido";
                    } else {
                        Object[] s = A1_1_AyudanteBD.sugerenciaDepuracion(db, anio, (Long) f[0], (String) f[4]);
                        propuesto = (Long) s[0];
                        motivo = "sugerido: " + s[1];
                    }
                    contrarias.add(new Object[]{f[0], f[1], f[2], f[3], f[4], propuesto, motivo});
                }
            } else {
                for (Object[] r : A1_1_AyudanteBD.saldosCrudosDelAnio(db, anio)) {
                    long saldo = (Long) r[3];
                    if (r[2] == null || !esContrario((String) r[4], saldo)) continue;
                    String nat = A1_1_AyudanteBD.naturalezaDeRenglon(db, (Long) r[2]);
                    if (!esNaturalezaDepurable(nat)) continue;
                    Long propuesto;
                    String motivo;
                    if (decisionesMemoria.containsKey((Long) r[0])) {
                        propuesto = decisionesMemoria.get((Long) r[0]);
                        motivo = "decidido";
                    } else {
                        Object[] s = A1_1_AyudanteBD.sugerenciaDepuracion(db, anio, (Long) r[0], nat);
                        propuesto = (Long) s[0];
                        motivo = "sugerido: " + s[1];
                    }
                    contrarias.add(new Object[]{r[0], r[1], saldo, r[2], nat, propuesto, motivo});
                }
                Collections.sort(contrarias, (x, y) -> Long.compare(Math.abs((Long) y[2]), Math.abs((Long) x[2])));
            }
        } finally {
            db.close();
        }

        List<Long> idsRenglon = new ArrayList<>();
        List<String> etiquetasRenglon = new ArrayList<>();
        for (Object[] r : renglones) {
            idsRenglon.add((Long) r[0]);
            etiquetasRenglon.add((String) r[1]);
        }

        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Depurar saldos contrarios",
                "Cuentas que terminaron el año con saldo contrario a su tipo de cuenta (ej.: un banco "
                        + "sobregirado). Para cada una viene un destino sugerido: la decisión del año anterior, "
                        + "o el renglón de la naturaleza contraria (activo negativo → Deudas). Puede elegir otro. "
                        + "Los saldos no cambian; solo el renglón donde se declaran. Al pasar a un comparativo, "
                        + "las decisiones se guardan" + (conFoto ? " en la foto." : " para este recorrido (el año aún no tiene cierre).")));
        List<Spinner> selectores = new ArrayList<>();
        if (contrarias.isEmpty()) {
            raiz.addView(texto("Ninguna cuenta terminó el año con saldo contrario.", false));
        }
        for (Object[] f : contrarias) {
            raiz.addView(texto(f[1] + "   $ " + formato.format((Long) f[2]), true));
            int posAsignado = idsRenglon.indexOf((Long) f[3]);
            TextView motivo = texto("Asignado: " + (posAsignado < 0 ? "(sin renglón vigente)" : etiquetasRenglon.get(posAsignado))
                    + " · " + f[6], false);
            motivo.setTextSize(12);
            raiz.addView(motivo);
            Spinner selector = new Spinner(context);
            selector.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, etiquetasRenglon));
            int pos = f[5] == null ? posAsignado : idsRenglon.indexOf((Long) f[5]);
            selector.setSelection(Math.max(0, pos));
            raiz.addView(selector);
            selectores.add(selector);
        }
        AlertDialog[] dialogo = new AlertDialog[1];
        Runnable guardar = () -> guardarDecisiones(contrarias, selectores, idsRenglon);
        raiz.addView(botones(
                new String[]{"Comparativo actual", "Comparativo entre años", "Cancelar"},
                new Runnable[]{
                        () -> { guardar.run(); dialogo[0].dismiss(); pantallaComparativoActual(); },
                        () -> { guardar.run(); dialogo[0].dismiss(); pantallaComparativoAnios(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " depuración", raiz);
    }

    private void guardarDecisiones(List<Object[]> contrarias, List<Spinner> selectores, List<Long> idsRenglon) {
        if (contrarias.isEmpty()) return;
        if (!conFoto) {
            for (int i = 0; i < contrarias.size(); i++) {
                int pos = selectores.get(i).getSelectedItemPosition();
                if (pos >= 0 && pos < idsRenglon.size()) decisionesMemoria.put((Long) contrarias.get(i)[0], idsRenglon.get(pos));
            }
            return;
        }
        SQLiteDatabase db = abrir();
        try {
            db.beginTransaction();
            try {
                for (int i = 0; i < contrarias.size(); i++) {
                    int pos = selectores.get(i).getSelectedItemPosition();
                    if (pos < 0 || pos >= idsRenglon.size()) continue;
                    A1_1_AyudanteBD.guardarDecisionDepuracion(db, anio, (Long) contrarias.get(i)[0], idsRenglon.get(pos));
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron guardar las decisiones: " + e.getMessage(), e);
            Toast.makeText(context, "Error al guardar las decisiones: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.close();
        }
        // El respaldo 423 (todas las fotos) se regenera y sube con las decisiones.
        String nombreFoto = A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getFileName();
        if (A5_1_BackupManager.backupFotoRentaCSV(context, nombreFoto) && folderIdDrive != null) {
            A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombreFoto, folderIdDrive);
        }
    }

    // ── 3. Comparativos ─────────────────────────────────────────────────

    private void pantallaComparativoActual() {
        List<FilaComp> filas = comparar(calcularActual(false), calcularActual(true));
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Comparativo actual",
                "Año " + anio + ": cada renglón y cuenta sin depurar y depurada, con la diferencia en valor y en "
                        + "porcentaje. Una cuenta depurada aparece en su renglón original (sale) y en el renglón "
                        + "decidido (entra). El patrimonio líquido no cambia: la depuración solo reubica."));
        raiz.addView(tablaComparativo(filas, "Sin depurar", "Depurada"));
        accionesFinales(raiz, "Renta fiscal " + nombreAnio() + " comparativo actual");
    }

    private void pantallaComparativoAnios() {
        Datos anterior = calcularAnterior();
        List<FilaComp> filas = comparar(anterior, calcularActual(true));
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Comparativo entre años",
                "Año " + (anio - 1) + " (foto del cierre, depurada) frente al año " + anio + " (depurado), con la "
                        + "diferencia en valor y en porcentaje. Sirve como planeación tributaria."));
        if (anterior.vacio) raiz.addView(texto("No hay foto del cierre de " + (anio - 1) + " para comparar.", false));
        raiz.addView(tablaComparativo(filas, String.valueOf(anio - 1), String.valueOf(anio)));
        accionesFinales(raiz, "Renta fiscal comparativo " + (anio - 1) + " – " + anio);
    }

    private void accionesFinales(LinearLayout raiz, String titulo) {
        AlertDialog[] dialogo = new AlertDialog[1];
        raiz.addView(botones(
                new String[]{"Exportar y salir", "Volver a depurar", "Salir sin exportar"},
                new Runnable[]{
                        () -> { exportarFinal(); dialogo[0].dismiss(); },
                        () -> { dialogo[0].dismiss(); pantallaDepuracion(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo(titulo, raiz);
    }

    // ── Cálculo ─────────────────────────────────────────────────────────

    private Datos calcularActual(boolean depurada) {
        SQLiteDatabase db = abrir();
        try {
            return calcular(db, anio, conFoto, depurada);
        } finally {
            db.close();
        }
    }

    /** Año anterior: su foto depurada, si existe. */
    private Datos calcularAnterior() {
        SQLiteDatabase db = abrir();
        try {
            if (!A1_1_AyudanteBD.existeFotoRenta(db, anio - 1)) return new Datos();
            return calcular(db, anio - 1, true, true);
        } finally {
            db.close();
        }
    }

    private Datos calcular(SQLiteDatabase db, int a, boolean desdeFoto, boolean depurada) {
        Datos d = new Datos();
        Map<Long, Renglon> porId = new HashMap<>();
        Map<String, Long> totales = new HashMap<>();
        for (Object[] r : A1_1_AyudanteBD.renglonesDelAnio(db, a)) {
            Renglon rg = new Renglon((String) r[1], (String) r[2], (String) r[3], (String) r[4]);
            d.renglones.put(rg.codigo, rg);
            porId.put((Long) r[0], rg);
        }
        // {cuenta_id, nombre, renglón sin depurar, saldo, naturaleza del tipo, renglón depurado, decisión}
        List<Object[]> filas = new ArrayList<>();
        if (desdeFoto) {
            for (Object[] f : A1_1_AyudanteBD.filasFotoRenta(db, a)) {
                Long efectivo = f[5] != null ? (Long) f[5] : (Long) f[2];
                filas.add(new Object[]{f[0], f[1], f[2], f[3], f[4], efectivo, f[6] != null ? "confirmada" : ""});
            }
        } else {
            for (Object[] r : A1_1_AyudanteBD.saldosCrudosDelAnio(db, a)) {
                Long renglon = (Long) r[2];
                Long depuradoId = renglon;
                String decision = "";
                if (renglon != null && esContrario((String) r[4], (Long) r[3])) {
                    String nat = A1_1_AyudanteBD.naturalezaDeRenglon(db, renglon);
                    if (esNaturalezaDepurable(nat)) {
                        if (decisionesMemoria.containsKey((Long) r[0])) {
                            depuradoId = decisionesMemoria.get((Long) r[0]);
                            decision = "decidida";
                        } else {
                            Object[] s = A1_1_AyudanteBD.sugerenciaDepuracion(db, a, (Long) r[0], nat);
                            if (s[0] != null) depuradoId = (Long) s[0];
                            decision = "sugerida";
                        }
                    }
                }
                filas.add(new Object[]{r[0], r[1], renglon, r[3], r[4], depuradoId, decision});
            }
        }
        for (Object[] f : filas) {
            d.vacio = false;
            long saldo = (Long) f[3];
            Renglon sinDep = (Long) f[2] == null ? null : porId.get((Long) f[2]);
            Renglon dep = (Long) f[5] == null ? null : porId.get((Long) f[5]);
            Renglon destino = depurada ? dep : sinDep;
            if (destino == null) {
                destino = d.renglones.get(SIN);
                if (destino == null) {
                    destino = new Renglon(SIN, "", "", null);
                    d.renglones.put(SIN, destino);
                }
            }
            long valor = A1_1_AyudanteBD.esNaturalezaDePresentacionNegativa(destino.naturaleza) ? -saldo : saldo;
            String nombre = String.valueOf(f[1]);
            Cuenta c = destino.cuentas.get(nombre);
            if (c == null) {
                c = new Cuenta((Long) f[0], nombre);
                destino.cuentas.put(nombre, c);
            }
            c.valor += valor;
            destino.total += valor;
            Info info = new Info();
            info.saldo = saldo;
            info.naturaleza = (String) f[4];
            info.codigoSinDepurar = sinDep == null ? "" : sinDep.codigo;
            info.codigoDepurado = dep == null ? "" : dep.codigo;
            info.decision = (String) f[6];
            d.info.put((Long) f[0], info);
        }
        for (Renglon r : d.renglones.values()) {
            if (!"Calculado".equals(r.naturaleza) && !SIN.equals(r.codigo)) totales.put(r.codigo, r.total);
        }
        for (Renglon r : d.renglones.values()) {
            if ("Calculado".equals(r.naturaleza)) r.total = A1_1_AyudanteBD.evaluarFormulaRenta(r.formula, totales);
        }
        // "Sin renglón" al final.
        Renglon sin = d.renglones.remove(SIN);
        if (sin != null) d.renglones.put(SIN, sin);
        return d;
    }

    private static boolean esContrario(String naturalezaTipo, long saldo) {
        return ("DEUDORA".equals(naturalezaTipo) && saldo < 0) || ("ACREEDORA".equals(naturalezaTipo) && saldo > 0);
    }

    private static boolean esNaturalezaDepurable(String naturalezaRenglon) {
        return "Activo".equals(naturalezaRenglon) || "Pasivo".equals(naturalezaRenglon)
                || "Ingreso".equals(naturalezaRenglon) || "Gasto".equals(naturalezaRenglon);
    }

    /** Une dos cálculos renglón por renglón y cuenta por cuenta (A = antes, B = después). */
    private static List<FilaComp> comparar(Datos a, Datos b) {
        LinkedHashSet<String> codigos = new LinkedHashSet<>(b.renglones.keySet());
        codigos.addAll(a.renglones.keySet());
        if (codigos.remove(SIN)) codigos.add(SIN);
        List<FilaComp> filas = new ArrayList<>();
        for (String codigo : codigos) {
            Renglon ra = a.renglones.get(codigo), rb = b.renglones.get(codigo);
            Renglon ref = rb != null ? rb : ra;
            filas.add(new FilaComp(true, ref.etiqueta(), ra == null ? null : ra.total, rb == null ? null : rb.total));
            TreeSet<String> nombres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (ra != null) nombres.addAll(ra.cuentas.keySet());
            if (rb != null) nombres.addAll(rb.cuentas.keySet());
            for (String n : nombres) {
                Cuenta ca = ra == null ? null : ra.cuentas.get(n);
                Cuenta cb = rb == null ? null : rb.cuentas.get(n);
                filas.add(new FilaComp(false, n, ca == null ? null : ca.valor, cb == null ? null : cb.valor));
            }
        }
        return filas;
    }

    private static long cero(Long v) {
        return v == null ? 0 : v;
    }

    /** Diferencia en % sobre el valor anterior; null si el anterior es 0. */
    private static Double porcentaje(Long a, Long b) {
        long base = cero(a);
        if (base == 0) return null;
        return (cero(b) - base) * 100.0 / Math.abs(base);
    }

    // ── Vistas ──────────────────────────────────────────────────────────

    private View tablaDetalle(Datos datos) {
        TableLayout t = new TableLayout(context);
        t.setPadding(0, 8, 0, 8);
        if (datos.vacio) {
            TableRow fila = new TableRow(context);
            fila.addView(celda("No hay saldos para el año " + anio + ".", false, false));
            t.addView(fila);
        }
        for (Renglon r : datos.renglones.values()) {
            TableRow fila = new TableRow(context);
            fila.addView(celda(r.etiqueta(), true, false));
            fila.addView(celda("$ " + formato.format(r.total), true, true));
            fila.setBackgroundColor(Color.parseColor("#DCE8F5"));
            t.addView(fila);
            for (Cuenta c : r.cuentas.values()) {
                TableRow fc = new TableRow(context);
                fc.addView(celda("      " + c.nombre, false, false));
                fc.addView(celda("$ " + formato.format(c.valor), false, true));
                t.addView(fc);
            }
        }
        HorizontalScrollView h = new HorizontalScrollView(context);
        h.addView(t);
        return h;
    }

    private View tablaComparativo(List<FilaComp> filas, String tituloA, String tituloB) {
        TableLayout t = new TableLayout(context);
        t.setPadding(0, 8, 0, 8);
        TableRow enc = new TableRow(context);
        enc.addView(celda("Renglón / cuenta", true, false));
        enc.addView(celda(tituloA, true, true));
        enc.addView(celda(tituloB, true, true));
        enc.addView(celda("Diferencia", true, true));
        enc.addView(celda("Dif. %", true, true));
        t.addView(enc);
        for (FilaComp f : filas) {
            TableRow fila = new TableRow(context);
            String[] v = valoresComparativo(f);
            fila.addView(celda(f.esRenglon ? f.etiqueta : "      " + f.etiqueta, f.esRenglon, false));
            for (int i = 0; i < v.length; i++) fila.addView(celda(v[i], f.esRenglon, true));
            if (f.esRenglon) fila.setBackgroundColor(Color.parseColor("#DCE8F5"));
            t.addView(fila);
        }
        HorizontalScrollView h = new HorizontalScrollView(context);
        h.addView(t);
        return h;
    }

    private String[] valoresComparativo(FilaComp f) {
        long dif = cero(f.b) - cero(f.a);
        Double pct = porcentaje(f.a, f.b);
        return new String[]{
                f.a == null ? "" : formato.format(f.a),
                f.b == null ? "" : formato.format(f.b),
                formato.format(dif),
                pct == null ? "—" : formatoPct.format(pct) + "%"};
    }

    private LinearLayout contenedor() {
        LinearLayout raiz = new LinearLayout(context);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(24, 8, 24, 8);
        return raiz;
    }

    /** Botón "i" a la derecha: muestra la explicación de la pantalla solo si se pide. */
    private View botonInfo(String titulo, String explicacion) {
        LinearLayout fila = new LinearLayout(context);
        fila.setGravity(Gravity.END);
        Button i = new Button(context);
        i.setText("i");
        i.setAllCaps(false);
        i.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle(titulo)
                .setMessage(explicacion)
                .setPositiveButton("Entendido", null)
                .show());
        fila.addView(i, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return fila;
    }

    /** Opciones al final de la pantalla, de a dos por fila. */
    private View botones(String[] textos, Runnable[] acciones) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 16, 0, 8);
        LinearLayout fila = null;
        for (int k = 0; k < textos.length; k++) {
            if (k % 2 == 0) {
                fila = new LinearLayout(context);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(fila);
            }
            Button b = new Button(context);
            b.setText(textos[k]);
            b.setAllCaps(false);
            Runnable accion = acciones[k];
            b.setOnClickListener(v -> accion.run());
            fila.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return col;
    }

    private AlertDialog dialogo(String titulo, View contenido) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(contenido);
        AlertDialog d = new AlertDialog.Builder(context)
                .setTitle(titulo)
                .setView(scroll)
                .setCancelable(true)
                .create();
        d.show();
        return d;
    }

    private TextView texto(String t, boolean negrita) {
        TextView v = new TextView(context);
        v.setText(t);
        v.setTextColor(Color.BLACK);
        v.setPadding(0, 10, 0, 2);
        if (negrita) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private TextView celda(String texto, boolean negrita, boolean derecha) {
        TextView t = new TextView(context);
        t.setText(texto);
        t.setPadding(10, 6, 16, 6);
        t.setTextColor(Color.BLACK);
        if (negrita) t.setTypeface(null, Typeface.BOLD);
        if (derecha) t.setGravity(Gravity.END);
        return t;
    }

    // ── Exportar ────────────────────────────────────────────────────────

    /** Paso 1: solo el detalle sin depurar. */
    private void exportarDetalle(Datos sinDepurar) {
        String titulo = "Renta fiscal " + nombreAnio() + " sin depurar";
        String base = "424_Renta_" + anio + (conFoto ? "_foto_sin_depurar" : "_sin_cierre_sin_depurar");
        List<Seccion> secciones = new ArrayList<>();
        secciones.add(seccionDetalle("Detalle sin depurar", sinDepurar));
        exportar(base, titulo, secciones, sinDepurar, null, new Datos());
    }

    /** Pasos 3a/3b: detalle depurado + comparativo actual + comparativo entre años. */
    private void exportarFinal() {
        Datos sinDepurar = calcularActual(false);
        Datos depurada = calcularActual(true);
        Datos anterior = calcularAnterior();
        String titulo = "Renta fiscal " + nombreAnio() + " depurada";
        String base = "424_Renta_" + anio + (conFoto ? "_foto_depurada" : "_sin_cierre_depurada");
        List<Seccion> secciones = new ArrayList<>();
        secciones.add(seccionDetalle("Detalle depurado", depurada));
        secciones.add(seccionComparativo("Comparativo actual: sin depurar y depurada",
                comparar(sinDepurar, depurada), "Sin dep.", "Depurada"));
        secciones.add(seccionComparativo("Comparativo entre años: " + (anio - 1) + " y " + anio
                        + (anterior.vacio ? " (no hay foto de " + (anio - 1) + ")" : ""),
                comparar(anterior, depurada), String.valueOf(anio - 1), String.valueOf(anio)));
        exportar(base, titulo, secciones, sinDepurar, depurada, anterior);
    }

    private static final class Seccion {
        final String titulo;
        final String[] encabezados;
        final List<String[]> filas = new ArrayList<>(); // [0] = "R" renglón / "C" cuenta
        Seccion(String titulo, String[] encabezados) {
            this.titulo = titulo;
            this.encabezados = encabezados;
        }
    }

    private Seccion seccionDetalle(String titulo, Datos d) {
        Seccion s = new Seccion(titulo, new String[]{"Renglón / cuenta", String.valueOf(anio)});
        for (Renglon r : d.renglones.values()) {
            s.filas.add(new String[]{"R", r.etiqueta(), formato.format(r.total)});
            for (Cuenta c : r.cuentas.values()) s.filas.add(new String[]{"C", c.nombre, formato.format(c.valor)});
        }
        return s;
    }

    private Seccion seccionComparativo(String titulo, List<FilaComp> filas, String a, String b) {
        Seccion s = new Seccion(titulo, new String[]{"Renglón / cuenta", a, b, "Diferencia", "Dif. %"});
        for (FilaComp f : filas) {
            String[] v = valoresComparativo(f);
            s.filas.add(new String[]{f.esRenglon ? "R" : "C", f.etiqueta, v[0], v[1], v[2], v[3]});
        }
        return s;
    }

    private void exportar(String base, String titulo, List<Seccion> secciones,
                          Datos sinDepurar, Datos depurada, Datos anterior) {
        File carpeta = new File(Environment.getExternalStorageDirectory(), "Balance");
        String nombreCsv = base + A9_1_FileType.CSV.getExtension();
        String nombrePdf = base + A9_1_FileType.PDF.getExtension();
        boolean csvOk = escribirCsv(new File(carpeta, nombreCsv), sinDepurar, depurada, anterior);
        boolean pdfOk = escribirPdf(new File(carpeta, nombrePdf), titulo, secciones);
        if (csvOk) A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombreCsv, folderIdDrive);
        if (pdfOk) A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombrePdf, folderIdDrive);
        Toast.makeText(context, (csvOk && pdfOk) ? "Exportado: " + base + " (PDF y CSV), en Balance y Drive"
                : "Error al exportar " + (csvOk ? "el PDF" : pdfOk ? "el CSV" : "el reporte"), Toast.LENGTH_LONG).show();
    }

    /**
     * CSV con más datos que el PDF: una fila por renglón (totales) y una por cuenta, con saldo
     * contable, naturaleza del tipo, renglón sin depurar y depurado, decisión, valores, diferencias y
     * el año anterior.
     */
    private boolean escribirCsv(File archivo, Datos sinDepurar, Datos depurada, Datos anterior) {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(archivo, false))) {
            w.write("anio,origen,tipo_fila,codigo_sin_depurar,renglon_sin_depurar,codigo_depurado,renglon_depurado," +
                    "cuenta_id,cuenta,saldo_contable,naturaleza_tipo,valor_sin_depurar,valor_depurado," +
                    "dif_depuracion,decision,valor_anio_anterior,dif_anios,dif_anios_pct\n");
            String origen = conFoto ? "foto_cierre" : "datos_hoy_sin_cierre";
            Datos ref = depurada != null ? depurada : sinDepurar;
            // Renglones
            LinkedHashSet<String> codigos = new LinkedHashSet<>(ref.renglones.keySet());
            codigos.addAll(sinDepurar.renglones.keySet());
            for (String codigo : codigos) {
                Renglon rs = sinDepurar.renglones.get(codigo);
                Renglon rd = depurada == null ? null : depurada.renglones.get(codigo);
                Renglon ra = anterior.renglones.get(codigo);
                Renglon r = rd != null ? rd : rs;
                String cod = SIN.equals(codigo) ? "" : codigo;
                Long vd = rd == null ? null : rd.total;
                Long va = ra == null ? null : ra.total;
                Double pct = depurada == null ? null : porcentaje(va, vd);
                w.write(anio + "," + origen + ",renglon," + cod + "," + limpio(r.etiqueta()) + "," +
                        (depurada == null ? "," : cod + "," + limpio(r.etiqueta())) + ",,,,," +
                        (rs == null ? "" : rs.total) + "," + (vd == null ? "" : vd) + "," +
                        (depurada == null ? "" : String.valueOf(cero(vd) - (rs == null ? 0 : rs.total))) + ",," +
                        (va == null ? "" : va) + "," +
                        (depurada == null ? "" : String.valueOf(cero(vd) - cero(va))) + "," +
                        (pct == null ? "" : String.format(java.util.Locale.ROOT, "%.1f", pct)) + "\n");
            }
            // Cuentas
            Map<Long, Long> anteriorPorCuenta = valoresPorCuenta(anterior);
            Map<Long, Long> sinPorCuenta = valoresPorCuenta(sinDepurar);
            Map<Long, Long> depPorCuenta = depurada == null ? new HashMap<>() : valoresPorCuenta(depurada);
            Map<Long, String> nombres = nombresPorCuenta(sinDepurar);
            Map<String, String> etiquetaPorCodigo = new HashMap<>();
            for (Renglon r : sinDepurar.renglones.values()) etiquetaPorCodigo.put(r.codigo, r.etiqueta());
            if (depurada != null) for (Renglon r : depurada.renglones.values()) etiquetaPorCodigo.put(r.codigo, r.etiqueta());
            for (Map.Entry<Long, Info> e : sinDepurar.info.entrySet()) {
                long id = e.getKey();
                Info inf = depurada != null && depurada.info.containsKey(id) ? depurada.info.get(id) : e.getValue();
                Long vs = sinPorCuenta.get(id), vd = depPorCuenta.get(id), va = anteriorPorCuenta.get(id);
                Double pct = depurada == null ? null : porcentaje(va, vd);
                String codS = inf.codigoSinDepurar, codD = inf.codigoDepurado;
                w.write(anio + "," + origen + ",cuenta," + codS + "," +
                        limpio(etiquetaPorCodigo.containsKey(codS) ? etiquetaPorCodigo.get(codS) : "") + "," +
                        (depurada == null ? "," : codD + "," + limpio(etiquetaPorCodigo.containsKey(codD) ? etiquetaPorCodigo.get(codD) : "")) + "," +
                        id + "," + limpio(nombres.get(id)) + "," + inf.saldo + "," + limpio(inf.naturaleza) + "," +
                        (vs == null ? "" : vs) + "," + (vd == null ? "" : vd) + "," +
                        (depurada == null ? "" : String.valueOf(cero(vd) - cero(vs))) + "," +
                        (depurada == null ? "" : limpio(inf.decision)) + "," + (va == null ? "" : va) + "," +
                        (depurada == null ? "" : String.valueOf(cero(vd) - cero(va))) + "," +
                        (pct == null ? "" : String.format(java.util.Locale.ROOT, "%.1f", pct)) + "\n");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al escribir el CSV: " + e.getMessage(), e);
            return false;
        }
    }

    private static Map<Long, Long> valoresPorCuenta(Datos d) {
        Map<Long, Long> m = new HashMap<>();
        for (Renglon r : d.renglones.values()) {
            for (Cuenta c : r.cuentas.values()) m.put(c.id, cero(m.get(c.id)) + c.valor);
        }
        return m;
    }

    private static Map<Long, String> nombresPorCuenta(Datos d) {
        Map<Long, String> m = new HashMap<>();
        for (Renglon r : d.renglones.values()) for (Cuenta c : r.cuentas.values()) m.put(c.id, c.nombre);
        return m;
    }

    /** PDF A4 vertical: un solo título; cada sección con su tabla; varias páginas si hace falta. */
    private boolean escribirPdf(File archivo, String titulo, List<Seccion> secciones) {
        PdfDocument pdf = new PdfDocument();
        try {
            final int ancho = 595, alto = 842, margen = 36, linea = 14, anchoValor = 72;
            Paint texto = new Paint();
            texto.setTextSize(9f);
            texto.setColor(Color.BLACK);
            Paint negrita = new Paint(texto);
            negrita.setTypeface(Typeface.DEFAULT_BOLD);
            Paint grande = new Paint(negrita);
            grande.setTextSize(13f);
            Paint seccionP = new Paint(negrita);
            seccionP.setTextSize(10.5f);

            int[] pagina = {0};
            Canvas[] c = {null};
            PdfDocument.Page[] p = {null};
            int[] y = {0};
            Runnable nuevaPagina = () -> {
                if (p[0] != null) pdf.finishPage(p[0]);
                pagina[0]++;
                p[0] = pdf.startPage(new PdfDocument.PageInfo.Builder(ancho, alto, pagina[0]).create());
                c[0] = p[0].getCanvas();
                y[0] = margen + 14;
                if (pagina[0] == 1) {
                    c[0].drawText(titulo, margen, y[0], grande);
                    y[0] += 22;
                }
                c[0].drawText(pais + " · " + moneda + " · " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",
                        java.util.Locale.ROOT).format(new java.util.Date()) + " · página " + pagina[0],
                        margen, alto - margen + 14, texto);
            };
            nuevaPagina.run();
            for (Seccion s : secciones) {
                int columnas = s.encabezados.length - 1;
                int xValores = ancho - margen - columnas * anchoValor;
                if (y[0] > alto - margen - 60) nuevaPagina.run();
                y[0] += 8;
                c[0].drawText(s.titulo, margen, y[0], seccionP);
                y[0] += 16;
                c[0].drawText(s.encabezados[0], margen, y[0], negrita);
                for (int k = 0; k < columnas; k++) {
                    String t = s.encabezados[k + 1];
                    c[0].drawText(t, xValores + (k + 1) * anchoValor - negrita.measureText(t), y[0], negrita);
                }
                y[0] += 5;
                c[0].drawLine(margen, y[0], ancho - margen, y[0], texto);
                y[0] += linea;
                for (String[] f : s.filas) {
                    if (y[0] > alto - margen - 10) {
                        nuevaPagina.run();
                        y[0] += linea;
                    }
                    Paint pinta = "R".equals(f[0]) ? negrita : texto;
                    String etiqueta = "R".equals(f[0]) ? f[1] : "    " + f[1];
                    while (etiqueta.length() > 3 && pinta.measureText(etiqueta) > xValores - margen - 6) {
                        etiqueta = etiqueta.substring(0, etiqueta.length() - 2);
                    }
                    c[0].drawText(etiqueta, margen, y[0], pinta);
                    for (int k = 0; k < columnas; k++) {
                        String v = f[k + 2];
                        c[0].drawText(v, xValores + (k + 1) * anchoValor - pinta.measureText(v), y[0], pinta);
                    }
                    y[0] += linea;
                }
                y[0] += 10;
            }
            pdf.finishPage(p[0]);
            try (FileOutputStream salida = new FileOutputStream(archivo, false)) {
                pdf.writeTo(salida);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al escribir el PDF: " + e.getMessage(), e);
            return false;
        } finally {
            pdf.close();
        }
    }

    private static String limpio(String v) {
        return v == null ? "" : v.replace(",", " ").replace("\n", " ");
    }

    private SQLiteDatabase abrir() {
        return new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF)
                .getWritableDatabase();
    }
}
