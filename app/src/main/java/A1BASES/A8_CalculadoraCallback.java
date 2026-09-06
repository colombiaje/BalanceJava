package A1BASES;

/**
 * Interface para comunicación limpia entre la calculadora y el fragment que la invoca.
 * Implementa este interface en cualquier Fragment que necesite recibir resultados de la calculadora.
 *
 * Ejemplo de uso:
 * public class MiFragment extends DialogFragment implements CalculadoraCallback {
 *     @Override
 *     public void onResultadoConfirmado(String resultado) {
 *         miEditText.setText(resultado);
 *     }
 * }
 */
public interface A8_CalculadoraCallback {
    /**
     * Se invoca cuando el usuario presiona "Usar Resultado" en la calculadora
     * @param resultado El valor calculado como String (puede contener decimales)
     */
    void onResultadoConfirmado(String resultado);

    /**
     * (Opcional) Se invoca si el usuario cancela sin usar el resultado
     * Por defecto no hace nada, pero puedes override si necesitas lógica especial
     */
    default void onCalculadoraCancelada() {
        // Implementación opcional
    }

}