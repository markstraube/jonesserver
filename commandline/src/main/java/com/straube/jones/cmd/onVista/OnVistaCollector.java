package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.html.HttpTools;
import com.straube.jones.cmd.onVista.OnVistaModel.Column;
import com.straube.jones.cmd.onVista.OnVistaModel.Columns;
import com.straube.jones.cmd.onVista.OnVistaModel.UNITS;

public class OnVistaCollector
{
	private File ONVISTA_ROOT;
	private File ONVISTA_FINDER;

	private final static String ONVISTA_FINDER_BASE_URL = "https://www.onvista.de/aktien/finder";

	private final static String[] COLS = {	"instrument", ",instrument.wkn", ",company.branch.name", ",company.nameCountry", ",quote.last,quote.performancePct", ",doubleValues.perfW52", ",doubleValues.cnDivYieldM1", ",doubleValues.cnMarketCapM1",
											",doubleValues.cnDpsM1", ",company.branch.sector.name", ",doubleValues.perfM6,doubleValues.perfW4", ",stocksDetails.theScreenerRisk", ",doubleValues.employeesM1", ",doubleValues.turnoverM1"};

	private static final String CONTINENT_NORDAMERIKA = "289";
	private static final String CONTINENT_EUROPA = "258";
	private static final String CONTINENT_ASIEN = "259";
	private static final String[] CONTINENTS = {CONTINENT_NORDAMERIKA, CONTINENT_EUROPA, CONTINENT_ASIEN};

	private static final String QUERY_SUP500 = "https://www.onvista.de/aktien/finder?sort=instrument&order=ASC&idIndex=4359526";

	private static final String QUERY_NORTHAMERICA = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=289&cnMarketCapM1Range=5000000000;10000000000000";
	private static final String QUERY_EURO = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=258&cnMarketCapM1Range=5000000000;10000000000000";
	private static final String QUERY_ASIA = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=259&cnMarketCapM1Range=5000000000;10000000000000";

	private static final String[] QUERIES = {QUERY_NORTHAMERICA, QUERY_EURO, QUERY_ASIA};

	final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

	public OnVistaCollector(String dataRoot)
	{
		ONVISTA_ROOT = new File(dataRoot, "onVista");
		ONVISTA_ROOT.mkdirs();
		ONVISTA_FINDER = new File(ONVISTA_ROOT, "finder2");
		ONVISTA_FINDER.mkdirs();
	}


	public File getJsonFromFinder()
	{
		final LocalDate date = LocalDate.now().minusDays(1);
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		final String baseName = date.format(dtf);
		final File targetFolder = new File(ONVISTA_FINDER, baseName);
		targetFolder.mkdirs();

		AtomicInteger cnt = new AtomicInteger();
		Arrays.asList(QUERIES).forEach(query -> {
			runQuery(query, targetFolder, CONTINENTS[cnt.get()]);
			cnt.incrementAndGet();
		});
		return targetFolder;
	}


