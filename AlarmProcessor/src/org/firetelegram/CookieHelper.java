package org.firetelegram;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class CookieHelper {

	//final String cookieName2 = prop.getProperty("cookieTokenIdKey");
	//final String cookieValue2 = prop.getProperty("cookieTokenIdVal");
	
	public static void cookieTest(String httpsURL, String cookieName, String cookieValue) throws IOException {
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
