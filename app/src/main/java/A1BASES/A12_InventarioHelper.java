package A1BASES;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * A12_InventarioHelper — lógica de costeo del "Modelo de Costeo por Inventario en Cuentas".
 *
 * ⭐ NUEVO v12 tanda 2. Esta clase es SOLO lógica de cálculo y guardado — no la usa ninguna
 * pantalla todavía. A propósito: si la UX para crear una cuenta con inventario (tanda 3)
 * saliera antes que la UX para registrar transacciones sobre ella (tanda 4), el usuario
 * podría crear una cuenta con con_inventario = 1 y luego registrar una transacción sobre
 * esa cuenta desde el formulario VIEJO (que no sabe nada de items_inventario), dejando una
 * fila en "transacciones" sin su fila correspondiente en "transacciones_inventario" — un
 * estado inconsistente que después ninguna tanda podría reparar sola. Por eso esta tanda
 * deja lista y verificada toda la lógica de costeo por promedio ponderado, pero inerte: se
 * conecta a la UX real hasta la tanda 4, cuando el formulario de registro ya sepa pedir
 * item/unidades/precio y guardar con este helper de forma atómica.
 *
 * Responsabilidades (reglas de negocio del documento de especificación, sección 3):
 * - obtenerSaldoUnidades: SUM(unidades) de un artículo (regla de negocio #3).
 * - calcularCostoPromedioPonderado: costo promedio ponderado vigente de un artículo,
 *   recalculado sobre el total acumulado (regla de negocio #4).
 * - guardarTransaccionConInventario: inserta la transacción y su detalle de inventario de
 *   forma atómica (reglas de negocio #1 y #2). El monto (c5_Valor) lo trae SIEMPRE el
 *   llamador, exactamente como lo escribió el usuario — este método nunca lo recalcula ni lo
 *   sobrescribe, porque es lo que hace cuadrar el documento en cero. costo_total (⭐ v15) es
 *   el costo EXACTO en COP que entra o sale del inventario: para una entrada, es el valor ya
 *   escrito; para una salida, se calcula sobre el saldo exacto acumulado (nunca lo recibe del
 *   llamador) — tal como Jorge confirmó tras la retroalimentación de la tanda 4 (tensión
 *   resuelta distinguiendo "valor recibido/pagado", que manda para el balance, de "costo de
 *   inventario", que es el que preserva el promedio ponderado). precio_unitario pasa a ser
 *   PURAMENTE informativo, derivado de costo_total — con hasta 3 decimales.
 *
 * ⭐ NUEVO v12 tanda 3: insertarItemInventario y existenItemsPorCuenta — CRUD mínimo de
 * items_inventario, usado por F2_Cuentas al crear una cuenta con inventario (sección 4 del
 * documento: "al guardar la cuenta la app debe llevarlo a dar de alta al menos un
 * artículo").
 *
 * ⭐ NUEVO v12 tanda 4: listarItemsActivosPorCuenta — alimenta el selector de artículo del
 * registro de transacciones. guardarTransaccionConInventario ya se usa desde ahí
 * (B12_DocumentPersistence.baseParaGuardarEnLaEnBDConListaDocumento), para los ítems nuevos
 * de una cuenta con inventario agregados a través del diálogo nuevo — ver esa clase para el
 * detalle completo del flujo y de qué queda todavía bloqueado (editar un documento que ya
 * tenía ítems de inventario guardados). ⭐ REDISEÑO v12 tanda 4 (fix, 29-sep): el método pasó
 * de recibir un precio de entrada opcional y sobrescribir c5_Valor, a nunca tocar c5_Valor y
 * derivar precio_unitario internamente — ver el javadoc del método para el detalle completo.
 *
 * ⭐ REDISEÑO v15 (30-sep, retroalimentación de Jorge tras probar la tanda 4): precio_unitario
 * redondeado a un peso entero generaba diferencias representativas para artículos de precio
 * bajo (como una divisa), y una salida que agotaba el saldo de un artículo podía dejar un
 * pequeño residuo de costo (sin unidades) que contaminaba el promedio de una entrada futura.
 * Se agrega transacciones_inventario.costo_total (el costo exacto de cada movimiento) como
 * fuente real del costo promedio ponderado; precio_unitario pasa a ser puramente informativo,
 * con hasta 3 decimales — ver el javadoc de calcularCostoPromedioPonderado y
 * guardarTransaccionConInventario para el detalle completo.
 *
 * ⭐ REDISEÑO v16 (1-oct, rediseño del diálogo de registro a pedido de Jorge): se agregan
 * obtenerSaldoUnidadesYCosto (saldo público, para que el diálogo muestre "Saldo anterior") y
 * calcularCostoSalida (la fórmula de costo de una salida, extraída de
 * guardarTransaccionConInventario a su propio método público, para que el diálogo pueda
 * mostrar una vista previa — "Nuevo saldo" — que coincide exactamente con lo que se guarda
 * después). calcularCostoSalida agrega también la validación de sobregiro que faltaba: una
 * salida por más unidades de las que el artículo tiene en existencia ahora se rechaza (antes
 * no se validaba en ningún lado — ver el javadoc de ese método para el detalle completo).
 */
public class A12_InventarioHelper {

    // Nombre de la columna de monto en "transacciones" (ver A1_1_AyudanteBD.crearTransacciones_String).
    private static final String COLUMNA_MONTO_TRANSACCION = "c5_Valor";

    /**
     * Saldo en unidades de un artículo (regla de negocio #3): SUM(unidades) sobre todas sus
     * filas en transacciones_inventario. 0 si el artículo no tiene movimientos todavía.
     */
    public long obtenerSaldoUnidades(SQLiteDatabase db, long itemId) {
        long saldo = 0;
        Cursor c = db.rawQuery(
                "SELECT SUM(unidades) FROM transacciones_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)});
        try {
            if (c.moveToFirst() && !c.isNull(0)) {
                saldo = c.getLong(0);
            }
        } finally {
            c.close();
        }
        return saldo;
    }

    /**
     * ⭐ NUEVO v15: saldo acumulado de un artículo — unidades y costo_total — sobre TODAS sus
     * filas (entradas y salidas) en transacciones_inventario. Reemplaza el cálculo anterior
     * (SUM(unidades × precio_unitario)), que dependía de precio_unitario redondeado a un peso
     * entero y podía dejar un pequeño residuo de costo al agotar el saldo de un artículo — ver
     * el comentario de clase v15 en A1_1_AyudanteBD para el detalle completo del problema que
     * esto resuelve.
     */
    private static final class SaldoInventario {
        final long unidadesTotal;
        final long costoTotal;

        SaldoInventario(long unidadesTotal, long costoTotal) {
            this.unidadesTotal = unidadesTotal;
            this.costoTotal = costoTotal;
        }
    }

    private SaldoInventario obtenerSaldoInventario(SQLiteDatabase db, long itemId) {
        long unidadesTotal = 0;
        long costoTotal = 0;
        Cursor c = db.rawQuery(
                "SELECT SUM(unidades), SUM(costo_total) FROM transacciones_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)});
        try {
            if (c.moveToFirst() && !c.isNull(0)) {
                unidadesTotal = c.getLong(0);
                costoTotal = c.getLong(1);
            }
        } finally {
            c.close();
        }
        return new SaldoInventario(unidadesTotal, costoTotal);
    }

    /**
     * ⭐ NUEVO v16 (1-oct, rediseño del diálogo de registro a pedido de Jorge): mismo saldo que
     * obtenerSaldoInventario (unidades y costo_total acumulados de un artículo), pero público y
     * en forma de arreglo {unidades, costoTotal} — para que B12_DocumentPersistence pueda
     * mostrar el "Saldo anterior" del artículo en el diálogo de registro, sin tener que hacer
     * pública la clase interna SaldoInventario ni duplicar la consulta SQL.
     */
    public long[] obtenerSaldoUnidadesYCosto(SQLiteDatabase db, long itemId) {
        SaldoInventario saldo = obtenerSaldoInventario(db, itemId);
        return new long[]{saldo.unidadesTotal, saldo.costoTotal};
    }

    /**
     * ⭐ NUEVO (1-oct, pedido de Jorge): ¿es "transaccionId" el movimiento MÁS RECIENTE (mayor
     * transaccion_id, que por ser AUTOINCREMENT equivale a orden de inserción) de este
     * artículo? Se usa para decidir si un movimiento de inventario YA GUARDADO se puede editar
     * o eliminar: obtenerSaldoInventario/calcularCostoSalida calculan el saldo con un SUM
     * simple sobre TODAS las filas del artículo (no hay una cadena de saldos "congelados" por
     * fila) — pero costo_total de cada fila SÍ quedó fijado para siempre con el saldo que había
     * al momento de insertarla. Por eso, cambiar o borrar una fila que NO es la última dejaría
     * el costo_total de las filas posteriores (que ya se calcularon con el saldo de ANTES del
     * cambio) desactualizado, sin ninguna forma automática de corregirlas. Si SÍ es la última,
     * no hay ninguna fila posterior que dependa de ella — editarla o borrarla (y volver a
     * insertarla, que es como de hecho se guarda una edición: ver
     * B12_DocumentPersistence.baseParaGuardarEnLaEnBDConListaDocumento) es seguro.
     */
    public boolean esUltimoMovimiento(SQLiteDatabase db, long itemId, long transaccionId) {
        Cursor c = db.rawQuery(
                "SELECT MAX(transaccion_id) FROM transacciones_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)});
        try {
            return c.moveToFirst() && !c.isNull(0) && c.getLong(0) == transaccionId;
        } finally {
            c.close();
        }
    }

    /**
     * Costo promedio ponderado vigente de un artículo (regla de negocio #4), PURAMENTE
     * INFORMATIVO desde la v15 — con hasta 3 decimales (antes redondeaba al peso entero, lo
     * cual generaba diferencias representativas para artículos de precio bajo, como una
     * divisa — reportado por Jorge). Ya NO es lo que se guarda como costo real del
     * movimiento (ver guardarTransaccionConInventario/costo_total) — este método es solo para
     * mostrarle al usuario el costo promedio vigente antes de confirmar una salida.
     *
     * SUM(costo_total) / SUM(unidades), sobre TODAS las filas (entradas y salidas) del
     * artículo — costo_total ya viene exacto en cada fila (ver esa columna), así que este
     * promedio no arrastra ningún redondeo de filas anteriores.
     *
     * @throws IllegalStateException si el artículo no tiene saldo en unidades (nada que
     *         vender, o saldo en 0 o negativo) — no hay costo promedio que calcular.
     */
    public double calcularCostoPromedioPonderado(SQLiteDatabase db, long itemId) {
        SaldoInventario saldo = obtenerSaldoInventario(db, itemId);

        if (saldo.unidadesTotal <= 0) {
            throw new IllegalStateException(
                    "El artículo " + itemId + " no tiene saldo en unidades (" + saldo.unidadesTotal +
                            ") — no se puede calcular un costo promedio ni registrar una salida.");
        }

        double costoPromedio = redondearA3Decimales(
                (double) saldo.costoTotal / (double) saldo.unidadesTotal);
        if (costoPromedio <= 0) {
            // Defensivo: la tabla exige precio_unitario > 0 (CHECK). En la práctica, con
            // valores en COP, esto no debería pasar — pero si pasara, es mejor fallar aquí
            // con un mensaje claro que dejar que SQLite rechace el INSERT más abajo.
            throw new IllegalStateException(
                    "El costo promedio calculado para el artículo " + itemId +
                            " no es válido (" + costoPromedio + ").");
        }
        return costoPromedio;
    }

    /**
     * ⭐ NUEVO v15: redondeo a 3 decimales, usado en todo este archivo para el precio unitario
     * informativo (nunca para costo_total, que siempre es un entero exacto en COP).
     */
    private static double redondearA3Decimales(double valor) {
        return Math.round(valor * 1000.0) / 1000.0;
    }

    /**
     * ⭐ NUEVO v16 (1-oct, rediseño del diálogo de registro a pedido de Jorge): calcula, SIN
     * GUARDAR NADA, el costo_total (positivo, la MAGNITUD) que tendría una salida de
     * `unidadesVendidas` unidades de `itemId`, dado el saldo acumulado ANTES de ese movimiento
     * — exactamente la misma fórmula (incluido el cierre exacto sin residual cuando la salida
     * agota el saldo) que guardarTransaccionConInventario usa al guardar de verdad, extraída
     * aquí como método único para que el diálogo de registro pueda mostrarle al usuario una
     * vista previa ("Nuevo saldo") que después, al guardar, coincide exactamente — nunca dos
     * copias de la misma fórmula que puedan desincronizarse con el tiempo.
     *
     * ⭐ NUEVO v16: agrega también la validación de "sobregiro" que faltaba — una salida por más
     * unidades de las que el artículo tiene en existencia ahora se rechaza aquí (antes no se
     * validaba en ningún lado: una salida mayor al saldo dejaba el artículo con saldo NEGATIVO
     * en unidades y en costo, sin aviso). Como guardarTransaccionConInventario pasa a llamar a
     * este mismo método (ver más abajo), la validación aplica tanto a la vista previa del
     * diálogo como al guardado real — una sola fuente de verdad.
     *
     * @return el costo_total de la salida, en magnitud POSITIVA — el llamador decide el signo.
     * @throws IllegalStateException si el artículo no tiene saldo en unidades, o si
     *         unidadesVendidas es mayor al saldo disponible.
     */
    public long calcularCostoSalida(SQLiteDatabase db, long itemId, long unidadesVendidas) {
        if (unidadesVendidas <= 0) {
            throw new IllegalArgumentException("Las unidades a vender deben ser mayores que 0.");
        }
        SaldoInventario saldoAntes = obtenerSaldoInventario(db, itemId);
        if (saldoAntes.unidadesTotal <= 0) {
            throw new IllegalStateException(
                    "El artículo " + itemId + " no tiene saldo en unidades (" +
                            saldoAntes.unidadesTotal + ") — no se puede registrar una salida.");
        }
        if (unidadesVendidas > saldoAntes.unidadesTotal) {
            throw new IllegalStateException(
                    "No hay saldo suficiente del artículo " + itemId + ": disponible " +
                            saldoAntes.unidadesTotal + ", solicitado " + unidadesVendidas + ".");
        }

        boolean agotaElSaldo = unidadesVendidas == saldoAntes.unidadesTotal;
        long costoTotal;
        if (agotaElSaldo) {
            // Sale por el costo EXACTO que quedaba acumulado, sin redondear — garantiza saldo
            // en costo = 0 al mismo tiempo que saldo en unidades = 0 (sin residuales).
            costoTotal = saldoAntes.costoTotal;
        } else {
            costoTotal = Math.round(
                    (double) saldoAntes.costoTotal * unidadesVendidas / (double) saldoAntes.unidadesTotal);
        }
        if (costoTotal <= 0) {
            throw new IllegalStateException(
                    "El costo calculado para la salida del artículo " + itemId +
                            " no es válido (" + costoTotal + ").");
        }
        return costoTotal;
    }

    /**
     * Inserta, de forma atómica, una transacción sobre una cuenta con inventario y su
     * detalle correspondiente en transacciones_inventario (regla de negocio #1: si
     * cualquiera de los dos INSERT falla, se revierten ambos).
     *
     * ⭐ REDISEÑO v12 tanda 4 (fix, tras retroalimentación de Jorge): c5_Valor SIEMPRE es
     * exactamente lo que el usuario ya escribió en el campo "valor" del formulario — este
     * método NUNCA lo recalcula ni lo sobrescribe (antes sí lo hacía, con
     * unidades × precio_unitario, lo cual reemplazaba en silencio el valor ya digitado y
     * rompía el orden natural del formulario). La razón es que c5_Valor es lo que hace
     * cuadrar el documento en cero (partida doble) — es el llamador quien debe traerlo ya
     * puesto en valoresTransaccion.
     *
     * ⭐ REDISEÑO v15 (tras retroalimentación de Jorge sobre precisión y saldos residuales):
     * costo_total es ahora la fuente real del costo del movimiento — el costo EXACTO en COP
     * que este movimiento agrega o quita del inventario, con el mismo signo que unidades:
     * - Entrada (unidades > 0): costo_total = |c5_Valor| (el dinero pagado y el costo agregado
     *   al inventario son, económicamente, la misma cifra — no hay conflicto en derivar uno
     *   del otro).
     * - Salida (unidades < 0): costo_total NUNCA se deriva de c5_Valor (el valor que el
     *   usuario recibió por la venta puede diferir del costo que sale del inventario — esa
     *   diferencia es la utilidad o pérdida de la venta, que esta tanda todavía no registra
     *   aparte). Se calcula sobre el saldo EXACTO acumulado del artículo (unidades y
     *   costo_total, ver obtenerSaldoInventario), no sobre precio_unitario redondeado:
     *     · Si esta salida agota EXACTAMENTE el saldo en unidades del artículo (lo deja en
     *       0), costo_total sale por el costo_total EXACTO que quedaba acumulado, sin
     *       redondear — así el saldo en costo también queda en EXACTAMENTE 0 al mismo tiempo
     *       que el saldo en unidades, sin dejar ningún residuo que después contamine el
     *       promedio de una entrada futura del mismo artículo (esto es lo que Jorge pidió:
     *       "cuando se ha ido retirando las unidades y su saldo es cero debe salir por el
     *       valor del saldo").
     *     · Si es una salida parcial, costo_total sale proporcional al costo promedio vigente
     *       (costoTotalAntes × unidadesVendidas ÷ unidadesTotalAntes, redondeado UNA sola vez
     *       aquí — nunca redondeando primero un precio por unidad y multiplicando después, para
     *       no arrastrar redondeos de una transacción a la siguiente).
     *
     * precio_unitario sigue existiendo, pero pasa a ser PURAMENTE informativo/derivado — con
     * hasta 3 decimales (antes redondeaba al peso entero, generando diferencias representativas
     * para artículos de precio bajo, como una divisa) — nunca decide nada del guardado ni del
     * promedio ponderado: se calcula DESPUÉS de costo_total, solo para que quede un dato legible
     * junto a cada movimiento.
     *
     * @param db                    base de datos escribible (ya abierta por el llamador).
     * @param valoresTransaccion    columnas de "transacciones" ya armadas por el llamador
     *                              (documento, ítem, cuenta, signo, descripción, fechas,
     *                              cuenta_id, tipo_cuenta_id, etc.) — INCLUYENDO c5_Valor, ya
     *                              puesto por el llamador exactamente como lo escribió el
     *                              usuario. Este método NO lo toca.
     * @param itemId                artículo (items_inventario.item_id) al que pertenece el
     *                              movimiento. Debe existir y pertenecer a una cuenta con
     *                              con_inventario = 1.
     * @param unidades              unidades del movimiento, con signo: positivo = entrada,
     *                              negativo = salida. No puede ser 0.
     * @return el transaccion_id recién creado.
     * @throws IllegalArgumentException si el artículo no existe, no pertenece a una cuenta
     *         con inventario, valoresTransaccion no trae c5_Valor, o los parámetros no son
     *         válidos (por ejemplo, una entrada cuyo valor/unidades redondea a 0 o menos).
     */
    public long guardarTransaccionConInventario(SQLiteDatabase db,
                                                  ContentValues valoresTransaccion,
                                                  long itemId,
                                                  long unidades) {
        if (unidades == 0) {
            throw new IllegalArgumentException("Las unidades no pueden ser 0.");
        }
        if (!valoresTransaccion.containsKey(COLUMNA_MONTO_TRANSACCION)) {
            throw new IllegalArgumentException(
                    "valoresTransaccion debe traer " + COLUMNA_MONTO_TRANSACCION +
                            " ya puesto por el llamador (el valor tal cual lo escribió el usuario).");
        }

        // El artículo debe existir y pertenecer a una cuenta con con_inventario = 1 — evita
        // dejar un detalle de inventario "huérfano" de una cuenta que no debería tenerlo.
        Long cuentaIdDelItem = null;
        Cursor cItem = db.rawQuery(
                "SELECT i.cuenta_id, c.con_inventario " +
                        "FROM items_inventario i " +
                        "JOIN cuentas c ON c.cuenta_id = i.cuenta_id " +
                        "WHERE i.item_id = ?",
                new String[]{String.valueOf(itemId)});
        try {
            if (cItem.moveToFirst()) {
                cuentaIdDelItem = cItem.getLong(0);
                boolean conInventario = cItem.getLong(1) != 0;
                if (!conInventario) {
                    throw new IllegalArgumentException(
                            "El artículo " + itemId + " pertenece a la cuenta " + cuentaIdDelItem +
                                    ", que no tiene con_inventario = 1.");
                }
            } else {
                throw new IllegalArgumentException("No existe el artículo " + itemId + ".");
            }
        } finally {
            cItem.close();
        }

        long costoTotal;
        if (unidades > 0) {
            // Entrada: costo_total es exactamente el valor ya escrito por el usuario — nunca
            // se pide aparte, para que no pueda quedar en una escala distinta a "valor".
            long valorTransaccion = valoresTransaccion.getAsLong(COLUMNA_MONTO_TRANSACCION);
            costoTotal = Math.abs(valorTransaccion);
            if (costoTotal <= 0) {
                throw new IllegalArgumentException(
                        "El valor de la entrada no puede ser 0 — no hay costo que agregar al inventario.");
            }
        } else {
            // Salida: el costo NUNCA se deriva de c5_Valor — se calcula sobre el saldo exacto
            // acumulado del artículo (nunca sobre precio_unitario redondeado). ⭐ v16: la
            // fórmula (incluido el cierre exacto sin residual y la validación de sobregiro) ya
            // no vive aquí duplicada — se extrajo a calcularCostoSalida(), que también usa la
            // vista previa del diálogo de registro, para que ambas coincidan siempre.
            long unidadesVendidas = -unidades;
            costoTotal = -calcularCostoSalida(db, itemId, unidadesVendidas);
        }

        // ⭐ (26-nov, decimales): unidades viene en la escala interna del artículo.
        double precioUnitarioInformativo = precioPorUnidad(costoTotal, unidades, decimalesDe(db, itemId));
        if (precioUnitarioInformativo <= 0) {
            throw new IllegalArgumentException(
                    "El precio unitario informativo (costo/unidades) no es válido (" +
                            precioUnitarioInformativo + ") — revisa el valor y las unidades digitadas.");
        }

        // insertOrThrow (a diferencia de insert) nunca devuelve -1: si algo falla, lanza
        // SQLException — con la transacción abierta y sin setTransactionSuccessful(), el
        // finally de abajo revierte automáticamente CUALQUIERA de los dos INSERT ya hecho
        // (regla de negocio #1: atómico, con rollback si cualquiera falla).
        db.beginTransaction();
        try {
            long transaccionId = db.insertOrThrow("transacciones", null, valoresTransaccion);

            ContentValues valoresInventario = new ContentValues();
            valoresInventario.put("transaccion_id", transaccionId);
            valoresInventario.put("item_id", itemId);
            valoresInventario.put("unidades", unidades);
            valoresInventario.put("precio_unitario", precioUnitarioInformativo);
            valoresInventario.put("costo_total", costoTotal);
            db.insertOrThrow("transacciones_inventario", null, valoresInventario);

            db.setTransactionSuccessful();
            return transaccionId;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Da de alta un artículo nuevo en items_inventario para una cuenta con inventario (tanda
     * 3: alta del primer artículo al crear la cuenta; también sirve para la tanda 4, cuando
     * el selector de artículo del registro de transacciones permita "crear uno nuevo en el
     * momento", tal como pide el documento de especificación).
     *
     * @param db        base de datos escribible.
     * @param cuentaId  cuenta dueña del artículo — se asume ya con con_inventario = 1; este
     *                  método no lo valida (a diferencia de guardarTransaccionConInventario,
     *                  que sí valida el artículo contra su cuenta antes de guardar una
     *                  transacción) porque aquí el llamador acaba de crear o ya conoce esa
     *                  cuenta.
     * @param nombre    nombre del artículo — obligatorio, no puede quedar vacío.
     * @param unidad    unidad del artículo (ej. "acciones", "USD") — opcional, puede ser
     *                  null o vacío.
     * @return el item_id recién creado.
     * @throws IllegalArgumentException si nombre viene vacío o null.
     * @throws android.database.sqlite.SQLiteConstraintException si ya existe un artículo con
     *         ese mismo nombre en esa cuenta (UNIQUE cuenta_id + nombre).
     */
    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (25-nov, Jorge): REPASO DE INVENTARIO HACIA ATRÁS
    //  Para cuentas a las que se les activó el inventario después de tener movimientos: se le
    //  AGREGA el dato de inventario (artículo, unidades, costo) a una línea ya guardada, sin
    //  tocar la transacción (valor, descripción, documento). El costo se calcula igual que en
    //  guardarTransaccionConInventario: entrada = |valor| de la línea; salida = costo promedio
    //  del saldo acumulado. Por eso el repaso va del más antiguo al más reciente.
    // ─────────────────────────────────────────────

    /** Fecha AAAAMMDD de una línea: Fecha inicial, o la de "Fecha y hora" si está en 0. */
    public static final String SQL_FECHA_LINEA =
            "(CASE WHEN IFNULL(t.c8_FechaInicial, 0) BETWEEN 19000101 AND 21001231 THEN t.c8_FechaInicial"
            + " ELSE IFNULL(CAST(REPLACE(REPLACE(SUBSTR(t.c7_FechaYHora, 1, 10), '.', ''), '-', '') AS INTEGER), 0) END)";

    /** Línea pendiente de inventario (sin fila en transacciones_inventario). */
    public static class LineaPendiente {
        public long transaccionId;
        public String documento;
        public long valor;
        public String descripcion;
        public int fecha;
    }

    /** Pendientes de una cuenta, de la más antigua a la más reciente (incluye el doc 0). */
    public List<LineaPendiente> listarPendientes(SQLiteDatabase db, long cuentaId) {
        List<LineaPendiente> lista = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT t.transaccion_id, t.c1_Documento, IFNULL(t.c5_Valor, 0), t.c6_Descripcion, "
                + SQL_FECHA_LINEA + " AS f FROM transacciones t WHERE t.cuenta_id = ?"
                + " AND NOT EXISTS (SELECT 1 FROM transacciones_inventario ti WHERE ti.transaccion_id = t.transaccion_id)"
                // ⭐ (26-nov): el ajuste de costo (solo valor, sin unidades) no queda pendiente.
                + " AND t.c6_Descripcion NOT LIKE '" + AJUSTE_COSTO + "%'"
                // ⭐ (27-nov, BD v34): ni las líneas omitidas.
                + " AND NOT EXISTS (SELECT 1 FROM " + A1_1_AyudanteBD.TABLE_INVENTARIO_OMITIDAS
                + " o WHERE o.transaccion_id = t.transaccion_id)"
                + " ORDER BY f, t.transaccion_id", new String[]{String.valueOf(cuentaId)})) {
            while (c.moveToNext()) {
                LineaPendiente l = new LineaPendiente();
                l.transaccionId = c.getLong(0);
                l.documento = c.getString(1);
                l.valor = c.getLong(2);
                l.descripcion = c.getString(3);
                l.fecha = c.getInt(4);
                lista.add(l);
            }
        }
        return lista;
    }

    /**
     * null si se puede asignar el inventario a esta línea; si no, el motivo para el usuario:
     * hay una pendiente más antigua de la misma cuenta, o el artículo ya tiene movimientos con
     * fecha posterior (su costo promedio dependería de este).
     */
    public String motivoNoAsignable(SQLiteDatabase db, long cuentaId, LineaPendiente linea, long itemId) {
        List<LineaPendiente> pendientes = listarPendientes(db, cuentaId);
        if (!pendientes.isEmpty() && pendientes.get(0).transaccionId != linea.transaccionId) {
            LineaPendiente p = pendientes.get(0);
            return "Primero va la línea más antigua: documento " + p.documento + " del " + p.fecha
                    + " por " + p.valor + ". El repaso va del más antiguo al más reciente, para que el"
                    + " costo promedio quede bien.";
        }
        try (Cursor c = db.rawQuery("SELECT MAX(" + SQL_FECHA_LINEA + ") FROM transacciones_inventario ti"
                + " JOIN transacciones t ON t.transaccion_id = ti.transaccion_id WHERE ti.item_id = ?",
                new String[]{String.valueOf(itemId)})) {
            if (c.moveToFirst() && !c.isNull(0) && c.getInt(0) > linea.fecha) {
                return "Este artículo ya tiene movimientos con fecha posterior (" + c.getInt(0) + "). Su costo"
                        + " promedio dependería de esta línea, así que no se puede agregar antes.";
            }
        }
        return null;
    }

    /**
     * Agrega el dato de inventario a una línea ya guardada. unidadesMagnitud > 0; el signo lo
     * da el valor de la línea (positivo = entrada, negativo = salida). Devuelve el costo_total.
     */
    public long asignarInventarioALineaExistente(SQLiteDatabase db, LineaPendiente linea, long itemId,
                                                  long unidadesMagnitud) {
        if (unidadesMagnitud <= 0) throw new IllegalArgumentException("Las unidades deben ser mayores que 0.");
        if (linea.valor == 0) throw new IllegalArgumentException("La línea tiene valor 0: no hay movimiento que asignar.");
        boolean salida = linea.valor < 0;
        long unidades = salida ? -unidadesMagnitud : unidadesMagnitud;
        long costoTotal = salida ? -calcularCostoSalida(db, itemId, unidadesMagnitud) : Math.abs(linea.valor);
        double precio = precioPorUnidad(costoTotal, unidadesMagnitud, decimalesDe(db, itemId));
        if (precio <= 0) throw new IllegalArgumentException("El precio unitario resultante no es válido.");
        ContentValues v = new ContentValues();
        v.put("transaccion_id", linea.transaccionId);
        v.put("item_id", itemId);
        v.put("unidades", unidades);
        v.put("precio_unitario", precio);
        v.put("costo_total", costoTotal);
        db.insertOrThrow("transacciones_inventario", null, v);
        return costoTotal;
    }

    public long insertarItemInventario(SQLiteDatabase db, long cuentaId, String nombre, String unidad) {
        return insertarItemInventario(db, cuentaId, nombre, unidad, 0);
    }

    /** ⭐ (26-nov): con decimales de las unidades (0 a 3). */
    public long insertarItemInventario(SQLiteDatabase db, long cuentaId, String nombre, String unidad, int decimales) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del artículo no puede estar vacío.");
        }
        ContentValues valores = new ContentValues();
        valores.put("cuenta_id", cuentaId);
        valores.put("nombre", nombre.trim());
        if (unidad != null && !unidad.trim().isEmpty()) {
            valores.put("unidad", unidad.trim());
        }
        if (decimales > 0) valores.put("decimales", Math.min(decimales, MAX_DECIMALES));
        return db.insertOrThrow("items_inventario", null, valores);
    }

    /**
     * true si la cuenta ya tiene al menos un artículo dado de alta (activo o no) en
     * items_inventario. Pensado para que una futura pantalla (tanda 4/5) pueda avisar si una
     * cuenta con inventario todavía no tiene ningún artículo.
     */
    public boolean existenItemsPorCuenta(SQLiteDatabase db, long cuentaId) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM items_inventario WHERE cuenta_id = ?",
                new String[]{String.valueOf(cuentaId)});
        try {
            return c.moveToFirst() && c.getLong(0) > 0;
        } finally {
            c.close();
        }
    }

    /**
     * ⭐ NUEVO — v12 tanda 4. Datos mínimos de un artículo para mostrarlo en el selector del
     * registro de transacciones: id, nombre y unidad (puede ser null/vacía).
     */
    public static class ItemInventario {
        public final long itemId;
        public final String nombre;
        public final String unidad;
        public final int decimales; // ⭐ (26-nov): 0 a 3

        public ItemInventario(long itemId, String nombre, String unidad) {
            this(itemId, nombre, unidad, 0);
        }

        public ItemInventario(long itemId, String nombre, String unidad, int decimales) {
            this.itemId = itemId;
            this.nombre = nombre;
            this.unidad = unidad;
            this.decimales = decimales;
        }
    }

    /**
     * ⭐ NUEVO — v12 tanda 4: artículos activos (items_inventario.activo = 1) de una cuenta,
     * ordenados por nombre — alimenta el selector de artículo del registro de transacciones
     * (B12_DocumentPersistence.mostrarDialogoRegistroInventario). Lista vacía si la cuenta
     * todavía no tiene ningún artículo activo dado de alta.
     */
    public List<ItemInventario> listarItemsActivosPorCuenta(SQLiteDatabase db, long cuentaId) {
        List<ItemInventario> items = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT item_id, nombre, unidad, IFNULL(decimales, 0) FROM items_inventario " +
                        "WHERE cuenta_id = ? AND activo = 1 ORDER BY nombre COLLATE NOCASE",
                new String[]{String.valueOf(cuentaId)});
        try {
            while (c.moveToNext()) {
                items.add(new ItemInventario(
                        c.getLong(0),
                        c.getString(1),
                        c.isNull(2) ? null : c.getString(2),
                        c.getInt(3)));
            }
        } finally {
            c.close();
        }
        return items;
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (26-nov, Jorge, BD v33): UNIDADES CON DECIMALES
    //  Cada artículo tiene sus decimales (items_inventario.decimales, 0 a 3; monedas en 3).
    //  Por dentro las unidades siguen siendo enteros, en la escala del artículo: con 3
    //  decimales, 3.83 USD se guarda como 3830. El costo promedio sale de razones entre
    //  unidades (no depende de la escala); solo el precio unitario y lo que se muestra o se
    //  digita pasan por estos métodos.
    // ─────────────────────────────────────────────
    public static final int MAX_DECIMALES = 3;

    public static long factor(int decimales) {
        long f = 1;
        for (int i = 0; i < decimales; i++) f *= 10;
        return f;
    }

    public static int decimalesDe(SQLiteDatabase db, long itemId) {
        try (Cursor c = db.rawQuery("SELECT IFNULL(decimales, 0) FROM items_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0; // base sin la columna (antes de la v33)
        }
    }

    /**
     * Texto digitado → unidades internas (sin signo si el texto no lo trae). Acepta punto o
     * coma decimal. Lanza IllegalArgumentException con un mensaje para el usuario si no es un
     * número o trae más decimales de los que admite el artículo.
     */
    public static long aInterno(String texto, int decimales) {
        if (texto == null || texto.trim().isEmpty()) throw new IllegalArgumentException("Escribe las unidades.");
        java.math.BigDecimal n;
        try {
            n = new java.math.BigDecimal(texto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Las unidades no son un número válido.");
        }
        n = n.stripTrailingZeros();
        if (n.scale() > decimales) {
            throw new IllegalArgumentException(decimales == 0
                    ? "Este artículo no admite decimales."
                    : "Este artículo admite hasta " + decimales + " decimales.");
        }
        return n.movePointRight(decimales).longValueExact();
    }

    /** Unidades internas → texto, con los decimales del artículo (ej. 3830 con 3 → "3.830"). */
    public static String formatear(long interno, int decimales) {
        if (decimales <= 0) return String.valueOf(interno);
        return java.math.BigDecimal.valueOf(interno, decimales).toPlainString();
    }

    /** Precio informativo por unidad REAL (no interna), con 3 decimales. */
    public static double precioPorUnidad(long costo, long unidadesInternas, int decimales) {
        if (unidadesInternas == 0) return 0;
        return redondearA3Decimales((double) Math.abs(costo) * factor(decimales) / (double) Math.abs(unidadesInternas));
    }

    public static boolean tieneMovimientos(SQLiteDatabase db, long itemId) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transacciones_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)})) {
            return c.moveToFirst() && c.getLong(0) > 0;
        }
    }

    /** Cambia los decimales de un artículo SIN movimientos (con movimientos cambiaría su escala). */
    public static void cambiarDecimales(SQLiteDatabase db, long itemId, int decimales) {
        if (tieneMovimientos(db, itemId)) {
            throw new IllegalStateException("El artículo ya tiene movimientos: sus decimales no se pueden cambiar.");
        }
        ContentValues v = new ContentValues();
        v.put("decimales", Math.max(0, Math.min(decimales, MAX_DECIMALES)));
        db.update("items_inventario", v, "item_id = ?", new String[]{String.valueOf(itemId)});
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (26-nov, Jorge, opción B): AJUSTE DE COSTO DE INVENTARIO
    //  Al inventariar lo pasado, una salida descarga del inventario su costo promedio, pero la
    //  línea contable registró lo recibido; la diferencia (ganancia o pérdida en cambio
    //  realizada) quedó sin registrar y el saldo contable de la cuenta ≠ costo del inventario.
    //  El ajuste es un documento nuevo, con fecha de hoy (período abierto): la cuenta por la
    //  diferencia y la contrapartida (p. ej. "Pat rentabilidad sana") por lo contrario. No
    //  toca los documentos viejos. Su línea en la cuenta lleva solo valor (sin unidades): se
    //  reconoce por la descripción y no queda en 📦 Pendientes.
    // ─────────────────────────────────────────────
    public static final String AJUSTE_COSTO = "⚖ Ajuste costo inventario";

    /** {saldo contable de la cuenta, costo total del inventario de la cuenta, líneas pendientes}. */
    public long[] comparacionContableInventario(SQLiteDatabase db, long cuentaId) {
        String[] id = {String.valueOf(cuentaId)};
        long contable = 0, inventario = 0;
        try (Cursor c = db.rawQuery("SELECT IFNULL(SUM(c5_Valor), 0) FROM transacciones WHERE cuenta_id = ?", id)) {
            if (c.moveToFirst()) contable = c.getLong(0);
        }
        try (Cursor c = db.rawQuery("SELECT IFNULL(SUM(ti.costo_total), 0) FROM transacciones_inventario ti"
                + " JOIN items_inventario i ON i.item_id = ti.item_id WHERE i.cuenta_id = ?", id)) {
            if (c.moveToFirst()) inventario = c.getLong(0);
        }
        return new long[]{contable, inventario, listarPendientes(db, cuentaId).size()};
    }

    /**
     * Crea el documento de ajuste (fecha de hoy). Devuelve el número de documento. Lanza
     * IllegalStateException si hay pendientes o no hay diferencia.
     */
    public String crearAjusteCosto(SQLiteDatabase db, long cuentaId, long contrapartidaId) {
        long[] k = comparacionContableInventario(db, cuentaId);
        if (k[2] > 0) throw new IllegalStateException("Primero asigna las " + k[2] + " líneas pendientes (📦).");
        long diferencia = k[0] - k[1];
        if (diferencia == 0) throw new IllegalStateException("No hay diferencia que ajustar.");
        if (contrapartidaId == cuentaId) throw new IllegalStateException("La contrapartida debe ser otra cuenta.");
        String nombre = "";
        try (Cursor c = db.rawQuery("SELECT Cuenta FROM cuentas WHERE cuenta_id = ?", new String[]{String.valueOf(cuentaId)})) {
            if (c.moveToFirst()) nombre = c.getString(0);
        }
        String descripcion = AJUSTE_COSTO + " " + nombre + ": " + (diferencia > 0 ? "pérdida" : "ganancia")
                + " en cambio realizada (contable " + k[0] + " → costo " + k[1] + ")";
        java.util.Date ya = new java.util.Date();
        String ahora = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.US).format(ya);
        int hoy = Integer.parseInt(new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(ya));
        db.beginTransaction();
        try {
            String doc = A1_1_AyudanteBD.siguienteNumeroDocumento(db);
            long[][] lineas = {{cuentaId, -diferencia}, {contrapartidaId, diferencia}};
            int item = 1;
            for (long[] l : lineas) {
                ContentValues v = new ContentValues();
                v.put("c1_Documento", doc);
                v.put("c2_ItemDoc", String.valueOf(item++));
                v.put("c5_Valor", l[1]);
                v.put("c6_Descripcion", descripcion);
                v.put("c7_FechaYHora", ahora);
                v.put("c8_FechaInicial", hoy);
                v.put("c9_FechaModificacion", "n a");
                v.put("cuenta_id", l[0]);
                try (Cursor c = db.rawQuery("SELECT tipo_cuenta_id FROM cuentas WHERE cuenta_id = ?",
                        new String[]{String.valueOf(l[0])})) {
                    if (c.moveToFirst() && !c.isNull(0)) v.put("tipo_cuenta_id", c.getLong(0));
                }
                db.insertOrThrow("transacciones", null, v);
            }
            long[] despues = comparacionContableInventario(db, cuentaId);
            if (despues[0] != despues[1]) throw new IllegalStateException("El ajuste no cuadró; no se hizo nada.");
            db.setTransactionSuccessful();
            return doc;
        } finally {
            db.endTransaction();
        }
    }

    // ─────────────────────────────────────────────
    //  ⭐ NUEVO (27-nov, Jorge, BD v34): OMITIR LÍNEAS en 📦 Pendientes
    //  Lo pasado que ya se compensó (p. ej. euros comprados y gastados) no se repasa: se marca
    //  "sin inventario" y el inventario arranca desde los registros que forman el saldo actual.
    //  No toca ningún documento.
    // ─────────────────────────────────────────────
    /** Cuántas pendientes son más antiguas que esta (en el orden de 📦). */
    public int pendientesAnteriores(SQLiteDatabase db, long cuentaId, LineaPendiente linea) {
        List<LineaPendiente> l = listarPendientes(db, cuentaId);
        for (int i = 0; i < l.size(); i++) if (l.get(i).transaccionId == linea.transaccionId) return i;
        return 0;
    }

    /** Omite la línea (soloEsta) o todas las pendientes más antiguas que ella. Devuelve cuántas. */
    public int omitir(SQLiteDatabase db, long cuentaId, LineaPendiente linea, boolean soloEsta) {
        String ahora = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        List<Long> ids = new ArrayList<>();
        if (soloEsta) {
            ids.add(linea.transaccionId);
        } else {
            for (LineaPendiente p : listarPendientes(db, cuentaId)) {
                if (p.transaccionId == linea.transaccionId) break;
                ids.add(p.transaccionId);
            }
        }
        db.beginTransaction();
        try {
            for (long id : ids) {
                ContentValues v = new ContentValues();
                v.put("transaccion_id", id);
                v.put("fecha_registro", ahora);
                db.insertWithOnConflict(A1_1_AyudanteBD.TABLE_INVENTARIO_OMITIDAS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return ids.size();
    }

    public int contarOmitidas(SQLiteDatabase db, long cuentaId) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_INVENTARIO_OMITIDAS + " o"
                + " JOIN transacciones t ON t.transaccion_id = o.transaccion_id WHERE t.cuenta_id = ?",
                new String[]{String.valueOf(cuentaId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Des-omite todas las líneas omitidas de la cuenta (vuelven a 📦 Pendientes). */
    public int restaurarOmitidas(SQLiteDatabase db, long cuentaId) {
        return db.delete(A1_1_AyudanteBD.TABLE_INVENTARIO_OMITIDAS, "transaccion_id IN (SELECT transaccion_id"
                + " FROM transacciones WHERE cuenta_id = ?)", new String[]{String.valueOf(cuentaId)});
    }
}
