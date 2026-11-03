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
// ⭐ MODIFICADO: Versión 11 — Fase 6 (parte C) / v11 tanda 2 (real): "transacciones" gana
//   tipo_cuenta_id (nullable, referencia hacia tipo_cuenta.tipo_cuenta_id) — una FOTO de la
//   clasificación de la cuenta en el momento de crear/editar la transacción, en el mismo
//   espíritu que c10_Grupo1/c11_Grupo2 (que NO se tocan ni se borran todavía — eso es v11
//   tanda 3, aparte). No es redundante con la relación cuenta_id → cuentas.tipo_cuenta_id: sin
//   esta foto, reclasificar una cuenta (o un bug que la reasigne por error) reescribiría en
//   silencio el historial de TODAS sus transacciones pasadas con solo un JOIN. Con la foto, el
//   historial queda fijo tal como fue, y el comparador de auditoría (ver
//   obtenerTransaccionesDesalineadas() en A21_OptimizedQuery) puede seguir avisando cuándo la
//   foto guardada ya no coincide con el valor actual de la cuenta — ahora comparando también
//   este id, no solo Grupo1/Grupo2. Backfill en dispositivos existentes: se toma el
//   tipo_cuenta_id ACTUAL de la cuenta de cada transacción (vía cuenta_id) como mejor punto de
//   partida disponible — no hay forma de reconstruir cuál era en el pasado si nunca se guardó.
//   Se agrega, igual que cuenta_id/transaccion_id en las versiones 3/8, leída por NOMBRE (no
//   por posición) en A21_OptimizedQuery.mapTransactionFromCursor, así que no hay riesgo de
//   desalinear el mapeo posicional de c1..c13. El lado que ESCRIBE (B11_DocumentCalculator /
//   B12_DocumentPersistence) se ajusta en esta misma versión: toda transacción nueva la recibe
//   de atributosCuenta[7] (tipo_cuenta_id, ya disponible desde la v10) al crearse, y
//   guardarModificacion() la refresca junto con Grupo1/Grupo2 cuando cambia la cuenta durante
//   una edición — ver el comentario en ese método para el detalle. Queda pendiente, aparte y
//   NO en esta versión (ver checklist), auditar el código de esa misma ruta de edición para
//   encontrar la causa raíz de una reasignación indebida que Jorge reportó como intermitente.
// ⭐ MODIFICADO: Versión 10 — Fase 6 (parte B): el único cambio de ESQUEMA de esta versión es
//   "cuentas" ganando cuenta_seguimiento (INTEGER, valor 1 o NULL — mismo estilo que Cerrable).
//   Reemplaza el nombre de cuenta fijo "CxC Enrique", que F5_1_Indicadores/
//   F5_3_GraficasIndicadores tenían hardcodeado como "la cuenta de gasto diario a seguir", por
//   una marca de datos: la cuenta que hoy se llame "CxC Enrique" se marca automáticamente con
//   cuenta_seguimiento = 1 en la migración (ver el bloque "if (oldVersion < 10)" en onUpgrade()),
//   para que el panel de Indicadores siga funcionando exactamente igual sin ningún paso manual;
//   de ahí en adelante, si Jorge quiere seguir otra cuenta, la marca él mismo desde F2_Cuentas en
//   vez de que quede fija en el código. El resto de la versión 10 es reescritura de CÓDIGO (no de
//   esquema): F2_Cuentas pasa de dos spinners (Grupo1/Grupo2) a un spinner único de tipo_cuenta;
//   A21/A22 agregan tipo_cuenta_id al final del arreglo de queryAttributesByAccount (índice 7,
//   puramente aditivo, mismo criterio que cuenta_id/Cerrable en las versiones 3/6) y una consulta
//   nueva que suma transacciones por clasificacion_contable (reemplaza el filtro de texto
//   "Activo"/"Pasivo" + variantes de Grupo2 que tenía F5_1_Indicadores); A5_1_BackupManager y las
//   rutas de CSV de cuentas quedan unificadas para no perder cuenta_id/codigo_cuenta/Cerrable/
//   tipo_cuenta_id en ningún camino de restauración, y los dos generadores de CSV de "resumen
//   antes de cerrar" pasan a unir transacciones con cuentas por cuenta_id en vez de por nombre
//   (más seguro ahora que F2_Cuentas permite renombrar cuentas). Grupo1/Grupo2 siguen sin tocarse
//   ni borrarse (eso es la v11); B11_DocumentCalculator/B12_DocumentPersistence — el lado que
//   ESCRIBE una transacción nueva — tampoco se toca en esta versión.
// ⭐ MODIFICADO: Versión 13 — v11 tanda 3 (parte D, paso final): se retiran definitivamente
//   cuentas.Grupo1/Grupo2 y transacciones.c10_Grupo1/c11_Grupo2 — el modelo de clasificación
//   contable (tipo_cuenta_id) es, desde ahora, la única fuente de verdad. Ambas tablas se
//   recrean (SQLite en minSdk 27 no soporta DROP COLUMN de forma confiable), preservando
//   cuenta_id/transaccion_id exactos — ver el bloque "if (oldVersion < 13)" en onUpgrade() para
//   el detalle completo. También se dropean los 2 catálogos de Grupo1/Grupo2 (Fase 2, v4), sin
//   uso desde ahora. Del lado del código: mapTransactionFromCursor/mapCuentasFromCursor
//   (A21_OptimizedQuery) ajustan su lectura posicional; los 5 puntos de escritura que nombraban
//   estas columnas explícitamente (INSERT/ContentValues en B12_DocumentPersistence,
//   A1_2_OperacionesBD, F2_Cuentas —registrarNuevas() y clickModify()—, F4_Cierres y
//   F1_CrudDocumento) dejan de referenciarlas; A22_QueryManager.queryAttributesByAccount() deja
//   de seleccionarlas por nombre (SELECT explícito, no *); A5_1_BackupManager deja de leerlas de
//   "transacciones" en los 2 resúmenes de cierre (los backups CSV conservan las columnas
//   Grupo1/Grupo2 en blanco para siempre — formato congelado). De paso, aprovechando esta misma
//   recreación de tabla, la pantalla "Ver Cuentas" (D_F2_AdaptadorCuentas) recibe el mismo
//   tratamiento ya aplicado en la parte B a las 3 pantallas de lista de transacciones: la columna
//   que mostraba Grupo1 ahora muestra el nombre de tipo_cuenta (JOIN nuevo en
//   A22_QueryManager.queryAllAccounts()).
// ⭐ MODIFICADO: Versión 14 — Tanda 1 del "Modelo de Costeo por Inventario en Cuentas"
//   (especificación técnica aprobada, 22-sep). Objetivo: dejar el esquema listo para que
//   cualquier cuenta cuyo saldo esté compuesto por unidades de algo con precio variable
//   (divisas, acciones, mercancías) pueda llevar costeo por promedio ponderado, activable por
//   cuenta y sin alterar en nada el comportamiento de las cuentas que no lo usan. Esta tanda es
//   SOLO esquema — no cambia ninguna pantalla ni flujo todavía (eso son las tandas siguientes).
//   "cuentas" gana con_inventario (INTEGER NOT NULL DEFAULT 0 — 0 = comportamiento idéntico al
//   de hoy). Dos tablas nuevas, vacías en toda instalación existente: items_inventario (los
//   artículos concretos de una cuenta con inventario — p.ej. la cuenta "Inversiones" puede
//   tener Ecopetrol, Bancolombia, Apple, cada uno con su propio saldo — con FK a
//   cuentas.cuenta_id, ON DELETE RESTRICT, y un UNIQUE (cuenta_id, nombre)) y
//   transacciones_inventario (extiende 1 a 1 cada transacción de una cuenta con inventario,
//   guardando unidades y precio_unitario — transaccion_id es a la vez PK y FK hacia
//   transacciones.transaccion_id, sin AUTOINCREMENT propio: toma el id de la transacción que
//   extiende; FK hacia items_inventario.item_id con ON DELETE RESTRICT; CHECK unidades <> 0 y
//   CHECK precio_unitario > 0 — ambos exigibles de verdad porque el cumplimiento de llaves
//   foráneas y CHECK ya está activo desde la v8, vía onOpen()). Ver el bloque
//   "if (oldVersion < 14)" en onUpgrade() para el detalle de la migración en dispositivos
//   existentes (ALTER TABLE + 2 CREATE TABLE, nada que recrear ni backfillear: son columnas y
//   tablas nuevas, no hay datos legados que migrar todavía).
//
// ⭐ NUEVO v15 (30-sep, tras retroalimentación de Jorge sobre la tanda 4): se agrega
//   transacciones_inventario.costo_total — el costo EXACTO en COP que entra o sale del
//   inventario en cada movimiento, con el mismo signo que unidades (positivo entrada, negativo
//   salida). Antes, el costo promedio ponderado se calculaba como SUM(unidades × precio_unitario)
//   / SUM(unidades) — con precio_unitario redondeado a un peso entero, esto podía dejar un
//   pequeño "saldo residual" en costo (sin unidades) cuando se vendía hasta agotar el saldo de un
//   artículo, que luego contaminaba el promedio de una entrada futura. Ahora costo_total se
//   guarda exacto en cada fila, y precio_unitario pasa a ser PURAMENTE informativo (con hasta 3
//   decimales, ya no limitado a pesos enteros) — el promedio ponderado se calcula como
//   SUM(costo_total)/SUM(unidades), y la salida que agota exactamente el saldo de un artículo usa
//   como costo_total el valor EXACTO que quedaba acumulado (sin redondear), para que
//   SUM(costo_total) llegue a 0 exactamente cuando SUM(unidades) también llega a 0. Ver
//   A12_InventarioHelper para el detalle completo del cálculo. Ver el bloque
//   "if (oldVersion < 15)" en onUpgrade() para la migración en dispositivos existentes (ALTER
//   TABLE + backfill: las filas ya guardadas no tienen forma de recuperar su costo exacto
//   histórico, así que se backfillean con unidades × precio_unitario, la misma aproximación que
//   ya se usaba antes de este cambio).

public class A1_1_AyudanteBD extends SQLiteOpenHelper {

    // ─────────────────────────────────────────────
    //  CONSTANTES DE BD
    // ─────────────────────────────────────────────
    public static final String balanceSqlite_String_PSF = "balance.db";

