package A1BASES;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class A6_1_GoogleDriveManager {
    private static final String APPLICATION_NAME = "a4.balance";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String[] SCOPES = {DriveScopes.DRIVE_FILE};
    private static final String TAG = "DriveManager";
    private static final int MAX_BACKUPS_POR_TIPO = 5;
    private Context context;
    private String filePath;
    private String fileName;
    private String folderId;

    // DESPUÉS
    private int maxBackups = MAX_BACKUPS_POR_TIPO; // Agregar esta variable en línea 35

    public A6_1_GoogleDriveManager(Context context, String filePath, String fileName, String folderId) {
        this.context = context;
        this.filePath = filePath;
        this.fileName = fileName;
        this.folderId = folderId;
    }

    // AGREGAR NUEVO CONSTRUCTOR después de línea 46
    public A6_1_GoogleDriveManager(Context context, String filePath, String fileName, String folderId, int maxBackups) {
        this.context = context;
        this.filePath = filePath;
        this.fileName = fileName;
        this.folderId = folderId;
        this.maxBackups = maxBackups;
    }

    /**
     * Genera el timestamp en formato: _F-2024-12-14_H-14-30-22
     */
    private String generarTimestamp() {
        //SimpleDateFormat sdf = new SimpleDateFormat("_MMdd_HHmmss", Locale.getDefault());
        SimpleDateFormat sdf = new SimpleDateFormat("_MMdd_HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Extrae el nombre base del archivo sin timestamp
     * Ejemplo: "csv 411 Backup todas las transacciones_F-2024-12-14_H-14-30-22.csv"
     * Retorna: "csv 411 Backup todas las transacciones"
     */
    private String extraerNombreBase(String nombreCompleto) {
        // Eliminar la extensión
        String sinExtension = nombreCompleto.replaceFirst("\\.csv$", "");
        // Eliminar el timestamp si existe
        //return sinExtension.replaceFirst("_F-\\d{4}-\\d{2}-\\d{2}_H-\\d{2}-\\d{2}-\\d{2}$", "");
        return sinExtension.replaceFirst("_\\d{4}_\\d{6}$", "");
    }

    /**
     * Genera el nombre del archivo con timestamp
     */
    private String generarNombreConTimestamp(String nombreOriginal) {
        String nombreBase = nombreOriginal.replaceFirst("\\.csv$", "");
        String timestamp = generarTimestamp();
        return nombreBase + timestamp + ".csv";
    }

    public void uploadFileToDrive() {
        new UploadTask().execute();
    }

    private class UploadTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(DriveScopes.DRIVE_FILE)
                ).setSelectedAccountName("colombiaje@gmail.com");

                Drive driveService = new Drive.Builder(transport, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                // Generar nombre con timestamp
                String nombreConTimestamp = generarNombreConTimestamp(fileName);

                // Path al archivo CSV local
                java.io.File csvFile = new java.io.File(filePath, fileName);

                // Configurar metadata del archivo
                File fileMetadata = new File();
                fileMetadata.setName(nombreConTimestamp);
                fileMetadata.setParents(Collections.singletonList(folderId));
                FileContent mediaContent = new FileContent("text/csv", csvFile);

                // Subir archivo
                File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id, name, createdTime")
                        .execute();

                if (uploadedFile != null) {
                    Log.d(TAG, "Archivo subido: " + uploadedFile.getName() + " (ID: " + uploadedFile.getId() + ")");

                    // Iniciar limpieza de backups antiguos en background
                    limpiarBackupsAntiguos(driveService, fileName);

                    return "File uploaded: " + uploadedFile.getId();
                } else {
                    return "Error uploading file.";
                }
            } catch (IOException e) {
                Log.e(TAG, "Error al subir archivo: " + e.getMessage(), e);
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "Resultado de subida: " + result);
        }
    }

    /**
     * Limpia backups antiguos manteniendo solo los últimos 5
     */
    private void limpiarBackupsAntiguos(Drive driveService, String nombreArchivoOriginal) {
        try {
            // Extraer nombre base para buscar archivos relacionados
            String nombreBase = extraerNombreBase(nombreArchivoOriginal);

            Log.d(TAG, "Iniciando limpieza de backups para: " + nombreBase);

            // Buscar archivos en la carpeta con el nombre base
            String query = String.format(
                    "'%s' in parents and name contains '%s' and mimeType='text/csv' and trashed=false",
                    folderId,
                    nombreBase
            );

            FileList result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime)")
                    .setOrderBy("createdTime desc")
                    .execute();

            List<File> archivos = result.getFiles();

            if (archivos == null || archivos.isEmpty()) {
                Log.d(TAG, "No se encontraron archivos para limpiar");
                return;
            }

            Log.d(TAG, "Archivos encontrados: " + archivos.size());

            // Si hay más de 5 archivos, eliminar los más antiguos
            if (archivos.size() > maxBackups) {
                int archivosAEliminar = archivos.size() - maxBackups;
                Log.d(TAG, "Se eliminarán " + archivosAEliminar + " archivo(s) antiguo(s)");

                // Los archivos más antiguos están al final de la lista (ordenada desc)
                for (int i = archivos.size() - 1; i >= maxBackups; i--) {
                    File archivoAEliminar = archivos.get(i);
                    try {
                        driveService.files().delete(archivoAEliminar.getId()).execute();
                        Log.d(TAG, "Eliminado: " + archivoAEliminar.getName() + " (ID: " + archivoAEliminar.getId() + ")");
                    } catch (IOException e) {
                        Log.e(TAG, "Error al eliminar archivo: " + archivoAEliminar.getName(), e);
                    }
                }

                Log.d(TAG, "Limpieza completada. Archivos restantes: " + maxBackups);
            } else {
                Log.d(TAG, "No es necesario eliminar archivos. Total: " + archivos.size());
            }

        } catch (IOException e) {
            Log.e(TAG, "Error en limpieza de backups: " + e.getMessage(), e);
        }
    }
}