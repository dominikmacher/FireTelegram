package org.firetelegram;

import java.io.BufferedReader;

import org.json.simple.JSONArray; 
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

public class AlarmProcessor {

	private static boolean DEBUG = false;
	
	public static void main(String[] args) throws IOException {
		
		String urlEinsatz = "https://infoscreen.florian10.info/ows/infoscreen/einsatz.ashx";
		String urlHistory = "https://infoscreen.florian10.info/ows/infoscreen/historic.ashx";
		
		if (args.length > 0) { 
			for (String parameter : args) {
				if (parameter.toLowerCase().equals("-debug")) {
					DEBUG = true;
				}
				else if (parameter.toLowerCase().equals("-history")) {
					urlEinsatz = urlHistory;
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
		final String cookieName2 = prop.getProperty("cookieTokenIdKey");
		final String cookieValue2 = prop.getProperty("cookieTokenIdVal");
        final String telegramApiToken = prop.getProperty("telegramApiToken");
        final String telegramChatId = prop.getProperty("telegramChatId");
        
        final String processedAlarmsFileName = "processed_alarms.txt";
		List<String> alreadyReportedAlarms = _readFile(processedAlarmsFileName);

		String jsonContent = _getJsonData(urlEinsatz, cookieName, cookieValue);
		JSONObject newAlarm = _checkForNewAlarm(jsonContent, alreadyReportedAlarms);
		if (newAlarm != null) {
						
			String alarmText = _parseAlarmText(newAlarm);
			
			if (DEBUG) {
				System.out.println("Processed alarm \"" + alarmText + "\" - no telegram message sent.");
			}
			else {
				// Do not send telegram messages in debug mode
				_sendMessageToTelegram(alarmText, telegramApiToken, telegramChatId);
			}

			_writeToFile(processedAlarmsFileName, newAlarm.get("EinsatzNummer").toString().trim());
		}
		else {
			if (DEBUG) {
				System.out.println("...");
			}
		}
		
	}
	
	
	private static void _writeToFile(String fileName, String text) {
		File file = new File(fileName);
		try {
			file.createNewFile();
		} catch (IOException e) {
			System.out.println("ERROR: cannot create file");
		} 
		
		try {
			FileWriter fileWriter = new FileWriter(file, true);
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
        urlString = String.format(urlString, apiToken, chatId, text);

        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            InputStream is = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
	    }
	}
	
	
	private static JSONObject _checkForNewAlarm(String jsonContent, List<String> alreadyReportedAlarms) {
		try {
			JSONObject jObj = (JSONObject) new JSONParser().parse(jsonContent);
			JSONArray jArr = (JSONArray)jObj.get("EinsatzData");
			
			for (int i=0; i<jArr.size(); i++) {
				JSONObject einsatz = (JSONObject) jArr.get(i);
				String einsatzNr = einsatz.get("EinsatzNummer").toString();
				if (alreadyReportedAlarms.contains(einsatzNr)) {
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
		String ort = (einsatz.get("Plz").toString()+" "+einsatz.get("Ort").toString()+" "+einsatz.get("Strasse").toString()).trim();
		String zusatzinfo = einsatz.get("Bemerkung").toString();
		String zeit = einsatz.get("EinsatzErzeugt").toString().replace("T", " um ");
		
		String alarmText = alarmstufe + " " + titel + ", " + ort;
		if (!zusatzinfo.isEmpty()) {
			alarmText += ", " + zusatzinfo;
		}
		alarmText += ", " + zeit;
		
		return alarmText;
	}
	

	private static String _getJsonData(String httpsURL, String cookieName, String cookieValue) throws IOException {
		HttpsURLConnection authCon = (HttpsURLConnection) new URL(httpsURL)
				.openConnection();
		authCon.setRequestProperty("Cookie", cookieName + "=" + cookieValue);
		authCon.connect();

		InputStream ins = authCon.getInputStream();
		InputStreamReader isr = new InputStreamReader(ins, "UTF-8");
		BufferedReader in = new BufferedReader(isr);
		String inputLine;
		
		StringBuffer sb = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			sb.append(inputLine);
		}
		in.close();

		if (authCon != null) {
			authCon.disconnect();
		}
		
		return sb.toString();
	}

	
	private static void _cookieTest(String httpsURL, String cookieName, String cookieValue) throws IOException {
        HttpsURLConnection authCon = (HttpsURLConnection) new URL(httpsURL).openConnection();
        authCon.setRequestProperty("Cookie", cookieName+"="+cookieValue);
        authCon.connect();
        
        // temporary to build request cookie header
        StringBuilder sb = new StringBuilder();

        // find the cookies in the response header from the first request
        List<String> cookies = authCon.getHeaderFields().get("Set-Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }

                // only want the first part of the cookie header that has the value
                String value = cookie.split(";")[0];
                sb.append(value);
            }
        }

        // build request cookie header to send on all subsequent requests
        String cookieHeader = sb.toString();
        System.out.println(cookieHeader);
	}
}
