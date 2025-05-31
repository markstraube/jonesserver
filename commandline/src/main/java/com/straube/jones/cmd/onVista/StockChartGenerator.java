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

/**
 * Utility-Klasse zur Erzeugung von Aktienchart-Bildern.
 */
public class StockChartGenerator
{
    private static final int DEFAULTMARGIN = 40;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Color LIGHT_BLUE_FILL = new Color(80, 90, 190, 150); // Transparente Füllfarbe für Chartfläche
    private static final Color GRID_COLOR = Color.LIGHT_GRAY;
    private static final Color AXIS_COLOR = Color.BLACK;
    private static final Color TEXT_COLOR = Color.BLACK;
    private static final int SINGLE_POINT_RECT_WIDTH = 6; // Breite für Einzelpunkt-Darstellung

    /**
     * Erzeugt ein Chart-Bild für die übergebenen Datenpunkte.
     * @param dataPoints Liste der Datenpunkte (sortiert nach Datum)
     * @param width Bildbreite
     * @param height Bildhöhe
     * @param isin ISIN für Titel/Beschriftung
     * @return BufferedImage mit Chart
     */
    public static BufferedImage generateChart(List<StockDataPoint> dataPoints, int width, int height, String isin)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Bestimme, ob Ränder/Margins gezeichnet werden sollen
        boolean showMargings = true;
        int margin = getMargin(width, height);
        if (margin == 0)
        {
            showMargings = false;
        }

        // Anti-Aliasing für bessere Darstellung aktivieren
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Hintergrund weiß füllen
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Falls keine Daten vorhanden sind, Hinweistext zeichnen
        if (dataPoints == null || dataPoints.isEmpty())
        {
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            String message = "No data available for ISIN: " + isin;
            drawTextCentered(g2d, message, width / 2, height / 2);
            g2d.dispose();
            return image;
        }

        // Datenpunkte sortieren (nach Datum)
        List<StockDataPoint> sortedDataPoints = new ArrayList<>(dataPoints);
        Collections.sort(sortedDataPoints);

        // Bestimme Zeitbereich (X-Achse)
        LocalDate minDate = sortedDataPoints.get(0).getDate();
        LocalDate maxDate = sortedDataPoints.get(sortedDataPoints.size() - 1).getDate();

        // Bestimme Wertebereich (Y-Achse)
        double dataMaxPrice = sortedDataPoints.stream().mapToDouble(StockDataPoint::getPrice).max().orElse(0.0);
        double dataMinPrice = sortedDataPoints.stream().mapToDouble(StockDataPoint::getPrice).min().orElse(0.0);

        // Minimal dargestellter Y-Wert ist 5 Punkte unterhalb des minimalen Wertes der Zeitreihe
        double yAxisMinPrice = dataMinPrice - 5.0;

        // Maximalwert für Y-Achse bestimmen
        double yAxisTentativeMaxPrice = (dataMaxPrice == 0.0) ? 10.0 : dataMaxPrice;
        if (yAxisTentativeMaxPrice < yAxisMinPrice) {
            yAxisTentativeMaxPrice = yAxisMinPrice + 10.0;
        }

        // Tick-Werte für Y-Achse berechnen
        List<Double> yTickValues = calculateTickValues(yAxisMinPrice, yAxisTentativeMaxPrice, 10);
        double yAxisMaxPriceForScaling = yTickValues.get(yTickValues.size() - 1);
        if (yAxisMaxPriceForScaling == 0 && yAxisMinPrice == 0)
        {
            yAxisMaxPriceForScaling = 10.0;
            yTickValues = calculateTickValues(0, yAxisMaxPriceForScaling, 5);
        }

        int chartWidth = width - 2 * margin;
        int chartHeight = height - 2 * margin;
        int xAxisY = height - margin; // Y-Koordinate der X-Achse

