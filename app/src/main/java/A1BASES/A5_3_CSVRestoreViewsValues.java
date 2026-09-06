package A1BASES;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class
A5_3_CSVRestoreViewsValues {

    private String csvFile;

    public A5_3_CSVRestoreViewsValues(String csvFile) {
        this.csvFile = csvFile;
    }

    public List<String> readCSV() {
        List<String> textList = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(csvFile));
            String line;

            while ((line = reader.readLine()) != null) {
                textList.add(line);
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return textList;
    }
}

