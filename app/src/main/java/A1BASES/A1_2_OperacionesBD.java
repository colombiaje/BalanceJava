
package A1BASES;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class A1_2_OperacionesBD extends Activity {


    private A1_1_AyudanteBD ayudante_Class;
    private SQLiteDatabase sqliteDatabase_Abstracta;
    private Context contexto_Context;

    public A1_2_OperacionesBD(Context context) {
        contexto_Context = context;
    }

    public A1_2_OperacionesBD abrirBaseDatos() throws SQLException {
        ayudante_Class = new A1_1_AyudanteBD(contexto_Context, "balance", null, 1);
        sqliteDatabase_Abstracta = ayudante_Class.getReadableDatabase();
        return this;
    }

    public int obtenerUltimoItem() {
        abrirBaseDatos();
        int ultimoItem = 0;

        Cursor cursor = sqliteDatabase_Abstracta.rawQuery(
                "SELECT Item FROM cuentas ORDER BY CAST(Item AS INTEGER) DESC LIMIT 1",
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            String itemString = cursor.getString(0);
            try {
                ultimoItem = Integer.parseInt(itemString);
            } catch (NumberFormatException e) {
                Log.e("BD", "Error al convertir Item: " + itemString);
                ultimoItem = 0;
            }
            cursor.close();
        }

        return ultimoItem;
    }

    public void insertarCuentas(String stringItemDoc, String cuenta_String,
                                String grupo1_String, String grupo2_String, String fecha_String) {
        abrirBaseDatos();
        ContentValues contenedor_ContentValues = new ContentValues();
        contenedor_ContentValues.put("Item", stringItemDoc);
        contenedor_ContentValues.put("Cuenta", cuenta_String);
        contenedor_ContentValues.put("Grupo1", grupo1_String);
        contenedor_ContentValues.put("Grupo2", grupo2_String);
        contenedor_ContentValues.put("Fecha", fecha_String);
        sqliteDatabase_Abstracta.insert("cuentas", null, contenedor_ContentValues);

        // ⭐ AGREGAR ESTO:
        cerrarBaseDatos();
    }

    // Método para cerrar (si no lo tienes)
    public void cerrarBaseDatos() {
        if (sqliteDatabase_Abstracta != null && sqliteDatabase_Abstracta.isOpen()) {
            sqliteDatabase_Abstracta.close();
        }
    }


    public void eliminarTransacciones (String documento_String) {

        abrirBaseDatos();
        sqliteDatabase_Abstracta.delete("transacciones", "c1_Documento" + "="
                + '"'+documento_String+'"', null);

        sqliteDatabase_Abstracta.close();

        }

    public static void borrarRegistros(String tablaX_String, SQLiteDatabase db) {
        db.execSQL("DELETE FROM "+tablaX_String);
    }

    public void eliminarTransaccionesAlgunasCuentas() {

        abrirBaseDatos();
        sqliteDatabase_Abstracta.execSQL("DELETE FROM transacciones WHERE c11_Grupo2 = 'Exigible Conciliable Cerrable' " +
                "OR c11_Grupo2 = 'No exigible No conciliable Cerrable'");

    }

    public void eliminarCuenta(String cuentaSinMovimiento) {
        Log.e("DEBUG_ELIMINAR", "1. Iniciando eliminación de: " + cuentaSinMovimiento);

        if (cuentaSinMovimiento == null || cuentaSinMovimiento.trim().isEmpty()) {
            Log.e("DEBUG_ELIMINAR", "2. Cuenta vacía - CANCELADO");
            return;
        }

        abrirBaseDatos();
        Log.e("DEBUG_ELIMINAR", "3. BD abierta");

        try {
            int filasEliminadas = sqliteDatabase_Abstracta.delete(
                    "cuentas",
                    "Cuenta = ?",
                    new String[]{cuentaSinMovimiento}
            );

            Log.e("DEBUG_ELIMINAR", "4. Filas eliminadas: " + filasEliminadas);

        } catch (Exception e) {
            Log.e("DEBUG_ELIMINAR", "5. ERROR: " + e.getMessage());
        } finally {
            cerrarBaseDatos();
            Log.e("DEBUG_ELIMINAR", "6. BD cerrada");
        }
    }

}

