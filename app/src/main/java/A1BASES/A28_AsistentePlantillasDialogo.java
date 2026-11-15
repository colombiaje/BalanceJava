package A1BASES;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ⭐ NUEVO (15-nov, plantillas P1, aprobado por Jorge): asistente de carga inicial.
 * Agrupa las descripciones antiguas que contienen "plantilla" (≈130) en familias, quitando
 * fechas, meses y la palabra "plantilla" ("quincena plantilla 23 2" y "… Feb 2022" →
 * "quincena"). En cada familia se ve cada versión, la más reciente primero, con sus cuentas;
 * el usuario elige una, le pone nombre y periodicidad, y se crea la plantilla nueva con los
 * ítems de ese documento. El histórico no se toca.
 */
public final class A28_AsistentePlantillasDialogo {

    private final Context ctx;
    private final Runnable alTerminar;

    /** Una descripción antigua con su documento más reciente. */
    private static final class Version {
        String descripcion;
        String documento;
        int fecha;
    }

    public A28_AsistentePlantillasDialogo(Context ctx, Runnable alTerminar) {
        this.ctx = ctx;
        this.alTerminar = alTerminar;
    }

    public void mostrar() {
        Map<String, List<Version>> familias = familias();
        if (familias.isEmpty()) {
            Toast.makeText(ctx, "No hay plantillas antiguas para convertir", Toast.LENGTH_LONG).show();
            return;
        }
        // ⭐ (15-nov, Jorge): la marca ✓ dice a qué plantilla se convirtió la familia.
        Map<String, String> convertidas = new HashMap<>();
        int creadas = 0;
        for (A27_Plantillas.Plantilla p : A27_Plantillas.listarTodas(ctx)) {
            creadas++;
            if (p.origen != null) convertidas.put(p.origen.trim().toLowerCase(Locale.ROOT), p.nombre);
        }
        final List<String> claves = new ArrayList<>(familias.keySet());
        String[] filas = new String[claves.size()];
        for (int i = 0; i < claves.size(); i++) {
            List<Version> vs = familias.get(claves.get(i));
            String plantilla = null;
            for (Version v : vs) {
                String n = convertidas.get(v.descripcion.trim().toLowerCase(Locale.ROOT));
                if (n != null) plantilla = n;
            }
            filas[i] = (plantilla != null ? "✓ " : "") + claves.get(i) + "  ·  " + vs.size() +
                    (vs.size() == 1 ? " versión" : " versiones") + " · última " + fechaTexto(vs.get(0).fecha) +
                    (plantilla != null ? "  → " + plantilla : "");
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Asistente de plantillas (" + creadas + " creadas)")
                .setItems(filas, (d, i) -> mostrarVersiones(claves.get(i), familias.get(claves.get(i))))
                .setNegativeButton("Cerrar", (d, w) -> { if (alTerminar != null) alTerminar.run(); })
                .show();
    }

    private void mostrarVersiones(String familia, List<Version> versiones) {
        String[] filas = new String[versiones.size()];
        for (int i = 0; i < versiones.size(); i++) {
            Version v = versiones.get(i);
            filas[i] = fechaTexto(v.fecha) + " · " + v.descripcion + " · " + cuentasDe(v.documento).size() + " ítems";
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Familia \"" + familia + "\": elija la versión base")
                .setItems(filas, (d, i) -> mostrarFormulario(familia, versiones.get(i)))
                .setNegativeButton("Volver", (d, w) -> mostrar())
                .show();
    }

    private void mostrarFormulario(String familia, Version v) {
        List<String[]> cuentas = cuentasDe(v.documento);
        int pad = dp(12);
        LinearLayout raiz = new LinearLayout(ctx);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(pad, pad / 2, pad, 0);

        StringBuilder detalle = new StringBuilder("Documento " + v.documento + " del " + fechaTexto(v.fecha) + ":\n");
        int conInventario = 0;
        for (String[] c : cuentas) {
            if ("1".equals(c[3])) conInventario++;
            detalle.append("• ").append(c[0]).append("  ").append(c[1]).append("\n");
        }
        if (conInventario > 0) {
            detalle.append("\nLas cuentas con inventario no entran en la plantilla (").append(conInventario).append(").");
        }
        TextView info = new TextView(ctx);
        info.setText(detalle.toString().trim());
        info.setTextColor(A20_Informacion.COLOR_TEXTO);
        info.setBackgroundColor(A20_Informacion.COLOR_FONDO);
        info.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        raiz.addView(info);

        TextView etiqueta = new TextView(ctx);
        etiqueta.setText("Nombre de la plantilla");
        etiqueta.setPadding(0, pad, 0, 0);
        raiz.addView(etiqueta);
        EditText nombre = new EditText(ctx);
        nombre.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nombre.setSingleLine(true);
        nombre.setText(nombreSugerido(familia));
        raiz.addView(nombre, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView etiquetaP = new TextView(ctx);
        etiquetaP.setText("Periodicidad (para la descripción de cada registro)");
        etiquetaP.setPadding(0, pad / 2, 0, 0);
        raiz.addView(etiquetaP);
        RadioGroup grupo = new RadioGroup(ctx);
        String[] opciones = {A27_Plantillas.QUINCENAL, A27_Plantillas.MENSUAL, A27_Plantillas.SIN_PERIODO};
        String sugerida = periodicidadSugerida(familia);
        for (int i = 0; i < opciones.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setId(1000 + i);
            rb.setText(A27_Plantillas.textoPeriodicidad(opciones[i]));
            grupo.addView(rb);
            if (opciones[i].equals(sugerida)) rb.setChecked(true);
        }
        raiz.addView(grupo);

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(raiz);

        int total = A27_Plantillas.listarActivas(ctx).size();
        new AlertDialog.Builder(ctx)
                .setTitle("Plantilla " + (total + 1) + " de " + (total + 1) + " (hoy hay " + total + " activas)")
                .setView(scroll)
                .setPositiveButton("Crear", (d, w) -> {
                    int marcado = grupo.getCheckedRadioButtonId();
                    String periodicidad = marcado >= 1000 && marcado < 1003 ? opciones[marcado - 1000] : A27_Plantillas.MENSUAL;
                    crear(nombre.getText().toString().trim(), periodicidad, v, cuentas, familia);
                })
                .setNegativeButton("Volver", (d, w) -> mostrarVersiones(familia, familias().get(familia)))
                .show();
    }

    private void crear(String nombre, String periodicidad, Version v, List<String[]> cuentas, String familia) {
        if (nombre.isEmpty()) {
            Toast.makeText(ctx, "Falta el nombre", Toast.LENGTH_SHORT).show();
            mostrarVersiones(familia, familias().get(familia));
            return;
        }
        List<A27_Plantillas.NuevoItem> items = new ArrayList<>();
        for (String[] c : cuentas) {
            if ("1".equals(c[3]) || c[2] == null) continue; // inventario o cuenta inexistente
            int valor = Integer.parseInt(c[1]);
            items.add(new A27_Plantillas.NuevoItem(Long.parseLong(c[2]), valor < 0 ? "-" : "+", valor,
                    A27_Plantillas.conceptoDeCuenta(c[0])));
        }
        if (items.isEmpty()) {
            Toast.makeText(ctx, "Ese documento no tiene ítems utilizables", Toast.LENGTH_LONG).show();
            return;
        }
        A27_Plantillas.Plantilla existente = A27_Plantillas.buscarPorNombre(ctx, nombre);
        if (existente != null) {
            new AlertDialog.Builder(ctx)
                    .setTitle("Ya existe \"" + existente.nombre + "\"")
                    .setMessage("No se crea otra con el mismo nombre. ¿Reemplazar su modelo con este documento? " +
                            "El modelo actual queda como versión anterior.")
                    .setPositiveButton("Reemplazar", (d, w) -> {
                        boolean ok = A27_Plantillas.reemplazarModelo(ctx, existente.id, periodicidad,
                                v.descripcion, items);
                        Toast.makeText(ctx, ok ? "Modelo de \"" + existente.nombre + "\" actualizado"
                                : "No se pudo actualizar", Toast.LENGTH_LONG).show();
                        mostrar();
                    })
                    .setNegativeButton("Cancelar", (d, w) -> mostrar())
                    .show();
            return;
        }
        long id = A27_Plantillas.crear(ctx, nombre, periodicidad, v.descripcion, items);
        int activas = A27_Plantillas.listarActivas(ctx).size();
        Toast.makeText(ctx, id > 0 ? "Plantilla \"" + nombre + "\" creada (" + activas + " activas)"
                : "No se pudo crear la plantilla", Toast.LENGTH_LONG).show();
        mostrar();
    }

    // ═══════════════════════ Datos ═══════════════════════

    /** Familias (clave normalizada → versiones, la más reciente primero), por fecha de la última. */
    private Map<String, List<Version>> familias() {
        Map<String, Version> porDescripcion = new HashMap<>();
        SQLiteDatabase db = abrir();
        try (Cursor c = db.rawQuery("SELECT c6_Descripcion, c1_Documento, c8_FechaInicial FROM transacciones " +
                "WHERE c6_Descripcion LIKE '%plantilla%'", null)) {
            while (c.moveToNext()) {
                String d = c.getString(0);
                if (d == null || d.trim().isEmpty()) continue;
                String clave = d.trim();
                int fecha = c.getInt(2);
                Version v = porDescripcion.get(clave);
                if (v == null || fecha > v.fecha) {
                    v = new Version();
                    v.descripcion = clave;
                    v.documento = c.getString(1);
                    v.fecha = fecha;
                    porDescripcion.put(clave, v);
                }
            }
        } finally {
            db.close();
        }
        Map<String, List<Version>> grupos = new HashMap<>();
        for (Version v : porDescripcion.values()) {
            String k = claveFamilia(v.descripcion);
            if (k.isEmpty()) k = "(sin nombre)";
            List<Version> l = grupos.get(k);
            if (l == null) grupos.put(k, l = new ArrayList<>());
            l.add(v);
        }
        List<Map.Entry<String, List<Version>>> orden = new ArrayList<>(grupos.entrySet());
        for (Map.Entry<String, List<Version>> e : orden) {
            e.getValue().sort((a, b) -> Integer.compare(b.fecha, a.fecha));
        }
        orden.sort((a, b) -> Integer.compare(b.getValue().get(0).fecha, a.getValue().get(0).fecha));
        Map<String, List<Version>> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, List<Version>> e : orden) resultado.put(e.getKey(), e.getValue());
        return resultado;
    }

    /** Ítems del documento: {cuenta, valor, cuenta_id, con_inventario}. */
    private List<String[]> cuentasDe(String documento) {
        List<String[]> l = new ArrayList<>();
        SQLiteDatabase db = abrir();
        try (Cursor c = db.rawQuery("SELECT IFNULL(c.Cuenta, '(cuenta no existe)'), t.c5_Valor, t.cuenta_id, " +
                "IFNULL(c.con_inventario, 0) FROM transacciones t LEFT JOIN cuentas c ON c.cuenta_id = t.cuenta_id " +
                "WHERE t.c1_Documento = ? ORDER BY CAST(t.c2_ItemDoc AS INTEGER)", new String[]{documento})) {
            while (c.moveToNext()) {
                l.add(new String[]{c.getString(0), String.valueOf(c.getInt(1)),
                        c.isNull(2) ? null : String.valueOf(c.getLong(2)), String.valueOf(c.getInt(3))});
            }
        } finally {
            db.close();
        }
        return l;
    }

    private SQLiteDatabase abrir() {
        return new A1_1_AyudanteBD(ctx, A1_1_AyudanteBD.balanceSqlite_String_PSF, null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF).getReadableDatabase();
    }

    // ═══════════════════════ Normalización ═══════════════════════

    private static final Set<String> MESES = new HashSet<>(java.util.Arrays.asList(
            "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "sept", "oct", "nov", "dic",
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
            "octubre", "noviembre", "diciembre"));

    /**
     * Clave de familia: minúsculas, sin tildes ni signos, sin "plantilla", años (2 o 4 cifras),
     * el mes que sigue a un año ("24 7", "26 1ah" → "ah"), nombres de mes, "año" y "q1"/"q2".
     */
    static String claveFamilia(String descripcion) {
        String s = Normalizer.normalize(descripcion.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\[\\]/.,;:()]", " ")
                .trim();
        String[] t = s.split("\\s+");
        List<String> salida = new ArrayList<>();
        boolean despuesDeAnio = false;
        for (String w : t) {
            if (w.isEmpty() || w.equals("plantilla") || w.equals("ano") || w.matches("q[12]")
                    || MESES.contains(w)) {
                continue;
            }
            if (w.matches("(19|20)\\d\\d") || w.matches("2\\d")) {
                despuesDeAnio = true;
                continue;
            }
            if (despuesDeAnio) {
                despuesDeAnio = false;
                if (w.matches("\\d{1,2}")) continue;
                if (w.matches("\\d{1,2}[a-z]+")) w = w.replaceFirst("^\\d{1,2}", "");
            }
            salida.add(w);
        }
        return String.join(" ", salida).trim();
    }

    private static String nombreSugerido(String familia) {
        if (familia.isEmpty() || familia.startsWith("(")) return "";
        return Character.toUpperCase(familia.charAt(0)) + familia.substring(1);
    }

    private static String periodicidadSugerida(String familia) {
        if (familia.contains("quincena") || familia.contains("15 dias") || familia.contains("16 dias")
                || familia.equals("q") || familia.endsWith(" q")) {
            return A27_Plantillas.QUINCENAL;
        }
        return A27_Plantillas.MENSUAL;
    }

    private static String fechaTexto(int f) {
        if (f < 19000101) return String.valueOf(f);
        return String.format(Locale.ROOT, "%04d-%02d-%02d", f / 10000, (f / 100) % 100, f % 100);
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
