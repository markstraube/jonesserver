package com.straube.jones.dataprovider.stocks;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class StockPoints
{
    Map<String, String> meta = new HashMap<>();
    List<Map<String, String>> data = new ArrayList<>();

    public StockPoints(List<String> lines)
    {
        JSONObject jmeta = new JSONObject(lines.get(0));
        jmeta.toMap().forEach((k, v) -> {
            this.meta.put(k, String.valueOf(v));
        });
        for (int i = 1; i < lines.size(); i++ )
        {
            final Map<String, String> point = new HashMap<>();
            JSONObject jdata = new JSONObject(lines.get(i));
            jdata.toMap().forEach((k, v) -> {
                point.put(k, String.valueOf(v));
            });
            data.add(point);
        }
    }

    /**
     * @return the meta
     */
    public Map<String, String> getMeta()
    {
        return meta;
    }

    /**
     * @param meta the meta to set
     */
    public void setMeta(Map<String, String> meta)
    {
        this.meta = meta;
    }

    /**
     * @return the data
     */
    public List<Map<String, String>> getData()
    {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(List<Map<String, String>> data)
    {
        this.data = data;
    }
}
