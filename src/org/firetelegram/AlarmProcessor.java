package org.firetelegram;

import java.io.BufferedReader;

import org.json.simple.JSONArray; 
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

public class AlarmProcessor {

	public static void main(String[] args) throws IOException {

		Properties prop = new Properties();
		InputStream inputStream = AlarmProcessor.class.getClassLoader().getResourceAsStream("config.properties");

        if (inputStream == null) {
            System.out.println("Sorry, unable to find config.properties");
            return;
        }
        prop.load(inputStream);
		
		final String urlEinsatz = "https://infoscreen.florian10.info/ows/infoscreen/einsatz.ashx";
		final String urlHistory = "https://infoscreen.florian10.info/ows/infoscreen/historic.ashx";
		final String cookieName = prop.getProperty("cookieSessIdKey");
		final String cookieValue = prop.getProperty("cookieSessIdVal");
		final String cookieName2 = prop.getProperty("cookieTokenIdKey");
		final String cookieValue2 = prop.getProperty("cookieTokenIdVal");

		String jsonContent = _getJsonData(urlHistory, cookieName, cookieValue);

		List<String> alreadyReportedAlarms = new ArrayList<String>();
		alreadyReportedAlarms.add("28456523");
		alreadyReportedAlarms.add("28434323");
		
		JSONObject newAlarm = _checkForNewAlarm(jsonContent, alreadyReportedAlarms);
		if (newAlarm != null) {
						
			String alarmText = _parseAlarmText(newAlarm);
						
			/**
			 * TODO:
			 *  Send Alarmtext via Telegram
			 */
			
			/**
			 * TODO:
			 *  Store Einsatznummer in CSV File = already reported
			 *  einsatz.get("EinsatzNummer").toString()
			 */
			
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
