package B1_F1Support;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import A1BASES.A12_InventarioHelper;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A99_MetodosVarios;
import A2QueryBD.A23_QueryResult;
import B_FRAGMENTS.F1_CrudDocumento;
import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;

/**
 * DocumentPersistence — persistencia y construcción de ítems de documento.
 *
 * Responsabilidades:
 * - Guardar lista de ítems en SQLite (baseParaGuardarEnLaEnBDConListaDocumento)
 * - Asignar fecha al documento según área activa (assignDocumentDate)
 * - Construir primer registro de conciliación (primerRegistroAListaDocumentoCuentaConciliable)
 * - Agregar ítems adicionales a la lista (losDemasRegistrosAListaDocumento)
 * - Calcular diferencia físico vs contable (digitarFisicoVsSaldoConciliacion)
 * - Guardar modificación inline de ítem (guardarModificacion)
 */
public class B12_DocumentPersistence {

    private static final String TAG = "DocumentPersistence";
    private final F1_CrudDocumento f1;

    public B12_DocumentPersistence(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. baseParaGuardarEnLaEnBDConListaDocumento
    // ═══════════════════════════════════════════════════════════════
    // ⭐ CAMBIO — v12 tanda 3 (segundo fix, 29-sep): pasa de void a boolean (true = se guardó,
    // false = bloqueado por una cuenta con inventario — nada se guardó). Antes, aunque el
    // guardado se bloqueara, ExecuteButtonsUnit igual llamaba a continuación a
    // realizarOperacionesPostSeleccion(), que limpia la lista de ítems en memoria Y muestra
    // "Backup local, en Drive y documento actualizado" — un mensaje de ÉXITO engañoso
    // apareciendo justo después (o casi encima) del aviso real de bloqueo, y la lista de
    // ítems desapareciendo de la pantalla como si sí se hubiera guardado. Esto explica el
    // reporte de Jorge de "no sale el aviso... y no queda registrado en la BD": el aviso sí
    // se mostraba, pero quedaba tapado/opacado por el segundo mensaje de "éxito" que llegaba
    // enseguida, y la pantalla se limpiaba igual que en un guardado real. Con el valor de
    // retorno, ExecuteButtonsUnit ahora puede saltarse ese post-procesamiento cuando el
    // guardado fue bloqueado.
    public boolean baseParaGuardarEnLaEnBDConListaDocumento(String nombreDelRadioButton) {
        assignDocumentDate(nombreDelRadioButton);
        f1.renumerarItemsListaDocumento();

        // ⭐ CORRECCIÓN — v12 tanda 3 (segundo fix, 29-sep): la verificación se movió a su
        // propio método público (bloqueadoPorCuentaConInventario(), más abajo) para poder
        // llamarla TAMBIÉN desde ExecuteButtonsUnit ANTES de borrar las transacciones viejas
        // del documento en el flujo "Modificar documento" — ver el comentario completo en esa
        // función.
        if (bloqueadoPorCuentaConInventario()) {
            return false;
        }

        SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
        db.beginTransaction();
        try {
            for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
                String nombreCuentaParaGuardar = p.tipoTget_3CuentaMetodoEnA5();

                // ⭐ NUEVO — Fase 3 (parte A) de la reestructuración de BD: resolver
                // cuenta_id por nombre al momento de guardar, para que las transacciones
                // nuevas queden ligadas a "cuentas" desde su creación. Antes de este
                // cambio, cuenta_id solo se llenaba en el backfill de la migración de la
                // Fase 1 (versión 3); ninguna transacción nueva guardada desde entonces lo
                // recibía, porque este INSERT no lo incluía. No se toca c3_Cuenta ni ningún
                // otro comportamiento existente: esto solo agrega el dato en paralelo.
                Long cuentaIdParaGuardar = null;
                Cursor cCuentaId = db.rawQuery(
                        "SELECT cuenta_id FROM cuentas WHERE Cuenta = ?",
                        new String[]{nombreCuentaParaGuardar});
                if (cCuentaId.moveToFirst()) {
                    cuentaIdParaGuardar = cCuentaId.getLong(0);
                } else {
                    Log.w(TAG, "cuenta_id no encontrado para '" + nombreCuentaParaGuardar +
                            "' al guardar transacción — quedará con cuenta_id NULL, igual " +
                            "que antes de este cambio");
                }
                cCuentaId.close();

                // ⭐ NUEVO v11 — Fase 6 (parte C): tipo_cuenta_id se toma del OBJETO (no se
                // reconsulta aquí por nombre de cuenta, a diferencia de cuenta_id arriba). Es a
                // propósito: cuenta_id es un simple enlace de identidad y debe reflejar siempre
                // la cuenta real por nombre; tipo_cuenta_id es una FOTO de clasificación (mismo
                // criterio que c10_Grupo1/c11_Grupo2, que también se toman del objeto) — si se
                // reconsultara aquí en vez de leerla del objeto, un documento con VARIOS ítems se
                // reescribiría completo (ver eliminarTransacciones + reinserción en
                // ExecuteButtonsUnit) cada vez que se edita CUALQUIER ítem, y los ítems NO
                // tocados de ese mismo documento perderían su foto histórica y quedarían con la
                // clasificación actual de su cuenta — exactamente el efecto que la foto existe
                // para evitar. El objeto ya trae el valor correcto: para un ítem nuevo, desde
                // B11_DocumentCalculator (atributosCuenta[7]); para un ítem cargado de la BD
                // (edición/plantilla), desde A21_OptimizedQuery.mapTransactionFromCursor; y para
                // el ítem que sí cambió de cuenta en esta edición, desde el refresco en
                // guardarModificacion() (ver ahí).
                Long tipoCuentaIdParaGuardar = p.tipoTget_16TipoCuentaIdMetodoEnA5();

                // ⭐ NUEVO — v12 tanda 4: un ítem de una cuenta con inventario (viene marcado
                // con tipoTget_19ItemInventarioIdMetodoEnA5() != null, ver
                // mostrarDialogoRegistroInventario) se guarda distinto: no con el INSERT crudo
                // de siempre, sino con A12_InventarioHelper.guardarTransaccionConInventario, que
                // inserta "transacciones" y su fila correspondiente en "transacciones_inventario"
                // de forma atómica (con su propio db.beginTransaction()/setTransactionSuccessful
                // anidado dentro de esta transacción de todo el documento — Android soporta
                // transacciones anidadas: si cualquiera de las dos falla, se revierte TODO el
                // documento, no solo este ítem — regla de negocio #1 del documento de
                // especificación).
                // ⭐ CAMBIO — v12 tanda 4 (rediseño, retroalimentación de Jorge): c5_Valor SÍ se
                // pone aquí ahora, con tipoTget_5ValorMetodoEnA5() — el valor exacto que el
                // usuario ya escribió en el formulario, tal cual, igual que para cualquier otro
                // ítem — porque es lo que hace cuadrar el documento en cero (partida doble). El
                // helper ya NO lo recalcula ni lo sobrescribe; solo deriva internamente el
                // precio_unitario informativo (ver el javadoc de guardarTransaccionConInventario).
                //
                // bloqueadoPorCuentaConInventario() (llamado arriba, antes de abrir esta
                // transacción) ya garantiza que, si llegamos aquí con un ítem de inventario, es
                // uno NUEVO (nunca antes guardado) — nunca uno cargado de la BD para editar.
                if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
                    ContentValues valoresTransaccion = new ContentValues();
                    valoresTransaccion.put("c1_Documento", p.tipoTget_1DocumentoMetodoEnA5());
                    valoresTransaccion.put("c2_ItemDoc", p.tipoTget_2ItemDocMetodoEnA5());
                    valoresTransaccion.put("c3_Cuenta", nombreCuentaParaGuardar);
                    valoresTransaccion.put("c4_Signo", p.tipoTget_4MasMenosMetodoEnA5());
                    valoresTransaccion.put("c5_Valor", p.tipoTget_5ValorMetodoEnA5());
                    valoresTransaccion.put("c6_Descripcion", p.tipoTget_6DescripcionMetodoEnA5());
                    valoresTransaccion.put("c7_FechaYhora", p.tipoTget_7FechaYHoraMetodoEnA5());
                    valoresTransaccion.put("c8_FechaInicial", p.tipoTget_8FechaInicialMetodoEnA5());
                    valoresTransaccion.put("c9_FechaModificacion", p.tipoTget_9FechaModificacionMetodoEnA5());
                    valoresTransaccion.put("c12_ColumnaDisponible", p.tipoTget_12ColumnaDisponibleMetodoEnA5());
                    valoresTransaccion.put("c13_ColumnaDisponible", p.tipoTget_13ColumnaDisponibleMetodoEnA5());
                    valoresTransaccion.put("cuenta_id", cuentaIdParaGuardar);
                    valoresTransaccion.put("tipo_cuenta_id", tipoCuentaIdParaGuardar);

                    try {
                        new A12_InventarioHelper().guardarTransaccionConInventario(
                                db,
                                valoresTransaccion,
                                p.tipoTget_19ItemInventarioIdMetodoEnA5(),
                                p.tipoTget_20UnidadesInventarioMetodoEnA5());
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        // Error de negocio esperable (p.ej. una salida sin saldo suficiente si
                        // cambió entre agregar el ítem y guardar) — se relanza como Exception
                        // genérica para que el catch de más abajo revierta TODO el documento
                        // (nunca a medias) y muestre un mensaje, con este detalle específico
                        // adjunto en vez del genérico.
                        throw new RuntimeException(
                                "Inventario — \"" + nombreCuentaParaGuardar + "\": " + e.getMessage(), e);
                    }
                    continue;
                }

                // ⭐ CAMBIO — v11 tanda 3 (parte D): "transacciones" pierde c10_Grupo1/c11_Grupo2
                // (ver A1_1_AyudanteBD, migración v13) — se quitan de la lista de columnas y de
                // los VALUES; ya no se escriben (desde parte C solo se guardaba "" de todas
                // formas). p.tipoTget_10Grupo1MetodoEnA5()/p.tipoTget_11Grupo2MetodoEnA5() dejan
                // de leerse aquí.
                db.execSQL(
                        "INSERT INTO transacciones (" +
                                "c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                                "c6_Descripcion, c7_FechaYhora, c8_FechaInicial, " +
                                "c9_FechaModificacion, " +
                                "c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id, " +
                                "tipo_cuenta_id) " +
                                "VALUES ('" +
                                p.tipoTget_1DocumentoMetodoEnA5()          + "','" +
                                p.tipoTget_2ItemDocMetodoEnA5()            + "','" +
                                nombreCuentaParaGuardar                    + "','" +
                                p.tipoTget_4MasMenosMetodoEnA5()           + "','" +
                                p.tipoTget_5ValorMetodoEnA5()              + "','" +
                                p.tipoTget_6DescripcionMetodoEnA5()        + "','" +
                                p.tipoTget_7FechaYHoraMetodoEnA5()         + "','" +
                                p.tipoTget_8FechaInicialMetodoEnA5()       + "','" +
                                p.tipoTget_9FechaModificacionMetodoEnA5()  + "','" +
                                p.tipoTget_12ColumnaDisponibleMetodoEnA5() + "','" +
                                p.tipoTget_13ColumnaDisponibleMetodoEnA5() + "'," +
                                (cuentaIdParaGuardar != null ? cuentaIdParaGuardar : "NULL") + "," +
                                (tipoCuentaIdParaGuardar != null ? tipoCuentaIdParaGuardar : "NULL") +
                                ")"
                );
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting transactions", e);
            // ⭐ CAMBIO — v12 tanda 4: si el mensaje trae detalle (ver el guardado de ítems de
            // inventario arriba), se muestra tal cual en vez del genérico de siempre — sigue
            // siendo Toast, no AlertDialog, porque este es un error inesperado de guardado (no
            // el aviso de bloqueo, que sí es AlertDialog desde la tanda 3).
            String detalle = e.getMessage();
            Toast.makeText(f1.getActivity(),
                    (detalle != null && !detalle.isEmpty())
                            ? "Error al guardar: " + detalle
                            : "Error al guardar las transacciones",
                    Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            db.close();
        }

        f1.numerarDocumentoConsecutivo();
        f1.numeroConsecutivoDocEnEdicion_XTv.setText(f1.documentoRecibido_Resultado_String);
        f1.metodosVarios_Class.fechasYHoras();
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1b. bloqueadoPorCuentaConInventario
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 3: este formulario (el de siempre) todavía no sabe pedir
    // item/unidades/precio_unitario para una cuenta con con_inventario = 1 — eso llega en
    // la tanda 4, que va a conectar aquí la lógica ya lista y probada de
    // A12_InventarioHelper (tanda 2). Mientras tanto, si CUALQUIER ítem de la lista actual
    // del documento apunta a una cuenta con inventario, se bloquea el guardado — nunca a
    // medias — para que nunca pueda quedar una transacción sin su fila correspondiente en
    // transacciones_inventario.
    // ⭐ CORRECCIÓN — v12 tanda 3 (fix, 28-sep): se agrega .trim() al nombre antes de
    // comparar contra "cuentas.Cuenta" — c3_Cuenta se guarda tal cual viene del campo de
    // texto de este formulario (a diferencia de F2_Cuentas.registrarNuevas(), que SÍ recorta
    // espacios al crear la cuenta), así que un espacio de más al escribir o pegar el nombre
    // de la cuenta en ESTE formulario hacía que la comparación exacta no encontrara la
    // cuenta y el bloqueo se saltara en silencio.
    // ⭐ CORRECCIÓN — v12 tanda 3 (SEGUNDO fix, 29-sep): dos cambios más, a partir de la
    // segunda ronda de pruebas de Jorge (el aviso seguía sin verse y las transacciones
    // seguían sin registrarse):
    //   1) El aviso pasa de Toast a AlertDialog. Un Toast desaparece solo a los pocos
    //      segundos y es fácil perdérselo — con un diálogo que hay que cerrar a propósito,
    //      es imposible que pase inadvertido.
    //   2) Este método se EXTRAE de baseParaGuardarEnLaEnBDConListaDocumento() a su propia
    //      función pública para poder llamarlo, desde ExecuteButtonsUnit, ANTES de que el
    //      flujo "Modificar documento" borre las transacciones viejas del documento
    //      (A1_2_OperacionesBD.eliminarTransacciones()). Antes de este cambio, el orden real
    //      era: borrar TODO lo viejo del documento → intentar reinsertar la lista completa →
    //      SI esa reinserción se bloqueaba por una cuenta con inventario, el documento
    //      quedaba con sus transacciones viejas ya borradas y nada nuevo en su lugar —
    //      pérdida real de datos de ese documento, no solo "no se guardó el cambio nuevo".
    //      Esto probablemente explica lo que Jorge reportó como "no se guarda nada": si
    //      estaba editando un documento existente (no creando uno nuevo) y alguno de sus
    //      ítems apuntaba a una cuenta con inventario, el documento completo se vaciaba.
    // Se agrega también un try/catch alrededor de toda la verificación: si algo inesperado
    // falla al consultar con_inventario (por ejemplo, en un dispositivo donde la migración
    // no corrió como se esperaba), se bloquea el guardado POR SEGURIDAD en vez de dejar
    // pasar la transacción sin verificar, y se avisa con un mensaje claro en vez de fallar
    // en silencio.
    // ⭐ CAMBIO — v12 tanda 4: la tanda 4 ya conecta el formulario nuevo (diálogo de
    // artículo/unidades/precio, ver mostrarDialogoRegistroInventario) — así que este bloqueo
    // deja de ser un bloqueo TOTAL de cualquier ítem de una cuenta con inventario, y pasa a
    // cubrir 2 casos puntuales que SÍ siguen sin soportarse:
    //   1) Un ítem de una cuenta con inventario que llegó al guardado SIN pasar por ese
    //      diálogo (tipoTget_19ItemInventarioIdMetodoEnA5() == null) — no debería poder pasar
    //      desde la UI ahora que losDemasRegistrosAListaDocumento desvía al diálogo, pero se
    //      deja el bloqueo como red de seguridad (mismo espíritu que el resto de este método).
    //   2) Un ítem de inventario que YA estaba guardado en la BD antes de abrir este documento
    //      para modificarlo (tipoTget_15TransaccionIdMetodoEnA5() != null, es decir, viene
    //      cargado — ver A21_OptimizedQuery.mapTransactionFromCursor). "Modificar documento"
    //      borra TODAS las transacciones del documento y reinserta la lista completa (ver
    //      ExecuteButtonsUnit/baseParaGuardarEnLaEnBDConListaDocumento) — para una entrada esto
    //      sería seguro (mismo precio de siempre), pero para una SALIDA, volver a calcularle el
    //      costo promedio en el momento de reinsertar podría no coincidir con lo que se calculó
    //      la primera vez, reescribiendo silenciosamente un costo histórico. Editar documentos
    //      que ya tienen movimientos de inventario queda deliberadamente pendiente para una
    //      tanda aparte (hay que decidir primero cómo debe comportarse esa edición) — por ahora
    //      se bloquea con un mensaje claro en vez de arriesgar el dato.
    public boolean bloqueadoPorCuentaConInventario() {
        for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
            if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null
                    && p.tipoTget_15TransaccionIdMetodoEnA5() != null) {
                Log.i(TAG, "Bloqueo de inventario ACTIVADO: el ítem de \"" +
                        p.tipoTget_3CuentaMetodoEnA5() + "\" (transaccion_id " +
                        p.tipoTget_15TransaccionIdMetodoEnA5() + ") ya estaba guardado — " +
                        "editar documentos con movimientos de inventario todavía no está " +
                        "soportado.");
                new AlertDialog.Builder(f1.getActivity())
                        .setTitle("Documento con inventario")
                        .setMessage("Este documento ya tiene un movimiento guardado sobre \"" +
                                p.tipoTget_3CuentaMetodoEnA5() + "\" (cuenta con inventario) — " +
                                "modificar documentos que ya tienen movimientos de inventario " +
                                "todavía está en construcción, no se puede guardar aquí por " +
                                "ahora.")
                        .setPositiveButton("Entendido", null)
                        .setCancelable(true)
                        .show();
                return true;
            }
        }

        SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
        try {
            for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
                if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
                    // Ya pasó por el diálogo nuevo — este ítem sabe guardarse (ver el guardado
                    // en baseParaGuardarEnLaEnBDConListaDocumento), no se bloquea.
                    continue;
                }
                String nombreCuentaAVerificar = p.tipoTget_3CuentaMetodoEnA5();
                String nombreCuentaAVerificarRecortado =
                        nombreCuentaAVerificar == null ? null : nombreCuentaAVerificar.trim();
                Cursor cConInventario = db.rawQuery(
                        "SELECT con_inventario FROM cuentas WHERE Cuenta = ?",
                        new String[]{nombreCuentaAVerificarRecortado});
                boolean esConInventario = false;
                try {
                    if (cConInventario.moveToFirst() && !cConInventario.isNull(0)) {
                        esConInventario = cConInventario.getInt(0) != 0;
                    } else {
                        Log.w(TAG, "Bloqueo de inventario: no se encontró la cuenta \"" +
                                nombreCuentaAVerificarRecortado + "\" al verificar con_inventario " +
                                "— si esta cuenta SÍ existe y SÍ tiene inventario, revisar si el " +
                                "nombre guardado en el ítem del documento no coincide exactamente " +
                                "(mayúsculas/espacios) con el de \"cuentas\".");
                    }
                } finally {
                    cConInventario.close();
                }
                if (esConInventario) {
                    Log.i(TAG, "Bloqueo de inventario ACTIVADO para la cuenta \"" +
                            nombreCuentaAVerificarRecortado + "\" — se muestra el diálogo y no " +
                            "se guarda nada.");
                    new AlertDialog.Builder(f1.getActivity())
                            .setTitle("Cuenta con inventario")
                            .setMessage("\"" + nombreCuentaAVerificarRecortado + "\" maneja " +
                                    "inventario, y este ítem no pasó por el registro de " +
                                    "inventario (artículo/unidades/precio) — no se puede " +
                                    "guardar así.")
                            .setPositiveButton("Entendido", null)
                            .setCancelable(true)
                            .show();
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error verificando cuentas con inventario antes de guardar", e);
            Toast.makeText(f1.getActivity(),
                    "No se pudo verificar si alguna cuenta maneja inventario — por seguridad, " +
                            "no se guardó nada. Detalle: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return true;
        } finally {
            db.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. assignDocumentDate
    // ═══════════════════════════════════════════════════════════════
    public void assignDocumentDate(String radioButtonName) {
        switch (radioButtonName) {
            case "create_XRb":
                if (!f1.dateInCreateNew_XTv.getText().toString().isEmpty()) {
                    f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];
                }
                if (!f1.otherDateInCreateNew_XTv.getText().toString().isEmpty()) {
                    f1.DateOfDocument_Integer = f1.otherDateAssignedInCreateNew_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                    }
                }
                break;

            case "template_XRb":
                if (f1.dateTemplateAssignedInCaledarView_Int > 0) {
                    f1.DateOfDocument_Integer = f1.dateTemplateAssignedInCaledarView_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_1DocumentoMetodoEnA5(f1.nuevoNumeroDocEnAdicionar_String);
                        item.tipoTset_7FechaYHoraMetodoEnA5(A99_MetodosVarios.stringFechaYHora);
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                        item.tipoTset_9FechaModificacionMetodoEnA5("No Aplica");
                    }
                }
                break;

            case "updateDelete_XRb":
                String docAModificar = f1.documentoABuscarParaEditar_XATv.getText().toString();
                if (docAModificar.isEmpty()) break;

                if (f1.dateInUpdateAssignedInCaledarView_Int > 0) {
                    f1.DateOfDocument_Integer = f1.dateInUpdateAssignedInCaledarView_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                        item.tipoTset_9FechaModificacionMetodoEnA5(
                                f1.dateInUpdate_XTv.getText().toString());
                    }
                } else {
                    f1.DateOfDocument_Integer = Integer.parseInt(
                            f1.dateInUpdate_XTv.getText().toString());
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                    }
                }
                for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                    item.tipoTset_1DocumentoMetodoEnA5(docAModificar);
                }
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. digitarFisicoVsSaldoConciliacion
    // ═══════════════════════════════════════════════════════════════
    public void digitarFisicoVsSaldoConciliacion() {
        try {
            String strFisico = f1.inputPhysicalVsAccounting_XEt.getText().toString();
            f1.$valorAc = strFisico.isEmpty() ? 0 : Integer.parseInt(strFisico);

            if (f1.$valorAc != 0) {
                f1.$diferenciaAConciliar_Integer = f1.$sAc - f1.$valorAc;
                f1.diferenciaAConciliar_XTv.setText("Dif.\n" + f1.$diferenciaAConciliar_Integer);
            } else {
                f1.$diferenciaAConciliar_Integer = 0;
                f1.diferenciaAConciliar_XTv.setText("");
            }

            if (!f1.cuentaConciliacion_XSp.getSelectedItem().toString().isEmpty()) {
                f1.dynamicQuerySumByAccountAcordingToArgument();
                f1.dynamicQueryByAllAccountAz();
                f1.$sAc = Integer.parseInt(String.valueOf(f1.sumaTransaccionesCuenta2));
            } else {
                f1.$sAc = 0;
            }

            f1.$dAc = f1.$sAc - f1.$valorAc;
            f1.sumarItemListaDocumento();
            f1.$adicionesAc = f1.$netoMC + f1.$dAc;
            f1.saldoCuentaAconciliar_XTv.setText("Saldo:\n" + f1.$sAc);

            if (f1.$mTP == 0 && f1.$mTN == 0) {
                f1.$quedaPorRegistrar = f1.$dAc;
            } else {
                f1.$quedaPorRegistrar = f1.$sAc - f1.$valorAc
                        - (f1.$mTP - f1.$mCP)
                        + (f1.$mTN * (-1) - f1.$mCN * (-1));
            }

            f1.controlACeroTotales_XTv.setText("" + f1.$netoT);

            if (f1.enModoModificacion) {
                f1.fabModificar.post(() -> {
                    if (f1.enModoModificacion
                            && f1.fabModificar.getVisibility() != View.VISIBLE) {
                        f1.fabModificar.setVisibility(View.VISIBLE);
                        f1.fabModificar.show();
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en digitarFisicoVsSaldoConciliacion", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. primerRegistroAListaDocumentoCuentaConciliable
    // ═══════════════════════════════════════════════════════════════
    public void primerRegistroAListaDocumentoCuentaConciliable() {
        if (f1.listaDocumento_ArrayLTT.size() != 0) return;
        if (f1.$dAc == 0) return;

        String cuentaConciliable = f1.cuentaConciliacion_XSp.getSelectedItem().toString();
        if ("1 No conciliar".equals(cuentaConciliable)) return;

        f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];
        f1.dateCurrent_ArrayInteger = f1.metodosVarios_Class.fechasYHoras();

        f1.dynamicQueryAtributtesAccount();
        f1.dynamicQuerySumByAccountAcordingToArgument();
        f1.dynamicQueryByAllAccountAz();

        A3_2_TipoTransaccionesGetsYSets item =
                f1.calculator.construirItemRegistroInicial(
                        f1.$dAc,
                        cuentaConciliable,
                        f1.nuevoNumeroDocEnAdicionar_String,
                        A99_MetodosVarios.stringFechaYHora,
                        f1.DateOfDocument_Integer,
                        f1.atributosCuenta_ArrayS);

        if (item == null) return;

        f1.listaDocumento_ArrayLTT.add(item);
        f1.listaCuentasRevisionParaAdapterSpinner_ArrayListString.add(cuentaConciliable);
        f1.conectarListaCuentasRevisionConSpinner_arrayAdapterString.notifyDataSetChanged();
        f1.listaCuentasDeRevision_XSp.setAdapter(
                f1.conectarListaCuentasRevisionConSpinner_arrayAdapterString);

        Toast.makeText(f1.getActivity(), "Primer registro", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. losDemasRegistrosAListaDocumento
    // ═══════════════════════════════════════════════════════════════
    // ⭐ REORDEN UX — v16 (1-oct, a pedido de Jorge): el orden de digitación del formulario
    // cambia de (valor, descripción, signo, cuenta) a (descripción, cuenta, valor, signo) —
    // ver el comentario de clase en RecordDocumentUnit.setupCuentaSpinner()/setupSignoSpinner()
    // para el porqué completo. Este método, que antes se disparaba al elegir CUENTA (la última
    // casilla del orden viejo) y hacía TODO — validar, resolver la cuenta y agregar el ítem a
    // la lista — ahora se dispara al elegir cuenta en el orden NUEVO, que es la ANTEPENÚLTIMA
    // casilla (antes de valor y signo). Ya no puede validar ni usar valor/signo (todavía no se
    // han digitado) ni agregar el ítem: solo resuelve a qué cuenta corresponde y, si esa cuenta
    // maneja inventario, abre el diálogo (que con el orden nuevo se abre ANTES de que el
    // usuario toque valor — el diálogo es quien determina valor y signo, y agrega el ítem él
    // mismo al confirmar, ver mostrarDialogoRegistroInventario). Si la cuenta NO maneja
    // inventario, este método ya no hace nada más — simplemente deja que el usuario continúe
    // con valor y signo; es confirmarRegistroAlElegirSigno() (más abajo) quien ahora valida y
    // agrega el ítem, disparado al elegir signo (la nueva última casilla).
    public void losDemasRegistrosAListaDocumento() {
        f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];

        // Validaciones — solo descripción y cuenta están disponibles en este punto del orden
        // nuevo; valor y signo se validan en confirmarRegistroAlElegirSigno().
        if (f1.descripcion_XAtv.getText().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la descripcion", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determinar cuenta

        String cuentaAlItemList = f1.enModoModificacion
                ? f1.cuenta_XSp.getSelectedItem().toString()
                : f1.cuenta_XAtv.getText().toString();

        if (cuentaAlItemList == null || cuentaAlItemList.isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        A23_QueryResult<String[]> obtenerAtributo =
                f1.a22QueryManager.queryAttributesByAccount(cuentaAlItemList);

        if (obtenerAtributo == null
                || obtenerAtributo.getAtributosCuenta() == null
                || obtenerAtributo.getAtributosCuenta().length == 0) {
            Toast.makeText(f1.getActivity(),
                    "Cuenta no encontrada: " + cuentaAlItemList, Toast.LENGTH_SHORT).show();
            return;
        }

        f1.atributosCuenta_ArrayS = obtenerAtributo.getAtributosCuenta();
        f1.cuentaDemasRegistros_ArrayS = new String[]{cuentaAlItemList};
        f1.dynamicQuerySumByAccountAcordingToArgument();
        f1.dynamicQueryByAllAccountAz();
// ✅ hasta aquí

        // ⭐ NUEVO — v12 tanda 4: si la cuenta elegida maneja inventario (índice 8 del arreglo,
        // ver A22_QueryManager.queryAttributesByAccount), el formulario de siempre no alcanza
        // — hace falta además el artículo, las unidades y el precio unitario (documento de
        // especificación, sección 4). En vez de agregar el ítem de una vez con lo que hay en
        // pantalla, se abre un diálogo a pedirlos (misma decisión que Jorge ya aprobó para el
        // bloqueo de la tanda 3: un diálogo, sin tocar el formulario existente) y es ESE
        // diálogo, al confirmar, el que arma y agrega el ítem — ver
        // mostrarDialogoRegistroInventario más abajo. ⭐ REORDEN v16: con el orden nuevo, este
        // diálogo se abre ANTES de que el usuario digite valor o elija signo — el diálogo
        // mismo determina ambos y agrega el ítem directamente (ver el método), así que si la
        // cuenta maneja inventario, el usuario nunca llega a confirmarRegistroAlElegirSigno().
        boolean cuentaConInventario = f1.atributosCuenta_ArrayS.length > 8
                && "1".equals(f1.atributosCuenta_ArrayS[8]);
        if (cuentaConInventario) {
            mostrarDialogoRegistroInventario(cuentaAlItemList, f1.atributosCuenta_ArrayS);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5a. confirmarRegistroAlElegirSigno
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v16 (1-oct): con el reorden de la UX (ver losDemasRegistrosAListaDocumento,
    // arriba), signo pasa a ser la ÚLTIMA casilla del formulario y toma el rol de "disparador"
    // que agrega el ítem a la lista — rol que antes cumplía cuenta. Se dispara desde
    // RecordDocumentUnit.setupSignoSpinner() cuando el usuario elige "+" o "-" (nunca con
    // signo vacío). Para una cuenta CON inventario, este método nunca llega a ejecutarse de
    // verdad: el diálogo de inventario ya agregó el ítem y limpió el formulario al elegir
    // cuenta (más arriba), así que cuenta_XAtv/cuenta_XSp ya están vacíos cuando el usuario
    // vuelve a tocar signo para un ítem SIGUIENTE — el guard de "cuenta con inventario" de
    // abajo es, por eso, una red de seguridad defensiva (mismo espíritu que el resto de esta
    // clase), no el camino esperado.
    public void confirmarRegistroAlElegirSigno() {
        f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];

        if (f1.valor_XEt.getText().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta el valor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.signo_XSp.getSelectedItem().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta el signo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.descripcion_XAtv.getText().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la descripcion", Toast.LENGTH_SHORT).show();
            return;
        }

        String cuentaAlItemList = f1.enModoModificacion
                ? f1.cuenta_XSp.getSelectedItem().toString()
                : f1.cuenta_XAtv.getText().toString();

        if (cuentaAlItemList == null || cuentaAlItemList.isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        f1.dateCurrent_ArrayInteger = f1.metodosVarios_Class.fechasYHoras();

        A23_QueryResult<String[]> obtenerAtributo =
                f1.a22QueryManager.queryAttributesByAccount(cuentaAlItemList);

        if (obtenerAtributo == null
                || obtenerAtributo.getAtributosCuenta() == null
                || obtenerAtributo.getAtributosCuenta().length == 0) {
            Toast.makeText(f1.getActivity(),
                    "Cuenta no encontrada: " + cuentaAlItemList, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] atributosCuenta = obtenerAtributo.getAtributosCuenta();

        // Red de seguridad: una cuenta con inventario nunca debería llegar hasta aquí (ver el
        // comentario de clase arriba) — si de algún modo ocurre (por ejemplo, el usuario
        // canceló el diálogo de inventario sin limpiar la cuenta elegida), se bloquea con un
        // mensaje claro en vez de guardar un ítem de inventario sin su artículo/unidades.
        boolean cuentaConInventario = atributosCuenta.length > 8
                && "1".equals(atributosCuenta[8]);
        if (cuentaConInventario) {
            Toast.makeText(f1.getActivity(),
                    "\"" + cuentaAlItemList + "\" maneja inventario — completa el diálogo de " +
                            "artículo/unidades que debió abrirse al elegir la cuenta.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        f1.atributosCuenta_ArrayS = atributosCuenta;
        f1.cuentaDemasRegistros_ArrayS = new String[]{cuentaAlItemList};

        // Construir ítem via DocumentCalculator
        A3_2_TipoTransaccionesGetsYSets nuevoItem = f1.calculator.construirItemRegistro(
                f1.nuevoNumeroDocEnAdicionar_String,
                f1.listaDocumento_ArrayLTT.size() + 1,
                cuentaAlItemList,
                f1.signo_XSp.getSelectedItem().toString(),
                f1.valor_XEt.getText().toString(),
                f1.descripcion_XAtv.getText().toString(),
                A99_MetodosVarios.stringFechaYHora,
                f1.DateOfDocument_Integer,
                f1.atributosCuenta_ArrayS);

        if (nuevoItem == null) {
            Toast.makeText(f1.getActivity(),
                    "Error: Atributos de cuenta no disponibles",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        agregarItemAListaYRefrescarUI(nuevoItem);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5b. agregarItemAListaYRefrescarUI
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 4: extraído de la cola de losDemasRegistrosAListaDocumento (sin
    // cambiar nada de lo que hacía ahí) para poder reusarlo también desde
    // mostrarDialogoRegistroInventario, al confirmar el diálogo de artículo/unidades/precio.
    private void agregarItemAListaYRefrescarUI(A3_2_TipoTransaccionesGetsYSets nuevoItem) {
        f1.listaDocumento_ArrayLTT.add(nuevoItem);

        try {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                    new D_F1_AdaptadorCrudDocumento(
                            f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
            f1.listaDocumento_XLv.setAdapter(
                    f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
        } catch (Exception e) {
            Log.e(TAG, "Error setting adapter in agregarItemAListaYRefrescarUI", e);
        }

        f1.sumarItemListaDocumento();
        f1.clearViewValuesAreaRecords();

        int size = f1.listaDocumento_ArrayLTT.size();
        f1.consecutivoItemRegistro_XTv.setText(
                size > 0 ? "Item:\n" + size + "/" + size : "0/0");
    }

    // ═══════════════════════════════════════════════════════════════
    // 5b-bis. actualizarItemEnListaYRefrescarUI
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — contraparte de agregarItemAListaYRefrescarUI para el modo edición del diálogo
    // de inventario (B.a, pedido de Jorge): REEMPLAZA el ítem en su misma posición de la lista
    // (.set, no .add) — el documento no gana un ítem nuevo, se modifica uno que ya estaba. No
    // toca los campos del formulario principal (clearViewValuesAreaRecords/consecutivo), porque
    // este flujo no los usa — a diferencia de "agregar nuevo", se entra aquí desde "Modificar"
    // (long-press sobre un ítem ya en la lista), no desde el formulario de registro.
    private void actualizarItemEnListaYRefrescarUI(int posicion, A3_2_TipoTransaccionesGetsYSets itemActualizado) {
        f1.listaDocumento_ArrayLTT.set(posicion, itemActualizado);

        try {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                    new D_F1_AdaptadorCrudDocumento(
                            f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
            f1.listaDocumento_XLv.setAdapter(
                    f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
        } catch (Exception e) {
            Log.e(TAG, "Error setting adapter in actualizarItemEnListaYRefrescarUI", e);
        }

        f1.sumarItemListaDocumento();
        Toast.makeText(f1.getActivity(), "✅ Registro de inventario modificado", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════
    // 5b-ter. buscarPosicionDuplicado
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — A.1 (pedido de Jorge): busca, en la lista actual del documento, otro ítem con
    // la misma cuenta + mismo artículo de inventario. "posicionAExcluir" (puede ser null) es la
    // posición del ítem que se está editando — se excluye de la búsqueda para no detectarlo
    // como duplicado de sí mismo. Devuelve el índice encontrado, o -1 si no hay duplicado.
    private int buscarPosicionDuplicado(String cuenta, long itemId, Integer posicionAExcluir) {
        for (int i = 0; i < f1.listaDocumento_ArrayLTT.size(); i++) {
            if (posicionAExcluir != null && i == posicionAExcluir) continue;
            A3_2_TipoTransaccionesGetsYSets p = f1.listaDocumento_ArrayLTT.get(i);
            Long itemIdDelItem = p.tipoTget_19ItemInventarioIdMetodoEnA5();
            if (itemIdDelItem != null
                    && itemIdDelItem == itemId
                    && cuenta.equals(p.tipoTget_3CuentaMetodoEnA5())) {
                return i;
            }
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════
    // 5c. mostrarDialogoRegistroInventario
    // ═══════════════════════════════════════════════════════════════
    // ⭐ REDISEÑO — v16 (1-oct, mockup en matriz pedido por Jorge): con el reorden de campos del
    // formulario (descripción, cuenta, valor, signo — ver RecordDocumentUnit.setupSignoSpinner())
    // este diálogo ya NO puede asumir que f1.valor_XEt/f1.signo_XSp traen algo útil: ahora se
    // abre justo al elegir la cuenta, es decir ANTES de que el usuario toque esos dos campos. El
    // diálogo pasa a ser autónomo — tiene su PROPIO "Tipo de movimiento" (Entrada/Salida), sus
    // propios "Unidades" y "Valor total" — y al presionar "Agregar" arma el ítem directamente
    // con esos datos, sin leer ni tocar f1.valor_XEt/f1.signo_XSp (así se evita además disparar
    // por programación el onItemSelected de signo_XSp, que ahora confirma el registro — ver
    // confirmarRegistroAlElegirSigno()).
    //
    // Se muestra como la matriz que pidió Jorge:
    //   columnas: Tipo de movimiento | Unidades | Valor total | Precio unitario promedio
    //   filas:    "Este movimiento" (lo que el usuario está digitando ahora, editable)
    //             "Saldo anterior"  (saldo acumulado del artículo ANTES de este movimiento)
    //             "Nuevo saldo"     (Saldo anterior + Este movimiento, suma algebraica)
    //
    // "Este movimiento" lleva signo algebraico (entrada positiva, salida negativa) para que
    // "Nuevo saldo" sea una simple suma de las dos filas de arriba.
    //
    // Entrada: el usuario digita Unidades Y Valor total; el Precio unitario se deriva (Valor
    // total ÷ Unidades), puramente informativo.
    // Salida: el usuario digita SOLO Unidades; Valor total y Precio los calcula la app a partir
    // del costo promedio ponderado vigente, vía A12_InventarioHelper.calcularCostoSalida() — la
    // MISMA fórmula (cierre exacto sin residual al agotar el saldo, y bloqueo de sobregiro) que
    // usa el guardado real (guardarTransaccionConInventario) — una sola fuente de verdad entre
    // la vista previa de este diálogo y lo que realmente se guarda.
    //
    // ⭐ REDISEÑO v16: el "Valor total" de "Este movimiento" pasa a ser lo que alimenta el campo
    // contable "valor" del ítem del documento — esto REEMPLAZA la regla de la v15 ("valor
    // siempre exacto, nunca sobrescrito"); cambio confirmado explícitamente por Jorge ("no
    // importa que lo sobrescriba").
    //
    // ⭐ REDISEÑO v16: se agrega el bloqueo de sobregiro (vender más unidades de las que hay en
    // existencia), que antes no existía en ningún lado — ver el detalle en el Javadoc de
    // A12_InventarioHelper.calcularCostoSalida().
    private void mostrarDialogoRegistroInventario(String cuentaAlItemList, String[] atributosCuenta) {
        mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta, null);
    }

    // ⭐ NUEVO — combinado A.1 (duplicados) / B.a (editar ítem de inventario ya en la lista),
    // pedido de Jorge. Punto de entrada público para abrir este mismo diálogo en MODO EDICIÓN,
    // desde SeeDocumentUnit.iniciarModificacionItemInventario() (opción "Modificar" del
    // long-press). "posicion" es el índice en f1.listaDocumento_ArrayLTT del ítem a editar —
    // quien llama YA verificó que ese ítem no está guardado en la BD todavía
    // (tipoTget_15TransaccionIdMetodoEnA5() == null), que es la condición que hace seguro
    // editarlo aquí (ver el comentario de clase completo más abajo, en el overload de 3
    // parámetros).
    public void mostrarDialogoRegistroInventarioParaEditar(
            String cuentaAlItemList, String[] atributosCuenta, int posicion) {
        mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta, posicion);
    }

    // ⭐ NUEVO — "posicionAEditar": null = modo "agregar ítem nuevo" (comportamiento de siempre,
    // sin cambios). No-null = modo "editar ítem ya en la lista" (índice en
    // f1.listaDocumento_ArrayLTT) — Jorge pidió que en este modo TODO sea editable, incluido el
    // artículo (A/B, retroalimentación "debe ser flexible"), y que "saldo anterior"/"nuevo
    // saldo" se recalculen en vivo igual que al agregar uno nuevo.
    //
    // Por qué es seguro (verificado antes de implementar, explicado a Jorge): el ítem que se
    // edita aquí TODAVÍA no tiene fila en transacciones_inventario — solo existe en la lista en
    // memoria del documento que se está armando. obtenerSaldoUnidadesYCosto()/
    // calcularCostoSalida() leen el saldo SOLO de lo ya guardado en la BD, así que nunca cuentan
    // este ítem (ni ningún otro ítem sin guardar) al recalcular — es exactamente el mismo
    // cálculo de "saldo antes de este movimiento" que ya usa el modo "agregar nuevo". El caso
    // aparte — editar un ítem de inventario que SÍ ya está guardado (documento reabierto con
    // "Editar documento") — sigue bloqueado como ya lo estaba desde antes
    // (bloqueadoPorCuentaConInventario()); quien llama a este método con posicionAEditar ya
    // verificó eso y nunca debe llamar aquí para un ítem ya guardado.
    //
    // A.1) Detección de duplicados: si el artículo elegido (en cualquiera de los dos modos) ya
    // tiene otro registro para la misma cuenta en este documento, se muestra un aviso con color
    // que resalta, se bloquea "Agregar"/"Guardar cambios", y se ofrece un botón para cerrar este
    // diálogo y abrir el existente en modo edición — ver buscarPosicionDuplicado() más abajo.
    private void mostrarDialogoRegistroInventario(
            String cuentaAlItemList, String[] atributosCuenta, Integer posicionAEditar) {
        Long cuentaId = parseLongSeguro(atributosCuenta.length > 5 ? atributosCuenta[5] : null);
        if (cuentaId == null) {
            Toast.makeText(f1.getActivity(),
                    "No se pudo determinar la cuenta para el inventario de \"" +
                            cuentaAlItemList + "\"", Toast.LENGTH_LONG).show();
            return;
        }

        // ⭐ CAMBIO — antes se capturaba una sola vez de f1.descripcion_XAtv (ya validada por
        // losDemasRegistrosAListaDocumento justo antes de llegar aquí) y se usaba tal cual al
        // confirmar. Ahora ese valor es solo el PRELLENADO inicial de un campo propio del
        // diálogo (descripcionEt, más abajo) — en modo edición no existe tal validación previa
        // (se entra directo desde "Modificar"), y Jorge pidió que la descripción también sea
        // editable aquí igual que el resto de los campos.
        final String descripcionInicial = posicionAEditar != null
                ? f1.listaDocumento_ArrayLTT.get(posicionAEditar).tipoTget_6DescripcionMetodoEnA5()
                : f1.descripcion_XAtv.getText().toString();

        List<A12_InventarioHelper.ItemInventario> items;
        SQLiteDatabase dbLectura = f1.ayudante_Class.getReadableDatabase();
        try {
            items = new A12_InventarioHelper().listarItemsActivosPorCuenta(dbLectura, cuentaId);
        } catch (Exception e) {
            Log.e(TAG, "Error listando artículos de inventario", e);
            Toast.makeText(f1.getActivity(),
                    "Error al consultar los artículos de \"" + cuentaAlItemList + "\": " +
                            e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        } finally {
            dbLectura.close();
        }

        if (items.isEmpty()) {
            new AlertDialog.Builder(f1.getActivity())
                    .setTitle("\"" + cuentaAlItemList + "\" no tiene artículos")
                    .setMessage("Esta cuenta maneja inventario pero todavía no tiene ningún " +
                            "artículo dado de alta — da de alta al menos uno para poder " +
                            "registrar transacciones aquí.")
                    .setPositiveButton("Crear artículo", (dialog, which) ->
                            mostrarDialogoNuevoArticulo(cuentaAlItemList, atributosCuenta, cuentaId))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        int paddingPx = (int) (16 * f1.getResources().getDisplayMetrics().density);
        int padCeldaPx = (int) (4 * f1.getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(f1.getActivity());
        LinearLayout contenedor = new LinearLayout(f1.getActivity());
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        scroll.addView(contenedor);

        // ⭐ NUEVO — A.1 (pedido de Jorge): aviso de duplicado, en la parte SUPERIOR del
        // diálogo, con un color que resalte (distinto del fucsia/azul rey ya usados, para que
        // no se confunda con esos). Oculto mientras no haya duplicado — ver
        // actualizarAvisoDuplicado más abajo.
        TextView avisoDuplicadoTv = new TextView(f1.getActivity());
        avisoDuplicadoTv.setBackgroundColor(Color.parseColor("#FFC107"));
        avisoDuplicadoTv.setTextColor(Color.BLACK);
        avisoDuplicadoTv.setTypeface(avisoDuplicadoTv.getTypeface(), Typeface.BOLD);
        avisoDuplicadoTv.setPadding(padCeldaPx * 3, padCeldaPx * 3, padCeldaPx * 3, padCeldaPx * 3);
        avisoDuplicadoTv.setVisibility(View.GONE);
        contenedor.addView(avisoDuplicadoTv);

        Button editarExistenteBtn = new Button(f1.getActivity());
        editarExistenteBtn.setText("Editar el registro existente");
        editarExistenteBtn.setVisibility(View.GONE);
        contenedor.addView(editarExistenteBtn);

        List<String> nombresParaSpinner = new ArrayList<>();
        for (A12_InventarioHelper.ItemInventario item : items) {
            nombresParaSpinner.add(item.nombre);
        }
        final String OPCION_CREAR_NUEVO = "+ Crear nuevo artículo…";
        nombresParaSpinner.add(OPCION_CREAR_NUEVO);

        TextView etiquetaArticulo = new TextView(f1.getActivity());
        etiquetaArticulo.setText("Artículo");
        contenedor.addView(etiquetaArticulo);

        Spinner articuloSpinner = new Spinner(f1.getActivity());
        ArrayAdapter<String> articuloAdapter = new ArrayAdapter<>(f1.getActivity(),
                android.R.layout.simple_spinner_dropdown_item, nombresParaSpinner);
        articuloSpinner.setAdapter(articuloAdapter);
        contenedor.addView(articuloSpinner);

        // ⭐ NUEVO — campo de descripción propio del diálogo (ver el comentario junto a
        // descripcionInicial, arriba): en modo "agregar nuevo" viene prellenado con lo que el
        // usuario ya escribió en el formulario (comportamiento de siempre, ahora también
        // editable aquí sin tener que cerrar el diálogo); en modo edición, con la descripción
        // que tenía el ítem.
        TextView etiquetaDescripcion = new TextView(f1.getActivity());
        etiquetaDescripcion.setText("Descripción");
        contenedor.addView(etiquetaDescripcion);

        EditText descripcionEt = new EditText(f1.getActivity());
        descripcionEt.setText(descripcionInicial);
        descripcionEt.setBackgroundColor(Color.parseColor("#F4D7F5"));
        descripcionEt.setTextColor(Color.parseColor("#425DF6"));
        contenedor.addView(descripcionEt);

        // ⭐ NUEVO v16: matriz pedida por Jorge — ver el comentario de clase arriba.
        TableLayout tabla = new TableLayout(f1.getActivity());
        tabla.setStretchAllColumns(true);
        contenedor.addView(tabla);

        TableRow filaCabecera = new TableRow(f1.getActivity());
        filaCabecera.addView(celdaTexto("", padCeldaPx, true));
        filaCabecera.addView(celdaTexto("Tipo de movimiento", padCeldaPx, true));
        filaCabecera.addView(celdaTexto("Unidades", padCeldaPx, true));
        filaCabecera.addView(celdaTexto("Valor total", padCeldaPx, true));
        filaCabecera.addView(celdaTexto("Precio prom.", padCeldaPx, true));
        tabla.addView(filaCabecera);

        TableRow filaMovimiento = new TableRow(f1.getActivity());
        filaMovimiento.addView(celdaTexto("Este movimiento", padCeldaPx, true));

        List<String> tiposMovimiento = new ArrayList<>();
        tiposMovimiento.add("Entrada");
        tiposMovimiento.add("Salida");
        Spinner tipoMovimientoSpinner = new Spinner(f1.getActivity());
        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(f1.getActivity(),
                android.R.layout.simple_spinner_dropdown_item, tiposMovimiento);
        tipoMovimientoSpinner.setAdapter(tipoAdapter);
        filaMovimiento.addView(tipoMovimientoSpinner);

        // ⭐ NUEVO (a pedido de Jorge): los campos donde el usuario digita — Unidades y Valor
        // total — en fucsia (#F4D7F5), el mismo color de fondo que ya se usa en el resto del
        // formulario para un campo editable (p.ej. valor_XEt). El resto de la matriz
        // (encabezados, etiquetas de fila y celdas calculadas) va en azul rey (#425DF6), el
        // mismo color de texto ya usado en el formulario para lo no editable.
        EditText unidadesEt = new EditText(f1.getActivity());
        unidadesEt.setHint("Unidades");
        unidadesEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        unidadesEt.setPadding(padCeldaPx, padCeldaPx, padCeldaPx, padCeldaPx);
        unidadesEt.setBackgroundColor(Color.parseColor("#F4D7F5"));
        unidadesEt.setTextColor(Color.parseColor("#425DF6"));
        filaMovimiento.addView(unidadesEt);

        // Entrada: el usuario la digita. Salida: queda deshabilitada — la calcula la app (ver
        // actualizarMatriz más abajo) a partir del costo promedio ponderado vigente.
        EditText valorTotalEt = new EditText(f1.getActivity());
        valorTotalEt.setHint("Valor total");
        valorTotalEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        valorTotalEt.setPadding(padCeldaPx, padCeldaPx, padCeldaPx, padCeldaPx);
        valorTotalEt.setBackgroundColor(Color.parseColor("#F4D7F5"));
        valorTotalEt.setTextColor(Color.parseColor("#425DF6"));
        filaMovimiento.addView(valorTotalEt);

        TextView precioMovimientoTv = celdaTexto("—", padCeldaPx, false);
        filaMovimiento.addView(precioMovimientoTv);
        tabla.addView(filaMovimiento);

        TableRow filaSaldoAnterior = new TableRow(f1.getActivity());
        filaSaldoAnterior.addView(celdaTexto("Saldo anterior", padCeldaPx, true));
        filaSaldoAnterior.addView(celdaTexto("—", padCeldaPx, false));
        TextView saldoAntUnidadesTv = celdaTexto("—", padCeldaPx, false);
        TextView saldoAntValorTv = celdaTexto("—", padCeldaPx, false);
        TextView saldoAntPrecioTv = celdaTexto("—", padCeldaPx, false);
        filaSaldoAnterior.addView(saldoAntUnidadesTv);
        filaSaldoAnterior.addView(saldoAntValorTv);
        filaSaldoAnterior.addView(saldoAntPrecioTv);
        tabla.addView(filaSaldoAnterior);

        TableRow filaNuevoSaldo = new TableRow(f1.getActivity());
        filaNuevoSaldo.addView(celdaTexto("Nuevo saldo", padCeldaPx, true));
        filaNuevoSaldo.addView(celdaTexto("—", padCeldaPx, false));
        TextView nuevoSaldoUnidadesTv = celdaTexto("—", padCeldaPx, false);
        TextView nuevoSaldoValorTv = celdaTexto("—", padCeldaPx, false);
        TextView nuevoSaldoPrecioTv = celdaTexto("—", padCeldaPx, false);
        filaNuevoSaldo.addView(nuevoSaldoUnidadesTv);
        filaNuevoSaldo.addView(nuevoSaldoValorTv);
        filaNuevoSaldo.addView(nuevoSaldoPrecioTv);
        tabla.addView(filaNuevoSaldo);

        // Mensaje de error en vivo (p.ej. sobregiro) — oculto mientras no haya ningún problema;
        // también se vuelve a validar todo al presionar "Agregar" (ver el positive button).
        TextView errorTv = new TextView(f1.getActivity());
        errorTv.setTextColor(Color.RED);
        errorTv.setVisibility(View.GONE);
        contenedor.addView(errorTv);

        SQLiteDatabase dbParaCalculos = f1.ayudante_Class.getReadableDatabase();
        A12_InventarioHelper inventarioHelper = new A12_InventarioHelper();

        // ⭐ Bandera de guardia: en Salida, actualizarMatriz hace valorTotalEt.setText(...) de
        // forma programática (para mostrar el valor calculado) — sin esta bandera, ese setText
        // volvería a disparar el TextWatcher de valorTotalEt y causaría una recursión infinita
        // (setText → onTextChanged → actualizarMatriz → setText → ...).
        final boolean[] actualizandoProgramaticamente = {false};

        // ⭐ Recalcula toda la matriz en vivo — se dispara al cambiar artículo, tipo de
        // movimiento, unidades o valor total. Usa SIEMPRE calcularCostoSalida/
        // obtenerSaldoUnidadesYCosto (las mismas que el guardado real), nunca una copia propia
        // de la fórmula.
        Runnable actualizarMatriz = () -> {
            actualizandoProgramaticamente[0] = true;
            try {
                errorTv.setVisibility(View.GONE);

                int posicionArticulo = articuloSpinner.getSelectedItemPosition();
                if (posicionArticulo < 0 || posicionArticulo >= items.size()) {
                    saldoAntUnidadesTv.setText("—");
                    saldoAntValorTv.setText("—");
                    saldoAntPrecioTv.setText("—");
                    precioMovimientoTv.setText("—");
                    nuevoSaldoUnidadesTv.setText("—");
                    nuevoSaldoValorTv.setText("—");
                    nuevoSaldoPrecioTv.setText("—");
                    return;
                }

                long itemId = items.get(posicionArticulo).itemId;
                long[] saldoAntes = inventarioHelper.obtenerSaldoUnidadesYCosto(dbParaCalculos, itemId);
                long saldoAntUnidades = saldoAntes[0];
                long saldoAntValor = saldoAntes[1];

                saldoAntUnidadesTv.setText(String.valueOf(saldoAntUnidades));
                saldoAntValorTv.setText(String.valueOf(saldoAntValor));
                saldoAntPrecioTv.setText(saldoAntUnidades > 0
                        ? formatearPrecioInformativo((double) saldoAntValor / (double) saldoAntUnidades)
                        : "—");

                boolean esSalida = tipoMovimientoSpinner.getSelectedItemPosition() == 1;
                valorTotalEt.setEnabled(!esSalida);

                String unidadesTexto = unidadesEt.getText().toString().trim();
                long unidades;
                try {
                    unidades = unidadesTexto.isEmpty() ? 0 : Long.parseLong(unidadesTexto);
                } catch (NumberFormatException e) {
                    unidades = 0;
                }

                if (unidades <= 0) {
                    if (esSalida) valorTotalEt.setText("");
                    precioMovimientoTv.setText("—");
                    nuevoSaldoUnidadesTv.setText(String.valueOf(saldoAntUnidades));
                    nuevoSaldoValorTv.setText(String.valueOf(saldoAntValor));
                    nuevoSaldoPrecioTv.setText(saldoAntPrecioTv.getText());
                    return;
                }

                long movUnidadesConSigno;
                long movValorConSigno;

                if (esSalida) {
                    try {
                        long costoSalida = inventarioHelper.calcularCostoSalida(dbParaCalculos, itemId, unidades);
                        movUnidadesConSigno = -unidades;
                        movValorConSigno = -costoSalida;
                        valorTotalEt.setText("-" + costoSalida);
                        precioMovimientoTv.setText(
                                formatearPrecioInformativo((double) costoSalida / (double) unidades));
                    } catch (IllegalStateException e) {
                        valorTotalEt.setText("");
                        precioMovimientoTv.setText("—");
                        nuevoSaldoUnidadesTv.setText(String.valueOf(saldoAntUnidades));
                        nuevoSaldoValorTv.setText(String.valueOf(saldoAntValor));
                        nuevoSaldoPrecioTv.setText(saldoAntPrecioTv.getText());
                        errorTv.setText(e.getMessage());
                        errorTv.setVisibility(View.VISIBLE);
                        return;
                    }
                } else {
                    String valorTexto = valorTotalEt.getText().toString().trim();
                    long valorTotal;
                    try {
                        valorTotal = valorTexto.isEmpty() ? 0 : Long.parseLong(valorTexto);
                    } catch (NumberFormatException e) {
                        valorTotal = 0;
                    }
                    if (valorTotal <= 0) {
                        precioMovimientoTv.setText("—");
                        nuevoSaldoUnidadesTv.setText(String.valueOf(saldoAntUnidades));
                        nuevoSaldoValorTv.setText(String.valueOf(saldoAntValor));
                        nuevoSaldoPrecioTv.setText(saldoAntPrecioTv.getText());
                        return;
                    }
                    movUnidadesConSigno = unidades;
                    movValorConSigno = valorTotal;
                    precioMovimientoTv.setText(
                            formatearPrecioInformativo((double) valorTotal / (double) unidades));
                }

                long nuevoSaldoUnidades = saldoAntUnidades + movUnidadesConSigno;
                long nuevoSaldoValor = saldoAntValor + movValorConSigno;
                nuevoSaldoUnidadesTv.setText(String.valueOf(nuevoSaldoUnidades));
                nuevoSaldoValorTv.setText(String.valueOf(nuevoSaldoValor));
                nuevoSaldoPrecioTv.setText(nuevoSaldoUnidades > 0
                        ? formatearPrecioInformativo((double) nuevoSaldoValor / (double) nuevoSaldoUnidades)
                        : "—");
            } finally {
                actualizandoProgramaticamente[0] = false;
            }
        };

        // ⭐ NUEVO — modo edición: prellenar artículo/tipo de movimiento/unidades/valor total
        // con lo que ya tenía el ítem, ANTES de la primera corrida de actualizarMatriz (que ya
        // recalcula saldo anterior/nuevo saldo con lo prellenado) y antes de registrar los
        // listeners (para no disparar un recálculo a medias con solo parte de los campos ya
        // puestos).
        if (posicionAEditar != null) {
            A3_2_TipoTransaccionesGetsYSets itemExistente =
                    f1.listaDocumento_ArrayLTT.get(posicionAEditar);
            long itemIdExistente = itemExistente.tipoTget_19ItemInventarioIdMetodoEnA5();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).itemId == itemIdExistente) {
                    articuloSpinner.setSelection(i);
                    break;
                }
            }
            long unidadesConSignoExistente = itemExistente.tipoTget_20UnidadesInventarioMetodoEnA5();
            boolean esSalidaExistente = unidadesConSignoExistente < 0;
            tipoMovimientoSpinner.setSelection(esSalidaExistente ? 1 : 0);
            unidadesEt.setText(String.valueOf(Math.abs(unidadesConSignoExistente)));
            if (!esSalidaExistente) {
                // Salida: valorTotalEt la calcula actualizarMatriz (queda deshabilitada) — no
                // hace falta (ni conviene) prellenarla a mano.
                valorTotalEt.setText(String.valueOf(Math.abs(itemExistente.tipoTget_5ValorMetodoEnA5())));
            }
        }

        actualizarMatriz.run();

        // ⭐ NUEVO — A.1 (pedido de Jorge): detección de duplicados — misma cuenta+artículo ya
        // registrado en este documento. En modo edición, el ítem que se está editando se
        // excluye de la búsqueda (no tiene sentido que se alerte contra sí mismo).
        final int[] posicionDuplicadaActual = {-1};
        Runnable actualizarAvisoDuplicado = () -> {
            int posicionArticulo = articuloSpinner.getSelectedItemPosition();
            if (posicionArticulo < 0 || posicionArticulo >= items.size()) {
                posicionDuplicadaActual[0] = -1;
                avisoDuplicadoTv.setVisibility(View.GONE);
                editarExistenteBtn.setVisibility(View.GONE);
                return;
            }
            long itemIdSeleccionado = items.get(posicionArticulo).itemId;
            posicionDuplicadaActual[0] = buscarPosicionDuplicado(
                    cuentaAlItemList, itemIdSeleccionado, posicionAEditar);
            if (posicionDuplicadaActual[0] >= 0) {
                avisoDuplicadoTv.setText("Ya existe un registro de \"" +
                        items.get(posicionArticulo).nombre + "\" para \"" + cuentaAlItemList +
                        "\" en este documento — no se puede guardar otro (el promedio no " +
                        "quedaría bien). Usa el botón de abajo para editar el que ya existe.");
                avisoDuplicadoTv.setVisibility(View.VISIBLE);
                editarExistenteBtn.setVisibility(View.VISIBLE);
            } else {
                avisoDuplicadoTv.setVisibility(View.GONE);
                editarExistenteBtn.setVisibility(View.GONE);
            }
        };
        actualizarAvisoDuplicado.run();

        TextWatcher recalcularAlEscribir = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (actualizandoProgramaticamente[0]) return;
                actualizarMatriz.run();
            }
            @Override public void afterTextChanged(Editable e) {}
        };
        unidadesEt.addTextChangedListener(recalcularAlEscribir);
        valorTotalEt.addTextChangedListener(recalcularAlEscribir);

        articuloSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                actualizarMatriz.run();
                actualizarAvisoDuplicado.run();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        tipoMovimientoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                actualizarMatriz.run();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ⭐ NUEVO — título y texto del botón cambian en modo edición, para que quede claro que
        // se está modificando un ítem ya en la lista y no agregando uno nuevo.
        String tituloDialogo = posicionAEditar != null
                ? "Editar inventario — \"" + cuentaAlItemList + "\""
                : "Inventario — \"" + cuentaAlItemList + "\"";
        String textoBotonPositivo = posicionAEditar != null ? "Guardar cambios" : "Agregar";

        final AlertDialog[] dialogoHolder = new AlertDialog[1];
        editarExistenteBtn.setOnClickListener(v -> {
            int posicionExistente = posicionDuplicadaActual[0];
            if (posicionExistente < 0) return;
            if (dialogoHolder[0] != null) dialogoHolder[0].dismiss();
            mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta, posicionExistente);
        });

        AlertDialog dialogo = new AlertDialog.Builder(f1.getActivity())
                .setTitle(tituloDialogo)
                .setView(scroll)
                .setCancelable(true)
                .setOnDismissListener(d -> dbParaCalculos.close())
                .setPositiveButton(textoBotonPositivo, null) // se sobreescribe abajo para no cerrar en error
                .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                .create();
        dialogoHolder[0] = dialogo;

        dialogo.setOnShowListener(dialogInterface -> dialogo
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int posicionSeleccionada = articuloSpinner.getSelectedItemPosition();

                    if (posicionSeleccionada == items.size()) {
                        // "+ Crear nuevo artículo…" — se abre el sub-diálogo y se cierra este;
                        // al terminar de crear el artículo se vuelve a abrir este mismo diálogo
                        // (ya con el artículo nuevo en la lista), para no duplicar la lógica de
                        // arriba.
                        dialogo.dismiss();
                        mostrarDialogoNuevoArticulo(cuentaAlItemList, atributosCuenta, cuentaId);
                        return;
                    }

                    // ⭐ A.2) Se vuelve a verificar el duplicado aquí, fresco (mismo criterio que
                    // el resto de las validaciones de este botón), en vez de confiar solo en el
                    // aviso en vivo — bloquea el guardado si ya existe un registro para la misma
                    // cuenta+artículo en este documento.
                    long itemIdARevisar = items.get(posicionSeleccionada).itemId;
                    if (buscarPosicionDuplicado(cuentaAlItemList, itemIdARevisar, posicionAEditar) >= 0) {
                        Toast.makeText(f1.getActivity(),
                                "Ya existe un registro para este artículo en el documento — " +
                                        "no se puede guardar otro. Usa \"Editar el registro " +
                                        "existente\".",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String descripcionDelDialogo = descripcionEt.getText().toString().trim();
                    if (descripcionDelDialogo.isEmpty()) {
                        Toast.makeText(f1.getActivity(), "Falta la descripcion",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String unidadesTexto = unidadesEt.getText().toString().trim();
                    if (unidadesTexto.isEmpty()) {
                        Toast.makeText(f1.getActivity(), "Falta la cantidad de unidades",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long unidadesMagnitud;
                    try {
                        unidadesMagnitud = Long.parseLong(unidadesTexto);
                    } catch (NumberFormatException e) {
                        Toast.makeText(f1.getActivity(), "Unidades inválidas",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (unidadesMagnitud <= 0) {
                        Toast.makeText(f1.getActivity(), "Las unidades deben ser mayores que 0",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    A12_InventarioHelper.ItemInventario itemElegido = items.get(posicionSeleccionada);
                    boolean esSalida = tipoMovimientoSpinner.getSelectedItemPosition() == 1;
                    String signoDelDialogo = esSalida ? "-" : "+";

                    // ⭐ Se recalcula de nuevo aquí, fresco, en vez de confiar en lo que mostró la
                    // última recalculación en vivo — misma fórmula/única fuente de verdad,
                    // incluido el bloqueo de sobregiro (calcularCostoSalida).
                    long valorTotalMagnitud;
                    if (esSalida) {
                        try {
                            valorTotalMagnitud = inventarioHelper.calcularCostoSalida(
                                    dbParaCalculos, itemElegido.itemId, unidadesMagnitud);
                        } catch (IllegalStateException e) {
                            Toast.makeText(f1.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } else {
                        String valorTexto = valorTotalEt.getText().toString().trim();
                        if (valorTexto.isEmpty()) {
                            Toast.makeText(f1.getActivity(), "Falta el valor total de la entrada",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            valorTotalMagnitud = Math.abs(Long.parseLong(valorTexto));
                        } catch (NumberFormatException e) {
                            Toast.makeText(f1.getActivity(), "Valor total inválido",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (valorTotalMagnitud <= 0) {
                            Toast.makeText(f1.getActivity(),
                                    "El valor total de la entrada debe ser mayor que 0",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    double precioInformativoDouble =
                            (double) valorTotalMagnitud / (double) unidadesMagnitud;

                    // ⭐ REDISEÑO v16: el valor contable del ítem ya NO viene de f1.valor_XEt (que
                    // a esta altura del nuevo orden ni se ha tocado) — viene del "Valor total" de
                    // "Este movimiento", calculado/digitado en este mismo diálogo. El signo
                    // también lo determina este diálogo (su propio Tipo de movimiento), nunca el
                    // signo_XSp del formulario.
                    // ⭐ NUEVO — modo edición: se conserva el número de ítem ORIGINAL del
                    // documento (tipoT_2DocumentItems_String del ítem que se está editando) en
                    // vez de calcular uno nuevo al final de la lista — este ítem no se está
                    // agregando, se está reemplazando en su misma posición.
                    int numeroItemEnDocumento = posicionAEditar != null
                            ? Integer.parseInt(f1.listaDocumento_ArrayLTT.get(posicionAEditar)
                                    .tipoTget_2ItemDocMetodoEnA5())
                            : f1.listaDocumento_ArrayLTT.size() + 1;

                    A3_2_TipoTransaccionesGetsYSets nuevoItem = f1.calculator.construirItemRegistro(
                            f1.nuevoNumeroDocEnAdicionar_String,
                            numeroItemEnDocumento,
                            cuentaAlItemList,
                            signoDelDialogo,
                            String.valueOf(valorTotalMagnitud),
                            descripcionDelDialogo,
                            A99_MetodosVarios.stringFechaYHora,
                            f1.DateOfDocument_Integer,
                            atributosCuenta);

                    if (nuevoItem == null) {
                        Toast.makeText(f1.getActivity(),
                                "Error: Atributos de cuenta no disponibles", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long unidadesConSigno = esSalida ? -unidadesMagnitud : unidadesMagnitud;
                    nuevoItem.tipoTset_19ItemInventarioIdMetodoEnA5(itemElegido.itemId);
                    nuevoItem.tipoTset_20UnidadesInventarioMetodoEnA5(unidadesConSigno);
                    nuevoItem.tipoTset_21PrecioUnitarioInventarioMetodoEnA5(precioInformativoDouble);

                    if (posicionAEditar != null) {
                        actualizarItemEnListaYRefrescarUI(posicionAEditar, nuevoItem);
                    } else {
                        agregarItemAListaYRefrescarUI(nuevoItem);
                    }
                    dialogo.dismiss();
                }));

        dialogo.show();
    }

    // ⭐ NUEVO v16: celda reutilizable de la matriz del diálogo de inventario — una TextView con
    // el mismo padding/estilo en todas las celdas, para no repetir la construcción.
    private TextView celdaTexto(String texto, int paddingPx, boolean negrita) {
        TextView tv = new TextView(f1.getActivity());
        tv.setText(texto);
        tv.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        // ⭐ NUEVO (a pedido de Jorge): azul rey, el mismo color de texto ya usado en el
        // formulario para lo que no es editable (ver el comentario junto a unidadesEt/
        // valorTotalEt, que sí llevan fucsia).
        tv.setTextColor(Color.parseColor("#425DF6"));
        if (negrita) {
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        }
        return tv;
    }

    // ⭐ NUEVO v15: formatea un precio unitario informativo (double, hasta 3 decimales) para
    // mostrarlo en pantalla — sin ceros de relleno. Si el valor es un entero exacto (p.ej.
    // 8.0), se muestra "8"; si no, se muestra con hasta 3 decimales sin ceros sobrantes al
    // final (p.ej. 7.7, no "7.700"; 7.333, no "7.3330"). Se usa Locale.US al formatear para
    // evitar que un locale con coma decimal (como es-CO) produzca "7,7" en vez de "7.7".
    private static String formatearPrecioInformativo(double valor) {
        if (valor == Math.rint(valor)) {
            return String.valueOf((long) valor);
        }
        String s = String.format(java.util.Locale.US, "%.3f", valor);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ═══════════════════════════════════════════════════════════════
    // 5d. mostrarDialogoNuevoArticulo
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 4: crear un artículo nuevo "en el momento" desde el selector del
    // registro de transacciones (documento de especificación, sección 4: "con opción de crear
    // uno nuevo en el momento"). Mismo diálogo simple que F2_Cuentas.mostrarDialogoPrimerArticulo
    // (nombre obligatorio, unidad opcional). Al guardar, se vuelve a abrir
    // mostrarDialogoRegistroInventario para que el usuario complete unidades/precio con el
    // artículo recién creado ya disponible en el selector — evita duplicar esa lógica aquí.
    private void mostrarDialogoNuevoArticulo(String cuentaAlItemList, String[] atributosCuenta, long cuentaId) {
        int paddingPx = (int) (16 * f1.getResources().getDisplayMetrics().density);
        LinearLayout contenedor = new LinearLayout(f1.getActivity());
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        final EditText nombreArticuloEt = new EditText(f1.getActivity());
        nombreArticuloEt.setHint("Nombre del artículo (obligatorio)");
        contenedor.addView(nombreArticuloEt);

        final EditText unidadArticuloEt = new EditText(f1.getActivity());
        unidadArticuloEt.setHint("Unidad (opcional)");
        contenedor.addView(unidadArticuloEt);

        new AlertDialog.Builder(f1.getActivity())
                .setTitle("Nuevo artículo de \"" + cuentaAlItemList + "\"")
                .setView(contenedor)
                .setCancelable(true)
                .setPositiveButton("Guardar artículo", (dialog, which) -> {
                    String nombre = nombreArticuloEt.getText().toString().trim();
                    if (nombre.isEmpty()) {
                        Toast.makeText(f1.getActivity(), "Falta el nombre del artículo",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String unidad = unidadArticuloEt.getText().toString().trim();
                    SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
                    try {
                        new A12_InventarioHelper().insertarItemInventario(
                                db, cuentaId, nombre, unidad.isEmpty() ? null : unidad);
                        Toast.makeText(f1.getActivity(), "Artículo \"" + nombre + "\" guardado",
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error al guardar artículo nuevo de inventario", e);
                        Toast.makeText(f1.getActivity(),
                                "Error al guardar el artículo: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    } finally {
                        db.close();
                    }
                    mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta);
                })
                .setNegativeButton("Cancelar", (dialog, which) ->
                        mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta))
                .show();
    }

    private Long parseLongSeguro(String valor) {
        if (valor == null || valor.isEmpty()) return null;
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. guardarModificacion
    // ═══════════════════════════════════════════════════════════════
    public void guardarModificacion() {
        if (f1.consecutivoRegistroAModificar_StringStatic == null
                || f1.consecutivoRegistroAModificar_StringStatic.isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Error: No hay registro seleccionado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.valor_XEt.getText().toString().trim().isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Por favor ingresa un valor", Toast.LENGTH_SHORT).show();
            f1.valor_XEt.requestFocus();
            return;
        }
        if (f1.cuenta_XSp.getSelectedItem() == null
                || f1.cuenta_XSp.getSelectedItem().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Por favor selecciona una cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        // Buscar posición del registro a modificar
        int posicion = -1;
        for (int i = 0; i < f1.listaDocumento_ArrayLTT.size(); i++) {
            if (f1.listaDocumento_ArrayLTT.get(i).tipoT_2DocumentItems_String
                    .equals(f1.consecutivoRegistroAModificar_StringStatic)) {
                posicion = i;
                break;
            }
        }
        if (posicion == -1) {
            Toast.makeText(f1.getActivity(),
                    "Error: Registro no encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        // ⭐ NUEVO — v12 tanda 4: este método reasigna cuenta/valor/signo/descripción a mano,
        // sin pasar por mostrarDialogoRegistroInventario — así que no sabe actualizar
        // item_id/unidades/precio_unitario si el ítem es de inventario (podría, por ejemplo,
        // cambiarle la cuenta y dejarle el item_id de la cuenta VIEJA). Se bloquea con un
        // mensaje claro en vez de arriesgar esa inconsistencia; para cambiar un ítem de
        // inventario, se elimina de la lista y se vuelve a agregar desde el diálogo.
        if (f1.listaDocumento_ArrayLTT.get(posicion).tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
            Toast.makeText(f1.getActivity(),
                    "Este ítem es de una cuenta con inventario — no se puede editar así. " +
                            "Elimínalo de la lista y agrégalo de nuevo desde el diálogo de " +
                            "inventario.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Integer valorNuevo = Integer.parseInt(
                    f1.valor_XEt.getText().toString().trim());
            String signoNuevo = f1.signo_XSp.getSelectedItem().toString();
            if ("-".equals(signoNuevo)) valorNuevo = valorNuevo * -1;

            String cuentaNueva = f1.cuenta_XSp.getSelectedItem().toString();

            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_5Value_Integer   = valorNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_4Sign_String      = signoNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_3Accout_String    = cuentaNueva;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_6Description_String =
                    f1.descripcion_XAtv.getText().toString().trim();

            // Refrescar la clasificación para la cuenta NUEVA — si no se hace,
            // el registro se queda con la clasificación de la cuenta
            // ORIGINAL con la que se creó, y con el tiempo una misma cuenta
            // termina con transacciones clasificadas de forma inconsistente
            // (causa raíz de la duplicación vista en el informe de cuentas).
            // ⭐ NUEVO v11 — Fase 6 (parte C): ya no se refresca Grupo1/Grupo2 con el valor
            // real de la cuenta nueva (atributos[2]/[3]) — se deja en "" igual que en un
            // ítem recién creado (ver B11_DocumentCalculator). El refresco que de verdad
            // importa para la Auditoría de Clasificación es el de tipo_cuenta_id, justo
            // abajo, que no se toca.
            A23_QueryResult<String[]> atributosCuentaNueva =
                    f1.a22QueryManager.queryAttributesByAccount(cuentaNueva);
            if (atributosCuentaNueva != null
                    && atributosCuentaNueva.getAtributosCuenta() != null
                    && atributosCuentaNueva.getAtributosCuenta().length >= 4) {
                String[] atributos = atributosCuentaNueva.getAtributosCuenta();
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_10Grupo1MetodoEnA5("");
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_11Grupo2MetodoEnA5("");

                // ⭐ NUEVO v11 — Fase 6 (parte C): mismo refresco de arriba, pero para
                // tipo_cuenta_id (índice 7, disponible desde la v10). Sin esto, el ítem editado
                // se reinsertaría (ver el comentario en el INSERT de
                // baseParaGuardarEnLaEnBDConListaDocumento) con la foto de tipo_cuenta_id de la
                // cuenta ORIGINAL con la que se creó, en vez de la cuenta nueva elegida en esta
                // edición — mismo riesgo que el de Grupo1/Grupo2 de arriba.
                Long tipoCuentaIdNuevo = null;
                if (atributos.length >= 8 && atributos[7] != null && !atributos[7].isEmpty()) {
                    try {
                        tipoCuentaIdNuevo = Long.valueOf(atributos[7]);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "tipo_cuenta_id no numérico en atributos[7]: " + atributos[7]);
                    }
                }
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_16TipoCuentaIdMetodoEnA5(tipoCuentaIdNuevo);
            }

            f1.valor_XEt.setText("");
            f1.descripcion_XAtv.setText("");
            f1.signo_XSp.setSelection(0);
            f1.cuenta_XSp.setSelection(0);
            f1.consecutivoItemRegistro_XTv.setText("0/0");
            f1.consecutivoRegistroAModificar_StringStatic = "";

            f1.finalizarModoModificacion();
            f1.procesarActualizacionCompleta();

            // La cuenta pudo haber cambiado (o su Grupo1/Grupo2 recién se
            // refrescó arriba) — es el punto más probable donde una
            // transacción desalineada se corrige, así que el badge del
            // botón ✔️ de Auditoría debe reflejarlo de inmediato, sin
            // esperar a que el usuario salga y vuelva a entrar.
            f1.actualizarBadgeAuditoria();

            Toast.makeText(f1.getActivity(),
                    "✅ Registro modificado exitosamente", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(f1.getActivity(),
                    "Error: El valor debe ser un número válido", Toast.LENGTH_SHORT).show();
            f1.valor_XEt.requestFocus();
        } catch (Exception e) {
            Toast.makeText(f1.getActivity(),
                    "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error en guardarModificacion", e);
        }
    }
}