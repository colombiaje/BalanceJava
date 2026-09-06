package A1BASES;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import A2QueryBD.A22_QueryManager;

/**
 * Clase mejorada para gestionar la subida de archivos CSV a Google Drive
 * con sistema de rotación automática de backups (máximo 5 por tipo)
 */
public class A6_3_CSVDriveUploader {

    private static final String TAG = "CSVDriveUploader";
    private static final String CARPETA_BALANCE = "/Balance/";

    // Instancias
    A5_1_BackupManager a6_backupManager;

    /**
     * Guarda transacciones localmente y las sube a Drive con rotación automática
     * Mantiene solo los últimos 5 backups del mismo tipo
     *
     * @param context Contexto de la aplicación
     * @param nombreArchivo Nombre base del archivo (se agregará timestamp automáticamente)
     * @param folderId ID de la carpeta en Google Drive
     */
    public static void guardarYSubirTransacciones(Context context, String nombreArchivo, String folderId, int maxBackups) {
        Log.d("CSVDriveUploader", "═══════════════════════════════════════");
        Log.d("CSVDriveUploader", "INICIO guardarYSubirTransacciones");
        Log.d("CSVDriveUploader", "Archivo: " + nombreArchivo);
        Log.d("CSVDriveUploader", "FolderId: " + folderId);
        Log.d("CSVDriveUploader", "MaxBackups: " + maxBackups);  // ← CORREGIDO: era maxArchivos
        Log.d("CSVDriveUploader", "═══════════════════════════════════════");

        try {
            // 1. Guardar archivo localmente
            A5_1_BackupManager.guardarTodasLasTransancionsAUnArchivoCSV(context, nombreArchivo);
            Log.d("CSVDriveUploader", "✓ Archivo guardado localmente: " + nombreArchivo);

            // 2. Verificar que existe
            String filePath = Environment.getExternalStorageDirectory().getPath() + CARPETA_BALANCE;
            String filePathAndfileName = filePath + nombreArchivo;
            File archivo = new File(filePathAndfileName);

            if (archivo.exists()) {
                Log.d("CSVDriveUploader", "✓ Archivo verificado, tamaño: " + archivo.length() + " bytes");
                Log.d("CSVDriveUploader", "→ Iniciando subida a Drive...");

                // 3. Subir a Drive con rotación
                A6_1_GoogleDriveManager driveManager = new A6_1_GoogleDriveManager(
                        context, filePath, nombreArchivo, folderId, maxBackups  // ← CORREGIDO
                );
                driveManager.uploadFileToDrive();
                Log.d("CSVDriveUploader", "✓ Proceso de subida iniciado para: " + nombreArchivo);
            } else {
                String mensaje = "✗ El archivo no existe: " + nombreArchivo;
                Log.e("CSVDriveUploader", mensaje);
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("CSVDriveUploader", "✗✗✗ ERROR en guardarYSubirTransacciones: " + e.getMessage(), e);
            Toast.makeText(context, "Error al procesar backup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        Log.d("CSVDriveUploader", "═══════════════════════════════════════");
        Log.d("CSVDriveUploader", "FIN guardarYSubirTransacciones");
        Log.d("CSVDriveUploader", "═══════════════════════════════════════");
    }

    /**
     * Guarda cuentas localmente y las sube a Drive con rotación automática
     * Mantiene solo los últimos 5 backups del mismo tipo
     *
     * @param context Contexto de la aplicación
     * @param nombreArchivo Nombre base del archivo (se agregará timestamp automáticamente)
     * @param folderId ID de la carpeta en Google Drive
     */
    public static void guardarYSubirCuentas(Context context, String nombreArchivo, String folderId) {
        try {
            // 1. Inicializar managers
            A5_1_BackupManager a6_backupManager = new A5_1_BackupManager();
            A22_QueryManager a22QueryManager = new A22_QueryManager(context);

            // 2. Guardar archivo localmente
            a6_backupManager.backupCuentasArchivoCSV(nombreArchivo, a22QueryManager);
            Log.d(TAG, "Archivo de cuentas guardado localmente: " + nombreArchivo);

            // 3. Verificar que el archivo existe
            String filePath = Environment.getExternalStorageDirectory().getPath() + CARPETA_BALANCE;
            String filePathAndfileName = filePath + nombreArchivo;
            File archivo = new File(filePathAndfileName);

            if (archivo.exists()) {
                // 4. Subir a Drive (incluye rotación automática)
                A6_1_GoogleDriveManager driveManager = new A6_1_GoogleDriveManager(
                        context,
                        filePath,
                        nombreArchivo,
                        folderId
                );
                driveManager.uploadFileToDrive();

                Log.d(TAG, "Proceso de subida iniciado para cuentas: " + nombreArchivo);
            } else {
                String mensaje = "El archivo no existe: " + nombreArchivo;
                Log.e(TAG, mensaje);
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en guardarYSubirCuentas: " + e.getMessage(), e);
            Toast.makeText(context, "Error al procesar backup de cuentas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}