        // Y-Achse und horizontale Hilfslinien zeichnen
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
            // Y-Achsen-Beschriftung für alle Ticks (auch negative)
            if (showMargings)
            {
                g2d.drawString(String.format("%.0f", tickValue), margin - 25, y + 4);
            }
        }

        // X-Achse und vertikale Hilfslinien zeichnen
        g2d.setColor(AXIS_COLOR);
        g2d.drawLine(margin, xAxisY, width - margin, xAxisY); // X-Achse
        g2d.drawLine(margin, margin, margin, xAxisY); // Y-Achse

        // X-Achsen-Beschriftung und vertikale Hilfslinien für Datumswerte
        if (showMargings)
        {
            // Chart-Titel
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            drawTextCentered(g2d, isin, width / 2, margin / 2);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            g2d.drawString(minDate.format(DATE_FORMATTER), margin, xAxisY + 15);
            if (!minDate.equals(maxDate))
            {
                g2d.drawString(maxDate.format(DATE_FORMATTER), width - margin - fm.stringWidth(maxDate.format(DATE_FORMATTER)), xAxisY + 15);
            }
        }
        // Zusätzliche Datums-Ticks und vertikale Linien
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
                    g2d.drawLine(x, margin, x, xAxisY); // Vertikale Hilfslinie
                    g2d.setColor(TEXT_COLOR);
                    String dateStr = date.format(DATE_FORMATTER);
                    drawTextCentered(g2d, dateStr, x, xAxisY + 15, false);
                }
            }
        }

        // Fläche zwischen Chartlinie und 0.0-Linie füllen
        Polygon filledArea = new Polygon();
        if (sortedDataPoints.size() == 1)
        {
            // Einzelpunkt: Rechteck um den Punkt zeichnen
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
            // Polygon von 0.0-Linie zu allen Chartpunkten und zurück zur 0.0-Linie
            int xFirst = mapDateToX(sortedDataPoints.get(0).getDate(), minDate, maxDate, chartWidth, margin);
            int yZero = mapPriceToY(0.0, yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
            filledArea.addPoint(xFirst, yZero);

            // Alle Chartpunkte hinzufügen
            for (StockDataPoint point : sortedDataPoints)
            {
                int x = mapDateToX(point.getDate(), minDate, maxDate, chartWidth, margin);
                int y = mapPriceToY(point.getPrice(), yAxisMinPrice, yAxisMaxPriceForScaling, chartHeight, margin, xAxisY);
                filledArea.addPoint(x, y);
            }
            // Rechts wieder zur 0.0-Linie
            int xLast = mapDateToX(sortedDataPoints.get(sortedDataPoints.size() - 1).getDate(), minDate, maxDate, chartWidth, margin);
            filledArea.addPoint(xLast, yZero);
        }

        // Fläche füllen
        g2d.setColor(LIGHT_BLUE_FILL);
        g2d.fill(filledArea);

        g2d.dispose();
        return image;
    }

    /**
     * Bestimmt den Margin für das Chart abhängig von der Bildgröße.
     */
    private static int getMargin(int width, int height)
    {
        if (width < 400 || height < 300)
        {
            return 0; // Keine Margins für kleine Charts
        }
        return DEFAULTMARGIN;
    }

    /**
     * Berechnet sinnvolle Tick-Werte für die Y-Achse.
     */
    private static List<Double> calculateTickValues(double dataMin, double dataMax, int targetNumTicks)
    {
        List<Double> ticks = new ArrayList<>();
        if (dataMax < dataMin)
            dataMax = dataMin + 10;
        if (dataMax == dataMin && dataMin == 0)
            dataMax = 10.0;
        else if (dataMax == dataMin)
            dataMax = dataMin + 1.0;

        double range = dataMax - dataMin;
        if (range == 0)
        {
            ticks.add(dataMin);
            ticks.add(dataMax + 1.0);
            return ticks.stream().distinct().sorted().collect(Collectors.toList());
        }

        double rawStep = range / Math.max(1, targetNumTicks - 1);

        // "Schöne" Schrittweiten
        double[] niceSteps = {1, 2, 2.5, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000};
        double step = rawStep;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));

        for (double niceStepBase : niceSteps)
        {
            step = niceStepBase * magnitude;
            if (step >= rawStep)
                break;
        }
        // Schrittweite ggf. anpassen
        if (range / step > targetNumTicks * 1.5 && targetNumTicks > 1)
        {
            for (double niceStepBase : niceSteps)
            {
                if (niceStepBase * magnitude > step)
                {
                    step = niceStepBase * magnitude;
                    break;
                }
            }
        }

        // Ticks ab dataMin (statt 0) erzeugen
        double currentTick = Math.ceil(dataMin / step) * step;
        while (currentTick <= dataMax + step / 2)
        {
            ticks.add(roundToSensibleDecimal(currentTick));
            if (step == 0)
                break;
            currentTick += step;
        }

        if (ticks.isEmpty() || ticks.get(ticks.size() - 1) < dataMax)
        {
            ticks.add(roundToSensibleDecimal(currentTick));
        }

        if (ticks.size() < 2)
        {
            ticks.clear();
            ticks.add(dataMin);
            ticks.add(roundToSensibleDecimal(dataMax > dataMin ? dataMax : dataMin + 10.0));
        }

        return ticks.stream().distinct().sorted().collect(Collectors.toList());
    }

    /**
     * Rundet Werte für Tick-Beschriftung sinnvoll.
     */
    private static double roundToSensibleDecimal(double value)
    {
        if (value == 0)
            return 0.0;
        if (Math.abs(value) >= 1)
        {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
        else if (Math.abs(value) >= 0.01)
        {
            return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
        }
        return value;
    }

    /**
     * Zeichnet Text zentriert an die angegebene Position.
     */
    private static void drawTextCentered(Graphics2D g2d, String text, int x, int y)
    {
        drawTextCentered(g2d, text, x, y, false);
    }

    /**
     * Zeichnet Text zentriert, optional rotiert.
     */
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

    /**
     * Berechnet die X-Position für ein Datum.
     */
    private static int mapDateToX(LocalDate date, LocalDate minDate, LocalDate maxDate, int chartWidth, int margin)
    {
        if (minDate.equals(maxDate))
        {
            return margin + chartWidth / 2;
        }
        long totalDays = ChronoUnit.DAYS.between(minDate, maxDate);
        long daysFromMin = ChronoUnit.DAYS.between(minDate, date);
        if (totalDays == 0)
            return margin + chartWidth / 2;
        return margin + (int)(daysFromMin * (double)chartWidth / totalDays);
    }

    /**
     * Berechnet die Y-Position für einen Wert.
     */
    private static int mapPriceToY(double price, double minPrice, double maxPriceForScaling, int chartHeight, int topMargin, int bottomMarginY)
    {
        if (maxPriceForScaling <= minPrice)
        {
            return bottomMarginY - chartHeight / 2;
        }
        // Y ist invertiert (0 oben)
        double normalizedPrice = (price - minPrice) / (maxPriceForScaling - minPrice);
        return bottomMarginY - (int)(normalizedPrice * chartHeight);
    }
}
