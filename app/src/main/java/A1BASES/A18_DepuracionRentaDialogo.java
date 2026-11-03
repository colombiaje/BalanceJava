package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;

/**
 * A18_DepuracionRentaDialogo — (3-nov, renta R5, diseño acordado con Jorge): opción
 * "7. Depurar saldos contrarios" de F4. Sobre la foto de un año (cierre 421), lista solo las
 * cuentas que terminaron con saldo contrario a la naturaleza de su tipo de cuenta (ej.: un banco
 * sobregirado) y, para cada una, propone un destino: la decisión del año anterior para esa cuenta
 * ("como en 2025"), o el renglón por defecto de la naturaleza contraria (banco sobregirado →
 * Deudas). El usuario confirma o elige cualquier renglón del catálogo de ese año; la decisión se
 * guarda en la foto (renglón efectivo) y el reporte de la opción 6 la refleja. La foto no cambia
 * en nada más: el saldo y el renglón asignado quedan como se tomaron.
 */
public class A18_DepuracionRentaDialogo {

    private static final String TAG = "A18_DepuracionRenta";

    private String folderIdDrive;

    /** @param folderIdDrive carpeta de cierres en Drive, donde se vuelve a subir el respaldo 423. */
    public void mostrar(Context context, String folderIdDrive) {
        if (context == null) return;
        this.folderIdDrive = folderIdDrive;
        List<Integer> anios;
        SQLiteDatabase db = abrir(context);
        try {
            anios = A1_1_AyudanteBD.aniosConFotoRenta(db);
        } finally {
            db.close();
        }
        if (anios.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle("Depurar saldos contrarios")
                    .setMessage("Todavía no hay fotos anuales. La foto se toma en el cierre de todas " +
                            "las cuentas (opción 2).")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (anios.size() == 1) {
            mostrarAnio(context, anios.get(0));
            return;
        }
        String[] etiquetas = new String[anios.size()];
        for (int i = 0; i < etiquetas.length; i++) etiquetas[i] = String.valueOf(anios.get(i));
        new AlertDialog.Builder(context)
                .setTitle("¿De qué año?")
                .setItems(etiquetas, (d, i) -> mostrarAnio(context, anios.get(i)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarAnio(Context context, int anio) {
        DecimalFormat formato = new DecimalFormat("#,##0");
        List<Object[]> contrarias;
        List<Object[]> renglones;
        List<Long> idsPropuestos = new ArrayList<>();
        List<String> motivos = new ArrayList<>();
        SQLiteDatabase db = abrir(context);
        try {
            contrarias = A1_1_AyudanteBD.cuentasConSaldoContrario(db, anio);
            renglones = A1_1_AyudanteBD.renglonesAsignablesEnAnio(db, anio);
            for (Object[] f : contrarias) {
                if (f[6] != null && f[5] != null) { // ya decidida: se muestra lo guardado
                    idsPropuestos.add((Long) f[5]);
                    motivos.add("decidido");
                } else {
                    Object[] s = A1_1_AyudanteBD.sugerenciaDepuracion(db, anio, (Long) f[0], (String) f[4]);
                    idsPropuestos.add((Long) s[0]);
                    motivos.add("sugerido: " + s[1]);
                }
            }
        } finally {
            db.close();
        }
        if (contrarias.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle("Depuración " + anio)
                    .setMessage("Ninguna cuenta de la foto de " + anio + " terminó con saldo contrario.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        List<Long> idsRenglon = new ArrayList<>();
        List<String> etiquetasRenglon = new ArrayList<>();
        for (Object[] r : renglones) {
            idsRenglon.add((Long) r[0]);
            etiquetasRenglon.add((String) r[1]);
        }

        LinearLayout raiz = new LinearLayout(context);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(32, 8, 32, 8);
        TextView ayuda = new TextView(context);
        ayuda.setText("Cuentas que terminaron " + anio + " con saldo contrario a su tipo de cuenta. " +
                "Elija en qué renglón se declara cada una (viene una sugerencia).");
        ayuda.setPadding(0, 0, 0, 12);
        raiz.addView(ayuda);

        List<Spinner> selectores = new ArrayList<>();
        for (int i = 0; i < contrarias.size(); i++) {
            Object[] f = contrarias.get(i);
            TextView nombre = new TextView(context);
            nombre.setText(f[1] + "   $ " + formato.format((Long) f[2]));
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setPadding(0, 12, 0, 0);
            raiz.addView(nombre);
            TextView motivo = new TextView(context);
            motivo.setText("Asignado: " + etiquetaDe(idsRenglon, etiquetasRenglon, (Long) f[3]) +
                    " · " + motivos.get(i));
            motivo.setTextSize(12);
            raiz.addView(motivo);
            Spinner selector = new Spinner(context);
            selector.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, etiquetasRenglon));
            int pos = idsPropuestos.get(i) == null ? idsRenglon.indexOf((Long) f[3]) : idsRenglon.indexOf(idsPropuestos.get(i));
            selector.setSelection(Math.max(0, pos));
            raiz.addView(selector);
            selectores.add(selector);
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(raiz);
        new AlertDialog.Builder(context)
                .setTitle("Depuración " + anio + " · " + contrarias.size() + " cuentas")
                .setView(scroll)
                .setPositiveButton("Guardar decisiones", (d, w) -> {
                    SQLiteDatabase dbG = abrir(context);
                    try {
                        dbG.beginTransaction();
                        try {
                            for (int i = 0; i < contrarias.size(); i++) {
                                int pos = selectores.get(i).getSelectedItemPosition();
                                if (pos < 0 || pos >= idsRenglon.size()) continue;
                                A1_1_AyudanteBD.guardarDecisionDepuracion(dbG, anio,
                                        (Long) contrarias.get(i)[0], idsRenglon.get(pos));
                            }
                            dbG.setTransactionSuccessful();
                        } finally {
                            dbG.endTransaction();
                        }
                        // El respaldo 423 (todas las fotos) se regenera y sube con las decisiones.
                        String nombreFoto = A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getFileName();
                        if (A5_1_BackupManager.backupFotoRentaCSV(context, nombreFoto) && folderIdDrive != null) {
                            A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(context, nombreFoto, folderIdDrive);
                        }
                        Toast.makeText(context, "Decisiones guardadas (" + contrarias.size() +
                                " cuentas). El reporte de " + anio + " ya las refleja.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudieron guardar las decisiones: " + e.getMessage(), e);
                        Toast.makeText(context, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    } finally {
                        dbG.close();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String etiquetaDe(List<Long> ids, List<String> etiquetas, Long id) {
        int i = id == null ? -1 : ids.indexOf(id);
        return i < 0 ? "(sin renglón vigente)" : etiquetas.get(i);
    }

    private static SQLiteDatabase abrir(Context context) {
        return new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF)
                .getWritableDatabase();
    }
}
