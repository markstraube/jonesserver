package com.straube.jones.dataprovider.userprefs;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class UserPrefsRepo
{
    private static final String USER_PREFS_ROOT = "/home/mark/Software/data/userprefs/";
    private static final String USEER_PREFS_FILTER_FILE = "filter.json";

    static
    {
        try
        {
            Files.createDirectories(new File(USER_PREFS_ROOT).toPath());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private UserPrefsRepo()
    {}


    public static String getFilter()
        throws IOException
    {
        File f = new File(USER_PREFS_ROOT, USEER_PREFS_FILTER_FILE);
        if (!f.exists())
        { return ""; }
        return new String(Files.readAllBytes(f.toPath()));
    }


    public static String getStocks(String topic)
        throws IOException
    {
        File f = new File(USER_PREFS_ROOT, topic + ".json");
        if (!f.exists())
        { return ""; }
        return new String(Files.readAllBytes(f.toPath()));
    }


    public static String saveFilter(String filter)
        throws IOException
    {
        File f = new File(USER_PREFS_ROOT, USEER_PREFS_FILTER_FILE);
        Files.write(f.toPath(), filter.getBytes());
        return "OK";
    }


    public static String saveStocks(String topic, String stocks)
        throws IOException
    {
        File f = new File(USER_PREFS_ROOT, topic + ".json");
        Files.write(f.toPath(), stocks.getBytes());
        return "OK";
    }
}
