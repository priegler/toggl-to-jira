package at.nineyards;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.io.Console;
import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Peter on 09.06.2016.
 */
public class Util {

    public static String readStringFromStdin(){
        Scanner reader = new Scanner(System.in);
        return reader.next();
    }

    public static String readPasswordFromStdin(){
        try {
            Console console = System.console();
            char[] password = console.readPassword();
            return new String(password);
        }
        catch (Exception e){
            //e.printStackTrace();
            //Backup if code is run in IDE (not hidden here)
            Scanner reader = new Scanner(System.in);
            return reader.next();
        }


    }

    public static String readLineFromStdin(){
        Scanner reader = new Scanner(System.in);
        String line = reader.nextLine();
        return line;
    }

    public static int readIntFromStdin(){
        Scanner reader = new Scanner(System.in);
        try {
            return reader.nextInt();
        }
        catch (Exception e){
            //e.printStackTrace();
        }
        return -1;

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

    public static boolean isShortTimeFormat(String input) {
        Pattern p = Pattern.compile("(\\d\\d)-(\\d\\d)");
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m.matches();
        }
        return false;
    }


    public static DateTime readTimeframe(String input, boolean fromStartOfday) {
        Pattern p = Pattern.compile("(\\d\\d)-(\\d\\d)-(\\d\\d\\d\\d)");
        Matcher m = p.matcher(input);
        if(m.find()){
            boolean b = m.matches();
            if(b && m.groupCount() >= 2){
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                DateTime dateTime = new DateTime().withDate(year, month, day);
//                from = from.withDayOfMonth(day);
//                from = from.withMonthOfYear(month);
//                from = from.withYear(year);
//                dateTime.getZone();
                LocalDateTime ldt = dateTime.toLocalDateTime();

                if(fromStartOfday)
                    dateTime = ldt.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).toDateTime(); //.withTimeAtStartOfDay();
                else
                    dateTime = ldt.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).toDateTime(); //dateTime.plusDays(1).withTimeAtStartOfDay();
                return dateTime;
            }
        }
        return null;
    }

    public static void checkOrThrowIfNull(String key, String value) {
        if(value == null) throw new RuntimeException("no " + key + " defined in config!");
    }
}
