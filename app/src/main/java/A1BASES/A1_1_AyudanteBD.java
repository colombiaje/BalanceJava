package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

// Created by JorgeEnrique on 6/08/2016.
// ⭐ MODIFICADO: Versión 2 — Agrega tablas de caché SQLite para reemplazar CSVs de borrador.

public class A1_1_AyudanteBD extends SQLiteOpenHelper {

    // ─────────────────────────────────────────────
    //  CONSTANTES DE BD
    // ─────────────────────────────────────────────
    public static final String balanceSqlite_String_PSF = "balance.db";

    // ⭐ CAMBIO: versión 1 → 2 para disparar onUpgrade en dispositivos existentes.
    public static final int version1BalanceSqlite_int_PSF = 2;

    // ─────────────────────────────────────────────
    //  CONSTANTES DE LAS NUEVAS TABLAS DE CACHÉ
    //  Úsalas desde F1 para evitar strings sueltos.
    // ─────────────────────────────────────────────
    public static final String TABLE_CACHE_HEADER  = "cache_encabezado";
    public static final String TABLE_CACHE_RECORDS = "cache_registros";

    // area_id: 1 = Nuevo | 2 = Plantilla | 3 = Modificar
    public static final int AREA_CREATE   = 1;
    public static final int AREA_TEMPLATE = 2;
    public static final int AREA_UPDATE   = 3;

    // Constructor
    public A1_1_AyudanteBD(Context context, String name, Object o, int i) {
        super(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
    }

    // ─────────────────────────────────────────────
    //  DDL — TABLAS ORIGINALES (sin cambios)
    // ─────────────────────────────────────────────
    String crearTransacciones_String =
            "CREATE TABLE IF NOT EXISTS transacciones(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, c10_Grupo1 TEXT NOT NULL, " +
                    "c11_Grupo2 TEXT NOT NULL, c12_ColumnaDisponible TEXT NOT NULL, " +
                    "c13_ColumnaDisponible TEXT NOT NULL)";

    String crearCuentas_String =
            "CREATE TABLE IF NOT EXISTS cuentas (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Grupo1 TEXT NOT NULL, " +
                    "Grupo2 TEXT NOT NULL, Fecha TEXT NOT NULL)";

    // ─────────────────────────────────────────────
    //  DDL — NUEVAS TABLAS DE CACHÉ  ⭐ NUEVO
    // ─────────────────────────────────────────────

    /**
     * cache_encabezado — guarda los 18 campos de vistas de F1 por área.
     * Reemplaza los 3 archivos CSV de "header" (CSV_CREATE_HEADER, CSV_TEMPLATE_HEADER, CSV_UPDATE_HEADER).
     *
     * Campos mapeados desde utilizacionOrigenBackupCRUDCSVViewsValues():
     *   índice 0  → campo_0_otraFechaChb      (assignedOtherDateInCreateNew_XChB)
     *   índice 1  → campo_1_otraFechaTv        (otherDateInCreateNew_XTv)
     *   índice 2  → campo_2_descPlantilla      (descripcionABuscarEnPlantilla_XAtv)
     *   índice 3  → campo_3_fechaUpdate        (dateInUpdate_XTv)
     *   índice 4  → campo_4_fechaChbTemplate   (assignedDateInTemplate_XChB)
     *   índice 5  → campo_5_numDocPlantilla    (consecutivoNuevoDocEnPLantilla_XTv)
     *   índice 6  → campo_6_fechaTemplate      (dateInTemplate_XTv)
     *   índice 7  → campo_7_docFechaBase       (documentoYFechaInicialBaseDeLaPLantilla_XTv)
     *   índice 8  → campo_8_docBuscarEditar    (documentoABuscarParaEditar_XATv)
     *   índice 9  → campo_9_otraFechaUpdateChb (assignedOtherDateInUpdate_XChB)
     *   índice 10 → campo_10_cambioFechaUpdate (changeOfDateInUpdate_XTv)
     *   índice 11 → campo_11_cuentaConciliacion(cuentaConciliacion_XSp)
     *   índice 12 → campo_12_fisico            (inputPhysicalVsAccounting_XEt)
     *   índice 13 → campo_13_valor             (valor_XEt)
     *   índice 14 → campo_14_descripcion       (descripcion_XAtv)
     *   índice 15 → campo_15_signo             (signo_XSp)
     *   índice 16 → campo_16_cuentaAtv         (cuenta_XAtv)
     *   índice 17 → campo_17_cuentaSp          (cuenta_XSp)
     */
    private static final String SQL_CREAR_CACHE_HEADER =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CACHE_HEADER + " (" +
                    "area_id INTEGER PRIMARY KEY, " +       // 1, 2 o 3
                    "campo_0_otraFechaChb      TEXT, " +
                    "campo_1_otraFechaTv       TEXT, " +
                    "campo_2_descPlantilla     TEXT, " +
                    "campo_3_fechaUpdate       TEXT, " +
                    "campo_4_fechaChbTemplate  TEXT, " +
                    "campo_5_numDocPlantilla   TEXT, " +
                    "campo_6_fechaTemplate     TEXT, " +
                    "campo_7_docFechaBase      TEXT, " +
                    "campo_8_docBuscarEditar   TEXT, " +
                    "campo_9_otraFechaUpChb    TEXT, " +
                    "campo_10_cambioFechaUp    TEXT, " +
                    "campo_11_cuentaConcilia   TEXT, " +
                    "campo_12_fisico           TEXT, " +
                    "campo_13_valor            TEXT, " +
                    "campo_14_descripcion      TEXT, " +
                    "campo_15_signo            TEXT, " +
                    "campo_16_cuentaAtv        TEXT, " +
                    "campo_17_cuentaSp         TEXT)";

    /**
     * cache_registros — guarda los ítems de listaDocumento_ArrayLTT por área.
     * Reemplaza los 3 archivos CSV de "records" (CSV_CREATE_RECORDS, CSV_TEMPLATE_RECORDS, CSV_UPDATE_RECORDS).
     * Estructura idéntica a las columnas de A3_2_TipoTransaccionesGetsYSets
     * para que restoreBackups() pueda reconstruir el ArrayList directamente.
     */
    private static final String SQL_CREAR_CACHE_RECORDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CACHE_RECORDS + " (" +
                    "registro_id      INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "area_id          INTEGER NOT NULL, " +     // FK lógica → cache_encabezado
                    "c1_Documento     TEXT, " +
                    "c2_ItemDoc       TEXT, " +
                    "c3_Cuenta        TEXT, " +
                    "c4_Signo         TEXT, " +
                    "c5_Valor         INTEGER, " +
                    "c6_Descripcion   TEXT, " +
                    "c7_FechaYHora    TEXT, " +
                    "c8_FechaInicial  INTEGER, " +
                    "c9_FechaMod      TEXT, " +
                    "c10_Grupo1       TEXT, " +
                    "c11_Grupo2       TEXT, " +
                    "c12_Col          TEXT, " +
                    "c13_Col          TEXT)";

    // ─────────────────────────────────────────────
    //  COLUMNAS (para uso en queries de F1)
    // ─────────────────────────────────────────────
    public static final String[] columnasTransacciones_ArrayString_PSF = {
            "c1_Documento", "c2_ItemDoc", "c3_Cuenta", "c4_Signo", "c5_Valor",
            "c6_Descripcion", "c7_FechaYhora", "c8_FechaInicial", "c9_FechaModificacion",
            "c10_Grupo1", "c11_Grupo2", "c12_ColumnaDisponible", "c13_ColumnaDisponible"};

    // ─────────────────────────────────────────────
    //  LIFECYCLE DE LA BD
    // ─────────────────────────────────────────────

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA encoding = 'UTF-8'");
        db.execSQL(crearTransacciones_String);
        db.execSQL(crearCuentas_String);
        // ⭐ NUEVO: crear tablas de caché desde el inicio en instalaciones frescas
        db.execSQL(SQL_CREAR_CACHE_HEADER);
        db.execSQL(SQL_CREAR_CACHE_RECORDS);
    }

