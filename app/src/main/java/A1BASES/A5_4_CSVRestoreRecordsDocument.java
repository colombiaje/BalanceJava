package A1BASES;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class A5_4_CSVRestoreRecordsDocument {
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> itemsListaBackup_ArrayListTipoT;

    public List<A3_2_TipoTransaccionesGetsYSets> restoreorigenBackupCsvCRUDListaDocumentoOfDocumentJobToFinish(String nombreArchivo) {

        itemsListaBackup_ArrayListTipoT = new ArrayList<A3_2_TipoTransaccionesGetsYSets>();

        File carpeta_File = new File(Environment.getExternalStorageDirectory() + "/Balance/");
        String nombreArchivo_String = carpeta_File.toString() + "/" + nombreArchivo;

        try (BufferedReader br = Files.newBufferedReader(Paths.get(nombreArchivo_String),
                StandardCharsets.ISO_8859_1)) {

            String line = br.readLine();

            while (line != null) {
                String[] attributes = line.split(",");
                A3_2_TipoTransaccionesGetsYSets contenidoCSV = contentForRestoreorigenBackupCsvCRUDListaDocumentoOfDocumentJobToFinish(attributes);
                itemsListaBackup_ArrayListTipoT.add(contenidoCSV);
                line = br.readLine();
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return itemsListaBackup_ArrayListTipoT;
    }

    private static A3_2_TipoTransaccionesGetsYSets contentForRestoreorigenBackupCsvCRUDListaDocumentoOfDocumentJobToFinish(String[] metadata) {

        try {
            String _c1 = metadata[0];
            String _c2 = metadata[1];
            String _c3 = metadata[2];
            String _c4 = metadata[3];
            Integer _c5 = Integer.valueOf(String.valueOf(metadata[4]));
            String _c6 = metadata[5];
            String _c7 = metadata[6];
            Integer _c8 = Integer.parseInt(metadata[7]);
            String _c9 = metadata[8];
            String _c10 = metadata[9];
            String _c11 = metadata[10];
            String _c12 = metadata[11];
            String _c13 = metadata[12];

            return new A3_2_TipoTransaccionesGetsYSets(_c1,_c2,_c3,_c4,_c5,_c6,_c7,_c8,_c9,_c10,_c11,_c12,_c13);

        } catch (NumberFormatException e) {
            e.printStackTrace();

        }
        return null;
    }

}