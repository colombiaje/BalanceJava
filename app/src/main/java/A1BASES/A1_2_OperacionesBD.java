
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

    // ⭐ NUEVO — Fase 4 Objetivo 2: siguiente cuenta_id que asignará AUTOINCREMENT.
    // A diferencia de obtenerUltimoItem() (que puede repetirse si alguna vez se borra la
    // cuenta con el Item más alto, porque solo hace MAX(Item)+1), sqlite_sequence nunca
    // reutiliza un cuenta_id ya usado — por eso Jorge decidió mostrar/guardar este número
    // como "Item" de las cuentas nuevas de aquí en adelante (las cuentas viejas conservan
    // su Item histórico intacto, sin migrar nada).
    public int obtenerProximoCuentaId() {
        abrirBaseDatos();
        int proximoCuentaId = 1;

        Cursor cursor = sqliteDatabase_Abstracta.rawQuery(
                "SELECT seq FROM sqlite_sequence WHERE name = 'cuentas'",
                null
        );

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                proximoCuentaId = cursor.getInt(0) + 1;
            }
            cursor.close();
        }

        return proximoCuentaId;
    }

    public void insertarCuentas(String stringItemDoc, String cuenta_String,
                                String grupo1_String, String grupo2_String, String fecha_String,
                                String cerrable_String, Long tipoCuentaId_Long,
                                String conciliable_String, boolean conInventario_boolean) {
        abrirBaseDatos();
        ContentValues contenedor_ContentValues = new ContentValues();
        contenedor_ContentValues.put("Item", stringItemDoc);
        contenedor_ContentValues.put("Cuenta", cuenta_String);
        // ⭐ CAMBIO — v11 tanda 3 (parte D): "cuentas" pierde Grupo1/Grupo2 (ver A1_1_AyudanteBD,
        // migración v13) — se quitan estos 2 .put(); ya no se escriben (desde parte C
        // grupo1_String/grupo2_String llegaban siempre como "" de todas formas). La firma del
        // método se deja intacta para no tocar a sus llamadores.
        contenedor_ContentValues.put("Fecha", fecha_String);
        // ⭐ NUEVO — Fase 4 (parte B): "Cerrable" o null (ver A1_1_AyudanteBD, migración v6).
        // ⭐ CAMBIO (31-oct, BD v18): se guarda 0/1 (el parámetro sigue siendo "Cerrable"/null).
        contenedor_ContentValues.put("Cerrable", A1_1_AyudanteBD.textoAMarca01(cerrable_String, "Cerrable"));
        // ⭐ NUEVO — v11 tanda 3 (parte A): "Conciliable" o null (ver A1_1_AyudanteBD,
        // migración v12) — mismo criterio que Cerrable arriba.
        contenedor_ContentValues.put("conciliable", A1_1_AyudanteBD.textoAMarca01(conciliable_String, "Conciliable"));
        // ⭐ NUEVO — tanda 2 v10: tipo_cuenta_id ya se llena desde que la cuenta se crea (antes
        // de esto solo lo llenaba, una sola vez, el backfill de la migración v9 — cualquier
        // cuenta creada después quedaba con tipo_cuenta_id NULL hasta que se editara desde el
        // spinner nuevo de F2_Cuentas).
        if (tipoCuentaId_Long != null) {
            contenedor_ContentValues.put("tipo_cuenta_id", tipoCuentaId_Long);
        }
        // ⭐ NUEVO — v12 tanda 3: interruptor "con inventario" (ver A1_1_AyudanteBD, migración
        // v14 / tanda 1). Solo se escribe al CREAR — F2_Cuentas.clickModify() no toca esta
        // columna, así que modificar una cuenta existente nunca cambia su con_inventario.
        contenedor_ContentValues.put("con_inventario", conInventario_boolean ? 1 : 0);
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

    // ⭐ NUEVO — fix v8 (aprobado aparte de la v10): "DELETE FROM cuentas" con las FK activas
    // desde v8 (ver A1_1_AyudanteBD.onOpen) lanza SQLiteConstraintException si alguna fila de
    // "transacciones" todavía apunta (transacciones.cuenta_id) a una cuenta que se va a borrar.
    // Esto pasaba sin excepción en F2_Cuentas al restaurar TODAS las cuentas desde un backup
    // (Sheets, backup local, o Google Drive) — los 3 flujos primero vacían "cuentas" por
    // completo y luego la vuelven a llenar desde el CSV. Las dos funciones de abajo separan
    // ese vaciado en dos pasos seguros, con las FK siempre activas:
    //   1) limpiarReferenciasCuentaId(): suelta (a NULL) las referencias antes del DELETE.
    //      Nunca viola una FK — poner una columna FK en NULL siempre está permitido.
    //   2) repararReferenciasCuentaIdPorNombre(): después de reinsertar "cuentas" desde el CSV,
    //      reempareja cada transacción con su cuenta por nombre (c3_Cuenta = cuentas.Cuenta) —
    //      el mismo backfill ya usado en las migraciones v3 y v5 (ver A1_1_AyudanteBD),
    //      reutilizado aquí en vez de inventar un mecanismo nuevo.
    // Ninguna de las dos toca cuentas ni transacciones fuera de esta ventana de borrado+
    // restauración completa de "cuentas".
    // ⭐ CAMBIO (1-nov, paso 4 / Fase E1): el vínculo de cada transacción con su cuenta ya NO
    // se reconstruye desde transacciones.c3_Cuenta (columna que se retirará en la Fase E2).
    // Antes de soltar las referencias se guarda, en una tabla auxiliar persistente, el NOMBRE
    // de la cuenta de cada transacción (tomado de "cuentas", la fuente actual); al reparar se
    // reempareja por ese nombre con las cuentas reinsertadas. Mismo resultado que antes (las
    // cuentas se identifican por nombre a través de la restauración), sin depender de c3.
    // La tabla es persistente (no TEMP) porque limpiar y reparar usan conexiones distintas.
    private static final String SQL_CREAR_RESPALDO_VINCULO =
            "CREATE TABLE IF NOT EXISTS respaldo_vinculo_cuenta (" +
                    "transaccion_id INTEGER PRIMARY KEY, nombre_cuenta TEXT NOT NULL)";

    public static void limpiarReferenciasCuentaId(SQLiteDatabase db) {
        db.execSQL(SQL_CREAR_RESPALDO_VINCULO);
        // ⭐ (1-nov) NO se vacía el respaldo aquí: si una restauración anterior se interrumpió
        // después de soltar las referencias (como el cierre por la FK de artículos), el respaldo
        // conserva el vínculo de esas transacciones y la siguiente restauración lo recupera. Solo
        // repararReferenciasCuentaIdPorNombre() lo vacía, después de usarlo.
        db.execSQL("INSERT OR REPLACE INTO respaldo_vinculo_cuenta (transaccion_id, nombre_cuenta) " +
                "SELECT t.transaccion_id, c.Cuenta FROM transacciones t " +
                "JOIN cuentas c ON c.cuenta_id = t.cuenta_id");
        db.execSQL("UPDATE transacciones SET cuenta_id = NULL WHERE cuenta_id IS NOT NULL");
    }

    /**
     * @return cuántas transacciones quedaron sin cuenta_id tras el reemparejamiento (su
     * c3_Cuenta no tuvo match exacto en cuentas.Cuenta) — el llamador decide cómo registrarlo.
     */
    public static int repararReferenciasCuentaIdPorNombre(SQLiteDatabase db) {
        // ⭐ CAMBIO (1-nov, Fase E1): reempareja desde el respaldo guardado en
        // limpiarReferenciasCuentaId() (nombre de la cuenta según "cuentas"), no desde c3_Cuenta.
        db.execSQL(SQL_CREAR_RESPALDO_VINCULO);
        db.execSQL("UPDATE transacciones SET cuenta_id = " +
                "(SELECT c.cuenta_id FROM respaldo_vinculo_cuenta r " +
                "JOIN cuentas c ON c.Cuenta = r.nombre_cuenta " +
                "WHERE r.transaccion_id = transacciones.transaccion_id) " +
                "WHERE cuenta_id IS NULL AND transaccion_id IN " +
                "(SELECT transaccion_id FROM respaldo_vinculo_cuenta)");
        db.execSQL("DELETE FROM respaldo_vinculo_cuenta");
        // ⭐ RED DE SEGURIDAD TEMPORAL (1-nov, hasta la Fase E2): mientras la columna c3_Cuenta
        // exista, las transacciones que sigan sin cuenta se reemparejan por ese nombre. Sirve
        // para recuperar la restauración interrumpida del 1-nov en el dispositivo de Jorge. En la
        // E2 la columna desaparece y este paso se omite solo.
        Cursor col = db.rawQuery("PRAGMA table_info(transacciones)", null);
        boolean existeC3 = false;
        while (col.moveToNext()) {
            if ("c3_Cuenta".equalsIgnoreCase(col.getString(1))) existeC3 = true;
        }
        col.close();
        if (existeC3) {
            db.execSQL("UPDATE transacciones SET cuenta_id = " +
                    "(SELECT cuenta_id FROM cuentas WHERE cuentas.Cuenta = transacciones.c3_Cuenta) " +
                    "WHERE cuenta_id IS NULL");
        }

        int huerfanas = 0;
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM transacciones WHERE cuenta_id IS NULL", null);
        if (cursor.moveToFirst()) {
            huerfanas = cursor.getInt(0);
        }
        cursor.close();
        return huerfanas;
    }

    // ⭐ NUEVO (1-nov, bug encontrado por Jorge al probar la Fase E1): restaurar las cuentas
    // cerraba la app con "FOREIGN KEY constraint failed (1811)" cuando existían artículos de
    // inventario. items_inventario.cuenta_id es NOT NULL y su FK hacia "cuentas" es ON DELETE
    // RESTRICT: no se puede soltar (como sí se hace con transacciones) ni borrar la cuenta.
    // Solución: (1) se guarda el nombre de la cuenta de cada artículo en una tabla auxiliar,
    // (2) el DELETE de "cuentas" se hace con las FK apagadas SOLO en esa conexión y SOLO para esa
    // sentencia, y (3) al terminar de reinsertar, cada artículo se reapunta a su cuenta por
    // nombre. Los artículos cuya cuenta no venga en el backup quedan contados (aviso).
    private static final String SQL_CREAR_RESPALDO_ARTICULOS =
            "CREATE TABLE IF NOT EXISTS respaldo_vinculo_articulo (" +
                    "item_id INTEGER PRIMARY KEY, nombre_cuenta TEXT NOT NULL)";

    public static void respaldarVinculoArticulos(SQLiteDatabase db) {
        db.execSQL(SQL_CREAR_RESPALDO_ARTICULOS);
        // (1-nov) Igual que el respaldo de transacciones: no se vacía aquí, solo al reparar.
        db.execSQL("INSERT OR REPLACE INTO respaldo_vinculo_articulo (item_id, nombre_cuenta) " +
                "SELECT i.item_id, c.Cuenta FROM items_inventario i " +
                "JOIN cuentas c ON c.cuenta_id = i.cuenta_id");
    }

    /** Borra todas las cuentas con las FK apagadas en esta conexión (ver arriba). */
    public static void borrarCuentasSinRestriccionDeArticulos(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(false);
        try {
            db.execSQL("DELETE FROM cuentas");
        } finally {
            db.setForeignKeyConstraintsEnabled(true);
        }
    }

    /** @return cuántos artículos quedaron apuntando a una cuenta que ya no existe. */
    public static int repararVinculoArticulos(SQLiteDatabase db) {
        db.execSQL(SQL_CREAR_RESPALDO_ARTICULOS);
        db.execSQL("UPDATE items_inventario SET cuenta_id = " +
                "(SELECT c.cuenta_id FROM respaldo_vinculo_articulo r " +
                "JOIN cuentas c ON c.Cuenta = r.nombre_cuenta " +
                "WHERE r.item_id = items_inventario.item_id) " +
                "WHERE item_id IN (SELECT r.item_id FROM respaldo_vinculo_articulo r " +
                "JOIN cuentas c ON c.Cuenta = r.nombre_cuenta)");
        db.execSQL("DELETE FROM respaldo_vinculo_articulo");
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM items_inventario " +
                "WHERE cuenta_id NOT IN (SELECT cuenta_id FROM cuentas)", null);
        int sinCuenta = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return sinCuenta;
    }

    public void eliminarTransaccionesAlgunasCuentas() {

        abrirBaseDatos();
        // ⭐ CAMBIO — Fase 4 (parte A): antes identificaba las cuentas "Cerrable" comparando el
        // texto exacto de Grupo2. Desde la migración v6 ese texto ya no lleva la palabra
        // "Cerrable" (ver A1_1_AyudanteBD); el atributo vive en su propia columna, con snapshot
        // por transacción en c12_ColumnaDisponible. Misma lógica de negocio, misma selección de
        // filas — solo cambia por dónde se identifica.
        // ⭐ CAMBIO (31-oct, paso 4 / Fase C, decisión de Jorge): se usa la marca ACTUAL de la
        // cuenta (cuentas.Cerrable = 1), no la foto guardada en cada transacción (c12, retirada
        // en la BD v20). Mismo emparejamiento que los resúmenes de cierre: por cuenta_id y, si
        // falta, por nombre.
        sqliteDatabase_Abstracta.execSQL("DELETE FROM transacciones WHERE " + A1_1_AyudanteBD.CONDICION_TRANSACCION_CERRABLE);

    }

    // ⭐ NUEVO (31-oct): último error al eliminar una cuenta, para mostrarlo al usuario.
    public String ultimoErrorEliminarCuenta = null;

    /**
     * Cuenta los artículos de inventario de una cuenta (por nombre). Sirve para pedir
     * confirmación antes de eliminarla, ya que se borran junto con ella.
     */
    public int contarArticulosDeCuenta(String nombreCuenta) {
        abrirBaseDatos();
        try {
            android.database.Cursor c = sqliteDatabase_Abstracta.rawQuery(
                    "SELECT COUNT(*) FROM items_inventario WHERE cuenta_id = " +
                            "(SELECT cuenta_id FROM cuentas WHERE Cuenta = ?)",
                    new String[]{nombreCuenta});
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } finally {
            cerrarBaseDatos();
        }
    }

    /**
     * Elimina una cuenta SIN movimientos.
     * ⭐ CAMBIO (31-oct, bug reportado por Jorge: "parece eliminarse pero vuelve a aparecer"):
     * antes, si la cuenta tenía artículos de inventario (items_inventario, FK con ON DELETE
     * RESTRICT), SQLite rechazaba el borrado, el error se tragaba aquí y la pantalla igual
     * decía "Se borró la cuenta". Ahora, en una sola transacción, se borran primero sus
     * artículos — solo si ninguno tiene movimientos (transacciones_inventario) — y luego la
     * cuenta. Devuelve cuántas cuentas se borraron (1) o -1 si no se pudo, con el motivo en
     * ultimoErrorEliminarCuenta.
     */
    public int eliminarCuenta(String cuentaSinMovimiento) {
        ultimoErrorEliminarCuenta = null;
        if (cuentaSinMovimiento == null || cuentaSinMovimiento.trim().isEmpty()) {
            ultimoErrorEliminarCuenta = "Falta el nombre de la cuenta";
            return -1;
        }

        abrirBaseDatos();
        sqliteDatabase_Abstracta.beginTransaction();
        try {
            android.database.Cursor c = sqliteDatabase_Abstracta.rawQuery(
                    "SELECT COUNT(*) FROM transacciones_inventario WHERE item_id IN " +
                            "(SELECT item_id FROM items_inventario WHERE cuenta_id = " +
                            "(SELECT cuenta_id FROM cuentas WHERE Cuenta = ?))",
                    new String[]{cuentaSinMovimiento});
            int movimientosInventario = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            if (movimientosInventario > 0) {
                ultimoErrorEliminarCuenta = "La cuenta tiene movimientos de inventario";
                return -1;
            }

            sqliteDatabase_Abstracta.delete("items_inventario",
                    "cuenta_id = (SELECT cuenta_id FROM cuentas WHERE Cuenta = ?)",
                    new String[]{cuentaSinMovimiento});
            int filasEliminadas = sqliteDatabase_Abstracta.delete(
                    "cuentas", "Cuenta = ?", new String[]{cuentaSinMovimiento});
            if (filasEliminadas == 0) {
                ultimoErrorEliminarCuenta = "No se encontró la cuenta";
                return -1;
            }
            sqliteDatabase_Abstracta.setTransactionSuccessful();
            return filasEliminadas;
        } catch (Exception e) {
            Log.e("DEBUG_ELIMINAR", "ERROR al eliminar cuenta: " + e.getMessage(), e);
            ultimoErrorEliminarCuenta = e.getMessage();
            return -1;
        } finally {
            sqliteDatabase_Abstracta.endTransaction();
            cerrarBaseDatos();
        }
    }

}

