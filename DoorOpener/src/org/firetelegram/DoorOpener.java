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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class DoorOpener {

	private static boolean DEBUG = false;
	
	public static void main(String[] args) throws IOException {
		
		String fireTelegramJsonWebserviceUrl = "";
		
		if (args.length > 0) { 
			for (String parameter : args) {
				if (parameter.toLowerCase().equals("-h") || parameter.toLowerCase().equals("--help")) {
					System.out.println("Usage: java -jar firetelegram_dooropener.jar [options] <url>");
					System.out.println("  [-d|--debug]    print all log messages to console");
		            return;
		        }
				else if (parameter.toLowerCase().equals("--debug") || parameter.toLowerCase().equals("-d")) {
					DEBUG = true;
				}
				else {
					// this would check for the protocol
					URL u = new URL(parameter.toLowerCase()); 
					try {
						// does the extra checking required for validation of URI 
						fireTelegramJsonWebserviceUrl = u.toURI().toString();
					} catch (URISyntaxException e) {
					} 
				}
			}
        } 
		
		Properties prop = new Properties();
		InputStream inputStream = DoorOpener.class.getClassLoader().getResourceAsStream("config.properties");

        if (inputStream == null) {
            System.out.println("ERROR: cannot find properties file");
            return;
        }
        prop.load(inputStream);
        
        if (fireTelegramJsonWebserviceUrl.isEmpty()) {
        	fireTelegramJsonWebserviceUrl = prop.getProperty("fireTelegramUrl");
        }
		
		if (DEBUG) {
			System.out.println("Start reading alarmstatus from " + fireTelegramJsonWebserviceUrl);
		}
		
		Boolean activeAlarm = _getAlarmStatusFromFromJsonWebservice(fireTelegramJsonWebserviceUrl);
		
		if (DEBUG) {
			System.out.println("Active alarm \"" +  activeAlarm + "\".");
		}
		
		if (activeAlarm) {
			// now set some IO pins to LOW or HIGH
		}
		else {
			if (DEBUG) {
				System.out.println("...");
			}
		}
				
	}
	
	


	private static Boolean _getAlarmStatusFromFromJsonWebservice(String httpURL) throws IOException {
		ProcessBuilder process = new ProcessBuilder("curl","-H","Accept: application/json",httpURL);
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
	    
	    Boolean activeAlarm = false;
	    try {
			JSONObject jObj = (JSONObject) new JSONParser().parse(sb.toString());
			activeAlarm = (Boolean)jObj.get("ACTIVE_ALARM");
		} 
	    catch (ParseException e) {
			System.out.println("ERROR during JSON parsing");
			e.printStackTrace();
		}
		
		return activeAlarm;
	}

}
