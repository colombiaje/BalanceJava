package A1BASES;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐ NUEVO (15-nov, plantillas P2, diseño de Jorge):
 *  - Crear una plantilla con el documento en pantalla (acto deliberado): contador
 *    "Plantilla x de y", nombre único y control por contenido (≥ 70 % de cuentas iguales →
 *    ofrece actualizar la parecida).
 *  - Al guardar un documento hecho con una plantilla, si quedó distinto del modelo, pregunta
 *    si pasa a ser el modelo (por defecto, No).
 *  - Administrar: renombrar, editar conceptos, periodicidad, desactivar/activar y deshacer el
 *    último reemplazo del modelo.
 */
public final class A29_AdministrarPlantillasDialogo {

    private final Context ctx;
    private final Runnable alCambiar;

    public A29_AdministrarPlantillasDialogo(Context ctx, Runnable alCambiar) {
        this.ctx = ctx;
        this.alCambiar = alCambiar;
    }

    private void avisarCambio() {
        if (alCambiar != null) alCambiar.run();
    }

    // ═══════════════════════ Después de guardar ═══════════════════════

    /**
     * Se llama tras guardar un documento hecho con la plantilla; pregunta solo si cambió (el
     * periodo de la descripción no cuenta). ⭐ (15-nov, Jorge): el aviso dice qué cambió y qué
     * pasa con cada opción; el documento queda guardado en ambos casos.
     */
    public void preguntarSiPasaAModelo(long plantillaId, List<A27_Plantillas.NuevoItem> documento) {
        if (documento.isEmpty() || !A27_Plantillas.difiereDelModelo(ctx, plantillaId, documento)) return;
        A27_Plantillas.Plantilla p = porId(plantillaId);
        if (p == null) return;
        String mensaje = "El documento ya quedó guardado. Cambios frente al modelo:\n" +
                diferencias(plantillaId, documento) + "\n\n" +
                "• Mantener el modelo: la plantilla sigue igual; estos cambios solo valen para este documento.\n" +
                "• Actualizar el modelo: este documento pasa a ser la plantilla. El modelo actual se " +
                "guarda como versión anterior (se recupera en 🗂 › Administrar › Deshacer).";
        new AlertDialog.Builder(ctx)
                .setTitle("Plantilla \"" + p.nombre + "\"")
                .setMessage(mensaje)
                .setPositiveButton("Actualizar el modelo", (d, w) -> {
                    boolean ok = A27_Plantillas.reemplazarModelo(ctx, plantillaId, null, documento);
                    Toast.makeText(ctx, ok ? "Modelo de \"" + p.nombre + "\" actualizado"
                            : "No se pudo actualizar", Toast.LENGTH_LONG).show();
                    avisarCambio();
                })
                .setNegativeButton("Mantener el modelo", null)
                .show();
    }

    /** Resumen de diferencias por cuenta: valor o signo, concepto, ítems agregados y quitados. */
    private String diferencias(long plantillaId, List<A27_Plantillas.NuevoItem> documento) {
        List<A27_Plantillas.Item> modelo = A27_Plantillas.items(ctx, plantillaId);
        java.util.Map<Long, A27_Plantillas.Item> porCuenta = new java.util.LinkedHashMap<>();
        for (A27_Plantillas.Item m : modelo) porCuenta.put(m.cuentaId, m);
        List<String> lineas = new ArrayList<>();
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        for (A27_Plantillas.NuevoItem d : documento) {
            vistos.add(d.cuentaId);
            A27_Plantillas.Item m = porCuenta.get(d.cuentaId);
            String nombre = d.concepto;
            if (m == null) {
                lineas.add("+ " + nombre + " " + d.signo + d.valor + " (nuevo)");
                continue;
            }
            if (!m.signo.equals(d.signo) || Math.abs(m.valor) != d.valor) {
                lineas.add("• " + nombre + ": " + m.signo + Math.abs(m.valor) + " → " + d.signo + d.valor);
            }
            String cm = m.concepto == null ? "" : m.concepto.trim();
            if (!cm.equalsIgnoreCase(d.concepto == null ? "" : d.concepto.trim())) {
                lineas.add("• Concepto \"" + cm + "\" → \"" + d.concepto + "\"");
            }
        }
        for (A27_Plantillas.Item m : modelo) {
            if (!vistos.contains(m.cuentaId)) {
                lineas.add("− " + (m.concepto == null ? m.cuenta : m.concepto) + " (quitado)");
            }
        }
        if (lineas.isEmpty()) lineas.add("• Cambió el orden de los ítems");
        if (lineas.size() > 8) {
            int resto = lineas.size() - 7;
            lineas = new ArrayList<>(lineas.subList(0, 7));
            lineas.add("… y " + resto + " más");
        }
        return android.text.TextUtils.join("\n", lineas);
    }

