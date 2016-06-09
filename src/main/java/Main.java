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

    private static JiraClient jira;

    private static final String togglApiToken = "a97b5fd26a389f8bf9113cb8d0d1c12e";
    private static ch.simas.jtoggl.JToggl jToggl;


    public static void main(String[] args) {
        // write your code here
        System.out.println("starting to migrate time entries...");
        BasicCredentials creds = new BasicCredentials("priegler", "password");

        try {
            jira = new JiraClient("https://jira.9yrds.net", creds);

            //RestClient restClient = jira.getRestClient();


//            List<WorkLog> worklogs = issue.getWorkLogs();
//            for (int i = 0; i < worklogs.size(); i++) {
//                WorkLog worklog = worklogs.get(i);
//                System.out.println(worklog.getAuthor() + " >> " + worklog.getComment() + ", ");
//            }
//
//            User me = User.get(restClient, "priegler");
//
//            System.out.println(me.getDisplayName());


            //Calendar from = GregorianCalendar.getInstance(TimeZone.getDefault());

//            from.set(Calendar.HOUR, 0);
//            from.set(Calendar.MINUTE, 0);
//            from.set(Calendar.SECOND, 0);
//            from.roll(GregorianCalendar.HOUR, -12);
//
//            Calendar to = GregorianCalendar.getInstance();
//            to.set(Calendar.HOUR, 23);
//            to.set(Calendar.MINUTE, 59);
//            to.set(Calendar.SECOND, 59);

            DateTime from = new DateTime().withTimeAtStartOfDay();
            DateTime to = new DateTime().plusDays(1).withTimeAtStartOfDay();

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

//        final AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
//        final JiraRestClient restClient = factory.createWithBasicHttpAuthentication(jiraServerUri, "admin", "admin");

//        jToggl = new JToggl(togglApiToken, "api_token");
//        //jToggl.setThrottlePeriod(500l);
//        jToggl.switchLoggingOn();
//
//        List<Workspace> workspaces = jToggl.getWorkspaces();
//        workspace = workspaces.get(0);
//
//        List<TimeEntry> entries = jToggl.getTimeEntries();
//        List<Project> projects = jToggl.getProjects();

//        String description = "INT-x descr";
//        String[] data = parseIssue(description);
//        String issueNumber = data[0];
//        String issueDescription = data[1];


    }

    private static void createWorklog(Issue issue, String descriptionWithoutIssueKey, Date start, long timeSpentSeconds) {
//        try {
//            issue.createWorkLog(descriptionWithoutIssueKey, start, timeSpentSeconds);
//        } catch (JiraException ex) {
//            ex.printStackTrace();
//        }
    }

    private static Issue askForIssue(JiraClient jira) {
        net.rcarz.jiraclient.Issue issue = null;

        do {
            Scanner reader = new Scanner(System.in);
            System.out.println("To what issue do you want to add the worklog (type quit to skip)");
            String input = reader.next();
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
            Scanner reader = new Scanner(System.in);

                System.out.println("For what project do you want to search an issue (type quit to skip)");

            String input = reader.next();
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
         Scanner reader = new Scanner(System.in);
         System.out.println("Enter a description (or leave empty to skip issue/worklog creation):");
         String summary = reader.nextLine();
         if(summary != null && summary.length() == 0) {
             summary = null;
         }
         return summary;
    }

    private static boolean askForCreationOfNewIssue(String issueKey, String descriptionWithoutIssueKey) {
        Scanner reader = new Scanner(System.in);
        System.out.println("Issue with key: " + issueKey + " not found.\nDo you want to create a new one for (\""+descriptionWithoutIssueKey+"\") [y yes, n no]: ");
        String input = reader.next();
        if(input.contains("y"))
            return true;
        else
            return false;

    }

    private static boolean askForCreationOfNewIssue(String descriptionWithoutIssueKey) {
        Scanner reader = new Scanner(System.in);
        System.out.println("No issue key defined.\nDo you want to create a new one for (\"" + descriptionWithoutIssueKey + "\") [y yes, n no]: ");
        String input = reader.next();
        if(input.contains("y"))
            return true;
        else
            return false;
    }

//    private static boolean createTimeEntry(){
//        /* Create a new issue. */
//        try {
//            Issue newIssue = jira.createIssue("TEST", "Bug")
//                    .field(Field.SUMMARY, "Bat signal is broken")
//                    .field(Field.DESCRIPTION, "Commissioner Gordon reports the Bat signal is broken.")
//                    .field(Field.REPORTER, "batman")
//                    .field(Field.ASSIGNEE, "robin")
//                    .execute();
//            //newIssue.getWorkLogs();
//            final WorklogInput worklogInput = new WorklogInputBuilder(issue.getSelf())
//                    .setStartDate(new DateTime())
//                    .setComment("Comment for my worklog.")
//                    .setMinutesSpent(60)
//                    .build();
//
//            issueClient.addWorklog(issue.getWorklogUri(), worklogInput, pm);
//            return true;
//        } catch (JiraException e) {
//            e.printStackTrace();
//            return false;
//        }
//    }
//
//    private static boolean createIssue(){
//        /* Create a new issue. */
//        try {
//            final Issue issue = jira.getIssueClient().getIssue("TST-7").claim();
//            Issue newIssue = jira.createIssue("TEST", "Bug")
//                    .field(Field.SUMMARY, "Bat signal is broken")
//                    .field(Field.DESCRIPTION, "Commissioner Gordon reports the Bat signal is broken.")
//                    .field(Field.REPORTER, "batman")
//                    .field(Field.ASSIGNEE, "robin")
//                    .execute();
//            return true;
//        } catch (JiraException e) {
//            e.printStackTrace();
//            return false;
//        }
//    }

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

//        System.out.println("jiraTask="+jiraTask);
//        System.out.println("jiraDescription="+jiraDescription);
        return new String[]{jiraTask, jiraDescription};
    }

    public static List<TimeEntry> getTimeEntriesWithRange(Calendar from, Calendar to) {
        jToggl = new JToggl(togglApiToken, "api_token");
        List<TimeEntry> entries = jToggl.getTimeEntries(from.getTime(), to.getTime());
        return entries;
    }
}
