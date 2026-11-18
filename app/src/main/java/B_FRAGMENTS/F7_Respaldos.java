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
 * ("antes-restaurar"), se deja la base lista para reemplazarse y la app se cierra; al abrirla
 * de nuevo se aplica el cambio (C2_ActivityMenu.onCreate, antes de abrir la base).
 */
public class F7_Respaldos extends Fragment {

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private TextView estado_XTv, equipo_XTv, resultadoSelector_XTv;
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
        verAvanzado.setOnCheckedChangeListener((b, marcado) ->
                areaAvanzada.setVisibility(marcado ? View.VISIBLE : View.GONE));
        v.findViewById(R.id.irCuentasBackups_XBt).setOnClickListener(x -> irA(R.id.navf2));
        v.findViewById(R.id.irCierres_XBt).setOnClickListener(x -> irA(R.id.navf5));
        v.findViewById(R.id.probarSelector_XBt).setOnClickListener(x -> selectorCarpeta.launch(null));

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
        String[] items = new String[locales.size()];
        for (int i = 0; i < locales.size(); i++) {
            File f = locales.get(i);
            items[i] = (i == 0 ? "★ " : "") + f.getName() + "\n   " + fecha(f.lastModified()) + " · " + tamano(f.length());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Respaldos en este equipo")
                .setItems(items, (d, i) -> revisarYConfirmar(locales.get(i), locales.get(i).getName(), "este equipo"))
                .setNegativeButton("Cancelar", null)
                .show();
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
                    items[i] = (i == 0 ? "★ " : "") + fin.get(i)[1] + "\n   " + tamano(Long.parseLong(fin.get(i)[2]));
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
        hilo.execute(() -> {
            A32_Respaldos.Revision rv = A32_Respaldos.revisarCandidato(archivo);
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
                new AlertDialog.Builder(requireContext())
                        .setTitle("Confirmar restauración")
                        .setMessage("Respaldo: " + nombre + "\nOrigen: " + origen
                                + "\nVersión de base: v" + rv.version
                                + "\nContenido: " + rv.tablas + " tablas · " + rv.filas + " filas"
                                + "\n\nSe reemplazará TODA la base actual por este respaldo."
                                + "\nAntes se guarda un respaldo del estado actual."
                                + "\nLuego la app se cierra; al abrirla de nuevo queda restaurada.")
                        .setPositiveButton("Sí, restaurar", (d, w) -> respaldarEstadoActualYPreparar(archivo, nombre))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    private void respaldarEstadoActualYPreparar(File archivo, String nombre) {
        mostrarEspera("Respaldando el estado actual…");
        final android.content.Context app = requireContext().getApplicationContext();
        hilo.execute(() -> {
            A32_Respaldos.Resultado previo =
                    A32_Respaldos.respaldarBaseCompleta(app, A32_Respaldos.TIPO_ANTES_RESTAURAR, 5);
            boolean preparado = previo.ok && A32_Respaldos.prepararRestauracion(app, archivo, nombre);
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
                new AlertDialog.Builder(requireContext())
                        .setTitle("✓ Restauración lista")
                        .setMessage("Estado actual respaldado:\n" + previo.archivo.getName()
                                + "\n\nLa app se cerrará ahora. Ábrela de nuevo y quedará restaurada desde:\n" + nombre)
                        .setCancelable(false)
                        .setPositiveButton("Cerrar la app", (d, w) -> cerrarApp())
                        .show();
            });
        });
    }

    private void cerrarApp() {
        Activity a = getActivity();
        if (a != null) a.finishAffinity();
        principal.postDelayed(() -> Runtime.getRuntime().exit(0), 300);
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

    private void irA(int destino) {
        try {
            NavController nav = Navigation.findNavController(requireActivity(), R.id.contenedor_fragments_f0_Xf);
            nav.navigate(destino);
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
