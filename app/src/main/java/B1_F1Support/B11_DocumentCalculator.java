package B1_F1Support;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;

/**
 * DocumentCalculator — cálculo puro para F1_CrudDocumento.
 *
 * REGLA: esta clase NO importa ninguna vista Android (TextView, Spinner, etc.).
 * Recibe datos como parámetros y retorna resultados como objetos/primitivos.
 * El fragment es responsable de leer las vistas ANTES de llamar estos métodos
 * y de pintar los resultados DESPUÉS de recibirlos.
 *
 * Esto la hace apta para ViewModel en el futuro si se decide.
 */
public class B11_DocumentCalculator {

    private static final String TAG = "DocumentCalculator";

    // ─────────────────────────────────────────────────────────────
    // Clase interna: resultado de sumarItems()
    // El fragment lee esto y lo pinta en los TextViews correspondientes
    // ─────────────────────────────────────────────────────────────
    public static class Sumas {
        public int mTP  = 0;   // total positivos (todos los items)
        public int mTN  = 0;   // total negativos (todos los items)
        public int mCP  = 0;   // positivos de la cuenta conciliable
        public int mCN  = 0;   // negativos de la cuenta conciliable
        public int netoT        = 0;   // neto total del documento
        public int netoMC       = 0;   // neto de la cuenta conciliable
        public int netoMTSinMC  = 0;   // neto del documento sin la cuenta conciliable
        public int netoTSinCaja = 0;   // neto sin movimientos de caja
    }

    // ─────────────────────────────────────────────────────────────
    // Constructor — solo Context para Logs (sin vistas)
    // ─────────────────────────────────────────────────────────────
    private final Context ctx;

    public B11_DocumentCalculator(Context ctx) {
        this.ctx = ctx;
    }

