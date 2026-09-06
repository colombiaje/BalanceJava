package A1BASES;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class A4_TemplatePDF {

    private Context context;
    private  File pdfFile;
    public  static Document document;
    private PdfWriter pdfWriter;
    private Paragraph paragraph;
    private Font ftitle = new Font (Font.FontFamily.TIMES_ROMAN,20,Font.BOLD);
    private Font fSubtitle = new Font (Font.FontFamily.TIMES_ROMAN,18,Font.BOLD);
    private Font fText = new Font (Font.FontFamily.TIMES_ROMAN,12,Font.BOLD);
    private Font fHigthText = new Font (Font.FontFamily.TIMES_ROMAN,15,Font.BOLD, BaseColor.RED);
    private FragmentManager supportFragmentManager;
    private OutputStream outputStream;
    private PdfReader reader;
    Integer numero_Integer = null;
    //public static String numeroPagina_String;


    public A4_TemplatePDF(Context context) {
        this.context = context;
    }

    public void  openDocument () {
        createFile();

        try {

            document = new Document(PageSize.A4);
            pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

        }

        catch (Exception e) {
            Log.e("openDocument: ", e.toString() );
        }
    }

    public void createFile () {

        String nombreCarpetaDestino_String="Pdfs";

        String directorioDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
        String rutaDestinoYnombreCarpetaDestino_String = directorioDestino_String+nombreCarpetaDestino_String;
        File rutaFinalArchivo_File = new File(rutaDestinoYnombreCarpetaDestino_String);

        try {

            if (!rutaFinalArchivo_File.exists()) {
                rutaFinalArchivo_File.mkdirs();
                pdfFile = new File(rutaFinalArchivo_File, "Informe por cuenta.pdf");

            }

            if (rutaFinalArchivo_File.exists()) {
                rutaFinalArchivo_File.mkdirs();
                pdfFile = new File(rutaFinalArchivo_File, "Informe por cuenta.pdf");

            }
        }
        catch (Exception e) {
        }

    }

    public  void closeDocument() {

        document.close();
    }

    public void onEndPage(PdfWriter writer, Document document) {
        final int currentPageNumber = writer.getCurrentPageNumber();

        if (currentPageNumber == 1) {
            return;
        }

        try {
            final Rectangle pageSize = document.getPageSize();
            final PdfContentByte directContent = writer.getDirectContent();

            directContent.setColorFill(BaseColor.GRAY);
            directContent.setFontAndSize(BaseFont.createFont(), 10);

            directContent.setTextMatrix(pageSize.getRight(40), pageSize.getBottom(30));
            directContent.showText(String.valueOf(currentPageNumber));

        } catch (DocumentException | IOException e) {
            Log.d(TAG, "onEndPage: ");
        }
    }

    public  void addMetaData (String title, String subject, String author) {

        document.addTitle(title);
        document.addSubject(subject);
        document.addTitle(author);

    }

    public void addTitles (String title, String subtitle, String date) {
        paragraph = new Paragraph();
        addChildP(new Paragraph(title,ftitle));
        addChildP(new Paragraph(subtitle,fSubtitle));
        //addChildP(new Paragraph("Generado: "+date,fHigthText));
        addChildP(new Paragraph(""+date,fHigthText));
        paragraph.setSpacingAfter(10);
        try {
            document.add(paragraph);
        }
        catch (Exception e) {
            Log.e( "addTitles: ",e.toString() );
        }
    }

    private void addChildP (Paragraph chilParagraph) {

        chilParagraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.add(chilParagraph);

    }

    public String addParagraph (String text) {

        paragraph = new Paragraph(text,fText);
        paragraph.setSpacingAfter(10);
        paragraph.setSpacingBefore(1);

        try {
            document.add(paragraph);
        }
        catch (Exception e) {
            Log.e( "addParagraph: ",e.toString() );
        }
        return text;
    }

    public void createSimpleTable(String[][] data) {
        try {
            PdfPTable pdfPTable = new PdfPTable(2); // 2 columnas

            // Configurar los anchos de las columnas (personalizables)
            float[] columnWidths = {2f, 1f}; // Anchos relativos de las columnas (ejemplo)
            pdfPTable.setWidths(columnWidths);
            pdfPTable.setWidthPercentage(50); // La tabla ocupa la mitad del ancho de la página

            // Añadir las filas
            for (int i = 0; i < data.length; i++) {
                PdfPCell cell1 = new PdfPCell(new Phrase(data[i][0]));
                PdfPCell cell2 = new PdfPCell(new Phrase(data[i][1]));

                // Alinear el texto de la primera columna a la izquierda
                cell1.setHorizontalAlignment(Element.ALIGN_LEFT);

                // Alinear el texto de la segunda columna al centro
                cell2.setHorizontalAlignment(Element.ALIGN_CENTER);

                // Configurar la altura de las filas según el size del texto
                cell1.setPadding(5f); // Espacio de relleno para el texto
                cell1.setMinimumHeight(cell1.getPhrase().getLeading() + 6f); // Altura mínima de la celda

                cell2.setPadding(5f);
                cell2.setMinimumHeight(cell2.getPhrase().getLeading() + 6f);

                pdfPTable.addCell(cell1);
                pdfPTable.addCell(cell2);
            }

            // Añadir la tabla al documento
            document.add(pdfPTable);
        } catch (Exception e) {
            Log.e("createSimpleTable: ", e.toString());
        }
    }

    public void createTable(String[] header, ArrayList<String[]> clients) {
        try {
            paragraph = new Paragraph();
            paragraph.setFont(fText);
            PdfPTable pdfPTable = new PdfPTable(header.length);
            int[] columnWidths = {75, 50, 275, 50}; // Anchos de cada columna (ejemplo)
            pdfPTable.setWidths(columnWidths);
            pdfPTable.setWidthPercentage(100);
            PdfPCell pdfPCell;
            int indexC = 0;
            while (indexC < header.length) {
                pdfPCell = new PdfPCell(new Phrase(header[indexC++], fSubtitle));
                pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                pdfPCell.setBackgroundColor(BaseColor.GREEN);
                pdfPTable.addCell(pdfPCell);
            }

            for (int indexR = 0; indexR < clients.size(); indexR++) {
                String[] row = clients.get(indexR);
                for (indexC = 0; indexC < header.length; indexC++) {
                    pdfPCell = new PdfPCell(new Phrase(row[indexC]));
                    if (indexC == 2) {
                        pdfPCell.setHorizontalAlignment(Element.ALIGN_LEFT); // Alineación a la izquierda para la tercera columna
                    } else {
                        pdfPCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    }
                    pdfPTable.addCell(pdfPCell);
                }
            }

            paragraph.add(pdfPTable);
            document.add(paragraph);
        } catch (Exception e) {
            Log.e("createTable: ", e.toString());
        }
    }

}
