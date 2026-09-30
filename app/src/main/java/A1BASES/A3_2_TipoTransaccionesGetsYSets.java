package A1BASES;

// Created by JorgeEnrique on 6/08/2016.

public class A3_2_TipoTransaccionesGetsYSets {

    //declaramos atributos, los cuales se relacionan biunivomante con las columnas sub i de la TD
    public String tipoT_1NumberDocument_String;
    public String tipoT_2DocumentItems_String;
    public String tipoT_3Accout_String;
    public String tipoT_4Sign_String;
    public Integer tipoT_5Value_Integer;
    public String tipoT_6Description_String;
    public String tipoT_7TimeOfProcess_String;
    public Integer tipoT_8DateOfDocument_Integer;
    public String tipoT_9DateUpdateDocument_String;
    public String tipoT_10BalanceItems_String;
    public String tipoT_11BalanceItemsClassification_String;
    public String tipoT_12AccountWhitFlag_String;
    public String tipoT_13ColumnaDisponible_String;

    // ⭐ NUEVO — Fase 3 (parte B) de la reestructuración de BD: cuenta_id real de la cuenta
    // (tabla "cuentas"), resuelto por nombre en el mismo momento en que ya se resuelven
    // Grupo1/Grupo2 al construir el ítem (ver B11_DocumentCalculator). Puramente aditivo:
    // no reemplaza tipoT_3Accout_String (el nombre sigue siendo lo que usa hoy toda la app
    // para guardar, mostrar y reportar) y no se usa todavía en el guardado en BD — ese sigue
    // resolviendo cuenta_id por su cuenta en B12_DocumentPersistence, exactamente igual que
    // antes de este cambio. Queda disponible en el objeto para cuando se necesite.
    public Long tipoT_14CuentaId_Long;

    // ⭐ NUEVO v8 — Fase 5: transaccion_id real de la fila (tabla "transacciones"), leído por
    // nombre igual que tipoT_14CuentaId_Long (ver A21_OptimizedQuery.mapTransactionFromCursor).
    // Puramente aditivo: no reemplaza ningún campo existente.
    public Long tipoT_15TransaccionId_Long;

    // ⭐ NUEVO v11 — Fase 6 (parte C): tipo_cuenta_id real (tabla "tipo_cuenta"), foto de la
    // clasificación de la cuenta al momento de crear/editar la transacción — mismo espíritu que
    // tipoT_10Grupo1MetodoEnA5/tipoT_11Grupo2MetodoEnA5, pero apuntando al modelo nuevo (ver
    // A1_1_AyudanteBD, migración v11, para el porqué completo). Puramente aditivo: no reemplaza
    // ningún campo existente. Se llena en B11_DocumentCalculator al crear un ítem nuevo (desde
    // atributosCuenta[7]) y se refresca en B12_DocumentPersistence.guardarModificacion() cuando
    // cambia la cuenta durante una edición, igual que Grupo1/Grupo2.
    public Long tipoT_16TipoCuentaId_Long;

    // ⭐ NUEVO — v11 tanda 3 (parte B): nombre de tipo_cuenta (tabla "tipo_cuenta", vía JOIN por
    // tipo_cuenta_id), para reemplazar la visualización de Grupo1/Grupo2 en las 3 pantallas de
    // lista (F1_CrudDocumento, F3_1_VerInformePrincipal, F4_Cierres) sin depender de esas
    // columnas — se retirarán más adelante en esta misma tanda (parte D). Puramente aditivo:
    // no reemplaza ningún campo existente. Se llena por nombre desde el cursor (ver
    // A21_OptimizedQuery.mapTransactionFromCursor/obtenerSumaNetoCuentaPorCuenta), null si la
    // fila no tiene tipo_cuenta_id o el JOIN no encuentra la cuenta.
    public String tipoT_17TipoCuentaNombre_String;

