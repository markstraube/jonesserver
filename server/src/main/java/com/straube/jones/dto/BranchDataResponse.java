package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

/**
 * JSON-freundliche Response-Klasse für Branchendaten
 */
public class BranchDataResponse
{
    @JsonProperty("branch")
    private String branch;

    @JsonProperty("country")
    private String country;

    @JsonProperty("start_time")
    private Long startTime;

    @JsonProperty("data_points")
    private Map<String, String> dataPoints;

    @JsonProperty("summary")
    private DataSummary summary;

    public BranchDataResponse()
    {
        this.dataPoints = new HashMap<>();
        this.summary = new DataSummary();
    }


    public BranchDataResponse(String branch, String country, Long startTime, Map<Long, Double> rawData)
    {
        this.branch = branch;
        this.country = country;
        this.startTime = startTime;
        this.dataPoints = new HashMap<>();
        this.summary = new DataSummary();

        // Konvertierung zu String-basierter Map
        for (Map.Entry<Long, Double> entry : rawData.entrySet())
        {
            dataPoints.put(entry.getKey().toString(), entry.getValue().toString());
        }

        // Summary berechnen
        calculateSummary(rawData);
    }


    private void calculateSummary(Map<Long, Double> rawData)
    {
        if (!rawData.isEmpty())
        {
            summary.setCount(rawData.size());

            double min = rawData.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = rawData.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double avg = rawData.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            summary.setMinValue(String.format("%.2f", min));
            summary.setMaxValue(String.format("%.2f", max));
            summary.setAverageValue(String.format("%.2f", avg));
        }
    }


    // Getters und Setters
    public String getBranch()
    {
        return branch;
    }


    public void setBranch(String branch)
    {
        this.branch = branch;
    }


    public String getCountry()
    {
        return country;
    }


    public void setCountry(String country)
    {
        this.country = country;
    }


    public Long getStartTime()
    {
        return startTime;
    }


    public void setStartTime(Long startTime)
    {
        this.startTime = startTime;
    }


    public Map<String, String> getDataPoints()
    {
        return dataPoints;
    }


    public void setDataPoints(Map<String, String> dataPoints)
    {
        this.dataPoints = dataPoints;
    }


    public DataSummary getSummary()
    {
        return summary;
    }


    public void setSummary(DataSummary summary)
    {
        this.summary = summary;
    }

    public static class DataSummary
    {
        @JsonProperty("count")
        private Integer count = 0;

        @JsonProperty("min_value")
        private String minValue = "0.00";

        @JsonProperty("max_value")
        private String maxValue = "0.00";

        @JsonProperty("average_value")
        private String averageValue = "0.00";

        public Integer getCount()
        {
            return count;
        }


        public void setCount(Integer count)
        {
            this.count = count;
        }


        public String getMinValue()
        {
            return minValue;
        }


        public void setMinValue(String minValue)
        {
            this.minValue = minValue;
        }


        public String getMaxValue()
        {
            return maxValue;
        }


        public void setMaxValue(String maxValue)
        {
            this.maxValue = maxValue;
        }


        public String getAverageValue()
        {
            return averageValue;
        }


        public void setAverageValue(String averageValue)
        {
            this.averageValue = averageValue;
        }
    }
}
