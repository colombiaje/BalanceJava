package B_FRAGMENTS;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.jj.appbalancev31.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import android.view.MotionEvent;

import A1BASES.A8_CalculadoraCallback;


/**
 * DialogFragment de calculadora con funcionalidad completa.
 * Soporta operaciones básicas: +, -, *, /, % y cambio de signo.
 * Maneja decimales correctamente usando Double.
 *
 * Uso desde un campo (con callback):
 *   F91_Calculadora calc = F91_Calculadora.newInstanceDesdeCampo(callback);
 *   calc.show(getFragmentManager(), "tag");
 *
 * Uso libre (sin callback):
 *   F91_Calculadora calc = F91_Calculadora.newInstanceLibre();
 *   calc.show(getFragmentManager(), "tag");
 */
public class F6_Calculadora extends DialogFragment {

    // ==================== CONSTANTES ====================
    private static final String ARG_LLAMADO_DESDE_CAMPO = "llamado_desde_campo";
    private static final int MAX_DIGITOS = 15; // Máximo de dígitos permitidos
    private static final String TAG = "F91_Calculadora";

    // ==================== COMPONENTES UI ====================
    private TextView resultado_XTv;
    private Button usarResultado_XBt;
    private Button salir_XBt;

    // Botones numéricos (0-9)
    private Button b0_XBt, b1_XBt, b2_XBt, b3_XBt, b4_XBt;
    private Button b5_XBt, b6_XBt, b7_XBt, b8_XBt, b9_XBt;

    // Botones de operación
    private Button bMas_XBt;        // +
    private Button bMenos_XBt;      // -
    private Button bPor_XBt;        // *
    private Button bDivision_XBt;   // /
    private Button bPorcentaje_XBt; // %
    private Button bPunto_XBt;      // .
    private Button bMasMenos_XBt;   // ±

    // Botones de control
    private Button bIgual_XBt;      // =
    private Button bLimp_XBt;       // AC
    private Button bRetroceso_XBt;  // ←

    // ==================== VARIABLES DE ESTADO ====================
    private String displayActual = "0";     // Lo que se muestra en pantalla
    private double primerNumero = 0;        // Primer operando
    private double segundoNumero = 0;       // Segundo operando
    private String operadorActual = "";     // +, -, *, /
    private boolean nuevoNumero = true;     // Flag: siguiente entrada inicia número nuevo
    private boolean puntoDecimalUsado = false; // Flag: ya se usó punto decimal
    private boolean resultadoMostrado = false; // Flag: se mostró un resultado con =

    // ==================== CALLBACK Y CONFIGURACIÓN ====================
    private A8_CalculadoraCallback callback;
    private boolean llamadoDesdeCampo = false;

    // ==================== FORMATO DE NÚMEROS ====================
    private DecimalFormat decimalFormat;

    // ==================== CONSTRUCTORES Y FACTORY METHODS ====================

    /**
     * Constructor público requerido por Android
     */
    public F6_Calculadora() {
        // Required empty public constructor
    }

    /**
     * Factory method: Crear calculadora desde un campo EditText.
     * Muestra botón "Usar Resultado" y envía valor por callback.
     *
     * @param callback Interface que recibirá el resultado
     * @return Nueva instancia configurada
     */
    public static F6_Calculadora newInstanceDesdeCampo(A8_CalculadoraCallback callback) {
        F6_Calculadora fragment = new F6_Calculadora();
        fragment.setCallback(callback);

        Bundle args = new Bundle();
        args.putBoolean(ARG_LLAMADO_DESDE_CAMPO, true);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Factory method: Crear calculadora en modo libre.
     * Solo muestra botón "Cerrar", para cálculos generales.
     *
     * @return Nueva instancia configurada
     */
    public static F6_Calculadora newInstanceLibre() {
        F6_Calculadora fragment = new F6_Calculadora();

        Bundle args = new Bundle();
        args.putBoolean(ARG_LLAMADO_DESDE_CAMPO, false);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Setter para el callback (usado internamente por factory methods)
     */
    public void setCallback(A8_CalculadoraCallback callback) {
        this.callback = callback;
    }

    // ==================== CICLO DE VIDA ====================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Estilo del diálogo sin título
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Light_Dialog);

        // Configurar formato de números (separador decimal = punto)
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        decimalFormat = new DecimalFormat("#,##0.##########", symbols);
        decimalFormat.setGroupingUsed(false); // Sin separador de miles

        // Leer argumentos
        if (getArguments() != null) {
            llamadoDesdeCampo = getArguments().getBoolean(ARG_LLAMADO_DESDE_CAMPO, false);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // Fondo semi-transparente
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.f9_calculadora, container, false);

        inicializarVistas(view);
        configurarListeners();
        actualizarDisplay();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() == null || getDialog().getWindow() == null) return;