    // ⭐ NUEVO — v12 tanda 3 (segundo fix, 29-sep, a pedido de Jorge): con_inventario real de
    // la cuenta (tabla "cuentas", vía JOIN por transacciones.cuenta_id — ver
    // A22_QueryManager.SELECT_TRANSACCIONES_CON_TIPO_CUENTA/FROM_TRANSACCIONES_CON_TIPO_CUENTA
    // y A21_OptimizedQuery.mapTransactionFromCursor), mismo criterio que
    // tipoT_17TipoCuentaNombre_String arriba: puramente informativo, para los 3 CSV de
    // transacciones de A5_1_BackupManager (mismo criterio que Cerrable/cuenta_id/
    // tipo_cuenta_id, ya presentes ahí). NO es una columna de "transacciones" — es el estado
    // ACTUAL de la cuenta al momento de generar el CSV, no una foto histórica — así que no se
    // lee de vuelta al restaurar (F4_Cierres.insertarTransaccion() simplemente no la usa,
    // igual que ya ignora cualquier columna que no le interese). Null si la cuenta no tiene
    // cuenta_id asignado o el JOIN no la encuentra (misma situación ya posible con
    // tipoT_17TipoCuentaNombre_String).
    public Boolean tipoT_18ConInventario_Boolean;

    // ⭐ NUEVO — v12 tanda 4: detalle de inventario del ítem (tabla "transacciones_inventario"),
    // presente SOLO cuando este ítem pertenece a una cuenta con con_inventario = 1 y fue
    // registrado (o cargado) a través del flujo nuevo de la tanda 4 — null en cualquier otro
    // caso (cuenta sin inventario, o ítem viejo de una cuenta sin inventario). Puramente
    // aditivo: no reemplaza ningún campo existente.
    //
    // tipoT_19ItemInventarioId_Long: items_inventario.item_id del artículo elegido.
    // tipoT_20UnidadesInventario_Long: unidades del movimiento, CON signo (positivo entrada,
    //   negativo salida) — mismo signo que tipoT_5Value_Integer.
    // tipoT_21PrecioUnitarioInventario_Double: dato puramente INFORMATIVO/derivado — nunca
    //   autoritativo. ⭐ REDISEÑO v12 tanda 4 (fix, 29-sep, tras retroalimentación de Jorge):
    //   antes, para una entrada, este campo era el precio que el usuario digitaba y se usaba
    //   tal cual al guardar; ahora el usuario nunca digita un precio aparte — tipoT_5Value_Integer
    //   (el valor ya escrito en el formulario) es siempre el dato autoritativo, y este campo,
    //   para un ítem NUEVO, es solo la vista previa que se le muestra (entrada: valor ÷
    //   unidades; salida: costo promedio vigente al momento de agregarlo a la lista) — ver
    //   B12_DocumentPersistence.mostrarDialogoRegistroInventario. A12_InventarioHelper.
    //   guardarTransaccionConInventario() vuelve a calcularlo de forma independiente al
    //   guardar de verdad, sin leer este campo. Para un ítem CARGADO de la BD (ya tiene
    //   tipoT_15TransaccionId_Long, ver A21_OptimizedQuery.mapTransactionFromCursor), sí viene
    //   con el precio_unitario histórico realmente guardado, tanto para entradas como salidas —
    //   necesario para que B12_DocumentPersistence pueda distinguir "ítem nuevo" de "ítem ya
    //   persistido" y bloquear el reguardado de este último por ahora (ver el comentario
    //   completo en baseParaGuardarEnLaEnBDConListaDocumento).
    // ⭐ REDISEÑO v15 (30-sep): el campo pasa de Long a Double — precio_unitario ahora puede
    //   traer hasta 3 decimales (antes redondeaba al peso entero) — ver A12_InventarioHelper
    //   para el detalle completo.
    public Long tipoT_19ItemInventarioId_Long;
    public Long tipoT_20UnidadesInventario_Long;
    public Double tipoT_21PrecioUnitarioInventario_Double;

    private String columna1;
    private int columna2;
    private String columna3;
    private int columna4;


// metodo constructor

    public A3_2_TipoTransaccionesGetsYSets(String tipoT_1NumberDocument_String, String tipoT_2DocumentItems_String, String tipoT_3Accout_String, String tipoT_4Sign_String,
                                           Integer tipoT_5Value_Integer, String tipoT_6Description_String, String tipoT_7TimeOfProcess_String, Integer tipoT_8DateOfDocument_Integer,
                                           String tipoT_9DateUpdateDocument_String, String tipoT_10BalanceItems_String, String tipoT_11BalanceItemsClassification_String, String
                                         tipoT_12AccountWhitFlag_String, String tipoT_13ColumnaDisponible_String) {

        this.tipoT_1NumberDocument_String = tipoT_1NumberDocument_String;
        this.tipoT_2DocumentItems_String = tipoT_2DocumentItems_String;
        this.tipoT_3Accout_String = tipoT_3Accout_String;
        this.tipoT_4Sign_String = tipoT_4Sign_String;
        this.tipoT_5Value_Integer = tipoT_5Value_Integer;
        this.tipoT_6Description_String = tipoT_6Description_String;
        this.tipoT_7TimeOfProcess_String = tipoT_7TimeOfProcess_String;
        this.tipoT_8DateOfDocument_Integer = tipoT_8DateOfDocument_Integer;
        this.tipoT_9DateUpdateDocument_String = tipoT_9DateUpdateDocument_String;
        this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;
        this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;
        this.tipoT_12AccountWhitFlag_String = tipoT_12AccountWhitFlag_String;
        this.tipoT_13ColumnaDisponible_String = tipoT_13ColumnaDisponible_String;
    }

