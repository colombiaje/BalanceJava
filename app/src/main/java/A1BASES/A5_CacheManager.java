package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;

/**
 * A5_CacheManager — Motor de persistencia de borradores en SQLite.
 *
 * REEMPLAZA para el flujo de borrador a:
 *   A5_1_BackupManager                → guardar()
 *   A5_2_refactorCodeArrayListEndCSV  → (internamente)
 *   A5_3_CSVRestoreViewsValues        → restaurarEncabezado()
 *   A5_4_CSVRestoreRecordsDocument    → restaurarRegistros()
 *
 * USO DESDE F1 (reemplazos directos):
 *   hacerBackupSilencioso()   → CacheManager.guardar(ctx, areaId, enc, lista)
 *   existenBackupsPara()      → CacheManager.existeCache(ctx, areaId)
 *   restoreBackups()          → CacheManager.restaurarEncabezado/restaurarRegistros()
 *   eliminarBackups()         → CacheManager.eliminar(ctx, areaId)
 *   deleteCsvBackupsCRUD()    → CacheManager.eliminar(ctx, areaId)
 */
public class A5_CacheManager {

    private static final String TAG = "CacheManager";

    // ─────────────────────────────────────────────────────────────────────────
    //  MODELO DE ENCABEZADO
    //  18 campos mapeados 1:1 con utilizacionOrigenBackupCRUDCSVViewsValues()
    // ─────────────────────────────────────────────────────────────────────────
    public static class Encabezado {
        public String campo_0_otraFechaChb;      // assignedOtherDateInCreateNew_XChB
        public String campo_1_otraFechaTv;        // otherDateInCreateNew_XTv
        public String campo_2_descPlantilla;      // descripcionABuscarEnPlantilla_XAtv
        public String campo_3_fechaUpdate;        // dateInUpdate_XTv
        public String campo_4_fechaChbTemplate;   // assignedDateInTemplate_XChB
        public String campo_5_numDocPlantilla;    // consecutivoNuevoDocEnPLantilla_XTv
        public String campo_6_fechaTemplate;      // dateInTemplate_XTv
        public String campo_7_docFechaBase;       // documentoYFechaInicialBaseDeLaPLantilla_XTv
        public String campo_8_docBuscarEditar;    // documentoABuscarParaEditar_XATv
        public String campo_9_otraFechaUpChb;     // assignedOtherDateInUpdate_XChB
        public String campo_10_cambioFechaUp;     // changeOfDateInUpdate_XTv
        public String campo_11_cuentaConcilia;    // cuentaConciliacion_XSp
        public String campo_12_fisico;            // inputPhysicalVsAccounting_XEt
        public String campo_13_valor;             // valor_XEt
        public String campo_14_descripcion;       // descripcion_XAtv
        public String campo_15_signo;             // signo_XSp
        public String campo_16_cuentaAtv;         // cuenta_XAtv
        public String campo_17_cuentaSp;          // cuenta_XSp
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GUARDAR  (reemplaza hacerBackupSilencioso)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Guarda encabezado + registros para un área. Operación atómica con transacción.
     *
     * @param context  Context del Fragment
     * @param areaId   AREA_CREATE(1), AREA_TEMPLATE(2) o AREA_UPDATE(3)
     * @param enc      18 campos de vistas
     * @param lista    Items de listaDocumento_ArrayLTT (puede ser null o vacío)
     * @return true si se guardó sin errores
     */
    /*
    public static boolean guardar(Context context,
                                  int areaId,
                                  Encabezado enc,
                                  ArrayList<A3_2_TipoTransaccionesGetsYSets> lista) {

        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getWritableDatabase();
        boolean exito = false;

        db.beginTransaction();
        try {
            // 1. Guardar encabezado (INSERT OR REPLACE por area_id PK)
            ContentValues cv = encabezadoToContentValues(areaId, enc);
            long result = db.insertWithOnConflict(
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);

            if (result == -1) {
                Log.e(TAG, "Error guardando encabezado area_id=" + areaId);
                return false;
            }

            // 2. Limpiar registros anteriores de este área
            db.delete(A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    "area_id = ?", new String[]{String.valueOf(areaId)});

            // 3. Insertar registros actuales
            if (lista != null && !lista.isEmpty()) {
                for (A3_2_TipoTransaccionesGetsYSets item : lista) {
                    ContentValues cvReg = registroToContentValues(areaId, item);
                    db.insert(A1_1_AyudanteBD.TABLE_CACHE_RECORDS, null, cvReg);
                }
            }

            db.setTransactionSuccessful();
            exito = true;
            Log.d(TAG, "Cache guardado OK — area=" + areaId +
                    " registros=" + (lista != null ? lista.size() : 0));

        } catch (Exception e) {
            Log.e(TAG, "Error en guardar() area=" + areaId, e);
        } finally {
            db.endTransaction();
            db.close();
            helper.close();
        }
        return exito;
    }*/

