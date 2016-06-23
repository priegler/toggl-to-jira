Toggl To Jira
A commandline-tool to migrate Toggl time entries to Jira worklogs.

In order to perform the migration without the need to assign (or create) issues manually please follow this pattern for your descriptions:
JIRA_ISSUE_KEY description
e.g. PRJ-13 Creating an awesome migration tool

This way the migration tool knows which issue the time entry should be assigned to.
Nevertheless, you still can:
 * create a Jira issue during the migration
 * assign it to an existing Jira issue
 * or skip it at all.

IMPORTANT:
Please update the config to your environment before you start using the tool.

To execute inside the folder: java -jar toggl-to-jira-x.x.x-all.jar
 
CONFIG:
The config: "update_toggl_projects" when set to true will create and assign Toggl projects if the do not already have one.
The pattern for Project names should also follow: "JIRA_PROJECT_KEY Project Name"