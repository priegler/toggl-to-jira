import java.io.Console;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by Peter on 09.06.2016.
 */
public class Util {

    public static String readStringFromStdin(){
        Scanner reader = new Scanner(System.in);
        return reader.next();
    }

    public static String readPasswordFromStdin(){
        //TODO: ...
        Scanner reader = new Scanner(System.in);
        return reader.next();
//        Scanner reader = new Scanner(System.in);
//        reader.
//        Console console = System.console();
//        char[] password = console.readPassword();
//        return new String(password);
    }

    public static String readLineFromStdin(){
        Scanner reader = new Scanner(System.in);
        String line = reader.nextLine();
        return line;
    }

    public static int readIntFromStdin(){
        Scanner reader = new Scanner(System.in);
        return reader.nextInt();
    }

    public static void clearScreen() {
        final String operatingSystem = System.getProperty("os.name");

        if (operatingSystem .contains("Windows")) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            catch (InterruptedException  e2) {
                e2.printStackTrace();
            }
        }
        else {
            try {
                Runtime.getRuntime().exec("clear");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
