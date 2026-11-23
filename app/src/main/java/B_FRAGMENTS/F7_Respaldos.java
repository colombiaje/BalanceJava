package B_FRAGMENTS;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.jj.appbalancev31.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import A1BASES.A32_Respaldos;

/**
 * ⭐ NUEVO — E2 del sistema de respaldos (17-nov, Jorge).
 *
 * Pantalla única "Respaldos":
 *   - Básico: estado del último respaldo (local y Drive), equipo, "Respaldar ahora" y
 *     "Restaurar" desde este equipo o desde Drive.
 *   - Avanzado: accesos a los CSV/Sheets de F2 y F4, y prueba del selector de carpetas de
 *     Android (para la versión sin API de Drive).
 *
 * Restaurar: se revisa el respaldo (integridad y versión), se respalda el estado actual
 * ("antes-restaurar"), se deja la base lista para reemplazarse y la app se reinicia sola
 * (⭐ 21-nov, build B); al arrancar se aplica el cambio (C2_ActivityMenu.onCreate, antes de
 * abrir la base).
 */
public class F7_Respaldos extends Fragment {

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private TextView estado_XTv, equipo_XTv, resultadoSelector_XTv;
    private android.widget.TableLayout tablaUltimos_XTl;
    private TextView tituloTablaUltimos_XTv;
    private boolean tablaAbierta = false;
    private int filasEnTabla = 0;

    private void actualizarTituloTabla() {
        if (tituloTablaUltimos_XTv == null) return;
        tituloTablaUltimos_XTv.setText("Últimos respaldos por tipo (" + filasEnTabla + ") "
                + (tablaAbierta ? "▾  tocar para cerrar" : "▸  tocar para ver"));
    }
    private TextView estadoDrive_XTv;
    // ⭐ NUEVO (21-nov, Jorge, build B): lista plegable de últimas restauraciones.
    private android.widget.TableLayout tablaRestauraciones_XTl;
    private TextView tituloRestauraciones_XTv;
    private boolean restauracionesAbierta = false;
    private int filasRestauraciones = 0;

    private void actualizarTituloRestauraciones() {
        if (tituloRestauraciones_XTv == null) return;
        tituloRestauraciones_XTv.setText("Últimas restauraciones (" + filasRestauraciones + ") "
                + (restauracionesAbierta ? "▾  tocar para cerrar" : "▸  tocar para ver"));
    }
    private AlertDialog espera;

    private final Locale es = new Locale("es", "CO");

    private ActivityResultLauncher<Uri> selectorCarpeta;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectorCarpeta = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (resultadoSelector_XTv == null) return;
            if (uri == null) {
                resultadoSelector_XTv.setText("No se eligió carpeta.");
            } else {
                resultadoSelector_XTv.setText("Carpeta elegida:\nProveedor: " + uri.getAuthority()
                        + "\n" + uri + "\n\nSi el proveedor contiene \"google\", Drive sí permite elegir carpeta.");
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.f7_respaldos, container, false);

        estado_XTv = v.findViewById(R.id.estadoRespaldos_XTv);
        equipo_XTv = v.findViewById(R.id.equipoRespaldos_XTv);
        resultadoSelector_XTv = v.findViewById(R.id.resultadoSelector_XTv);
        tablaUltimos_XTl = v.findViewById(R.id.tablaUltimos_XTl);
        tituloTablaUltimos_XTv = v.findViewById(R.id.tituloTablaUltimos_XTv);
        // ⭐ (21-nov, Jorge): la tabla se abre y se cierra al tocar su título (cerrada al entrar).
        tituloTablaUltimos_XTv.setOnClickListener(x -> {
            tablaAbierta = !tablaAbierta;
            tablaUltimos_XTl.setVisibility(tablaAbierta ? View.VISIBLE : View.GONE);
            actualizarTituloTabla();
        });
        estadoDrive_XTv = v.findViewById(R.id.estadoDrive_XTv);
        tablaRestauraciones_XTl = v.findViewById(R.id.tablaRestauraciones_XTl);
        tituloRestauraciones_XTv = v.findViewById(R.id.tituloRestauraciones_XTv);
        tituloRestauraciones_XTv.setOnClickListener(x -> {
            restauracionesAbierta = !restauracionesAbierta;
            tablaRestauraciones_XTl.setVisibility(restauracionesAbierta ? View.VISIBLE : View.GONE);
            actualizarTituloRestauraciones();
        });
        // ⭐ (21-nov, build B): dos formas de exportar: una tabla, o todas a una carpeta.
        v.findViewById(R.id.exportarTabla_XBt).setOnClickListener(x -> elegirRespaldoParaExportar(false));
        v.findViewById(R.id.exportarTodas_XBt).setOnClickListener(x -> elegirRespaldoParaExportar(true));
        // ⭐ NUEVO (22-nov, Jorge, etapa S1): Sheets como herramienta de correcciones masivas.
        v.findViewById(R.id.enviarASheets_XBt).setOnClickListener(x -> elegirEnvioASheets());
        v.findViewById(R.id.traerDeSheets_XBt).setOnClickListener(x -> elegirHojaParaTraer());
        Button cambiarEquipo = v.findViewById(R.id.cambiarEquipo_XBt);
        Button respaldarAhora = v.findViewById(R.id.respaldarAhora_XBt);
        Button restaurarLocal = v.findViewById(R.id.restaurarLocal_XBt);
        Button restaurarDrive = v.findViewById(R.id.restaurarDrive_XBt);
        CheckBox verAvanzado = v.findViewById(R.id.verAvanzado_XCb);
        LinearLayout areaAvanzada = v.findViewById(R.id.areaAvanzada_XLL);

