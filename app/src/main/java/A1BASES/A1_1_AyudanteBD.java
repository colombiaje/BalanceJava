package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

// Created by JorgeEnrique on 6/08/2016.
// ⭐ MODIFICADO: Versión 2 — Agrega tablas de caché SQLite para reemplazar CSVs de borrador.
// ⭐ MODIFICADO: Versión 3 — Fase 1 de reestructuración: "cuentas" gana cuenta_id (llave
//   primaria técnica) y codigo_cuenta (código de plan de cuentas, lo llena el usuario a su
//   ritmo); "transacciones" gana cuenta_id como referencia hacia cuentas.cuenta_id. El nombre
//   de cuenta deja de ser la identidad y pasa a ser un atributo editable. No se toca ninguna
//   columna existente ni se cambia el comportamiento de nada que ya funcione.
// ⭐ MODIFICADO: Versión 4 — Fase 2: las categorías de Grupo1 y Grupo2 (antes hardcodeadas en
//   F2_Cuentas.java) pasan a vivir en dos tablas catálogo (catalogo_grupo1, catalogo_grupo2),
//   sembradas con exactamente los mismos valores y el mismo orden de siempre. El comportamiento
//   de la app no cambia hoy; lo que cambia es que esas categorías ya no exigen recompilar la app.
// ⭐ MODIFICADO: Versión 5 — Fase 3 (parte A): corrige que las transacciones nuevas guardadas
//   desde la app no estaban recibiendo cuenta_id (el backfill de la Fase 1/versión 3 solo
//   corrió una vez, sobre lo que existía en ese momento; el INSERT de transacciones nunca lo
//   llenaba desde entonces — ver B12_DocumentPersistence). Este backfill corrige, de forma
//   idempotente, cualquier transacción que haya quedado con cuenta_id NULL entre la versión 3
//   y esta corrección. El INSERT ya se corrigió aparte para que esto no vuelva a ocurrir.

public class A1_1_AyudanteBD extends SQLiteOpenHelper {

    // ─────────────────────────────────────────────
    //  CONSTANTES DE BD
    // ─────────────────────────────────────────────
    public static final String balanceSqlite_String_PSF = "balance.db";

    // ⭐ CAMBIO: versión 4 → 5 para disparar onUpgrade en dispositivos existentes (ver Fase 3 parte A arriba).
    public static final int version1BalanceSqlite_int_PSF = 5;

