package com.straube.jones.dataprovider.stocks;


import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;

public class TableData
{
    private static final long ONE_DAY_MILLIS = (long)1000 * 60 * 60 * 24;
    List<List<Object>> data = new ArrayList<>();

    public TableData()
    {}


    public void addLines(List<String> lines, long fromDate, long toDate, int type)
    {
        Double refValue = null;
        for (int i = 1; i < lines.size(); i++ )
        {
            JSONArray jar = new JSONArray(lines.get(i));
            long date = (jar.getLong(3) / ONE_DAY_MILLIS) * ONE_DAY_MILLIS;
            if (fromDate <= date && toDate >= date)
            {
                List<Object> item = new ArrayList<>();
                item.add(jar.getString(0));
                item.add(jar.getString(1));
                item.add(jar.getString(2));
                item.add(date);
                Double value = jar.getDouble(4);
                if (type == 1)
                {
                    if (refValue == null)
                    {
                        refValue = value;
                    }
                    item.add(Math.round(((value / refValue) - 1) * 1000) / 10.0d);
                }
                else
                {
                    item.add(value);
                }
                data.add(item);
            }
        }
    }


    /**
     * @return the data
     */
    public List<List<Object>> getData()
    {
        List<Object> header = new ArrayList<>();
        header.add("isin");
        header.add("name");
        header.add("date");
        header.add("date-long");
        header.add("value");

        data.add(0, header);

        return data;
    }


    /**
     * @param data the data to set
     */
    public void setData(List<List<Object>> data)
    {
        this.data = data;
    }
}
