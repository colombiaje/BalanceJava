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
            // acumulado del artículo (nunca sobre precio_unitario redondeado).
            long unidadesVendidas = -unidades;
            SaldoInventario saldoAntes = obtenerSaldoInventario(db, itemId);
            if (saldoAntes.unidadesTotal <= 0) {
                throw new IllegalStateException(
                        "El artículo " + itemId + " no tiene saldo en unidades (" +
                                saldoAntes.unidadesTotal + ") — no se puede registrar una salida.");
            }
            boolean agotaElSaldo = (saldoAntes.unidadesTotal + unidades) == 0;
            if (agotaElSaldo) {
                // Sale por el costo EXACTO que quedaba acumulado, sin redondear — garantiza
                // saldo en costo = 0 al mismo tiempo que saldo en unidades = 0 (sin residuales).
                costoTotal = saldoAntes.costoTotal;
            } else {
                costoTotal = Math.round(
                        (double) saldoAntes.costoTotal * unidadesVendidas / (double) saldoAntes.unidadesTotal);
            }
            costoTotal = -costoTotal; // mismo signo que unidades (negativo en una salida)
            if (costoTotal >= 0) {
                throw new IllegalStateException(
                        "El costo calculado para la salida del artículo " + itemId +
                                " no es válido (" + costoTotal + ").");
            }
        }

        double precioUnitarioInformativo = redondearA3Decimales(
                (double) Math.abs(costoTotal) / (double) Math.abs(unidades));
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
    public long insertarItemInventario(SQLiteDatabase db, long cuentaId, String nombre, String unidad) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del artículo no puede estar vacío.");
        }
        ContentValues valores = new ContentValues();
        valores.put("cuenta_id", cuentaId);
        valores.put("nombre", nombre.trim());
        if (unidad != null && !unidad.trim().isEmpty()) {
            valores.put("unidad", unidad.trim());
        }
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

        public ItemInventario(long itemId, String nombre, String unidad) {
            this.itemId = itemId;
            this.nombre = nombre;
            this.unidad = unidad;
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
                "SELECT item_id, nombre, unidad FROM items_inventario " +
                        "WHERE cuenta_id = ? AND activo = 1 ORDER BY nombre COLLATE NOCASE",
                new String[]{String.valueOf(cuentaId)});
        try {
            while (c.moveToNext()) {
                items.add(new ItemInventario(
                        c.getLong(0),
                        c.getString(1),
                        c.isNull(2) ? null : c.getString(2)));
            }
        } finally {
            c.close();
        }
        return items;
    }
}
