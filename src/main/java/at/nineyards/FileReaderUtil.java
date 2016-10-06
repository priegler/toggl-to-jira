package at.nineyards;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Peter on 20.06.2016.
 */
public class FileReaderUtil {


    /**
     * @param fileName fileName of the file to read
     * @return
     */
    public static Map<String, String> readConfig(String fileName){
        Map<String, String> config = new HashMap<String, String>();
        // This will reference one line at a time
        String line = null;

        try {
            // at.nineyards.FileReaderUtil reads text files in the default encoding.
            FileReader fileReader =
                    new FileReader(fileName);

            // Always wrap at.nineyards.FileReaderUtil in BufferedReader.
            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                if(line.startsWith("#")) continue;
                if(line.length() == 0) continue;
                String[] properties = line.split("=");
                if(properties.length == 2){

                    String key = properties[0].trim().toLowerCase();
                    String valueWithComment = properties[1];

                    String[] stripped = valueWithComment.split("#");
                    String value = stripped[0].trim().replace("'", "");
                    config.put(key, value);
                }
                else {
                    System.out.println("ignoring: "+ line);
                }
            }

            // Always close files.
            bufferedReader.close();
        }
        catch(FileNotFoundException ex) {
            System.out.println(
                    "Unable to open file '" +
                            fileName + "'");
        }
        catch(IOException ex) {
            System.out.println(
                    "Error reading file '"
                            + fileName + "'");
            // Or we could just do this:
            // ex.printStackTrace();
        }
        return config;
    }

}
