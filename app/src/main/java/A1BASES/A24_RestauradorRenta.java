package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import androidx.appcompat.app.AlertDialog;

/**
 * ⭐ NUEVO (12-nov, opción B elegida por Jorge): restaura la renta (fotos anuales, ajustes a años
 * cerrados y checklist de soportes) después de restaurar cuentas y transacciones.
 *
 * Orden fijo: 423_FotoRentaCierre → 425_AjustesRentaCierre → 426_SoportesRenta. Para cada uno:
 *   1. Drive: el respaldo más reciente de la carpeta (la lista ya viene por fecha descendente).
 *   2. Si Drive no responde o no lo tiene (opción "a" de Jorge): el archivo más reciente de la
 *      carpeta Balance del equipo (la tradicional y la de la app, como F2 › 4).
 *   3. Si tampoco está, se anota en el resumen y se sigue con el siguiente.
 * Importa con las mismas funciones de F2 › 4 (A1_1_AyudanteBD.importar*Renta), cada archivo en su
 * propia transacción. No cambia la base ni los importadores: solo los encadena.
 */
public class A24_RestauradorRenta {

    private static final String TAG = "A24_RestauradorRenta";
    private static final String[] PREFIJOS = {
            "423_FotoRentaCierre", "425_AjustesRentaCierre", "426_SoportesRenta"};
    private static final String[] NOMBRES = {"Fotos anuales", "Ajustes a años cerrados", "Soportes"};

    private final Context context;
    private final String folderIdDrive;
    private final Runnable alTerminar;
    private final StringBuilder resumen = new StringBuilder();
    private ProgressDialog progreso;

    public A24_RestauradorRenta(Context context, String folderIdDrive, Runnable alTerminar) {
        this.context = context;
        this.folderIdDrive = folderIdDrive;
        this.alTerminar = alTerminar;
    }

    public void restaurar() {
        if (context == null) return;
        progreso = new ProgressDialog(context);
        progreso.setTitle("Restaurando la renta");
        progreso.setCancelable(false);
        progreso.show();
        paso(0);
    }

    /** Procesa el prefijo i y, al terminar (bien o mal), sigue con el siguiente. */
    private void paso(int i) {
        if (i >= PREFIJOS.length) {
            terminar();
            return;
        }
        progreso.setMessage(NOMBRES[i] + "…");
        if (folderIdDrive == null) {
            usarLocal(i, "sin carpeta de Drive");
            return;
        }
        A6_2_GoogleDriveDownloader drive = new A6_2_GoogleDriveDownloader(context, folderIdDrive);
        drive.listarArchivos(PREFIJOS[i], new A6_2_GoogleDriveDownloader.OnFilesListedListener() {
            @Override
            public void onFilesListed(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
                if (archivos == null || archivos.isEmpty()) {
                    usarLocal(i, "no está en Drive");
                    return;
                }
                A6_2_GoogleDriveDownloader.DriveFileInfo ultimo = archivos.get(0);
                drive.descargarArchivo(ultimo.id, ultimo.nombre,
                        new A6_2_GoogleDriveDownloader.OnFileDownloadedListener() {
                            @Override
                            public void onFileDownloaded(String localPath) {
                                importar(i, new File(localPath), "Drive: " + ultimo.nombre);
                            }

                            @Override
                            public void onError(String error) {
                                usarLocal(i, "falló la descarga (" + error + ")");
                            }
                        });
            }

            @Override
            public void onError(String error) {
                usarLocal(i, "Drive no respondió (" + error + ")");
            }
        });
    }

    /** Opción "a": el archivo más reciente de la carpeta Balance del equipo. */
    private void usarLocal(int i, String motivoDrive) {
        File local = masRecienteLocal(PREFIJOS[i]);
        if (local == null) {
            resumen.append("• ").append(NOMBRES[i]).append(": no se encontró (")
                    .append(motivoDrive).append(", y tampoco está en la carpeta Balance).\n");
            paso(i + 1);
            return;
        }
        importar(i, local, "equipo: " + local.getName() + " (" + motivoDrive + ")");
    }

    private File masRecienteLocal(String prefijo) {
        File[] carpetas = {
                new File(Environment.getExternalStorageDirectory(), "Balance"),
                context.getExternalFilesDir(null) == null ? null
                        : new File(context.getExternalFilesDir(null), "Balance")};
        File mejor = null;
        for (File carpeta : carpetas) {
            if (carpeta == null || !carpeta.isDirectory()) continue;
            File[] archivos = carpeta.listFiles((dir, nombre) ->
                    nombre.startsWith(prefijo) && nombre.toLowerCase().endsWith(".csv"));
            if (archivos == null) continue;
            for (File f : archivos) {
                if (mejor == null || f.lastModified() > mejor.lastModified()) mejor = f;
            }
        }
        return mejor;
    }

    private void importar(int i, File archivo, String origen) {
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null,
                version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getWritableDatabase();
        try {
            String texto = leerTexto(archivo);
            int filas;
            db.beginTransaction();
            try {
                if (i == 0) filas = A1_1_AyudanteBD.importarFotosRenta(db, texto);
                else if (i == 1) filas = A1_1_AyudanteBD.importarAjustesRenta(db, texto);
                else filas = A1_1_AyudanteBD.importarSoportesRenta(db, texto);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            resumen.append("• ").append(NOMBRES[i]).append(": ").append(filas)
                    .append(" filas, desde ").append(origen).append(".\n");
        } catch (Exception e) {
            Log.e(TAG, "No se pudo importar " + archivo + ": " + e.getMessage(), e);
            resumen.append("• ").append(NOMBRES[i]).append(": error al importar ")
                    .append(archivo.getName()).append(" (").append(e.getMessage()).append(").\n");
        } finally {
            db.close();
        }
        paso(i + 1);
    }

    private void terminar() {
        if (progreso != null && progreso.isShowing()) progreso.dismiss();
        new AlertDialog.Builder(context)
                .setTitle("Renta restaurada")
                .setView(A20_Informacion.vista(context, resumen.toString()
                        + "\nAbre F4 › 6 para revisarla."))
                .setPositiveButton("Entendido", (d, w) -> {
                    if (alTerminar != null) alTerminar.run();
                })
                .show();
    }

    private static String leerTexto(File archivo) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) sb.append(linea).append("\n");
        }
        return sb.toString();
    }
}