    public A3_2_TipoTransaccionesGetsYSets(String tipoT_3Accout_String, String tipoT_4Sign_String,
                                           Integer tipoT_5Value_Integer, String tipoT_10BalanceItems_String, String tipoT_11BalanceItemsClassification_String) {

        this.tipoT_3Accout_String = tipoT_3Accout_String;
        this.tipoT_4Sign_String = tipoT_4Sign_String;
        this.tipoT_5Value_Integer = tipoT_5Value_Integer;
        this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;
        this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;

            }

    public A3_2_TipoTransaccionesGetsYSets() {

    }

    public String tipoTget_1DocumentoMetodoEnA5() {return tipoT_1NumberDocument_String; }
    public String tipoTget_2ItemDocMetodoEnA5() {
        return tipoT_2DocumentItems_String;
    }
    public String tipoTget_3CuentaMetodoEnA5() {return tipoT_3Accout_String;  }
    public String tipoTget_4MasMenosMetodoEnA5() {
        return tipoT_4Sign_String;
    }
    public Integer tipoTget_5ValorMetodoEnA5() {
        return tipoT_5Value_Integer;
    }
    public String tipoTget_6DescripcionMetodoEnA5() {
        return tipoT_6Description_String;
    }
    public String tipoTget_7FechaYHoraMetodoEnA5() {
        return tipoT_7TimeOfProcess_String;
    }
    public Integer tipoTget_8FechaInicialMetodoEnA5() {
        return tipoT_8DateOfDocument_Integer;
    }
    public String tipoTget_9FechaModificacionMetodoEnA5() {return tipoT_9DateUpdateDocument_String;}
    public String tipoTget_10Grupo1MetodoEnA5() {return tipoT_10BalanceItems_String;}
    public String tipoTget_11Grupo2MetodoEnA5() {return tipoT_11BalanceItemsClassification_String;}
    public String tipoTget_12ColumnaDisponibleMetodoEnA5() {return tipoT_12AccountWhitFlag_String;}
    public String tipoTget_13ColumnaDisponibleMetodoEnA5() {return tipoT_13ColumnaDisponible_String;}
    public Long tipoTget_14CuentaIdMetodoEnA5() {return tipoT_14CuentaId_Long;}
    public Long tipoTget_15TransaccionIdMetodoEnA5() {return tipoT_15TransaccionId_Long;}
    public Long tipoTget_16TipoCuentaIdMetodoEnA5() {return tipoT_16TipoCuentaId_Long;}
    public String tipoTget_17TipoCuentaNombreMetodoEnA5() {return tipoT_17TipoCuentaNombre_String;}
    public Boolean tipoTget_18ConInventarioMetodoEnA5() {return tipoT_18ConInventario_Boolean;}
    public Long tipoTget_19ItemInventarioIdMetodoEnA5() {return tipoT_19ItemInventarioId_Long;}
    public Long tipoTget_20UnidadesInventarioMetodoEnA5() {return tipoT_20UnidadesInventario_Long;}
    public Double tipoTget_21PrecioUnitarioInventarioMetodoEnA5() {return tipoT_21PrecioUnitarioInventario_Double;}

