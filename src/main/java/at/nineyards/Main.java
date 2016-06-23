package at.nineyards;

import ch.simas.jtoggl.*;
import ch.simas.jtoggl.Project;
import net.rcarz.jiraclient.*;
import org.joda.time.DateTime;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Peter on 24.05.2016.
 */
public class Main {

    private static final String CONF_TIME_DIFF = "time_diff";
    private static final String CONF_TOGGL_API_TOKEN = "toggl.api_token";
    private static final String CONF_JIRA_SERVER_URL = "jira.server_url";
    private static final String CONF_JIRA_USERNAME = "jira.username";
    private static final String CONF_UPDATE_TOGGL_PROJECT = "update_toggl_projects";
    private static final String CONF_DEFAULT_ISSUE = "jira.default_issue";
    private static boolean UPDATE_TOGGL_PROJECT = false;

    private static String JIRA_SERVER_URL;
    private static JiraClient sJira;

    private static String TOGGL_API_TOKEN;
    private static ch.simas.jtoggl.JToggl jToggl;
    private static String sUsername;
    private static String sPassword;
    private static int sTimeDiff = 0;
    private static String DEFAULT_ISSUE;

    static int created = 0;
    static int skipped = 0;
    static int errors = 0;
    private static boolean skipTheRest = false;
    private static List<Project> sProjects;


    public static void main(String[] args) {
        System.out.println("************ Welcome to Toggl-To-Jira ************");
        System.out.println("This is version: 0.8.0");
        configure();

        do {
            initJira();
            System.out.println("************ Main menu ************");
            System.out.println("1 ...migrate time entries of today");
            System.out.println("2 ...migrate time entries of yesterday");
            System.out.println("3 ...migrate time entries for a given time span");
            System.out.println("0 ...quit");
            int input = Util.readIntFromStdin();
            if(input == 0){
                break;
            }
            else if(input == 1){
                migrateTodayTimeEntries();
            }
            else if(input == 2){
                migrateYesterdayTimeEntries();
            }
            else if(input == 3){
                askForTimeframe();
            }
        } while(true);
    }

    private static void configure() {
        System.out.println("### Applying config from 'ttj.config'");
        Map<String, String> config = FileReaderUtil.readConfig("ttj.config");
        for(String key: config.keySet()){
            String value = config.get(key);
            if(key.equals(CONF_JIRA_USERNAME)){
                sUsername = value;
                System.out.println(CONF_JIRA_USERNAME + " = " + value);
            }
            else if(key.equals(CONF_JIRA_SERVER_URL)){
                JIRA_SERVER_URL = value;
                System.out.println(CONF_JIRA_SERVER_URL + " = " + value);
            }
            else if(key.equals(CONF_TOGGL_API_TOKEN)){
                TOGGL_API_TOKEN = value;
                System.out.println(CONF_TOGGL_API_TOKEN + " = " + value);
            }
            else if(key.equals(CONF_TIME_DIFF)){
                sTimeDiff = Integer.valueOf(value);
                System.out.println(CONF_TIME_DIFF + " = " + sTimeDiff);
            }
            else if(key.equals(CONF_DEFAULT_ISSUE)){
                DEFAULT_ISSUE = value;
                System.out.println(CONF_DEFAULT_ISSUE + " = " + DEFAULT_ISSUE);
            }
            else if(key.equals(CONF_UPDATE_TOGGL_PROJECT)){
                UPDATE_TOGGL_PROJECT = value.equalsIgnoreCase("true");
                System.out.println(CONF_UPDATE_TOGGL_PROJECT + " = " + UPDATE_TOGGL_PROJECT);
            }
        }
        Util.checkOrThrowIfNull(CONF_JIRA_SERVER_URL, JIRA_SERVER_URL);
        Util.checkOrThrowIfNull(CONF_TOGGL_API_TOKEN, TOGGL_API_TOKEN);
        Util.checkOrThrowIfNull(CONF_DEFAULT_ISSUE, DEFAULT_ISSUE);
        System.out.println("### Config applied!");
    }

    private static void initJira() {
        if(sJira == null) {
            if(sUsername == null || sPassword == null){
                askForCredentials();
            }
            else {
                sJira = createJiraInstance(sUsername, sPassword);
            }
        }
    }

    private static void askForCredentials() {
        System.out.println("please enter your credentials for: " + JIRA_SERVER_URL);

        String username = null;
        if(sUsername == null){
            System.out.println("username: ");
            username = Util.readStringFromStdin();
        }
        else {
            username = sUsername;
        }
        System.out.println("password: ");
        String password = Util.readPasswordFromStdin();
        sJira = createJiraInstance(username, password);
    }

