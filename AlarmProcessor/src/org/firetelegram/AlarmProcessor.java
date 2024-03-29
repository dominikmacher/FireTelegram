package org.firetelegram;

import java.io.BufferedReader;

import org.json.simple.JSONArray; 
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlarmProcessor {

	private static boolean DEBUG = false;
	private static boolean TESTMODE = false;
	private final static long MILLIS_PER_HOUR = 60 * 60 * 1000L;
	private final static long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;
	
	public static void main(String[] args) throws IOException {
		
		final String processedAlarmsFileName = "firetelegram_processed_alarms.txt";
		final String lastProcessedAlarmFileName = "firetelegram_last_processed_alarm.txt";
		final String aliveSignalFileName = "firetelegram_alive_signal.txt";
		final String alarmLogFileName = "firetelegram_alarm_log.txt";
		
		SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		
		String urlEinsatz = "https://infoscreen.florian10.info/ows/infoscreen/einsatz.ashx";
		String urlHistory = "https://infoscreen.florian10.info/ows/infoscreen/historic.ashx";
		
		if (args.length > 0) { 
			for (String parameter : args) {
				if (parameter.toLowerCase().equals("-h") || parameter.toLowerCase().equals("--help")) {
					System.out.println("Usage: java -jar firetelegram.jar");
					System.out.println("  [-d|--debug]    print all log messages to console");
					System.out.println("  [-i|--history]  take history alarms instead");
					System.out.println("  [-t|--test]     test mode: fetch alarm + send to test telegram channel \n");
		            return;
		        }
				else if (parameter.toLowerCase().equals("--debug") || parameter.toLowerCase().equals("-d")) {
					DEBUG = true;
				}
				else if (parameter.toLowerCase().equals("--history") || parameter.toLowerCase().equals("-i")) {
					urlEinsatz = urlHistory;
				}
				else if (parameter.toLowerCase().equals("--test") || parameter.toLowerCase().equals("-t")) {
					TESTMODE = true;
				}
			}
        } 
		
		Properties prop = new Properties();
		InputStream inputStream = AlarmProcessor.class.getClassLoader().getResourceAsStream("config.properties");

        if (inputStream == null) {
            System.out.println("ERROR: cannot find properties file");
            return;
        }
        prop.load(inputStream);
				
		final String cookieName = prop.getProperty("cookieSessIdKey");
		final String cookieValue = prop.getProperty("cookieSessIdVal");
        final String telegramApiToken = (TESTMODE ? prop.getProperty("telegramApiTokenTest") : prop.getProperty("telegramApiToken"));
        final String telegramChatId = (TESTMODE ? prop.getProperty("telegramChatIdTest") : prop.getProperty("telegramChatId"));
        final String nonAlarmCodes = prop.getProperty("nonAlarmCodes");
        
		List<String> alreadyReportedAlarms = _readFile(processedAlarmsFileName);

		if (DEBUG) {
			System.out.println("Start fetching alarms from webservice");
		}
		
		String jsonContent = _getJsonDataFromWebservice(urlEinsatz, cookieName, cookieValue);
		JSONObject newAlarm = _checkForNewAlarm(jsonContent, alreadyReportedAlarms);
		if (newAlarm != null) {
						
			String alarmText = _parseAlarmText(newAlarm);
			
			if (!_isNonAlarmCode(alarmText, nonAlarmCodes)) {
				// only send out messages if there is a real alarm
				_sendMessageToTelegram(alarmText, telegramApiToken, telegramChatId);
			}

			if (DEBUG) {
				System.out.println("Processed alarm \"" + alarmText + "\".");
				_writeToFile(alarmLogFileName, "==========\nProcessed alarm \"" + alarmText + "\".", true);
			}

			_writeToFile(processedAlarmsFileName, newAlarm.get("EinsatzNummer").toString().trim() + " " + newAlarm.get("Alarmstufe").toString(), true);
			
			_writeToFile(lastProcessedAlarmFileName, formatter.format(new Date()) + " - " + alarmText, false);
		}
		else {
			if (DEBUG) {
				System.out.println("...");
			}
		}
		
		Date newRuntime = new Date();
		List<String> lastPrintTime = _readFile(aliveSignalFileName);
		if (lastPrintTime.size() > 0 && !(lastPrintTime.get(0).isEmpty())) {
			try {
				Date lastRuntime = formatter.parse(lastPrintTime.get(0));
				if (Math.abs(lastRuntime.getTime() - newRuntime.getTime()) < MILLIS_PER_HOUR) {
					newRuntime = lastRuntime; 
				}
				else if (DEBUG) {
					System.out.println("Updated alive signal");
				}
				
			} catch (java.text.ParseException e) {
				System.out.println("ERROR: cannot parse last-runtime date");
			} 
		}
		_writeToFile(aliveSignalFileName, formatter.format(newRuntime), false);
	}
	
	
	private static void _writeToFile(String fileName, String text, boolean append) {
		File file = new File(fileName);
		try {
			file.createNewFile();
		} catch (IOException e) {
			System.out.println("ERROR: cannot create file");
		} 
		
		try {
			FileWriter fileWriter = new FileWriter(file, append);
			BufferedWriter bufWriter = new BufferedWriter(fileWriter);
			bufWriter.write(text);
			bufWriter.newLine();
			bufWriter.close();
			fileWriter.close();
		} catch (IOException e) {
			System.out.println("ERROR: cannot write to file");
		}
	}

	
	private static List<String> _readFile(String fileName) {
		File file = new File(fileName);
		try {
			file.createNewFile();
		} catch (IOException e) {
			System.out.println("ERROR: cannot create file");
		} 
		
		ArrayList<String> listOfLines = new ArrayList<String>();
		try {
			BufferedReader bufReader = new BufferedReader(new FileReader(fileName));
			String line = bufReader.readLine();
		    while (line != null) {
		      listOfLines.add(line);
		      line = bufReader.readLine();
		    }
		    bufReader.close();
		} catch (IOException e) {
			System.out.println("ERROR: cannot read from file");
		}
	    
		return listOfLines;
	}
	
	
	private static void _sendMessageToTelegram(String text, String apiToken, String chatId) {
		String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";
        
        try {
        	urlString = String.format(urlString, apiToken, chatId, URLEncoder.encode(text, StandardCharsets.UTF_8.toString()));
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            InputStream is = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
	    }
	}
	
	
	private static JSONObject _checkForNewAlarm(String jsonContent, List<String> alreadyReportedAlarms) {
		try {
			JSONObject jsonObj = (JSONObject) new JSONParser().parse(jsonContent);
			JSONArray jsonArr = (JSONArray)jsonObj.get("EinsatzData");
			
			for (int i=0; i<jsonArr.size(); i++) {
				JSONObject einsatz = (JSONObject) jsonArr.get(i);
				String tmpAlarm = einsatz.get("EinsatzNummer").toString().trim() + " " + einsatz.get("Alarmstufe").toString();
				
				boolean alreadyReportedAlarm = false;
				for (int j=0; j<alreadyReportedAlarms.size(); j++) {
					if (alreadyReportedAlarms.get(j).contentEquals(tmpAlarm)) {
						alreadyReportedAlarm = true;
						break;
					}
				}
				
				if (alreadyReportedAlarm) {
					continue;
				}
				
				return einsatz;
			}
		
		} catch (ParseException e) {
			System.out.println("ERROR during JSON parsing");
		}
		
		return null;
	}
	
	
	private static String _parseAlarmText(JSONObject einsatz) {
		String alarmstufe = einsatz.get("Alarmstufe").toString();
		String titel = einsatz.get("Meldebild").toString();
		
		String ort = (einsatz.get("Plz").toString()+" "+einsatz.get("Ort").toString()).trim();
		if (!einsatz.get("Strasse").toString().isEmpty()) {
			ort+= ", " + einsatz.get("Strasse").toString().trim();
		}
		if (einsatz.containsKey("Nummer1") && !einsatz.get("Nummer1").toString().isEmpty()) {
			ort+= " " + (int)Double.parseDouble(einsatz.get("Nummer1").toString());
		}
		//new: if 'Objekt' is set, then take it as full address
		String objekt = einsatz.get("Objekt").toString(); 
		if (!objekt.isEmpty()) {
			ort = objekt.trim();
		}
		
		String zusatzinfo = einsatz.get("Bemerkung").toString();
		String zeit = einsatz.get("EinsatzErzeugt").toString().replace("T", " um ");
		
		String alarmText = alarmstufe + " " + titel;
		if (!zusatzinfo.isEmpty()) {
			alarmText += ", " + zusatzinfo;
		}
		alarmText += ", " + ort + ", " + zeit;
		
		if (!einsatz.get("Lat").toString().isEmpty() && !einsatz.get("Lng").toString().isEmpty()) {
			alarmText += ", https://maps.google.com/maps?q=" + einsatz.get("Lat").toString() + "," + einsatz.get("Lng").toString();
		}
		
		return alarmText;
	}
	
	
	private static boolean _isNonAlarmCode(String alarmText, String nonAlarmCodes) {
		
		String[] nonAlarmCodesList = nonAlarmCodes.split(",");
		
	    Pattern p = Pattern.compile("([a-zA-Z]+[0-9]*) ");   // the pattern to search for
	    Matcher m = p.matcher(alarmText);
	    if (m.find())
	    {
	    	for (int i=0; i<nonAlarmCodesList.length; i++) {
	    		if (nonAlarmCodesList[i].equals(m.group(1))) {
	    			return true;
	    		}
	    	}
	    }
				
		return false;
	}
	

	private static String _getJsonDataFromWebservice(String httpsURL, String cookieName, String cookieValue) throws IOException {
		ProcessBuilder process = new ProcessBuilder("curl","--cookie",cookieName+"="+cookieValue,"-H","Accept: application/json",httpsURL);
	    Process p;
	    StringBuilder sb = new StringBuilder();
	    
	    try {
	    	p = process.start();
	    	BufferedReader reader =  new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
	    	String line = null;
	    	while ( (line = reader.readLine()) != null) {
	    		sb.append(line);
	    		sb.append(System.getProperty("line.separator"));
	    	}
	    }
	    catch (IOException e) {   
	    	System.out.print("ERROR during JSON data fetching via CURL");
	    	e.printStackTrace();
	    }
		
		return sb.toString();
	}

}
