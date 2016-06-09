import ch.simas.jtoggl.*;
import ch.simas.jtoggl.Project;
import net.rcarz.jiraclient.*;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Peter on 24.05.2016.
 */
public class Main {

    private static final java.lang.String JIRA_SERVER_URL = "https://jira.9yrds.net";
    private static JiraClient jira;

    private static final String togglApiToken = "a97b5fd26a389f8bf9113cb8d0d1c12e";
    private static ch.simas.jtoggl.JToggl jToggl;
    private static String sUsername;
    private static String sPassword;


    public static void main(String[] args) {
        do {
            System.out.println("Welcome to the 9yards Toogl-To-Jira");
            if(sUsername == null || sPassword == null){
                askForCredentials();
            }
            else {
                System.out.println("1 ...migrate timeentries of today");
                System.out.println("2 ...Migrate timeentries for a given timespan");
                System.out.println("9 ...quit");
                int input = Util.readIntFromStdin();
                if(input == 9){
                    break;
                }
                else if(input == 1){
                    migrateTodayTimeentries();
                }
                else if(input == 2){
                    askForTimeframe();
                }
            }
        } while(true);
    }

    private static void askForCredentials() {
        System.out.println("please enter your credentials for: " + JIRA_SERVER_URL);
        System.out.println("username: ");
        String username = Util.readStringFromStdin();
        System.out.println("password: ");
        String password = Util.readPasswordFromStdin();
        BasicCredentials creds = new BasicCredentials(username, password);

        try {
            jira = new JiraClient(JIRA_SERVER_URL, creds);
            jira.getIssue("INT-1");
            sPassword = password;
            sUsername = username;
        }
        catch(Exception exception) {
            if(exception instanceof JiraException) {
                try {
                    int status = ((RestException) exception.getCause()).getHttpStatusCode();
                    if(status == 403){
                        System.out.println("Authorization failed!");
                    }
                    else {
                        System.out.println("Something went wrong! Error code: "+status);
                    }
                }
                catch (Exception e){
                    System.out.println("Something went wrong!");
                }
            }
        }
    }