    private static JiraClient createJiraInstance(String username, String password) {
        try {
            BasicCredentials creds = new BasicCredentials(username, password);
            JiraClient jiraClient = new JiraClient(JIRA_SERVER_URL, creds);
            jiraClient.getIssue(DEFAULT_ISSUE); // this will throw an exception if something is wrong with the credentials
            sPassword = password;
            sUsername = username;
            System.out.println("### login successful");
            return jiraClient;
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
        System.exit(0);
        return null;
    }

    private static void askForTimeframe() {
        System.out.println("enter start date (DD-MM-JJJJ)");
        String input = Util.readLineFromStdin();
        DateTime from = Util.readTimeframe(input, true);
        if(from == null){
            System.out.println("ERROR: Date could not be parsed");
        }
        else {
            migrateTimeEntries(from, input);
        }

    }

    private static void migrateTimeEntries(DateTime from, String fromInput){
        System.out.println("enter end date (DD-MM-JJJJ) or leave empty for same date as start date");
        String input = Util.readLineFromStdin();
        if(input.trim().length() == 0)
            input = fromInput;

        DateTime to = Util.readTimeframe(input, false);
        if(from == null){
            System.out.println("ERROR: Date could not be parsed");
        }
        else {
            migrateTimeEntries(from, to);
        }
    }

    private static void migrateTodayTimeEntries() {
        DateTime from = new DateTime().withTimeAtStartOfDay();
        DateTime to = new DateTime().plusDays(1).withTimeAtStartOfDay();
        migrateTimeEntries(from, to);
    }

    private static void migrateYesterdayTimeEntries() {
        DateTime from = new DateTime().minusDays(1).withTimeAtStartOfDay();
        DateTime to = new DateTime().withTimeAtStartOfDay();
        migrateTimeEntries(from, to);
    }

    private static void migrateTimeEntries(DateTime from, DateTime to){
        Util.clearScreen();
        errors = 0;
        skipped = 0;
        created = 0;
        sProjects = null;
        skipTheRest = false;
        System.out.println("starting to migrate time entries...");
        try {

            List<TimeEntry> entries = getTimeEntriesWithRange(from.toGregorianCalendar(), to.toGregorianCalendar());
            int totalCount = entries.size();
            System.out.println(totalCount + " entries found:");
            int i = 0;
            for(TimeEntry entry: entries){
                if(skipTheRest){
                    int theRest = (totalCount-i);
                    System.out.println("WARNING: skipping " + theRest + " further entries!");
                    skipped += theRest;
                    break;
                }
                i++;
                String description = entry.getDescription();
                DateTime startTime = new DateTime(entry.getStart().getTime());
                startTime = startTime.plusHours(sTimeDiff);
                System.out.println("Processing toggl entry #" + i + " of " + totalCount + ": ");
                System.out.println("Description: " + description);
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
                        issue = sJira.getIssue(issueKey);
                    } catch (JiraException ex) {
                        System.out.println(ex.getMessage());
                        // issue does probably not exist, create an issue with that number?
                        createNewIssue = askForCreationOfNewIssue(issueKey, descriptionWithoutIssueKey);
                    }
                }

                if(createNewIssue){
                    net.rcarz.jiraclient.Project project = askForProject(sJira);
                    if(project != null){
                        String summary = askForSummary();
                        if(summary != null){
                            issue = sJira.createIssue(project.getKey(), "Task")
                                    .field(Field.SUMMARY, summary)
                                    .execute();
                            System.out.println("Issue created: " + issue.getKey());
                        }
                    }
                }
                if(issue == null && !createNewIssue){
                    issue = askForIssue(sJira);
                }
                if(issue != null) {
                    long timeSpentSeconds = entry.getDuration();// ((int) end.getTime() / 1000) - ((int) start.getTime() / 1000);
                    //System.out.println("Would create worklog with: issue " + issue.getKey() + " timeSpent " + timeSpentSeconds + " timeStarted " + entry.getStart() + " desc: " + descriptionWithoutIssueKey);
                    if(UPDATE_TOGGL_PROJECT){
                        updateTogglEntry(entry, issue);
                    }
                    createWorklog(issue, descriptionWithoutIssueKey, startTime.toDate(), timeSpentSeconds);
                }
                else {
                    System.out.println("WARNING: Skipped worklog...");
                    skipped++;
                }
                System.out.println("-------------------------------------");
                //break; //TODO: only for testing
            }

            System.out.println("Done...");
            System.out.println("Created: "+ created);
            System.out.println("Errors: "+ errors);
            System.out.println("Skipped: "+ skipped);


        } catch (JiraException ex) {
            ex.printStackTrace();
        }

    }

    private static void updateTogglEntry(TimeEntry entry, Issue issue) {
        Project togglProject = entry.getProject();
        if(togglProject == null){
            net.rcarz.jiraclient.Project jiraProject = issue.getProject();
            String key = jiraProject.getKey();
            String description = jiraProject.getDescription();
            togglProject = findTogglProjectByKey(key);
            if(togglProject == null){
                togglProject = createTogglProject(key + description);
                entry.setProject(togglProject);
            }
            if(togglProject != null){
              entry.setProject(togglProject);
              jToggl.updateTimeEntry(entry);
            }
        }
    }

    private static Project createTogglProject(String fullName){
        Project newTogglProject = new Project();
        newTogglProject.setName(fullName);

        List<Workspace> ws = jToggl.getWorkspaces();
        newTogglProject.setWorkspace(ws.get(0));
        Project newProject = jToggl.createProject(newTogglProject);
        sProjects.add(newProject);
        return newProject;
    }

    private static Project findTogglProjectByKey(String key) {
        List<Project> projects = getTogglProjects();
        for(Project project: projects){
            if(project.getName().startsWith(key + " ")){
                return project;
            }
        }
        return null;
    }

    private static void createWorklog(Issue issue, String descriptionWithoutIssueKey, Date start, long timeSpentSeconds) {
        if(descriptionWithoutIssueKey == null || descriptionWithoutIssueKey.equals("")){
            descriptionWithoutIssueKey = "Working on " + issue.getKey();
        }
        if(timeSpentSeconds % 60 != 0) {
            // no full minute -> round up
            timeSpentSeconds += 60 - (timeSpentSeconds % 60);
        }
        System.out.println("Creating worklog with: issue " + issue.getKey() + " timeSpentInMinutes " + timeSpentSeconds / 60 + " timeStarted " + start + " desc: " + descriptionWithoutIssueKey);

        if(start == null){
            System.out.println("Warning: no starttime set for issue...");
        }

        try {
            System.out.println("Do you want to create this worklog in Jira (y) or skip it (n)?");
            if(askForYesOrNo()){
                WorkLog worklog = issue.createWorkLog(descriptionWithoutIssueKey, start, timeSpentSeconds);
                System.out.println("Created worklog: " + worklog.toString() + ", comment: " + worklog.getComment() + ", timespent: " + worklog.getTimeSpent());
                //System.out.println("Created worklog (NOT EXECUTED)!");
                created++;
            }
            else {
                System.out.println("Skipped worklog");
                skipped++;
            }


        } catch (JiraException ex) {
            ex.printStackTrace();
            errors++;
        }
    }

    private static Issue askForIssue(JiraClient jira) {
        net.rcarz.jiraclient.Issue issue = null;

        do {
            System.out.println("To what issue do you want to add the worklog (type s to skip)");
            String input = Util.readStringFromStdin();
            if(input.equals("s")){
                break;
            }
            try {
                issue = jira.getIssue(input);
            } catch (JiraException e) {
               System.out.println(e.getMessage());
            }

        } while(issue == null);

        return issue;
    }

    private static net.rcarz.jiraclient.Project askForProject(JiraClient jira) {
        net.rcarz.jiraclient.Project project = null;

        do {
            System.out.println("For what project do you want to search an issue (type s to skip)");
            String input = Util.readStringFromStdin();
            if(input.equals("s")){
                break;
            }
            try {
                project = jira.getProject(input);
            } catch (JiraException e) {
                System.out.println(e.getMessage());
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

    private static boolean askForXOrNo(String x, String xLong){
        String input = Util.readStringFromStdin();
        input = input.toLowerCase().trim();
        if(input.equals(x) || input.equals(xLong)){
            return true;
        }
        else if(input.equals("n") || input.equals("no")){
            return false;
        }
        else if(input.equals("n++") || input.equals("no++")){
            skipTheRest = true;
            return false;
        }
        else {
            System.out.println("Error: Input not recognized (" + input + ")");
            return askForYesOrNo();
        }
    }

    private static boolean askForYesOrNo(){
        return askForXOrNo("y", "yes");
    }

    private static boolean askForCreateOrNo(){
        return askForXOrNo("c", "create");
    }

    private static boolean askForCreationOfNewIssue(String issueKey, String descriptionWithoutIssueKey) {
        System.out.println("Issue with key: " + issueKey + " not found.\nDo you want to create a new one for (\""+descriptionWithoutIssueKey+"\") [c create, n no]: ");
        return askForCreateOrNo();
    }

    private static boolean askForCreationOfNewIssue(String descriptionWithoutIssueKey) {
        System.out.println("No issue key defined.\nDo you want to create a new one for (\"" + descriptionWithoutIssueKey + "\") [c create, n no]: ");
        return askForCreateOrNo();
    }

    public static String[] parseIssue(String togglDescription){
        if(togglDescription == null) togglDescription = "";
        boolean jiraTaskFound = false;
        String jiraDescription = togglDescription;

        String jiraTask = null;
        Pattern p = Pattern.compile("([A-Z|a-z]+\\-\\d+)\\s?(.*)");
        Matcher m = p.matcher(togglDescription);

        if(m.find()) {
            //m.matches() &&
            jiraTask = m.group(1);
            jiraTaskFound = true;
            jiraTask = jiraTask.toUpperCase();
            if(m.groupCount() >= 2){
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
        jToggl = new JToggl(TOGGL_API_TOKEN, "api_token");
        List<TimeEntry> entries = jToggl.getTimeEntries(from.getTime(), to.getTime());
        return entries;
    }

    private static List<Project> getTogglProjects() {
        if(sProjects == null){
            sProjects = jToggl.getProjects();
        }
        return sProjects;
    }
}
