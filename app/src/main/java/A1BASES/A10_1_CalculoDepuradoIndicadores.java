package A1BASES;

import java.util.ArrayList;

public class A10_1_CalculoDepuradoIndicadores {

    private int gastoContable;              // v3 original (saldo cuenta)
    private int totalProrrateables;         // Σv1 (suma de todos los valores prorrateables)
    private float consumoAcumulado;         // Σ(v2 × días efectivos)
    private int gastoDepurado;              // v3_depurado = v3 - Σv1 + consumoAcumulado
    private float promedioDiarioGastado;    // gastoDepurado / diaActual
    private float proyeccionRestoMes;       // promedioDiarioGastado × diasRestantes
    private ArrayList<A10_2_GastoProrrateableIndicadores> detalle;  // Lista de gastos prorrateables

    public A10_1_CalculoDepuradoIndicadores() {
        this.detalle = new ArrayList<>();
    }

    /**
     * Calcula todos los valores derivados
     */
    public void calcular(int gastoContable, int diaActual, int diasMes) {
        this.gastoContable = gastoContable;

        // Sumar todos los totales y consumos
        totalProrrateables = 0;
        consumoAcumulado = 0;

        for (A10_2_GastoProrrateableIndicadores gasto : detalle) {
            totalProrrateables += gasto.getValorTotal();
            consumoAcumulado += gasto.getConsumoAcumulado(diaActual);
        }

        // Cálculo del gasto depurado
        // v3_depurado = v3_contable - Σv1 + consumoAcumulado
        gastoDepurado = Math.round(gastoContable - totalProrrateables + consumoAcumulado);

        // Promedio diario gastado (hasta hoy)
        if (diaActual > 0) {
            promedioDiarioGastado = (float) gastoDepurado / diaActual;
        } else {
            promedioDiarioGastado = 0;
        }

        // Proyección resto del mes
        int diasRestantes = diasMes - diaActual;
        if (diasRestantes > 0) {
            proyeccionRestoMes = promedioDiarioGastado * diasRestantes;
        } else {
            proyeccionRestoMes = 0;
        }
    }

    /**
     * Agrega un gasto prorrateable al detalle
     */
    public void agregarGasto(A10_2_GastoProrrateableIndicadores gasto) {
        if (gasto != null) {
            detalle.add(gasto);
        }
    }

    // Getters
    public int getGastoContable() { return gastoContable; }
    public int getTotalProrrateables() { return totalProrrateables; }
    public float getConsumoAcumulado() { return consumoAcumulado; }
    public int getGastoDepurado() { return gastoDepurado; }
    public float getPromedioDiarioGastado() { return promedioDiarioGastado; }
    public float getProyeccionRestoMes() { return proyeccionRestoMes; }
    public ArrayList<A10_2_GastoProrrateableIndicadores> getDetalle() { return detalle; }
    public int getCantidadProrrateables() { return detalle.size(); }

    /**
     * Retorna el ajuste por prorrateo (lo que aún no se ha consumido)
     */
    public float getAjustePorProrrateo() {
        return totalProrrateables - consumoAcumulado;
    }

    @Override
    public String toString() {
        return "CalculoDepurado{" +
                "gastoContable=" + gastoContable +
                ", gastoDepurado=" + gastoDepurado +
                ", totalProrrateables=" + totalProrrateables +
                ", consumoAcumulado=" + consumoAcumulado +
                ", detalle=" + detalle.size() + " items" +
                '}';
    }
}