        cambiarEquipo.setOnClickListener(x -> dialogoCambiarEquipo());
        respaldarAhora.setOnClickListener(x -> respaldarAhora());
        restaurarLocal.setOnClickListener(x -> elegirRespaldoLocal());
        restaurarDrive.setOnClickListener(x -> elegirRespaldoDrive());
        verAvanzado.setOnCheckedChangeListener((b, marcado) -> {
            areaAvanzada.setVisibility(marcado ? View.VISIBLE : View.GONE);
            // ⭐ (21-nov, Jorge, build B): al abrir las opciones avanzadas se cierran las listas.
            if (marcado) {
                tablaAbierta = false;
                tablaUltimos_XTl.setVisibility(View.GONE);
                actualizarTituloTabla();
                restauracionesAbierta = false;
                tablaRestauraciones_XTl.setVisibility(View.GONE);
                actualizarTituloRestauraciones();
            }
        });
        v.findViewById(R.id.irCuentasBackups_XBt).setOnClickListener(x -> irA(R.id.navf2));
        v.findViewById(R.id.irCierres_XBt).setOnClickListener(x -> irA(R.id.navf5));
        v.findViewById(R.id.probarSelector_XBt).setOnClickListener(x -> selectorCarpeta.launch(
                // ⭐ (18-nov, Jorge): abre directamente en la carpeta Balance del equipo.
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABalance")));

        refrescarEstado();
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hilo.shutdown();
    }

    // ─────────────────────────────────────────────────────────────
    // Estado
    // ─────────────────────────────────────────────────────────────
    private void refrescarEstado() {
        if (!isAdded()) return;
        String equipo = A32_Respaldos.equipo(requireContext());
        equipo_XTv.setText("Equipo: " + equipo);

        StringBuilder sb = new StringBuilder();
        List<File> locales = A32_Respaldos.respaldosLocales();
        File ultimoPropio = null;
        for (File f : locales) {
            if (f.getName().startsWith(equipo + "_")) { ultimoPropio = f; break; }
        }
        if (ultimoPropio == null) {
            sb.append("Último respaldo en este equipo: ninguno todavía.");
        } else {
            sb.append("Último respaldo en este equipo:\n")
                    .append(fecha(ultimoPropio.lastModified())).append(" · ")
                    .append(tamano(ultimoPropio.length())).append(" · verificado\n")
                    .append(ultimoPropio.getName());
        }
        sb.append("\nCopias en este equipo: ").append(locales.size());
        String drive = A32_Respaldos.estadoDrive(requireContext());
        sb.append("\n\nDrive: ").append(drive == null ? "sin subidas todavía." : drive);
        estado_XTv.setText(sb.toString());
        // ⭐ (18-nov): debajo de la tabla, solo el resultado de la última subida a Drive.
        estadoDrive_XTv.setText("Última subida a Drive: " + (drive == null ? "ninguna todavía." : drive));
        llenarTablaUltimos();
        llenarTablaRestauraciones();
    }

    // ⭐ NUEVO (21-nov, Jorge, build B): Fecha · Copia restaurada · Origen.
    private void llenarTablaRestauraciones() {
        android.widget.TableLayout tabla = tablaRestauraciones_XTl;
        if (tabla == null) return;
        tabla.removeAllViews();
        tabla.addView(filaTabla("Fecha", "Copia restaurada", "Origen", true));
        List<A32_Respaldos.Restauracion> lista = A32_Respaldos.ultimasRestauraciones(requireContext());
        filasRestauraciones = lista.size();
        actualizarTituloRestauraciones();
        if (lista.isEmpty()) {
            tabla.addView(filaTabla("—", "Sin restauraciones registradas", "", false));
        }
        int n = 0;
        for (A32_Respaldos.Restauracion r : lista) {
            android.widget.TableRow fila = filaTabla(
                    new SimpleDateFormat("d MMM HH:mm", es).format(new Date(r.fecha)),
                    (r.ok ? "" : "✗ falló: ") + A32_Respaldos.etiquetaRespaldo(r.nombre),
                    r.origen.isEmpty() ? "—" : r.origen, false);
            fila.setBackgroundColor(android.graphics.Color.parseColor(n++ % 2 == 0 ? "#FFFFFF" : "#EEF3FB"));
            tabla.addView(fila);
        }
    }

    // ⭐ NUEVO (18-nov, Jorge): tabla Tipo · Nombre · Fecha y hora, el último de cada tipo.
    private void llenarTablaUltimos() {
        android.widget.TableLayout tabla = tablaUltimos_XTl;
        if (tabla == null) return;
        tabla.removeAllViews();
        tabla.addView(filaTabla("Tipo", "Nombre", "Fecha y hora", true));
        List<A32_Respaldos.Fila> filas = A32_Respaldos.ultimosPorTipo();
        filasEnTabla = filas.size();
        actualizarTituloTabla();
        if (filas.isEmpty()) {
            tabla.addView(filaTabla("—", "Sin respaldos todavía", "", false));
        }
        int n = 0;
        for (A32_Respaldos.Fila f : filas) {
            android.widget.TableRow fila = filaTabla(f.tipo, f.nombre,
                    new SimpleDateFormat("d MMM HH:mm:ss", es).format(new Date(f.fecha)), false);
            // ⭐ (18-nov): sin bordes; filas alternas para leer fácil.
            fila.setBackgroundColor(android.graphics.Color.parseColor(n++ % 2 == 0 ? "#FFFFFF" : "#EEF3FB"));
            tabla.addView(fila);
        }
    }

    private android.widget.TableRow filaTabla(String a, String b, String c, boolean encabezado) {
        android.widget.TableRow fila = new android.widget.TableRow(requireContext());
        int pad = (int) (4 * getResources().getDisplayMetrics().density);
        for (String texto : new String[]{a, b, c}) {
            TextView t = new TextView(requireContext());
            t.setText(texto);
            t.setTextSize(12);
            t.setPadding(pad, pad, pad, pad);
            t.setTextColor(android.graphics.Color.parseColor(encabezado ? "#FFFFFF" : "#1F3FA6"));
            if (encabezado) {
                t.setBackgroundColor(android.graphics.Color.parseColor("#1F3FA6"));
                t.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            fila.addView(t);
        }
        return fila;
    }

    // ─────────────────────────────────────────────────────────────
    // Respaldar
    // ─────────────────────────────────────────────────────────────
    private void respaldarAhora() {
        mostrarEspera("Copiando y verificando la base…");
        A32_Respaldos.respaldarEnSegundoPlano(requireContext(), A32_Respaldos.TIPO_MANUAL, 5,
                new A32_Respaldos.Escucha() {
                    @Override
                    public void alTerminarLocal(A32_Respaldos.Resultado r) {
                        cerrarEspera();
                        if (!isAdded()) return;
                        refrescarEstado();
                        new AlertDialog.Builder(requireContext())
                                .setTitle(r.ok ? "✓ Respaldo verificado" : "✗ Respaldo no realizado")
                                .setMessage(r.ok
                                        ? r.archivo.getName() + "\nVersión v" + r.version + " · "
                                            + r.tablas + " tablas · " + r.filas + " filas"
                                            + "\n\nSubiendo a Drive… te aviso al terminar."
                                        : r.mensaje)
                                .setPositiveButton("Entendido", null)
                                .show();
                    }

                    @Override
                    public void alTerminarDrive(boolean ok, String mensaje) {
                        if (!isAdded()) return;
                        refrescarEstado();
                        Toast.makeText(requireContext(), (ok ? "✓ " : "✗ ") + mensaje, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Restaurar: elegir
    // ─────────────────────────────────────────────────────────────
    private void elegirRespaldoLocal() {
        List<File> locales = A32_Respaldos.respaldosLocales();
        if (locales.isEmpty()) {
            Toast.makeText(requireContext(), "No hay respaldos completos en este equipo.", Toast.LENGTH_LONG).show();
            return;
        }
        // ⭐ CAMBIO (21-nov, Jorge): cada copia muestra cuántas cuentas y transacciones tiene
        // (se cuentan fuera del hilo principal), para no elegir por error una copia vacía.
        mostrarEspera("Leyendo los respaldos…");
        hilo.execute(() -> {
            String[] items = new String[locales.size()];
            for (int i = 0; i < locales.size(); i++) {
                File f = locales.get(i);
                long[] ct = A32_Respaldos.cuentasYTransacciones(f);
                String contenido = ct[1] < 0 ? "no se pudo leer"
                        : (ct[0] == 0 || ct[1] == 0 ? "⚠ vacía · " : "") + miles(ct[0]) + " cuentas · " + miles(ct[1]) + " transacciones";
                items[i] = (i == 0 ? "★ " : "") + A32_Respaldos.etiquetaRespaldo(f.getName())
                        + "\n   " + tamano(f.length()) + " · " + contenido;
            }
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                new AlertDialog.Builder(requireContext())
                        .setTitle("Respaldos en este equipo")
                        .setItems(items, (d, i) -> revisarYConfirmar(locales.get(i), locales.get(i).getName(), "este equipo"))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    private static long valor(java.util.Map<String, Long> m, String tabla) {
        Long n = m == null ? null : m.get(tabla);
        return n == null ? 0 : n;
    }

    private String miles(long n) {
        return String.format(es, "%,d", n);
    }

    private void elegirRespaldoDrive() {
        mostrarEspera("Consultando Drive…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            List<String[]> lista;
            String error = null;
            try {
                lista = A32_Respaldos.respaldosEnDrive(app);
            } catch (Exception e) {
                lista = null;
                error = e.getMessage();
            }
            final List<String[]> fin = lista;
            final String err = error;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (fin == null) {
                    Toast.makeText(requireContext(), "No se pudo consultar Drive: " + err, Toast.LENGTH_LONG).show();
                    return;
                }
                if (fin.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay respaldos completos en Drive.", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] items = new String[fin.size()];
                for (int i = 0; i < fin.size(); i++) {
                    items[i] = (i == 0 ? "★ " : "") + A32_Respaldos.etiquetaRespaldo(fin.get(i)[1])
                            + "\n   " + tamano(Long.parseLong(fin.get(i)[2]));
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Respaldos en Drive")
                        .setItems(items, (d, i) -> descargarYConfirmar(fin.get(i)))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    private void descargarYConfirmar(String[] elegido) {
        mostrarEspera("Descargando " + elegido[1] + "…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            File archivo = null;
            String error = null;
            try {
                archivo = A32_Respaldos.descargarDeDrive(app, elegido[0], elegido[1]);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final File fin = archivo;
            final String err = error;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (fin == null) {
                    Toast.makeText(requireContext(), "No se pudo descargar: " + err + ". No se cambió nada.", Toast.LENGTH_LONG).show();
                    return;
                }
                revisarYConfirmar(fin, elegido[1], "Drive");
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Restaurar: revisar, confirmar, respaldar el estado actual y preparar
    // ─────────────────────────────────────────────────────────────
    private void revisarYConfirmar(File archivo, String nombre, String origen) {
        mostrarEspera("Revisando el respaldo…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A32_Respaldos.Revision rv = A32_Respaldos.revisarCandidato(archivo);
            // ⭐ NUEVO (21-nov, Jorge): comparar la copia con la base actual antes de confirmar.
            A32_Respaldos.Resultado actual = A32_Respaldos.estadoActual(app);
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (!rv.ok) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("✗ No se puede restaurar")
                            .setMessage(nombre + "\n\n" + rv.mensaje + "\n\nNo se cambió nada.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                long cCopia = valor(rv.filasPorTabla, "cuentas"), tCopia = valor(rv.filasPorTabla, "transacciones");
                long cActual = actual.ok ? valor(actual.filasPorTabla, "cuentas") : -1;
                long tActual = actual.ok ? valor(actual.filasPorTabla, "transacciones") : -1;
                boolean alerta = (tActual > 0 && tCopia < tActual / 2) || (cActual > 0 && cCopia < cActual / 2);
                // ⭐ NUEVO (21-nov, Jorge): una copia vacía (0 cuentas o 0 transacciones) nunca se
                // restaura, esté como esté la base actual: más riesgo que utilidad.
                boolean vacia = cCopia == 0 || tCopia == 0;
                if (vacia) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("✗ No se puede restaurar")
                            .setMessage("La copia está vacía (sin cuentas o sin transacciones); una copia así nunca se restaura."
                                    + "\n\nCuentas: copia " + miles(cCopia) + " · actual " + (cActual < 0 ? "?" : miles(cActual))
                                    + "\nTransacciones: copia " + miles(tCopia) + " · actual " + (tActual < 0 ? "?" : miles(tActual))
                                    + "\n\nRespaldo: " + A32_Respaldos.etiquetaRespaldo(nombre) + "\n" + nombre
                                    + "\n\nNo se cambió nada. Elige una copia con datos.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(alerta ? "⚠ Revisa antes de restaurar" : "Confirmar restauración")
                        .setMessage((alerta ? "⚠ La copia tiene muchas menos cuentas o transacciones que la base actual.\n\n" : "")
                                + "Cuentas: copia " + miles(cCopia) + " · actual " + (cActual < 0 ? "?" : miles(cActual))
                                + "\nTransacciones: copia " + miles(tCopia) + " · actual " + (tActual < 0 ? "?" : miles(tActual))
                                + "\n\nRespaldo: " + A32_Respaldos.etiquetaRespaldo(nombre) + "\n" + nombre
                                + "\nOrigen: " + origen
                                + "\nVersión de base: v" + rv.version
                                + "\nContenido: " + rv.tablas + " tablas · " + rv.filas + " filas"
                                + "\n\nSe reemplazará TODA la base actual por este respaldo."
                                + "\nAntes se guarda un respaldo del estado actual."
                                + "\nLuego la app se reinicia sola y queda restaurada.")
                        .setPositiveButton("Sí, restaurar", (d, w) -> respaldarEstadoActualYPreparar(archivo, nombre, origen))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    private void respaldarEstadoActualYPreparar(File archivo, String nombre, String origen) {
        mostrarEspera("Respaldando el estado actual…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A32_Respaldos.Resultado previo =
                    A32_Respaldos.respaldarBaseCompleta(app, A32_Respaldos.TIPO_ANTES_RESTAURAR, 5);
            boolean preparado = previo.ok && A32_Respaldos.prepararRestauracion(app, archivo, nombre,
                    origen, previo.archivo.getName());
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (!previo.ok) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("No se hizo ningún cambio")
                            .setMessage("No se pudo respaldar el estado actual:\n\n" + previo.mensaje
                                    + "\n\nPor seguridad, la restauración se detuvo.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                if (!preparado) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("No se hizo ningún cambio")
                            .setMessage("No se pudo preparar la restauración. La base actual sigue igual.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                // ⭐ CAMBIO (21-nov, Jorge, build B, opción A): la app se reinicia sola (antes
                // pedía cerrarla y abrirla a mano). El aviso del resultado sale al arrancar.
                mostrarEspera("Restaurando… la app se reinicia sola.");
                principal.postDelayed(() -> {
                    Activity a = getActivity();
                    if (a != null) A32_Respaldos.reiniciarApp(a);
                }, 800);
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ⭐ NUEVO (18-nov, Jorge): exportar una tabla de un respaldo .db a CSV (solo lectura)
    // ─────────────────────────────────────────────────────────────
    private void elegirRespaldoParaExportar(boolean todas) {
        List<File> locales = A32_Respaldos.respaldosLocales();
        if (locales.isEmpty()) {
            Toast.makeText(requireContext(), "No hay respaldos .db en este equipo.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[locales.size()];
        for (int i = 0; i < locales.size(); i++) {
            items[i] = (i == 0 ? "★ " : "") + A32_Respaldos.etiquetaRespaldo(locales.get(i).getName());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(todas ? "Respaldo .db a exportar completo" : "Elige el respaldo .db")
                .setItems(items, (d, i) -> {
                    if (todas) exportarTodas(locales.get(i));
                    else elegirTablaParaExportar(locales.get(i));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void elegirTablaParaExportar(File db) {
        List<String[]> tablas;
        try {
            tablas = A32_Respaldos.tablasDeRespaldo(db);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No se pudo abrir el respaldo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[tablas.size()];
        for (int i = 0; i < tablas.size(); i++) items[i] = tablas.get(i)[0] + "  (" + tablas.get(i)[1] + " filas)";
        new AlertDialog.Builder(requireContext())
                .setTitle("Tabla a exportar")
                .setItems(items, (d, i) -> exportarTabla(db, tablas.get(i)[0]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void exportarTabla(File db, String tabla) {
        mostrarEspera("Exportando " + tabla + "…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            File csv = A32_Respaldos.exportarTablaCsv(app, db, tabla);
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                new AlertDialog.Builder(requireContext())
                        .setTitle(csv != null ? "✓ Tabla exportada" : "✗ No se pudo exportar")
                        .setMessage(csv != null
                                ? csv.getName() + "\n\nCarpeta: Balance\nTambién se sube a Drive › Respaldos completos.\nSe abre en Sheets o Excel."
                                : "Revisa que el respaldo no esté dañado.")
                        .setPositiveButton("Entendido", null)
                        .show();
            });
        });
    }

    // ⭐ NUEVO (21-nov, Jorge, build B): todas las tablas a una carpeta con fecha.
    private void exportarTodas(File db) {
        mostrarEspera("Exportando todas las tablas…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A32_Respaldos.Exportacion r = A32_Respaldos.exportarTodasLasTablas(app, db, new A32_Respaldos.Escucha() {
                @Override public void alTerminarLocal(A32_Respaldos.Resultado x) { }
                @Override public void alTerminarDrive(boolean ok, String mensaje) {
                    Toast.makeText(app, (ok ? "✓ " : "✗ ") + mensaje, Toast.LENGTH_LONG).show();
                }
            });
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                new AlertDialog.Builder(requireContext())
                        .setTitle(r.ok ? "✓ Tablas exportadas" : "✗ No se pudo exportar")
                        .setMessage(r.ok
                                ? "Respaldo: " + A32_Respaldos.etiquetaRespaldo(db.getName())
                                    + "\n" + r.mensaje
                                    + "\n\nCarpeta: " + A32_Respaldos.CARPETA_EXPORTACIONES + "/" + r.carpeta.getName()
                                    + "\nTambién se sube a Drive › Backups (te aviso al terminar)."
                                    + "\nSe conservan las 5 exportaciones más recientes."
                                : r.mensaje)
                        .setPositiveButton("Entendido", null)
                        .show();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ⭐ NUEVO (22-nov, Jorge, etapa S1): Enviar a Sheets
    // ─────────────────────────────────────────────────────────────
    private void elegirEnvioASheets() {
        // ⭐ (22-nov, Jorge): opciones con estilo de menú (botones verdes numerados).
        A1BASES.A34_MenuOpciones.mostrar(requireContext(), "Enviar a Sheets",
                new String[]{"Transacciones, cuentas y catálogos", "Plantilla de saldos iniciales"},
                new String[]{"Para corregir registros. Carpeta Sheets › Correcciones.",
                        "Una fila por cuenta, para llenar el valor. Carpeta Sheets › Saldos iniciales."},
                i -> revisarSesionYEnviar(i == 1));
    }

    /**
     * ⭐ NUEVO (22-nov, Jorge): una sola sesión activa por equipo. Si hay hojas en curso (de
     * correcciones o de saldos), se ofrece continuar con ellas o empezar de nuevo (las actuales
     * van a la papelera de Drive).
     */
    private void revisarSesionYEnviar(boolean plantillaSaldos) {
        mostrarEspera("Revisando hojas en curso…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            List<A1BASES.A36_TraerSheets.HojaDrive> activas;
            try {
                activas = A1BASES.A36_TraerSheets.sesionActiva(app);
            } catch (Exception e) {
                activas = new java.util.ArrayList<>();
            }
            final List<A1BASES.A36_TraerSheets.HojaDrive> fin = activas;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (fin.isEmpty()) {
                    enviarASheets(plantillaSaldos);
                    return;
                }
                boolean deSaldos = A1BASES.A36_TraerSheets.TIPO_SALDOS.equals(fin.get(0).tipo);
                long min = A1BASES.A36_TraerSheets.minutosRestantes(app, fin.get(0));
                StringBuilder sb = new StringBuilder("Hay una sesión de ")
                        .append(deSaldos ? "Saldos iniciales" : "Correcciones").append(" en curso (⏱ quedan ")
                        .append(A1BASES.A36_TraerSheets.textoMinutos(min)).append("):\n");
                for (A1BASES.A36_TraerSheets.HojaDrive h : fin) sb.append("• ").append(h.titulo()).append('\n');
                sb.append("\nSolo se trabaja una sesión a la vez en este equipo.");
                new AlertDialog.Builder(requireContext())
                        .setTitle("Sesión de Sheets en curso")
                        .setMessage(sb.toString())
                        .setPositiveButton("Continuar con ella", (d, w) -> mostrarHojasActivas(fin))
                        .setNeutralButton("Empezar de nuevo", (d, w) -> descartarYEnviar(fin, plantillaSaldos))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    // ⭐ CAMBIO (22-nov, Jorge): "Continuar con ella" lleva a la hoja donde estaba, con el tiempo
    // que le queda; con el estilo de menú.
    private void mostrarHojasActivas(List<A1BASES.A36_TraerSheets.HojaDrive> hojas) {
        long min = A1BASES.A36_TraerSheets.minutosRestantes(requireContext(), hojas.get(0));
        String[] opciones = new String[hojas.size()];
        String[] notas = new String[hojas.size()];
        for (int i = 0; i < opciones.length; i++) {
            opciones[i] = "Ir a " + hojas.get(i).titulo();
            notas[i] = hojas.get(i).nombre;
        }
        A1BASES.A34_MenuOpciones.mostrar(requireContext(), "Continúa donde estabas",
                "Puedes seguir corrigiendo en Sheets en la hoja donde estabas.\n⏱ Te quedan "
                        + A1BASES.A36_TraerSheets.textoMinutos(min) + " de la ventana de "
                        + A1BASES.A36_TraerSheets.VENTANA_HORAS + " h.",
                opciones, notas, "Listo",
                i -> abrirEnlace("https://docs.google.com/spreadsheets/d/" + hojas.get(i).id + "/edit"));
    }

    private void descartarYEnviar(List<A1BASES.A36_TraerSheets.HojaDrive> hojas, boolean plantillaSaldos) {
        mostrarEspera("Mandando las hojas en curso a la papelera…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            String error = null;
            try {
                A1BASES.A36_TraerSheets.descartar(app, hojas);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (err != null) {
                    Toast.makeText(requireContext(), "No se pudieron descartar: " + err, Toast.LENGTH_LONG).show();
                    return;
                }
                enviarASheets(plantillaSaldos);
            });
        });
    }

    private void enviarASheets(boolean plantillaSaldos) {
        mostrarEspera("Creando las hojas en Drive…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A1BASES.A33_Sheets.Envio r = plantillaSaldos
                    ? A1BASES.A33_Sheets.enviarPlantillaSaldos(app)
                    : A1BASES.A33_Sheets.enviarTablas(app);
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (!r.ok) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("✗ No se crearon las hojas")
                            .setMessage(r.mensaje)
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                String[] items = new String[r.hojas.size()];
                for (int i = 0; i < items.length; i++) {
                    A1BASES.A33_Sheets.Hoja h = r.hojas.get(i);
                    items[i] = "↗ " + h.titulo + " · " + miles(h.filas) + " filas\n   " + h.nombre;
                }
                if (!r.aviso.isEmpty()) {
                    // ⭐ (22-nov): aviso de saldos iniciales con movimientos ya registrados.
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Saldos iniciales")
                            .setMessage(r.aviso)
                            .setPositiveButton("Entendido", null)
                            .show();
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("✓ " + r.mensaje)
                        .setItems(items, (d, i) -> abrirEnlace(r.hojas.get(i).enlace))
                        .setPositiveButton("Listo", null)
                        .show();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ⭐ NUEVO (22-nov, Jorge, etapa S2): Traer de Sheets
    // Elegir hoja → revisar (vista previa con cambios y errores) → respaldo "antes" → aplicar →
    // resumen. Con un solo error no se aplica nada.
    // ─────────────────────────────────────────────────────────────
    private void elegirHojaParaTraer() {
        mostrarEspera("Buscando hojas en Drive…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            List<A1BASES.A36_TraerSheets.HojaDrive> hojas;
            String error = null;
            try {
                hojas = A1BASES.A36_TraerSheets.listar(app);
            } catch (Exception e) {
                hojas = new java.util.ArrayList<>();
                error = e.getMessage();
            }
            final List<A1BASES.A36_TraerSheets.HojaDrive> fin = hojas;
            final String err = error;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                if (err != null || fin.isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(err != null ? "✗ No se pudo leer Drive" : "No hay hojas para traer")
                            .setMessage(err != null ? err : "Primero usa \"Enviar a Sheets\", corrige la hoja y vuelve aquí.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                String[] items = new String[fin.size()];
                for (int i = 0; i < items.length; i++) {
                    A1BASES.A36_TraerSheets.HojaDrive h = fin.get(i);
                    items[i] = (i == 0 ? "★ " : "") + h.titulo()
                            + (h.aplicaciones > 0 ? " · aplicada " + h.aplicaciones + (h.aplicaciones == 1 ? " vez" : " veces") : "")
                            + tiempoHoja(fin.get(i))
                            + " · modificada "
                            + new SimpleDateFormat("d MMM HH:mm", es).format(new Date(h.modificada)) + "\n   " + h.nombre;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Elige la hoja a traer")
                        .setItems(items, (d, i) -> revisarHoja(fin.get(i)))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    // ⭐ (22-nov, Jorge): tiempo que le queda a cada hoja en la lista.
    private String tiempoHoja(A1BASES.A36_TraerSheets.HojaDrive h) {
        long min = A1BASES.A36_TraerSheets.minutosRestantes(requireContext(), h);
        if (min == Long.MIN_VALUE) return " · ✗ sin base en este equipo";
        if (min <= 0) return " · ✗ vencida";
        return " · ⏱ quedan " + A1BASES.A36_TraerSheets.textoMinutos(min);
    }

    private void revisarHoja(A1BASES.A36_TraerSheets.HojaDrive hoja) {
        mostrarEspera("Revisando la hoja…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A1BASES.A36_TraerSheets.Analisis a;
            try {
                a = A1BASES.A36_TraerSheets.analizar(app, hoja, A1BASES.A36_TraerSheets.descargar(app, hoja.id));
            } catch (Exception e) {
                a = new A1BASES.A36_TraerSheets.Analisis();
                a.hoja = hoja;
                a.errores.add("No se pudo descargar la hoja: " + e.getMessage());
            }
            final A1BASES.A36_TraerSheets.Analisis an = a;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                mostrarVistaPrevia(an);
            });
        });
    }

    private void mostrarVistaPrevia(A1BASES.A36_TraerSheets.Analisis a) {
        String conteo = "Nuevas: " + a.nuevas + " · Modificadas: " + a.modificadas
                + " · Borradas: " + a.borradas + " · Sin cambio: " + a.sinCambio;
        // ⭐ (22-nov): edad de la hoja y cambios de la app que se conservan (fusión de tres versiones).
        if (a.minutosRestantes != Long.MIN_VALUE && a.minutosRestantes > 0) {
            conteo += "\n⏱ A esta hoja le quedan " + A1BASES.A36_TraerSheets.textoMinutos(a.minutosRestantes)
                    + " (vale " + A1BASES.A36_TraerSheets.VENTANA_HORAS + " h).";
        }
        if (a.conservadosApp > 0) {
            conteo += "\nCambios hechos en la app desde que se preparó la hoja: " + a.conservadosApp + " (se conservan).";
        }
        if (!a.errores.isEmpty()) {
            StringBuilder sb = new StringBuilder("No se aplicó nada. Corrige la hoja en Sheets y vuelve a traerla.\n\n");
            for (int i = 0; i < Math.min(40, a.errores.size()); i++) sb.append("• ").append(a.errores.get(i)).append('\n');
            if (a.errores.size() > 40) sb.append("… y ").append(a.errores.size() - 40).append(" más.\n");
            new AlertDialog.Builder(requireContext())
                    .setTitle("✗ La hoja tiene " + a.errores.size() + (a.errores.size() == 1 ? " error" : " errores"))
                    .setView(vistaConAlerta(a.hoja, sb.toString()))
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        if (!a.hayCambios()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Sin cambios")
                    .setMessage("La hoja " + a.hoja.titulo() + " es igual a la app.\n" + conteo)
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        int total = a.nuevas + a.modificadas + a.borradas;
        StringBuilder sb = new StringBuilder(conteo).append("\n\n");
        for (String c : a.cambios) sb.append("• ").append(c).append('\n');
        if (total > a.cambios.size()) sb.append("… y ").append(total - a.cambios.size()).append(" más.\n");
        sb.append("\nSe hará: respaldo completo → aplicar los cambios → poner la hoja al día.");
        new AlertDialog.Builder(requireContext())
                .setTitle("Revisa antes de aplicar: " + a.hoja.titulo())
                .setView(vistaConAlerta(a.hoja, sb.toString(), a.conservadosApp))
                .setPositiveButton("Aplicar", (d, w) -> aplicarHoja(a))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * ⭐ NUEVO (22-nov, Jorge): si la hoja ya se aplicó antes, una franja de color arriba avisa
     * cuántas veces y cuántos cambios lleva: la app ya tiene esos cambios y esta traída se
     * compara contra la app actual.
     */
    private View vistaConAlerta(A1BASES.A36_TraerSheets.HojaDrive hoja, String mensaje) {
        return vistaConAlerta(hoja, mensaje, 0);
    }

    private View vistaConAlerta(A1BASES.A36_TraerSheets.HojaDrive hoja, String mensaje, int conservados) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout caja = new LinearLayout(requireContext());
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setPadding((int) (22 * d), (int) (8 * d), (int) (22 * d), 0);
        StringBuilder aviso = new StringBuilder();
        if (hoja.aplicaciones > 0) {
            aviso.append("⚠ Esta hoja ya se aplicó ").append(hoja.aplicaciones)
                    .append(hoja.aplicaciones == 1 ? " vez" : " veces").append(" y lleva ").append(hoja.cambiosTotales)
                    .append(" cambios en la base de la app. Esta traída se compara contra la app actual y suma más cambios.");
        }
        if (conservados > 0) {
            if (aviso.length() > 0) aviso.append("\n");
            aviso.append("⚠ La app cambió ").append(conservados)
                    .append(" registros desde que se preparó la hoja: se conservan; revisa que no choquen con tu corrección.");
        }
        if (aviso.length() > 0) {
            TextView alerta = new TextView(requireContext());
            alerta.setText(aviso.toString());
            alerta.setTextColor(android.graphics.Color.WHITE);
            alerta.setTextSize(14);
            alerta.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            alerta.setBackgroundColor(android.graphics.Color.parseColor("#E441F1"));
            alerta.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * d);
            caja.addView(alerta, lp);
        }
        TextView texto = new TextView(requireContext());
        texto.setText(mensaje);
        texto.setTextSize(15);
        caja.addView(texto);
        android.widget.ScrollView desplazable = new android.widget.ScrollView(requireContext());
        desplazable.addView(caja);
        return desplazable;
    }

    private void aplicarHoja(A1BASES.A36_TraerSheets.Analisis a) {
        mostrarEspera("Aplicando los cambios…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            String titulo;
            String texto;
            A32_Respaldos.Resultado antes = A32_Respaldos.puntoDeRestauracion(app, "antes-" + a.operacion());
            if (!antes.ok) {
                titulo = "✗ No se hizo ningún cambio";
                texto = "No se pudo hacer el respaldo completo:\n" + antes.mensaje;
            } else {
                try {
                    A1BASES.A36_TraerSheets.aplicar(app, a);
                    A32_Respaldos.Resultado despues = A32_Respaldos.puntoDeRestauracion(app, "despues-" + a.operacion());
                    titulo = "✓ " + a.hoja.titulo() + " traída de Sheets";
                    texto = A32_Respaldos.resumenOperacion(app, a.operacion(), "Traer de Sheets: " + a.hoja.titulo(),
                            antes, despues, A1BASES.A36_TraerSheets.detalle(a));
                    // ⭐ (22-nov): la hoja se pone al día con la app para seguir corrigiendo.
                    texto += "\n" + A1BASES.A36_TraerSheets.actualizarTrasAplicar(app, a);
                } catch (Exception e) {
                    titulo = "✗ No se aplicó nada";
                    texto = "Error al aplicar: " + e.getMessage() + "\n\nLa base quedó como estaba.";
                }
            }
            final String t = titulo, m = texto;
            principal.post(() -> {
                cerrarEspera();
                if (!isAdded()) return;
                new AlertDialog.Builder(requireContext())
                        .setTitle(t)
                        .setMessage(m)
                        .setPositiveButton("Entendido", null)
                        .show();
                refrescarEstado();
            });
        });
    }

    private void abrirEnlace(String enlace) {
        if (enlace == null) {
            Toast.makeText(requireContext(), "La hoja no trae enlace; ábrela desde Drive.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(enlace)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No se pudo abrir: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Equipo, navegación y utilidades
    // ─────────────────────────────────────────────────────────────
    private void dialogoCambiarEquipo() {
        EditText campo = new EditText(requireContext());
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        campo.setText(A32_Respaldos.equipo(requireContext()));
        new AlertDialog.Builder(requireContext())
                .setTitle("Nombre corto de este equipo")
                .setMessage("Va al inicio del nombre de cada respaldo (por ejemplo 13T o PAD5).")
                .setView(campo)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nuevo = campo.getText().toString().trim().replaceAll("[^A-Za-z0-9-]", "");
                    if (nuevo.isEmpty()) {
                        Toast.makeText(requireContext(), "Escribe letras o números.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    A32_Respaldos.cambiarEquipo(requireContext(), nuevo);
                    refrescarEstado();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ⭐ CAMBIO (18-nov): se abre por el MISMO camino que tocar el menú lateral. Antes iba
    // directo por la navegación y el menú quedaba desincronizado: luego no dejaba volver a
    // "Respaldos".
    private void irA(int destino) {
        try {
            com.google.android.material.navigation.NavigationView menu =
                    requireActivity().findViewById(R.id.nav_view);
            menu.getMenu().performIdentifierAction(destino, 0);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No se pudo abrir la pantalla.", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarEspera(String texto) {
        cerrarEspera();
        if (!isAdded()) return;
        espera = new AlertDialog.Builder(requireContext())
                .setMessage(texto)
                .setCancelable(false)
                .show();
    }

    private void cerrarEspera() {
        if (espera != null && espera.isShowing()) espera.dismiss();
        espera = null;
    }

    private String fecha(long ms) {
        return new SimpleDateFormat("d MMM yyyy HH:mm", es).format(new Date(ms));
    }

    private String tamano(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(es, "%.2f MB", bytes / 1048576.0);
        return String.format(es, "%d KB", Math.max(1, bytes / 1024));
    }
}
