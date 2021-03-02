# FireTelegram

JAVA Backend application which fetches alarmdata from webservice, only processes new alarmdata if it was not processed before, and sends a telegram chat message via configured TelegramBot.


## Usage / Synopsis
java -jar firetelegram.jar

  [-d|--debug]    print all log messages to console
  
  [-i|--history]  take history alarms instead
  
  [-t|--test]     test mode: fetch alarm + send to test telegram channel
  

## HowTo Crontab
In order to trigger the application every minute, the following line must be entered via "crontab -e":

"* * * * * java -jar <location_of_jar_file>/firetelegram.jar"
