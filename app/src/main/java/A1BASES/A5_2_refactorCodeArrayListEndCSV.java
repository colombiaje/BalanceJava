package A1BASES;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class A5_2_refactorCodeArrayListEndCSV {

    private List<String> textList;
    private String nombreArchivo;

    public A5_2_refactorCodeArrayListEndCSV(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
        textList = new ArrayList<>();
    }

    public void addToTextList(String[] texts) {
        Collections.addAll(textList, texts);
    }

    public void saveToCSV() {
        try {
            String rutaDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
            String csvFile = rutaDestino_String + nombreArchivo;
            File archivo_File = new File(csvFile);

            if (textList.size() == 0) {
                if (archivo_File.exists()) {
                    archivo_File.delete();
                }
                return;
            } else {
                if (archivo_File.exists()) {
                    archivo_File.delete();
                }
            }

            FileWriter writer = new FileWriter(csvFile);

            for (String text : textList) {
                writer.append(text);
                writer.append("\n");
            }

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    }
    