    // area_id: 1 = Nuevo | 2 = Plantilla | 3 = Modificar | 4 = Modificar en espera

    public static final int AREA_UPDATE_ESPERA = 4; // ← nuevo slot Canal D

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(SQL_CREAR_CACHE_HEADER);
            db.execSQL(SQL_CREAR_CACHE_RECORDS);
        }
        // ⭐ NUEVO: versión 3 — el slot 4 no requiere DDL nuevo,
        // usa las mismas tablas con area_id=4. Sin cambios estructurales.
        // if (oldVersion < 3) { } ← reservado, no necesita nada
    }

    // ─────────────────────────────────────────────
    //  MÉTODOS UTF-8 (sin cambios respecto al original)
    // ─────────────────────────────────────────────

    public long insertWithEncoding(String table, ContentValues values) {
        ContentValues encodedValues = new ContentValues();
        for (String key : values.keySet()) {
            Object value = values.get(key);
            if (value instanceof String) {
                encodedValues.put(key, A2_EncodingUtils.toUTF8((String) value));
            } else {
                encodedValues.put(key, values.getAsString(key));
            }
        }
        return getWritableDatabase().insert(table, null, encodedValues);
    }

    public String getStringFromCursor(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex != -1) {
            String value = cursor.getString(columnIndex);
            return A2_EncodingUtils.fromDatabase(value);
        }
        return null;
    }
}