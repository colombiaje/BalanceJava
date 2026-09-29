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
 *   sobrescribe, porque es lo que hace cuadrar el documento en cero. precio_unitario es un
 *   dato DERIVADO e informativo: para una entrada, se deriva del valor ya escrito
 *   (valor ÷ unidades); para una salida, se calcula solo como el costo promedio vigente
 *   (nunca lo recibe del llamador) — tal como Jorge confirmó tras la retroalimentación de la
 *   tanda 4 (tensión resuelta distinguiendo "valor recibido/pagado", que manda para el
 *   balance, de "costo de inventario", que es el que preserva el promedio ponderado).
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
     * Costo promedio ponderado vigente de un artículo (regla de negocio #4): valor total
     * acumulado en COP entre unidades totales acumuladas, sobre TODAS las filas
     * (entradas y salidas) del artículo en transacciones_inventario.
     *
     * No hace falta distinguir entradas de salidas ni guardar un promedio "corriente"
     * aparte: como cada salida se guarda exactamente al costo promedio vigente (nunca a su
     * propio precio), SUM(unidades × precio_unitario) / SUM(unidades) da, en cualquier
     * momento, el mismo resultado que recalcular el promedio solo con las entradas — una
     * salida resta valor y unidades en la misma proporción, así que no mueve el promedio.
     *
     * Redondea al peso más cercano (COP no maneja centavos en esta app — ver
     * transacciones.c5_Valor, que también es INTEGER).
     *
     * @throws IllegalStateException si el artículo no tiene saldo en unidades (nada que
     *         vender, o saldo en 0 o negativo) — no hay costo promedio que calcular.
     */
    public long calcularCostoPromedioPonderado(SQLiteDatabase db, long itemId) {
        long valorTotal = 0;
        long unidadesTotal = 0;
        Cursor c = db.rawQuery(
                "SELECT SUM(unidades * precio_unitario), SUM(unidades) " +
                        "FROM transacciones_inventario WHERE item_id = ?",
                new String[]{String.valueOf(itemId)});
        try {
            if (c.moveToFirst() && !c.isNull(1)) {
                valorTotal = c.getLong(0);
                unidadesTotal = c.getLong(1);
            }
        } finally {
            c.close();
        }

        if (unidadesTotal <= 0) {
            throw new IllegalStateException(
                    "El artículo " + itemId + " no tiene saldo en unidades (" + unidadesTotal +
                            ") — no se puede calcular un costo promedio ni registrar una salida.");
        }

        long costoPromedio = Math.round((double) valorTotal / (double) unidadesTotal);
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
     * precio_unitario pasa a ser un dato DERIVADO, informativo, calculado internamente aquí:
     * - Entrada (unidades > 0): precio_unitario = round(|c5_Valor| / unidades). El dinero
     *   pagado (c5_Valor) y el costo agregado al inventario son, económicamente, la misma
     *   cifra — no hay conflicto en derivar uno del otro.
     * - Salida (unidades < 0): precio_unitario = costo promedio ponderado vigente (regla de
     *   negocio #4, sin cambios) — el valor que el usuario recibió por la venta (c5_Valor)
     *   puede diferir del costo que sale del inventario (esa diferencia es la utilidad o
     *   pérdida de la venta, que esta tanda todavía no registra aparte); por eso aquí NO se
     *   deriva de c5_Valor, se sigue calculando solo, igual que antes.
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

        long precioUnitario;
        if (unidades > 0) {
            // Entrada: precio_unitario se DERIVA del valor ya escrito por el usuario — nunca
            // se pide aparte, para que no pueda quedar en una escala distinta a "valor".
            long valorTransaccion = valoresTransaccion.getAsLong(COLUMNA_MONTO_TRANSACCION);
            precioUnitario = Math.round((double) Math.abs(valorTransaccion) / (double) unidades);
            if (precioUnitario <= 0) {
                throw new IllegalArgumentException(
                        "El precio unitario derivado de valor/unidades no es válido (" +
                                precioUnitario + ") — revisa el valor y las unidades digitadas.");
            }
        } else {
            // Salida: el precio NUNCA se deriva de c5_Valor — se calcula solo, como el costo
            // promedio vigente (decisión ya confirmada). calcularCostoPromedioPonderado ya
            // valida que haya saldo suficiente para vender.
            precioUnitario = calcularCostoPromedioPonderado(db, itemId);
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
            valoresInventario.put("precio_unitario", precioUnitario);
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
