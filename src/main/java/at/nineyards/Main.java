package at.nineyards;

import ch.simas.jtoggl.*;
import ch.simas.jtoggl.Project;
import net.rcarz.jiraclient.*;
import org.joda.time.DateTime;

import java.util.*;
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
    private static final String CONF_TOGGL_PROJECT_TO_IGNORE = "toggle.ignore_project";
    private static final String CONF_DEFAULT_ISSUE = "jira.default_issue";
    private static boolean UPDATE_TOGGL_PROJECT = false;
    private static String TOGGL_PROJECT_TO_IGNORE = "";
    static List<String> sProjectsToIgnore;

    private static String JIRA_SERVER_URL;
    private static JiraClient sJira;

    private static String TOGGL_API_TOKEN;
    private static ch.simas.jtoggl.JToggl jToggl;
    private static String sUsername;
    private static String sPassword;
    private static int sTimeDiff = 0;
    private static String DEFAULT_ISSUE;

    private static int created = 0;
    private static int skipped = 0;
    private static int errors = 0;
    private static int skippedExcludedProjectWorklog = 0;

    private static boolean skipTheRest = false;
    private static List<Project> sProjects = new ArrayList<Project>();


    public static void main(String[] args) {
        System.out.println("************ Welcome to Toggl-To-Jira ************");
        System.out.println("This is version: 0.8.1");
        configure();

        do {
            initJira();
            System.out.println("************ Main menu ************");
            System.out.println("1 ...migrate time entries of today (toggle -> jira)");
            System.out.println("2 ...migrate time entries of yesterday  (toggle -> jira)");
            System.out.println("3 ...migrate time entries for a given time span  (toggle -> jira)");
            System.out.println("99 ...delete duplicated entries (jira)");
            System.out.println("0 ...quit");
            int input = Util.readIntFromStdin();
            if (input == 0) {
                break;
            } else if (input == 1) {
                migrateTodayTimeEntries();
            } else if (input == 2) {
                migrateYesterdayTimeEntries();
            } else if (input == 3) {
                migrateTimeEntries();
            } else if (input == 99) {
                deleteDuplicateWorklogs();
            } else {
                System.out.println("Input not recognized, please try again!");
            }
        } while (true);
    }

    private static void deleteDuplicateWorklogs() {
        System.out.println("Delete duplicated Worklog from Jira");
        askForTimeframe(new TimeframeCallback() {
            public void onTimeFrameSet(DateTime timeStarting, DateTime timeEnding) {
                performDeleteDuplicateWorklogs(timeStarting, timeEnding);
            }

            public void onExit() {
                // do nothing...
            }
        });
    }

    private static void configure() {
        System.out.println("### Applying config from 'ttj.config'");
        Map<String, String> config = FileReaderUtil.readConfig("ttj.config");
        for (String key : config.keySet()) {
            String value = config.get(key);
            if (key.equals(CONF_JIRA_USERNAME)) {
                sUsername = value;
                System.out.println(CONF_JIRA_USERNAME + " = " + value);
            } else if (key.equals(CONF_JIRA_SERVER_URL)) {
                JIRA_SERVER_URL = value;
                System.out.println(CONF_JIRA_SERVER_URL + " = " + value);
            } else if (key.equals(CONF_TOGGL_API_TOKEN)) {
                TOGGL_API_TOKEN = value;
                System.out.println(CONF_TOGGL_API_TOKEN + " = " + value);
            } else if (key.equals(CONF_TIME_DIFF)) {
                sTimeDiff = Integer.valueOf(value);
                System.out.println(CONF_TIME_DIFF + " = " + sTimeDiff);
            } else if (key.equals(CONF_DEFAULT_ISSUE)) {
                DEFAULT_ISSUE = value;
                System.out.println(CONF_DEFAULT_ISSUE + " = " + DEFAULT_ISSUE);
            } else if (key.equals(CONF_UPDATE_TOGGL_PROJECT)) {
                UPDATE_TOGGL_PROJECT = value.equalsIgnoreCase("true");
                System.out.println(CONF_UPDATE_TOGGL_PROJECT + " = " + UPDATE_TOGGL_PROJECT);
            } else if (key.equals(CONF_TOGGL_PROJECT_TO_IGNORE)) {
                TOGGL_PROJECT_TO_IGNORE = value;
                sProjectsToIgnore = Arrays.asList(TOGGL_PROJECT_TO_IGNORE.split(","));
                System.out.println(CONF_TOGGL_PROJECT_TO_IGNORE + " = " + sProjectsToIgnore);
            }
        }
        Util.checkOrThrowIfNull(CONF_JIRA_SERVER_URL, JIRA_SERVER_URL);
        Util.checkOrThrowIfNull(CONF_TOGGL_API_TOKEN, TOGGL_API_TOKEN);
        Util.checkOrThrowIfNull(CONF_DEFAULT_ISSUE, DEFAULT_ISSUE);
        System.out.println("### Config applied!");
    }

    private static void initJira() {
        if (sJira == null) {
            if (sUsername == null || sPassword == null) {
                askForCredentials();
            } else {
                sJira = createJiraInstance(sUsername, sPassword);
            }
        }
    }

    private static void askForCredentials() {
        System.out.println("please enter your credentials for: " + JIRA_SERVER_URL);

        String username = null;
        if (sUsername == null) {
            System.out.println("username: ");
            username = Util.readStringFromStdin();
        } else {
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
        } catch (Exception exception) {
            if (exception instanceof JiraException) {
                try {
                    int status = ((RestException) exception.getCause()).getHttpStatusCode();
                    if (status == 403) {
                        System.out.println("Authorization failed!");
                    } else {
                        System.out.println("Something went wrong! Error code: " + status);
                    }
                } catch (Exception e) {
                    System.out.println("Something went wrong!");
                }
            }
        }
        System.exit(0);
        return null;
    }


    /**
     *  method to input startDate and endDate and return to callback after those two
     *
     * @param callback the callback that is called after both dates are entered or if the users exits
     */
    private static void askForTimeframe(TimeframeCallback callback) {
        askForTimeframe(callback, null);
    }


    /**
     * helper method for askForTimeframe(TimeframeCallback callback)
     * @param callback
     * @param start
     */
    private static void askForTimeframe(TimeframeCallback callback, DateTime start) {
        DateTime now = DateTime.now();
        if(start == null){ // start date will be entered
            System.out.println("enter start date (DD-MM with auto year set to " + now.getYear()  + " or DD-MM-JJJJ) [or enter \"q\" to quit]");
        }
        else { // end date will be entered
            System.out.println("enter end date or leave empty for same date as start date [or enter \"q\" to quit]");
        }

        String input = Util.readLineFromStdin();

        if(Util.isShortTimeFormat(input)){
            input = input.concat("-" + now.getYear());
        }
        DateTime dateTime;
        if(input.trim().length() == 0 && start != null) {
            dateTime = start;
        }
        else {
            dateTime = Util.readTimeframe(input, true);
        }

        if(dateTime == null){
            System.out.println("ERROR: Date could not be parsed");
        }
        else {
            if(input.equals("q")){
                callback.onExit();
            }
            else if(start == null) {
                askForTimeframe(callback, dateTime);
            }
            else {
                callback.onTimeFrameSet(start, dateTime);
            }

        }
    }



    private static void migrateTimeEntries(){
        System.out.println("Migrate Worklog entries Toggl -> Jira");
        askForTimeframe(new TimeframeCallback() {
            public void onTimeFrameSet(DateTime timeStarting, DateTime timeEnding) {
                migrateTimeEntries(timeStarting, timeEnding);
            }

            public void onExit() {
                // do nothing...
            }
        });
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
        Util.clearScreen(); //TODO: does not always work
        errors = 0;
        skipped = 0;
        created = 0;
        skippedExcludedProjectWorklog = 0;
        sProjects = null;
        skipTheRest = false;

        String startDateeHumanReadable = getHumanReadableDateTimeString(from);
        String endDateeHumanReadable = getHumanReadableDateTimeString(to);
        System.out.println("Migrating worklogs for timespan (" + startDateeHumanReadable + " to " + endDateeHumanReadable +")");
        System.out.println("Do you want to auto-migrate (y) all worklos or confirm each one manually (n)");
        boolean autoMigrate = askForYesOrNo();

        try {
            to = to.plusDays(1).withTimeAtStartOfDay(); // allways take full days into account
            List<TimeEntry> entries = getTimeEntriesWithRange(from.toGregorianCalendar(), to.toGregorianCalendar());
            int totalCount = entries.size();
            System.out.println(totalCount + " entries found:");
            int i = 0;
            ArrayList<TimeEntry> malformedEntries = new ArrayList<TimeEntry>();
            ArrayList<Integer> malformedIndex = new ArrayList<Integer>();
            for(TimeEntry entry: entries){
                if(skipTheRest){
                    int theRest = (totalCount-i);
                    System.out.println("WARNING: skipping " + theRest + " further entries!");
                    skipped += theRest;
                    break;
                }
                if(i == 0) System.out.println("-------------------------------------");
                i++;
                if(isExcludedProject(entry)){
                    skippedExcludedProjectWorklog++;
                    return;
                }

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
                    if(!autoMigrate) createNewIssue = askForCreationOfNewIssue(descriptionWithoutIssueKey);
                }
                else {
                    try {
                        issue = sJira.getIssue(issueKey);
                    } catch (JiraException ex) {
                        System.out.println(ex.getMessage());
                        // issue does probably not exist, create an issue with that number?
                        if(descriptionWithoutIssueKey != null && descriptionWithoutIssueKey.length() > 0){ // if we have a description without the usue key alone we set it as new description
                            entry.setDescription(descriptionWithoutIssueKey);
                        }

                        if(!autoMigrate) createNewIssue = askForCreationOfNewIssue(issueKey, descriptionWithoutIssueKey);
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
                    if(autoMigrate){
                        malformedEntries.add(entry);
                        malformedIndex.add(i);
                    }
                    else {
                        issue = askForIssue(sJira);
                    }

                }
                if(issue != null) {
                    updateTogglAndCreateWorklog(entry, startTime, descriptionWithoutIssueKey, issue, autoMigrate);
                }
                else {
                    System.out.println("WARNING: Skipped worklog...");
                    skipped++;
                }
                System.out.println("-------------------------------------");
            }
            if(autoMigrate && malformedEntries.size() > 0) {
                System.out.println("Going now through #" + malformedEntries.size() + " issues with missing or wrong Issue key");
                Issue issue = null;
                System.out.println("-------------------------------------");
                int j = 0;
                for(TimeEntry entry: malformedEntries){
                    System.out.println("Processing toggl entry #" + malformedIndex.get(j) + " of " + totalCount + "");
                    System.out.println("Description: " + entry.getDescription());
                    issue = askForIssue(sJira);
                    if(issue != null) {
                        DateTime startTime = new DateTime(entry.getStart().getTime());
                        updateTogglAndCreateWorklog(entry, startTime, entry.getDescription(), issue, false);
                        skipped--;
                    }
                    else {
                        System.out.println("WARNING: Skipped worklog...");
                    }
                    j++;
                    System.out.println("-------------------------------------");
                }

            }

            System.out.println("Done...");
            System.out.println("Created: "+ created);
            System.out.println("Errors: "+ errors);
            System.out.println("Skipped: "+ skipped);
            System.out.println("skippedExcludedProjectWorklog " + skippedExcludedProjectWorklog);

        } catch (JiraException ex) {
            ex.printStackTrace();
        }

    }

    private static String getHumanReadableDateTimeString(DateTime dateTime) {
        return String.format("%02d-%02d-%04d", dateTime.getDayOfMonth(), dateTime.getMonthOfYear(), dateTime.getYear()); // dd-MM-yyyy
    }

    private static void updateTogglAndCreateWorklog(TimeEntry entry, DateTime startTime, String descriptionWithoutIssueKey, Issue issue, boolean autoMigrate) {
        long timeSpentSeconds = entry.getDuration();// ((int) end.getTime() / 1000) - ((int) start.getTime() / 1000);
        //System.out.println("Would create worklog with: issue " + issue.getKey() + " timeSpent " + timeSpentSeconds + " timeStarted " + entry.getStart() + " desc: " + descriptionWithoutIssueKey);
        if(UPDATE_TOGGL_PROJECT){
            updateTogglEntry(entry, issue);
        }
        createWorklog(issue, descriptionWithoutIssueKey, startTime, timeSpentSeconds, autoMigrate);
    }

    private static void updateTogglEntry(TimeEntry entry, Issue issue) {
        Project togglProject = entry.getProject();
        if(togglProject == null){
            net.rcarz.jiraclient.Project jiraProject = issue.getProject();
            String key = jiraProject.getKey();
            String description = jiraProject.getDescription();
            Project togglProjectExtracted = findTogglProjectByKey(key);
            if(togglProjectExtracted == null){
                togglProject = createTogglProject(key + description);
                entry.setProject(togglProject);
            }
        }

        if(togglProject != null){
            entry.setProject(togglProject);
            jToggl.updateTimeEntry(entry);
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

    private static void createWorklog(Issue issue, String descriptionWithoutIssueKey, DateTime start, long timeSpentSeconds, boolean autoMigrate) {
        if(descriptionWithoutIssueKey == null || descriptionWithoutIssueKey.equals("")){
            descriptionWithoutIssueKey = "Working on " + issue.getKey();
        }
        if(timeSpentSeconds <= 59){
            timeSpentSeconds = 60;
        }
//        if (timeSpentSeconds % 60 != 0) {
//            // no full minute -> round up
//            timeSpentSeconds += 60 - (timeSpentSeconds % 60);
//        }
        System.out.println("Creating worklog with: issue " + issue.getKey() + " timeSpentInMinutes " + timeSpentSeconds / 60 + " timeStarted " + start + " desc: " + descriptionWithoutIssueKey);

        if(start == null){
            System.out.println("ERROR: no starttime set for issue!");
            errors++;
            return;
        }

        try {
            if(autoMigrate){
                WorkLog worklog = issue.createWorkLog(descriptionWithoutIssueKey, start, timeSpentSeconds);
                System.out.println("Created worklog " + worklog.getComment() + ", started: " + worklog.getStarted() + ", timespent: " + worklog.getTimeSpent() );
                created++;
            }
            else {
                System.out.println("Do you want to create this worklog in Jira (y) or skip it (n)?");
                if(askForYesOrNo()){
                    WorkLog worklog = issue.createWorkLog(descriptionWithoutIssueKey, start, timeSpentSeconds);
                    System.out.println("Created worklog " + worklog.getComment() + ", started: " + worklog.getStarted() + ", timespent: " + worklog.getTimeSpent() );
                    created++;
                }
                else {
                    System.out.println("Skipped worklog");
                    skipped++;
                }
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


    private static void performDeleteDuplicateWorklogs(DateTime timeStarting, DateTime timeEnding) {

        String startDate = String.format("%04d-%02d-%02d", timeStarting.getYear(), timeStarting.getMonthOfYear(), timeStarting.getDayOfMonth()); // yyyy-MM-dd
        String endDate = String.format("%04d-%02d-%02d", timeEnding.getYear(), timeEnding.getMonthOfYear(), timeEnding.getDayOfMonth()); // yyyy-MM-dd
        String startDateHumanReadable = getHumanReadableDateTimeString(timeStarting);
        String endDateHumanReadable = getHumanReadableDateTimeString(timeEnding);
        System.out.println("Deleting worklogs for timespan (" + startDateHumanReadable + " to " + endDateHumanReadable +")");
        System.out.println("Do you want to continue yes (y) or no (n)");
        if(!askForYesOrNo()){
            System.out.println("exiting");
            return;
        }

        skipTheRest = false;
        int count = 1;
        int deleted = 0, errorsDeleting = 0;
        try {
            System.out.println("fetching worklogs for july");
            RestClient restClient = sJira.getRestClient();
            List<TempoWorkLog> worklogs = Tempo.getWorklogs(restClient, startDate, endDate, "priegler"); // yyyy-MM-dd

            if (worklogs != null) {
                for (int i = 0; i < worklogs.size() - 1; i++) {
                    TempoWorkLog workLog1 = worklogs.get(i);
                    TempoWorkLog workLog2 = worklogs.get(i + 1);

                    if (workLog1.isDuplicate(workLog2)) {
                        System.out.println(workLog1.getComment() + " == " + workLog2.getComment());
                        System.out.println("Deleting duplicated worklog " + workLog2);
                        boolean wasDeleted = Tempo.deleteWorklog(restClient, ""+workLog2.getId());
                        if(wasDeleted){
                            System.out.println("deleted!");
                            deleted++;
                        }
                        else {
                            System.out.println("An error occurred while deleting worklog: " + workLog2);
                            errorsDeleting++;
                        }
                    }
                    count++;
                }
            }

        } catch (JiraException e) {
            e.printStackTrace();
        }
        System.out.println("processed: #" + count);
        System.out.println("deleted: #" + deleted);
        System.out.println("errorsDeleting: #" + errorsDeleting);

    }

    private static boolean isExcludedProject(TimeEntry entry) {
        if(entry == null || entry.getProject() == null) {
            return false;
        }
        return sProjectsToIgnore.contains(entry.getProject().getName());
    }
}
