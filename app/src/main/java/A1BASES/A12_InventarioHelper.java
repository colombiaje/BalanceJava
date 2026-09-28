package A1BASES;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
 *   forma atómica, con el monto siempre consistente con unidades × precio_unitario (reglas
 *   de negocio #1 y #2) — para una salida, el precio_unitario se calcula solo como el
 *   costo promedio vigente (nunca lo recibe del llamador), tal como Jorge confirmó.
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
     * @param db                    base de datos escribible (ya abierta por el llamador).
     * @param valoresTransaccion    columnas de "transacciones" ya armadas por el llamador
     *                              (documento, ítem, cuenta, signo, descripción, fechas,
     *                              cuenta_id, tipo_cuenta_id, etc.) — TODAS menos el monto:
     *                              este método pone/sobrescribe c5_Valor con
     *                              unidades × precio_unitario, para que siempre queden
     *                              consistentes (regla de negocio #2).
     * @param itemId                artículo (items_inventario.item_id) al que pertenece el
     *                              movimiento. Debe existir y pertenecer a una cuenta con
     *                              con_inventario = 1.
     * @param unidades              unidades del movimiento, con signo: positivo = entrada,
     *                              negativo = salida. No puede ser 0.
     * @param precioUnitarioEntrada precio unitario en COP, SOLO para una entrada (unidades
     *                              positivas) — obligatorio y mayor que 0 en ese caso. Para
     *                              una salida (unidades negativas) debe venir null: el
     *                              precio se calcula solo como el costo promedio vigente
     *                              (así lo confirmó Jorge) y cualquier valor recibido aquí
     *                              se ignora a propósito, para que la UX de salida no pueda
     *                              dejarlo inconsistente.
     * @return el transaccion_id recién creado.
     * @throws IllegalArgumentException si el artículo no existe, no pertenece a una cuenta
     *         con inventario, o los parámetros no son válidos.
     */
    public long guardarTransaccionConInventario(SQLiteDatabase db,
                                                  ContentValues valoresTransaccion,
                                                  long itemId,
                                                  long unidades,
                                                  Long precioUnitarioEntrada) {
        if (unidades == 0) {
            throw new IllegalArgumentException("Las unidades no pueden ser 0.");
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
            // Entrada: el precio lo trae el usuario.
            if (precioUnitarioEntrada == null || precioUnitarioEntrada <= 0) {
                throw new IllegalArgumentException(
                        "Una entrada necesita un precio_unitario mayor que 0.");
            }
            precioUnitario = precioUnitarioEntrada;
        } else {
            // Salida: el precio NUNCA lo trae el llamador — se calcula solo, como el costo
            // promedio vigente (decisión ya confirmada). calcularCostoPromedioPonderado ya
            // valida que haya saldo suficiente para vender.
            precioUnitario = calcularCostoPromedioPonderado(db, itemId);
        }

        long monto = unidades * precioUnitario;
        valoresTransaccion.put(COLUMNA_MONTO_TRANSACCION, monto);

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
}
