package com.straube.jones.dataprovider;


public class DataUtil
{

	public enum TIMESPAN
	{
		INTRADAY("INTRADAY"), WEEK("6TD"), MONTH1("1M"), MONTH3("3M"), YEAR1("1Y"), YEAR3("3Y"), YEAR5("5Y"), YEAR10("10Y");

		public final String label;

		private TIMESPAN(String lable)
		{
			this.label = lable;
		}
	}

	public static String getOrDefault(String value, String defValue)
	{
		if (value == null || value.length() == 0)
		{ return defValue; }
		return value;
	}


	public static String getChartImageWithArivaLink(String isin, TIMESPAN timeSpan)
	{
		return String.format(	"<a class=\"ariva\" href=\"https://www.ariva.de/search/search.m?searchname=%s\" target=\"_blank\"><img src=\"https://charts.onvista.de/images/onvista/plain/rw-mini-area.png?&chart.colorPlot=009900&chart.colorFill=dfeacc&key.isin=%s&timeSpan=%s&expires=3600\" class=\"table-chart-image\"/ style=></a>",
								isin,
								isin,
								timeSpan.label);
	}


	public static String getChartImageWithOnVistaLink(String shortUrl, String isin, TIMESPAN timeSpan)
	{
		return String.format(	"<a class=\"onvista\" href=\"https://www.onvista.de/aktien/%s\" target=\"_blank\"><img src=\"https://charts.onvista.de/images/onvista/plain/rw-mini-area.png?&chart.colorPlot=009900&chart.colorFill=dfeacc&key.isin=%s&timeSpan=%s&expires=3600\" class=\"table-chart-image\"/ style=></a>",
								shortUrl,
								isin,
								timeSpan.label);
	}


	public static String createOnVistaLink(String isin, String shortUrl)
	{
		String s = isin;
		if (shortUrl != null && shortUrl.length() > 0)
		{
			s = shortUrl;
		}
		return String.format("<a class=\"onvista\" href=\"https://www.onvista.de/aktien/%s\" target=\"_blank\">%s</a>", s, isin);
	}


	public static String createFinanzenNetLink(String isin, String name)
	{
		return String.format("<a class=\"finanzen-net\"href=\"https://www.finanzen.net/suchergebnis.asp?strSuchString=%s\" target=\"_blank\">%s</a>", isin, name);
	}


	public static String getChartImageWithFinanzNetLink(String isin, TIMESPAN timeSpan)
	{
		// https://charts.onvista.de/images/onvista/plain/rw-mini-area.png?&chart.colorPlot=009900&chart.colorFill=dfeacc&key.isin=US5949181045&timeSpan=3M&expires=3600
		// https://charts.onvista.de/images/onvista/plain/rw-mini-area.png?&chart.colorPlot=009900&chart.colorFill=dfeacc&key.isin=%s&timeSpan=%s&expires=3600
		return String.format(	"<a class=\"finanzen-net\" href=\"https://www.finanzen.net/suchergebnis.asp?strSuchString=%s\" target=\"_blank\"><img src=\"https://charts.onvista.de/images/onvista/plain/rw-mini-area.png?&chart.colorPlot=009900&chart.colorFill=dfeacc&key.isin=%s&timeSpan=%s&expires=3600\" class=\"table-chart-image\"/></a>",
								isin,
								isin,
								timeSpan.label);
	}
}
