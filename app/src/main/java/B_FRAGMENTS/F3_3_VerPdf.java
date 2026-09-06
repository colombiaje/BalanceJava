package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static B_FRAGMENTS.F3_2_VerItemTransaccion.pdfFile;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jj.appbalancev31.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import A1BASES.A1_1_AyudanteBD;
import D_ADAPTERS.D_F3_3_PdfPageAdapter;

public class F3_3_VerPdf extends DialogFragment {
    private Button volver_XBt, compartirEstadoDeCuenta_XBt;
    private RecyclerView pdfRecyclerView;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private A1_1_AyudanteBD ayudante_Class;
    private SQLiteDatabase db;

    private List<Bitmap> pdfPages;
    private D_F3_3_PdfPageAdapter pdfPageAdapter;

    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;


    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.f3_4_1_ver_pdf, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = rootView.findViewById(R.id.calculadoraLibre_XBt);

        // Configurar RecyclerView
        pdfRecyclerView = rootView.findViewById(R.id.pdfRecyclerView);
        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // Botones
        volver_XBt = rootView.findViewById(R.id.volver_XBt);
        compartirEstadoDeCuenta_XBt = rootView.findViewById(R.id.compartirEstadoDeCuenta_XBt);

        // Eventos de botones
        compartirEstadoDeCuenta_XBt.setOnClickListener(v -> compartirPDFConTablaCuenta());

        volver_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {
                    /*F32_VerItemTransaccion f32_verItemTransaccion = new F32_VerItemTransaccion();
                    f32_verItemTransaccion.show(getFragmentManager().beginTransaction(), "");
                    dismiss();*/

                }
                catch (Exception exception) {}

                dismiss();

            }
        });


        // Abrir la base de datos
        if (ayudante_Class != null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
            db = ayudante_Class.getReadableDatabase();
        }

        // Cargar el PDF
        try {
            loadPdf(pdfFile);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Error al cargar el archivo PDF", Toast.LENGTH_SHORT).show();
        }

        return rootView;
    }

    private void loadPdf(File pdfFile) throws IOException {
        parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
        pdfRenderer = new PdfRenderer(parcelFileDescriptor);

        pdfPages = new ArrayList<>();
        for (int i = 0; i < pdfRenderer.getPageCount(); i++) {
            Bitmap bitmap = renderPage(i);
            pdfPages.add(bitmap);
        }

        pdfPageAdapter = new D_F3_3_PdfPageAdapter(pdfPages);
        pdfRecyclerView.setAdapter(pdfPageAdapter);
    }

    private Bitmap renderPage(int pageIndex) {
        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        return bitmap;
    }

    private void compartirPDFConTablaCuenta() {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(getActivity(), "No se encontró el archivo PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri pdfUri = FileProvider.getUriForFile(
                requireContext(),
                "com.jj.appbalancev31.fileprovider", // Debe coincidir con el Manifest
                pdfFile
        );

        Intent compartirIntent = new Intent(Intent.ACTION_SEND);
        compartirIntent.setType("application/pdf");
        compartirIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        compartirIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(compartirIntent, "Compartir PDF"));
    }


    @Override
    public void onStop() {
        super.onStop();
        if (pdfRenderer != null) pdfRenderer.close();
        if (parcelFileDescriptor != null) {
            try { parcelFileDescriptor.close(); } catch (IOException e) { e.printStackTrace(); }
        }
        if (db != null && db.isOpen()) db.close();
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }
}
