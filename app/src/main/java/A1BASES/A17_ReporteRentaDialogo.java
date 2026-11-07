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
 *   2b. ⭐ (7-nov, R6) Ajustes posteriores al cierre (solo con foto): documentos del año abierto
 *      marcados "afecta al año fiscal X". Opciones: Marcar documento · Quitar marca (por documento)
 *      · Comparativo actual · Comparativo entre años · Cancelar. La foto no cambia: la columna
 *      "Depurada" pasa a ser "Depurada y ajustada" (foto + ajustes, con la depuración).
 *   3a. Comparativo actual: sin depurar vs depurada (y ajustada), diferencia y %.
 *   3b. Comparativo entre años: año anterior (foto depurada) vs este año (depurado), dif. y %.
 *       Opciones en 3a y 3b: Exportar · Ver resumen · Volver a depurar · Pantalla anterior ·
 *       Volver al inicio · Salir.
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
        /** ⭐ (7-nov, R6) Saldo contrario por un ajuste y sin decisión de depuración. */
        boolean alerta;
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
        long saldo, ajuste;
        String naturaleza, codigoSinDepurar, codigoDepurado, decision;
    }

    private static final class Datos {
        final LinkedHashMap<String, Renglon> renglones = new LinkedHashMap<>();
        final LinkedHashMap<Long, Info> info = new LinkedHashMap<>();
        boolean vacio = true, hayAlerta;
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
    /** ⭐ (4-nov, Jorge) Pantalla desde la que se llegó a un comparativo ("Pantalla anterior"). */
    private Runnable pantallaAnterior = null;

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
            // ⭐ (7-nov, R6) Por si un documento marcado como ajuste se modificó en F1.
            if (conFoto) A1_1_AyudanteBD.refrescarAjustesRenta(db);
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

    /** ⭐ (7-nov, R6) ¿Hay documentos marcados como ajuste de este año? (solo con foto). */
    private boolean hayAjustes() {
        if (!conFoto) return false;
        SQLiteDatabase db = abrir();
        try {
            return !A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio).isEmpty();
        } finally {
            db.close();
        }
    }

    /** b2 (7-nov, Jorge): con ajustes, "depurada" se llama "depurada y ajustada". */
    private String estadoDepurada(boolean ajustes) {
        return ajustes ? "depurada y ajustada" : "depurada";
    }

    private String columnaDepurada(boolean ajustes) {
        return ajustes ? "Depurada y ajustada" : "Depurada";
    }

    // ── 1. Detalle sin depurar ──────────────────────────────────────────

    /** ⭐ (4-nov, Jorge) ¿Ya se depuró algo? Entonces la primera pantalla muestra la foto depurada. */
    private boolean hayDepuracion() {
        if (!conFoto) return !decisionesMemoria.isEmpty();
        SQLiteDatabase db = abrir();
        try {
            for (Object[] f : A1_1_AyudanteBD.filasFotoRenta(db, anio)) {
                if (f[6] != null) return true;
            }
            return false;
        } finally {
            db.close();
        }
    }

    private void pantallaDetalle() {
        boolean ajustes = hayAjustes();
        boolean depurada = hayDepuracion() || ajustes;
        Datos datos = calcularActual(depurada);
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo(depurada ? (ajustes ? "Detalle depurado y ajustado" : "Detalle depurado") : "Detalle sin depurar",
                (conFoto
                        ? "Los saldos congelados en el cierre del año " + anio + ". "
                        : "El año " + anio + " todavía no tiene cierre: estos son los saldos con los datos de hoy. ")
                        + (ajustes ? "Incluye los ajustes posteriores al cierre (foto + ajustes); la foto no cambia. " : "")
                        + (depurada
                        ? "Los saldos contrarios están en el renglón decidido. Para cambiarlo, "
                        + "\"Revisar depuración\"."
                        : "Cada cuenta está en su renglón original. Un saldo contrario (ej.: un banco "
                        + "sobregirado) resta dentro de su renglón; se corrige en \"Depurar saldos contrarios\".")));
        if (datos.hayAlerta) {
            raiz.addView(texto("⚠ Saldo contrario por un ajuste: depúrelo en \"Revisar depuración\".", false));
        }
        raiz.addView(tablaDetalle(datos));
        AlertDialog[] dialogo = new AlertDialog[1];
        List<String> textos = new ArrayList<>();
        List<Runnable> acciones = new ArrayList<>();
        textos.add(hayDepuracion() ? "Revisar depuración" : "Depurar saldos contrarios");
        acciones.add(() -> { dialogo[0].dismiss(); pantallaDepuracion(); });
        if (conFoto) {
            // ⭐ (7-nov, R6) Ajustes a este año cerrado, registrados después del cierre.
            textos.add(ajustes ? "Revisar ajustes" : "Ajustes posteriores al cierre");
            acciones.add(() -> { dialogo[0].dismiss(); pantallaAjustes(); });
        }
        textos.add("Ver resumen");
        acciones.add(() -> verResumen(datos));
        textos.add("Exportar y salir");
        acciones.add(() -> { exportarDetalle(depurada); dialogo[0].dismiss(); });
        textos.add("Cancelar");
        acciones.add(() -> dialogo[0].dismiss());
        raiz.addView(botones(textos.toArray(new String[0]), acciones.toArray(new Runnable[0])));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " " + (depurada ? estadoDepurada(ajustes) : "sin depurar"), raiz);
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
                .setCustomTitle(tituloCentrado("Renta fiscal " + nombreAnio() + " resumen"))
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
                // ⭐ (7-nov, R6) Contraria según el saldo corregido (foto + ajustes del año).
                Map<Long, Long> ajustes = ajustesPorCuenta(db, anio);
                for (Object[] f : A1_1_AyudanteBD.filasFotoRenta(db, anio)) {
                    long ajuste = cero(ajustes.get((Long) f[0]));
                    long saldo = (Long) f[3] + ajuste;
                    if (f[2] == null || !esContrario((String) f[4], saldo)) continue;
                    String nat = A1_1_AyudanteBD.naturalezaDeRenglon(db, (Long) f[2]);
                    if (!esNaturalezaDepurable(nat)) continue;
                    Long propuesto;
                    String motivo;
                    if (f[6] != null && f[5] != null) {
                        propuesto = (Long) f[5];
                        motivo = "decidido";
                    } else {
                        Object[] s = A1_1_AyudanteBD.sugerenciaDepuracion(db, anio, (Long) f[0], nat);
                        propuesto = (Long) s[0];
                        motivo = "sugerido: " + s[1];
                    }
                    if (ajuste != 0) motivo += " · con ajuste";
                    contrarias.add(new Object[]{f[0], f[1], saldo, f[2], nat, propuesto, motivo});
                }
                Collections.sort(contrarias, (x, y) -> Long.compare(Math.abs((Long) y[2]), Math.abs((Long) x[2])));
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
                        + "Con ajustes posteriores al cierre, cuenta el saldo corregido (foto + ajustes). "
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
                        () -> { guardar.run(); dialogo[0].dismiss(); pantallaAnterior = this::pantallaDepuracion; pantallaComparativoActual(); },
                        () -> { guardar.run(); dialogo[0].dismiss(); pantallaAnterior = this::pantallaDepuracion; pantallaComparativoAnios(); },
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
        String nombreFoto = A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getBaseName() + "_depurada"
                + A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getFileType().getExtension();
        if (A5_1_BackupManager.backupFotoRentaCSV(context, nombreFoto) && folderIdDrive != null) {
            A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombreFoto, folderIdDrive);
        }
    }

    // ── 2b. Ajustes posteriores al cierre (R6) ──────────────────────────

    /**
     * ⭐ NUEVO (7-nov, renta R6, diseño acordado con Jorge): documentos del año abierto marcados
     * como ajuste de este año cerrado. Cada marca se guarda al momento y sube el respaldo 425.
     */
    private void pantallaAjustes() {
        List<Object[]> partidas;
        SQLiteDatabase db = abrir();
        try {
            partidas = A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio);
        } finally {
            db.close();
        }
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Ajustes posteriores al cierre",
                "Correcciones del año " + anio + " que aparecieron después del cierre (ej.: un extracto que "
                        + "llegó tarde). El ajuste se registra en F1 como cualquier documento del año abierto: "
                        + "así la contabilidad queda corregida desde ese año. Aquí solo se marca \"afecta al año "
                        + "fiscal " + anio + "\": la renta de " + anio + " queda en foto + ajustes, y la foto no "
                        + "cambia. Un documento afecta un solo año. Si después se modifica en F1, el ajuste se "
                        + "actualiza solo; el cierre 421 siguiente lo deja fijo. Si un ajuste deja una cuenta con "
                        + "saldo contrario (⚠), se depura en \"Revisar depuración\"."));
        AlertDialog[] dialogo = new AlertDialog[1];
        if (partidas.isEmpty()) {
            raiz.addView(texto("No hay documentos marcados como ajuste del año " + anio + ".", false));
        }
        // Un bloque por documento: encabezado, sus partidas y "Quitar marca".
        String docActual = null;
        for (int i = 0; i < partidas.size(); i++) {
            Object[] p = partidas.get(i);
            String doc = (String) p[0];
            if (!doc.equals(docActual)) {
                docActual = doc;
                long total = 0;
                for (Object[] q : partidas) if (doc.equals(q[0]) && (Long) q[4] > 0) total += (Long) q[4];
                raiz.addView(texto("Doc. " + doc + " · " + fechaLegible((Integer) p[6]) + " · "
                        + (p[5] == null ? "" : p[5]) + "   $ " + formato.format(total), true));
                if (!(Boolean) p[7]) {
                    raiz.addView(texto("⚠ El documento ya no existe en las transacciones: quite la marca si se borró a propósito.", false));
                }
            }
            TextView partida = texto("      " + (p[3] == null ? "(cuenta sin nombre)" : p[3]) + "   $ "
                    + formato.format((Long) p[4]), false);
            partida.setTextSize(13);
            raiz.addView(partida);
            boolean ultimaDelDoc = i == partidas.size() - 1 || !doc.equals(partidas.get(i + 1)[0]);
            if (ultimaDelDoc) {
                Button quitar = new Button(context);
                quitar.setText("Quitar marca del doc. " + doc);
                quitar.setAllCaps(false);
                quitar.setOnClickListener(v -> {
                    SQLiteDatabase w = abrir();
                    try {
                        A1_1_AyudanteBD.quitarAjusteRenta(w, doc);
                    } finally {
                        w.close();
                    }
                    respaldarAjustes();
                    dialogo[0].dismiss();
                    pantallaAjustes();
                });
                raiz.addView(quitar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }
        raiz.addView(botones(
                new String[]{"Marcar documento", "Comparativo actual", "Comparativo entre años", "Cancelar"},
                new Runnable[]{
                        () -> elegirDocumentoAjuste(dialogo[0]),
                        () -> { dialogo[0].dismiss(); pantallaAnterior = this::pantallaAjustes; pantallaComparativoActual(); },
                        () -> { dialogo[0].dismiss(); pantallaAnterior = this::pantallaAjustes; pantallaComparativoAnios(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " ajustes", raiz);
    }

    /** Lista de documentos del año abierto aún sin marcar; al elegir uno, lo marca para este año. */
    private void elegirDocumentoAjuste(AlertDialog pantalla) {
        List<Object[]> docs;
        SQLiteDatabase db = abrir();
        try {
            docs = A1_1_AyudanteBD.documentosCandidatosAjuste(db);
        } finally {
            db.close();
        }
        if (docs.isEmpty()) {
            Toast.makeText(context, "No hay documentos sin marcar en las transacciones.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[docs.size()];
        for (int i = 0; i < items.length; i++) {
            Object[] d = docs.get(i);
            items[i] = "Doc. " + d[0] + " · " + fechaLegible((Integer) d[1]) + " · "
                    + (d[2] == null ? "" : d[2]) + " · $ " + formato.format((Long) d[3]);
        }
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("Marcar como ajuste de " + anio))
                .setItems(items, (dlg, i) -> {
                    SQLiteDatabase w = abrir();
                    try {
                        A1_1_AyudanteBD.marcarAjusteRenta(w, anio, (String) docs.get(i)[0]);
                    } finally {
                        w.close();
                    }
                    respaldarAjustes();
                    pantalla.dismiss();
                    pantallaAjustes();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Regenera el respaldo 425 (todos los ajustes) y lo sube a Drive. */
    private void respaldarAjustes() {
        String nombre = A5_1_BackupManager.backupAjustesRentaCSV(context);
        if (nombre != null && folderIdDrive != null) {
            A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombre, folderIdDrive);
        }
    }

    private static String fechaLegible(Integer aaaammdd) {
        if (aaaammdd == null || aaaammdd <= 0) return "sin fecha";
        return (aaaammdd % 100) + "/" + ((aaaammdd / 100) % 100) + "/" + (aaaammdd / 10000);
    }

    /** Suma de los ajustes del año por cuenta (signo contable). */
    private static Map<Long, Long> ajustesPorCuenta(SQLiteDatabase db, int a) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] p : A1_1_AyudanteBD.ajustesRentaDelAnio(db, a)) {
            if (p[2] == null) continue;
            m.put((Long) p[2], cero(m.get((Long) p[2])) + (Long) p[4]);
        }
        return m;
    }

    // ── 3. Comparativos ─────────────────────────────────────────────────

    private void pantallaComparativoActual() {
        boolean ajustes = hayAjustes();
        String colB = columnaDepurada(ajustes);
        List<FilaComp> filas = comparar(calcularActual(false), calcularActual(true));
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Comparativo actual",
                "Año " + anio + ": cada renglón y cuenta sin depurar y " + estadoDepurada(ajustes) + ", con la "
                        + "diferencia en valor y en porcentaje. Una cuenta depurada aparece en su renglón original "
                        + "(sale) y en el renglón decidido (entra). La depuración solo reubica: no cambia el "
                        + "patrimonio líquido" + (ajustes ? "; los ajustes posteriores al cierre sí lo cambian." : ".")));
        raiz.addView(tablaComparativo(filas, "Sin depurar", colB));
        accionesFinales(raiz, "Renta fiscal " + nombreAnio() + " comparativo actual", filas, "Sin depurar", colB);
    }

    private void pantallaComparativoAnios() {
        Datos anterior = calcularAnterior();
        List<FilaComp> filas = comparar(anterior, calcularActual(true));
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Comparativo entre años",
                "Año " + (anio - 1) + " (foto del cierre, depurada y con sus ajustes) frente al año " + anio
                        + " (depurado y con sus ajustes), con la "
                        + "diferencia en valor y en porcentaje. Sirve como planeación tributaria."));
        if (anterior.vacio) raiz.addView(texto("No hay foto del cierre de " + (anio - 1) + " para comparar.", false));
        raiz.addView(tablaComparativo(filas, String.valueOf(anio - 1), String.valueOf(anio)));
        accionesFinales(raiz, "Renta fiscal comparativo " + (anio - 1) + " – " + anio, filas,
                String.valueOf(anio - 1), String.valueOf(anio));
    }

    private void accionesFinales(LinearLayout raiz, String titulo, List<FilaComp> filas, String tituloA, String tituloB) {
        AlertDialog[] dialogo = new AlertDialog[1];
        // ⭐ (4-nov, Jorge) "Exportar" no cierra (para seguir); "Salir" cierra.
        Runnable anterior = pantallaAnterior != null ? pantallaAnterior : this::pantallaDepuracion;
        raiz.addView(botones(
                new String[]{"Exportar", "Ver resumen", "Volver a depurar", "Pantalla anterior",
                        "Volver al inicio", "Salir"},
                new Runnable[]{
                        this::exportarFinal,
                        () -> verResumenComparativo(titulo, filas, tituloA, tituloB),
                        () -> { dialogo[0].dismiss(); pantallaDepuracion(); },
                        () -> { dialogo[0].dismiss(); anterior.run(); },
                        () -> { dialogo[0].dismiss(); pantallaDetalle(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo(titulo, raiz);
    }

    /** ⭐ (4-nov, Jorge) Resumen de un comparativo: solo los renglones, con las dos fotos y la diferencia. */
    private void verResumenComparativo(String titulo, List<FilaComp> filas, String tituloA, String tituloB) {
        List<FilaComp> renglones = new ArrayList<>();
        for (FilaComp f : filas) if (f.esRenglon) renglones.add(f);
        View tabla = tablaComparativo(renglones, tituloA, tituloB);
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado(titulo + " · resumen"))
                .setView(tabla)
                .setPositiveButton("Cerrar", null)
                .show();
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
        // ⭐ (7-nov, R6) [7] = ajuste del año a la cuenta (solo en la vista depurada de una foto).
        List<Object[]> filas = new ArrayList<>();
        if (desdeFoto) {
            Map<Long, Long> ajustes = depurada ? ajustesPorCuenta(db, a) : new HashMap<>();
            Map<Long, String> nombresAjuste = new HashMap<>();
            if (depurada) {
                for (Object[] p : A1_1_AyudanteBD.ajustesRentaDelAnio(db, a)) {
                    if (p[2] != null) nombresAjuste.put((Long) p[2], (String) p[3]);
                }
            }
            for (Object[] f : A1_1_AyudanteBD.filasFotoRenta(db, a)) {
                long ajuste = cero(ajustes.remove((Long) f[0]));
                long corregido = (Long) f[3] + ajuste;
                Long efectivo = f[5] != null ? (Long) f[5] : (Long) f[2];
                String decision = f[6] != null ? "confirmada" : "";
                // Una decisión de depuración vale mientras la cuenta siga contraria: si un ajuste
                // la deja con saldo normal, vuelve a su renglón asignado.
                if (f[6] != null && ajuste != 0 && !esContrario((String) f[4], corregido)) {
                    efectivo = (Long) f[2];
                    decision = "sin efecto por ajuste";
                }
                filas.add(new Object[]{f[0], f[1], f[2], f[3], f[4], efectivo, decision, ajuste});
            }
            // Cuentas con ajuste que no estaban en la foto: van a su renglón actual.
            for (Map.Entry<Long, Long> e : ajustes.entrySet()) {
                Object[] rn = A1_1_AyudanteBD.renglonYNaturalezaDeCuenta(db, e.getKey());
                String nombre = nombresAjuste.get(e.getKey());
                filas.add(new Object[]{e.getKey(), nombre == null ? "Cuenta " + e.getKey() : nombre,
                        rn[0], 0L, rn[1], rn[0], "", e.getValue()});
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
                filas.add(new Object[]{r[0], r[1], renglon, r[3], r[4], depuradoId, decision, 0L});
            }
        }
        for (Object[] f : filas) {
            d.vacio = false;
            long ajuste = (Long) f[7];
            long saldo = (Long) f[3] + (depurada ? ajuste : 0);
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
            // ⭐ (7-nov, R6) ⚠ si el ajuste deja la cuenta contraria y no hay decisión que la depure.
            if (depurada && ajuste != 0 && ((String) f[6]).isEmpty() && esContrario((String) f[4], saldo)
                    && sinDep != null && esNaturalezaDepurable(sinDep.naturaleza)) {
                c.alerta = true;
                d.hayAlerta = true;
            }
            Info info = new Info();
            info.saldo = (Long) f[3];
            info.ajuste = ajuste;
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
                fc.addView(celda("      " + (c.alerta ? "⚠ " : "") + c.nombre, false, false));
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
                .setCustomTitle(tituloCentrado(titulo))
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

    /**
     * ⭐ (4-nov, Jorge) Título centrado y, fija arriba (fuera del desplazamiento), una barra que
     * avisa que las opciones para continuar están al final y lleva hasta ellas con un toque.
     */
    private AlertDialog dialogo(String titulo, View contenido) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(contenido);
        Button irAOpciones = new Button(context);
        irAOpciones.setText("▼  Las opciones para continuar están al final  ▼");
        irAOpciones.setAllCaps(false);
        irAOpciones.setOnClickListener(v -> scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)));
        LinearLayout marco = new LinearLayout(context);
        marco.setOrientation(LinearLayout.VERTICAL);
        marco.addView(irAOpciones, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        marco.addView(scroll);
        AlertDialog d = new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado(titulo))
                .setView(marco)
                .setCancelable(true)
                .create();
        d.show();
        return d;
    }

    private TextView tituloCentrado(String titulo) {
        TextView t = new TextView(context);
        t.setText(titulo);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(19);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.BLACK);
        t.setPadding(24, 28, 24, 12);
        return t;
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

    /** Paso 1: el detalle que se ve (depurado si ya se depuró). El CSV va completo. */
    private void exportarDetalle(boolean depurada) {
        boolean ajustes = hayAjustes();
        Datos sinDepurar = calcularActual(false);
        Datos dep = calcularActual(true);
        String estado = depurada ? estadoDepurada(ajustes) : "sin depurar";
        String titulo = "Renta fiscal " + nombreAnio() + " " + estado;
        String base = "424_Renta_" + anio + (conFoto ? "_foto_" : "_sin_cierre_") + estado.replace(" ", "_");
        List<Seccion> secciones = new ArrayList<>();
        secciones.add(seccionDetalle(depurada ? (ajustes ? "Detalle depurado y ajustado" : "Detalle depurado")
                : "Detalle sin depurar", depurada ? dep : sinDepurar));
        if (ajustes) secciones.add(seccionAjustes());
        exportar(base, titulo, secciones, sinDepurar, dep, calcularAnterior());
    }

    /** Pasos 3a/3b: detalle depurado + comparativo actual + comparativo entre años. */
    private void exportarFinal() {
        boolean ajustes = hayAjustes();
        Datos sinDepurar = calcularActual(false);
        Datos depurada = calcularActual(true);
        Datos anterior = calcularAnterior();
        String estado = estadoDepurada(ajustes);
        String titulo = "Renta fiscal " + nombreAnio() + " " + estado;
        String base = "424_Renta_" + anio + (conFoto ? "_foto_" : "_sin_cierre_") + estado.replace(" ", "_");
        List<Seccion> secciones = new ArrayList<>();
        secciones.add(seccionDetalle(ajustes ? "Detalle depurado y ajustado" : "Detalle depurado", depurada));
        if (ajustes) secciones.add(seccionAjustes());
        secciones.add(seccionComparativo("Comparativo actual: sin depurar y " + estado,
                comparar(sinDepurar, depurada), "Sin dep.", ajustes ? "Dep. y aj." : "Depurada"));
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

    /** ⭐ (7-nov, R6) Los documentos marcados como ajuste del año, con sus partidas (signo contable). */
    private Seccion seccionAjustes() {
        Seccion s = new Seccion("Ajustes posteriores al cierre (afectan a " + anio + ")",
                new String[]{"Documento / cuenta", "Valor"});
        SQLiteDatabase db = abrir();
        try {
            List<Object[]> partidas = A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio);
            String docActual = null;
            for (Object[] p : partidas) {
                String doc = (String) p[0];
                if (!doc.equals(docActual)) {
                    docActual = doc;
                    long total = 0;
                    for (Object[] q : partidas) if (doc.equals(q[0]) && (Long) q[4] > 0) total += (Long) q[4];
                    s.filas.add(new String[]{"R", "Doc. " + doc + " · " + fechaLegible((Integer) p[6]) + " · "
                            + (p[5] == null ? "" : p[5]), formato.format(total)});
                }
                s.filas.add(new String[]{"C", p[3] == null ? "" : (String) p[3], formato.format((Long) p[4])});
            }
        } finally {
            db.close();
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
            // ⭐ (4-nov, Jorge) encabezados: año, diferencia_valor, diferencia_%.
            // (4-nov, Jorge) "periodo" en vez de "año"/"anio".
            // ⭐ (7-nov, R6, b2) ajuste_contable; "depurado" pasa a "depurado_ajustado".
            w.write("periodo,origen,tipo_fila,codigo_sin_depurar,renglon_sin_depurar,codigo_depurado,renglon_depurado," +
                    "cuenta_id,cuenta,saldo_contable,ajuste_contable,naturaleza_tipo,valor_sin_depurar,valor_depurado_ajustado," +
                    "dif_depuracion_ajuste,decision,valor_periodo_anterior,diferencia_valor,diferencia_%\n");
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
                w.write(anio + "," + origen + ",resumen renglón," + cod + "," + limpio(textoRenglon(r)) + "," +
                        (depurada == null ? "," : cod + "," + limpio(textoRenglon(r))) + ",,,,,," +
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
            if (depurada != null) {
                for (Map.Entry<Long, String> e : nombresPorCuenta(depurada).entrySet()) nombres.putIfAbsent(e.getKey(), e.getValue());
            }
            Map<String, String> etiquetaPorCodigo = new HashMap<>();
            for (Renglon r : sinDepurar.renglones.values()) etiquetaPorCodigo.put(r.codigo, textoRenglon(r));
            if (depurada != null) for (Renglon r : depurada.renglones.values()) etiquetaPorCodigo.put(r.codigo, textoRenglon(r));
            // ⭐ (7-nov, R6) también las cuentas que solo aparecen por un ajuste (no estaban en la foto).
            LinkedHashMap<Long, Info> todas = new LinkedHashMap<>(sinDepurar.info);
            if (depurada != null) for (Map.Entry<Long, Info> e : depurada.info.entrySet()) todas.putIfAbsent(e.getKey(), e.getValue());
            for (Map.Entry<Long, Info> e : todas.entrySet()) {
                long id = e.getKey();
                Info inf = depurada != null && depurada.info.containsKey(id) ? depurada.info.get(id) : e.getValue();
                Long vs = sinPorCuenta.get(id), vd = depPorCuenta.get(id), va = anteriorPorCuenta.get(id);
                Double pct = depurada == null ? null : porcentaje(va, vd);
                String codS = inf.codigoSinDepurar, codD = inf.codigoDepurado;
                w.write(anio + "," + origen + ",cuenta," + codS + "," +
                        limpio(etiquetaPorCodigo.containsKey(codS) ? etiquetaPorCodigo.get(codS) : "") + "," +
                        (depurada == null ? "," : codD + "," + limpio(etiquetaPorCodigo.containsKey(codD) ? etiquetaPorCodigo.get(codD) : "")) + "," +
                        id + "," + limpio(nombres.get(id)) + "," + inf.saldo + "," +
                        (depurada == null ? "" : String.valueOf(inf.ajuste)) + "," + limpio(inf.naturaleza) + "," +
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

    /** "Renglón 29 · Total patrimonio bruto" (CSV). */
    private static String textoRenglon(Renglon r) {
        return SIN.equals(r.codigo) ? "Sin renglón asignado" : "Renglón " + r.etiqueta();
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
            final int ancho = 595, alto = 842, margen = 36, linea = 15, anchoValor = 72;
            Paint texto = new Paint();
            texto.setTextSize(10f);
            texto.setColor(Color.BLACK);
            Paint negrita = new Paint(texto);
            negrita.setTypeface(Typeface.DEFAULT_BOLD);
            Paint grande = new Paint(negrita);
            grande.setTextSize(13f);
            Paint seccionP = new Paint(negrita);
            seccionP.setTextSize(11.5f);
            // Puntos guía (gris claro) entre el nombre y su valor, para leer la fila sin perderse.
            Paint guia = new Paint(texto);
            guia.setColor(Color.GRAY);

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
                // Título de la sección y encabezados de columna; se repiten en cada página nueva
                // de la misma sección (con "(continuación)") para no perder qué es cada columna.
                Runnable encabezado = new Runnable() {
                    boolean primera = true;
                    @Override public void run() {
                        y[0] += 8;
                        c[0].drawText(primera ? s.titulo : s.titulo + " (continuación)", margen, y[0], seccionP);
                        primera = false;
                        y[0] += 16;
                        c[0].drawText(s.encabezados[0], margen, y[0], negrita);
                        for (int k = 0; k < columnas; k++) {
                            String t = s.encabezados[k + 1];
                            c[0].drawText(t, xValores + (k + 1) * anchoValor - negrita.measureText(t), y[0], negrita);
                        }
                        y[0] += 5;
                        c[0].drawLine(margen, y[0], ancho - margen, y[0], texto);
                        y[0] += linea;
                    }
                };
                encabezado.run();
                for (String[] f : s.filas) {
                    if (y[0] > alto - margen - 10) {
                        nuevaPagina.run();
                        encabezado.run();
                    }
                    Paint pinta = "R".equals(f[0]) ? negrita : texto;
                    String etiqueta = "R".equals(f[0]) ? f[1] : "    " + f[1];
                    while (etiqueta.length() > 3 && pinta.measureText(etiqueta) > xValores - margen - 6) {
                        etiqueta = etiqueta.substring(0, etiqueta.length() - 2);
                    }
                    c[0].drawText(etiqueta, margen, y[0], pinta);
                    if (columnas > 0) {
                        float desde = margen + pinta.measureText(etiqueta) + 4;
                        float hasta = xValores + anchoValor - pinta.measureText(f[2]) - 4;
                        StringBuilder puntos = new StringBuilder();
                        while (desde + guia.measureText(puntos + " .") <= hasta) puntos.append(" .");
                        if (puntos.length() > 0) c[0].drawText(puntos.toString(), desde, y[0], guia);
                    }
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
