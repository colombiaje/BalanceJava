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
// ⭐ MODIFICADO: Versión 6 — Fase 4 (parte A): separa el atributo "Cerrable" del texto de
//   Grupo2. Antes "Cerrable" venía mezclado dentro de dos combinaciones de Grupo2
//   ("Exigible Conciliable Cerrable" / "No exigible No conciliable Cerrable"), lo que obligaba
//   a todo el código que necesitaba saber si una cuenta era cerrable a comparar ese texto exacto.
//   Ahora "cuentas" gana una columna propia "Cerrable" (texto, valor literal "Cerrable" o NULL
//   cuando no aplica), y se le quita la palabra "Cerrable" al texto de Grupo2 en cuentas y en el
//   snapshot histórico de transacciones (c11_Grupo2). El snapshot por transacción se guarda en
//   la columna c12_ColumnaDisponible, que ya existía en el esquema pero nunca se usaba de verdad
//   (B11_DocumentCalculator la llenaba siempre con el texto fijo "na" en toda transacción nueva
//   — se verificó que ningún otro punto de la app depende de ese valor literal, así que no hace
//   falta agregar columna nueva ahí). Como esa columna es NOT NULL, el caso "no aplica" se guarda
//   como el texto literal "No Aplica", mientras que en "cuentas" (columna sí nullable) "no
//   aplica" se guarda como NULL — mismo significado, distinta representación por la restricción
//   de cada columna. Se migran en el mismo paso las dos consultas operativas que dependían del
//   texto viejo de Grupo2 (el cierre parcial en A1_2_OperacionesBD y el resumen de respaldo en
//   A5_1_BackupManager) para que lean la columna nueva — si se separaran en pasos distintos, el
//   cierre parcial quedaría temporalmente roto entre uno y otro.
// ⭐ MODIFICADO: Versión 7 — Fase 4 (parte B): B11_DocumentCalculator ya escribe el valor real
//   ("Cerrable" o "No Aplica") en c12_ColumnaDisponible para toda transacción nueva, en vez del
//   "na" fijo de siempre — y se agrega el checkbox en F2_Cuentas para que el usuario pueda
//   marcar una cuenta como Cerrable. Esta versión solo corrige, de forma idempotente, cualquier
//   transacción que se haya creado ENTRE la v6 (parte A, ya instalada) y esta corrección: en ese
//   intervalo, B11_DocumentCalculator todavía escribía "na" (no había cambiado), así que esas
//   filas puntuales quedaron con "na" en vez de "Cerrable"/"No Aplica". No es un cambio de
//   esquema, solo un backfill de continuidad.
// ⭐ MODIFICADO: Versión 8 — Fase 5 (primer paso del modelo nuevo acordado): "transacciones"
//   gana transaccion_id, una llave primaria técnica propia (hasta ahora la tabla solo tenía el
//   rowid interno de SQLite, sin nombre ni columna visible). Es el requisito de base para que,
//   en una versión futura, "transacciones_inventario" pueda enlazarse 1 a 1 con cada transacción
//   por un id real en vez de depender del rowid implícito. Se agrega AL FINAL de la tabla, no al
//   principio — ver el comentario junto a crearTransacciones_String más abajo sobre por qué.
//   De paso: se activa el cumplimiento de llaves foráneas (PRAGMA foreign_keys, vía onOpen —
//   ver más abajo); ya existía la referencia cuenta_id → cuentas.cuenta_id desde la versión 3,
//   pero sin esto SQLite nunca la hacía cumplir de verdad. Y se agregan índices: uno único sobre
//   (c1_Documento, c2_ItemDoc) de forma "best effort" (no debe tumbar la migración si hay
//   duplicados reales en el dispositivo — todavía no se ha verificado), y tres simples
//   (cuenta_id, c1_Documento, c8_FechaInicial) para acelerar las consultas que ya existen hoy. No
//   se toca ninguna columna existente ni su contenido.
// ⭐ CORRECCIÓN: Versión 8 (parte B) — el cumplimiento de llaves foráneas de arriba se activaba
//   originalmente en onConfigure() (que corre ANTES de onUpgrade()), lo que hizo que la propia
//   migración de esta versión fallara al copiar filas con un cuenta_id huérfano (que ya existían
//   en dispositivos reales, sin que ninguna versión anterior lo hubiera detectado) — la app
//   quedaba sin poder abrir la base de datos. Se mueve a onOpen() (corre DESPUÉS de que la
//   migración ya terminó) para que la migración copie los datos tal cual, sin bloquear por eso;
//   ver el comentario junto a onOpen() más abajo para el detalle completo.
// ⭐ MODIFICADO: Versión 9 — Fase 6 (primer paso del modelo de clasificación contable
//   definitivo, ver Documento 3 y la hoja "Modelo — Tablas/Columnas" del modelo aprobado). Se
//   crean, EN PARALELO a Grupo1/Grupo2 (que NO se tocan ni se borran todavía), tres tablas
//   nuevas: clase_contable (9 filas, exactamente los mismos 9 valores y orden que ya tenía
//   SEED_GRUPO1 — Activo, Pasivo, Patrimonio, Ingresos, Costo de ventas, Gastos, Costos de
//   producción, Cuentas de orden Db, Cuentas de orden Cr), clasificacion_contable (11 filas:
//   Activo y Pasivo se dividen en "corriente"/"no corriente" según el prefijo "Exigible"/"No
//   exigible" que ya traía Grupo2 desde antes de la versión 6; las otras 7 clases quedan con una
//   sola clasificación genérica, del mismo nombre que la clase) y tipo_cuenta (vacía al crearse;
//   se llena dinámicamente en el backfill de "cuentas" — ver el punto 4 de más abajo).
//   IMPORTANTE: los Apéndices A/B/C del Documento 3 están marcados ahí mismo como "Ejemplo" — no
//   son el catálogo real de Jorge — así que tipo_cuenta NO se siembra con esos valores de
//   ejemplo. En vez de eso, se genera un tipo_cuenta por cada combinación distinta de
//   (Grupo1, Grupo2) que exista de verdad hoy en "cuentas", nombrado de forma trazable como
//   "{Grupo1} - {Grupo2}" (p.ej. "Activo - Exigible Conciliable"), para poder auditar después de
//   dónde salió cada uno y hacer un mapeo fino más adelante si Jorge lo necesita. "cuentas" gana
//   tipo_cuenta_id (nullable, referencia hacia tipo_cuenta.tipo_cuenta_id) y cada fila existente
//   se actualiza según su combinación de Grupo1/Grupo2. Ver el bloque "if (oldVersion < 9)" en
//   onUpgrade() para el detalle completo, y sembrarClaseYClasificacionContable()/
//   clasificacionNombreParaCombo() para la lógica de nombres.