        Window window = getDialog().getWindow();

        // 1️⃣ Ancho deseado: 80% de la pantalla (estable, siempre igual)
        int ancho = (int) (getResources().getDisplayMetrics().widthPixels * 0.65);

        // 2️⃣ Alto = wrap_content
        int alto = ViewGroup.LayoutParams.WRAP_CONTENT;

        window.setLayout(ancho, alto);

        // 3️⃣ Centrar el diálogo
        window.setGravity(android.view.Gravity.CENTER);

        // 4️⃣ Mover ligeramente a la izquierda (opcional)
        WindowManager.LayoutParams params = window.getAttributes();
        params.x = 70;  // desplaza sin perder el centrado. + derecha / - izquierda
        window.setAttributes(params);

        //Window window = getDialog().getWindow();
        if (window != null) {
            window.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            window.getAttributes().x = -50; // mueve más a la izquierda (+ derecha / - izquierda)
        }

        // ================== HACER EL DIÁLOGO MÓVIL (DRAGGABLE) ==================
        View root = getView();
        Window w = getDialog().getWindow();

        if (root != null && w != null) {

            root.setOnTouchListener(new View.OnTouchListener() {

                float dX, dY;
                int lastX, lastY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:
                            dX = event.getRawX();
                            dY = event.getRawY();
                            lastX = w.getAttributes().x;
                            lastY = w.getAttributes().y;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            WindowManager.LayoutParams params = w.getAttributes();
                            params.x = lastX + (int) (event.getRawX() - dX);
                            params.y = lastY + (int) (event.getRawY() - dY);
                            w.setAttributes(params);
                            return true;
                    }

                    return false;
                }
            });
        }

    }

    // ==================== INICIALIZACIÓN DE VISTAS ====================

    private void inicializarVistas(View view) {
        // Display
        resultado_XTv = view.findViewById(R.id.resultado_XTv);

        // Botones numéricos
        b0_XBt = view.findViewById(R.id.b0_XBt);
        b1_XBt = view.findViewById(R.id.b1_XBt);
        b2_XBt = view.findViewById(R.id.b2_XBt);
        b3_XBt = view.findViewById(R.id.b3_XBt);
        b4_XBt = view.findViewById(R.id.b4_XBt);
        b5_XBt = view.findViewById(R.id.b5_XBt);
        b6_XBt = view.findViewById(R.id.b6_XBt);
        b7_XBt = view.findViewById(R.id.b7_XBt);
        b8_XBt = view.findViewById(R.id.b8_XBt);
        b9_XBt = view.findViewById(R.id.b9_XBt);

        // Operaciones
        bMas_XBt = view.findViewById(R.id.bMas_XBt);
        bMenos_XBt = view.findViewById(R.id.bMenos_XBt);
        bPor_XBt = view.findViewById(R.id.bPor_XBt);
        bDivision_XBt = view.findViewById(R.id.bDivision_XBt);
        bPorcentaje_XBt = view.findViewById(R.id.bPorcentaje_XBt);
        bPunto_XBt = view.findViewById(R.id.bPunto_XBt);
        bMasMenos_XBt = view.findViewById(R.id.bMasMenos_XBt);

        // Control
        bIgual_XBt = view.findViewById(R.id.bIgual_XBt);
        bLimp_XBt = view.findViewById(R.id.bLimp_XBt);
        bRetroceso_XBt = view.findViewById(R.id.bRetroceso_XBt);
        salir_XBt = view.findViewById(R.id.salir_XBt);
        usarResultado_XBt = view.findViewById(R.id.usarResultado_XBt);

        // Configurar visibilidad y texto según el modo
        if (llamadoDesdeCampo) {
            usarResultado_XBt.setVisibility(View.VISIBLE);
            salir_XBt.setText("Cerrar");
        } else {
            usarResultado_XBt.setVisibility(View.GONE);
            salir_XBt.setText("Salir");
        }
    }

    // ==================== CONFIGURACIÓN DE LISTENERS ====================

    private void configurarListeners() {
        // Listener compartido para todos los botones numéricos
        View.OnClickListener numeroListener = v -> {
            Button btn = (Button) v;
            agregarDigito(btn.getText().toString());
        };

        b0_XBt.setOnClickListener(numeroListener);
        b1_XBt.setOnClickListener(numeroListener);
        b2_XBt.setOnClickListener(numeroListener);
        b3_XBt.setOnClickListener(numeroListener);
        b4_XBt.setOnClickListener(numeroListener);
        b5_XBt.setOnClickListener(numeroListener);
        b6_XBt.setOnClickListener(numeroListener);
        b7_XBt.setOnClickListener(numeroListener);
        b8_XBt.setOnClickListener(numeroListener);
        b9_XBt.setOnClickListener(numeroListener);

        // Operadores aritméticos
        bMas_XBt.setOnClickListener(v -> establecerOperador("+"));
        bMenos_XBt.setOnClickListener(v -> establecerOperador("-"));
        bPor_XBt.setOnClickListener(v -> establecerOperador("*"));
        bDivision_XBt.setOnClickListener(v -> establecerOperador("/"));
        bPorcentaje_XBt.setOnClickListener(v -> calcularPorcentaje());

        // Funciones especiales
        bPunto_XBt.setOnClickListener(v -> agregarPuntoDecimal());
        bMasMenos_XBt.setOnClickListener(v -> cambiarSigno());

        // Control
        bIgual_XBt.setOnClickListener(v -> calcularResultado());
        bLimp_XBt.setOnClickListener(v -> limpiarTodo());
        bRetroceso_XBt.setOnClickListener(v -> retroceder());
        salir_XBt.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCalculadoraCancelada();
            }
            dismiss();
        });

        // Botón "Usar Resultado" - solo visible cuando llamadoDesdeCampo = true
        usarResultado_XBt.setOnClickListener(v -> {
            if (callback != null) {
                callback.onResultadoConfirmado(displayActual);
                dismiss();
            } else {
                mostrarToast("No hay callback configurado");
            }
        });
    }

    // ==================== LÓGICA DE CALCULADORA ====================

    /**
     * Agrega un dígito al display actual
     */
    private void agregarDigito(String digito) {
        // Si se acaba de mostrar un resultado con =, empezar número nuevo
        if (resultadoMostrado) {
            displayActual = "0";
            resultadoMostrado = false;
            nuevoNumero = true;
        }

        if (nuevoNumero) {
            displayActual = digito;
            nuevoNumero = false;
            puntoDecimalUsado = false;
        } else {
            // Validar longitud máxima (sin contar punto y signo)
            String soloDigitos = displayActual.replace(".", "").replace("-", "");
            if (soloDigitos.length() >= MAX_DIGITOS) {
                mostrarToast("Máximo " + MAX_DIGITOS + " dígitos");
                return;
            }

            // Evitar múltiples ceros al inicio
            if (displayActual.equals("0") && digito.equals("0")) {
                return;
            }

            // Reemplazar 0 inicial si no hay punto decimal
            if (displayActual.equals("0") && !digito.equals("0")) {
                displayActual = digito;
            } else {
                displayActual += digito;
            }
        }

        actualizarDisplay();
    }

    /**
     * Agrega punto decimal si no existe uno ya
     */
    private void agregarPuntoDecimal() {
        if (resultadoMostrado) {
            displayActual = "0";
            resultadoMostrado = false;
            nuevoNumero = false;
        }

        if (nuevoNumero) {
            displayActual = "0.";
            nuevoNumero = false;
            puntoDecimalUsado = true;
        } else if (!puntoDecimalUsado) {
            displayActual += ".";
            puntoDecimalUsado = true;
        }

        actualizarDisplay();
    }

    /**
     * Cambia el signo del número actual (positivo ↔ negativo)
     */
    private void cambiarSigno() {
        if (displayActual.equals("0") || displayActual.isEmpty()) {
            return;
        }

        if (displayActual.startsWith("-")) {
            displayActual = displayActual.substring(1);
        } else {
            displayActual = "-" + displayActual;
        }

        actualizarDisplay();
    }

    /**
     * Establece el operador (+, -, *, /) y guarda el primer número
     */
    private void establecerOperador(String operador) {
        if (displayActual.isEmpty() || displayActual.equals("-")) {
            return;
        }

        try {
            // Si ya hay un operador pendiente, calcular primero
            if (!operadorActual.isEmpty() && !nuevoNumero) {
                calcularResultado();
            }

            primerNumero = parsearNumero(displayActual);
            operadorActual = operador;
            nuevoNumero = true;
            puntoDecimalUsado = false;
            resultadoMostrado = false;

        } catch (NumberFormatException e) {
            mostrarToast("Número inválido");
        }
    }

    /**
     * Calcula el resultado de la operación pendiente
     */
    private void calcularResultado() {
        if (operadorActual.isEmpty() || displayActual.isEmpty() || nuevoNumero) {
            return;
        }

        try {
            segundoNumero = parsearNumero(displayActual);
            double resultado = 0;
            boolean operacionValida = true;

            switch (operadorActual) {
                case "+":
                    resultado = primerNumero + segundoNumero;
                    break;

                case "-":
                    resultado = primerNumero - segundoNumero;
                    break;

                case "*":
                    resultado = primerNumero * segundoNumero;
                    break;

                case "/":
                    if (segundoNumero == 0) {
                        mostrarToast("Error: División por cero");
                        limpiarTodo();
                        return;
                    }
                    resultado = primerNumero / segundoNumero;
                    break;

                default:
                    operacionValida = false;
                    break;
            }

            if (operacionValida) {
                displayActual = formatearNumero(resultado);
                operadorActual = "";
                nuevoNumero = true;
                puntoDecimalUsado = displayActual.contains(".");
                resultadoMostrado = true; // Marcar que se mostró un resultado
                actualizarDisplay();
            }

        } catch (NumberFormatException e) {
            mostrarToast("Error en el cálculo");
            limpiarTodo();
        } catch (ArithmeticException e) {
            mostrarToast("Error matemático");
            limpiarTodo();
        }
    }

    /**
     * Calcula el porcentaje según el contexto
     * - Con operador pendiente: porcentaje del primer número
     * - Sin operador: divide entre 100
     */
    private void calcularPorcentaje() {
        if (displayActual.isEmpty() || displayActual.equals("-")) {
            return;
        }

        try {
            double numero = parsearNumero(displayActual);

            if (!operadorActual.isEmpty()) {
                // Ejemplo: 200 + 10% = 200 + (200 * 0.10) = 220
                numero = primerNumero * (numero / 100.0);
            } else {
                // Ejemplo: 50% = 0.5
                numero = numero / 100.0;
            }

            displayActual = formatearNumero(numero);
            puntoDecimalUsado = displayActual.contains(".");
            actualizarDisplay();

        } catch (NumberFormatException e) {
            mostrarToast("Error al calcular porcentaje");
        }
    }

    /**
     * Retrocede un carácter en el display
     */
    private void retroceder() {
        if (displayActual.isEmpty() || nuevoNumero || resultadoMostrado) {
            return;
        }

        // Si se borra el punto decimal, actualizar flag
        if (displayActual.endsWith(".")) {
            puntoDecimalUsado = false;
        }

        displayActual = displayActual.substring(0, displayActual.length() - 1);

        // Si queda vacío o solo "-", poner 0 y marcar nuevoNumero
        if (displayActual.isEmpty() || displayActual.equals("-")) {
            displayActual = "0";
            nuevoNumero = true;
            puntoDecimalUsado = false;
        }

        actualizarDisplay();
    }

    /**
     * Limpia todo y resetea la calculadora
     */
    private void limpiarTodo() {
        displayActual = "0";
        primerNumero = 0;
        segundoNumero = 0;
        operadorActual = "";
        nuevoNumero = true;
        puntoDecimalUsado = false;
        resultadoMostrado = false;
        actualizarDisplay();
    }

    // ==================== FUNCIONES AUXILIARES ====================

    /**
     * Actualiza el TextView con el display actual
     */
    private void actualizarDisplay() {
        resultado_XTv.setText(displayActual);
    }

    /**
     * Parsea un String a Double, manejando errores comunes
     */
    private double parsearNumero(String texto) throws NumberFormatException {
        if (texto == null || texto.isEmpty() || texto.equals("-")) {
            return 0;
        }
        return Double.parseDouble(texto);
    }

    /**
     * Formatea un número Double a String, eliminando decimales innecesarios
     */
    private String formatearNumero(double numero) {
        // Verificar si es infinito o NaN
        if (Double.isInfinite(numero) || Double.isNaN(numero)) {
            mostrarToast("Resultado inválido");
            return "0";
        }

        // Verificar overflow (número muy grande)
        String numeroStr = String.valueOf(numero);
        if (numeroStr.length() > MAX_DIGITOS + 10) {
            // Usar notación científica para números muy grandes
            return String.format(Locale.US, "%.4e", numero);
        }

        // Formatear número
        String resultado = decimalFormat.format(numero);

        // Si es un entero, remover el ".0"
        if (numero == Math.floor(numero) && !Double.isInfinite(numero)) {
            resultado = String.valueOf((long) numero);
        }

        return resultado;
    }

    /**
     * Muestra un Toast con un mensaje
     */
    private void mostrarToast(String mensaje) {
        if (getContext() != null) {
            Toast.makeText(getContext(), mensaje, Toast.LENGTH_SHORT).show();
        }
    }
}