	public void runQuery(String query, File folder, String prefix)
	{
		final StringBuilder onVistaQueryUrl = new StringBuilder();
		onVistaQueryUrl.append(query);
		onVistaQueryUrl.append("&page=${PAGE}&cols=");
		OnVistaModel model = new OnVistaModel();
		Columns columns = model.columns;
		columns.columns.forEach(col -> {
			if (col.unit != UNITS.PRIMARY)
			{
				onVistaQueryUrl.append(col.id).append(",");
			}
		});
		final String onVistaUrl = onVistaQueryUrl.toString();
		try
		{
			int numRows = 0;
			AtomicInteger rowCounter = new AtomicInteger(0);
			for (int page = 0; page < 10; page++ )
			{
				File jsonFile = new File(folder, String.format("%s-%02d.json", prefix, page));
				if (!jsonFile.exists())
				{
					String htmlString = HttpTools.downloadFromWebToString(onVistaUrl.replace("${PAGE}", String.valueOf(page)));
					if (htmlString == null)
					{
						break;
					}
					Document doc = Jsoup.parse(htmlString);
					if (numRows == 0)
					{
						Elements eNumber = doc.select("#filter-chips > div.flex-layout.flex-layout__justify-content--space-between.flex-layout__align-items--center.hidden-sm.hidden-md > div.outer-spacing--large-left.text-nowrap > data");
						if (eNumber != null && !eNumber.isEmpty())
						{
							Attributes attr = eNumber.get(0).attributes();
							numRows = Integer.parseInt(attr.get("value"));
						}
					}
					// get Header
					List<String> lHeaders = new ArrayList<>();
					Elements elHeaders = doc.select("#finderResults > thead > tr > th");
					if (elHeaders.isEmpty())
					{
						break;
					}
					elHeaders.forEach(header -> {
						Elements e = header.select("span:nth-child(1)");
						if (e.text() == null || e.text().length() == 0)
						{
							e = header.select("span:nth-child(2)");
						}
						lHeaders.add(e.text());
					});

					// get Values
					List<List<String>> lValues = new ArrayList<>();
					Elements elRows = doc.select("#finderResults > tbody > tr");

					elRows.forEach(row -> {
						List<String> lRow = new ArrayList<>();
						Elements entries = row.select("td");
						entries.forEach((entry -> {
							Element e = null;
							Elements es = entry.select("data");
							if (es != null && !es.isEmpty())
							{
								String val = es.get(0).attributes().get("value");
								lRow.add(val);
							}
							else
							{
								es = entry.select("div > div > div > a");
								if (es != null && !es.isEmpty())
								{
									e = es.get(0);
									String title = e.attr("title");
									if (title != null)
									{
										String[] segs = title.split(":");
										if (segs.length == 3)
										{
											String isin = segs[2].trim();
											lRow.add(isin);
										}
									}
								}
								else
								{
									e = entry;
								}
								lRow.add(e.text());
							}
						}));
						lValues.add(lRow);
						rowCounter.incrementAndGet();
					});
					Map<String, Object> m = new HashMap<>();
					m.put("cols", lHeaders);
					m.put("values", lValues);
					JSONObject jo = new JSONObject(m);
					try (FileWriter writer = new FileWriter(jsonFile, Charset.forName("UTF-8")))
					{
						jo.write(writer, 4, 4);
					}
					if (rowCounter.get() >= numRows || lValues.size() >= 1000)
					{
						break;
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}


	public void updateFinderJsonToDB(File targetFolder)
		throws SQLException
	{
		DirectoryStream.Filter<Path> filter = file -> {
			final String fileName = file.toFile().getName();
			return (fileName.endsWith(".json"));
		};

		Path dirName = targetFolder.toPath();

		StringBuilder onVistaColumns = new StringBuilder();
		StringBuilder onVistaValues = new StringBuilder();
		OnVistaModel model = new OnVistaModel();
		Columns columns = model.columns;
		columns.columns.forEach(col -> {
			onVistaColumns.append(col.id.replace('.', '_')).append(",");
			onVistaValues.append("?,");
		});
		onVistaColumns.trimToSize();
		onVistaColumns.deleteCharAt(onVistaColumns.length() - 1).trimToSize();
		onVistaValues.trimToSize();
		onVistaValues.deleteCharAt(onVistaValues.length() - 1).trimToSize();

		final Connection connection = DBConnection.getStocksConnection();
		final PreparedStatement psTruncate = connection.prepareStatement("TRUNCATE TABLE stocksdb.tOnVista2;");
		psTruncate.executeQuery();
		connection.commit();

		try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
		{
			paths.forEach((path) -> {
				try
				{
					final PreparedStatement psInsert = connection.prepareStatement("INSERT INTO tOnVista2 (" + onVistaColumns.toString() + ") VALUES(" + onVistaValues + ")");

					String jsonString = FileUtils.readFileToString(path.toFile(), "UTF-8");
					JSONObject jo = new JSONObject(jsonString);
					JSONArray ar = jo.getJSONArray("values");
					ar.forEach((e) -> {
						try
						{
							if (e instanceof JSONArray)
							{
								List<Object> list = ((JSONArray)e).toList();
								AtomicInteger cnt = new AtomicInteger();
								list.forEach(v -> {
									try
									{
										Column col = columns.columns.get(cnt.get());
										if (col.unit == UNITS.NUMBER || col.unit == UNITS.EURO || col.unit == UNITS.PERCENT)
										{
											String s = String.valueOf(v);
											if (s == null || s.isEmpty())
											{
												psInsert.setDouble(cnt.incrementAndGet(), 0.0);
											}
											else
											{
												Double d = Double.parseDouble(String.valueOf(v));
												psInsert.setDouble(cnt.incrementAndGet(), d);
											}
										}
										else
										{
											psInsert.setString(cnt.incrementAndGet(), String.valueOf(v));
										}
									}
									catch (Exception e1)
									{
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
								});
								psInsert.addBatch();
							}
						}
						catch (Exception e2)
						{
							e2.printStackTrace();
						}
					});
					final int[] nr = psInsert.executeBatch();
					connection.commit();
				}
				catch (Exception e1)
				{
					e1.printStackTrace();
				}
			});
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DBConnection.close();
		}
	}
}