    // ─────────────────────────────────────────────
    //  CONSTANTES DE LOS CATÁLOGOS DE GRUPO1/GRUPO2  ⭐ NUEVO v4
    // ─────────────────────────────────────────────
    public static final String TABLE_CATALOGO_GRUPO1 = "catalogo_grupo1";
    public static final String TABLE_CATALOGO_GRUPO2 = "catalogo_grupo2";

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
                    "c13_ColumnaDisponible TEXT NOT NULL, " +
                    // ⭐ NUEVO v3: referencia real hacia cuentas.cuenta_id (ver Fase 1).
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id))";

    String crearCuentas_String =
            "CREATE TABLE IF NOT EXISTS cuentas (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Grupo1 TEXT NOT NULL, " +
                    "Grupo2 TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    // ⭐ NUEVO v3: llave primaria técnica + código de plan de cuentas, ambas al
                    // final para no correr el orden posicional de ningún query existente (Fase 1).
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT)";

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
    //  DDL — CATÁLOGOS DE GRUPO1/GRUPO2  ⭐ NUEVO v4 (Fase 2)
    //  Reemplazan los arrays hardcodeados de F2_Cuentas.java. "orden" conserva el mismo
    //  orden de aparición que tenían los arrays, para que los spinners se vean exactamente igual.
    // ─────────────────────────────────────────────
    private static final String SQL_CREAR_CATALOGO_GRUPO1 =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CATALOGO_GRUPO1 + " (" +
                    "grupo1_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "nombre TEXT NOT NULL UNIQUE, " +
                    "orden INTEGER NOT NULL)";

    private static final String SQL_CREAR_CATALOGO_GRUPO2 =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CATALOGO_GRUPO2 + " (" +
                    "grupo2_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "nombre TEXT NOT NULL UNIQUE, " +
                    "orden INTEGER NOT NULL)";

    // Valores de siempre, en el mismo orden que tenían los arrays hardcodeados en F2_Cuentas.java.
    // Se conservan tal cual (incluida la falta de tilde en "produccion") para no alterar ningún
    // valor ya guardado en cuentas.Grupo1/Grupo2 ni en transacciones.c10_Grupo1/c11_Grupo2.
    private static final String[] SEED_GRUPO1 = {
            "Activo", "Pasivo", "Patrimonio", "Ingresos", "Costo de ventas", "Gastos",
            "Costos de produccion", "Cuentas de orden Db", "Cuentas de orden Cr"};

    private static final String[] SEED_GRUPO2 = {
            "Exigible Conciliable", "Exigible Conciliable Cerrable", "Exigible No conciliable",
            "No exigible Conciliable", "No exigible No conciliable", "No exigible No conciliable Cerrable"};

    private void sembrarCatalogosGrupo1Y2(SQLiteDatabase db) {
        for (int i = 0; i < SEED_GRUPO1.length; i++) {
            ContentValues cv = new ContentValues();
            cv.put("nombre", SEED_GRUPO1[i]);
            cv.put("orden", i + 1);
            db.insert(TABLE_CATALOGO_GRUPO1, null, cv);
        }
        for (int i = 0; i < SEED_GRUPO2.length; i++) {
            ContentValues cv = new ContentValues();
            cv.put("nombre", SEED_GRUPO2[i]);
            cv.put("orden", i + 1);
            db.insert(TABLE_CATALOGO_GRUPO2, null, cv);
        }
    }

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
        // ⭐ v3: "cuentas" se crea primero porque "transacciones" ahora la referencia
        // (cuenta_id REFERENCES cuentas.cuenta_id) — orden lógico padre→hijo.
        db.execSQL(crearCuentas_String);
        db.execSQL(crearTransacciones_String);
        // ⭐ NUEVO: crear tablas de caché desde el inicio en instalaciones frescas
        db.execSQL(SQL_CREAR_CACHE_HEADER);
        db.execSQL(SQL_CREAR_CACHE_RECORDS);
        // ⭐ NUEVO v4 — Fase 2: catálogos de Grupo1/Grupo2, sembrados desde el inicio.
        db.execSQL(SQL_CREAR_CATALOGO_GRUPO1);
        db.execSQL(SQL_CREAR_CATALOGO_GRUPO2);
        sembrarCatalogosGrupo1Y2(db);
    }

    // area_id: 1 = Nuevo | 2 = Plantilla | 3 = Modificar | 4 = Modificar en espera

    public static final int AREA_UPDATE_ESPERA = 4; // ← nuevo slot Canal D

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(SQL_CREAR_CACHE_HEADER);
            db.execSQL(SQL_CREAR_CACHE_RECORDS);
        }

        // ⭐ NUEVO v3 — Fase 1 de reestructuración de la BD (cuenta_id + codigo_cuenta).
        // El slot area_id=4 (Canal D) no necesitó DDL nuevo; esta sí es una migración real.
        if (oldVersion < 3) {

            // 1) Recrear "cuentas" agregando cuenta_id (llave primaria técnica autoincremental)
            //    y codigo_cuenta (lo llena el usuario a su propio ritmo), preservando todos los
            //    datos existentes tal cual. Ninguna columna ni valor actual se pierde o cambia.
            db.execSQL("CREATE TABLE cuentas_temp_v3 (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Grupo1 TEXT NOT NULL, " +
                    "Grupo2 TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT)");
            db.execSQL("INSERT INTO cuentas_temp_v3 (Item, Cuenta, Grupo1, Grupo2, Fecha) " +
                    "SELECT Item, Cuenta, Grupo1, Grupo2, Fecha FROM cuentas");
            db.execSQL("DROP TABLE cuentas");
            db.execSQL("ALTER TABLE cuentas_temp_v3 RENAME TO cuentas");

            // 2) Recrear "transacciones" agregando cuenta_id (referencia hacia cuentas.cuenta_id),
            //    preservando las 13 columnas c1..c13 existentes exactamente igual que hoy.
            db.execSQL("CREATE TABLE transacciones_temp_v3(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, c10_Grupo1 TEXT NOT NULL, " +
                    "c11_Grupo2 TEXT NOT NULL, c12_ColumnaDisponible TEXT NOT NULL, " +
                    "c13_ColumnaDisponible TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v3 (" +
                    "c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, c6_Descripcion, " +
                    "c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, c10_Grupo1, c11_Grupo2, " +
                    "c12_ColumnaDisponible, c13_ColumnaDisponible) " +
                    "SELECT c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, c6_Descripcion, " +
                    "c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, c10_Grupo1, c11_Grupo2, " +
                    "c12_ColumnaDisponible, c13_ColumnaDisponible FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v3 RENAME TO transacciones");

            // 3) Backfill: emparejar cada transacción con su cuenta por nombre (c3_Cuenta = Cuenta).
            //    Si en "cuentas" hay nombres duplicados, el emparejamiento entre esos duplicados
            //    queda arbitrario — no aplica hoy porque no hay UNIQUE, se deja registrado abajo.
            db.execSQL("UPDATE transacciones SET cuenta_id = " +
                    "(SELECT cuenta_id FROM cuentas WHERE cuentas.Cuenta = transacciones.c3_Cuenta) " +
                    "WHERE cuenta_id IS NULL");

            // 4) Diagnóstico: cuántas transacciones quedaron sin cuenta_id (c3_Cuenta sin match
            //    exacto en cuentas.Cuenta). No detiene la migración; solo se reporta en el log
            //    para poder revisar después cuáles nombres de cuenta no calzaron.
            Cursor huerfanas = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE cuenta_id IS NULL", null);
            if (huerfanas.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v3: " + huerfanas.getInt(0) +
                                " transacciones sin cuenta_id (c3_Cuenta sin match en cuentas.Cuenta)");
            }
            huerfanas.close();
        }

        // ⭐ NUEVO v4 — Fase 2: catálogos de Grupo1/Grupo2 (ver comentario arriba de la clase).
        // No modifica cuentas ni transacciones; solo crea y siembra las dos tablas nuevas.
        if (oldVersion < 4) {
            db.execSQL(SQL_CREAR_CATALOGO_GRUPO1);
            db.execSQL(SQL_CREAR_CATALOGO_GRUPO2);
            sembrarCatalogosGrupo1Y2(db);
        }

        // ⭐ NUEVO v5 — Fase 3 (parte A): backfill correctivo de cuenta_id (ver comentario
        // arriba de la clase). Idempotente: solo toca filas con cuenta_id IS NULL, igual que
        // el backfill original de la versión 3, así que es seguro correrlo aunque ya no queden
        // huérfanas.
        if (oldVersion < 5) {
            db.execSQL("UPDATE transacciones SET cuenta_id = " +
                    "(SELECT cuenta_id FROM cuentas WHERE cuentas.Cuenta = transacciones.c3_Cuenta) " +
                    "WHERE cuenta_id IS NULL");

            Cursor huerfanasV5 = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE cuenta_id IS NULL", null);
            if (huerfanasV5.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v5: " + huerfanasV5.getInt(0) +
                                " transacciones sin cuenta_id tras el backfill correctivo " +
                                "(c3_Cuenta sin match en cuentas.Cuenta)");
            }
            huerfanasV5.close();
        }
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