package A1BASES;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Clase para descargar archivos CSV desde Google Sheets
 * y prepararlos para uso en la app
 */
public class A6_5_SheetsDownloader {

    private static final String TAG = "SheetsDownloader";
    private static final String CARPETA_BALANCE = "/Balance/";
    private static final int TIMEOUT_CONEXION = 15000; // 15 segundos
    private static final int TIMEOUT_LECTURA = 30000;  // 30 segundos

    private Context context;

    public A6_5_SheetsDownloader(Context context) {
        this.context = context;
    }

    /**
     * Interface para callbacks
     */
    public interface OnDownloadListener {
        void onDownloadSuccess(String rutaLocal, int numLineas);
        void onDownloadError(String error);
    }

    /**
     * Descarga CSV desde Google Sheets
     *
     * @param sheetsId          ID del archivo en Sheets
     * @param nombreArchivo     Nombre para guardar localmente
     * @param listener          Callback de resultado
     */
    //* @param SheetsEspecificaAApp
    /*public void descargarCSV(String sheetsId, String nombreArchivo, String SheetsEspecificaAApp, OnDownloadListener listener) {
        new DescargarSheetsTask(sheetsId, nombreArchivo, listener).execute();
    }*/

    public void descargarCSV(String sheetsId, String nombreArchivo, OnDownloadListener listener) {
        new DescargarSheetsTask(sheetsId, nombreArchivo, listener).execute();
    }

    /**
     * AsyncTask para descarga en background
     */
    private class DescargarSheetsTask extends AsyncTask<Void, Integer, ResultadoDescarga> {

        private String sheetsId;
        private String nombreArchivo;
        private OnDownloadListener listener;

        public DescargarSheetsTask(String sheetsId, String nombreArchivo, OnDownloadListener listener) {
            this.sheetsId = sheetsId;
            this.nombreArchivo = nombreArchivo;
            this.listener = listener;
        }

        @Override
        protected ResultadoDescarga doInBackground(Void... voids) {
            ResultadoDescarga resultado = new ResultadoDescarga();
            HttpURLConnection connection = null;

            try {
                Log.d(TAG, "=== INICIO DESCARGA SHEETS ===");
                Log.d(TAG, "Sheets ID: " + sheetsId);

                // 1. Construir URL de exportación
                String urlExport = "https://docs.google.com/spreadsheets/d/" +
                        sheetsId + "/export?format=csv&gid=0";
                Log.d(TAG, "URL: " + urlExport);

                // 2. Configurar conexión
                URL url = new URL(urlExport);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_CONEXION);
                connection.setReadTimeout(TIMEOUT_LECTURA);
                connection.setInstanceFollowRedirects(true);

                // Headers para asegurar descarga
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                // 3. Conectar
                connection.connect();
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response Code: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    resultado.error = "Error HTTP: " + responseCode;
                    return resultado;
                }

                // 4. Verificar carpeta local
                String rutaCarpeta = Environment.getExternalStorageDirectory() + CARPETA_BALANCE;
                File carpeta = new File(rutaCarpeta);
                if (!carpeta.exists()) {
                    boolean created = carpeta.mkdirs();
                    Log.d(TAG, "Carpeta creada: " + created);
                }

                // 5. Preparar archivo destino
                String rutaCompleta = rutaCarpeta + nombreArchivo;
                File archivoDestino = new File(rutaCompleta);
                Log.d(TAG, "Guardando en: " + rutaCompleta);

                // 6. Descargar con validación de contenido
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                FileOutputStream outputStream = new FileOutputStream(archivoDestino);

                String linea;
                int numLineas = 0;
                StringBuilder preview = new StringBuilder();

                while ((linea = reader.readLine()) != null) {
                    numLineas++;

                    // Guardar línea con salto de línea
                    outputStream.write(linea.getBytes("UTF-8"));
                    outputStream.write("\n".getBytes("UTF-8"));

                    // Preview de primeras 3 líneas
                    if (numLineas <= 3) {
                        preview.append("Línea ").append(numLineas).append(": ")
                                .append(linea.substring(0, Math.min(50, linea.length())))
                                .append("...\n");
                    }
                }

                outputStream.flush();
                outputStream.close();
                reader.close();
                inputStream.close();

                Log.d(TAG, "=== DESCARGA EXITOSA ===");
                Log.d(TAG, "Líneas descargadas: " + numLineas);
                Log.d(TAG, "Preview:\n" + preview.toString());
                Log.d(TAG, "Tamaño archivo: " + archivoDestino.length() + " bytes");

                // 7. Validar contenido
                if (numLineas < 2) {
                    resultado.error = "Archivo vacío o sin datos (solo " + numLineas + " líneas)";
                    archivoDestino.delete();
                    return resultado;
                }

                if (archivoDestino.length() < 100) {
                    resultado.error = "Archivo demasiado pequeño (" + archivoDestino.length() + " bytes)";
                    archivoDestino.delete();
                    return resultado;
                }

                // 8. Éxito
                resultado.exito = true;
                resultado.rutaLocal = rutaCompleta;
                resultado.numLineas = numLineas - 1; // Restar header

            } catch (Exception e) {
                Log.e(TAG, "Error en descarga: " + e.getMessage(), e);
                resultado.error = "Error: " + e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return resultado;
        }

        @Override
        protected void onPostExecute(ResultadoDescarga resultado) {
            if (resultado.exito && listener != null) {
                listener.onDownloadSuccess(resultado.rutaLocal, resultado.numLineas);
            } else if (listener != null) {
                listener.onDownloadError(resultado.error != null ? resultado.error : "Error desconocido");
            }
        }
    }

    /**
     * Clase interna para resultado de descarga
     */
    private static class ResultadoDescarga {
        boolean exito = false;
        String rutaLocal = null;
        int numLineas = 0;
        String error = null;
    }

    /**
     * Verifica si un archivo local existe y es válido
     */
    public static boolean archivoExiste(String nombreArchivo) {
        String ruta = Environment.getExternalStorageDirectory() + CARPETA_BALANCE + nombreArchivo;
        File archivo = new File(ruta);
        return archivo.exists() && archivo.length() > 100;
    }

    /**
     * Obtiene info de un archivo local
     */
    public static String obtenerInfoArchivo(String nombreArchivo) {
        String ruta = Environment.getExternalStorageDirectory() + CARPETA_BALANCE + nombreArchivo;
        File archivo = new File(ruta);

        if (!archivo.exists()) {
            return "Archivo no existe";
        }

        long bytes = archivo.length();
        long kb = bytes / 1024;
        return String.format("Tamaño: %d KB (%d bytes)", kb, bytes);
    }

}