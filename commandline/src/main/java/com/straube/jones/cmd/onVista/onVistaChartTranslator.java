package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FileUtils;

public class onVistaChartTranslator
{
    public static void main(String[] args)
        throws Exception
    {
        String filename = "C:/Dev/__GIT/jonesserver/data/onVista/DE0005800601";
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN);
        String sStart = "2020-12-25";
        long xStart = df.parse(sStart).getTime();
        String sEnd = "2021-12-25";
        long xEnd = df.parse(sEnd).getTime();
        double yStart = 11.74;
        double yEnd = 45.25;
        onVistaChartTranslator translator = new onVistaChartTranslator();
        translator.process(filename, "DE0005800601", "GFT", xStart, xEnd, yStart, yEnd);
    }


    private void process(String filename, String isin, String name, long xStart, long xEnd, double yStart, double yEnd)
        throws IOException
    {
        final List<TupleOfDouble> data = new ArrayList<>();
        final List<String> lines = FileUtils.readLines(new File(filename), "UTF-8");
        lines.forEach(line -> {
            String[] segs = line.split(" ");
            data.add(new TupleOfDouble(Double.parseDouble(segs[1]), Double.parseDouble(segs[2])));
        });
        Double xRange = data.get(0).getX() + data.get(data.size() - 1).getX();
        Double xFactor = (xEnd - xStart) / xRange;

        Double yRange = data.get(0).getY() + data.get(data.size() - 1).getY();
        Double yFactor = (yEnd - yStart) / yRange;

        try (FileWriter w = new FileWriter(new File(filename + ".json"), StandardCharsets.UTF_8))
        {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss", Locale.GERMAN);
            data.forEach(t -> {
                long timestamp = (long)(xEnd - t.getX() * xFactor);
                String date = df.format(new Date(timestamp));
                double value = yEnd - t.getY() * yFactor;
                try
                {
                    w.write(String.format("[\"%s\",\"%s\",\"%s\",%d,%f]%n", isin, name, date, timestamp, value));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        }
    }


    public onVistaChartTranslator()
    {}
}


class TupleOfDateValue
{
    private Long x;
    private Double y;

    public TupleOfDateValue(Long x, Double y)
    {
        this.x = x;
        this.y = y;
    }


    public Long getX()
    {
        return this.x;
    }


    public Double getY()
    {
        return this.y;
    }
}


class TupleOfDouble
{
    private Double x;
    private Double y;

    public TupleOfDouble(Double x, Double y)
    {
        this.x = x;
        this.y = y;
    }


    public Double getX()
    {
        return this.x;
    }


    public Double getY()
    {
        return this.y;
    }
}
