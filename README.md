# Toggl To Jira
A commandline tool to migrate [Toggl](http.//toggl.com) time entries to Jira worklogs.

## Motivation
Lets be honest - timetrackig with Jira sucks (even with the tempo plugin).
Because I personally love Toggl and I have started using it way before I started with Jira, I created this little commandline tool to ease the migration process.

## What it does
Toggl to Jira as the name already unveils provides the migration of time tracking entries from Toggl to Jira.

### Basic features
Migrate time trackings from Toggl entries to Jira worklogs (for one day or specific time range).
During that process you can:

* assign the worklog to an existing Jira issue,
* or create an jira issue one on the fly,
* or skip the worklog at all.

## Issues with Issues
Toggl in itself does not have the fine grained concept of issues assigned to a specific project.
Therefor it is necessary to embed this information into the description text of a timetracking-entry.
For simplicity reasons we rely on the Jira issue key (e.g. INT-20, PROJ-7) to be the first part of the description!
During the migration process this information will be extracted to assign the worklog to a Jira issue.

## Prerequisites
* Locally: An installed version of Java (tested with version `1.8.0_91`)
* On the server side: A Jira instance with tempo timesheets plugin

## How to use it
As mentioned before you should keep to the convention for naming you issues accordingly.
This will save you a lot of time later on.

This means the pattern for description text in Toggl should follow:
`<JIRA_ISSUE_KEY> description text` e.g. `PROJ-7 Working on time tracking migration tool`.

### Toggl browser extension
If you use the [Toggl Browser extension for Chrome](https://chrome.google.com/webstore/detail/toggl-button-productivity/oejgccbfbmkkpaidnkphaiaecficdnfn) the description text is already formatted in the intended way.

### Configuration
Before you can get started with the migration tool, a few basic things need to be configured.
Therefore adopt the `ttj.config` file to your needs.

### Start migrating
To start the commandline tool execute the jar file: `java -jar toggl-to-jira.jar`.
If you don't have Java installed, please make sure to do this first!

After that you will be presented with some logs to confirm your config settings.
You need to log in to Jira with your according password now.
Due to security reasons it is not supported to store this password.
Afterwards you will be in the main menu and can get going.
```
************ Main menu ************
 1 ...migrate time entries of today (toggle -> jira)
 2 ...migrate time entries of yesterday  (toggle -> jira)
 3 ...migrate time entries for a given time span  (toggle -> jira)
 99 ..delete duplicated entries (jira)
 0 ...quit
 ```
The usage should be self explanatory.

Happy migrating :shipit:.

## Build from source
Feel free to fork and modify this project.

It depends on two sub-projects:

* The [jtoggl library](https://github.com/bbaumgartner/jtoggl) (unmodified in version 1.0.3)
This library is addes as gradle  dependency.
* The [jira client library](https://github.com/priegler/jira-client) (forked from [rcarz/jira-client](https://github.com/rcarz/jira-client), version 0.7.0-SNAPSHOT)
I added the possibility to interact with the Tempo API to fetch/delete/create worklogs.
This library is added as jar file dependency.

### Create the execute jar
To build the executeable jar `Shadow Jar` is used.
Run `gradle shadowJar`