    public void tipoTset_1DocumentoMetodoEnA5(String tipoT_1NumberDocument_String) {this.tipoT_1NumberDocument_String = tipoT_1NumberDocument_String;}
    public void tipoTset_2ItemDocMetodoEnA5(String tipoT_2DocumentItems_String) {this.tipoT_2DocumentItems_String = tipoT_2DocumentItems_String;}
    public void tipoTset_3CuentaMetodoEnA5(String tipoT_3Accout_String) {this.tipoT_3Accout_String = tipoT_3Accout_String;}
    public void tipoTset_4MasMenosMetodoEnA5(String tipoT_4Sign_String) {this.tipoT_4Sign_String = tipoT_4Sign_String;}
    public void tipoTset_5ValorMetodoEnA5(Integer tipoT_5Value_Integer) {this.tipoT_5Value_Integer = tipoT_5Value_Integer;}
    public void tipoTset_6DescripcionMetodoEnA5(String tipoT_6Description_String) {this.tipoT_6Description_String = tipoT_6Description_String;}
    public void tipoTset_7FechaYHoraMetodoEnA5(String tipoT_7TimeOfProcess_String) {this.tipoT_7TimeOfProcess_String = tipoT_7TimeOfProcess_String;}
    public void tipoTset_8FechaInicialMetodoEnA5(Integer tipoT_8DateOfDocument_Integer) {this.tipoT_8DateOfDocument_Integer = tipoT_8DateOfDocument_Integer;}
    public void tipoTset_9FechaModificacionMetodoEnA5(String tipoT_9DateUpdateDocument_String) {this.tipoT_9DateUpdateDocument_String = tipoT_9DateUpdateDocument_String;}
    public void tipoTset_10Grupo1MetodoEnA5(String tipoT_10BalanceItems_String) {this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;}
    public void tipoTset_11Grupo2MetodoEnA5(String tipoT_11BalanceItemsClassification_String) {this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;}
    public void tipoTset_12ColumnaDisponibleMetodoEnA5(String tipoT_12AccountWhitFlag_String) {this.tipoT_12AccountWhitFlag_String = tipoT_12AccountWhitFlag_String;}
    public void tipoTset_13ColumnaDisponibleMetodoEnA5(String tipoT_13ColumnaDisponible_String) {this.tipoT_13ColumnaDisponible_String = tipoT_13ColumnaDisponible_String;}
    public void tipoTset_14CuentaIdMetodoEnA5(Long tipoT_14CuentaId_Long) {this.tipoT_14CuentaId_Long = tipoT_14CuentaId_Long;}
    public void tipoTset_15TransaccionIdMetodoEnA5(Long tipoT_15TransaccionId_Long) {this.tipoT_15TransaccionId_Long = tipoT_15TransaccionId_Long;}
    public void tipoTset_16TipoCuentaIdMetodoEnA5(Long tipoT_16TipoCuentaId_Long) {this.tipoT_16TipoCuentaId_Long = tipoT_16TipoCuentaId_Long;}
    public void tipoTset_17TipoCuentaNombreMetodoEnA5(String tipoT_17TipoCuentaNombre_String) {this.tipoT_17TipoCuentaNombre_String = tipoT_17TipoCuentaNombre_String;}
    public void tipoTset_18ConInventarioMetodoEnA5(Boolean tipoT_18ConInventario_Boolean) {this.tipoT_18ConInventario_Boolean = tipoT_18ConInventario_Boolean;}
    public void tipoTset_19ItemInventarioIdMetodoEnA5(Long tipoT_19ItemInventarioId_Long) {this.tipoT_19ItemInventarioId_Long = tipoT_19ItemInventarioId_Long;}
    public void tipoTset_20UnidadesInventarioMetodoEnA5(Long tipoT_20UnidadesInventario_Long) {this.tipoT_20UnidadesInventario_Long = tipoT_20UnidadesInventario_Long;}
    public void tipoTset_21PrecioUnitarioInventarioMetodoEnA5(Double tipoT_21PrecioUnitarioInventario_Double) {this.tipoT_21PrecioUnitarioInventario_Double = tipoT_21PrecioUnitarioInventario_Double;}

    public A3_2_TipoTransaccionesGetsYSets(String columna1, int columna2, String columna3, int columna4) {

        this.columna1 = columna1;
        this.columna2 = columna2;
        this.columna3 = columna3;
        this.columna4 = columna4;

    }

    public String tipoTget_1DocumentoMetodoEnA52() { return columna1; }
    public int tipoTget_5ValorMetodoEnA52() { return columna2; }
    public String tipoTget_6DescripcionMetodoEnA52() { return columna3; }
    public int tipoTget_8FechaInicialMetodoEnA52() { return columna4; }


    // Campo para el estado del check (temporal, no en BD)
    private boolean isChecked = false;

    // Getter y Setter para isChecked
    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

}
