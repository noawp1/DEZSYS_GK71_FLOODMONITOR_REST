package com.example.floodmonitor.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "statistics")
public class StationStatistics {
    private String stationId;
    private double minWaterLevel;
    private double maxWaterLevel;
    private double avgWaterLevel;
    private double avgFlowRate;
    private double totalRainfall;
    private long measurementCount;
    private long warningCount;
    private long criticalCount;

    public StationStatistics() {}

    public StationStatistics(String stationId, double minWaterLevel, double maxWaterLevel,
                             double avgWaterLevel, double avgFlowRate, double totalRainfall,
                             long measurementCount, long warningCount, long criticalCount) {
        this.stationId = stationId;
        this.minWaterLevel = minWaterLevel;
        this.maxWaterLevel = maxWaterLevel;
        this.avgWaterLevel = avgWaterLevel;
        this.avgFlowRate = avgFlowRate;
        this.totalRainfall = totalRainfall;
        this.measurementCount = measurementCount;
        this.warningCount = warningCount;
        this.criticalCount = criticalCount;
    }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public double getMinWaterLevel() { return minWaterLevel; }
    public void setMinWaterLevel(double minWaterLevel) { this.minWaterLevel = minWaterLevel; }

    public double getMaxWaterLevel() { return maxWaterLevel; }
    public void setMaxWaterLevel(double maxWaterLevel) { this.maxWaterLevel = maxWaterLevel; }

    public double getAvgWaterLevel() { return avgWaterLevel; }
    public void setAvgWaterLevel(double avgWaterLevel) { this.avgWaterLevel = avgWaterLevel; }

    public double getAvgFlowRate() { return avgFlowRate; }
    public void setAvgFlowRate(double avgFlowRate) { this.avgFlowRate = avgFlowRate; }

    public double getTotalRainfall() { return totalRainfall; }
    public void setTotalRainfall(double totalRainfall) { this.totalRainfall = totalRainfall; }

    public long getMeasurementCount() { return measurementCount; }
    public void setMeasurementCount(long measurementCount) { this.measurementCount = measurementCount; }

    public long getWarningCount() { return warningCount; }
    public void setWarningCount(long warningCount) { this.warningCount = warningCount; }

    public long getCriticalCount() { return criticalCount; }
    public void setCriticalCount(long criticalCount) { this.criticalCount = criticalCount; }
}