    // ⭐ CAMBIO: versión 14 → 15 para disparar onUpgrade en dispositivos existentes — agrega
    // transacciones_inventario.costo_total (ver comentario de clase arriba y la migración
    // "if (oldVersion < 15)" en onUpgrade()).
    public static final int version1BalanceSqlite_int_PSF = 25; // ⭐ v16 catálogo tipo_cuenta; v17 liquidez; v18 Cerrable/conciliable 0/1; v19 sin c13; v20 sin c12; v21 sin c4; v22 sin c3; v23 borradores completos; v24 renta fase R1; v25 foto anual de renta (R3)

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
    // ⭐ CAMBIO — v11 tanda 3 (parte D): c10_Grupo1/c11_Grupo2 se retiran de la DDL de
    // instalaciones frescas (ver comentario de clase arriba y la migración v13 en onUpgrade(),
    // que aplica el mismo cambio en dispositivos existentes vía recreación de tabla).
    String crearTransacciones_String =
            "CREATE TABLE IF NOT EXISTS transacciones(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    // ⭐ RETIRADO (1-nov, BD v22, Fase E2): c3_Cuenta — la cuenta va por cuenta_id.
                    // ⭐ RETIRADO (1-nov, BD v21, Fase D): c4_Signo — el signo sale del valor (c5_Valor).
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, " +
                    // ⭐ RETIRADO (31-oct, BD v20, Fase C): c12_ColumnaDisponible (foto de "Cerrable") —
                    // los cierres usan la marca actual de la cuenta (decisión de Jorge).
                    // ⭐ RETIRADO (31-oct, BD v19, paso 4 / Fase B): c13_ColumnaDisponible — nunca tuvo uso
                    // ("na"/"n a"/"No Aplica"). Los CSV conservan la columna, en "n a".
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
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    // ⭐ NUEVO v11 — Fase 6 (parte C): foto de tipo_cuenta_id al momento de crear o
                    // editar la transacción (ver comentario de clase arriba). Al final, después de
                    // transaccion_id, por la misma razón de siempre: se lee por NOMBRE, no por
                    // posición (ver mapTransactionFromCursor), así que su posición exacta no
                    // importa para nada del mapeo existente.
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))";

    // ⭐ CAMBIO — v11 tanda 3 (parte D): Grupo1/Grupo2 se retiran de la DDL de instalaciones
    // frescas (ver comentario de clase arriba y la migración v13 en onUpgrade()).
    String crearCuentas_String =
            "CREATE TABLE IF NOT EXISTS cuentas (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    // ⭐ NUEVO v3: llave primaria técnica + código de plan de cuentas, ambas al
                    // final para no correr el orden posicional de ningún query existente (Fase 1).
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT, " +
                    // ⭐ NUEVO v6 — Fase 4 (parte A): atributo "Cerrable" propio, separado de
                    // Grupo2. Valor literal "Cerrable" o NULL ("no aplica" — la cuenta conserva
                    // su historial completo).
                    // ⭐ CAMBIO (31-oct, BD v18): 0/1 real en vez de texto "Cerrable"/NULL.
                    "Cerrable INTEGER NOT NULL DEFAULT 0, " +
                    // ⭐ NUEVO v9 — Fase 6: referencia hacia el nuevo tipo_cuenta.tipo_cuenta_id
                    // (nullable — en instalaciones frescas no hay backfill que hacer todavía; se
                    // llena a su ritmo cuando exista la pantalla de asignación, prevista para una
                    // versión futura). En dispositivos que actualizan desde una versión anterior,
                    // la migración v9 la agrega con ALTER TABLE y la llena vía backfill — ver
                    // el bloque "if (oldVersion < 9)" en onUpgrade().
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id), " +
                    // ⭐ NUEVO v10 — Fase 6 (parte B): marca la cuenta que el panel de
                    // Indicadores debe seguir como "gasto diario" (antes fija por nombre,
                    // "CxC Enrique", en el código de F5_1_Indicadores/F5_3_GraficasIndicadores).
                    // Valor 1 = marcada, NULL = no aplica (mismo estilo que Cerrable). En
                    // instalaciones frescas nace NULL en todas — el usuario la marca desde
                    // F2_Cuentas cuando cree la cuenta que quiera seguir.
                    "cuenta_seguimiento INTEGER, " +
                    // ⭐ NUEVO v12 — v11 tanda 3 (parte A): atributo "conciliable" propio,
                    // separado de Grupo1/Grupo2. Valor literal "Conciliable" o NULL ("no
                    // aplica"), mismo estilo que Cerrable. En instalaciones frescas nace NULL en
                    // todas — el usuario la marca desde F2_Cuentas al crear la cuenta. En
                    // dispositivos que actualizan, la migración v12 la agrega con ALTER TABLE y
                    // la llena vía backfill — ver el bloque "if (oldVersion < 12)" en onUpgrade().
                    // ⭐ CAMBIO (31-oct, BD v18): 0/1 real en vez de texto "Conciliable"/NULL.
                    "conciliable INTEGER NOT NULL DEFAULT 0, " +
                    // ⭐ NUEVO v14 — Tanda 1 del modelo de costeo por inventario (ver comentario de
                    // clase arriba). 0 = cuenta normal, comportamiento idéntico al de hoy (valor
                    // por defecto, así que ninguna cuenta existente ni nueva cambia de
                    // comportamiento en esta tanda). 1 = cuenta con inventario por unidades —
                    // habilitada para tener artículos propios en items_inventario.
                    "con_inventario INTEGER NOT NULL DEFAULT 0, " +
                    // ⭐ NUEVO (3-nov, BD v24, renta fase R1 — diseño acordado con Jorge):
                    // renglón de renta de la cuenta (catálogo renglon_renta), entidad que emite su
                    // certificado y tipo de soporte a pedir. Al final, para no correr el orden
                    // posicional de Item, Cuenta, Fecha (ver A21_OptimizedQuery.mapCuentasFromCursor).
                    "renglon_renta_id INTEGER REFERENCES renglon_renta(renglon_id), " +
                    "entidad TEXT, " +
                    "tipo_soporte TEXT)";

    // ─────────────────────────────────────────────
    //  DDL — MODELO DE COSTEO POR INVENTARIO  ⭐ NUEVO v14
    //  Ver "Modelo de Costeo por Inventario en Cuentas" (especificación técnica, 22-sep) y el
    //  comentario de clase arriba (Versión 14). Ambas tablas nacen vacías en toda instalación
    //  existente — no hay datos legados que backfillear en esta tanda.
    // ─────────────────────────────────────────────
    private static final String SQL_CREAR_ITEMS_INVENTARIO =
            "CREATE TABLE IF NOT EXISTS items_inventario (" +
                    "item_id     INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "cuenta_id   INTEGER NOT NULL, " +
                    "nombre      TEXT NOT NULL, " +
                    "unidad      TEXT, " +
                    // activo permite retirar un artículo de la lista de selección sin borrarlo,
                    // para conservar su historial cuando el usuario ya no tiene unidades de ese
                    // artículo (ver especificación, sección 2).
                    "activo      INTEGER NOT NULL DEFAULT 1, " +
                    "FOREIGN KEY (cuenta_id) REFERENCES cuentas(cuenta_id) " +
                    "ON DELETE RESTRICT ON UPDATE CASCADE, " +
                    "UNIQUE (cuenta_id, nombre))";

    private static final String SQL_CREAR_TRANSACCIONES_INVENTARIO =
            "CREATE TABLE IF NOT EXISTS transacciones_inventario (" +
                    // transaccion_id es PK y a la vez FK hacia transacciones.transaccion_id, SIN
                    // AUTOINCREMENT propio: toma el id de la transacción que extiende (relación
                    // 1 a 1 — ver especificación, sección 2).
                    "transaccion_id   INTEGER PRIMARY KEY, " +
                    "item_id          INTEGER NOT NULL, " +
                    // unidades sigue la misma convención de signo que transacciones.c5_Valor
                    // (positivo al entrar, negativo al salir).
                    "unidades         INTEGER NOT NULL, " +
                    // precio_unitario es siempre positivo, en pesos colombianos (COP). ⭐
                    // REDISEÑO v15: es PURAMENTE informativo/derivado (nunca decide nada del
                    // guardado ni del promedio ponderado — ver A12_InventarioHelper) y puede
                    // traer hasta 3 decimales; se declara "INTEGER" por herencia de la v14, pero
                    // SQLite permite guardar un REAL fraccionario en una columna con afinidad
                    // INTEGER sin perder precisión (afinidad de tipos de SQLite, verificado) —
                    // así que no hace falta ALTER para cambiar su tipo declarado.
                    "precio_unitario  INTEGER NOT NULL, " +
                    // costo_total ⭐ NUEVO v15: el costo EXACTO en COP que este movimiento agrega
                    // o quita del inventario, con el mismo signo que unidades (positivo entrada,
                    // negativo salida). Reemplaza a "unidades × precio_unitario" como la fuente
                    // real del costo promedio ponderado (SUM(costo_total)/SUM(unidades)) — ver
                    // A12_InventarioHelper.calcularCostoPromedioPonderado y
                    // guardarTransaccionConInventario para el detalle completo, incluida la
                    // salida que agota el saldo de un artículo (usa el costo_total EXACTO
                    // acumulado hasta ese momento, para no dejar saldo residual).
                    "costo_total      INTEGER NOT NULL, " +
                    "FOREIGN KEY (transaccion_id) REFERENCES transacciones(transaccion_id) " +
                    "ON DELETE CASCADE, " +
                    "FOREIGN KEY (item_id) REFERENCES items_inventario(item_id) " +
                    "ON DELETE RESTRICT, " +
                    "CHECK (unidades <> 0), " +
                    "CHECK (precio_unitario > 0))";

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
    // ⭐ CAMBIO (1-nov, BD v23, cierre del paso 4, con Jorge): se quitan c10_Grupo1, c11_Grupo2,
    // c12_Col y c13_Col (ya no existen en "transacciones") y se agregan los campos que el
    // borrador no guardaba: cuenta_id, transaccion_id, tipo de cuenta y los de inventario. Sin
    // ellos, un borrador recuperado perdía el movimiento de inventario de sus líneas (y, en
    // Modificar, el vínculo con la transacción original). c3_Cuenta y c4_Signo se conservan: el
    // borrador muestra nombre y signo en pantalla tal como se digitaron.
    private static final String COLUMNAS_CACHE_RECORDS =
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
                    "cuenta_id        INTEGER, " +
                    "transaccion_id   INTEGER, " +
                    "tipo_cuenta_id   INTEGER, " +
                    "tipo_cuenta_nombre TEXT, " +
                    "con_inventario   INTEGER, " +
                    "item_inventario_id INTEGER, " +
                    "unidades_inventario INTEGER, " +
                    "precio_unitario_inventario REAL";
    private static final String SQL_CREAR_CACHE_RECORDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CACHE_RECORDS + " (" + COLUMNAS_CACHE_RECORDS + ")";

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
                    "signo_presentacion TEXT, " +
                    // ⭐ NUEVO (31-oct, BD v17): nivel de liquidez del tipo de cuenta, definido por
                    // Jorge: "Inmediata", "Requiere trámite", "Depende de terceros",
                    // "No disponible a corto plazo" o "No aplica". Es efectivo todo lo "Inmediata"
                    // (reemplaza la columna cuentas.es_efectivo del modelo original).
                    "liquidez TEXT, " +
                    // ⭐ NUEVO (3-nov, BD v24, renta R1): soporte que se suele pedir para las
                    // cuentas de este tipo (lo heredan como cuentas.tipo_soporte; editable).
                    "soporte_sugerido TEXT)";

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

    /**
     * NUEVO — reparación (no es parte de ninguna migración versionada, se puede llamar cuantas
     * veces haga falta): recalcula tipo_cuenta_id para toda cuenta que hoy lo tenga en NULL,
     * usando exactamente la misma lógica del backfill de la migración v9 (combinación real de
     * Grupo1/Grupo2 → clasificacion_contable → tipo_cuenta) — ver el bloque "if (oldVersion < 9)"
     * en onUpgrade(). Reutiliza el tipo_cuenta si el nombre ya existe en el catálogo (nombre es
     * UNIQUE) en vez de crear uno nuevo cada vez, así que repetirla no duplica nada.
     * <p>
     * Pensada para volver a clasificar las cuentas que quedan con tipo_cuenta_id NULL después de
     * una restauración completa desde CSV (Sheets/backup local/Drive) — esos 3 flujos reinsertan
     * "cuentas" sin traer esa columna del CSV todavía (llega con la unificación de la tanda 2 de
     * la v10). Ver F2_Cuentas.repararReferenciasCuentaIdTrasRestaurarCuentas(), que la llama justo
     * después de cada restauración.
     *
     * @return cuántas cuentas siguieron con tipo_cuenta_id NULL después del intento (Grupo1 fuera
     * de las 9 clases esperadas — no debería pasar con datos reales).
     */
    public int repararTipoCuentaDeCuentasSinClasificar(SQLiteDatabase db) {
        // ⭐ CORRECCIÓN (1-nov): esta reparación lee Grupo1/Grupo2, columnas retiradas de
        // "cuentas" en la migración v13. Desde entonces lanzaba "no such column: Grupo1" y, como
        // se llamaba primero, abortaba TODO lo que venía después tras restaurar cuentas — entre
        // otras cosas, el reemparejado de transacciones con sus cuentas. Hoy la clasificación de
        // una cuenta restaurada viene del CSV (nombre del tipo) o del catálogo definitivo, así que
        // si Grupo1 ya no existe no hay nada que reparar aquí.
        if (!tieneColumna(db, "cuentas", "Grupo1")) {
            return 0;
        }
        java.util.List<String[]> combosDistintos = new java.util.ArrayList<>();
        Cursor combosCursor = db.rawQuery(
                "SELECT DISTINCT Grupo1, Grupo2 FROM cuentas WHERE tipo_cuenta_id IS NULL", null);
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
                android.util.Log.w("A1_1_AyudanteBD",
                        "Reparación tipo_cuenta: no se pudo ubicar clase/clasificación para " +
                                "Grupo1='" + grupo1 + "', Grupo2='" + grupo2 + "' (clasificación " +
                                "esperada: '" + clasificacionNombre + "'). Esas cuentas quedan con " +
                                "tipo_cuenta_id NULL.");
                continue;
            }

            String tipoCuentaNombre = grupo1 + " - " + grupo2;
            long tipoCuentaId;
            Cursor tipoExistenteCursor = db.rawQuery(
                    "SELECT tipo_cuenta_id FROM " + TABLE_TIPO_CUENTA + " WHERE nombre = ?",
                    new String[]{tipoCuentaNombre});
            if (tipoExistenteCursor.moveToFirst()) {
                tipoCuentaId = tipoExistenteCursor.getLong(0);
            } else {
                ContentValues tipoCv = new ContentValues();
                tipoCv.put("clasificacion_id", clasificacionId);
                tipoCv.put("nombre", tipoCuentaNombre);
                tipoCv.put("naturaleza_normal", naturaleza);
                tipoCuentaId = db.insert(TABLE_TIPO_CUENTA, null, tipoCv);
            }
            tipoExistenteCursor.close();

            db.execSQL("UPDATE cuentas SET tipo_cuenta_id = ? " +
                            "WHERE Grupo1 = ? AND Grupo2 = ? AND tipo_cuenta_id IS NULL",
                    new Object[]{tipoCuentaId, grupo1, grupo2});
        }

        int pendientes = 0;
        Cursor sinTipoCuenta = db.rawQuery(
                "SELECT COUNT(*) FROM cuentas WHERE tipo_cuenta_id IS NULL", null);
        if (sinTipoCuenta.moveToFirst()) {
            pendientes = sinTipoCuenta.getInt(0);
        }
        sinTipoCuenta.close();
        return pendientes;
    }

    // ─────────────────────────────────────────────
    //  COLUMNAS (para uso en queries de F1)
    // ─────────────────────────────────────────────
    // ⭐ CAMBIO — v11 tanda 3 (parte D): c10_Grupo1/c11_Grupo2 se quitan de este arreglo — ya no
    // existen en "transacciones" (ver migración v13 en onUpgrade()). Sin llamadores hoy (arreglo
    // sin uso en el resto del código), se corrige de todas formas para que no quede
    // desactualizado si algo empieza a usarlo más adelante.
    public static final String[] columnasTransacciones_ArrayString_PSF = {
            "c1_Documento", "c2_ItemDoc", "c5_Valor", // c3 (BD v22) y c4 (BD v21) retiradas
            "c6_Descripcion", "c7_FechaYhora", "c8_FechaInicial", "c9_FechaModificacion",
            }; // c12 (BD v20) y c13 (BD v19) retiradas

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
        // (31-oct) Se retira el renombrado en onOpen del catálogo provisional: la migración v16
        // (onUpgrade) lo reemplaza por el catálogo definitivo.
    }

    // ⭐ NUEVO (31-oct) — CATÁLOGO DEFINITIVO de tipo_cuenta, definido por Jorge (documento
    // "Catálogo definitivo tipo_cuenta" + Excel "Asignación cuentas a tipo_cuenta", 31-oct).
    // Reemplaza al catálogo provisional de ids 1..9 (fix de restauración del 30-oct).
    // Criterio de Jorge: corriente / no corriente por liquidez real a 12 meses, no por moneda.
    // Los tipos sin cuentas hoy (3, 6, 7, 12, 16, 17-20) se dejan creados a propósito, reservados.
    // { tipo_cuenta_id, clase (clase_contable.nombre), clasificación (clasificacion_contable.nombre),
    //   nombre visible, liquidez (⭐ BD v17), soporte sugerido (⭐ BD v24), naturaleza propia
    //   (⭐ BD v24, solo tipos correctores; vacío = la de la clase) }.
    private static final String[][] CATALOGO_TIPO_CUENTA_DEFINITIVO = {
            {"1", "Activo", "Activo corriente", "Efectivo y bancos", "Inmediata", "Certificado bancario", ""},
            {"2", "Activo", "Activo corriente", "Inversiones temporales", "Requiere trámite", "Certificado de la entidad", ""},
            {"3", "Activo", "Activo corriente", "Fondos de inversión", "Requiere trámite", "Certificado de la entidad", ""},
            {"4", "Activo", "Activo corriente", "Cuentas por cobrar", "Depende de terceros", "Documento de la deuda", ""},
            {"5", "Activo", "Activo corriente", "Efectivo y bancos en moneda extranjera", "Inmediata", "Certificado bancario", ""},
            {"6", "Activo", "Activo corriente", "Cuentas por cobrar en moneda extranjera", "Depende de terceros", "Documento de la deuda", ""},
            {"7", "Activo", "Activo no corriente", "Inversiones de largo plazo en el exterior", "No disponible a corto plazo", "Certificado de la entidad", ""},
            {"8", "Pasivo", "Pasivo corriente", "Tarjetas de crédito", "No aplica", "Certificado de saldo de la entidad", ""},
            {"9", "Pasivo", "Pasivo corriente", "Cuentas por pagar - gastos del hogar", "No aplica", "Documento de la deuda", ""},
            {"10", "Pasivo", "Pasivo corriente", "Cuentas por pagar personales", "No aplica", "Documento de la deuda", ""},
            {"11", "Pasivo", "Pasivo corriente", "Tarjetas y cuentas por pagar en moneda extranjera", "No aplica", "Certificado de saldo de la entidad", ""},
            {"12", "Pasivo", "Pasivo no corriente", "Obligaciones financieras de largo plazo", "No aplica", "Certificado de deuda de la entidad", ""},
            {"13", "Cuentas de orden Db", "Cuentas de orden Db", "Cuenta de orden deudora", "No aplica", "Ninguno", ""},
            {"14", "Cuentas de orden Cr", "Cuentas de orden Cr", "Cuenta de orden acreedora", "No aplica", "Ninguno", ""},
            {"15", "Patrimonio", "Patrimonio", "Capital", "No aplica", "Ninguno", ""},
            {"16", "Patrimonio", "Patrimonio", "Resultados / utilidades acumuladas", "No aplica", "Ninguno", ""},
            {"17", "Ingresos", "Ingresos", "Ingresos (genérico)", "No aplica", "Ninguno", ""},
            {"18", "Gastos", "Gastos", "Gastos (genérico)", "No aplica", "Ninguno", ""},
            {"19", "Costo de ventas", "Costo de ventas", "Costo de ventas (genérico)", "No aplica", "Ninguno", ""},
            {"20", "Costos de produccion", "Costos de produccion", "Costos de producción (genérico)", "No aplica", "Ninguno", ""},
            {"21", "Pasivo", "Pasivo corriente", "Efectivo por pagar en moneda extranjera", "No aplica", "Documento de la deuda", ""},
            // ⭐ NUEVO (3-nov, BD v24, renta R1 — decisión de Jorge): tipos correctores reservados,
            // sin cuentas. Están dentro del Activo pero son de naturaleza ACREEDORA (columna 7):
            // su saldo negativo es normal, restan dentro de su renglón y no aparecen en la
            // depuración de saldos contrarios.
            {"22", "Activo", "Activo corriente", "Provisión de cuentas por cobrar", "Depende de terceros", "Ninguno", "ACREEDORA"},
            {"23", "Activo", "Activo no corriente", "Depreciación acumulada", "No disponible a corto plazo", "Ninguno", "ACREEDORA"}
    };

    // Asignación de cada cuenta real a su tipo definitivo (Excel de Jorge, 31-oct). Se usa en la
    // migración v16 de la BD y al restaurar backups en formato ANTIGUO (sin columna
    // tipo_cuenta_nombre), cuyos tipo_cuenta_id 1..9 corresponden al catálogo provisional.
    private static final String[][] ASIGNACION_CUENTA_A_TIPO = {
            {"Caja", "1"}, {"Bolsillo", "1"}, {"Banco Bbva", "1"}, {"Banco Colpatria Bolsillo", "1"},
            {"Banco Colpatria Flexicuenta", "1"}, {"Banco Colpatria Premiun", "1"},
            {"Banco Daviplata", "1"}, {"Banco Neki", "1"}, {"Banco Nu", "1"},
            {"Banco Lulo Bank", "1"}, {"Banco Popular", "1"}, {"Banco Popular2", "1"},
            {"Banco Pibank", "1"}, {"BancoTuya", "1"}, {"B Flexi TD", "1"},
            {"Bbva cdt", "2"}, {"Coltefinanciera", "2"}, {"Cdt Banco de Occidente", "2"},
            {"Banco Santander cdt", "2"}, {"Banco Pibank Cdt", "2"}, {"B Popu 2023 12 23 cd", "2"},
            {"Bbva ctas ahorro fijo", "2"}, {"Bbva fondo Paramo", "2"},
            {"Bbva 2022 10 12", "2"}, {"Bbva 2022 12 09", "2"}, {"Bbva 2023 02 08", "2"},
            {"Bbva 2023 05 12", "2"}, {"Bbva 2023 10 13", "2"}, {"Bbva 2023 10 2", "2"},
            {"Bbva 2023 3 14 af", "2"}, {"Bbva 2023 6 9 cd", "2"},
            {"CxC Enrique", "4"}, {"CxC Enrique mes siguiente", "4"}, {"CxC Enrique Proyectos", "4"},
            {"CxC Enrique Viaje", "4"}, {"CxC Yolanda", "4"}, {"CxC Yolanda Otros", "4"},
            {"CxC Admon Cauca", "4"}, {"CxC Antonio J", "4"}, {"CxC Arriendo Cauca", "4"},
            {"CxC Betty Y Edgar", "4"}, {"CxC Cauca otros", "4"}, {"CxC Don Antonio", "4"},
            {"CxC Edwin Int", "4"}, {"CxC F Jj", "4"}, {"CxC Familia Diaz", "4"},
            {"CxC Familia García", "4"}, {"CxC Gina", "4"}, {"CxC Lucia", "4"},
            {"CxC Pan De Bono Expess Int", "4"}, {"CxC Simón G", "4"}, {"CxC Yenli", "4"},
            {"A Caja Euros", "5"}, {"A Caja Mayor Euros", "5"}, {"Banco Lulo x", "5"},
            {"Banco Dolar App", "5"}, {"Banco ARQ EURc", "5"},
            {"CxC Enrique Euros", "5"}, {"CxC Euros", "5"}, {"CxC Enrique Dolares", "5"},
            {"A CxC Enrique E Anti", "5"}, {"A CxC Enrique Euros", "5"}, {"A CxC Yolanda Euros", "5"},
            {"CxP TC Bbva", "8"}, {"CxP TC Colpatria", "8"}, {"CxP TC Nu", "8"},
            {"CxP Agua", "9"}, {"CxP Energia", "9"}, {"CxP Gas", "9"}, {"CxP Telefono", "9"},
            {"CxP Tv", "9"}, {"CxP Arriendo Cauca Jj", "9"}, {"CxP Aseo", "9"},
            {"CxP Alimento Mes Sig", "9"}, {"CxP Alimentos Y Aseo", "9"},
            {"CxP Servicio Domestico Diario", "9"}, {"CxP Servicio Domestico Pres", "9"},
            {"CxP Admon", "9"}, {"CxP Yolanda", "9"}, {"CxP Falabella", "9"},
            {"CxP Provision Gts Hogar", "9"}, {"CxP Provision Gts jj", "9"},
            {"CxP Don Leonardo Blanca", "9"},
            {"CxP Blanca personal", "10"}, {"CxP D Juana", "10"}, {"CxP Jjj", "10"},
            {"CxP Unificacion F Jj", "10"}, {"CxP Intereses Ban Jj", "10"},
            {"CxP Intereses Ban Ot", "10"}, {"CxP int jj no bancos", "10"},
            {"B CxP TC Bbva Euros", "11"}, {"B CxP TC Colp Euros", "11"}, {"B CxP TC Nu Euros", "11"},
            {"Blanquita c o activa", "13"}, {"Blanquita c o pasiva", "14"},
            {"PAT Capital", "15"}, {"PAT Rentabilidad sana", "15"},
            {"B Caja Euros Pasivo", "21"}
    };

    // Respaldo para cuentas que no estén en la asignación por nombre (p. ej. creadas después del
    // 31-oct que aparezcan en un backup antiguo): tipo provisional 1..9 → tipo definitivo.
    private static long tipoDefinitivoDesdeProvisional(long idProvisional) {
        switch ((int) idProvisional) {
            case 1: return 1;   // Efectivo y bancos
            case 2: return 4;   // Cuentas por cobrar
            case 3: return 10;  // Cuentas por pagar personales
            case 4: return 13;  // Cuenta de orden deudora
            case 5: return 14;  // Cuenta de orden acreedora
            case 6: return 5;   // Efectivo y bancos en moneda extranjera
            case 7: return 9;   // Cuentas por pagar - gastos del hogar
            case 8: return 9;   // Cuentas por pagar - gastos del hogar
            case 9: return 15;  // Capital
            default: return -1;
        }
    }

    private static Long tipoDefinitivoPorNombreDeCuenta(String nombreCuenta) {
        if (nombreCuenta == null) return null;
        String nombre = nombreCuenta.trim();
        for (String[] par : ASIGNACION_CUENTA_A_TIPO) {
            if (par[0].equals(nombre)) return Long.parseLong(par[1]);
        }
        return null;
    }

    /**
     * Si tipo_cuenta está vacía (instalación nueva), la llena con el catálogo definitivo, con sus
     * ids fijos. Idempotente: si la tabla ya tiene filas, no hace nada.
     * @return cuántos tipos se insertaron.
     */
    public static int asegurarCatalogoTipoCuenta(SQLiteDatabase db) {
        if (android.database.DatabaseUtils.queryNumEntries(db, TABLE_TIPO_CUENTA) > 0) {
            return 0;
        }
        return insertarCatalogoDefinitivo(db);
    }

    private static int insertarCatalogoDefinitivo(SQLiteDatabase db) {
        int insertados = 0;
        // La migración v16 (que llama a este método) corre ANTES de la v17 que agrega la columna
        // liquidez: en ese caso se inserta sin ella y la v17 la completa después.
        boolean tieneColumnaLiquidez = tieneColumna(db, TABLE_TIPO_CUENTA, "liquidez");
        boolean tieneColumnaSoporte = tieneColumna(db, TABLE_TIPO_CUENTA, "soporte_sugerido");
        for (String[] fila : CATALOGO_TIPO_CUENTA_DEFINITIVO) {
            Long clasificacionId = null;
            String naturaleza = null;
            Cursor c = db.rawQuery(
                    "SELECT cc.clasificacion_id, cl.naturaleza_normal " +
                            "FROM " + TABLE_CLASIFICACION_CONTABLE + " cc " +
                            "JOIN " + TABLE_CLASE_CONTABLE + " cl ON cc.clase_id = cl.clase_id " +
                            "WHERE cc.nombre = ? AND cl.nombre = ?",
                    new String[]{fila[2], fila[1]});
            if (c.moveToFirst()) {
                clasificacionId = c.getLong(0);
                naturaleza = c.getString(1);
            }
            c.close();
            if (clasificacionId == null) {
                android.util.Log.w("A1_1_AyudanteBD", "Catálogo definitivo: no existe la " +
                        "clasificación '" + fila[2] + "' de la clase '" + fila[1] +
                        "' — se omite tipo_cuenta_id " + fila[0]);
                continue;
            }
            ContentValues cv = new ContentValues();
            cv.put("tipo_cuenta_id", Long.parseLong(fila[0]));
            cv.put("clasificacion_id", clasificacionId);
            cv.put("nombre", fila[3]);
            // ⭐ (BD v24) los tipos correctores traen su propia naturaleza (columna 7).
            cv.put("naturaleza_normal", fila[6].isEmpty() ? naturaleza : fila[6]);
            if (tieneColumnaLiquidez) {
                cv.put("liquidez", fila[4]);
            }
            if (tieneColumnaSoporte) {
                cv.put("soporte_sugerido", fila[5]);
            }
            // ⭐ (BD v24) CONFLICT_IGNORE: insertarTiposFaltantesDelCatalogo() lo reusa sobre una
            // tabla con datos; un id o nombre ya existente se omite sin error.
            if (db.insertWithOnConflict(TABLE_TIPO_CUENTA, null, cv,
                    SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                insertados++;
            }
        }
        android.util.Log.w("A1_1_AyudanteBD",
                "Catálogo definitivo de tipo_cuenta sembrado: " + insertados + " filas");
        return insertados;
    }

    private static boolean tieneColumna(SQLiteDatabase db, String tabla, String columna) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + tabla + ")", null);
        try {
            while (c.moveToNext()) {
                if (columna.equalsIgnoreCase(c.getString(1))) return true;
            }
            return false;
        } finally {
            c.close();
        }
    }

    /**
     * ⭐ NUEVO (31-oct, BD v17): completa tipo_cuenta.liquidez con los niveles definidos por
     * Jorge, por tipo_cuenta_id, solo donde todavía esté vacía (no pisa lo que ya tenga valor).
     */
    private static void completarLiquidezCatalogo(SQLiteDatabase db) {
        for (String[] fila : CATALOGO_TIPO_CUENTA_DEFINITIVO) {
            db.execSQL("UPDATE " + TABLE_TIPO_CUENTA + " SET liquidez = ? " +
                            "WHERE tipo_cuenta_id = ? AND liquidez IS NULL",
                    new Object[]{fila[4], Long.parseLong(fila[0])});
        }
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (3-nov, BD v24) — MODELO DE RENTA FISCAL, FASE R1
    //  Diseño acordado con Jorge (doc "BalanceJava — Diseño del modelo de renta fiscal"):
    //  catálogo universal de renglones por país y vigencia, cargado por plantilla CSV; cada
    //  cuenta con su renglón (asignado por la clase de su tipo de cuenta, editable), entidad y
    //  tipo de soporte. La app clasifica y totaliza; no liquida impuestos.
    // ─────────────────────────────────────────────
    public static final String TABLE_RENGLON_RENTA = "renglon_renta";
    public static final String TABLE_CONFIGURACION_APP = "configuracion_app";

    private static final String SQL_CREAR_RENGLON_RENTA =
            "CREATE TABLE IF NOT EXISTS " + TABLE_RENGLON_RENTA + " (" +
                    "renglon_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "pais TEXT NOT NULL, " +
                    "formulario TEXT, " +
                    "seccion TEXT, " +
                    "codigo TEXT NOT NULL, " +          // texto: "29", "Box 3", "Label 10"...
                    "nombre TEXT NOT NULL, " +
                    // Activo, Pasivo, Ingreso, Gasto, Calculado o No se declara
                    "naturaleza TEXT NOT NULL, " +
                    "formula TEXT, " +                  // solo renglones calculados, ej. "29-30"
                    "por_defecto INTEGER NOT NULL DEFAULT 0, " + // 1 = sugerido para su naturaleza
                    "orden INTEGER, " +
                    "vigente_desde INTEGER, " +         // año fiscal
                    "vigente_hasta INTEGER, " +         // NULL = vigente
                    "UNIQUE (pais, formulario, codigo, vigente_desde))";

    // Una sola fila (config_id = 1). El fin del año fiscal no se guarda: inicio + 1 año − 1 día.
    private static final String SQL_CREAR_CONFIGURACION_APP =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CONFIGURACION_APP + " (" +
                    "config_id INTEGER PRIMARY KEY CHECK (config_id = 1), " +
                    "pais TEXT NOT NULL, " +
                    "moneda TEXT NOT NULL, " +
                    "inicio_anio_fiscal_mes INTEGER NOT NULL, " +
                    "inicio_anio_fiscal_dia INTEGER NOT NULL)";

    // Plantilla de Colombia (Formulario 210, vigente desde el año gravable 2023), dentro de la
    // app. Mismo formato que cualquier plantilla importada: columnas de renglon_renta.
    public static final String PLANTILLA_RENTA_COLOMBIA =
            "pais,formulario,seccion,codigo,nombre,naturaleza,formula,por_defecto,orden,vigente_desde,vigente_hasta\n" +
            "CO,210,Patrimonio,29,Total patrimonio bruto,Activo,,1,1,2023,\n" +
            "CO,210,Patrimonio,30,Deudas,Pasivo,,1,2,2023,\n" +
            "CO,210,Patrimonio,31,Total patrimonio líquido,Calculado,29-30,0,3,2023,\n" +
            "CO,210,,NA,No se declara,No se declara,,1,99,2023,\n";

    public static final String[] COLUMNAS_PLANTILLA_RENTA = {
            "pais", "formulario", "seccion", "codigo", "nombre", "naturaleza", "formula",
            "por_defecto", "orden", "vigente_desde", "vigente_hasta"};

    // Cuentas de efectivo físico: no tienen certificado (decisión de Jorge, 3-nov). Solo se usa
    // al llenar tipo_soporte por primera vez en la migración v24; después el usuario lo cambia.
    private static final String[] CUENTAS_SIN_SOPORTE = {
            "Caja", "Bolsillo", "A Caja Euros", "A Caja Mayor Euros"};

    private static void crearTablasRenta(SQLiteDatabase db) {
        db.execSQL(SQL_CREAR_RENGLON_RENTA);
        db.execSQL(SQL_CREAR_CONFIGURACION_APP);
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_CONFIGURACION_APP +
                " (config_id, pais, moneda, inicio_anio_fiscal_mes, inicio_anio_fiscal_dia) " +
                "VALUES (1, 'CO', 'COP', 1, 1)");
    }

    /** Completa tipo_cuenta.soporte_sugerido por id, solo donde esté vacío. */
    private static void completarSoporteSugeridoCatalogo(SQLiteDatabase db) {
        for (String[] fila : CATALOGO_TIPO_CUENTA_DEFINITIVO) {
            db.execSQL("UPDATE " + TABLE_TIPO_CUENTA + " SET soporte_sugerido = ? " +
                            "WHERE tipo_cuenta_id = ? AND soporte_sugerido IS NULL",
                    new Object[]{fila[5], Long.parseLong(fila[0])});
        }
    }

    /** País configurado (por defecto "CO"). */
    public static String paisConfigurado(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT pais FROM " + TABLE_CONFIGURACION_APP +
                " WHERE config_id = 1", null);
        try {
            return c.moveToFirst() ? c.getString(0) : "CO";
        } finally {
            c.close();
        }
    }

    /**
     * Año fiscal en curso según el inicio configurado: si hoy es anterior al inicio de este año
     * calendario, el año fiscal en curso empezó el año anterior. El año fiscal se nombra por el
     * año calendario en que empieza (inicio 1-ene → coincide con el año calendario).
     */
    public static int anioFiscalActual(SQLiteDatabase db) {
        int mes = 1, dia = 1;
        Cursor c = db.rawQuery("SELECT inicio_anio_fiscal_mes, inicio_anio_fiscal_dia FROM " +
                TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
        if (c.moveToFirst()) {
            mes = c.getInt(0);
            dia = c.getInt(1);
        }
        c.close();
        java.util.Calendar hoy = java.util.Calendar.getInstance();
        int anio = hoy.get(java.util.Calendar.YEAR);
        int mesHoy = hoy.get(java.util.Calendar.MONTH) + 1;
        int diaHoy = hoy.get(java.util.Calendar.DAY_OF_MONTH);
        boolean antesDelInicio = mesHoy < mes || (mesHoy == mes && diaHoy < dia);
        return antesDelInicio ? anio - 1 : anio;
    }

    /**
     * Importa una plantilla de renglones (texto CSV con encabezado; columnas por nombre, ver
     * COLUMNAS_PLANTILLA_RENTA). Por cada (país, vigente_desde X) de la plantilla, los renglones
     * de ese país todavía abiertos y que empezaron antes de X quedan "vigentes hasta X−1" (cambio
     * de formulario). Reimportar la misma plantilla no duplica nada (UNIQUE + IGNORE).
     * @return cuántos renglones se insertaron.
     */
    public static int importarPlantillaRenta(SQLiteDatabase db, String textoCsv) {
        String[] lineas = textoCsv.replace("\r", "").split("\n");
        if (lineas.length < 2) return 0;
        String[] encabezado = lineas[0].split(",", -1);
        java.util.Map<String, Integer> col = new java.util.HashMap<>();
        for (int i = 0; i < encabezado.length; i++) {
            col.put(encabezado[i].trim().toLowerCase(java.util.Locale.ROOT), i);
        }
        java.util.Set<String> vigenciasCerradas = new java.util.HashSet<>();
        int insertados = 0;
        for (int n = 1; n < lineas.length; n++) {
            if (lineas[n].trim().isEmpty()) continue;
            String[] d = lineas[n].split(",", -1);
            String pais = campoPlantilla(d, col, "pais");
            String codigo = campoPlantilla(d, col, "codigo");
            String nombre = campoPlantilla(d, col, "nombre");
            String naturaleza = campoPlantilla(d, col, "naturaleza");
            if (pais == null || codigo == null || nombre == null || naturaleza == null) {
                android.util.Log.w("A1_1_AyudanteBD", "Plantilla de renta: línea " + (n + 1) +
                        " sin país, código, nombre o naturaleza; se omite");
                continue;
            }
            Integer desde = enteroPlantilla(campoPlantilla(d, col, "vigente_desde"));
            if (desde != null && vigenciasCerradas.add(pais + "|" + desde)) {
                db.execSQL("UPDATE " + TABLE_RENGLON_RENTA + " SET vigente_hasta = ? " +
                                "WHERE pais = ? AND vigente_hasta IS NULL AND vigente_desde < ?",
                        new Object[]{desde - 1, pais, desde});
            }
            ContentValues cv = new ContentValues();
            cv.put("pais", pais);
            cv.put("formulario", campoPlantilla(d, col, "formulario"));
            cv.put("seccion", campoPlantilla(d, col, "seccion"));
            cv.put("codigo", codigo);
            cv.put("nombre", nombre);
            cv.put("naturaleza", naturaleza);
            cv.put("formula", campoPlantilla(d, col, "formula"));
            cv.put("por_defecto", "1".equals(campoPlantilla(d, col, "por_defecto")) ? 1 : 0);
            cv.put("orden", enteroPlantilla(campoPlantilla(d, col, "orden")));
            cv.put("vigente_desde", desde);
            cv.put("vigente_hasta", enteroPlantilla(campoPlantilla(d, col, "vigente_hasta")));
            if (db.insertWithOnConflict(TABLE_RENGLON_RENTA, null, cv,
                    SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                insertados++;
            }
        }
        android.util.Log.w("A1_1_AyudanteBD", "Plantilla de renta importada: " + insertados + " renglones");
        return insertados;
    }

    private static String campoPlantilla(String[] d, java.util.Map<String, Integer> col, String nombre) {
        Integer i = col.get(nombre);
        if (i == null || i >= d.length) return null;
        String v = d[i].trim();
        return v.isEmpty() ? null : v;
    }

    private static Integer enteroPlantilla(String v) {
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Naturaleza del renglón según la clase contable del tipo de cuenta.
    private static final String SQL_NATURALEZA_RENTA_DE_CLASE =
            "CASE cl.nombre WHEN 'Activo' THEN 'Activo' WHEN 'Pasivo' THEN 'Pasivo' " +
                    "WHEN 'Ingresos' THEN 'Ingreso' WHEN 'Gastos' THEN 'Gasto' " +
                    "WHEN 'Costo de ventas' THEN 'Gasto' WHEN 'Costos de produccion' THEN 'Gasto' " +
                    "ELSE 'No se declara' END";

    // Renglón por defecto y vigente para una naturaleza (país ?1, año ?2 dos veces).
    private static final String SQL_RENGLON_POR_DEFECTO =
            "SELECT r.renglon_id FROM " + TABLE_RENGLON_RENTA + " r " +
                    "WHERE r.pais = ? AND r.por_defecto = 1 AND r.naturaleza = ? " +
                    "AND (r.vigente_desde IS NULL OR r.vigente_desde <= ?) " +
                    "AND (r.vigente_hasta IS NULL OR r.vigente_hasta >= ?) " +
                    "ORDER BY r.orden LIMIT 1";

    /**
     * Asigna el renglón de renta por defecto a las cuentas que no tienen uno vigente (sin
     * renglón, o con un renglón que dejó de estar vigente por un cambio de formulario), según la
     * clase de su tipo de cuenta; si no hay renglón por defecto para esa naturaleza, va a
     * "No se declara". Las cuentas sin tipo de cuenta quedan sin asignar. Además completa
     * tipo_soporte vacío con el soporte sugerido de su tipo. No toca lo que el usuario ya eligió.
     * @return cuántas cuentas recibieron renglón.
     */
    public static int asignarRenglonYSoportePendientes(SQLiteDatabase db) {
        String pais = paisConfigurado(db);
        int anio = anioFiscalActual(db);
        String a = String.valueOf(anio);
        int asignadas = 0;
        Cursor c = db.rawQuery(
                "SELECT cu.cuenta_id, " + SQL_NATURALEZA_RENTA_DE_CLASE + " " +
                        "FROM cuentas cu " +
                        "JOIN " + TABLE_TIPO_CUENTA + " tc ON tc.tipo_cuenta_id = cu.tipo_cuenta_id " +
                        "JOIN " + TABLE_CLASIFICACION_CONTABLE + " cc ON cc.clasificacion_id = tc.clasificacion_id " +
                        "JOIN " + TABLE_CLASE_CONTABLE + " cl ON cl.clase_id = cc.clase_id " +
                        "WHERE cu.renglon_renta_id IS NULL OR cu.renglon_renta_id NOT IN (" +
                        "SELECT renglon_id FROM " + TABLE_RENGLON_RENTA + " WHERE pais = ? " +
                        "AND (vigente_desde IS NULL OR vigente_desde <= ?) " +
                        "AND (vigente_hasta IS NULL OR vigente_hasta >= ?))",
                new String[]{pais, a, a});
        java.util.Map<String, Long> porNaturaleza = new java.util.HashMap<>();
        try {
            while (c.moveToNext()) {
                long cuentaId = c.getLong(0);
                String naturaleza = c.getString(1);
                Long renglon = porNaturaleza.containsKey(naturaleza)
                        ? porNaturaleza.get(naturaleza)
                        : renglonPorDefecto(db, pais, naturaleza, a);
                if (renglon == null && !"No se declara".equals(naturaleza)) {
                    renglon = renglonPorDefecto(db, pais, "No se declara", a);
                }
                porNaturaleza.put(naturaleza, renglon);
                if (renglon != null) {
                    db.execSQL("UPDATE cuentas SET renglon_renta_id = ? WHERE cuenta_id = ?",
                            new Object[]{renglon, cuentaId});
                    asignadas++;
                }
            }
        } finally {
            c.close();
        }
        db.execSQL("UPDATE cuentas SET tipo_soporte = (SELECT tc.soporte_sugerido FROM " +
                TABLE_TIPO_CUENTA + " tc WHERE tc.tipo_cuenta_id = cuentas.tipo_cuenta_id) " +
                "WHERE tipo_soporte IS NULL AND tipo_cuenta_id IS NOT NULL");
        return asignadas;
    }

    private static Long renglonPorDefecto(SQLiteDatabase db, String pais, String naturaleza, String anio) {
        Cursor c = db.rawQuery(SQL_RENGLON_POR_DEFECTO, new String[]{pais, naturaleza, anio, anio});
        try {
            return c.moveToFirst() ? c.getLong(0) : null;
        } finally {
            c.close();
        }
    }

    // ⭐ NUEVO (3-nov, renta R2): tipos de soporte que ofrece el selector de F2 (además de los que
    // ya estén guardados en tipos de cuenta o cuentas).
    public static final String[] TIPOS_SOPORTE_BASE = {
            "Certificado bancario", "Certificado de la entidad", "Certificado de saldo de la entidad",
            "Certificado de deuda de la entidad", "Documento de la deuda", "Ninguno"};

    /** Opciones del selector de soporte: las base + las ya usadas, sin repetir, en orden. */
    public static java.util.List<String> opcionesTipoSoporte(SQLiteDatabase db) {
        java.util.LinkedHashSet<String> opciones = new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(TIPOS_SOPORTE_BASE));
        Cursor c = db.rawQuery("SELECT soporte_sugerido FROM " + TABLE_TIPO_CUENTA +
                " WHERE soporte_sugerido IS NOT NULL UNION SELECT tipo_soporte FROM cuentas " +
                "WHERE tipo_soporte IS NOT NULL", null);
        try {
            while (c.moveToNext()) {
                String v = c.getString(0);
                if (v != null && !v.trim().isEmpty()) opciones.add(v.trim());
            }
        } finally {
            c.close();
        }
        return new java.util.ArrayList<>(opciones);
    }

    /**
     * Renglones que se pueden asignar a una cuenta (vigentes en el país y año fiscal actuales,
     * sin los calculados), en el orden del catálogo: {renglon_id, "código · nombre"}.
     */
    public static java.util.List<Object[]> renglonesAsignables(SQLiteDatabase db) {
        return renglonesAsignablesEnAnio(db, anioFiscalActual(db));
    }

    /** Igual que renglonesAsignables(), pero vigentes en el año fiscal dado (depuración, R5). */
    public static java.util.List<Object[]> renglonesAsignablesEnAnio(SQLiteDatabase db, int anio) {
        String a = String.valueOf(anio);
        java.util.List<Object[]> lista = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT renglon_id, codigo, nombre FROM " + TABLE_RENGLON_RENTA +
                        " WHERE pais = ? AND naturaleza <> 'Calculado' " +
                        "AND (vigente_desde IS NULL OR vigente_desde <= ?) " +
                        "AND (vigente_hasta IS NULL OR vigente_hasta >= ?) ORDER BY orden, codigo",
                new String[]{paisConfigurado(db), a, a});
        try {
            while (c.moveToNext()) {
                lista.add(new Object[]{c.getLong(0), c.getString(1) + " · " + c.getString(2)});
            }
        } finally {
            c.close();
        }
        return lista;
    }

    /** Renglón por defecto que le corresponde a un tipo de cuenta (por la clase del tipo). */
    public static Long renglonPorDefectoParaTipo(SQLiteDatabase db, Long tipoCuentaId) {
        if (tipoCuentaId == null) return null;
        String naturaleza = null;
        Cursor c = db.rawQuery("SELECT " + SQL_NATURALEZA_RENTA_DE_CLASE + " FROM " +
                TABLE_TIPO_CUENTA + " tc " +
                "JOIN " + TABLE_CLASIFICACION_CONTABLE + " cc ON cc.clasificacion_id = tc.clasificacion_id " +
                "JOIN " + TABLE_CLASE_CONTABLE + " cl ON cl.clase_id = cc.clase_id " +
                "WHERE tc.tipo_cuenta_id = ?", new String[]{String.valueOf(tipoCuentaId)});
        if (c.moveToFirst()) naturaleza = c.getString(0);
        c.close();
        if (naturaleza == null) return null;
        String pais = paisConfigurado(db);
        String a = String.valueOf(anioFiscalActual(db));
        Long renglon = renglonPorDefecto(db, pais, naturaleza, a);
        return renglon != null ? renglon : renglonPorDefecto(db, pais, "No se declara", a);
    }

    /** Soporte sugerido de un tipo de cuenta, o null. */
    public static String soporteSugeridoDeTipo(SQLiteDatabase db, Long tipoCuentaId) {
        if (tipoCuentaId == null) return null;
        Cursor c = db.rawQuery("SELECT soporte_sugerido FROM " + TABLE_TIPO_CUENTA +
                " WHERE tipo_cuenta_id = ?", new String[]{String.valueOf(tipoCuentaId)});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    /** Texto "código · nombre" del renglón de una cuenta, para el panel de atributos. */
    public static String renglonRentaDeCuenta(SQLiteDatabase db, String nombreCuenta) {
        Cursor c = db.rawQuery("SELECT r.codigo || ' · ' || r.nombre FROM cuentas cu JOIN " +
                TABLE_RENGLON_RENTA + " r ON r.renglon_id = cu.renglon_renta_id WHERE cu.Cuenta = ?",
                new String[]{nombreCuenta});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (3-nov, BD v25) — FOTO ANUAL DE RENTA, FASE R3
    //  Saldos por cuenta y renglón congelados en el cierre total 421 (cierre del período contable
    //  y fiscal, decisión de Jorge). La foto nunca se modifica después (las correcciones de años
    //  cerrados van aparte, fase R6) y no depende de que la cuenta siga existiendo: guarda su
    //  nombre y no tiene llave foránea. renglon_efectivo_id = renglon_asignado_id hasta que la
    //  depuración de saldos contrarios (fase R5) lo cambie; "decision" la registra.
    // ─────────────────────────────────────────────
    public static final String TABLE_FOTO_RENTA_ANUAL = "foto_renta_anual";

    public static final String[] COLUMNAS_FOTO_RENTA = {
            "anio_fiscal", "cuenta_id", "cuenta_nombre", "tipo_cuenta_id", "naturaleza_tipo",
            "renglon_asignado_id", "renglon_efectivo_id", "saldo", "movimientos", "decision", "fecha_foto"};

    // ⭐ (3-nov, pedido de Jorge) el CSV de respaldo lleva además el código y el nombre de los
    // renglones: se lee solo y, al restaurar, el renglón se ubica por su código (el id interno
    // puede no coincidir entre instalaciones).
    public static final String[] COLUMNAS_FOTO_RENTA_CSV = {
            "anio_fiscal", "cuenta_id", "cuenta_nombre", "tipo_cuenta_id", "naturaleza_tipo",
            "renglon_asignado_id", "renglon_asignado_codigo", "renglon_asignado_nombre",
            "renglon_efectivo_id", "renglon_efectivo_codigo", "renglon_efectivo_nombre",
            "saldo", "movimientos", "decision", "fecha_foto"};

    public static final String SQL_SELECT_FOTO_RENTA_CSV =
            "SELECT f.anio_fiscal, f.cuenta_id, f.cuenta_nombre, f.tipo_cuenta_id, f.naturaleza_tipo, " +
                    "f.renglon_asignado_id, ra.codigo, ra.nombre, f.renglon_efectivo_id, re.codigo, re.nombre, " +
                    "f.saldo, f.movimientos, f.decision, f.fecha_foto FROM foto_renta_anual f " +
                    "LEFT JOIN renglon_renta ra ON ra.renglon_id = f.renglon_asignado_id " +
                    "LEFT JOIN renglon_renta re ON re.renglon_id = f.renglon_efectivo_id " +
                    "ORDER BY f.anio_fiscal, f.cuenta_id";

    private static final String SQL_CREAR_FOTO_RENTA_ANUAL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_FOTO_RENTA_ANUAL + " (" +
                    "anio_fiscal INTEGER NOT NULL, " +
                    "cuenta_id INTEGER NOT NULL, " +
                    "cuenta_nombre TEXT, " +
                    "tipo_cuenta_id INTEGER, " +
                    "naturaleza_tipo TEXT, " +          // DEUDORA / ACREEDORA del tipo, al momento de la foto
                    "renglon_asignado_id INTEGER, " +
                    "renglon_efectivo_id INTEGER, " +
                    "saldo INTEGER NOT NULL, " +        // al fin del año fiscal
                    "movimientos INTEGER NOT NULL, " +  // transacciones del año, sin saldos iniciales
                    "decision TEXT, " +                 // depuración (R5); NULL = sin depurar
                    "fecha_foto TEXT, " +
                    "PRIMARY KEY (anio_fiscal, cuenta_id))";

    // Saldo al fin del año fiscal (?1 = fin) y movimientos del año (?2 inicio, ?3 fin) por cuenta:
    // {cuenta_id, Cuenta, tipo_cuenta_id, naturaleza del tipo, renglon_renta_id, saldo, movimientos}.
    // Lo usan la foto (R3) y el año provisional del reporte (R4).
    private static final String SQL_SALDOS_RENTA_POR_CUENTA =
            "SELECT cu.cuenta_id, cu.Cuenta, cu.tipo_cuenta_id, tc.naturaleza_normal, cu.renglon_renta_id, " +
                    "COALESCE(SUM(CASE WHEN COALESCE(t.c8_FechaInicial, 0) <= ? THEN t.c5_Valor END), 0), " +
                    "COALESCE(SUM(CASE WHEN t.c8_FechaInicial BETWEEN ? AND ? " +
                    "AND CAST(t.c1_Documento AS INTEGER) <> 0 THEN 1 END), 0) " +
                    "FROM cuentas cu " +
                    "LEFT JOIN " + TABLE_TIPO_CUENTA + " tc ON tc.tipo_cuenta_id = cu.tipo_cuenta_id " +
                    "LEFT JOIN transacciones t ON t.cuenta_id = cu.cuenta_id " +
                    "GROUP BY cu.cuenta_id";

    /**
     * ⭐ NUEVO (3-nov, renta R4): saldos por cuenta de un año, para el reporte:
     * {cuenta_id (Long), nombre, renglon_id (Long o null), saldo (Long)}. De la foto si existe;
     * si no, calculados con los datos de hoy (año provisional). Solo cuentas con saldo o movimiento.
     * ⭐ (3-nov, R5) En el provisional, una cuenta con saldo contrario a su tipo va al renglón que
     * diga la depuración: la decisión ya tomada en la foto de ese año, o si no la sugerencia
     * (decisión del año anterior o naturaleza contraria). Así muestra cómo quedaría declarada.
     */
    public static java.util.List<Object[]> saldosRentaDelAnio(SQLiteDatabase db, int anio, boolean desdeFoto) {
        java.util.List<Object[]> lista = new java.util.ArrayList<>();
        if (desdeFoto) {
            Cursor c = db.rawQuery("SELECT cuenta_id, cuenta_nombre, renglon_efectivo_id, saldo FROM " +
                    TABLE_FOTO_RENTA_ANUAL + " WHERE anio_fiscal = ? ORDER BY cuenta_nombre",
                    new String[]{String.valueOf(anio)});
            try {
                while (c.moveToNext()) {
                    lista.add(new Object[]{c.getLong(0), c.getString(1),
                            c.isNull(2) ? null : c.getLong(2), c.getLong(3)});
                }
            } finally {
                c.close();
            }
            return lista;
        }
        int[] lim = limitesAnioFiscal(db, anio);
        Cursor c = db.rawQuery(SQL_SALDOS_RENTA_POR_CUENTA, new String[]{
                String.valueOf(lim[1]), String.valueOf(lim[0]), String.valueOf(lim[1])});
        java.util.List<Object[]> contrarias = new java.util.ArrayList<>(); // {fila, naturaleza del tipo}
        try {
            while (c.moveToNext()) {
                long saldo = c.getLong(5), movimientos = c.getLong(6);
                if (saldo == 0 && movimientos == 0) continue;
                Object[] fila = new Object[]{c.getLong(0), c.getString(1),
                        c.isNull(4) ? null : c.getLong(4), saldo};
                lista.add(fila);
                String naturalezaTipo = c.getString(3);
                if (fila[2] != null && (("DEUDORA".equals(naturalezaTipo) && saldo < 0)
                        || ("ACREEDORA".equals(naturalezaTipo) && saldo > 0))) {
                    contrarias.add(fila);
                }
            }
        } finally {
            c.close();
        }
        for (Object[] fila : contrarias) {
            Long decidido = null;
            Cursor d = db.rawQuery("SELECT renglon_efectivo_id FROM " + TABLE_FOTO_RENTA_ANUAL +
                            " WHERE anio_fiscal = ? AND cuenta_id = ? AND decision IS NOT NULL",
                    new String[]{String.valueOf(anio), String.valueOf(fila[0])});
            if (d.moveToFirst() && !d.isNull(0)) decidido = d.getLong(0);
            d.close();
            if (decidido != null) {
                fila[2] = decidido;
                continue;
            }
            String naturalezaRenglon = null;
            Cursor r = db.rawQuery("SELECT naturaleza FROM " + TABLE_RENGLON_RENTA + " WHERE renglon_id = ?",
                    new String[]{String.valueOf(fila[2])});
            if (r.moveToFirst()) naturalezaRenglon = r.getString(0);
            r.close();
            if (!"Activo".equals(naturalezaRenglon) && !"Pasivo".equals(naturalezaRenglon)
                    && !"Ingreso".equals(naturalezaRenglon) && !"Gasto".equals(naturalezaRenglon)) continue;
            Object[] sugerencia = sugerenciaDepuracion(db, anio, (Long) fila[0], naturalezaRenglon);
            if (sugerencia[0] != null) fila[2] = sugerencia[0];
        }
        java.util.Collections.sort(lista, (x, y) -> String.valueOf(x[1]).compareToIgnoreCase(String.valueOf(y[1])));
        return lista;
    }

    /** Años con foto, de más reciente a más antiguo. */
    public static java.util.List<Integer> aniosConFotoRenta(SQLiteDatabase db) {
        java.util.List<Integer> anios = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT DISTINCT anio_fiscal FROM " + TABLE_FOTO_RENTA_ANUAL +
                " ORDER BY anio_fiscal DESC", null);
        try {
            while (c.moveToNext()) anios.add(c.getInt(0));
        } finally {
            c.close();
        }
        return anios;
    }

    /**
     * Renglones del país configurado vigentes en el año, en orden:
     * {renglon_id (Long), código, nombre, naturaleza, fórmula}.
     */
    public static java.util.List<Object[]> renglonesDelAnio(SQLiteDatabase db, int anio) {
        String a = String.valueOf(anio);
        java.util.List<Object[]> lista = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT renglon_id, codigo, nombre, naturaleza, formula FROM " +
                        TABLE_RENGLON_RENTA + " WHERE pais = ? " +
                        "AND (vigente_desde IS NULL OR vigente_desde <= ?) " +
                        "AND (vigente_hasta IS NULL OR vigente_hasta >= ?) ORDER BY orden, codigo",
                new String[]{paisConfigurado(db), a, a});
        try {
            while (c.moveToNext()) {
                lista.add(new Object[]{c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4)});
            }
        } finally {
            c.close();
        }
        return lista;
    }

    /** Texto del renglón (código · nombre) por id, aunque ya no esté vigente; o null. */
    public static String etiquetaRenglon(SQLiteDatabase db, Long renglonId) {
        if (renglonId == null) return null;
        Cursor c = db.rawQuery("SELECT codigo || ' · ' || nombre, naturaleza FROM " + TABLE_RENGLON_RENTA +
                " WHERE renglon_id = ?", new String[]{String.valueOf(renglonId)});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    /** {inicio, fin} del año fiscal "anio" como enteros AAAAMMDD (fin = inicio + 1 año − 1 día). */
    public static int[] limitesAnioFiscal(SQLiteDatabase db, int anio) {
        int mes = 1, dia = 1;
        Cursor c = db.rawQuery("SELECT inicio_anio_fiscal_mes, inicio_anio_fiscal_dia FROM " +
                TABLE_CONFIGURACION_APP + " WHERE config_id = 1", null);
        if (c.moveToFirst()) {
            mes = c.getInt(0);
            dia = c.getInt(1);
        }
        c.close();
        java.util.Calendar fin = java.util.Calendar.getInstance();
        fin.clear();
        fin.set(anio + 1, mes - 1, dia);
        fin.add(java.util.Calendar.DAY_OF_MONTH, -1);
        int inicio = anio * 10000 + mes * 100 + dia;
        int finInt = fin.get(java.util.Calendar.YEAR) * 10000 +
                (fin.get(java.util.Calendar.MONTH) + 1) * 100 + fin.get(java.util.Calendar.DAY_OF_MONTH);
        return new int[]{inicio, finInt};
    }

    /**
     * Año fiscal que se propone para la foto: el que acaba de terminar si hoy cae en los primeros
     * 90 días del año fiscal en curso (el cierre anual se hace al empezar el año); si no, el año
     * en curso. El usuario lo confirma o lo cambia.
     */
    public static int anioFiscalPropuestoParaFoto(SQLiteDatabase db) {
        int actual = anioFiscalActual(db);
        int inicio = limitesAnioFiscal(db, actual)[0];
        java.util.Calendar limite = java.util.Calendar.getInstance();
        limite.clear();
        limite.set(inicio / 10000, (inicio / 100) % 100 - 1, inicio % 100);
        limite.add(java.util.Calendar.DAY_OF_MONTH, 90);
        return java.util.Calendar.getInstance().before(limite) ? actual - 1 : actual;
    }

    public static boolean existeFotoRenta(SQLiteDatabase db, int anio) {
        return android.database.DatabaseUtils.queryNumEntries(db, TABLE_FOTO_RENTA_ANUAL,
                "anio_fiscal = ?", new String[]{String.valueOf(anio)}) > 0;
    }

    /**
     * Toma (o reemplaza) la foto del año fiscal "anio": por cada cuenta con saldo o movimiento,
     * su saldo al fin del año fiscal (transacciones con c8_FechaInicial hasta ese día, incluidos
     * los saldos iniciales) y cuántas transacciones tuvo en el año (sin contar saldos iniciales,
     * documento 0). Antes asigna renglón por defecto a las cuentas que no tengan uno vigente.
     * @return cuántas cuentas quedaron en la foto.
     */
    public static int tomarFotoRentaAnual(SQLiteDatabase db, int anio) {
        asignarRenglonYSoportePendientes(db);
        int[] lim = limitesAnioFiscal(db, anio);
        String inicio = String.valueOf(lim[0]), fin = String.valueOf(lim[1]);
        String ahora = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                .format(new java.util.Date());
        int filas = 0;
        db.beginTransaction();
        try {
            db.delete(TABLE_FOTO_RENTA_ANUAL, "anio_fiscal = ?", new String[]{String.valueOf(anio)});
            Cursor c = db.rawQuery(SQL_SALDOS_RENTA_POR_CUENTA, new String[]{fin, inicio, fin});
            try {
                while (c.moveToNext()) {
                    long saldo = c.getLong(5);
                    long movimientos = c.getLong(6);
                    if (saldo == 0 && movimientos == 0) continue;
                    ContentValues cv = new ContentValues();
                    cv.put("anio_fiscal", anio);
                    cv.put("cuenta_id", c.getLong(0));
                    cv.put("cuenta_nombre", c.getString(1));
                    if (c.isNull(2)) cv.putNull("tipo_cuenta_id"); else cv.put("tipo_cuenta_id", c.getLong(2));
                    cv.put("naturaleza_tipo", c.getString(3));
                    if (c.isNull(4)) {
                        cv.putNull("renglon_asignado_id");
                        cv.putNull("renglon_efectivo_id");
                    } else {
                        cv.put("renglon_asignado_id", c.getLong(4));
                        cv.put("renglon_efectivo_id", c.getLong(4));
                    }
                    cv.put("saldo", saldo);
                    cv.put("movimientos", movimientos);
                    cv.put("fecha_foto", ahora);
                    db.insert(TABLE_FOTO_RENTA_ANUAL, null, cv);
                    filas++;
                }
            } finally {
                c.close();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        android.util.Log.w("A1_1_AyudanteBD", "Foto de renta " + anio + " tomada: " + filas + " cuentas");
        return filas;
    }

    /**
     * Resumen de una foto por renglón efectivo, en el orden del catálogo, incluidos los renglones
     * calculados (fórmula simple de sumas y restas de códigos, ej. "29-30"):
     * {código, nombre, total, número de cuentas}.
     * Signo: la app guarda los saldos acreedores (pasivos, ingresos) en negativo; un renglón de
     * naturaleza Pasivo o Ingreso se presenta con el signo invertido, como se declara (Deudas en
     * positivo), para que "29-30" reste. Un saldo contrario dentro del renglón resta, como debe.
     */
    public static java.util.List<Object[]> resumenFotoRentaPorRenglon(SQLiteDatabase db, int anio) {
        java.util.List<Object[]> lista = new java.util.ArrayList<>();
        java.util.Map<String, Long> totalPorCodigo = new java.util.HashMap<>();
        Cursor c = db.rawQuery("SELECT r.codigo, r.nombre, r.naturaleza, r.formula, " +
                "SUM(f.saldo), COUNT(f.cuenta_id) FROM " + TABLE_RENGLON_RENTA + " r " +
                "LEFT JOIN " + TABLE_FOTO_RENTA_ANUAL + " f ON f.renglon_efectivo_id = r.renglon_id AND f.anio_fiscal = ? " +
                "WHERE r.pais = ? AND (r.vigente_desde IS NULL OR r.vigente_desde <= ?) " +
                "AND (r.vigente_hasta IS NULL OR r.vigente_hasta >= ?) " +
                "GROUP BY r.renglon_id ORDER BY r.orden, r.codigo",
                new String[]{String.valueOf(anio), paisConfigurado(db), String.valueOf(anio), String.valueOf(anio)});
        java.util.List<Object[]> calculados = new java.util.ArrayList<>();
        try {
            while (c.moveToNext()) {
                String codigo = c.getString(0);
                long total = c.isNull(4) ? 0 : c.getLong(4);
                if (esNaturalezaDePresentacionNegativa(c.getString(2))) total = -total;
                Object[] fila = {codigo, c.getString(1), total, c.getInt(5)};
                if ("Calculado".equals(c.getString(2))) {
                    calculados.add(new Object[]{fila, c.getString(3)});
                } else {
                    totalPorCodigo.put(codigo, total);
                }
                lista.add(fila);
            }
        } finally {
            c.close();
        }
        for (Object[] calc : calculados) {
            Object[] fila = (Object[]) calc[0];
            fila[2] = evaluarFormulaRenta((String) calc[1], totalPorCodigo);
        }
        return lista;
    }

    /** Renglones cuyo saldo se presenta con el signo invertido (saldos acreedores). */
    public static boolean esNaturalezaDePresentacionNegativa(String naturaleza) {
        return "Pasivo".equals(naturaleza) || "Ingreso".equals(naturaleza);
    }

    /** Suma y resta de códigos de renglón: "29-30", "1+2-5". Un código desconocido vale 0. */
    public static long evaluarFormulaRenta(String formula, java.util.Map<String, Long> totales) {
        if (formula == null) return 0;
        long resultado = 0;
        int signo = 1;
        StringBuilder codigo = new StringBuilder();
        String f = formula.replace(" ", "") + "+";
        for (int i = 0; i < f.length(); i++) {
            char ch = f.charAt(i);
            if ((ch == '+' || ch == '-') && codigo.length() > 0) {
                Long v = totales.get(codigo.toString());
                resultado += signo * (v == null ? 0 : v);
                codigo.setLength(0);
                signo = ch == '+' ? 1 : -1;
            } else if (ch == '+' || ch == '-') {
                signo = ch == '+' ? 1 : -1;
            } else {
                codigo.append(ch);
            }
        }
        return resultado;
    }

    /**
     * Restaura fotos desde el CSV de respaldo (encabezado = COLUMNAS_FOTO_RENTA, por nombre).
     * Reemplaza las filas del mismo año y cuenta. @return filas restauradas.
     */
    public static int importarFotosRenta(SQLiteDatabase db, String textoCsv) {
        String[] lineas = textoCsv.replace("\r", "").split("\n");
        if (lineas.length < 2) return 0;
        String[] encabezado = lineas[0].split(",", -1);
        java.util.Map<String, Integer> col = new java.util.HashMap<>();
        for (int i = 0; i < encabezado.length; i++) {
            col.put(encabezado[i].trim().toLowerCase(java.util.Locale.ROOT), i);
        }
        int filas = 0;
        for (int n = 1; n < lineas.length; n++) {
            if (lineas[n].trim().isEmpty()) continue;
            String[] d = lineas[n].split(",", -1);
            Integer anio = enteroPlantilla(campoPlantilla(d, col, "anio_fiscal"));
            Integer cuenta = enteroPlantilla(campoPlantilla(d, col, "cuenta_id"));
            if (anio == null || cuenta == null) continue;
            ContentValues cv = new ContentValues();
            for (String nombre : COLUMNAS_FOTO_RENTA) {
                String v = campoPlantilla(d, col, nombre);
                if (v == null) cv.putNull(nombre); else cv.put(nombre, v);
            }
            // ⭐ (3-nov) Si el CSV trae el código del renglón, manda sobre el id interno.
            Long asignado = buscarRenglonPorCodigoEnAnio(db, campoPlantilla(d, col, "renglon_asignado_codigo"), anio);
            if (asignado != null) cv.put("renglon_asignado_id", asignado);
            Long efectivo = buscarRenglonPorCodigoEnAnio(db, campoPlantilla(d, col, "renglon_efectivo_codigo"), anio);
            if (efectivo != null) cv.put("renglon_efectivo_id", efectivo);
            if (cv.get("saldo") == null) cv.put("saldo", 0);
            if (cv.get("movimientos") == null) cv.put("movimientos", 0);
            if (db.insertWithOnConflict(TABLE_FOTO_RENTA_ANUAL, null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                filas++;
            }
        }
        return filas;
    }

    /** Renglón del país configurado con ese código, vigente en el año fiscal dado (o null). */
    public static Long buscarRenglonPorCodigoEnAnio(SQLiteDatabase db, String codigo, int anio) {
        if (codigo == null || codigo.trim().isEmpty()) return null;
        String a = String.valueOf(anio);
        Cursor c = db.rawQuery("SELECT renglon_id FROM " + TABLE_RENGLON_RENTA +
                        " WHERE pais = ? AND codigo = ? " +
                        "AND (vigente_desde IS NULL OR vigente_desde <= ?) " +
                        "AND (vigente_hasta IS NULL OR vigente_hasta >= ?) ORDER BY orden LIMIT 1",
                new String[]{paisConfigurado(db), codigo.trim(), a, a});
        try {
            return c.moveToFirst() ? c.getLong(0) : null;
        } finally {
            c.close();
        }
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (3-nov, renta R5) — DEPURACIÓN DE SALDOS CONTRARIOS
    //  Una cuenta de la foto tiene saldo contrario si su saldo va contra la naturaleza de su tipo
    //  de cuenta (DEUDORA con saldo negativo, ACREEDORA con saldo positivo). Los tipos correctores
    //  (provisión, depreciación) son ACREEDORA dentro del Activo: su saldo negativo es normal y no
    //  aparecen. Solo cuentan las de renglones de Activo, Pasivo, Ingreso o Gasto. La decisión del
    //  usuario cambia renglon_efectivo_id de la foto y queda registrada en "decision".
    // ─────────────────────────────────────────────
    public static final String DECISION_DEPURACION_CONFIRMADA = "confirmada";

    /**
     * Cuentas con saldo contrario en la foto del año:
     * {cuenta_id (Long), nombre, saldo (Long), renglon_asignado_id (Long), naturaleza del renglón
     * asignado, renglon_efectivo_id (Long o null), decision}.
     */
    public static java.util.List<Object[]> cuentasConSaldoContrario(SQLiteDatabase db, int anio) {
        java.util.List<Object[]> lista = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT f.cuenta_id, f.cuenta_nombre, f.saldo, f.renglon_asignado_id, " +
                        "r.naturaleza, f.renglon_efectivo_id, f.decision FROM " + TABLE_FOTO_RENTA_ANUAL + " f " +
                        "JOIN " + TABLE_RENGLON_RENTA + " r ON r.renglon_id = f.renglon_asignado_id " +
                        "WHERE f.anio_fiscal = ? AND r.naturaleza IN ('Activo', 'Pasivo', 'Ingreso', 'Gasto') " +
                        "AND ((f.naturaleza_tipo = 'DEUDORA' AND f.saldo < 0) " +
                        "OR (f.naturaleza_tipo = 'ACREEDORA' AND f.saldo > 0)) " +
                        "ORDER BY ABS(f.saldo) DESC",
                new String[]{String.valueOf(anio)});
        try {
            while (c.moveToNext()) {
                lista.add(new Object[]{c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3),
                        c.getString(4), c.isNull(5) ? null : c.getLong(5), c.getString(6)});
            }
        } finally {
            c.close();
        }
        return lista;
    }

    /**
     * Destino sugerido para un saldo contrario: {renglon_id (Long), motivo}. Primero la decisión
     * del año anterior para esa cuenta (por código del renglón, decisión de Jorge: "recordar,
     * solo como sugerencia"); si no hay, el renglón por defecto de la naturaleza contraria
     * (Activo ↔ Pasivo, Ingreso ↔ Gasto).
     */
    public static Object[] sugerenciaDepuracion(SQLiteDatabase db, int anio, long cuentaId, String naturalezaAsignado) {
        Cursor c = db.rawQuery("SELECT r.codigo FROM " + TABLE_FOTO_RENTA_ANUAL + " f JOIN " +
                        TABLE_RENGLON_RENTA + " r ON r.renglon_id = f.renglon_efectivo_id " +
                        "WHERE f.anio_fiscal = ? AND f.cuenta_id = ? AND f.decision IS NOT NULL",
                new String[]{String.valueOf(anio - 1), String.valueOf(cuentaId)});
        String codigoAnterior = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        Long anterior = buscarRenglonPorCodigoEnAnio(db, codigoAnterior, anio);
        if (anterior != null) return new Object[]{anterior, "como en " + (anio - 1)};
        String contraria;
        switch (naturalezaAsignado == null ? "" : naturalezaAsignado) {
            case "Activo": contraria = "Pasivo"; break;
            case "Pasivo": contraria = "Activo"; break;
            case "Ingreso": contraria = "Gasto"; break;
            case "Gasto": contraria = "Ingreso"; break;
            default: return new Object[]{null, ""};
        }
        return new Object[]{renglonPorDefecto(db, paisConfigurado(db), contraria, String.valueOf(anio)),
                "naturaleza contraria"};
    }

    /** Guarda la decisión: el renglón efectivo de esa cuenta en la foto del año. */
    public static void guardarDecisionDepuracion(SQLiteDatabase db, int anio, long cuentaId, long renglonId) {
        db.execSQL("UPDATE " + TABLE_FOTO_RENTA_ANUAL + " SET renglon_efectivo_id = ?, decision = ? " +
                        "WHERE anio_fiscal = ? AND cuenta_id = ?",
                new Object[]{renglonId, DECISION_DEPURACION_CONFIRMADA, anio, cuentaId});
    }

    /** Busca un renglón vigente por su código (al restaurar cuentas desde CSV). */
    public static Long buscarRenglonRentaVigentePorCodigo(SQLiteDatabase db, String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return null;
        String a = String.valueOf(anioFiscalActual(db));
        Cursor c = db.rawQuery("SELECT renglon_id FROM " + TABLE_RENGLON_RENTA +
                        " WHERE pais = ? AND codigo = ? " +
                        "AND (vigente_desde IS NULL OR vigente_desde <= ?) " +
                        "AND (vigente_hasta IS NULL OR vigente_hasta >= ?) ORDER BY orden LIMIT 1",
                new String[]{paisConfigurado(db), codigo.trim(), a, a});
        try {
            return c.moveToFirst() ? c.getLong(0) : null;
        } finally {
            c.close();
        }
    }

    /**
     * ⭐ NUEVO (31-oct, paso 4 / Fase C): condición SQL sobre "transacciones" (sin alias) para
     * las transacciones de cuentas CERRABLES HOY. Empareja por cuenta_id y, si falta, por nombre
     * — igual que los resúmenes de cierre de A5_1_BackupManager.
     */
    // ⭐ CAMBIO (1-nov, Fase E1): solo por cuenta_id (sin respaldo por c3_Cuenta).
    public static final String CONDICION_TRANSACCION_CERRABLE =
            "(cuenta_id IN (SELECT cuenta_id FROM cuentas WHERE Cerrable = 1))";

    /**
     * ⭐ NUEVO (31-oct, BD v18): convierte el texto de los CSV / la UI a 0/1 para
     * cuentas.Cerrable y cuentas.conciliable. Acepta la palabra ("Cerrable", "Conciliable", sin
     * importar mayúsculas) o "1"; cualquier otra cosa (vacío, NULL, "No Aplica") es 0.
     */
    public static int textoAMarca01(String texto, String palabra) {
        if (texto == null) return 0;
        String t = texto.trim();
        return (t.equalsIgnoreCase(palabra) || t.equals("1")) ? 1 : 0;
    }

    // ⭐ NUEVO (31-oct): niveles de liquidez en el orden en que se muestran (de más a menos
    // disponible). "No aplica" no se muestra en los resúmenes.
    public static final String[] NIVELES_LIQUIDEZ = {
            "Inmediata", "Requiere trámite", "Depende de terceros", "No disponible a corto plazo"};

    /** Liquidez del tipo de cuenta, o null si no hay tipo o no tiene liquidez. */
    public static String liquidezDeTipoCuenta(SQLiteDatabase db, Long tipoCuentaId) {
        if (tipoCuentaId == null) return null;
        Cursor c = db.rawQuery("SELECT liquidez FROM " + TABLE_TIPO_CUENTA +
                " WHERE tipo_cuenta_id = ?", new String[]{String.valueOf(tipoCuentaId)});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    /** Liquidez de una cuenta (heredada de su tipo), por nombre de cuenta; null si no tiene. */
    public static String liquidezDeCuenta(SQLiteDatabase db, String nombreCuenta) {
        if (nombreCuenta == null) return null;
        Cursor c = db.rawQuery("SELECT tc.liquidez FROM cuentas c JOIN " + TABLE_TIPO_CUENTA +
                " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id WHERE c.Cuenta = ?",
                new String[]{nombreCuenta});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Saldo total (suma de c5_Valor de las transacciones) por nivel de liquidez, en el orden de
     * NIVELES_LIQUIDEZ. Todos los niveles aparecen (en 0 si no tienen saldo). Une por cuenta_id,
     * igual que el Informe y los Indicadores.
     */
    public static java.util.LinkedHashMap<String, Long> sumarSaldosPorLiquidez(SQLiteDatabase db) {
        java.util.LinkedHashMap<String, Long> totales = new java.util.LinkedHashMap<>();
        for (String nivel : NIVELES_LIQUIDEZ) totales.put(nivel, 0L);
        Cursor c = db.rawQuery(
                "SELECT tc.liquidez, SUM(t.c5_Valor) FROM transacciones t " +
                        "JOIN cuentas c ON c.cuenta_id = t.cuenta_id " +
                        "JOIN " + TABLE_TIPO_CUENTA + " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id " +
                        "WHERE tc.liquidez IS NOT NULL GROUP BY tc.liquidez", null);
        try {
            while (c.moveToNext()) {
                String nivel = c.getString(0);
                if (totales.containsKey(nivel)) totales.put(nivel, c.getLong(1));
            }
        } finally {
            c.close();
        }
        return totales;
    }

    /** tipo_cuenta_id cuyo nombre coincide exactamente, o null si no existe. */
    public static Long buscarTipoCuentaIdPorNombre(SQLiteDatabase db, String nombreTipo) {
        if (nombreTipo == null || nombreTipo.trim().isEmpty()) return null;
        Cursor c = db.rawQuery("SELECT tipo_cuenta_id FROM " + TABLE_TIPO_CUENTA +
                " WHERE nombre = ?", new String[]{nombreTipo.trim()});
        try {
            return c.moveToFirst() ? c.getLong(0) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Reclasifica TODAS las cuentas cuyo tipo_cuenta_id venga del catálogo provisional (1..9):
     * primero por nombre de cuenta (asignación de Jorge) y, si el nombre no está, por el tipo
     * provisional. Pensado solo para (a) la migración v16 y (b) restauraciones de backups en
     * formato antiguo. NO debe llamarse sobre datos que ya usan el catálogo definitivo.
     * @return cuántas cuentas quedaron sin tipo.
     */
    public static int reclasificarCuentasDesdeCatalogoProvisional(SQLiteDatabase db) {
        java.util.List<long[]> cambios = new java.util.ArrayList<>();
        java.util.List<Long> sinTipo = new java.util.ArrayList<>();
        Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta, tipo_cuenta_id FROM cuentas", null);
        try {
            while (c.moveToNext()) {
                long cuentaId = c.getLong(0);
                Long nuevo = tipoDefinitivoPorNombreDeCuenta(c.getString(1));
                if (nuevo == null && !c.isNull(2)) {
                    long porProvisional = tipoDefinitivoDesdeProvisional(c.getLong(2));
                    if (porProvisional > 0) nuevo = porProvisional;
                }
                if (nuevo != null) {
                    cambios.add(new long[]{cuentaId, nuevo});
                } else {
                    sinTipo.add(cuentaId);
                }
            }
        } finally {
            c.close();
        }
        for (long[] cambio : cambios) {
            db.execSQL("UPDATE cuentas SET tipo_cuenta_id = ? WHERE cuenta_id = ?",
                    new Object[]{cambio[1], cambio[0]});
        }
        for (Long cuentaId : sinTipo) {
            db.execSQL("UPDATE cuentas SET tipo_cuenta_id = NULL WHERE cuenta_id = ?",
                    new Object[]{cuentaId});
        }
        return sinTipo.size();
    }

    /**
     * Copia a cada transacción el tipo_cuenta_id actual de su cuenta (por cuenta_id). Deja la
     * "foto" del tipo alineada con la cuenta — mismo criterio que la Auditoría de Clasificación,
     * que reporta como desalineadas las transacciones cuyo tipo difiere del de su cuenta.
     */
    public static void alinearTipoCuentaDeTransacciones(SQLiteDatabase db) {
        db.execSQL("UPDATE transacciones SET tipo_cuenta_id = " +
                "(SELECT c.tipo_cuenta_id FROM cuentas c WHERE c.cuenta_id = transacciones.cuenta_id) " +
                "WHERE cuenta_id IS NOT NULL");
    }

    /**
     * Migración v16 de la BD: del catálogo provisional (ids 1..9) al definitivo (ids 1..21).
     * Como los ids se reutilizan con otro significado, primero se calcula el tipo nuevo de cada
     * cuenta, luego se sueltan las referencias (NULL siempre es válido para la FK), se reemplaza
     * el catálogo y se vuelve a asignar. Además retira la cuenta comodín sin nombre ("Item 0"),
     * solo si no tiene transacciones ni artículos de inventario.
     */
    private void migrarACatalogoTipoCuentaDefinitivo(SQLiteDatabase db) {
        java.util.Map<Long, Long> nuevoPorCuenta = new java.util.HashMap<>();
        Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta, tipo_cuenta_id FROM cuentas", null);
        try {
            while (c.moveToNext()) {
                Long nuevo = tipoDefinitivoPorNombreDeCuenta(c.getString(1));
                if (nuevo == null && !c.isNull(2)) {
                    long porProvisional = tipoDefinitivoDesdeProvisional(c.getLong(2));
                    if (porProvisional > 0) nuevo = porProvisional;
                }
                if (nuevo != null) nuevoPorCuenta.put(c.getLong(0), nuevo);
            }
        } finally {
            c.close();
        }

        db.execSQL("UPDATE cuentas SET tipo_cuenta_id = NULL");
        db.execSQL("UPDATE transacciones SET tipo_cuenta_id = NULL");
        db.execSQL("DELETE FROM " + TABLE_TIPO_CUENTA);
        insertarCatalogoDefinitivo(db);

        for (java.util.Map.Entry<Long, Long> e : nuevoPorCuenta.entrySet()) {
            db.execSQL("UPDATE cuentas SET tipo_cuenta_id = ? WHERE cuenta_id = ?",
                    new Object[]{e.getValue(), e.getKey()});
        }
        alinearTipoCuentaDeTransacciones(db);

        db.execSQL("DELETE FROM cuentas WHERE TRIM(Cuenta) = '' " +
                "AND cuenta_id NOT IN (SELECT cuenta_id FROM transacciones WHERE cuenta_id IS NOT NULL) " +
                "AND cuenta_id NOT IN (SELECT cuenta_id FROM items_inventario)");
        android.util.Log.w("A1_1_AyudanteBD", "Migración v16: catálogo definitivo aplicado a " +
                nuevoPorCuenta.size() + " cuentas");
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

    // ⭐ NUEVO v14 — Tanda 1 del modelo de costeo por inventario: índices simples sobre las 2
    // tablas nuevas (misma razón que crearIndicesTransacciones — aceleran las consultas por
    // cuenta/por artículo que van a necesitar las tandas siguientes). Se comparte entre onCreate
    // y la migración v14 por la misma razón que los demás índices de esta clase.
    private void crearIndicesInventario(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_inventario_cuenta ON items_inventario(cuenta_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_inventario_item ON transacciones_inventario(item_id)");
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
        // ⭐ RETIRADO — v11 tanda 3 (parte D): los catálogos de Grupo1/Grupo2 (TABLE_CATALOGO_GRUPO1/
        // GRUPO2) ya no se crean ni se siembran en instalaciones frescas — eran soporte para
        // Grupo1/Grupo2, que se retiran en esta misma parte D (ver comentario de clase arriba).
        // Las constantes, la DDL (SQL_CREAR_CATALOGO_GRUPO1/GRUPO2) y sembrarCatalogosGrupo1Y2()
        // se conservan intactas: las sigue usando el bloque histórico "if (oldVersion < 4)" de
        // onUpgrade(), que no se toca (ver más abajo, y la migración v13 que dropea estas 2
        // tablas para dispositivos existentes).
        // ⭐ NUEVO v9 — Fase 6: tablas del modelo de clasificación contable definitivo, sembradas
        // desde el inicio en instalaciones frescas. tipo_cuenta se crea vacía a propósito: no hay
        // datos legados de qué backfillear todavía (ver comentario de clase arriba).
        db.execSQL(SQL_CREAR_CLASE_CONTABLE);
        db.execSQL(SQL_CREAR_CLASIFICACION_CONTABLE);
        db.execSQL(SQL_CREAR_TIPO_CUENTA);
        sembrarClaseYClasificacionContable(db);
        // ⭐ NUEVO (31-oct): una instalación nueva nace ya con el catálogo definitivo de
        // tipo_cuenta (antes nacía vacío y el spinner de "Tipo" quedaba sin opciones).
        insertarCatalogoDefinitivo(db);
        // ⭐ NUEVO v14 — Tanda 1 del modelo de costeo por inventario: tablas nuevas, vacías en
        // toda instalación fresca (con_inventario en "cuentas" ya nace en 0 por su propio
        // DEFAULT, dentro de crearCuentas_String — ver comentario de clase arriba).
        db.execSQL(SQL_CREAR_ITEMS_INVENTARIO);
        db.execSQL(SQL_CREAR_TRANSACCIONES_INVENTARIO);
        crearIndicesInventario(db);
        // ⭐ NUEVO (3-nov, BD v24, renta R1): configuración y catálogo de renglones (Colombia).
        crearTablasRenta(db);
        importarPlantillaRenta(db, PLANTILLA_RENTA_COLOMBIA);
        db.execSQL(SQL_CREAR_FOTO_RENTA_ANUAL); // ⭐ NUEVO (3-nov, BD v25, renta R3)
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

        // ⭐ NUEVO v10 — Fase 6 (parte B): agrega cuentas.cuenta_seguimiento (ver comentario de
        // clase arriba) y marca automáticamente la cuenta que hoy se llame "CxC Enrique" — el
        // nombre que F5_1_Indicadores/F5_3_GraficasIndicadores tenían hardcodeado — para que el
        // panel de Indicadores siga funcionando igual sin que Jorge tenga que hacer nada manual.
        // Si no existe ninguna cuenta con ese nombre exacto (dispositivo nuevo, o la cuenta fue
        // renombrada antes de esta migración), NO se marca ninguna — se registra en el log en
        // vez de adivinar, y el panel de Indicadores queda sin cuenta de seguimiento hasta que
        // el usuario marque una manualmente desde F2_Cuentas.
        if (oldVersion < 10) {

            // 1) cuentas: agregar la columna nueva (ALTER TABLE simple, sin recrear la tabla).
            db.execSQL("ALTER TABLE cuentas ADD COLUMN cuenta_seguimiento INTEGER");

            // 2) Backfill de continuidad: marcar "CxC Enrique" si existe, para no romper el
            //    panel de Indicadores que ya dependía de ese nombre fijo.
            db.execSQL("UPDATE cuentas SET cuenta_seguimiento = 1 WHERE Cuenta = 'CxC Enrique'");

            // 3) Diagnóstico: confirmar que quedó marcada exactamente una cuenta (lo esperado).
            //    Si queda en 0, el panel de Indicadores no tendrá cuenta de seguimiento hasta que
            //    el usuario marque una manualmente; si queda en más de 1 (nombres duplicados),
            //    conviene revisar cuál de ellas es la correcta.
            Cursor cuentasSeguimiento = db.rawQuery(
                    "SELECT COUNT(*) FROM cuentas WHERE cuenta_seguimiento = 1", null);
            if (cuentasSeguimiento.moveToFirst()) {
                int total = cuentasSeguimiento.getInt(0);
                if (total == 1) {
                    android.util.Log.w("A1_1_AyudanteBD",
                            "Migración v10: 1 cuenta marcada como cuenta_seguimiento (\"CxC " +
                                    "Enrique\" encontrada y migrada correctamente)");
                } else {
                    android.util.Log.w("A1_1_AyudanteBD",
                            "Migración v10: " + total + " cuentas marcadas como cuenta_seguimiento " +
                                    "(se esperaba exactamente 1 — revisar si \"CxC Enrique\" no " +
                                    "existe con ese nombre exacto, o si hay nombres duplicados)");
                }
            }
            cuentasSeguimiento.close();
        }

        // ⭐ NUEVO v11 — Fase 6 (parte C) / v11 tanda 2 (real): agrega transacciones.tipo_cuenta_id
        // (ver comentario de clase arriba para el porqué completo) y hace un backfill de mejor
        // esfuerzo: cada transacción existente recibe el tipo_cuenta_id ACTUAL de su cuenta (vía
        // cuenta_id). No es una reconstrucción histórica real (no hay forma de saber cuál era en
        // el pasado si nunca se guardó) — es el punto de partida más razonable disponible, y
        // corregible después con las herramientas de Sheets si hiciera falta ajustar algo puntual.
        if (oldVersion < 11) {

            // 1) transacciones: agregar la columna nueva (ALTER TABLE simple, sin recrear la
            //    tabla — mismo criterio que tipo_cuenta_id en cuentas, v9, y cuenta_seguimiento
            //    en cuentas, v10; no cambia el orden posicional de ninguna columna existente).
            db.execSQL("ALTER TABLE transacciones ADD COLUMN tipo_cuenta_id INTEGER " +
                    "REFERENCES tipo_cuenta(tipo_cuenta_id)");

            // 2) Backfill: para cada transacción con cuenta_id conocido, copiar el
            //    tipo_cuenta_id ACTUAL de esa cuenta. Las transacciones con cuenta_id NULL
            //    (huérfanas, ya diagnosticadas en versiones anteriores) quedan con
            //    tipo_cuenta_id NULL también — no hay de dónde tomarlo.
            db.execSQL(
                    "UPDATE transacciones SET tipo_cuenta_id = (" +
                            "SELECT c.tipo_cuenta_id FROM cuentas c " +
                            "WHERE c.cuenta_id = transacciones.cuenta_id" +
                            ") WHERE cuenta_id IS NOT NULL");

            // 3) Índice — misma razón que los demás: acelerar el comparador de auditoría
            //    (obtenerTransaccionesDesalineadas) y cualquier consulta futura que filtre o
            //    agrupe por tipo de cuenta a nivel de transacción.
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");

            // 4) Diagnóstico: cuántas transacciones quedaron con el backfill aplicado vs. cuántas
            //    quedaron en NULL (esperado solo para las huérfanas sin cuenta_id, o para cuentas
            //    cuyo propio tipo_cuenta_id todavía sea NULL).
            Cursor conTipoCuenta = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE tipo_cuenta_id IS NOT NULL", null);
            if (conTipoCuenta.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v11: " + conTipoCuenta.getInt(0) +
                                " transacciones recibieron tipo_cuenta_id por backfill");
            }
            conTipoCuenta.close();

            Cursor sinTipoCuentaTransacciones = db.rawQuery(
                    "SELECT COUNT(*) FROM transacciones WHERE tipo_cuenta_id IS NULL", null);
            if (sinTipoCuentaTransacciones.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v11: " + sinTipoCuentaTransacciones.getInt(0) +
                                " transacciones quedaron con tipo_cuenta_id NULL (esperado solo " +
                                "para huérfanas sin cuenta_id, o cuentas sin tipo_cuenta_id propio)");
            }
            sinTipoCuentaTransacciones.close();
        }

        // ⭐ NUEVO v12 — v11 tanda 3 (parte A): "cuentas" gana una columna propia "conciliable"
        // (texto, valor literal "Conciliable" o NULL cuando no aplica) — mismo patrón que
        // "Cerrable" desde v6. Hasta ahora, si una cuenta era conciliable o no se derivaba
        // leyendo el texto de Grupo2 ("Exigible Conciliable"/"...No conciliable" — ver
        // A22_QueryManager.queryAzConciliablesAccountsWithFilter()), el mismo patrón de texto
        // hardcodeado que se está retirando de "transacciones"/"cuentas" en esta tanda. Se
        // adelanta este campo ahora, ANTES de tocar Grupo1/Grupo2, para no perder esa
        // funcionalidad (el spinner de "cuenta de conciliación" al crear/editar una
        // transacción) cuando Grupo1/Grupo2 se retiren más adelante.
        if (oldVersion < 12) {

            // 1) ALTER TABLE simple — mismo criterio que Cerrable (v6) y tipo_cuenta_id (v9):
            //    no recrea la tabla, no cambia el orden posicional de ninguna columna existente.
            db.execSQL("ALTER TABLE cuentas ADD COLUMN conciliable TEXT");

            // 2) Backfill de mejor esfuerzo: se deriva del mismo texto de Grupo2 que hasta
            //    ahora usaba el filtro en vivo — "Exigible Conciliable" y "No exigible
            //    Conciliable" quedan en 'Conciliable'; "...No conciliable" (las otras 2
            //    combinaciones) quedan en NULL. Reproduce EXACTAMENTE el mismo resultado que
            //    daba hoy el filtro "Grupo2 LIKE '%Exigible Conciliable%'" (LIKE de SQLite es
            //    case-insensitive por defecto, así que ya capturaba ambas variantes
            //    "Exigible"/"No exigible" — la única palabra que distingue es "No" antes de
            //    "conciliable").
            db.execSQL(
                    "UPDATE cuentas SET conciliable = 'Conciliable' " +
                            "WHERE Grupo2 NOT LIKE '%No conciliable%'");

            // 3) Diagnóstico: cuántas cuentas quedaron marcadas conciliables vs. no, para poder
            //    comparar a simple vista contra lo que mostraba antes el spinner de "cuenta de
            //    conciliación".
            Cursor conciliablesCount = db.rawQuery(
                    "SELECT COUNT(*) FROM cuentas WHERE conciliable = 'Conciliable'", null);
            if (conciliablesCount.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v12: " + conciliablesCount.getInt(0) +
                                " cuentas quedaron marcadas 'Conciliable' por backfill");
            }
            conciliablesCount.close();

            Cursor noConciliablesCount = db.rawQuery(
                    "SELECT COUNT(*) FROM cuentas WHERE conciliable IS NULL", null);
            if (noConciliablesCount.moveToFirst()) {
                android.util.Log.w("A1_1_AyudanteBD",
                        "Migración v12: " + noConciliablesCount.getInt(0) +
                                " cuentas quedaron con conciliable NULL (Grupo2 'No conciliable')");
            }
            noConciliablesCount.close();
        }

        // ⭐ NUEVO v13 — v11 tanda 3 (parte D): retira definitivamente cuentas.Grupo1/Grupo2 y
        // transacciones.c10_Grupo1/c11_Grupo2 (ver comentario de clase arriba — este es el paso
        // final de la tanda 3: desde la parte C ya no se escribían valores reales en estas
        // columnas, solo "" — el modelo de clasificación vive por completo en tipo_cuenta_id
        // desde entonces). SQLite en minSdk 27 no soporta ALTER TABLE DROP COLUMN de forma
        // confiable, así que se recrean ambas tablas — mismo patrón ya usado en v3 (cuenta_id/
        // codigo_cuenta) y v8 (transaccion_id): tabla temporal con el esquema final → copiar
        // filas preservando exactamente los valores de las llaves primarias (cuenta_id,
        // transaccion_id) → DROP de la original → RENAME de la temporal. También se dropean los
        // 2 catálogos de Grupo1/Grupo2 (TABLE_CATALOGO_GRUPO1/GRUPO2 — ver Fase 2, v4), que ya no
        // tienen ningún propósito sin Grupo1/Grupo2 que catalogar.
        if (oldVersion < 13) {

            // 1) Diagnóstico previo: cuántas filas tiene cada tabla ANTES de recrearla, para
            //    poder comparar después que no se perdió ni duplicó ninguna.
            int cuentasAntes = 0;
            Cursor cuentasAntesCursor = db.rawQuery("SELECT COUNT(*) FROM cuentas", null);
            if (cuentasAntesCursor.moveToFirst()) cuentasAntes = cuentasAntesCursor.getInt(0);
            cuentasAntesCursor.close();

            int transaccionesAntes = 0;
            Cursor transaccionesAntesCursor = db.rawQuery("SELECT COUNT(*) FROM transacciones", null);
            if (transaccionesAntesCursor.moveToFirst()) transaccionesAntes = transaccionesAntesCursor.getInt(0);
            transaccionesAntesCursor.close();

            // 2) Recrear "cuentas" sin Grupo1/Grupo2, preservando cuenta_id EXACTO (se inserta
            //    explícitamente en vez de dejar que AUTOINCREMENT numere de nuevo — mismo criterio
            //    que transaccion_id en la migración v8 — para no romper ninguna referencia
            //    existente hacia cuenta_id, empezando por transacciones.cuenta_id).
            db.execSQL("CREATE TABLE cuentas_temp_v13 (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT, " +
                    "Cerrable TEXT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id), " +
                    "cuenta_seguimiento INTEGER, " +
                    "conciliable TEXT)");
            db.execSQL("INSERT INTO cuentas_temp_v13 (" +
                    "cuenta_id, Item, Cuenta, Fecha, codigo_cuenta, Cerrable, tipo_cuenta_id, " +
                    "cuenta_seguimiento, conciliable) " +
                    "SELECT cuenta_id, Item, Cuenta, Fecha, codigo_cuenta, Cerrable, tipo_cuenta_id, " +
                    "cuenta_seguimiento, conciliable FROM cuentas");
            db.execSQL("DROP TABLE cuentas");
            db.execSQL("ALTER TABLE cuentas_temp_v13 RENAME TO cuentas");

            // 3) Recrear "transacciones" sin c10_Grupo1/c11_Grupo2, preservando transaccion_id
            //    EXACTO (mismo criterio que en el paso anterior, y que ya usó la propia v8 para
            //    esta misma tabla).
            db.execSQL("CREATE TABLE transacciones_temp_v13(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, c12_ColumnaDisponible TEXT NOT NULL, " +
                    "c13_ColumnaDisponible TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v13 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id, tipo_cuenta_id) " +
                    "SELECT transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id, tipo_cuenta_id " +
                    "FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v13 RENAME TO transacciones");

            // 4) Índices — DROP TABLE se los lleva consigo, así que se recrean todos (mismos
            //    helpers que onCreate y las migraciones v8/v9/v11).
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cuentas_tipo_cuenta_id ON cuentas(tipo_cuenta_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");

            // 5) Los catálogos de Grupo1/Grupo2 (Fase 2, v4) ya no tienen propósito — se dropean.
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATALOGO_GRUPO1);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATALOGO_GRUPO2);

            // 6) Diagnóstico posterior: confirmar que ninguna tabla perdió o duplicó filas al
            //    recrearse.
            int cuentasDespues = 0;
            Cursor cuentasDespuesCursor = db.rawQuery("SELECT COUNT(*) FROM cuentas", null);
            if (cuentasDespuesCursor.moveToFirst()) cuentasDespues = cuentasDespuesCursor.getInt(0);
            cuentasDespuesCursor.close();
            android.util.Log.w("A1_1_AyudanteBD",
                    "Migración v13: cuentas tenía " + cuentasAntes + " filas antes, " +
                            cuentasDespues + " después de retirar Grupo1/Grupo2" +
                            (cuentasAntes == cuentasDespues ? " (coincide)" : " (¡NO COINCIDE!)"));

            int transaccionesDespues = 0;
            Cursor transaccionesDespuesCursor = db.rawQuery("SELECT COUNT(*) FROM transacciones", null);
            if (transaccionesDespuesCursor.moveToFirst()) transaccionesDespues = transaccionesDespuesCursor.getInt(0);
            transaccionesDespuesCursor.close();
            android.util.Log.w("A1_1_AyudanteBD",
                    "Migración v13: transacciones tenía " + transaccionesAntes + " filas antes, " +
                            transaccionesDespues + " después de retirar c10_Grupo1/c11_Grupo2" +
                            (transaccionesAntes == transaccionesDespues ? " (coincide)" : " (¡NO COINCIDE!)"));
        }

        // ⭐ NUEVO v14 — Tanda 1 del "Modelo de Costeo por Inventario en Cuentas" (ver comentario
        // de clase arriba). A diferencia de la v13, esto NO recrea ninguna tabla existente: solo
        // agrega una columna nueva con DEFAULT (ALTER TABLE, soportado sin problema por SQLite a
        // diferencia de DROP COLUMN) y crea 2 tablas nuevas, ambas vacías — no hay ningún dato
        // legado que backfillear en esta tanda.
        if (oldVersion < 14) {
            db.execSQL("ALTER TABLE cuentas ADD COLUMN con_inventario INTEGER NOT NULL DEFAULT 0");
            db.execSQL(SQL_CREAR_ITEMS_INVENTARIO);
            db.execSQL(SQL_CREAR_TRANSACCIONES_INVENTARIO);
            crearIndicesInventario(db);
        }

        // ⭐ NUEVO v15 (ver comentario de clase arriba): un dispositivo que ya estaba en v14
        // tiene transacciones_inventario SIN costo_total — se agrega y se backfillea con la
        // misma aproximación que ya se usaba (unidades × precio_unitario, el precio entero que
        // ya tenían esas filas). Un dispositivo que salta de una versión anterior a 14
        // directamente a 15 NO pasa por aquí para esta tabla: el bloque "if (oldVersion < 14)"
        // de arriba ya la crea de cero con SQL_CREAR_TRANSACCIONES_INVENTARIO, que ya incluye
        // costo_total — así que no hay nada que backfillear ahí (la tabla nace vacía).
        if (oldVersion < 15) {
            db.execSQL("ALTER TABLE transacciones_inventario ADD COLUMN costo_total INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE transacciones_inventario SET costo_total = unidades * precio_unitario");
        }
        // ⭐ NUEVO (31-oct): versión 16 de la BD — catálogo definitivo de tipo_cuenta (ver
        // migrarACatalogoTipoCuentaDefinitivo). OJO: "16" aquí es la versión de la base de datos,
        // no la "v16" del proyecto de costeo por inventario ni la del plan original.
        if (oldVersion < 16) {
            migrarACatalogoTipoCuentaDefinitivo(db);
        }
        // ⭐ NUEVO (31-oct): versión 17 de la BD — nivel de liquidez por tipo de cuenta.
        if (oldVersion < 17) {
            if (!tieneColumna(db, TABLE_TIPO_CUENTA, "liquidez")) {
                db.execSQL("ALTER TABLE " + TABLE_TIPO_CUENTA + " ADD COLUMN liquidez TEXT");
            }
            completarLiquidezCatalogo(db);
        }
        // ⭐ NUEVO (31-oct): versión 18 de la BD — cuentas.Cerrable y cuentas.conciliable pasan
        // de TEXT ("Cerrable"/"Conciliable" o NULL) a INTEGER 0/1 real (pendiente 2.5 del modelo
        // original). SQLite no cambia el tipo de una columna con ALTER, así que se recrea la
        // tabla — mismo procedimiento que la migración v13 — preservando cuenta_id EXACTO. Las
        // FK están apagadas durante onUpgrade (se activan en onOpen), así que soltar y renombrar
        // "cuentas" no choca con transacciones/items_inventario, que la siguen referenciando
        // por nombre. Los CSV de backup NO cambian de formato (se sigue escribiendo y leyendo
        // "Cerrable"/"Conciliable"); la conversión se hace al leer/escribir la base.
        if (oldVersion < 18) {
            db.execSQL("CREATE TABLE cuentas_temp_v18 (" +
                    "Item TEXT NOT NULL, Cuenta TEXT NOT NULL, Fecha TEXT NOT NULL, " +
                    "cuenta_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "codigo_cuenta TEXT, " +
                    "Cerrable INTEGER NOT NULL DEFAULT 0, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id), " +
                    "cuenta_seguimiento INTEGER, " +
                    "conciliable INTEGER NOT NULL DEFAULT 0, " +
                    "con_inventario INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO cuentas_temp_v18 (" +
                    "cuenta_id, Item, Cuenta, Fecha, codigo_cuenta, Cerrable, tipo_cuenta_id, " +
                    "cuenta_seguimiento, conciliable, con_inventario) " +
                    "SELECT cuenta_id, Item, Cuenta, Fecha, codigo_cuenta, " +
                    "CASE WHEN Cerrable = 'Cerrable' OR Cerrable = 1 THEN 1 ELSE 0 END, " +
                    "tipo_cuenta_id, cuenta_seguimiento, " +
                    "CASE WHEN conciliable = 'Conciliable' OR conciliable = 1 THEN 1 ELSE 0 END, " +
                    "COALESCE(con_inventario, 0) FROM cuentas");
            db.execSQL("DROP TABLE cuentas");
            db.execSQL("ALTER TABLE cuentas_temp_v18 RENAME TO cuentas");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cuentas_tipo_cuenta_id ON cuentas(tipo_cuenta_id)");
        }
        // ⭐ NUEVO (31-oct): versión 19 de la BD — paso 4 / Fase B: se retira
        // transacciones.c13_ColumnaDisponible (pendiente 2.8 del modelo original; nunca tuvo
        // lógica). Se recrea la tabla (mismo procedimiento que la v13) preservando transaccion_id,
        // cuenta_id y tipo_cuenta_id EXACTOS, y se recrean todos sus índices.
        // SEGURIDAD: transacciones_inventario referencia a transacciones con ON DELETE CASCADE.
        // Con las FK activas, DROP TABLE haría un DELETE implícito y BORRARÍA todos los
        // movimientos de inventario. Las FK solo se activan en onOpen (después de onUpgrade),
        // pero si algún día eso cambiara, se aborta la migración (se revierte entera) en vez de
        // perder datos.
        if (oldVersion < 19) {
            Cursor fk = db.rawQuery("PRAGMA foreign_keys", null);
            boolean fkActivas = fk.moveToFirst() && fk.getInt(0) == 1;
            fk.close();
            if (fkActivas) {
                throw new IllegalStateException("Migración v19 abortada: llaves foráneas activas " +
                        "durante onUpgrade; recrear 'transacciones' borraría el inventario.");
            }
            int filasAntes = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            db.execSQL("CREATE TABLE transacciones_temp_v19(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, c12_ColumnaDisponible TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v19 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c12_ColumnaDisponible, cuenta_id, tipo_cuenta_id) " +
                    "SELECT transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "c12_ColumnaDisponible, cuenta_id, tipo_cuenta_id FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v19 RENAME TO transacciones");
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");
            int filasDespues = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            if (filasAntes != filasDespues) {
                throw new IllegalStateException("Migración v19 abortada: transacciones antes=" +
                        filasAntes + " después=" + filasDespues);
            }
            android.util.Log.w("A1_1_AyudanteBD", "Migración v19: c13 retirada; transacciones=" + filasDespues);
        }
        // ⭐ NUEVO (31-oct): versión 20 de la BD — paso 4 / Fase C: se retira
        // transacciones.c12_ColumnaDisponible (foto de "Cerrable" por transacción). Decisión de
        // Jorge: los cierres usan la marca ACTUAL de la cuenta. Mismo procedimiento y mismas
        // protecciones que la v19.
        if (oldVersion < 20) {
            Cursor fk = db.rawQuery("PRAGMA foreign_keys", null);
            boolean fkActivas = fk.moveToFirst() && fk.getInt(0) == 1;
            fk.close();
            if (fkActivas) {
                throw new IllegalStateException("Migración v20 abortada: llaves foráneas activas " +
                        "durante onUpgrade; recrear 'transacciones' borraría el inventario.");
            }
            int filasAntes = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            db.execSQL("CREATE TABLE transacciones_temp_v20(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, c4_Signo TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v20 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id) " +
                    "SELECT transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v20 RENAME TO transacciones");
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");
            int filasDespues = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            if (filasAntes != filasDespues) {
                throw new IllegalStateException("Migración v20 abortada: transacciones antes=" +
                        filasAntes + " después=" + filasDespues);
            }
            android.util.Log.w("A1_1_AyudanteBD", "Migración v20: c12 retirada; transacciones=" + filasDespues);
        }
        // ⭐ NUEVO (1-nov): versión 21 de la BD — paso 4 / Fase D: se retira
        // transacciones.c4_Signo. El signo coincidía con el valor en 7.770 de 7.772 transacciones
        // (backup del 31-oct; las 2 restantes: un valor 0 y CxP Admon doc 0 ítem 25, que Jorge
        // confirmó como +51). Nunca hubo signo '?'. Las consultas lo calculan del valor. Mismo
        // procedimiento y protecciones que la v19.
        if (oldVersion < 21) {
            Cursor fk = db.rawQuery("PRAGMA foreign_keys", null);
            boolean fkActivas = fk.moveToFirst() && fk.getInt(0) == 1;
            fk.close();
            if (fkActivas) {
                throw new IllegalStateException("Migración v21 abortada: llaves foráneas activas " +
                        "durante onUpgrade; recrear 'transacciones' borraría el inventario.");
            }
            int filasAntes = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            db.execSQL("CREATE TABLE transacciones_temp_v21(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c3_Cuenta TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v21 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id) " +
                    "SELECT transaccion_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v21 RENAME TO transacciones");
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");
            int filasDespues = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            if (filasAntes != filasDespues) {
                throw new IllegalStateException("Migración v21 abortada: transacciones antes=" +
                        filasAntes + " después=" + filasDespues);
            }
            android.util.Log.w("A1_1_AyudanteBD", "Migración v21: c4 retirada; transacciones=" + filasDespues);
        }
        // ⭐ NUEVO (1-nov): versión 22 de la BD — paso 4 / Fase E2 (propuesta de Jorge): se retira
        // transacciones.c3_Cuenta. En la Fase E1 la app ya trabajaba como si no existiera
        // (todo por cuenta_id) y Jorge lo confirmó en el dispositivo. Mismo procedimiento y
        // protecciones que la v19. Antes de soltar la columna: (1) último reemparejado por
        // nombre de las transacciones que sigan sin cuenta_id, y (2) si aun así quedara alguna,
        // su nombre se conserva en la tabla auxiliar transacciones_nombre_sin_cuenta, para no
        // perder esa información (con los datos del 31-oct/1-nov: ninguna).
        if (oldVersion < 22) {
            Cursor fk = db.rawQuery("PRAGMA foreign_keys", null);
            boolean fkActivas = fk.moveToFirst() && fk.getInt(0) == 1;
            fk.close();
            if (fkActivas) {
                throw new IllegalStateException("Migración v22 abortada: llaves foráneas activas " +
                        "durante onUpgrade; recrear 'transacciones' borraría el inventario.");
            }
            db.execSQL("UPDATE transacciones SET cuenta_id = " +
                    "(SELECT cuenta_id FROM cuentas WHERE cuentas.Cuenta = transacciones.c3_Cuenta) " +
                    "WHERE cuenta_id IS NULL");
            db.execSQL("CREATE TABLE IF NOT EXISTS transacciones_nombre_sin_cuenta (" +
                    "transaccion_id INTEGER PRIMARY KEY, nombre_cuenta TEXT)");
            db.execSQL("INSERT OR REPLACE INTO transacciones_nombre_sin_cuenta " +
                    "SELECT transaccion_id, c3_Cuenta FROM transacciones WHERE cuenta_id IS NULL");
            int filasAntes = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            db.execSQL("CREATE TABLE transacciones_temp_v22(" +
                    "c1_Documento TEXT NOT NULL, c2_ItemDoc TEXT NOT NULL, " +
                    "c5_Valor INTEGER, c6_Descripcion TEXT NOT NULL, " +
                    "c7_FechaYHora TEXT NOT NULL, c8_FechaInicial INTEGER, " +
                    "c9_FechaModificacion TEXT NOT NULL, " +
                    "cuenta_id INTEGER REFERENCES cuentas(cuenta_id), " +
                    "transaccion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo_cuenta_id INTEGER REFERENCES tipo_cuenta(tipo_cuenta_id))");
            db.execSQL("INSERT INTO transacciones_temp_v22 (" +
                    "transaccion_id, c1_Documento, c2_ItemDoc, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id) " +
                    "SELECT transaccion_id, c1_Documento, c2_ItemDoc, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion, " +
                    "cuenta_id, tipo_cuenta_id FROM transacciones");
            db.execSQL("DROP TABLE transacciones");
            db.execSQL("ALTER TABLE transacciones_temp_v22 RENAME TO transacciones");
            crearIndiceUnicoDocumentoItem(db);
            crearIndicesTransacciones(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transacciones_tipo_cuenta_id " +
                    "ON transacciones(tipo_cuenta_id)");
            int filasDespues = (int) android.database.DatabaseUtils.queryNumEntries(db, "transacciones");
            if (filasAntes != filasDespues) {
                throw new IllegalStateException("Migración v22 abortada: transacciones antes=" +
                        filasAntes + " después=" + filasDespues);
            }
            android.util.Log.w("A1_1_AyudanteBD", "Migración v22: c3 retirada; transacciones=" + filasDespues);
        }

        // ⭐ NUEVO (1-nov): versión 23 de la BD — cierre del paso 4 (con Jorge): "cache_registros"
        // (borradores de F1) queda con la definición nueva de COLUMNAS_CACHE_RECORDS. Se recrea
        // copiando las columnas que siguen (un borrador pendiente no se pierde; los campos nuevos
        // quedan vacíos para ese borrador). Tabla sin llaves foráneas: no afecta otras tablas.
        if (oldVersion < 23) {
            int filasAntes = (int) android.database.DatabaseUtils.queryNumEntries(db, TABLE_CACHE_RECORDS);
            db.execSQL("DROP TABLE IF EXISTS cache_registros_temp_v23");
            db.execSQL("CREATE TABLE cache_registros_temp_v23 (" + COLUMNAS_CACHE_RECORDS + ")");
            db.execSQL("INSERT INTO cache_registros_temp_v23 (" +
                    "registro_id, area_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaMod) " +
                    "SELECT registro_id, area_id, c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                    "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaMod FROM " + TABLE_CACHE_RECORDS);
            db.execSQL("DROP TABLE " + TABLE_CACHE_RECORDS);
            db.execSQL("ALTER TABLE cache_registros_temp_v23 RENAME TO " + TABLE_CACHE_RECORDS);
            int filasDespues = (int) android.database.DatabaseUtils.queryNumEntries(db, TABLE_CACHE_RECORDS);
            if (filasAntes != filasDespues) {
                throw new IllegalStateException("Migración v23 abortada: cache_registros antes=" +
                        filasAntes + " después=" + filasDespues);
            }
            android.util.Log.w("A1_1_AyudanteBD", "Migración v23: borradores completos; filas=" + filasDespues);
        }

        // ⭐ NUEVO (3-nov): versión 24 de la BD — modelo de renta fiscal, fase R1 (diseño
        // acordado con Jorge). Solo agrega: tablas configuracion_app y renglon_renta (con la
        // plantilla de Colombia), soporte sugerido y 2 tipos correctores reservados en
        // tipo_cuenta, y 3 columnas al final de "cuentas", que quedan llenas por defecto.
        if (oldVersion < 24) {
            if (!tieneColumna(db, TABLE_TIPO_CUENTA, "soporte_sugerido")) {
                db.execSQL("ALTER TABLE " + TABLE_TIPO_CUENTA + " ADD COLUMN soporte_sugerido TEXT");
            }
            completarSoporteSugeridoCatalogo(db);
            insertarCatalogoDefinitivo(db); // solo entran los que faltan (22 y 23)
            crearTablasRenta(db);
            importarPlantillaRenta(db, PLANTILLA_RENTA_COLOMBIA);
            if (!tieneColumna(db, "cuentas", "renglon_renta_id")) {
                db.execSQL("ALTER TABLE cuentas ADD COLUMN renglon_renta_id INTEGER " +
                        "REFERENCES " + TABLE_RENGLON_RENTA + "(renglon_id)");
            }
            if (!tieneColumna(db, "cuentas", "entidad")) {
                db.execSQL("ALTER TABLE cuentas ADD COLUMN entidad TEXT");
            }
            if (!tieneColumna(db, "cuentas", "tipo_soporte")) {
                db.execSQL("ALTER TABLE cuentas ADD COLUMN tipo_soporte TEXT");
            }
            for (String nombre : CUENTAS_SIN_SOPORTE) {
                db.execSQL("UPDATE cuentas SET tipo_soporte = 'Ninguno' " +
                        "WHERE Cuenta = ? AND tipo_soporte IS NULL", new Object[]{nombre});
            }
            int asignadas = asignarRenglonYSoportePendientes(db);
            android.util.Log.w("A1_1_AyudanteBD", "Migración v24: renta R1; cuentas con renglón=" + asignadas);
        }

        // ⭐ NUEVO (3-nov): versión 25 de la BD — renta fase R3: tabla foto_renta_anual (vacía).
        if (oldVersion < 25) {
            db.execSQL(SQL_CREAR_FOTO_RENTA_ANUAL);
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
