package com.straube.jones.cmd;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import org.apache.commons.io.FileUtils;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.straube.jones.cmd.html.HttpTools;

import net.minidev.json.JSONArray;

public class Awattar
{

    public static void main(String[] args)
        throws Exception
    {
        final String dataRoot;
        if (args.length > 0 && args[0].length() > 0)
        {
            dataRoot = args[0];
        }
        else
        {
            dataRoot = "./data";
        }
        // daily url: https://api.awattar.de/v1/marketdata
        // time range url: https://api.awattar.de/v1/marketdata?start=1646089200000&end=1677884400000
        // 2022/03/01 - 2023/03/04

        String url = "https://api.awattar.de/v1/marketdata?start=1646089200000&end=1677884400000";
        File jsonFile = new File(dataRoot, "awattar.json");
        String jsonString = HttpTools.downloadFromWebToFile(url, jsonFile, false);

        String dataPath = "$['data']";
        DocumentContext jsonContext = JsonPath.parse(jsonString);
        JSONArray result = jsonContext.read(dataPath);

        StringBuilder bldr = new StringBuilder();
        result.forEach(m -> {
            if (m instanceof LinkedHashMap)
            {
                String sTimestamp = String.valueOf(((LinkedHashMap)m).get("start_timestamp")) + "000000";
                String sPrice = String.valueOf(((LinkedHashMap)m).get("marketprice"));
                double price = Double.parseDouble(sPrice) / 10.0;
                sPrice = String.format("%.2f", price).replace(',','.');
                bldr.append(String.format("marketdata,type=price value=%s %s\n", sPrice, sTimestamp));
            }
        });
        File f = new File(dataRoot, "body.txt");
        FileUtils.writeStringToFile(f, bldr.toString(), StandardCharsets.UTF_8);
        String influxUrl = "http://192.168.178.31:8086/write?db=awattar";
        String sret = HttpTools.postBinary(influxUrl, bldr.toString());
        System.out.println(sret);
    }
}
