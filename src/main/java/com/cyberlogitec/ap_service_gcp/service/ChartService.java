package com.cyberlogitec.ap_service_gcp.service;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.CategorySeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChartService {

    public byte[] generateChartForExternalEmail(List<String> weeks, List<Double> firmTeuData, List<Double> planTeuData, List<Double> utilData, List<Double> firmTeuNonApData) throws IOException {
        List<Double> utilDataScaled = new ArrayList<>();
        if (utilData != null) {
            utilDataScaled = utilData.stream()
                    .map(d -> d == null ? 0.0 : d * 100)
                    .collect(Collectors.toList());
        }

        CategoryChart chart = new CategoryChartBuilder()
                .width(900).height(500)
                .theme(Styler.ChartTheme.Matlab)
                .build();
        chart.setTitle("");

        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        chart.getStyler().setLegendLayout(Styler.LegendLayout.Horizontal);
        chart.getStyler().setAvailableSpaceFill(0.6);
        chart.getStyler().setOverlapped(false);

        chart.getStyler().setYAxisGroupPosition(1, Styler.YAxisPosition.Right);
        chart.getStyler().setDecimalPattern("#,###");
        chart.setYAxisTitle("Quantity (TEU)");
        chart.setYAxisGroupTitle(1, "Utilization (%)");

        chart.getStyler().setLabelsVisible(false);

        Color colorFirm = new Color(168, 24, 86);
        Color colorPlan = new Color(194, 204, 204);
        Color colorNonAP = new Color(208, 181, 222);
        Color colorLine = new Color(80, 80, 80);

        CategorySeries firmSeries = chart.addSeries("Firm TEU", weeks, firmTeuData);
        firmSeries.setFillColor(colorFirm);

        CategorySeries planSeries = chart.addSeries("Plan TEU", weeks, planTeuData);
        planSeries.setFillColor(colorPlan);

        CategorySeries nonApSeries = chart.addSeries("Firm TEU (Non AP)", weeks, firmTeuNonApData);
        nonApSeries.setFillColor(colorNonAP);

        CategorySeries lineSeries = chart.addSeries("Plan Utilization", weeks, utilDataScaled);
        lineSeries.setYAxisGroup(1);
        lineSeries.setChartCategorySeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Line);
        lineSeries.setLineColor(colorLine);
        lineSeries.setMarkerColor(colorLine);
        lineSeries.setMarker(SeriesMarkers.CIRCLE);
        lineSeries.setLineStyle(SeriesLines.SOLID);

        return BitmapEncoder.getBitmapBytes(chart, BitmapEncoder.BitmapFormat.PNG);
    }
}
