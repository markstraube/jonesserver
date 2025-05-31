package com.straube.jones.cmd.onVista;


import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.straube.jones.cmd.db.StockDataPoint;

public class StockChartGenerator
{
    private static final int DEFAULTMARGIN = 40;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Color LIGHT_BLUE_FILL = new Color(80, 90, 190, 150); // Light blue with transparency
    private static final Color GRID_COLOR = Color.LIGHT_GRAY;
    private static final Color AXIS_COLOR = Color.BLACK;
    private static final Color TEXT_COLOR = Color.BLACK;
    private static final int SINGLE_POINT_RECT_WIDTH = 6; // Width of the rectangle for a single data point

    public static BufferedImage generateChart(List<StockDataPoint> dataPoints, int width, int height, String isin)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        boolean showMargings = true;
        int margin = getMargin(width, height);
        if (margin == 0)
        {
            showMargings = false;
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Handle Empty Data
        if (dataPoints == null || dataPoints.isEmpty())
        {
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            String message = "No data available for ISIN: " + isin;
            drawTextCentered(g2d, message, width / 2, height / 2);
            g2d.dispose();
            return image;
        }

        List<StockDataPoint> sortedDataPoints = new ArrayList<>(dataPoints);
        Collections.sort(sortedDataPoints);

        LocalDate minDate = sortedDataPoints.get(0).getDate();
        LocalDate maxDate = sortedDataPoints.get(sortedDataPoints.size() - 1).getDate();

        double dataMaxPrice = sortedDataPoints.stream().mapToDouble(StockDataPoint::getPrice).max().orElse(0.0);
        double dataMinPrice = sortedDataPoints.stream().mapToDouble(StockDataPoint::getPrice).min().orElse(0.0);

        // Minimal dargestellter Y-Wert ist 10 Punkte unterhalb des minimalen Wertes der Zeitreihe
        double yAxisMinPrice = dataMinPrice - 10.0;
        // Optional: falls yAxisMinPrice > 0, auf 0 setzen (damit Chart nicht "über" 0 startet)
        // yAxisMinPrice = Math.min(yAxisMinPrice, 0.0);

        double yAxisTentativeMaxPrice = (dataMaxPrice == 0.0) ? 10.0 : dataMaxPrice;
        if (yAxisTentativeMaxPrice < yAxisMinPrice) {
            yAxisTentativeMaxPrice = yAxisMinPrice + 10.0;
        }

        List<Double> yTickValues = calculateTickValues(yAxisMinPrice, yAxisTentativeMaxPrice, 10);
        double yAxisMaxPriceForScaling = yTickValues.get(yTickValues.size() - 1);
        if (yAxisMaxPriceForScaling == 0 && yAxisMinPrice == 0)
        {
            yAxisMaxPriceForScaling = 10.0;
            yTickValues = calculateTickValues(0, yAxisMaxPriceForScaling, 5);
        }

        int chartWidth = width - 2 * margin;
        int chartHeight = height - 2 * margin;
        int xAxisY = height - margin;

        // Draw Y-axis and horizontal grid lines
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        for (Double tickValue : yTickValues)
        {
            int y = mapPriceToY(tickValue, yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
            if (Math.abs(tickValue) < 1e-8) // 0.0 Linie
            {
                g2d.setColor(Color.RED);
                g2d.drawLine(margin, y, width - margin, y);
            }
            else 
            {
                g2d.setColor(GRID_COLOR);
                g2d.drawLine(margin, y, width - margin, y);
            }
            g2d.setColor(TEXT_COLOR);
            // Draw y-axis label for all ticks (including negative)
            if (showMargings)
            {
                g2d.drawString(String.format("%.0f", tickValue), margin - 25, y + 4);
            }
        }

        // Draw X-axis and vertical grid lines
        g2d.setColor(AXIS_COLOR);
        g2d.drawLine(margin, xAxisY, width - margin, xAxisY); // X-axis line
        g2d.drawLine(margin, margin, margin, xAxisY); // Y-axis line

        // X-axis date ticks and vertical grid lines
        if (showMargings)
        {
            // Chart Title
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            drawTextCentered(g2d, "Stock Chart for ISIN: " + isin, width / 2, margin / 2);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            g2d.drawString(minDate.format(DATE_FORMATTER), margin, xAxisY + 15);
            if (!minDate.equals(maxDate))
            {
                g2d.drawString(maxDate.format(DATE_FORMATTER), width - margin - fm.stringWidth(maxDate.format(DATE_FORMATTER)), xAxisY + 15);
            }
        }
        // Axis Ticks and Labels
        long totalDays = ChronoUnit.DAYS.between(minDate, maxDate);
        if (totalDays > 1)
        {
            int numDateLines = Math.min(5, (int)totalDays - 1);
            if (numDateLines > 0)
            {
                for (int i = 1; i <= numDateLines; i++ )
                {
                    LocalDate date = minDate.plusDays(totalDays * i / (numDateLines + 1));
                    int x = mapDateToX(date, minDate, maxDate, chartWidth, margin);
                    g2d.setColor(GRID_COLOR);
                    g2d.drawLine(x, margin, x, xAxisY); // Vertical grid line
                    g2d.setColor(TEXT_COLOR);
                    String dateStr = date.format(DATE_FORMATTER);
                    drawTextCentered(g2d, dateStr, x, xAxisY + 15, false);
                }
            }
        }

        // Create and fill the area polygon between StockPoints and 0.0 line
        Polygon filledArea = new Polygon();
        if (sortedDataPoints.size() == 1)
        {
            StockDataPoint point = sortedDataPoints.get(0);
            int x = mapDateToX(point.getDate(), minDate, maxDate, chartWidth, margin);
            int yPrice = mapPriceToY(point.getPrice(), yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
            int yZero = mapPriceToY(0.0, yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);

            filledArea.addPoint(x - SINGLE_POINT_RECT_WIDTH / 2, yZero);
            filledArea.addPoint(x - SINGLE_POINT_RECT_WIDTH / 2, yPrice);
            filledArea.addPoint(x + SINGLE_POINT_RECT_WIDTH / 2, yPrice);
            filledArea.addPoint(x + SINGLE_POINT_RECT_WIDTH / 2, yZero);
        }
        else
        {
            // Add leftmost point at 0.0 line
            int xFirst = mapDateToX(sortedDataPoints.get(0).getDate(), minDate, maxDate, chartWidth, margin);
            int yZero = mapPriceToY(0.0, yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
            filledArea.addPoint(xFirst, yZero);

            // Add all data points
            for (StockDataPoint point : sortedDataPoints)
            {
                int x = mapDateToX(point.getDate(), minDate, maxDate, chartWidth, margin);
                int y = mapPriceToY(point.getPrice(), yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
                filledArea.addPoint(x, y);
            }
            // Add rightmost point at 0.0 line
            int xLast = mapDateToX(sortedDataPoints.get(sortedDataPoints.size() - 1).getDate(), minDate, maxDate, chartWidth, margin);
            filledArea.addPoint(xLast, yZero);
        }

        g2d.setColor(LIGHT_BLUE_FILL);
        g2d.fill(filledArea);

        g2d.dispose();
        return image;
    }


    private static int getMargin(int width, int height)
    {
        if (width < 800 || height < 600)
        {
            return 0; // No margins for small charts
        }
        return DEFAULTMARGIN;
    }


    private static List<Double> calculateTickValues(double dataMin, double dataMax, int targetNumTicks)
    {
        List<Double> ticks = new ArrayList<>();
        if (dataMax < dataMin)
            dataMax = dataMin + 10; // Ensure max is greater than min for calculation
        if (dataMax == dataMin && dataMin == 0)
            dataMax = 10.0; // Default if all data is 0
        else if (dataMax == dataMin)
            dataMax = dataMin + 1.0; // If all values are same but non-zero

        double range = dataMax - dataMin;
        if (range == 0)
        { // Handle case where all data points have the same non-zero value
            ticks.add(dataMin);
            ticks.add(dataMax + 1.0); // Add one tick above
            return ticks.stream().distinct().sorted().collect(Collectors.toList());
        }

        double rawStep = range / Math.max(1, targetNumTicks - 1); // Avoid division by zero for
                                                                  // targetNumTicks=1

        // Calculate "nice" step value (e.g., 1, 2, 5, 10, 20, 25, 50, 100...)
        double[] niceSteps = {1, 2, 2.5, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000}; // Extended
        double step = rawStep;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));

        for (double niceStepBase : niceSteps)
        {
            step = niceStepBase * magnitude;
            if (step >= rawStep)
                break;
        }
        // Refine step if it creates too many/few ticks
        if (range / step > targetNumTicks * 1.5 && targetNumTicks > 1)
        { // Too many ticks, try larger step
            for (double niceStepBase : niceSteps)
            {
                if (niceStepBase * magnitude > step)
                {
                    step = niceStepBase * magnitude;
                    break;
                }
            }
        }

        double currentTick = 0; // Y-axis always starts at 0
        // Add ticks starting from 0
        while (currentTick <= dataMax + step / 2)
        { // Add step/2 to ensure dataMax is covered
            ticks.add(roundToSensibleDecimal(currentTick));
            if (step == 0)
                break; // Avoid infinite loop if step is somehow 0
            currentTick += step;
        }

        if (ticks.isEmpty() || ticks.get(ticks.size() - 1) < dataMax)
        {
            ticks.add(roundToSensibleDecimal(currentTick)); // Ensure dataMax is covered or slightly exceeded
                                                            // by a tick
        }

        // Ensure there are at least two ticks (0 and max) if dataMax is very small or step is large
        if (ticks.size() < 2)
        {
            ticks.clear();
            ticks.add(0.0);
            ticks.add(roundToSensibleDecimal(dataMax > 0 ? dataMax : 10.0)); // If dataMax is 0, make top tick
                                                                             // 10
        }

        return ticks.stream().distinct().sorted().collect(Collectors.toList());
    }


    private static double roundToSensibleDecimal(double value)
    {
        if (value == 0)
            return 0.0;
        // For values >= 1, round to 2 decimal places. For smaller, allow more precision.
        if (Math.abs(value) >= 1)
        {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
        else if (Math.abs(value) >= 0.01)
        { return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue(); }
        return value; // Keep as is for very small numbers or let formatter handle
    }


    private static void drawTextCentered(Graphics2D g2d, String text, int x, int y)
    {
        drawTextCentered(g2d, text, x, y, false);
    }


    private static void drawTextCentered(Graphics2D g2d, String text, int x, int y, boolean rotate)
    {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        if (rotate)
        {
            Graphics2D g2dRotated = (Graphics2D)g2d.create();
            g2dRotated.translate(x - textHeight / 2.0, y + textWidth / 2.0);
            g2dRotated.rotate(Math.toRadians(-90));
            g2dRotated.drawString(text, 0, 0);
            g2dRotated.dispose();
        }
        else
        {
            g2d.drawString(text, x - textWidth / 2, y + fm.getAscent() / 2);
        }
    }


    private static int mapDateToX(LocalDate date, LocalDate minDate, LocalDate maxDate, int chartWidth, int margin)
    {
        if (minDate.equals(maxDate))
        { return margin + chartWidth / 2; }
        long totalDays = ChronoUnit.DAYS.between(minDate, maxDate);
        long daysFromMin = ChronoUnit.DAYS.between(minDate, date);
        if (totalDays == 0)
            return margin + chartWidth / 2;
        return margin + (int)(daysFromMin * (double)chartWidth / totalDays);
    }


    private static int mapPriceToY(double price, double minPrice, double maxPriceForScaling, int chartHeight, int topMargin, int bottomMarginY)
    {
        if (maxPriceForScaling <= minPrice)
        { // Avoid division by zero or negative scaling factor
            return bottomMarginY - chartHeight / 2; // Center vertically if no range
        }
        // Y is inverted in graphics (0 at top)
        double normalizedPrice = (price - minPrice) / (maxPriceForScaling - minPrice);
        return bottomMarginY - (int)(normalizedPrice * chartHeight);
    }
}