public class A1_1_AyudanteBD extends SQLiteOpenHelper {

    // ─────────────────────────────────────────────
    //  CONSTANTES DE BD
    // ─────────────────────────────────────────────
    public static final String balanceSqlite_String_PSF = "balance.db";

    // ⭐ CAMBIO: versión 8 → 9 para disparar onUpgrade en dispositivos existentes (ver Fase 6 arriba).
    public static final int version1BalanceSqlite_int_PSF = 9;

    // ─────────────────────────────────────────────
    //  CONSTANTES DE LOS CATÁLOGOS DE GRUPO1/GRUPO2  ⭐ NUEVO v4
    // ─────────────────────────────────────────────
    public static final String TABLE_CATALOGO_GRUPO1 = "catalogo_grupo1";
    public static final String TABLE_CATALOGO_GRUPO2 = "catalogo_grupo2";

    // ─────────────────────────────────────────────
    //  CONSTANTES DE LAS TABLAS DEL MODELO DE CLASIFICACIÓN CONTABLE DEFINITIVO  ⭐ NUEVO v9
    //  Conviven en paralelo con Grupo1/Grupo2 (ver comentario de clase arriba, Fase 6). NO se
    //  siembran con los ejemplos del Documento 3 — ver sembrarClaseYClasificacionContable() y el
    //  backfill dinámico en onUpgrade() para el detalle de dónde sale cada valor real.
    // ─────────────────────────────────────────────
    public static final String TABLE_CLASE_CONTABLE = "clase_contable";
    public static final String TABLE_CLASIFICACION_CONTABLE = "clasificacion_contable";
    public static final String TABLE_TIPO_CUENTA = "tipo_cuenta";

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
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    // ⭐ NUEVO v8 — Fase 5: llave primaria técnica propia de "transacciones", en el
                    // mismo espíritu que cuenta_id en "cuentas" (Fase 1). Se agrega AL FINAL,
                    // después de cuenta_id, y NO al principio como suele ser lo habitual:
                    // A21_OptimizedQuery.mapTransactionFromCursor lee las columnas c1..c13 de un
                    // "SELECT *" por POSICIÓN fija (cursor.getString(0)..getString(12)), y solo lee
                    // cuenta_id aparte, por nombre. Si transaccion_id se agregara al principio, esas
                    // 13 posiciones se recorrerían un lugar a la derecha y cada campo leído por
                    // índice numérico quedaría silenciosamente cruzado con el campo vecino — sin
                    // ningún error, solo datos mal leídos (afecta, entre otros, a
                    // A22_QueryManager.queryAllTransactions/queryTransactionsByDocument/
                    // queryTransactionsByAccount, que alimentan pantallas y el backup a CSV).
                    // Agregarla al final evita ese riesgo sin tocar ese mapeo, y no cambia en nada
                    // el comportamiento de la llave: SQLite no exige que "INTEGER PRIMARY KEY" sea
                    // la primera columna para funcionar como alias del rowid. Ver el bloque
                    // "if (oldVersion < 8)" en onUpgrade() para cómo se agrega en dispositivos que
                    // ya tienen la tabla creada, y mapTransactionFromCursor (A21_OptimizedQuery) para
                    // cómo se lee de vuelta.
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT)";

    String crearCuentas_String =
            "CREATE TABLE IF NOT EXISTS cuentas (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Grupo1 TEXT NOT NULL, " +
                    "Grupo2 TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    // ⭐ NUEVO v3: llave primaria técnica + código de plan de cuentas, ambas al
                    // final para no correr el orden posicional de ningún query existente (Fase 1).
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT, " +
                    // ⭐ NUEVO v6 — Fase 4 (parte A): atributo "Cerrable" propio, separado de
                    // Grupo2. Valor literal "Cerrable" o NULL ("no aplica" — la cuenta conserva
                    // su historial completo).
                    "Cerrable TEXT, " +
                    // ⭐ NUEVO v9 — Fase 6: referencia hacia el nuevo tipo_cuenta.tipo_cuenta_id
                    // (nullable — en instalaciones frescas no hay backfill que hacer todavía; se
                    // llena a su ritmo cuando exista la pantalla de asignación, prevista para una
                    // versión futura). En dispositivos que actualizan desde una versión anterior,
                    // la migración v9 la agrega con ALTER TABLE y la llena vía backfill — ver
                    // el bloque "if (oldVersion < 9)" en onUpgrade().
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))";

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

    // ⭐ CAMBIO v6 — Fase 4 (parte A): se quitan las dos combinaciones que mezclaban Grupo2 con
    // "Cerrable" ("Exigible Conciliable Cerrable" y "No exigible No conciliable Cerrable"). El
    // atributo "Cerrable" pasa a vivir por su cuenta en cuentas.Cerrable (ver más arriba); estas
    // 4 quedan como las combinaciones base de Grupo2, en el mismo orden relativo de siempre.
    private static final String[] SEED_GRUPO2 = {
            "Exigible Conciliable", "Exigible No conciliable",
            "No exigible Conciliable", "No exigible No conciliable"};

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
    //  DDL — TABLAS DEL MODELO DE CLASIFICACIÓN CONTABLE DEFINITIVO  ⭐ NUEVO v9 (Fase 6)
    // ─────────────────────────────────────────────
    private static final String SQL_CREAR_CLASE_CONTABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CLASE_CONTABLE + " (" +
                    "clase_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "nombre TEXT NOT NULL UNIQUE, " +
                    "naturaleza_normal TEXT NOT NULL)";

    private static final String SQL_CREAR_CLASIFICACION_CONTABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CLASIFICACION_CONTABLE + " (" +
                    "clasificacion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "clase_id INTEGER NOT NULL REFERENCES " + TABLE_CLASE_CONTABLE + "(clase_id), " +
                    "nombre TEXT NOT NULL)";

    private static final String SQL_CREAR_TIPO_CUENTA =
            "CREATE TABLE IF NOT EXISTS " + TABLE_TIPO_CUENTA + " (" +
                    "tipo_cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "clasificacion_id INTEGER NOT NULL REFERENCES " + TABLE_CLASIFICACION_CONTABLE + "(clasificacion_id), " +
                    "nombre TEXT NOT NULL UNIQUE, " +
                    "naturaleza_normal TEXT NOT NULL, " +
                    // ⭐ Columnas del modelo final (hoja "Modelo — Columnas") que esta versión ya
                    // declara para no tener que recrear la tabla más adelante, pero que todavía no
                    // se llenan con un valor real — eso pertenece al catálogo definitivo de
                    // tipo_cuenta que Jorge construya, no al placeholder mecánico de esta versión.
                    "estado_financiero TEXT, " +
                    "signo_presentacion TEXT)";

    // ⭐ NUEVO v9 — Fase 6: clase_contable se siembra con los mismos 9 valores y el mismo orden
    // que SEED_GRUPO1 (arriba) — en el modelo aprobado, Grupo1 YA ES esa clasificación de primer
    // nivel, solo que vivía como catálogo plano en vez de tabla con jerarquía propia. Reusar el
    // mismo texto evita inventar un mapeo nuevo y mantiene los dos catálogos sincronizados
    // mientras conviven (ver comentario de clase arriba: Grupo1/Grupo2 NO se borran todavía).
    private static final String[] SEED_CLASE_NATURALEZA = {
            "DEUDORA",   // Activo
            "ACREEDORA", // Pasivo
            "ACREEDORA", // Patrimonio
            "ACREEDORA", // Ingresos
            "DEUDORA",   // Costo de ventas
            "DEUDORA",   // Gastos
            "DEUDORA",   // Costos de produccion
            "DEUDORA",   // Cuentas de orden Db
            "ACREEDORA"  // Cuentas de orden Cr
    };

    /**
     * Siembra clase_contable (9 filas) y clasificacion_contable (11 filas). Activo y Pasivo
     * (índices 0 y 1 de SEED_GRUPO1) se dividen cada uno en dos clasificaciones —
     * "<nombre> corriente" y "<nombre> no corriente" — porque Grupo2 ya distinguía esa misma
     * idea desde antes de la v6 con los prefijos "Exigible"/"No exigible". Las otras 7 clases
     * quedan con una sola clasificación genérica, del mismo nombre que la clase, porque para
     * ellas Grupo2 no representa una subdivisión contable real (solo "conciliable" o no) — el
     * detalle real, cuando exista, vivirá en tipo_cuenta, no aquí. Se llama tanto desde
     * onCreate() (instalación fresca) como desde el bloque "if (oldVersion < 9)" de onUpgrade().
     */
    private void sembrarClaseYClasificacionContable(SQLiteDatabase db) {
        for (int i = 0; i < SEED_GRUPO1.length; i++) {
            String nombreClase = SEED_GRUPO1[i];

            ContentValues claseCv = new ContentValues();
            claseCv.put("nombre", nombreClase);
            claseCv.put("naturaleza_normal", SEED_CLASE_NATURALEZA[i]);
            long claseId = db.insert(TABLE_CLASE_CONTABLE, null, claseCv);

            if (i == 0 || i == 1) { // Activo, Pasivo
                ContentValues corrienteCv = new ContentValues();
                corrienteCv.put("clase_id", claseId);
                corrienteCv.put("nombre", nombreClase + " corriente");
                db.insert(TABLE_CLASIFICACION_CONTABLE, null, corrienteCv);

                ContentValues noCorrienteCv = new ContentValues();
                noCorrienteCv.put("clase_id", claseId);
                noCorrienteCv.put("nombre", nombreClase + " no corriente");
                db.insert(TABLE_CLASIFICACION_CONTABLE, null, noCorrienteCv);
            } else {
                ContentValues genericaCv = new ContentValues();
                genericaCv.put("clase_id", claseId);
                genericaCv.put("nombre", nombreClase);
                db.insert(TABLE_CLASIFICACION_CONTABLE, null, genericaCv);
            }
        }
    }

    /**
     * Determina el nombre de clasificacion_contable que le corresponde a una combinación real
     * (Grupo1, Grupo2) de "cuentas", usada solo durante el backfill de la migración v9 (ver
     * comentario de clase arriba y el bloque "if (oldVersion < 9)" en onUpgrade()). Para
     * Activo/Pasivo se apoya en el mismo prefijo "Exigible"/"No exigible" que ya trae Grupo2
     * desde antes de la v6; para las demás clases devuelve la clasificación genérica única
     * (mismo nombre que la clase, ver sembrarClaseYClasificacionContable()).
     */
    private String clasificacionNombreParaCombo(String grupo1, String grupo2) {
        if (("Activo".equals(grupo1) || "Pasivo".equals(grupo1)) && grupo2 != null) {
            if (grupo2.startsWith("Exigible")) {
                return grupo1 + " corriente";
            } else if (grupo2.startsWith("No exigible")) {
                return grupo1 + " no corriente";
            }
            // Grupo2 no empieza con ninguno de los dos prefijos esperados — no debería pasar en
            // datos reales después de la v6, pero por seguridad no se bloquea la migración: se
            // deja constancia en el log (desde el llamador, que sí tiene contexto de la cuenta)
            // y se cae del lado "no corriente" como valor más conservador.
            return grupo1 + " no corriente";
        }
        return grupo1; // demás clases: clasificación genérica única, mismo nombre que la clase.
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

    // ⭐ NUEVO v8 — Fase 5: habilita el cumplimiento de llaves foráneas en cada conexión que
    // Android abre hacia balance.db. SQLite las trae DESACTIVADAS por defecto y es una propiedad
    // de cada conexión, no de la base de datos en sí.
    //
    // ⭐ CORRECCIÓN — Fase 5 (parte B): se activa en onOpen(), NO en onConfigure(). Iba en
    // onConfigure() en el primer intento de esta versión, pero onConfigure() corre ANTES de
    // onCreate()/onUpgrade() — o sea, corre antes de que la migración v8 termine de reconstruir
    // "transacciones". Eso hizo que la propia migración (el INSERT que copia las filas existentes
    // a la tabla nueva) se validara contra la llave foránea cuenta_id → cuentas.cuenta_id, y
    // falló con "FOREIGN KEY constraint failed" porque en los datos reales del dispositivo hay
    // transacciones cuyo cuenta_id ya no coincide con ninguna cuenta existente (huérfanas con
    // valor, distintas de las huérfanas con cuenta_id NULL que sí se diagnosticaron en las
    // versiones 3 y 5) — la app quedaba sin poder abrir la base de datos en cada intento.
    // onOpen() corre DESPUÉS de que onCreate()/onUpgrade() ya terminaron y su transacción quedó
    // confirmada, así que la migración copia todas las filas tal cual, sin bloquear por esas
    // huérfanas (ver el diagnóstico que las cuenta, más abajo en el bloque "if (oldVersion < 8)").
    // El cumplimiento, activado aquí, sigue aplicando igual a partir de ese momento y en cada
    // apertura posterior — solo que a escrituras NUEVAS, nunca revisa retroactivamente filas que
    // ya estaban guardadas. No debería afectar ninguna escritura nueva de la app: no existe
    // ningún DELETE sobre "cuentas" en el código, y el único punto que inserta cuenta_id "a mano"
    // (F4_Cierres, al restaurar un backup CSV) ya lo valida con try/catch antes de intentarlo.
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ⭐ NUEVO v8: índice único "best effort" sobre (c1_Documento, c2_ItemDoc) — en el modelo
    // nuevo acordado, ese par debería identificar de forma única cada línea dentro de un
    // documento. Todavía no se ha verificado si los datos reales de cada dispositivo cumplen eso
    // (quedó anotado en la retroalimentación: "hay transacciones con el mismo documento en ese
    // campo"), así que si CREATE UNIQUE INDEX falla por duplicados reales, NO debe tumbar el
    // resto de la migración/creación — se atrapa el error, se registra en el log, y se continúa.
    // Se comparte entre onCreate y la migración v8 para no duplicar la sentencia en dos lugares.
    private void crearIndiceUnicoDocumentoItem(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_transacciones_documento_item " +
                    "ON transacciones(c1_Documento, c2_ItemDoc)");
        } catch (Exception e) {
            android.util.Log.w("A1_1_AyudanteBD",
                    "No se pudo crear el índice único (c1_Documento, c2_ItemDoc) — " +
                            "probablemente hay filas con el mismo documento+ítem en este " +
                            "dispositivo. Se continúa sin este índice; conviene revisar y depurar " +
                            "los duplicados. Detalle: " + e.getMessage());
        }
    }

    // ⭐ NUEVO v8: índices simples de lectura frecuente sobre "transacciones" — no imponen
    // ninguna restricción, solo aceleran consultas que ya existen hoy (por cuenta, por documento,
    // por fecha). Se comparte entre onCreate y la migración v8 por la misma razón que arriba.
    private void crearIndicesTransacciones(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta_id ON transacciones(cuenta_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_c1_documento ON transacciones(c1_Documento)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_c8_fecha_inicial ON transacciones(c8_FechaInicial)");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA encoding = 'UTF-8'");
        // ⭐ v3: "cuentas" se crea primero porque "transacciones" ahora la referencia
        // (cuenta_id REFERENCES cuentas.cuenta_id) — orden lógico padre→hijo.
        db.execSQL(crearCuentas_String);
        db.execSQL(crearTransacciones_String);
        // ⭐ NUEVO v8 — Fase 5: índices sobre "transacciones" desde el inicio en instalaciones
        // frescas (mismos helpers que usa la migración v8 — ver más abajo).
        crearIndiceUnicoDocumentoItem(db);
        crearIndicesTransacciones(db);
        // ⭐ NUEVO: crear tablas de caché desde el inicio en instalaciones frescas
        db.execSQL(SQL_CREAR_CACHE_HEADER);
        db.execSQL(SQL_CREAR_CACHE_RECORDS);
        // ⭐ NUEVO v4 — Fase 2: catálogos de Grupo1/Grupo2, sembrados desde el inicio.
        db.execSQL(SQL_CREAR_CATALOGO_GRUPO1);
        db.execSQL(SQL_CREAR_CATALOGO_GRUPO2);
        sembrarCatalogosGrupo1Y2(db);
        // ⭐ NUEVO v9 — Fase 6: tablas del modelo de clasificación contable definitivo, sembradas
        // desde el inicio en instalaciones frescas. tipo_cuenta se crea vacía a propósito: no hay
        // datos legados de qué backfillear todavía (ver comentario de clase arriba).
        db.execSQL(SQL_CREAR_CLASE_CONTABLE);
        db.execSQL(SQL_CREAR_CLASIFICACION_CONTABLE);
        db.execSQL(SQL_CREAR_TIPO_CUENTA);
        sembrarClaseYClasificacionContable(db);
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

        // ⭐ NUEVO v6 — Fase 4 (parte A): separar "Cerrable" del texto de Grupo2 (ver comentario
        // de clase arriba). Idempotente: cada paso solo toca filas que todavía tienen "Cerrable"
        // en el texto, así que es seguro volver a correrlo.
        if (oldVersion < 6) {

            // 1) cuentas: agregar la columna nueva (ALTER TABLE simple, sin recrear la tabla —
            //    a diferencia de la migración v3, esta columna no es llave ni cambia el orden
            //    posicional de ninguna columna existente).
            db.execSQL("ALTER TABLE cuentas ADD COLUMN Cerrable TEXT");

            // 2) cuentas: marcar como Cerrable las que hoy lo tienen mezclado en Grupo2, y
            //    quitarle esa palabra al texto de Grupo2. Se asume que "Cerrable" aparece solo
            //    como palabra final, precedida de un espacio (los dos únicos valores conocidos:
            //    "Exigible Conciliable Cerrable" y "No exigible No conciliable Cerrable").
            db.execSQL("UPDATE cuentas SET Cerrable = 'Cerrable' WHERE Grupo2 LIKE '%Cerrable%'");
            db.execSQL("UPDATE cuentas SET Grupo2 = TRIM(REPLACE(Grupo2, ' Cerrable', '')) " +
                    "WHERE Grupo2 LIKE '%Cerrable%'");

            // 3) transacciones: el snapshot por transacción se guarda en c12_ColumnaDisponible
            //    (columna existente, nunca usada de verdad — siempre tenía el texto fijo
            //    "No Aplica"; se verificó que ningún otro punto de la app depende de ese valor
            //    literal). Se deriva del texto de Grupo2 QUE YA TENÍA CADA TRANSACCIÓN en el
            //    momento en que se guardó (c11_Grupo2), no del estado actual de la cuenta — así
            //    el snapshot histórico queda fiel a lo que era cierto cuando se creó cada
            //    transacción, igual que ya pasa con Grupo1/Grupo2. Como la columna es NOT NULL,
            //    "no aplica" se guarda como el texto "No Aplica" (el mismo que ya tenía siempre).
            db.execSQL("UPDATE transacciones SET c12_ColumnaDisponible = 'Cerrable' " +
                    "WHERE c11_Grupo2 LIKE '%Cerrable%'");
            db.execSQL("UPDATE transacciones SET c12_ColumnaDisponible = 'No Aplica' " +
                    "WHERE c11_Grupo2 NOT LIKE '%Cerrable%'");
            db.execSQL("UPDATE transacciones SET c11_Grupo2 = TRIM(REPLACE(c11_Grupo2, ' Cerrable', '')) " +
                    "WHERE c11_Grupo2 LIKE '%Cerrable%'");

            // 4) catálogo Grupo2 (tabla, no el array): quitar las dos filas que ya no deberían
            //    existir como combinación seleccionable. Las instalaciones nuevas nunca las
            //    siembran porque SEED_GRUPO2 ya no las tiene (ver arriba).
            db.execSQL("DELETE FROM " + TABLE_CATALOGO_GRUPO2 +
                    " WHERE nombre IN ('Exigible Conciliable Cerrable', 'No exigible No conciliable Cerrable')");

            // 5) Diagnóstico: cuántas cuentas y transacciones quedaron marcadas Cerrable, para
            //    poder comparar contra lo que se veía en la app antes de la migración.
            Cursor cuentasCerrables = db.rawQuery(
                    "SELECT COUNT(*) FROM cuentas WHERE Cerrable = 'Cerrable'", null);
            if (cuentasCerrables.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v6: " + cuentasCerrables.getInt(0) + " cuentas marcadas Cerrable");
            }
            cuentasCerrables.close();

            Cursor transaccionesCerrables = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE c12_ColumnaDisponible = 'Cerrable'", null);
            if (transaccionesCerrables.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v6: " + transaccionesCerrables.getInt(0) + " transacciones marcadas Cerrable");
            }
            transaccionesCerrables.close();
        }

        // ⭐ NUEVO v7 — Fase 4 (parte B): backfill de continuidad (ver comentario de clase
        // arriba). Idempotente: solo toca filas que todavía digan "na" — el texto fijo que
        // escribía B11_DocumentCalculator antes de esta parte; después de esta versión ya nunca
        // vuelve a escribirse.
        if (oldVersion < 7) {
            db.execSQL("UPDATE transacciones SET c12_ColumnaDisponible = 'Cerrable' " +
                    "WHERE c12_ColumnaDisponible = 'na' AND cuenta_id IN " +
                    "(SELECT cuenta_id FROM cuentas WHERE Cerrable = 'Cerrable')");
            db.execSQL("UPDATE transacciones SET c12_ColumnaDisponible = 'No Aplica' " +
                    "WHERE c12_ColumnaDisponible = 'na'");

            Cursor naResiduales = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE c12_ColumnaDisponible = 'na'", null);
            if (naResiduales.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v7: " + naResiduales.getInt(0) +
                                " transacciones seguían con 'na' tras el backfill (no debería pasar)");
            }
            naResiduales.close();
        }

        // ⭐ NUEVO v8 — Fase 5: llave primaria técnica (transaccion_id) + índices en
        // "transacciones" (ver comentario de clase arriba). No toca ninguna columna existente ni
        // su contenido.
        if (oldVersion < 8) {

            // 1) Recrear "transacciones" agregando transaccion_id, preservando el rowid interno
            //    que SQLite ya le asigna a cada fila desde siempre (toda tabla normal lo tiene,
            //    aunque no se declare ninguna columna como llave primaria). Se usa ese mismo rowid
            //    como valor de transaccion_id — en vez de dejar que AUTOINCREMENT vuelva a numerar
            //    desde 1 — para no renumerar ninguna fila existente: cualquier referencia futura
            //    que se guarde hacia una transacción (p.ej. desde una futura tabla de inventario)
            //    coincidirá con la fila real desde el primer momento.
            db.execSQL("CREATE TABLE transacciones_temp_v8(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, c10_Grupo1 TEXT NOT NULL, " +
                    "c11_Grupo2 TEXT NOT NULL, c12_ColumnaDisponible TEXT NOT NULL, " +
                    "c13_ColumnaDisponible TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT)");
            db.execSQL("INSERT INTO transacciones_temp_v8 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c10_Grupo1, c11_Grupo2, c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id) " +
                    "SELECT rowid, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c10_Grupo1, c11_Grupo2, c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id " +
                    "FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v8 RENAME TO transacciones");

            // 2) Índices (mismos helpers que usa onCreate en instalaciones frescas — ver arriba).
            //    El único es "best effort": si falla por duplicados reales en este dispositivo, se
            //    registra en el log y la migración sigue sin él (no se pierde ni se bloquea nada).
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);

            // 3) Diagnóstico: confirmar en el log que la tabla quedó con el mismo número de filas
            //    que tenía antes de recrearla (la migración no debería perder ni duplicar ninguna).
            Cursor totalTransacciones = db.rawQuery("SELECT COUNT(*) FROM transacciones", null);
            if (totalTransacciones.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v8: transacciones quedó con " + totalTransacciones.getInt(0) +
                                " filas tras agregar transaccion_id");
            }
            totalTransacciones.close();

            // 4) Diagnóstico — Fase 5 (parte B): cuántas transacciones tienen un cuenta_id que ya
            //    NO existe en "cuentas" (huérfanas "con valor" — distintas de las huérfanas con
            //    cuenta_id NULL que ya diagnosticaban las versiones 3 y 5). Esta migración las deja
            //    tal cual, sin tocarlas ni bloquear por ellas (ver el comentario en onOpen() sobre
            //    por qué el cumplimiento de llaves foráneas se activa DESPUÉS de este bloque). Solo
            //    quedan registradas aquí para poder revisarlas y decidir qué hacer con ellas más
            //    adelante — es exactamente el tipo de dato que este número visibiliza.
            Cursor huerfanasConValor = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE cuenta_id IS NOT NULL " +
                            "AND cuenta_id NOT IN (SELECT cuenta_id FROM cuentas)", null);
            if (huerfanasConValor.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v8: " + huerfanasConValor.getInt(0) +
                                " transacciones con cuenta_id que ya no existe en cuentas (huérfanas " +
                                "con valor). Quedan sin tocar; conviene revisarlas.");
            }
            huerfanasConValor.close();
        }

        // ⭐ NUEVO v9 — Fase 6: primer paso del modelo de clasificación contable definitivo (ver
        // comentario de clase arriba). Crea clase_contable/clasificacion_contable/tipo_cuenta EN
        // PARALELO a Grupo1/Grupo2 (que no se tocan) y hace un backfill de cuentas.tipo_cuenta_id
        // a partir de las combinaciones (Grupo1, Grupo2) que existan de verdad hoy en "cuentas".
        if (oldVersion < 9) {

            // 1) Crear las tres tablas nuevas.
            db.execSQL(SQL_CREAR_CLASE_CONTABLE);
            db.execSQL(SQL_CREAR_CLASIFICACION_CONTABLE);
            db.execSQL(SQL_CREAR_TIPO_CUENTA);

            // 2) Sembrar clase_contable (9 filas) y clasificacion_contable (11 filas) — ver
            //    sembrarClaseYClasificacionContable() más arriba para el detalle de cada nombre.
            sembrarClaseYClasificacionContable(db);

            // 3) cuentas: agregar tipo_cuenta_id (nullable). ALTER TABLE simple, sin recrear la
            //    tabla — no cambia el orden posicional de ninguna columna existente.
            db.execSQL("ALTER TABLE cuentas ADD COLUMN tipo_cuenta_id INTEGER " +
                    "REFERENCES tipo_cuenta(tipo_cuenta_id)");

            // 4) Backfill: por cada combinación DISTINTA de (Grupo1, Grupo2) que exista hoy en
            //    "cuentas" en este dispositivo (no las combinaciones "de ejemplo" del Documento
            //    3 — esas son solo ilustrativas), se crea un tipo_cuenta propio, nombrado de
            //    forma trazable como "{Grupo1} - {Grupo2}", y se actualizan todas las cuentas que
            //    comparten esa combinación. Se recogen primero las combinaciones en una lista (con
            //    el cursor ya cerrado) para no tener dos cursores abiertos a la vez sobre la misma
            //    tabla mientras se inserta/actualiza.
            java.util.List<String[]> combosDistintos = new java.util.ArrayList<>();
            Cursor combosCursor = db.rawQuery(
                    "SELECT DISTINCT Grupo1, Grupo2 FROM cuentas", null);
            while (combosCursor.moveToNext()) {
                combosDistintos.add(new String[]{combosCursor.getString(0), combosCursor.getString(1)});
            }
            combosCursor.close();

            for (String[] combo : combosDistintos) {
                String grupo1 = combo[0];
                String grupo2 = combo[1];
                String clasificacionNombre = clasificacionNombreParaCombo(grupo1, grupo2);

                Long clasificacionId = null;
                String naturaleza = null;
                Cursor infoCursor = db.rawQuery(
                        "SELECT cc.clasificacion_id, cl.naturaleza_normal " +
                                "FROM " + TABLE_CLASIFICACION_CONTABLE + " cc " +
                                "JOIN " + TABLE_CLASE_CONTABLE + " cl ON cc.clase_id = cl.clase_id " +
                                "WHERE cc.nombre = ? AND cl.nombre = ?",
                        new String[]{clasificacionNombre, grupo1});
                if (infoCursor.moveToFirst()) {
                    clasificacionId = infoCursor.getLong(0);
                    naturaleza = infoCursor.getString(1);
                }
                infoCursor.close();

                if (clasificacionId == null || naturaleza == null) {
                    // No debería pasar (clasificacionNombreParaCombo solo devuelve nombres recién
                    // sembrados en el punto 2) — si un dispositivo tuviera un valor de Grupo1 fuera
                    // de los 9 esperados, se registra y esa combinación queda sin tipo_cuenta_id,
                    // sin tumbar el resto de la migración.
                    android.util.Log.w("A1_1_AyudanteBD",
                            "Migración v9: no se pudo ubicar clase/clasificación para Grupo1='" +
                                    grupo1 + "', Grupo2='" + grupo2 + "' (clasificación esperada: '" +
                                    clasificacionNombre + "'). Esas cuentas quedan con " +
                                    "tipo_cuenta_id NULL; conviene revisarlas.");
                    continue;
                }

                String tipoCuentaNombre = grupo1 + " - " + grupo2;
                ContentValues tipoCv = new ContentValues();
                tipoCv.put("clasificacion_id", clasificacionId);
                tipoCv.put("nombre", tipoCuentaNombre);
                tipoCv.put("naturaleza_normal", naturaleza);
                long tipoCuentaId = db.insert(TABLE_TIPO_CUENTA, null, tipoCv);

                db.execSQL("UPDATE cuentas SET tipo_cuenta_id = ? WHERE Grupo1 = ? AND Grupo2 = ?",
                        new Object[]{tipoCuentaId, grupo1, grupo2});
            }

            // 5) Índice sobre cuentas.tipo_cuenta_id — misma razón que los demás índices de esta
            //    clase: acelerar consultas futuras que filtren/agrupen por tipo de cuenta.
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cuentas_tipo_cuenta_id ON cuentas(tipo_cuenta_id)");

            // 6) Diagnóstico: cuántas combinaciones distintas se procesaron, y si quedó alguna
            //    cuenta sin tipo_cuenta_id (no debería pasar: Grupo1 y Grupo2 son NOT NULL en
            //    cuentas, así que toda fila calza con alguna de las combinaciones recogidas arriba,
            //    salvo el caso excepcional ya registrado en el punto 4).
            android.util.Log.w("A1_1_AyudanteBD",
                    "Migración v9: " + combosDistintos.size() +
                            " combinaciones distintas de (Grupo1, Grupo2) procesadas en tipo_cuenta");

            Cursor sinTipoCuenta = db.rawQuery(
                    "SELECT COUNT(*) FROM cuentas WHERE tipo_cuenta_id IS NULL", null);
            if (sinTipoCuenta.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v9: " + sinTipoCuenta.getInt(0) +
                                " cuentas quedaron con tipo_cuenta_id NULL (no debería pasar)");
            }
            sinTipoCuenta.close();
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
