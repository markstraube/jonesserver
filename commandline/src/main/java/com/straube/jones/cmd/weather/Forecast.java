package com.straube.jones.cmd.weather;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class Forecast
{
    private static final String HTML_NOT_FOUND = null;

    public static List<String> getFromWetterOnline()
        throws Exception
    {
        List<String> sunHours = new ArrayList<>();
        String baseURL = "https://www.wetteronline.de/wetter/kuernbach?iid=%255Bobject%2520Object%255D&location_info=%5Bobject%20Object%5D&prefpar=sun&prefpars=1000000&print=false";
        String response = HttpTools.downloadFromWebToString(baseURL);
        Document doc = Jsoup.parse(response);
        Elements els = doc.select("#sunwheel0 > div");
        els.forEach(el -> {
            sunHours.add(el.text());
        });
        return sunHours;
    }


    public static String getFromTibber()
        throws IOException
    {
        File dataRoot = new File("./data/tibber");
        dataRoot.mkdirs();
        return tibberGraphQL(dataRoot);
    }


    public static String tibberGraphQL(File dataRoot)
        throws IOException
    {
        String url = "https://api.tibber.com/v1-beta/gql";
        File jsonFile = new File(dataRoot, "tibber.json");
        System.out.println(jsonFile.getAbsolutePath());
        if (jsonFile.exists())
        { return FileUtils.readFileToString(jsonFile, "UTF-8"); }

        try (final CloseableHttpClient httpclient = HttpClients.createDefault())
        {
            URLEncoder.encode(url, StandardCharsets.UTF_8);
            final HttpPost httpPost = new HttpPost(url);
            final ResponseHandler<String> responseHandler = response -> {
                final int status = response.getStatusLine().getStatusCode();
                final HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : HTML_NOT_FOUND;
            };
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Authorization", "Bearer uQ8vF-C3Q73wIVd4n4bbIACGlMtwTvIVZoORgxCWfxU");
            httpPost.addHeader("X-REQUEST-TYPE", "GraphQL");
            try (InputStream inputStream = Forecast.class.getResourceAsStream("queryTibber.json"))
            {
                String query = new String(inputStream.readAllBytes());
                StringEntity requestBody = new StringEntity(query);
                httpPost.setEntity(requestBody);

                final String jsonString = httpclient.execute(httpPost, responseHandler);

                if (jsonString != null)
                {
                    FileUtils.writeStringToFile(jsonFile, jsonString, "UTF-8");
                }
                return jsonString;
            }
        }
    }


    public static void main(String[] args)
        throws Exception
    {
        // String[] hours = (String[])getFromWetterOnline().toArray();
        // System.out.println(hours);

        getFromTibber();

    }
}