    private static void askForTimeframe() {
        System.out.println("enter start date (DD-MM-JJJJ)");
        String input = Util.readLineFromStdin();
        Pattern p = Pattern.compile("(\\d\\d)-(\\d\\d)-(\\d\\d\\d\\d)");
        Matcher m = p.matcher(input);
        if(m.find()){
            boolean b = m.matches();
            if(b && m.groupCount() >= 2){
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(1));
                int year = Integer.parseInt(m.group(1));
                DateTime from = new DateTime().withDate(year, month, day);
                from = from.withTimeAtStartOfDay();
                migrateTimeEntris(from);
                return;
            }
        }
        System.out.println("ERROR: Date could not be parsed");
    }


    private static void migrateTimeEntris(DateTime from){
        System.out.println("enter end date (DD-MM-JJJJ)");
        String input = Util.readLineFromStdin();
        Pattern p = Pattern.compile("(\\d\\d)-(\\d\\d)-(\\d\\d\\d\\d)");
        Matcher m = p.matcher(input);
        if(m.find()){
            boolean b = m.matches();
            if(b && m.groupCount() >= 2){
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(1));
                int year = Integer.parseInt(m.group(1));
                DateTime to = new DateTime().withDate(year, month, day);
                to= to.plusDays(1).withTimeAtStartOfDay();
                migrateTimeEntries(from, to);
                return;
            }
        }
        System.out.println("ERROR: Date could not be parsed");
    }

    private static void migrateTodayTimeentries() {
        DateTime from = new DateTime().withTimeAtStartOfDay();
        DateTime to = new DateTime().plusDays(1).withTimeAtStartOfDay();
        migrateTimeEntries(from, to);
    }

    private static void migrateTimeEntries(DateTime from, DateTime to){
        Util.clearScreen();
        // write your code here
        System.out.println("starting to migrate time entries...");

        try {

            List<TimeEntry> entries = getTimeEntriesWithRange(from.toGregorianCalendar(), to.toGregorianCalendar());

            for(TimeEntry entry: entries){
                String description = entry.getDescription();
                System.out.println("Processing toggl entry: " + description);
                String[] issueData = parseIssue(description);
                String issueKey = issueData[0];
                String descriptionWithoutIssueKey = issueData[1];
                boolean createNewIssue = false;
                Issue issue = null;
                if(issueKey == null){
                    // no issue could be matched, ask the user what to do
                    createNewIssue = askForCreationOfNewIssue(descriptionWithoutIssueKey);
                }
                else {
                    try {
                        issue =  jira.getIssue(issueKey);
                    } catch (JiraException ex) {
                        ex.printStackTrace();
                        // issue does probably not exist, create an issue with that number?
                        createNewIssue = askForCreationOfNewIssue(issueKey, descriptionWithoutIssueKey);
                    }
                }

                if(createNewIssue){
                    net.rcarz.jiraclient.Project project = askForProject(jira);
                    if(project != null){
                        String summary = askForSummary();
                        if(summary != null){
                            issue = jira.createIssue(project.getKey(), "Task")
                                    .field(Field.SUMMARY, summary)
                                    .execute();
                            System.out.println("Issue created: " + issue.getKey());
                        }
                    }

                }
                if(issue == null && !createNewIssue){
                    issue = askForIssue(jira);
                }
                if(issue != null) {
                    long timeSpentSeconds = entry.getDuration();// ((int) end.getTime() / 1000) - ((int) start.getTime() / 1000);
                    System.out.println("Would create worklog with: issue " + issue.getKey() + " timeSpent " + timeSpentSeconds + " timeStarted " + entry.getStart() + " desc: " + descriptionWithoutIssueKey);
                    createWorklog(issue, descriptionWithoutIssueKey, entry.getStart(), timeSpentSeconds);
                }
                else {
                    System.out.println("WARNING: Skipped worklog...");
                }
                System.out.println("-------------------------------------");
            }

            System.out.println("DONE");

        } catch (JiraException ex) {
            ex.printStackTrace();
            //System.err.println(ex.getCause().getMessage());
        }

    }

    private static void createWorklog(Issue issue, String descriptionWithoutIssueKey, Date start, long timeSpentSeconds) {
        //TODO: uncomment
//        try {
//            issue.createWorkLog(descriptionWithoutIssueKey, start, timeSpentSeconds);
//        } catch (JiraException ex) {
//            ex.printStackTrace();
//        }
    }

    private static Issue askForIssue(JiraClient jira) {
        net.rcarz.jiraclient.Issue issue = null;

        do {

            System.out.println("To what issue do you want to add the worklog (type quit to skip)");
            String input = Util.readStringFromStdin();
            if(input.equals("quit")){
                break;
            }
            try {
                issue = jira.getIssue(input);
            } catch (JiraException e) {
                e.printStackTrace();
            }

        } while(issue == null);

        return issue;
    }

    private static net.rcarz.jiraclient.Project askForProject(JiraClient jira) {
        net.rcarz.jiraclient.Project project = null;

        do {
            System.out.println("For what project do you want to search an issue (type quit to skip)");
            String input = Util.readStringFromStdin();
            if(input.equals("quit")){
                break;
            }
            try {
                project = jira.getProject(input);
            } catch (JiraException e) {
                e.printStackTrace();
            }

        } while(project == null);

        return project;
    }

    private static String askForSummary() {

        System.out.println("Enter a description (or leave empty to skip issue/worklog creation):");
        String summary = Util.readLineFromStdin();
        if(summary != null && summary.length() == 0) {
            summary = null;
        }
        return summary;
    }

    private static boolean askForYesOrNo(){
        String input = Util.readStringFromStdin();
        return input.contains("y");
    }

    private static boolean askForCreationOfNewIssue(String issueKey, String descriptionWithoutIssueKey) {
        System.out.println("Issue with key: " + issueKey + " not found.\nDo you want to create a new one for (\""+descriptionWithoutIssueKey+"\") [y yes, n no]: ");
        return askForYesOrNo();
    }

    private static boolean askForCreationOfNewIssue(String descriptionWithoutIssueKey) {
        System.out.println("No issue key defined.\nDo you want to create a new one for (\"" + descriptionWithoutIssueKey + "\") [y yes, n no]: ");
        return askForYesOrNo();
    }

    public static String[] parseIssue(String togglDescription){
        boolean jiraTaskFound = false;
        String jiraDescription = togglDescription;
        String jiraTask = null;
        Pattern p = Pattern.compile("([A-Z]+\\-\\d+)\\s(.*)");
        Matcher m = p.matcher(togglDescription);

        if(m.find()){
            for(int i = 1; i <= m.groupCount(); i++){
                System.out.println(m.group(i));
            }
            boolean b = m.matches();
            if(b && m.groupCount() >= 2){
                jiraTaskFound = true;
                jiraTask = m.group(1);
                jiraDescription = m.group(2);
            }
        }
        else {
            //System.out.println("String not found...");
        }

        if(!jiraTaskFound){
            //try to find out the project by name and set default task for that project
            //TODO: ...
        }

        return new String[]{jiraTask, jiraDescription};
    }

    public static List<TimeEntry> getTimeEntriesWithRange(Calendar from, Calendar to) {
        jToggl = new JToggl(togglApiToken, "api_token");
        List<TimeEntry> entries = jToggl.getTimeEntries(from.getTime(), to.getTime());
        return entries;
    }
}
