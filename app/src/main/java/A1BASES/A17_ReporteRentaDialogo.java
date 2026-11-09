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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
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
 *   2b. ⭐ (7-nov, R6, diseño de Jorge: de lo fiscal a lo contable) Ajustes posteriores al cierre
 *      (solo con foto): sesiones de ajuste fiscal. Cada sesión corrige uno o varios valores del
 *      reporte (cuenta, valor correcto, descripción, contrapartida) y genera UN documento contable
 *      como borrador en F1 › Nuevo, con fecha del primer día del año fiscal siguiente. La foto no
 *      cambia: la columna "Depurada" pasa a ser "Depurada y ajustada" (foto + ajustes). Opciones:
 *      Nueva sesión · Comparativo actual · Comparativo entre años · Cancelar; por sesión,
 *      Generar/Regenerar borrador y Quitar sesión. Nada queda a medias: al entrar a la opción 6 se
 *      avisa de toda sesión sin borrador o sin documento guardado.
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
    /** ⭐ (8-nov) Filtro de la lista de sesiones: "Sin terminar", "Todas" o "Sesión N" (null = automático). */
    private String filtroAjustes = null;

    public void mostrar(Context context, String folderIdDrive) {
        if (preparar(context, folderIdDrive)) pantallaDetalle();
    }

    /**
     * ⭐ NUEVO (8-nov, renta R6, pedido de Jorge): abre la opción 6 directo en los ajustes (al
     * volver de F1 después de guardar el documento de un ajuste fiscal).
     */
    public void mostrarEnAjustes(Context context, String folderIdDrive) {
        if (!preparar(context, folderIdDrive)) return;
        if (conFoto) entrarAjustes(); else pantallaDetalle();
    }

    private boolean preparar(Context context, String folderIdDrive) {
        if (context == null) return false;
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
            // ⭐ (7-nov, R6) ¿Ya se guardó en F1 el documento de alguna sesión de ajuste?
            A1_1_AyudanteBD.actualizarDocumentosDeAjuste(db);
            pais = A1_1_AyudanteBD.paisConfigurado(db);
            android.database.Cursor c = db.rawQuery("SELECT moneda FROM " +
                    A1_1_AyudanteBD.TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
            if (c.moveToFirst()) moneda = c.getString(0);
            c.close();
        } finally {
            db.close();
        }
        return true;
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
            acciones.add(() -> { dialogo[0].dismiss(); entrarAjustes(); });
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

    private static final int COLOR_PARTIDA = Color.parseColor("#0D47A1");        // azul
    private static final int COLOR_CONTRAPARTIDA = Color.parseColor("#6A1B9A");  // morado

    /**
     * ⭐ (8-nov, Jorge) Entrada a los ajustes desde el Detalle: si hay sesiones sin terminar, el
     * aviso aparece aquí (no al abrir la opción 6, que puede ser por otro motivo).
     */
    private void entrarAjustes() {
        filtroAjustes = null;
        pantallaAjustes();
        int sinTerminar = 0;
        Integer enF1;
        SQLiteDatabase db = abrir();
        try {
            Integer anterior = null;
            for (Object[] r : A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio)) {
                if (r[13] == null && !((Integer) r[0]).equals(anterior)) sinTerminar++;
                anterior = (Integer) r[0];
            }
            enF1 = A1_1_AyudanteBD.sesionConBorradorEnF1(db);
        } finally {
            db.close();
        }
        if (sinTerminar == 0) return;
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("⚠ Ajuste fiscal sin terminar"))
                .setView(alerta("Hay " + sinTerminar + (sinTerminar == 1 ? " sesión" : " sesiones")
                        + " de ajuste fiscal sin terminar (sin borrador en F1 o con el borrador sin guardar).\n\n"
                        + "El reporte ya está corregido, pero la contabilidad todavía no. Termine la sesión para "
                        + "que las dos queden iguales.", true))
                .setNegativeButton("Continuar", null);
        if (enF1 != null) {
            b.setPositiveButton("Ir a F1 › Nuevo", (d, w) -> irAF1Nuevo());
        }
        b.show();
    }

    /**
     * ⭐ (8-nov, Jorge) Abre F1 en el área Nuevo, que restaura sola el borrador (F1 abre en el área
     * guardada en sus preferencias: se deja en Nuevo y se navega como el menú lateral).
     */
    private void irAF1Nuevo() {
        cerrarDialogos();
        context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE).edit()
                .putInt("lastSelectedRadioButtonId", com.jj.appbalancev31.R.id.create_XRb)
                .putInt("areaGuardadaCanalA", com.jj.appbalancev31.R.id.create_XRb)
                .apply();
        if (context instanceof C_ACTIVITYS.C2_ActivityMenu) {
            ((C_ACTIVITYS.C2_ActivityMenu) context).cambiarElFragmentPredeterminado();
        } else {
            Toast.makeText(context, "Abra F1 desde el menú: quedará en Nuevo con el borrador.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * ⭐ NUEVO (7-nov, renta R6, diseño de Jorge): sesiones de ajuste fiscal del año, con filtro
     * (Sin terminar · Todas · Sesión N) y su estado.
     */
    private void pantallaAjustes() {
        List<Object[]> registros;
        Map<String, List<Object[]>> contras;
        Integer sesionEnF1;
        boolean f1Ocupado = A5_CacheManager.tieneTrabajoPendienteReal(context, A1_1_AyudanteBD.AREA_CREATE);
        SQLiteDatabase db = abrir();
        try {
            A1_1_AyudanteBD.actualizarDocumentosDeAjuste(db);
            registros = A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio);
            contras = A1_1_AyudanteBD.contrapartidasDelAnio(db, anio);
            sesionEnF1 = f1Ocupado ? A1_1_AyudanteBD.sesionConBorradorEnF1(db) : null;
        } finally {
            db.close();
        }
        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Ajustes posteriores al cierre",
                "Ya hecho el cierre, si encuentra un valor fiscal equivocado del año " + anio + ", lo corrige "
                        + "aquí en una sesión de ajuste: elige la cuenta y escribe el valor correcto (como se ve en "
                        + "el reporte) o el valor del ajuste, la explicación y una o varias contrapartidas. El "
                        + "reporte queda corregido al momento (foto + ajustes; la foto no cambia) y la sesión genera "
                        + "un documento contable como borrador en F1 › Nuevo, con fecha "
                        + fechaLegible(fechaDocumentoAjuste()) + ", para que lo revise y lo guarde: así la "
                        + "contabilidad del año siguiente parte de los valores correctos. Solo la cuenta corregida "
                        + "(azul) cambia la renta; las contrapartidas (morado) son contables. Si un ajuste deja una "
                        + "cuenta con saldo contrario (⚠), se depura en \"Revisar depuración\"."));
        AlertDialog[] dialogo = new AlertDialog[1];
        if (f1Ocupado && sesionEnF1 != null) {
            raiz.addView(alerta("⚠ El borrador de la sesión " + sesionEnF1 + " está en F1 › Nuevo: ábralo, revíselo y "
                    + "guárdelo.", true));
            raiz.addView(botones(new String[]{"Ir a F1 › Nuevo"}, new Runnable[]{this::irAF1Nuevo}));
        } else if (f1Ocupado) {
            raiz.addView(alerta("⚠ F1 › Nuevo tiene un documento sin terminar. Es muy recomendable terminarlo y "
                    + "guardarlo (o descartarlo) para poder continuar: el borrador del ajuste necesita ese espacio.", true));
            raiz.addView(botones(new String[]{"Ir a F1 › Nuevo"}, new Runnable[]{this::irAF1Nuevo}));
        }

        // Filtro: por defecto "Sin terminar" si hay alguna, si no "Todas".
        List<Integer> sesiones = new ArrayList<>();
        boolean haySinTerminar = false;
        for (Object[] r : registros) {
            if (!sesiones.contains((Integer) r[0])) sesiones.add((Integer) r[0]);
            if (r[13] == null) haySinTerminar = true;
        }
        if (filtroAjustes == null) filtroAjustes = haySinTerminar ? "Sin terminar" : "Todas";
        List<String> opciones = new ArrayList<>();
        opciones.add("Sin terminar");
        opciones.add("Todas");
        for (int k = sesiones.size() - 1; k >= 0; k--) opciones.add("Sesión " + sesiones.get(k));
        if (!opciones.contains(filtroAjustes)) filtroAjustes = "Todas";
        if (!registros.isEmpty()) {
            Spinner filtro = new Spinner(context);
            filtro.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, opciones));
            filtro.setSelection(opciones.indexOf(filtroAjustes));
            filtro.setOnItemSelectedListener(alSeleccionar(() -> {
                String elegido = opciones.get(filtro.getSelectedItemPosition());
                if (elegido.equals(filtroAjustes)) return;
                filtroAjustes = elegido;
                dialogo[0].dismiss();
                pantallaAjustes();
            }));
            raiz.addView(texto("Ver:", false));
            raiz.addView(filtro);
        }

        if (registros.isEmpty()) {
            raiz.addView(texto("No hay ajustes fiscales del año " + anio + ".", false));
        }
        // ⭐ (8-nov, Jorge) Cada sesión en una tabla de asientos (partida y contrapartidas por registro).
        int mostradas = 0;
        for (int i = 0; i < registros.size(); i++) {
            Object[] r = registros.get(i);
            int sesion = (Integer) r[0];
            boolean esPrimero = i == 0 || (Integer) registros.get(i - 1)[0] != sesion;
            if (!esPrimero) continue;
            boolean pasa = "Todas".equals(filtroAjustes)
                    || ("Sin terminar".equals(filtroAjustes) && r[13] == null)
                    || ("Sesión " + sesion).equals(filtroAjustes);
            if (!pasa) continue;
            mostradas++;
            raiz.addView(texto("Sesión " + sesion + " · " + (r[1] == null ? "" : r[1]), true));
            raiz.addView(estadoSesion(r));
            List<Asiento> asientos = new ArrayList<>();
            for (Object[] q : registros) {
                if ((Integer) q[0] != sesion) continue;
                Asiento a = new Asiento();
                a.titulo = q[4] + ": $ " + formato.format((Long) q[5]) + " → $ " + formato.format((Long) q[6]);
                a.descripcion = q[8] == null ? "" : (String) q[8];
                a.cuenta = (String) q[4];
                a.valor = (Long) q[7];
                List<Object[]> lista = contras.get(sesion + "-" + q[2]);
                if (lista != null) for (Object[] k : lista) a.contras.add(new Object[]{k[1], k[2]});
                asientos.add(a);
            }
            raiz.addView(tablaAsientos(asientos));
            raiz.addView(botonesSesion(r, dialogo));
        }
        if (!registros.isEmpty() && mostradas == 0) {
            raiz.addView(texto("No hay sesiones sin terminar.", false));
        }
        raiz.addView(botones(
                new String[]{"Nueva sesión de ajuste", "Comparativo actual", "Comparativo entre años", "Cancelar"},
                new Runnable[]{
                        () -> { dialogo[0].dismiss(); pantallaSesion(new ArrayList<>(), new Formulario()); },
                        () -> { dialogo[0].dismiss(); pantallaAnterior = this::pantallaAjustes; pantallaComparativoActual(); },
                        () -> { dialogo[0].dismiss(); pantallaAnterior = this::pantallaAjustes; pantallaComparativoAnios(); },
                        () -> dialogo[0].dismiss()}));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " ajustes", raiz);
    }

    /** ⭐ (8-nov) Un registro de ajuste para la tabla de asientos: partida + contrapartidas {nombre, valor}. */
    private static final class Asiento {
        String titulo, descripcion, cuenta;
        long valor;
        final List<Object[]> contras = new ArrayList<>();
        Runnable quitar;
    }

    private static final int FONDO_ALTERNO = Color.parseColor("#F1F3F4");
    private static final int FONDO_ENCABEZADO = Color.parseColor("#DCE8F5");
    /** ⭐ (8-nov, Jorge) Fucsia: donde el usuario debe llenar datos. */
    private static final int COLOR_ENTRADA = Color.parseColor("#D81B60");
    private static final int COLOR_ENTRADA_PISTA = Color.parseColor("#F06292");

    /**
     * ⭐ (8-nov, Jorge) Tabla de asientos, a lo ancho y sin desplazamiento: Ítem (registro.ítem) ·
     * Tipo · Descripción · Cuenta · Valor (+) (−). Tipo, Descripción y Cuenta se ajustan al ancho
     * y crecen en altura. Una fila de título por registro (▾/▸ para expandir o contraer, ✕ para
     * quitarlo si aplica); solo el último registro se ve expandido. Fondo alterno por registro,
     * partida en azul y contrapartidas en morado; al pie Total (+), Total (−) y Neto.
     */
    private View tablaAsientos(List<Asiento> asientos) {
        TableLayout t = new TableLayout(context);
        t.setPadding(0, 6, 0, 6);
        t.setColumnShrinkable(1, true);
        t.setColumnShrinkable(2, true);
        t.setColumnShrinkable(3, true);
        t.setColumnStretchable(3, true);
        TableRow enc = new TableRow(context);
        for (String c : new String[]{"Ítem", "Tipo", "Descripción", "Cuenta", "Valor (+) (−)", ""}) {
            enc.addView(celda(c, true, c.startsWith("Valor")));
        }
        enc.setBackgroundColor(FONDO_ENCABEZADO);
        t.addView(enc);
        long mas = 0, menos = 0;
        for (int i = 0; i < asientos.size(); i++) {
            Asiento a = asientos.get(i);
            int fondo = i % 2 == 0 ? Color.WHITE : FONDO_ALTERNO;
            boolean expandido = i == asientos.size() - 1;
            List<View> detalle = new ArrayList<>();
            TableRow titulo = new TableRow(context);
            TextView flecha = celda((expandido ? "▾ " : "▸ ") + (i + 1), true, false);
            titulo.addView(flecha);
            TextView tt = celda(a.titulo, true, false);
            TableRow.LayoutParams span = new TableRow.LayoutParams();
            span.span = 4;
            titulo.addView(tt, span);
            if (a.quitar != null) {
                Button x = new Button(context);
                x.setText("✕");
                x.setOnClickListener(v -> a.quitar.run());
                titulo.addView(x);
            } else {
                titulo.addView(celda("", false, false));
            }
            titulo.setBackgroundColor(fondo);
            final int reg = i + 1;
            titulo.setOnClickListener(v -> {
                boolean ver = detalle.isEmpty() || detalle.get(0).getVisibility() != View.VISIBLE;
                for (View d : detalle) d.setVisibility(ver ? View.VISIBLE : View.GONE);
                flecha.setText((ver ? "▾ " : "▸ ") + reg);
            });
            t.addView(titulo);
            int item = 0;
            detalle.add(filaAsiento(reg + "." + (++item), "Partida", a.descripcion, a.cuenta, a.valor, COLOR_PARTIDA, fondo));
            if (a.valor > 0) mas += a.valor; else menos += a.valor;
            for (int k = 0; k < a.contras.size(); k++) {
                Object[] c = a.contras.get(k);
                long v = (Long) c[1];
                detalle.add(filaAsiento(reg + "." + (++item), "Contrapartida " + (k + 1), a.descripcion, (String) c[0], v,
                        COLOR_CONTRAPARTIDA, fondo));
                if (v > 0) mas += v; else menos += v;
            }
            for (View d : detalle) {
                d.setVisibility(expandido ? View.VISIBLE : View.GONE);
                t.addView(d);
            }
        }
        t.addView(filaTotal("Total (+)", formato.format(mas), Color.BLACK, true));
        t.addView(filaTotal("Total (−)", formato.format(menos), Color.BLACK, true));
        long neto = mas + menos;
        t.addView(filaTotal("Neto", neto == 0 ? "0" : "Falta por registrar: " + formato.format(Math.abs(neto)),
                Color.parseColor(neto == 0 ? "#1B5E20" : "#B71C1C"), false));
        t.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TableRow filaAsiento(String item, String tipo, String descripcion, String cuenta, long valor,
                                 int color, int fondo) {
        TableRow f = new TableRow(context);
        String[] celdas = {item, tipo, descripcion, cuenta, firmado(valor), ""};
        for (int i = 0; i < celdas.length; i++) {
            TextView c = celda(celdas[i], false, i == 4);
            c.setTextColor(color);
            f.addView(c);
        }
        f.setBackgroundColor(fondo);
        return f;
    }

    /** Fila del pie: etiqueta en la columna Cuenta y el valor en la de Valor. */
    private TableRow filaTotal(String etiqueta, String valor, int color, boolean fondo) {
        TableRow f = new TableRow(context);
        f.addView(celda("", false, false));
        f.addView(celda("", false, false));
        f.addView(celda("", false, false));
        f.addView(celda(etiqueta, true, false));
        TextView v = celda(valor, true, true);
        v.setTextColor(color);
        f.addView(v);
        f.addView(celda("", false, false));
        if (fondo) f.setBackgroundColor(FONDO_ENCABEZADO);
        return f;
    }

    /** Franja de color que separa zonas de la pantalla. */
    private View franja() {
        View v = new View(context);
        v.setBackgroundColor(Color.parseColor("#7E57C2"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8);
        lp.setMargins(0, 18, 0, 18);
        v.setLayoutParams(lp);
        return v;
    }

    /** Spinner con el texto elegido en fucsia (campo que el usuario debe llenar). */
    private Spinner spinnerEntrada(List<String> opciones, int seleccion) {
        Spinner sp = new Spinner(context);
        sp.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, opciones) {
            @Override
            public View getView(int posicion, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(posicion, convertView, parent);
                if (v instanceof TextView) ((TextView) v).setTextColor(COLOR_ENTRADA);
                return v;
            }
        });
        sp.setSelection(seleccion);
        return sp;
    }

    private EditText campoEntrada(String pista, String texto) {
        EditText e = new EditText(context);
        e.setHint(pista);
        e.setText(texto);
        e.setTextColor(COLOR_ENTRADA);
        e.setHintTextColor(COLOR_ENTRADA_PISTA);
        return e;
    }

    /** Estado de la sesión (con el primer registro): contabilizada, borrador en F1, perdido o pendiente. */
    private TextView estadoSesion(Object[] r) {
        if (r[13] != null) {
            TextView ok = texto("✓ Contabilizada: documento N° " + r[13] + " del " + fechaLegible((Integer) r[11]), false);
            ok.setTextColor(Color.parseColor("#1B5E20"));
            return ok;
        }
        if (!(Boolean) r[12]) {
            return alerta("⚠ Pendiente: falta generar el borrador en F1 › Nuevo.", false);
        }
        return borradorEnF1(r)
                ? alerta("⚠ Borrador en F1 › Nuevo sin guardar: revíselo y guárdelo en F1.", false)
                : alerta("⚠ El borrador ya no está en F1 › Nuevo y no se encontró el documento guardado: "
                + "regenérelo.", false);
    }

    private View botonesSesion(Object[] r, AlertDialog[] dialogo) {
        int sesion = (Integer) r[0];
        List<String> textos = new ArrayList<>();
        List<Runnable> acciones = new ArrayList<>();
        if (r[13] == null) {
            if ((Boolean) r[12] && borradorEnF1(r)) {
                // El borrador existe: lo que falta es guardarlo en F1.
                textos.add("Ir a F1 › Nuevo");
                acciones.add(this::irAF1Nuevo);
            } else {
                textos.add((Boolean) r[12] ? "Regenerar borrador" : "Generar borrador");
                acciones.add(() -> {
                    if (A5_CacheManager.tieneTrabajoPendienteReal(context, A1_1_AyudanteBD.AREA_CREATE)) {
                        avisoF1Ocupado();
                        return;
                    }
                    generarBorrador(sesion);
                    dialogo[0].dismiss();
                    pantallaAjustes();
                });
            }
        }
        textos.add("Quitar sesión " + sesion);
        acciones.add(() -> confirmarQuitarSesion(r, dialogo[0]));
        return botones(textos.toArray(new String[0]), acciones.toArray(new Runnable[0]));
    }

    private boolean borradorEnF1(Object[] r) {
        SQLiteDatabase db = abrir();
        try {
            return A1_1_AyudanteBD.borradorDeAjusteEnF1(db, (Integer) r[11], (String) r[8]);
        } finally {
            db.close();
        }
    }

    private void confirmarQuitarSesion(Object[] r, AlertDialog pantalla) {
        int sesion = (Integer) r[0];
        String contable = r[13] != null
                ? "Su documento contable N° " + r[13] + " sigue en las transacciones: revíselo en F1 y bórrelo si "
                + "corresponde, para que no quede un registro contable sin su ajuste fiscal."
                : borradorEnF1(r) ? "Su borrador sigue en F1 › Nuevo: descártelo allí."
                : "No tiene documento contable guardado.";
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("Quitar la sesión " + sesion))
                .setMessage("El reporte fiscal vuelve a los valores sin esta sesión. " + contable + "\n\n¿Quitarla?")
                .setPositiveButton("Quitar", (d, w) -> {
                    SQLiteDatabase db = abrir();
                    try {
                        A1_1_AyudanteBD.quitarSesionAjuste(db, sesion);
                    } finally {
                        db.close();
                    }
                    respaldarAjustes();
                    pantalla.dismiss();
                    pantallaAjustes();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void avisoF1Ocupado() {
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("⚠ F1 › Nuevo está ocupado"))
                .setView(alerta("F1 › Nuevo tiene un documento sin terminar. Termínelo y guárdelo (o descártelo) "
                        + "y vuelva aquí a generar el borrador. El ajuste fiscal ya quedó guardado y la sesión "
                        + "queda pendiente, con aviso cada vez que entre a los ajustes.", true))
                .setPositiveButton("Ir a F1 › Nuevo", (d, w) -> irAF1Nuevo())
                .setNegativeButton("Entendido", null)
                .show();
    }

    /** Fecha del documento contable de ajuste: primer día del año fiscal siguiente (AAAAMMDD). */
    private int fechaDocumentoAjuste() {
        SQLiteDatabase db = abrir();
        try {
            return A1_1_AyudanteBD.limitesAnioFiscal(db, anio + 1)[0];
        } finally {
            db.close();
        }
    }

    // ── 2c. Sesión de ajuste ────────────────────────────────────────────

    /** Datos de apoyo de una cuenta: valor que se ve en el reporte, su renglón y naturalezas. */
    private static final class Apoyo {
        long cuentaId, valorReporte;
        String nombre, renglon = "Sin renglón asignado", naturalezaRenglon, naturalezaTipo;
        Long tipoCuentaId;
        String tipoNombre;
    }

    /**
     * Lo que el usuario lleva escrito en el registro nuevo; sobrevive al redibujar la pantalla
     * (al agregar o quitar una contrapartida). contras: {posición del spinner (Integer), valor (String)}.
     */
    private static final class Formulario {
        int cuenta = 0;
        boolean porValor = false;
        String valor = "", descripcion = "";
        final List<Object[]> contras = new ArrayList<>();
    }

    private List<Apoyo> apoyoCuentas() {
        Datos actual = calcularActual(true);
        Map<Long, Long> valores = new HashMap<>();
        Map<Long, Renglon> renglonDe = new HashMap<>();
        for (Renglon r : actual.renglones.values()) {
            for (Cuenta c : r.cuentas.values()) {
                valores.put(c.id, cero(valores.get(c.id)) + c.valor);
                renglonDe.put(c.id, r);
            }
        }
        List<Apoyo> lista = new ArrayList<>();
        SQLiteDatabase db = abrir();
        try {
            Map<Long, Object[]> porId = new HashMap<>();
            for (Object[] r : A1_1_AyudanteBD.renglonesDelAnio(db, anio)) porId.put((Long) r[0], r);
            for (Object[] c : A1_1_AyudanteBD.cuentasParaAjuste(db)) {
                Apoyo a = new Apoyo();
                a.cuentaId = (Long) c[0];
                a.nombre = (String) c[1];
                a.naturalezaTipo = (String) c[3];
                a.tipoCuentaId = (Long) c[4];
                a.tipoNombre = (String) c[5];
                a.valorReporte = cero(valores.get(a.cuentaId));
                Renglon r = renglonDe.get(a.cuentaId);
                if (r != null) {
                    a.renglon = r.etiqueta();
                    a.naturalezaRenglon = r.naturaleza;
                } else if (c[2] != null && porId.containsKey((Long) c[2])) {
                    Object[] rr = porId.get((Long) c[2]);
                    a.renglon = rr[1] + " · " + rr[2];
                    a.naturalezaRenglon = (String) rr[3];
                }
                lista.add(a);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    /** Diferencia contable (signo de c5_Valor) a partir de la diferencia que se ve en el reporte. */
    private static long diferenciaContable(Apoyo a, long correcto) {
        long vista = correcto - a.valorReporte;
        return A1_1_AyudanteBD.esNaturalezaDePresentacionNegativa(a.naturalezaRenglon) ? -vista : vista;
    }

    /** Valor correcto según el modo: el escrito (corregir saldo) o reporte + X (suma algebraica). */
    private static Long valorCorrecto(Apoyo a, boolean porValor, Long escrito) {
        if (escrito == null) return null;
        return porValor ? a.valorReporte + escrito : escrito;
    }

    private static Long leerValor(String texto) {
        if (texto == null) return null;
        String t = texto.trim().replace("$", "").replace(".", "").replace(",", "").replace(" ", "");
        if (t.isEmpty() || "-".equals(t)) return null;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** La descripción viaja a F1 y a los CSV: sin comillas simples, comas ni saltos de línea. */
    private static String descripcionSegura(String t) {
        return t == null ? "" : t.trim().replace("'", "´").replace(",", " ").replace("\n", " ");
    }

    /**
     * ⭐ (8-nov, Jorge) Contrapartidas del formulario: cada una con su cuenta y su valor SIN signo
     * (la app le pone el opuesto al de la partida). Las filas vacías (sin cuenta ni valor) se
     * ignoran. @return {Apoyo, valor contable (Long)} por contrapartida, o null si una fila está
     * incompleta (mensaje en error[0]). No exige que cuadre: eso lo dice el neto.
     */
    private static List<Object[]> contrapartidasDelFormulario(Formulario f, List<Apoyo> cuentas, long dif, String[] error) {
        List<Object[]> salida = new ArrayList<>();
        long signo = dif > 0 ? -1 : 1;
        for (int k = 0; k < f.contras.size(); k++) {
            Object[] c = f.contras.get(k);
            int pos = (Integer) c[0];
            Long v = leerValor((String) c[1]);
            if (pos <= 0 && v == null) continue;
            if (pos <= 0) {
                error[0] = "Elija la cuenta de la contrapartida " + (k + 1) + ".";
                return null;
            }
            if (v == null || v == 0) {
                error[0] = "Escriba el valor de la contrapartida " + (k + 1) + ".";
                return null;
            }
            salida.add(new Object[]{cuentas.get(pos - 1), signo * Math.abs(v)});
        }
        return salida;
    }

    /**
     * ⭐ NUEVO (7-nov, renta R6; 8-nov: tablas, modos, varias contrapartidas, colores; afinado con
     * Jorge): una sesión de ajuste. Arriba, la tabla de los registros ya hechos (solo el último
     * expandido); una franja de color; abajo, el registro en curso: cuenta, modo, valor y
     * explicación (en fucsia, donde se llenan datos) y su tabla Ítem · Tipo · Cuenta · Valor (+) (−)
     * con la partida calculada y las contrapartidas (cuenta y valor sin signo). Mientras quede
     * saldo por registrar aparece sola una fila nueva; cada fila se puede quitar (✕). Al pie Total
     * (+), Total (−) y Neto: "Agregar registro" exige neto 0. Los registros se guardan todos juntos
     * al terminar la sesión. registros: {Apoyo cuenta, valor correcto (Long), diferencia contable
     * (Long), descripción, List de {Apoyo, valor contable (Long)} contrapartidas}.
     */
    private void pantallaSesion(List<Object[]> registros, Formulario form) {
        List<Apoyo> cuentas = apoyoCuentas();
        if (form.contras.isEmpty()) form.contras.add(new Object[]{0, ""});
        List<String> nombres = new ArrayList<>();
        nombres.add("(elija la cuenta)");
        for (Apoyo a : cuentas) nombres.add(a.nombre);

        LinearLayout raiz = contenedor();
        raiz.addView(botonInfo("Sesión de ajuste fiscal",
                "Agregue uno o varios registros. Cada uno corrige el valor fiscal de una cuenta en el año " + anio
                        + ", de dos formas: \"Corregir saldo\" (escribe el valor correcto, tal como debe verse en el "
                        + "reporte; las deudas en positivo) o \"Registrar valor\" (escribe el valor del ajuste, que se "
                        + "suma al del reporte: + aumenta lo que se ve, − lo disminuye). La app calcula la partida "
                        + "con su signo contable. En la tabla, cada contrapartida lleva su cuenta y su valor sin "
                        + "signo (la app le pone el opuesto); mientras quede saldo por registrar aparece una fila "
                        + "nueva. Lo que va en fucsia es lo que usted llena. Al pie, Total (+), Total (−) y Neto, que "
                        + "debe quedar en 0. Al terminar la sesión se guardan todos los registros, el reporte queda "
                        + "corregido y se genera un solo documento borrador en F1 › Nuevo con fecha "
                        + fechaLegible(fechaDocumentoAjuste()) + "."));

        // Registros ya hechos en esta sesión (aún sin guardar), en una sola tabla.
        AlertDialog[] dialogo = new AlertDialog[1];
        if (!registros.isEmpty()) {
            raiz.addView(texto("Registros hechos", true));
            List<Asiento> hechos = new ArrayList<>();
            for (int i = 0; i < registros.size(); i++) {
                Object[] g = registros.get(i);
                Apoyo a = (Apoyo) g[0];
                Asiento as = new Asiento();
                as.titulo = a.nombre + ": $ " + formato.format(a.valorReporte) + " → $ " + formato.format((Long) g[1]);
                as.descripcion = (String) g[3];
                as.cuenta = a.nombre;
                as.valor = (Long) g[2];
                @SuppressWarnings("unchecked")
                List<Object[]> cs = (List<Object[]>) g[4];
                for (Object[] c : cs) as.contras.add(new Object[]{((Apoyo) c[0]).nombre, c[1]});
                final int pos = i;
                as.quitar = () -> {
                    registros.remove(pos);
                    dialogo[0].dismiss();
                    pantallaSesion(registros, form);
                };
                hechos.add(as);
            }
            raiz.addView(tablaAsientos(hechos));
            raiz.addView(franja());
        }

        // Registro en curso.
        raiz.addView(texto("Registro " + (registros.size() + 1), true));
        TextView apoyo = texto("", false);
        apoyo.setTextSize(13);
        raiz.addView(apoyo);
        Spinner cuenta = spinnerEntrada(nombres, form.cuenta);
        raiz.addView(cuenta);
        TextView detalleCuenta = texto("", false);
        detalleCuenta.setTextSize(13);
        raiz.addView(detalleCuenta);
        List<String> modos = new ArrayList<>();
        modos.add("Corregir saldo (valor correcto)");
        modos.add("Registrar valor (se suma al del reporte)");
        Spinner modo = spinnerEntrada(modos, form.porValor ? 1 : 0);
        raiz.addView(modo);
        EditText valor = campoEntrada("", form.valor);
        valor.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        raiz.addView(valor);
        EditText descripcion = campoEntrada("Explicación breve del ajuste", form.descripcion);
        raiz.addView(descripcion);

        // Tabla del registro en curso: Ítem · Tipo · Cuenta · Valor (+) (−) · ✕ (a lo ancho).
        TableLayout tabla = new TableLayout(context);
        tabla.setColumnShrinkable(1, true);
        tabla.setColumnShrinkable(2, true);
        tabla.setColumnStretchable(2, true);
        tabla.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TableRow enc = new TableRow(context);
        for (String c : new String[]{"Ítem", "Tipo", "Cuenta", "Valor (+) (−)", ""}) {
            enc.addView(celda(c, true, c.startsWith("Valor")));
        }
        enc.setBackgroundColor(FONDO_ENCABEZADO);
        tabla.addView(enc);
        TableRow filaPartida = new TableRow(context);
        TextView[] pc = new TextView[4];
        for (int i = 0; i < 4; i++) {
            pc[i] = celda(i == 0 ? "1" : i == 1 ? "Partida" : "", false, i == 3);
            pc[i].setTextColor(COLOR_PARTIDA);
            filaPartida.addView(pc[i]);
        }
        filaPartida.addView(celda("", false, false));
        tabla.addView(filaPartida);
        TableRow filaMas = filaTotalEdicion("Total (+)"), filaMenos = filaTotalEdicion("Total (−)"),
                filaNeto = filaTotalEdicion("Neto");
        filaNeto.setBackgroundColor(Color.TRANSPARENT);
        TextView totMas = (TextView) filaMas.getChildAt(3), totMenos = (TextView) filaMenos.getChildAt(3),
                neto = (TextView) filaNeto.getChildAt(3);
        raiz.addView(tabla);

        // Filas de contrapartida (se agregan y quitan en la tabla, sin redibujar la pantalla).
        // Cada una: {TableRow, Spinner, EditText valor, TextView ítem, TextView tipo}.
        List<Object[]> filas = new ArrayList<>();
        Runnable[] actualizar = new Runnable[1];
        Runnable renumerar = () -> {
            for (int k = 0; k < filas.size(); k++) {
                ((TextView) filas.get(k)[3]).setText(String.valueOf(k + 2));
                ((TextView) filas.get(k)[4]).setText("Contrapartida " + (k + 1));
            }
        };
        java.util.function.BiConsumer<Integer, String> agregarFila = (posCuenta, texto) -> {
            TableRow tr = new TableRow(context);
            TextView ci = celda("", false, false), ct = celda("", false, false);
            ci.setTextColor(COLOR_CONTRAPARTIDA);
            ct.setTextColor(COLOR_CONTRAPARTIDA);
            Spinner sp = spinnerEntrada(nombres, posCuenta);
            EditText ev = campoEntrada("valor", texto);
            ev.setInputType(InputType.TYPE_CLASS_NUMBER);
            ev.setGravity(Gravity.END);
            Button x = new Button(context);
            x.setText("✕");
            Object[] fila = {tr, sp, ev, ci, ct};
            x.setOnClickListener(v -> {
                tabla.removeView(tr);
                filas.remove(fila);
                renumerar.run();
                actualizar[0].run();
            });
            tr.addView(ci);
            tr.addView(ct);
            tr.addView(sp);
            tr.addView(ev);
            tr.addView(x);
            tabla.addView(tr, 2 + filas.size()); // después del encabezado y la partida
            filas.add(fila);
            sp.setOnItemSelectedListener(alSeleccionar(() -> actualizar[0].run()));
            ev.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence t, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable e) { actualizar[0].run(); }
            });
            renumerar.run();
        };
        for (Object[] c : form.contras) agregarFila.accept((Integer) c[0], (String) c[1]);
        tabla.addView(filaMas);
        tabla.addView(filaMenos);
        tabla.addView(filaNeto);

        // Copia lo escrito al formulario (para redibujar sin perder nada).
        Runnable guardarForm = () -> {
            form.cuenta = cuenta.getSelectedItemPosition();
            form.porValor = modo.getSelectedItemPosition() == 1;
            form.valor = valor.getText().toString();
            form.descripcion = descripcion.getText().toString();
            form.contras.clear();
            for (Object[] f : filas) {
                form.contras.add(new Object[]{((Spinner) f[1]).getSelectedItemPosition(),
                        ((EditText) f[2]).getText().toString()});
            }
        };
        actualizar[0] = () -> {
            guardarForm.run();
            valor.setHint(form.porValor ? "Valor del ajuste (+ o −)" : "Valor fiscal correcto");
            Apoyo a = form.cuenta > 0 ? cuentas.get(form.cuenta - 1) : null;
            pc[2].setText(a == null ? "" : a.nombre);
            apoyo.setText(a == null ? "Elija la cuenta cuyo valor fiscal va a corregir." : "Cuenta a corregir:");
            Long correcto = a == null ? null : valorCorrecto(a, form.porValor, leerValor(form.valor));
            long dif = correcto == null ? 0 : diferenciaContable(a, correcto);
            if (a == null) {
                detalleCuenta.setText("");
            } else {
                detalleCuenta.setText("En el reporte: $ " + formato.format(a.valorReporte) + "  ·  " + a.renglon + "  ·  "
                        + (a.naturalezaTipo == null ? "naturaleza sin dato" : a.naturalezaTipo.toLowerCase())
                        + (A1_1_AyudanteBD.esNaturalezaDePresentacionNegativa(a.naturalezaRenglon)
                        ? "  ·  (en este renglón el valor se ve en positivo; en la contabilidad va en negativo)" : "")
                        + (correcto == null ? "" : "\nReporte: $ " + formato.format(a.valorReporte) + " → $ "
                        + formato.format(correcto) + "  (" + firmado(correcto - a.valorReporte) + ")"));
            }
            pc[3].setText(dif == 0 ? "" : firmado(dif));
            // Las contrapartidas llevan el signo contrario a la partida.
            long mas = Math.max(dif, 0), menos = Math.min(dif, 0);
            boolean contraNegativa = dif >= 0;
            for (Object[] f : filas) {
                EditText ev = (EditText) f[2];
                ev.setHint(contraNegativa ? "valor (−)" : "valor (+)");
                Long v = leerValor(ev.getText().toString());
                if (v != null) {
                    if (contraNegativa) menos -= Math.abs(v); else mas += Math.abs(v);
                }
            }
            totMas.setText(formato.format(mas));
            totMenos.setText(formato.format(menos));
            long n = mas + menos;
            neto.setText(dif == 0 ? "" : n == 0 ? "0" : "Falta por registrar: " + formato.format(Math.abs(n)));
            neto.setTextColor(Color.parseColor(n == 0 ? "#1B5E20" : "#B71C1C"));
            // Con el neto en 0 sobran las filas vacías del final (queda al menos una).
            if (dif != 0 && n == 0) {
                while (filas.size() > 1) {
                    Object[] u = filas.get(filas.size() - 1);
                    if (((Spinner) u[1]).getSelectedItemPosition() > 0
                            || !((EditText) u[2]).getText().toString().trim().isEmpty()) break;
                    tabla.removeView((TableRow) u[0]);
                    filas.remove(u);
                }
            }
            // Mientras quede saldo por registrar y la última fila esté completa, aparece una nueva.
            if (dif != 0 && n != 0) {
                Object[] ultima = filas.isEmpty() ? null : filas.get(filas.size() - 1);
                boolean completa = ultima == null || (((Spinner) ultima[1]).getSelectedItemPosition() > 0
                        && leerValor(((EditText) ultima[2]).getText().toString()) != null);
                if (completa) {
                    agregarFila.accept(0, "");
                    actualizar[0].run();
                }
            }
        };
        cuenta.setOnItemSelectedListener(alSeleccionar(actualizar[0]));
        modo.setOnItemSelectedListener(alSeleccionar(actualizar[0]));
        TextWatcher alEscribir = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { actualizar[0].run(); }
        };
        valor.addTextChangedListener(alEscribir);
        actualizar[0].run();

        Runnable agregar = () -> {
            guardarForm.run();
            Long escrito = leerValor(form.valor);
            String desc = descripcionSegura(form.descripcion);
            String error = null;
            List<Object[]> contras = null;
            Apoyo a = form.cuenta > 0 ? cuentas.get(form.cuenta - 1) : null;
            Long correcto = a == null ? null : valorCorrecto(a, form.porValor, escrito);
            if (a == null) error = "Elija la cuenta a corregir.";
            else if (correcto == null) error = "Escriba el valor.";
            else if (diferenciaContable(a, correcto) == 0) error = "El ajuste no cambia el valor del reporte.";
            else if (desc.isEmpty()) error = "Escriba la explicación del ajuste.";
            else {
                long dif = diferenciaContable(a, correcto);
                String[] e = new String[1];
                contras = contrapartidasDelFormulario(form, cuentas, dif, e);
                if (contras == null) error = e[0];
                else if (contras.isEmpty()) error = "Registre al menos una contrapartida.";
                else {
                    long suma = dif;
                    for (Object[] c : contras) suma += (Long) c[1];
                    if (suma != 0) error = "El neto no está en 0: falta por registrar " + formato.format(Math.abs(suma)) + ".";
                    for (Object[] c : contras) if (((Apoyo) c[0]).cuentaId == a.cuentaId) error = "Una contrapartida no puede ser la misma cuenta.";
                    for (Object[] g : registros) {
                        if (((Apoyo) g[0]).cuentaId == a.cuentaId) error = "Esa cuenta ya está en esta sesión.";
                    }
                }
            }
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                return;
            }
            registros.add(new Object[]{a, correcto, diferenciaContable(a, correcto), desc, contras});
            dialogo[0].dismiss();
            pantallaSesion(registros, new Formulario()); // formulario limpio, contrapartida incluida
        };
        raiz.addView(botones(
                new String[]{"Agregar registro", "Terminar sesión", "Cancelar sesión"},
                new Runnable[]{
                        agregar,
                        () -> confirmarTerminarSesion(registros, dialogo[0]),
                        () -> confirmarCancelarSesion(registros, dialogo[0])}));
        dialogo[0] = dialogo("Renta fiscal " + nombreAnio() + " sesión de ajuste", raiz);
    }

    /** Fila del pie de la tabla del registro en curso (Ítem · Tipo · Cuenta · Valor · ✕). */
    private TableRow filaTotalEdicion(String etiqueta) {
        TableRow f = new TableRow(context);
        f.addView(celda("", false, false));
        f.addView(celda("", false, false));
        f.addView(celda(etiqueta, true, false));
        f.addView(celda("", true, true));
        f.addView(celda("", false, false));
        f.setBackgroundColor(FONDO_ENCABEZADO);
        return f;
    }

    private static android.widget.AdapterView.OnItemSelectedListener alSeleccionar(Runnable r) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { r.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        };
    }

    private String firmado(long v) {
        return (v > 0 ? "+" : "") + formato.format(v);
    }

    private void confirmarCancelarSesion(List<Object[]> registros, AlertDialog pantalla) {
        if (registros.isEmpty()) {
            pantalla.dismiss();
            pantallaAjustes();
            return;
        }
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("Cancelar la sesión"))
                .setMessage("Se descartan " + registros.size() + " registro(s) sin guardar. El reporte no cambia.")
                .setPositiveButton("Descartar", (d, w) -> { pantalla.dismiss(); pantallaAjustes(); })
                .setNegativeButton("Seguir en la sesión", null)
                .show();
    }

    private void confirmarTerminarSesion(List<Object[]> registros, AlertDialog pantalla) {
        if (registros.isEmpty()) {
            Toast.makeText(context, "Agregue al menos un registro.", Toast.LENGTH_LONG).show();
            return;
        }
        boolean f1Ocupado = A5_CacheManager.tieneTrabajoPendienteReal(context, A1_1_AyudanteBD.AREA_CREATE);
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("Terminar la sesión"))
                .setMessage(registros.size() + " registro(s). El reporte fiscal de " + anio + " queda corregido y se "
                        + "genera un documento borrador en F1 › Nuevo con fecha " + fechaLegible(fechaDocumentoAjuste())
                        + ", para revisarlo y guardarlo."
                        + (f1Ocupado ? "\n\n⚠ F1 › Nuevo está ocupado: la sesión quedará pendiente de borrador hasta "
                        + "que lo libere." : "") + "\n\n¿Terminar?")
                .setPositiveButton("Terminar", (d, w) -> {
                    int sesion;
                    List<Object[]> filas = new ArrayList<>();
                    for (Object[] g : registros) {
                        Apoyo a = (Apoyo) g[0];
                        @SuppressWarnings("unchecked")
                        List<Object[]> reparto = (List<Object[]>) g[4];
                        List<Object[]> contras = new ArrayList<>();
                        for (Object[] c : reparto) {
                            Apoyo ca = (Apoyo) c[0];
                            contras.add(new Object[]{ca.cuentaId, ca.nombre, c[1]});
                        }
                        Apoyo primera = (Apoyo) reparto.get(0)[0];
                        filas.add(new Object[]{a.cuentaId, a.nombre, a.valorReporte, g[1], g[2], g[3],
                                primera.cuentaId, primera.nombre, contras});
                    }
                    SQLiteDatabase db = abrir();
                    try {
                        sesion = A1_1_AyudanteBD.guardarSesionAjuste(db, anio, filas);
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo guardar la sesión de ajuste: " + e.getMessage(), e);
                        Toast.makeText(context, "Error al guardar la sesión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    } finally {
                        db.close();
                    }
                    pantalla.dismiss();
                    filtroAjustes = null;
                    if (A5_CacheManager.tieneTrabajoPendienteReal(context, A1_1_AyudanteBD.AREA_CREATE)) {
                        respaldarAjustes();
                        pantallaAjustes();
                        avisoF1Ocupado();
                    } else {
                        generarBorrador(sesion);
                        pantallaAjustes();
                    }
                })
                .setNegativeButton("Seguir en la sesión", null)
                .show();
    }

    /**
     * Escribe el borrador de la sesión en F1 › Nuevo (caché del área 1, la que F1 restaura): por
     * cada registro, la partida de la cuenta corregida y las de sus contrapartidas (signo opuesto),
     * con la misma descripción y la fecha del primer día del año fiscal siguiente, también como
     * "otra fecha" del encabezado (así una partida que se agregue en F1 toma la misma fecha). El
     * número de documento lo vuelve a tomar F1 al guardar (consecutivo disponible en ese momento).
     */
    private void generarBorrador(int sesion) {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> items = new ArrayList<>();
        int fecha = 0;
        SQLiteDatabase db = abrir();
        try {
            String documento = A1_1_AyudanteBD.siguienteNumeroDocumento(db);
            String ahora = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.ROOT)
                    .format(new java.util.Date());
            Map<Long, Object[]> cuentas = new HashMap<>();
            for (Object[] c : A1_1_AyudanteBD.cuentasParaAjuste(db)) cuentas.put((Long) c[0], c);
            Map<String, List<Object[]>> contras = A1_1_AyudanteBD.contrapartidasDelAnio(db, anio);
            int n = 0;
            for (Object[] r : A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio)) {
                if ((Integer) r[0] != sesion) continue;
                fecha = (Integer) r[11];
                items.add(itemBorrador(documento, ++n, (Long) r[3], (String) r[4], (Long) r[7], (String) r[8], ahora,
                        fecha, cuentas));
                List<Object[]> lista = contras.get(sesion + "-" + r[2]);
                if (lista == null) continue;
                for (Object[] k : lista) {
                    items.add(itemBorrador(documento, ++n, (Long) k[0], (String) k[1], (Long) k[2], (String) r[8], ahora,
                            fecha, cuentas));
                }
            }
            if (items.isEmpty()) return;
            A5_CacheManager.Encabezado enc = A5_CacheManager.restaurarEncabezado(context, A1_1_AyudanteBD.AREA_CREATE);
            if (enc == null) enc = new A5_CacheManager.Encabezado();
            enc.campo_0_otraFechaChb = "true";
            enc.campo_1_otraFechaTv = String.valueOf(fecha);
            if (!A5_CacheManager.guardarRegistros(context, A1_1_AyudanteBD.AREA_CREATE, items)
                    || !A5_CacheManager.guardarEncabezado(context, A1_1_AyudanteBD.AREA_CREATE, enc)) {
                Toast.makeText(context, "No se pudo generar el borrador en F1.", Toast.LENGTH_LONG).show();
                return;
            }
            A1_1_AyudanteBD.marcarBorradorAjusteGenerado(db, sesion);
        } finally {
            db.close();
        }
        respaldarAjustes();
        new AlertDialog.Builder(context)
                .setCustomTitle(tituloCentrado("Borrador generado"))
                .setView(alerta("El borrador del ajuste (sesión " + sesion + ", " + items.size() + " partidas) está en "
                        + "F1 › Nuevo. Ábralo, revíselo y guárdelo: hasta entonces la contabilidad no refleja el "
                        + "ajuste y los ajustes le seguirán avisando.", false))
                .setPositiveButton("Ir a F1 › Nuevo", (d, w) -> irAF1Nuevo())
                .setNegativeButton("Más tarde", null)
                .show();
    }

    private static A3_2_TipoTransaccionesGetsYSets itemBorrador(String documento, int item, Long cuentaId, String nombre,
                                                               long valor, String descripcion, String ahora, int fecha,
                                                               Map<Long, Object[]> cuentas) {
        A3_2_TipoTransaccionesGetsYSets t = new A3_2_TipoTransaccionesGetsYSets(documento, String.valueOf(item), nombre,
                valor < 0 ? "-" : "+", (int) valor, descripcion, ahora, fecha, "No Aplica", "", "", "na", "na");
        Object[] c = cuentaId == null ? null : cuentas.get(cuentaId);
        t.tipoTset_14CuentaIdMetodoEnA5(cuentaId);
        t.tipoTset_16TipoCuentaIdMetodoEnA5(c == null ? null : (Long) c[4]);
        t.tipoTset_17TipoCuentaNombreMetodoEnA5(c == null ? null : (String) c[5]);
        t.tipoTset_18ConInventarioMetodoEnA5(false);
        return t;
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
            if (p[3] == null) continue;
            m.put((Long) p[3], cero(m.get((Long) p[3])) + (Long) p[7]);
        }
        return m;
    }

    /** Aviso llamativo (fondo naranja, letra roja oscura). */
    private TextView alerta(String t, boolean grande) {
        TextView v = new TextView(context);
        v.setText(t);
        v.setTextColor(Color.parseColor("#B71C1C"));
        v.setBackgroundColor(Color.parseColor("#FFE0B2"));
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(20, 16, 20, 16);
        if (grande) v.setTextSize(16);
        return v;
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
                    if (p[3] != null) nombresAjuste.put((Long) p[3], (String) p[4]);
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
        abiertos.add(d);
        d.setOnDismissListener(x -> abiertos.remove(d));
        d.show();
        return d;
    }

    /** ⭐ (8-nov) Pantallas del recorrido abiertas, para cerrarlas todas al ir a F1. */
    private final List<AlertDialog> abiertos = new ArrayList<>();

    private void cerrarDialogos() {
        for (AlertDialog d : new ArrayList<>(abiertos)) d.dismiss();
        abiertos.clear();
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

    /**
     * ⭐ (7-nov, R6; 8-nov tabla de asientos) Las sesiones de ajuste fiscal del año: por registro, la
     * partida y sus contrapartidas en Débito (+) / Crédito (−), con totales por sesión.
     */
    private Seccion seccionAjustes() {
        Seccion s = new Seccion("Ajustes posteriores al cierre (afectan a " + anio + ")",
                new String[]{"Sesión / registro / asiento", "Débito (+)", "Crédito (−)"});
        SQLiteDatabase db = abrir();
        try {
            List<Object[]> registros = A1_1_AyudanteBD.ajustesRentaDelAnio(db, anio);
            Map<String, List<Object[]>> contras = A1_1_AyudanteBD.contrapartidasDelAnio(db, anio);
            for (int i = 0; i < registros.size(); i++) {
                Object[] r = registros.get(i);
                boolean primero = i == 0 || !registros.get(i - 1)[0].equals(r[0]);
                if (primero) {
                    s.filas.add(new String[]{"R", "Sesión " + r[0] + " · " + (r[13] != null
                            ? "documento N° " + r[13] + " del " + fechaLegible((Integer) r[11])
                            : "documento contable sin guardar"), "", ""});
                }
                s.filas.add(new String[]{"C", "Reg. " + r[2] + " · " + r[4] + ": " + formato.format((Long) r[5]) + " → "
                        + formato.format((Long) r[6]) + " · " + (r[8] == null ? "" : r[8]), "", ""});
                long dif = (Long) r[7];
                s.filas.add(new String[]{"C", "      Partida · " + r[4], dif > 0 ? formato.format(dif) : "",
                        dif < 0 ? formato.format(dif) : ""});
                List<Object[]> lista = contras.get(r[0] + "-" + r[2]);
                if (lista != null) {
                    int n = 0;
                    for (Object[] c : lista) {
                        long v = (Long) c[2];
                        s.filas.add(new String[]{"C", "      Contrapartida " + (++n) + " · " + c[1],
                                v > 0 ? formato.format(v) : "", v < 0 ? formato.format(v) : ""});
                    }
                }
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