    public static boolean guardarEncabezado(Context context, int areaId, Encabezado enc) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getWritableDatabase();
        boolean exito = false;
        try {
            ContentValues cv = encabezadoToContentValues(areaId, enc);
            long result = db.insertWithOnConflict(
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);
            exito = (result != -1);
            Log.d(TAG, "Encabezado guardado — area=" + areaId + " ok=" + exito);
        } catch (Exception e) {
            Log.e(TAG, "Error guardando encabezado", e);
        } finally {
            db.close();
            helper.close();
        }
        return exito;
    }

    public static boolean guardarRegistros(Context context, int areaId,
                                           ArrayList<A3_2_TipoTransaccionesGetsYSets> lista) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getWritableDatabase();
        boolean exito = false;
        try {
            db.delete(A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    "area_id = ?", new String[]{String.valueOf(areaId)});
            if (lista != null && !lista.isEmpty()) {
                for (A3_2_TipoTransaccionesGetsYSets item : lista) {
                    db.insert(A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                            null, registroToContentValues(areaId, item));
                }
            }
            Log.d("hacer_backup","aqui 1 A5_Cache");
            exito = true;
            Log.d(TAG, "Registros guardados — area=" + areaId +
                    " cantidad=" + (lista != null ? lista.size() : 0));
        } catch (Exception e) {
            Log.e(TAG, "Error guardando registros", e);

        } finally {
            db.close();
            helper.close();
        }
        return exito;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VERIFICAR EXISTENCIA  (reemplaza existenBackupsPara)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna true si existe caché para el área indicada.
     * Equivale al File.exists() del flujo CSV.
     */
    public static boolean existeCache(Context context, int areaId) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getReadableDatabase();
        boolean existe = false;