    // ═════════════════════════════════════════════════════════════
    // 1. sumarItems — reemplaza sumarItemListaDocumento()
    //
    // ANTES en el fragment:
    //   cuentaAConciliar_String = cuentaConciliacion_XSp.getSelectedItem().toString();
    //   → leía el spinner directamente
    //
    // AHORA: el fragment lee el spinner y pasa el String aquí.
    //   Sumas s = calculator.sumarItems(listaDocumento_ArrayLTT, cuentaAConciliar_String);
    // ═════════════════════════════════════════════════════════════
    public Sumas sumarItems(List<A3_2_TipoTransaccionesGetsYSets> lista,
                            String cuentaAConciliar) {
        Sumas s = new Sumas();
        if (lista == null) return s;

        try {
            for (A3_2_TipoTransaccionesGetsYSets item : lista) {
                int    valor  = item.tipoT_5Value_Integer;
                String signo  = item.tipoT_4Sign_String;
                String cuenta = item.tipoT_3Accout_String;
                boolean esConciliable = cuenta != null
                        && cuenta.equals(cuentaAConciliar);

                if ("+".equals(signo)) s.mTP += valor;
                if ("-".equals(signo)) s.mTN += valor;
                if (esConciliable && "+".equals(signo)) s.mCP += valor;
                if (esConciliable && "-".equals(signo)) s.mCN += valor;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sumando items de lista", e);
        }

        s.netoT        = s.mTP + s.mTN;
        s.netoMC       = s.mCP + s.mCN;
        s.netoMTSinMC  = s.netoT + s.netoMC;
        s.netoTSinCaja = s.netoT + s.mCP - s.mCN;
        return s;
    }

    // ═════════════════════════════════════════════════════════════
    // 2. calcularDiferencia — parte pura de digitarFisicoVsSaldoConciliacion()
    //
    // ANTES en el fragment: leía inputPhysicalVsAccounting_XEt y cuentaConciliacion_XSp.
    // AHORA: recibe los valores ya leídos por el fragment.
    //
    // Retorna un objeto DiferenciaResult con todos los valores calculados.
    // El fragment pinta diferenciaAConciliar_XTv, saldoCuentaAconciliar_XTv, etc.
    // ═════════════════════════════════════════════════════════════
    public static class DiferenciaResult {
        public int valorAc              = 0;   // valor físico ingresado
        public int sAc                  = 0;   // saldo en BD de la cuenta conciliable
        public int dAc                  = 0;   // diferencia saldo-físico
        public int diferenciaAConciliar = 0;
        public int adicionesAc          = 0;
        public int quedaPorRegistrar    = 0;
        public boolean cuentaVacia      = false; // true si el spinner está vacío
    }

    /**
     * @param strFisico          getText() del inputPhysicalVsAccounting_XEt
     * @param cuentaSeleccionada getSelectedItem() del cuentaConciliacion_XSp (puede ser "")
     * @param sAcActual          $sAc actual del fragment (suma BD de esa cuenta)
     * @param sumas              resultado previo de sumarItems()
     */
    public DiferenciaResult calcularDiferencia(String strFisico,
                                               String cuentaSeleccionada,
                                               int sAcActual,
                                               Sumas sumas) {
        DiferenciaResult r = new DiferenciaResult();
        try {
            r.valorAc = (strFisico == null || strFisico.isEmpty())
                    ? 0 : Integer.parseInt(strFisico);

            if (r.valorAc != 0) {
                r.diferenciaAConciliar = sAcActual - r.valorAc;
            }

            r.cuentaVacia = (cuentaSeleccionada == null || cuentaSeleccionada.isEmpty());
            r.sAc = r.cuentaVacia ? 0 : sAcActual;
            r.dAc = r.sAc - r.valorAc;

            if (sumas.mTP == 0 && sumas.mTN == 0) {
                r.quedaPorRegistrar = r.dAc;
            } else {
                r.quedaPorRegistrar = r.sAc - r.valorAc
                        - (sumas.mTP - sumas.mCP)
                        + (sumas.mTN * (-1) - sumas.mCN * (-1));
            }

            r.adicionesAc = sumas.netoMC + r.dAc;

        } catch (Exception e) {
            Log.e(TAG, "Error en calcularDiferencia", e);
        }
        return r;
    }

    // ═════════════════════════════════════════════════════════════
    // 3. siguienteNumeroDoc — parte pura de numerarDocumentoConsecutivo()
    //
    // ANTES en el fragment: escribía en numeroConsecutivoDoc_XTv directamente.
    // AHORA: retorna el String; el fragment lo pinta en los TextViews.
    //
    // Retorna "0001" si ultimoDoc es null/vacío/inválido.
    // ═════════════════════════════════════════════════════════════
    public String siguienteNumeroDoc(String ultimoDoc) {
        if (ultimoDoc == null || ultimoDoc.isEmpty()) {
            return "0001";
        }
        try {
            int siguiente = Integer.parseInt(ultimoDoc) + 1;
            return String.format("%04d", siguiente);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Formato de documento inválido: " + ultimoDoc, e);
            return "0001";
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 4. extraerCuentasUnicas — parte pura de pasarItemListaTodoResumidoAItemListaRevision()
    //
    // ANTES en el fragment: construía la lista Y asignaba el adapter al spinner.
    // AHORA: solo construye y retorna la lista.
    // El fragment toma la lista, crea el adapter y asigna al spinner.
    // ═════════════════════════════════════════════════════════════
    public ArrayList<String> extraerCuentasUnicas(List<A3_2_TipoTransaccionesGetsYSets> lista) {
        ArrayList<String> resultado = new ArrayList<>();
        resultado.add(""); // ítem vacío en posición 0
        if (lista == null) return resultado;

        for (A3_2_TipoTransaccionesGetsYSets item : lista) {
            String cuenta = item.tipoT_3Accout_String;
            if (cuenta != null && !resultado.contains(cuenta)) {
                resultado.add(cuenta);
            }
        }
        return resultado;
    }

    // ═════════════════════════════════════════════════════════════
    // 5. sumarCuentaRevision — parte pura de sumarItemListaSaldosCuenta()
    //
    // ANTES en el fragment: leía listaCuentasDeRevision_XSp y escribía en
    //   movimientoListaCuentasRevisar_XTv directamente.
    // AHORA: recibe la cuenta como String, retorna el entero.
    // El fragment pinta el TextView.
    // ═════════════════════════════════════════════════════════════
    public int sumarCuentaRevision(List<A3_2_TipoTransaccionesGetsYSets> lista,
                                   String cuentaRevision) {
        int suma = 0;
        if (lista == null || cuentaRevision == null) return suma;
        try {
            for (A3_2_TipoTransaccionesGetsYSets item : lista) {
                if (cuentaRevision.equals(item.tipoT_3Accout_String)) {
                    suma += item.tipoT_5Value_Integer;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sumando saldos cuenta revisión", e);
        }
        return suma;
    }

    // ═════════════════════════════════════════════════════════════
    // 6. calcularMovimientosOriginales — extrae calcularMovimientosOriginalesDelDocumento()
    //
    // Retorna el HashMap en lugar de escribirlo en el campo del fragment.
    // ═════════════════════════════════════════════════════════════
    public java.util.HashMap<String, Integer> calcularMovimientosOriginales(
            List<A3_2_TipoTransaccionesGetsYSets> lista) {
        java.util.HashMap<String, Integer> mapa = new java.util.HashMap<>();
        if (lista == null) return mapa;
        for (A3_2_TipoTransaccionesGetsYSets t : lista) {
            String cuenta = t.tipoT_3Accout_String;
            int valor     = t.tipoT_5Value_Integer;
            int acumulado = mapa.containsKey(cuenta) ? mapa.get(cuenta) : 0;
            mapa.put(cuenta, acumulado + valor);
        }
        return mapa;
    }

    // ═════════════════════════════════════════════════════════════
    // 7. renumerarItems — parte pura de renumerarItemsListaDocumento()
    //
    // ANTES en el fragment: renumeraba Y creaba adapter Y escribía en TextView.
    // AHORA: solo renumera los items en la lista (opera in-place).
    // El fragment refresca el adapter y pinta el contador.
    // ═════════════════════════════════════════════════════════════
    public void renumerarItems(List<A3_2_TipoTransaccionesGetsYSets> lista) {
        if (lista == null) return;
        for (int i = 0; i < lista.size(); i++) {
            lista.get(i).tipoTset_2ItemDocMetodoEnA5("" + (i + 1));
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 8. construirItemRegistro — parte pura de losDemasRegistrosAListaDocumento()
    //
    // ANTES en el fragment: leía valor_XEt, signo_XSp, descripcion_XAtv,
    //   cuenta_XAtv, cuenta_XSp directamente.
    // AHORA: recibe todos los valores ya leídos, construye y retorna el objeto.
    // Retorna null si los atributos no son válidos.
    // ═════════════════════════════════════════════════════════════
    public A3_2_TipoTransaccionesGetsYSets construirItemRegistro(
            String numeroDoc,
            int    posicionEnLista,
            String cuentaAlItemList,
            String signo,
            String valorStr,
            String descripcion,
            String fechaYHora,
            int    dateOfDocument,
            String[] atributosCuenta) {

        if (atributosCuenta == null || atributosCuenta.length < 4) {
            Log.e(TAG, "Atributos de cuenta inválidos");
            return null;
        }

        int valorEntero = 0;
        try {
            if ("-".equals(signo)) {
                valorEntero = Integer.parseInt("-" + valorStr);
            } else {
                valorEntero = Integer.parseInt(valorStr);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Valor inválido: " + valorStr, e);
            return null;
        }

        return new A3_2_TipoTransaccionesGetsYSets(
                numeroDoc,
                Integer.toString(posicionEnLista),
                cuentaAlItemList,
                signo,
                valorEntero,
                descripcion,
                fechaYHora,
                dateOfDocument,
                "No Aplica",
                atributosCuenta[2],
                atributosCuenta[3],
                "na",
                "na"
        );
    }

    // ═════════════════════════════════════════════════════════════
    // 9. construirItemRegistroInicial — parte pura de primerRegistroAListaDocumentoCuentaConciliable()
    //
    // ANTES en el fragment: leía cuentaConciliacion_XSp directamente.
    // AHORA: recibe cuentaConciliable como String.
    // Retorna null si dAc == 0 (no hay nada que registrar) o cuenta es "1 No conciliar".
    // ═════════════════════════════════════════════════════════════
    public A3_2_TipoTransaccionesGetsYSets construirItemRegistroInicial(
            int    dAc,
            String cuentaConciliable,
            String numeroDoc,
            String fechaYHora,
            int    dateOfDocument,
            String[] atributosCuenta) {

        if (dAc == 0) return null;
        if ("1 No conciliar".equals(cuentaConciliable)) return null;
        if (atributosCuenta == null || atributosCuenta.length < 4) {
            Log.e(TAG, "Atributos de cuenta inválidos en primer registro");
            return null;
        }

        String signo;
        int    valorRegistro;
        String descripcion;

        if (dAc > 0) {
            signo        = "-";
            valorRegistro = dAc * -1;
            descripcion  = "Salida del dia";
        } else {
            signo        = "+";
            valorRegistro = dAc * -1;
            descripcion  = "Entrada del dia";
        }

        return new A3_2_TipoTransaccionesGetsYSets(
                numeroDoc,
                "1",
                cuentaConciliable,
                signo,
                valorRegistro,
                descripcion,
                fechaYHora,
                dateOfDocument,
                "No Aplica",
                atributosCuenta[2],
                atributosCuenta[3],
                "na",
                "na"
        );
    }
}