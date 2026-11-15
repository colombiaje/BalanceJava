package A1BASES;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ⭐ NUEVO (15-nov, plantillas P3): restaura las plantillas desde el respaldo 214_Plantillas.
 * Igual que A24_RestauradorRenta: primero el más reciente de Drive (carpeta de cuentas); si
 * Drive no responde o no lo tiene, el más reciente de la carpeta Balance del equipo. Importa con
 * A27_Plantillas.importarCsv (por nombre: crea las que faltan y actualiza el modelo de las que
 * existen, con el actual como versión anterior).
 */
public class A30_RestauradorPlantillas {

    private static final String TAG = "A30_RestauradorPlantillas";

    private final Context context;
    private final Runnable alTerminar;
    private ProgressDialog progreso;

    public A30_RestauradorPlantillas(Context context, Runnable alTerminar) {
        this.context = context;
        this.alTerminar = alTerminar;
    }

    public void confirmarYRestaurar() {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle("Restaurar plantillas")
                .setView(A20_Informacion.vista(context, "Enseguida verá los últimos 5 respaldos de Drive " +
                        "(★ el más reciente) y elige cuál restaurar; también puede tomar el de la carpeta " +
                        "Balance. Las plantillas que falten se crean; las que ya existen con el " +
                        "mismo nombre toman el modelo del respaldo y su modelo actual queda como versión anterior."))
                .setPositiveButton("Restaurar", (d, w) -> restaurar())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void restaurar() {
        progreso = new ProgressDialog(context);
        progreso.setTitle("Restaurando plantillas");
        progreso.setMessage("Buscando el respaldo…");
        progreso.setCancelable(false);
        progreso.show();
        A6_2_GoogleDriveDownloader drive = new A6_2_GoogleDriveDownloader(context, A27_Plantillas.FOLDER_ID_DRIVE);
        drive.listarArchivos(A27_Plantillas.PREFIJO_CSV, new A6_2_GoogleDriveDownloader.OnFilesListedListener() {
            @Override
            public void onFilesListed(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
                if (archivos == null || archivos.isEmpty()) {
                    usarLocal("no está en Drive");
                    return;
                }
                elegir(drive, archivos);
            }

            @Override
            public void onError(String error) {
                usarLocal("Drive no respondió (" + error + ")");
            }
        });
    }

    /**
     * ⭐ (15-nov, Jorge): muestra los últimos 5 respaldos de Drive (Drive conserva solo esos y
     * borra los anteriores al subir uno nuevo) para elegir cuál restaurar; el primero es el más
     * reciente. También se puede tomar el de la carpeta Balance del equipo.
     */
    private void elegir(A6_2_GoogleDriveDownloader drive, List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
        if (progreso != null && progreso.isShowing()) progreso.dismiss();
        final int n = Math.min(5, archivos.size());
        String[] filas = new String[n + 1];
        for (int i = 0; i < n; i++) {
            A6_2_GoogleDriveDownloader.DriveFileInfo a = archivos.get(i);
            filas[i] = (i == 0 ? "★ " : "") + fecha(a.fechaCreacion) + " · " + a.nombre;
        }
        filas[n] = "Carpeta Balance del equipo";
        new AlertDialog.Builder(context)
                .setTitle("Respaldos de plantillas (últimos " + n + ")")
                .setItems(filas, (d, i) -> {
                    progreso.setMessage("Restaurando…");
                    progreso.show();
                    if (i == n) {
                        usarLocal("elegida por el usuario");
                        return;
                    }
                    A6_2_GoogleDriveDownloader.DriveFileInfo elegido = archivos.get(i);
                    drive.descargarArchivo(elegido.id, elegido.nombre,
                            new A6_2_GoogleDriveDownloader.OnFileDownloadedListener() {
                                @Override
                                public void onFileDownloaded(String localPath) {
                                    importar(new File(localPath), "Drive: " + elegido.nombre);
                                }

                                @Override
                                public void onError(String error) {
                                    usarLocal("falló la descarga (" + error + ")");
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** "2026-11-15T13:40:12.345Z" → "2026-11-15 13:40" (UTC, como lo da Drive). */
    private static String fecha(String iso) {
        if (iso == null || iso.length() < 16) return iso == null ? "" : iso;
        return iso.substring(0, 10) + " " + iso.substring(11, 16);
    }

    private void usarLocal(String motivo) {
        File[] carpetas = {
                new File(Environment.getExternalStorageDirectory(), "Balance"),
                context.getExternalFilesDir(null) == null ? null : new File(context.getExternalFilesDir(null), "Balance")};
        File mejor = null;
        for (File carpeta : carpetas) {
            if (carpeta == null || !carpeta.isDirectory()) continue;
            File[] archivos = carpeta.listFiles((dir, n) -> n.startsWith(A27_Plantillas.PREFIJO_CSV)
                    && n.toLowerCase().endsWith(".csv"));
            if (archivos == null) continue;
            for (File f : archivos) if (mejor == null || f.lastModified() > mejor.lastModified()) mejor = f;
        }
        if (mejor == null) {
            terminar("No se encontró ningún respaldo de plantillas (" + motivo +
                    ", y tampoco está en la carpeta Balance).");
            return;
        }
        importar(mejor, "equipo: " + mejor.getName() + " (" + motivo + ")");
    }

    private void importar(File archivo, String origen) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(archivo), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l).append("\n");
            }
            String resultado = A27_Plantillas.importarCsv(context, sb.toString());
            terminar("Plantillas: " + resultado + ".\nDesde " + origen + ".");
        } catch (Exception e) {
            Log.e(TAG, "importar: " + e.getMessage(), e);
            terminar("Error al importar " + archivo.getName() + ": " + e.getMessage());
        }
    }

    private void terminar(String texto) {
        if (progreso != null && progreso.isShowing()) progreso.dismiss();
        new AlertDialog.Builder(context)
                .setTitle("Restaurar plantillas")
                .setView(A20_Informacion.vista(context, texto))
                .setPositiveButton("Entendido", (d, w) -> {
                    if (alTerminar != null) alTerminar.run();
                })
                .show();
    }
}