        try {
            // Verificar encabezado
            Cursor c = db.query(
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    new String[]{"area_id"},
                    "area_id = ?", new String[]{String.valueOf(areaId)},
                    null, null, null);
            if (c != null) {
                existe = c.moveToFirst();
                c.close();
            }

            // Si no hay encabezado, verificar si hay al menos registros
            if (!existe) {
                Cursor cr = db.query(
                        A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                        new String[]{"registro_id"},
                        "area_id = ?", new String[]{String.valueOf(areaId)},
                        null, null, null, "1"); // LIMIT 1
                if (cr != null) {
                    existe = cr.moveToFirst();
                    cr.close();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en existeCache() area=" + areaId, e);
        } finally {
            db.close();
            helper.close();
        }
        return existe;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RESTAURAR ENCABEZADO  (reemplaza origenRestoreCRUDBackupCsvViewsValues)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recupera los 18 campos de vistas para el área dada.
     * @return Encabezado con los valores, o null si no existe caché.
     */
    public static Encabezado restaurarEncabezado(Context context, int areaId) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getReadableDatabase();
        Encabezado enc = null;

        try {
            Cursor c = db.query(
                    A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    null,
                    "area_id = ?", new String[]{String.valueOf(areaId)},
                    null, null, null);

            if (c != null && c.moveToFirst()) {
                enc = new Encabezado();
                enc.campo_0_otraFechaChb     = getString(c, "campo_0_otraFechaChb");
                enc.campo_1_otraFechaTv      = getString(c, "campo_1_otraFechaTv");
                enc.campo_2_descPlantilla    = getString(c, "campo_2_descPlantilla");
                enc.campo_3_fechaUpdate      = getString(c, "campo_3_fechaUpdate");
                enc.campo_4_fechaChbTemplate = getString(c, "campo_4_fechaChbTemplate");
                enc.campo_5_numDocPlantilla  = getString(c, "campo_5_numDocPlantilla");
                enc.campo_6_fechaTemplate    = getString(c, "campo_6_fechaTemplate");
                enc.campo_7_docFechaBase     = getString(c, "campo_7_docFechaBase");
                enc.campo_8_docBuscarEditar  = getString(c, "campo_8_docBuscarEditar");
                enc.campo_9_otraFechaUpChb   = getString(c, "campo_9_otraFechaUpChb");
                enc.campo_10_cambioFechaUp   = getString(c, "campo_10_cambioFechaUp");
                enc.campo_11_cuentaConcilia  = getString(c, "campo_11_cuentaConcilia");
                enc.campo_12_fisico          = getString(c, "campo_12_fisico");
                enc.campo_13_valor           = getString(c, "campo_13_valor");
                enc.campo_14_descripcion     = getString(c, "campo_14_descripcion");
                enc.campo_15_signo           = getString(c, "campo_15_signo");
                enc.campo_16_cuentaAtv       = getString(c, "campo_16_cuentaAtv");
                enc.campo_17_cuentaSp        = getString(c, "campo_17_cuentaSp");
                c.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en restaurarEncabezado() area=" + areaId, e);
        } finally {
            db.close();
            helper.close();
        }
        return enc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RESTAURAR REGISTROS  (reemplaza A5_4_CSVRestoreRecordsDocument)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recupera la lista de registros del documento para el área dada.
     * Usa los SETTERS reales de A3_2_TipoTransaccionesGetsYSets:
     *   tipoTset_1DocumentoMetodoEnA5  → c1_Documento
     *   tipoTset_2ItemDocMetodoEnA5    → c2_ItemDoc
     *   tipoTset_3CuentaMetodoEnA5     → c3_Cuenta
     *   tipoTset_4MasMenosMetodoEnA5   → c4_Signo
     *   tipoTset_5ValorMetodoEnA5      → c5_Valor
     *   tipoTset_6DescripcionMetodoEnA5→ c6_Descripcion
     *   tipoTset_7FechaYHoraMetodoEnA5 → c7_FechaYHora
     *   tipoTset_8FechaInicialMetodoEnA5→c8_FechaInicial
     *   tipoTset_9FechaModificacionMetodoEnA5→ c9_FechaMod
     *   tipoTset_10Grupo1MetodoEnA5    → c10_Grupo1
     *   tipoTset_11Grupo2MetodoEnA5    → c11_Grupo2
     *   tipoTset_12ColumnaDisponibleMetodoEnA5→ c12_Col
     *   tipoTset_13ColumnaDisponibleMetodoEnA5→ c13_Col
     *
     * @return ArrayList listo para asignar a listaDocumento_ArrayLTT, nunca null.
     */
    public static ArrayList<A3_2_TipoTransaccionesGetsYSets> restaurarRegistros(
            Context context, int areaId) {

        ArrayList<A3_2_TipoTransaccionesGetsYSets> lista = new ArrayList<>();
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getReadableDatabase();

        try {
            Cursor c = db.query(
                    A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    null,
                    "area_id = ?", new String[]{String.valueOf(areaId)},
                    null, null, "registro_id ASC"); // preservar orden de entrada

            if (c != null) {
                while (c.moveToNext()) {
                    // Constructor vacío + setters — igual que hace A1_2_OperacionesBD en F1
                    A3_2_TipoTransaccionesGetsYSets item = new A3_2_TipoTransaccionesGetsYSets();

                    item.tipoTset_1DocumentoMetodoEnA5       (getString(c, "c1_Documento"));
                    item.tipoTset_2ItemDocMetodoEnA5         (getString(c, "c2_ItemDoc"));
                    item.tipoTset_3CuentaMetodoEnA5          (getString(c, "c3_Cuenta"));
                    item.tipoTset_4MasMenosMetodoEnA5        (getString(c, "c4_Signo"));
                    item.tipoTset_5ValorMetodoEnA5           (getInt   (c, "c5_Valor"));
                    item.tipoTset_6DescripcionMetodoEnA5     (getString(c, "c6_Descripcion"));
                    item.tipoTset_7FechaYHoraMetodoEnA5      (getString(c, "c7_FechaYHora"));
                    item.tipoTset_8FechaInicialMetodoEnA5    (getInt   (c, "c8_FechaInicial"));
                    item.tipoTset_9FechaModificacionMetodoEnA5(getString(c, "c9_FechaMod"));
                    item.tipoTset_10Grupo1MetodoEnA5         (getString(c, "c10_Grupo1"));
                    item.tipoTset_11Grupo2MetodoEnA5         (getString(c, "c11_Grupo2"));
                    item.tipoTset_12ColumnaDisponibleMetodoEnA5(getString(c, "c12_Col"));
                    item.tipoTset_13ColumnaDisponibleMetodoEnA5(getString(c, "c13_Col"));

                    lista.add(item);
                }
                c.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en restaurarRegistros() area=" + areaId, e);
        } finally {
            db.close();
            helper.close();
        }
        return lista;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ELIMINAR  (reemplaza eliminarBackups + deleteCsvBackupsCRUD)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Borra el caché completo de un área (encabezado + registros). Operación atómica.
     */
    public static void eliminar(Context context, int areaId) {
        A1_1_AyudanteBD helper = new A1_1_AyudanteBD(context, null, null, 0);
        SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.delete(A1_1_AyudanteBD.TABLE_CACHE_HEADER,
                    "area_id = ?", new String[]{String.valueOf(areaId)});
            db.delete(A1_1_AyudanteBD.TABLE_CACHE_RECORDS,
                    "area_id = ?", new String[]{String.valueOf(areaId)});
            db.setTransactionSuccessful();
            Log.d(TAG, "Cache eliminado — area=" + areaId);
        } catch (Exception e) {
            Log.e(TAG, "Error en eliminar() area=" + areaId, e);
        } finally {
            db.endTransaction();
            db.close();
            helper.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAPEO R.id → area_id
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convierte el ID del RadioButton al area_id de la tabla.
     * Recibe los tres R.id desde F1 para no depender de recursos externos.
     *
     * Uso en F1:
     *   int areaId = A5_CacheManager.radioButtonToAreaId(
     *       currentRadioButtonId,
     *       R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);
     */
    public static int radioButtonToAreaId(int radioButtonId,
                                          int idCreate, int idTemplate, int idUpdate) {
        if (radioButtonId == idCreate)   return A1_1_AyudanteBD.AREA_CREATE;
        if (radioButtonId == idTemplate) return A1_1_AyudanteBD.AREA_TEMPLATE;
        if (radioButtonId == idUpdate)   return A1_1_AyudanteBD.AREA_UPDATE;
        return A1_1_AyudanteBD.AREA_UPDATE; // fallback seguro
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mapea Encabezado → ContentValues para INSERT OR REPLACE.
     */
    private static ContentValues encabezadoToContentValues(int areaId, Encabezado enc) {
        ContentValues cv = new ContentValues();
        cv.put("area_id",                  areaId);
        cv.put("campo_0_otraFechaChb",     safe(enc.campo_0_otraFechaChb));
        cv.put("campo_1_otraFechaTv",      safe(enc.campo_1_otraFechaTv));
        cv.put("campo_2_descPlantilla",    safe(enc.campo_2_descPlantilla));
        cv.put("campo_3_fechaUpdate",      safe(enc.campo_3_fechaUpdate));
        cv.put("campo_4_fechaChbTemplate", safe(enc.campo_4_fechaChbTemplate));
        cv.put("campo_5_numDocPlantilla",  safe(enc.campo_5_numDocPlantilla));
        cv.put("campo_6_fechaTemplate",    safe(enc.campo_6_fechaTemplate));
        cv.put("campo_7_docFechaBase",     safe(enc.campo_7_docFechaBase));
        cv.put("campo_8_docBuscarEditar",  safe(enc.campo_8_docBuscarEditar));
        cv.put("campo_9_otraFechaUpChb",   safe(enc.campo_9_otraFechaUpChb));
        cv.put("campo_10_cambioFechaUp",   safe(enc.campo_10_cambioFechaUp));
        cv.put("campo_11_cuentaConcilia",  safe(enc.campo_11_cuentaConcilia));
        cv.put("campo_12_fisico",          safe(enc.campo_12_fisico));
        cv.put("campo_13_valor",           safe(enc.campo_13_valor));
        cv.put("campo_14_descripcion",     safe(enc.campo_14_descripcion));
        cv.put("campo_15_signo",           safe(enc.campo_15_signo));
        cv.put("campo_16_cuentaAtv",       safe(enc.campo_16_cuentaAtv));
        cv.put("campo_17_cuentaSp",        safe(enc.campo_17_cuentaSp));
        return cv;
    }

    /**
     * Mapea un ítem de la lista → ContentValues para INSERT.
     * Usa los GETTERS reales de A3_2_TipoTransaccionesGetsYSets:
     *   tipoTget_1DocumentoMetodoEnA5()           → c1_Documento
     *   tipoTget_2ItemDocMetodoEnA5()             → c2_ItemDoc
     *   tipoTget_3CuentaMetodoEnA5()              → c3_Cuenta
     *   tipoTget_4MasMenosMetodoEnA5()            → c4_Signo
     *   tipoTget_5ValorMetodoEnA5()               → c5_Valor
     *   tipoTget_6DescripcionMetodoEnA5()         → c6_Descripcion
     *   tipoTget_7FechaYHoraMetodoEnA5()          → c7_FechaYHora
     *   tipoTget_8FechaInicialMetodoEnA5()        → c8_FechaInicial
     *   tipoTget_9FechaModificacionMetodoEnA5()   → c9_FechaMod
     *   tipoTget_10Grupo1MetodoEnA5()             → c10_Grupo1
     *   tipoTget_11Grupo2MetodoEnA5()             → c11_Grupo2
     *   tipoTget_12ColumnaDisponibleMetodoEnA5()  → c12_Col
     *   tipoTget_13ColumnaDisponibleMetodoEnA5()  → c13_Col
     */
    private static ContentValues registroToContentValues(int areaId,
                                                         A3_2_TipoTransaccionesGetsYSets item) {
        ContentValues cv = new ContentValues();
        cv.put("area_id",        areaId);
        cv.put("c1_Documento",   safe(item.tipoTget_1DocumentoMetodoEnA5()));
        cv.put("c2_ItemDoc",     safe(item.tipoTget_2ItemDocMetodoEnA5()));
        cv.put("c3_Cuenta",      safe(item.tipoTget_3CuentaMetodoEnA5()));
        cv.put("c4_Signo",       safe(item.tipoTget_4MasMenosMetodoEnA5()));
        cv.put("c5_Valor",       item.tipoTget_5ValorMetodoEnA5());           // Integer directo
        cv.put("c6_Descripcion", safe(item.tipoTget_6DescripcionMetodoEnA5()));
        cv.put("c7_FechaYHora",  safe(item.tipoTget_7FechaYHoraMetodoEnA5()));
        cv.put("c8_FechaInicial",item.tipoTget_8FechaInicialMetodoEnA5());    // Integer directo
        cv.put("c9_FechaMod",    safe(item.tipoTget_9FechaModificacionMetodoEnA5()));
        cv.put("c10_Grupo1",     safe(item.tipoTget_10Grupo1MetodoEnA5()));
        cv.put("c11_Grupo2",     safe(item.tipoTget_11Grupo2MetodoEnA5()));
        cv.put("c12_Col",        safe(item.tipoTget_12ColumnaDisponibleMetodoEnA5()));
        cv.put("c13_Col",        safe(item.tipoTget_13ColumnaDisponibleMetodoEnA5()));
        return cv;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String getString(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return (idx >= 0 && !c.isNull(idx)) ? c.getString(idx) : "";
    }

    private static int getInt(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return (idx >= 0 && !c.isNull(idx)) ? c.getInt(idx) : 0;
    }

    public static void eliminarRegistrosCache(Context context, int areaId) {
        // 🛡️ Ajuste según los 4 argumentos que pide tu constructor
        // Usamos las constantes de tu propia clase de AyudanteBD
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(
                context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF
        );

        SQLiteDatabase db = ayudante.getWritableDatabase();

        // Eliminación por área para evitar "contaminación" [cite: 11, 41]
        db.delete("cache_registros", "area_id = ?", new String[]{String.valueOf(areaId)});
        db.close();
    }

    public static boolean tieneTrabajoPendienteReal(Context context, int areaId) {
        // 🛡️ Usamos los 4 argumentos correctos para tu constructor
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(
                context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF
        );

        SQLiteDatabase db = ayudante.getReadableDatabase();
        int cuenta = 0;

        // 🔍 Consulta ESTRICTA por area_id para evitar contaminación entre áreas
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM cache_registros WHERE area_id = ?",
                new String[]{String.valueOf(areaId)});

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                cuenta = cursor.getInt(0);
            }
            cursor.close();
        }
        db.close();

        return cuenta > 0;
    }
}