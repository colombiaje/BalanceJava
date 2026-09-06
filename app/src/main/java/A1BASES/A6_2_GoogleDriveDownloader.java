package A1BASES;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clase para listar y descargar archivos desde Google Drive
 */
public class A6_2_GoogleDriveDownloader {

    private static final String APPLICATION_NAME = "a4.balance";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TAG = "DriveDownloader";
    private static final String CARPETA_BALANCE = "/Balance/";

    private Context context;
    private String folderId;

    public A6_2_GoogleDriveDownloader(Context context, String folderId) {
        this.context = context;
        this.folderId = folderId;
    }

    /**
     * Interface para callback de listado de archivos
     */
    public interface OnFilesListedListener {
        void onFilesListed(List<DriveFileInfo> archivos);
        void onError(String error);
    }

    /**
     * Interface para callback de descarga
     */
    public interface OnFileDownloadedListener {
        void onFileDownloaded(String localPath);
        void onError(String error);
    }

    /**
     * Clase para información de archivo en Drive
     */
    public static class DriveFileInfo {
        public String id;
        public String nombre;
        public String fechaCreacion;
        public String nombreSinTimestamp;

        public DriveFileInfo(String id, String nombre, String fechaCreacion) {
            this.id = id;
            this.nombre = nombre;
            this.fechaCreacion = fechaCreacion;
            this.nombreSinTimestamp = extraerNombreSinTimestamp(nombre);
        }

        private String extraerNombreSinTimestamp(String nombreCompleto) {
            String sinExtension = nombreCompleto.replaceFirst("\\.csv$", "");
            Log.d("sheets", "Nombre sin timestamp: " + sinExtension);
            return sinExtension.replaceFirst("_\\d{4}_\\d{6}$", "");
        }

        @Override
        public String toString() {
            return nombre;
        }
    }

    /**
     * Lista archivos de Drive filtrados por prefijo
     */
    public void listarArchivos(String prefijo, OnFilesListedListener listener) {
        new ListarArchivosTask(prefijo, listener).execute();
    }

    /**
     * Descarga un archivo de Drive
     */
    public void descargarArchivo(String fileId, String nombreArchivo, OnFileDownloadedListener listener) {
        new DescargarArchivoTask(fileId, nombreArchivo, listener).execute();
    }

    // ==================== ASYNC TASKS ====================

    private class ListarArchivosTask extends AsyncTask<Void, Void, List<DriveFileInfo>> {
        private String prefijo;
        private OnFilesListedListener listener;
        private String errorMsg;

        public ListarArchivosTask(String prefijo, OnFilesListedListener listener) {
            this.prefijo = prefijo;
            this.listener = listener;
        }

        @Override
        protected List<DriveFileInfo> doInBackground(Void... voids) {
            try {
                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(DriveScopes.DRIVE_FILE)
                ).setSelectedAccountName("colombiaje@gmail.com");

                Drive driveService = new Drive.Builder(transport, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                // Buscar archivos en la carpeta con el prefijo
                String query = String.format(
                        "'%s' in parents and name contains '%s' and mimeType='text/csv' and trashed=false",
                        folderId,
                        prefijo
                );

                FileList result = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name, createdTime)")
                        .setOrderBy("createdTime desc")
                        .setPageSize(1000)  // ← AGREGAR ESTA LÍNEA
                        .execute();

                List<File> archivos = result.getFiles();
                List<DriveFileInfo> archivosList = new ArrayList<>();

                if (archivos != null && !archivos.isEmpty()) {
                    for (File archivo : archivos) {
                        archivosList.add(new DriveFileInfo(
                                archivo.getId(),
                                archivo.getName(),
                                archivo.getCreatedTime() != null ? archivo.getCreatedTime().toString() : ""
                        ));
                    }
                    Log.d(TAG, "Archivos encontrados: " + archivosList.size());
                } else {
                    Log.d(TAG, "No se encontraron archivos con prefijo: " + prefijo);
                }

                return archivosList;

            } catch (IOException e) {
                Log.e(TAG, "Error al listar archivos: " + e.getMessage(), e);
                errorMsg = "Error al listar archivos: " + e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<DriveFileInfo> result) {
            if (result != null && listener != null) {
                listener.onFilesListed(result);
            } else if (listener != null) {
                listener.onError(errorMsg != null ? errorMsg : "Error desconocido");
            }
        }
    }

    private class DescargarArchivoTask extends AsyncTask<Void, Void, String> {
        private String fileId;
        private String nombreArchivo;
        private OnFileDownloadedListener listener;
        private String errorMsg;

        public DescargarArchivoTask(String fileId, String nombreArchivo, OnFileDownloadedListener listener) {
            this.fileId = fileId;
            this.nombreArchivo = nombreArchivo;
            this.listener = listener;
        }

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

                // Descargar archivo
                InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream();

                // NUEVO: Usar carpeta de la app (sin permisos especiales)
                // Especificar java.io.File para evitar conflicto con com.google.api.services.drive.model.File
                java.io.File carpetaApp = context.getExternalFilesDir(null);
                String rutaBalance = carpetaApp.getAbsolutePath() + "/Balance/";
                java.io.File carpeta = new java.io.File(rutaBalance);
                if (!carpeta.exists()) {
                    boolean created = carpeta.mkdirs();
                    Log.d(TAG, "Carpeta app creada: " + created + " - " + rutaBalance);
                }

                // Extraer nombre sin timestamp para guardar
                DriveFileInfo info = new DriveFileInfo(fileId, nombreArchivo, "");
                String nombreSinTimestamp = info.nombreSinTimestamp + ".csv";

                String archivoDestino = rutaBalance + nombreSinTimestamp;
                java.io.File outputFile = new java.io.File(archivoDestino);

                Log.d(TAG, "Guardando archivo en: " + archivoDestino);

                // Escribir archivo
                FileOutputStream outputStream = new FileOutputStream(outputFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                Log.d(TAG, "Archivo descargado correctamente: " + archivoDestino);
                Log.d(TAG, "Tamaño archivo: " + outputFile.length() + " bytes");

                // INTENTAR copiar también a ubicación tradicional (para compatibilidad)
                try {
                    String rutaTradicional = Environment.getExternalStorageDirectory().getPath() + CARPETA_BALANCE;
                    java.io.File carpetaTradicional = new java.io.File(rutaTradicional);
                    if (!carpetaTradicional.exists()) {
                        carpetaTradicional.mkdirs();
                    }

                    java.io.File archivoTradicional = new java.io.File(rutaTradicional + nombreSinTimestamp);

                    // Copiar archivo
                    FileInputStream fis = new FileInputStream(outputFile);
                    FileOutputStream fos = new FileOutputStream(archivoTradicional);
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fis.close();
                    fos.close();

                    Log.d(TAG, "Archivo copiado también a ubicación tradicional: " + archivoTradicional.getAbsolutePath());
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo copiar a ubicación tradicional (normal en Android 10+): " + e.getMessage());
                }

                return archivoDestino;

            } catch (IOException e) {
                Log.e(TAG, "Error al descargar archivo: " + e.getMessage(), e);
                errorMsg = "Error al descargar: " + e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && listener != null) {
                listener.onFileDownloaded(result);
            } else if (listener != null) {
                listener.onError(errorMsg != null ? errorMsg : "Error desconocido");
            }
        }
    }
}