    // ═══════════════════════ Crear con el documento en pantalla ═══════════════════════

    public void crearDesdeDocumento(List<A3_2_TipoTransaccionesGetsYSets> documento) {
        List<A27_Plantillas.NuevoItem> items = A27_Plantillas.desdeDocumento(documento);
        if (items.isEmpty()) {
            Toast.makeText(ctx, "No hay ítems en pantalla para crear una plantilla", Toast.LENGTH_LONG).show();
            return;
        }
        A27_Plantillas.Parecido parecido = A27_Plantillas.masParecida(ctx, items, null);
        if (parecido != null) {
            new AlertDialog.Builder(ctx)
                    .setTitle("Se parece a \"" + parecido.plantilla.nombre + "\"")
                    .setMessage(parecido.comunes + " de " + parecido.total + " cuentas iguales.\n\n" +
                            "¿Actualizar esa plantilla con este documento en lugar de crear otra?")
                    .setPositiveButton("Actualizar esa", (d, w) -> {
                        boolean ok = A27_Plantillas.reemplazarModelo(ctx, parecido.plantilla.id, null, items);
                        Toast.makeText(ctx, ok ? "Modelo de \"" + parecido.plantilla.nombre + "\" actualizado"
                                : "No se pudo actualizar", Toast.LENGTH_LONG).show();
                        avisarCambio();
                    })
                    .setNeutralButton("Crear otra", (d, w) -> formularioCrear(items))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        formularioCrear(items);
    }

    private void formularioCrear(List<A27_Plantillas.NuevoItem> items) {
        LinearLayout raiz = contenedor();
        raiz.addView(info(items.size() + " ítems del documento en pantalla."));
        EditText nombre = campoTexto(raiz, "Nombre de la plantilla", "");
        RadioGroup grupo = grupoPeriodicidad(raiz, A27_Plantillas.MENSUAL);
        int total = A27_Plantillas.listarActivas(ctx).size();
        new AlertDialog.Builder(ctx)
                .setTitle("Plantilla " + (total + 1) + " de " + (total + 1) + " (hoy hay " + total + " activas)")
                .setView(envolver(raiz))
                .setPositiveButton("Crear", (d, w) -> {
                    String n = nombre.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(ctx, "Falta el nombre", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    A27_Plantillas.Plantilla existente = A27_Plantillas.buscarPorNombre(ctx, n);
                    if (existente != null) {
                        Toast.makeText(ctx, "Ya existe \"" + existente.nombre + "\"; use otro nombre o actualícela",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    long id = A27_Plantillas.crear(ctx, n, periodicidadMarcada(grupo), null, items);
                    Toast.makeText(ctx, id > 0 ? "Plantilla \"" + n + "\" creada (" +
                            A27_Plantillas.listarActivas(ctx).size() + " activas)" : "No se pudo crear",
                            Toast.LENGTH_LONG).show();
                    avisarCambio();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ═══════════════════════ Administrar ═══════════════════════

    public void administrar() {
        final List<A27_Plantillas.Plantilla> todas = A27_Plantillas.listarTodas(ctx);
        if (todas.isEmpty()) {
            Toast.makeText(ctx, "Aún no hay plantillas nuevas", Toast.LENGTH_SHORT).show();
            return;
        }
        int activas = 0;
        String[] filas = new String[todas.size()];
        for (int i = 0; i < todas.size(); i++) {
            A27_Plantillas.Plantilla p = todas.get(i);
            boolean activa = esActiva(p.id);
            if (activa) activas++;
            filas[i] = (activa ? "" : "⊘ ") + p.nombre + " · " + p.items + " ítems · " +
                    A27_Plantillas.textoPeriodicidad(p.periodicidad) +
                    (p.ultimoUso > 0 ? " · usada " + A27_Plantillas.fechaCorta(p.ultimoUso) : "");
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Plantillas (" + activas + " activas)")
                .setItems(filas, (d, i) -> acciones(todas.get(i)))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void acciones(A27_Plantillas.Plantilla p) {
        boolean activa = esActiva(p.id);
        boolean anterior = A27_Plantillas.tieneAnterior(ctx, p.id);
        List<String> op = new ArrayList<>();
        op.add("Renombrar");
        op.add("Editar conceptos");
        op.add("Periodicidad");
        op.add(activa ? "Desactivar" : "Activar");
        if (anterior) op.add("Deshacer el último cambio del modelo");
        new AlertDialog.Builder(ctx)
                .setTitle(p.nombre)
                .setItems(op.toArray(new String[0]), (d, i) -> {
                    switch (op.get(i)) {
                        case "Renombrar": renombrar(p); break;
                        case "Editar conceptos": editarConceptos(p); break;
                        case "Periodicidad": periodicidad(p); break;
                        case "Desactivar":
                        case "Activar":
                            A27_Plantillas.activar(ctx, p.id, !activa);
                            Toast.makeText(ctx, "\"" + p.nombre + "\" " + (activa ? "desactivada" : "activada"),
                                    Toast.LENGTH_SHORT).show();
                            avisarCambio();
                            administrar();
                            break;
                        default:
                            confirmarDeshacer(p);
                    }
                })
                .setNegativeButton("Volver", (d, w) -> administrar())
                .show();
    }

    private void renombrar(A27_Plantillas.Plantilla p) {
        LinearLayout raiz = contenedor();
        EditText nombre = campoTexto(raiz, "Nuevo nombre", p.nombre);
        new AlertDialog.Builder(ctx)
                .setTitle("Renombrar")
                .setView(envolver(raiz))
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = nombre.getText().toString().trim();
                    if (n.isEmpty()) return;
                    boolean ok = A27_Plantillas.renombrar(ctx, p.id, n);
                    Toast.makeText(ctx, ok ? "Renombrada" : "Ese nombre ya lo usa otra plantilla",
                            Toast.LENGTH_LONG).show();
                    avisarCambio();
                    administrar();
                })
                .setNegativeButton("Cancelar", (d, w) -> administrar())
                .show();
    }

    private void editarConceptos(A27_Plantillas.Plantilla p) {
        List<A27_Plantillas.Item> items = A27_Plantillas.items(ctx, p.id);
        LinearLayout raiz = contenedor();
        raiz.addView(info("El concepto es la descripción de cada registro; el periodo se agrega solo."));
        List<EditText> campos = new ArrayList<>();
        for (A27_Plantillas.Item it : items) {
            campos.add(campoTexto(raiz, (it.cuenta == null ? "(cuenta no existe)" : it.cuenta) +
                    "  " + it.signo + it.valor, it.concepto == null ? "" : it.concepto));
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Conceptos de \"" + p.nombre + "\"")
                .setView(envolver(raiz))
                .setPositiveButton("Guardar", (d, w) -> {
                    List<String> conceptos = new ArrayList<>();
                    for (EditText e : campos) conceptos.add(e.getText().toString());
                    A27_Plantillas.actualizarConceptos(ctx, p.id, conceptos);
                    Toast.makeText(ctx, "Conceptos guardados", Toast.LENGTH_SHORT).show();
                    avisarCambio();
                    administrar();
                })
                .setNegativeButton("Cancelar", (d, w) -> administrar())
                .show();
    }

    private void periodicidad(A27_Plantillas.Plantilla p) {
        LinearLayout raiz = contenedor();
        RadioGroup grupo = grupoPeriodicidad(raiz, p.periodicidad);
        new AlertDialog.Builder(ctx)
                .setTitle("Periodicidad de \"" + p.nombre + "\"")
                .setView(envolver(raiz))
                .setPositiveButton("Guardar", (d, w) -> {
                    A27_Plantillas.cambiarPeriodicidad(ctx, p.id, periodicidadMarcada(grupo));
                    avisarCambio();
                    administrar();
                })
                .setNegativeButton("Cancelar", (d, w) -> administrar())
                .show();
    }

    private void confirmarDeshacer(A27_Plantillas.Plantilla p) {
        new AlertDialog.Builder(ctx)
                .setTitle("Deshacer el último cambio")
                .setMessage("El modelo de \"" + p.nombre + "\" vuelve a la versión anterior " +
                        "(y el actual queda como anterior, por si quiere volver).")
                .setPositiveButton("Deshacer", (d, w) -> {
                    boolean ok = A27_Plantillas.restaurarAnterior(ctx, p.id);
                    Toast.makeText(ctx, ok ? "Modelo restaurado" : "No hay versión anterior",
                            Toast.LENGTH_LONG).show();
                    avisarCambio();
                    administrar();
                })
                .setNegativeButton("Cancelar", (d, w) -> administrar())
                .show();
    }

    // ═══════════════════════ Utilidades ═══════════════════════

    private A27_Plantillas.Plantilla porId(long id) {
        for (A27_Plantillas.Plantilla p : A27_Plantillas.listarTodas(ctx)) if (p.id == id) return p;
        return null;
    }

    private boolean esActiva(long id) {
        for (A27_Plantillas.Plantilla p : A27_Plantillas.listarActivas(ctx)) if (p.id == id) return true;
        return false;
    }

    private static final String[] OPCIONES = {A27_Plantillas.QUINCENAL, A27_Plantillas.MENSUAL,
            A27_Plantillas.SIN_PERIODO};

    private RadioGroup grupoPeriodicidad(LinearLayout raiz, String marcada) {
        TextView t = new TextView(ctx);
        t.setText("Periodicidad (para la descripción de cada registro)");
        t.setPadding(0, dp(8), 0, 0);
        raiz.addView(t);
        RadioGroup g = new RadioGroup(ctx);
        for (int i = 0; i < OPCIONES.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setId(2000 + i);
            rb.setText(A27_Plantillas.textoPeriodicidad(OPCIONES[i]));
            g.addView(rb);
            if (OPCIONES[i].equals(marcada)) rb.setChecked(true);
        }
        raiz.addView(g);
        return g;
    }

    private static String periodicidadMarcada(RadioGroup g) {
        int id = g.getCheckedRadioButtonId();
        return id >= 2000 && id < 2000 + OPCIONES.length ? OPCIONES[id - 2000] : A27_Plantillas.MENSUAL;
    }

    private LinearLayout contenedor() {
        LinearLayout raiz = new LinearLayout(ctx);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(dp(16), dp(8), dp(16), 0);
        return raiz;
    }

    private ScrollView envolver(LinearLayout raiz) {
        ScrollView s = new ScrollView(ctx);
        s.addView(raiz);
        return s;
    }

    private TextView info(String texto) {
        TextView t = new TextView(ctx);
        t.setText(texto);
        t.setTextColor(A20_Informacion.COLOR_TEXTO);
        t.setBackgroundColor(A20_Informacion.COLOR_FONDO);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return t;
    }

    private EditText campoTexto(LinearLayout raiz, String etiqueta, String valor) {
        TextView t = new TextView(ctx);
        t.setText(etiqueta);
        t.setPadding(0, dp(8), 0, 0);
        raiz.addView(t);
        EditText e = new EditText(ctx);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(true);
        e.setText(valor);
        raiz.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
