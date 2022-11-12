package com.straube.jones.cmd.onVista;


import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.html.HttpTools;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class OnVistaCollector
{
	private File ONVISTA_ROOT;
	private File ONVISTA_FINDER;

	private final String ONVISTA_FINDER_BASE_URL = "https://www.onvista.de/aktien/boxes/finder-json?";

	private final String[] ONVISTA_FINDER_QUERY_PARAMS = new String[]{
																		"QUOTE_aktiv=1",
																		"continent[0]=Europa",
																		"continent[1]=Nordamerika",
																		"continent[2]=Asien%20-%20Pazifik",
																		"DIVIDEND_AMOUNT[enabled]=1",
																		"DIVIDEND_AMOUNT[year]=${DIVIDEND_YEAR}",
																		"MARKET_CAPITALIZATION[enabled]=1",
																		"TURNOVER[enabled]=1",
																		"PERFORMANCE_52_WEEKS[enabled]=1",
																		"PERFORMANCE_6_MONTHS[enabled]=1",
																		"PERFORMANCE_4_WEEKS[enabled]=1",
																		"MARKET_CAPITALIZATION[min]=1000",
																		"MARKET_CAPITALIZATION[year]=${MARKET_CAPITALIZATION_YEAR}",
																		"MARKET_CAPITALIZATION[sort]=1",
																		"TURNOVER[year]=${TURNOVER_YEAR}",
																		"offset=${OFFSET}",
																		"sort=MARKET_CAPITALIZATION"
	};
	final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

	public OnVistaCollector(String dataRoot)
	{
		ONVISTA_ROOT = new File(dataRoot, "onVista");
		ONVISTA_ROOT.mkdirs();
		ONVISTA_FINDER = new File(ONVISTA_ROOT, "finder");
		ONVISTA_FINDER.mkdirs();
	}


	public File getJsonFromFinder()
	{
		final LocalDate date = LocalDate.now().minusDays(1);
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		final String baseName = date.format(dtf);
		final File targetFolder = new File(ONVISTA_FINDER, baseName);
		final StringBuilder onVistaQueryUrl = new StringBuilder();
		onVistaQueryUrl.append(ONVISTA_FINDER_BASE_URL);
		Stream<String> stream = Arrays.stream(ONVISTA_FINDER_QUERY_PARAMS);
		stream.forEach(s -> onVistaQueryUrl.append(s).append("&"));
		onVistaQueryUrl.append("order=2");
		final String onVistaUrl = onVistaQueryUrl.toString().replace("${DIVIDEND_YEAR}", "2021").replace("${MARKET_CAPITALIZATION_YEAR}", "2021").replace("${TURNOVER_YEAR}", "2021");
		try
		{
			int page = 0;
			targetFolder.mkdirs();
			String jsonString = null;
			JSONObject jo = null;
			File jsonFile = new File(targetFolder, String.format("%02d.json", page));
			if (jsonFile.exists())
			{
				jsonString = FileUtils.readFileToString(jsonFile, "UTF-8");
				jo = new JSONObject(jsonString);
			}
			else
			{
				jsonString = HttpTools.downloadFromWebToString(onVistaUrl.replace("${OFFSET}", "0"));
				jo = new JSONObject(jsonString);
				FileUtils.writeStringToFile(jsonFile, jo.toString(4), "UTF-8");
			}
			int nTotalHits = jo.getJSONObject("metaData").getInt("totalHits");
			JSONArray stock = jo.getJSONArray("stocks");

			int nPageSize = stock.length();
			int offset = nPageSize;
			while (offset < nTotalHits)
			{
				page++ ;
				jsonFile = new File(targetFolder, String.format("%02d.json", page));
				if (!jsonFile.exists())
				{
					jsonString = HttpTools.downloadFromWebToString(onVistaUrl.replace("${OFFSET}", Integer.toString(offset)));
					jo = new JSONObject(jsonString);
					FileUtils.writeStringToFile(jsonFile, jo.toString(4), "UTF-8");
				}
				offset += nPageSize;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return targetFolder;
	}


	public void updateFinderJsonToDB(File targetFolder)
	{
		final long timeStamp = System.currentTimeMillis();
		AtomicInteger inc = new AtomicInteger(0);
		final String year = targetFolder.getName().split("-")[0];
		final long minAcceptedDate = System.currentTimeMillis() - (long)24 * 60 * 60 * 1000;
		final long maxAcceptedDate = System.currentTimeMillis() + (long)24 * 60 * 60 * 1000;

		DirectoryStream.Filter<Path> filter = file -> {
			final String fileName = file.toFile().getName();
			return (fileName.endsWith(".json"));
		};

		Path dirName = targetFolder.toPath();

		try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
		{
			paths.forEach((path) -> {
				try
				{
					final Connection connection = DBConnection.getStocksConnection();
					final PreparedStatement psDelete = connection.prepareStatement("DELETE FROM tOnVista WHERE cIsin=?");
					final PreparedStatement psInsert = connection.prepareStatement("INSERT INTO tOnVista (cNsin, cName, cUrl, cCountry, cBranch, cCountryCode, cLast, cMarketCapitalization, cPerf4Weeks, cPerf6Months, cPerf1Year, cIsin, cUpdated, cCurrency, cDate, cDateLong, cDividend, cTurnover) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

					String jsonString = FileUtils.readFileToString(path.toFile(), "UTF-8");
					JSONObject jo = new JSONObject(jsonString);
					JSONArray ar = jo.getJSONArray("stocks");
					ar.forEach((e) -> {
						try
						{
							Map<String, Object> m = ((JSONObject)e).toMap();
							String country = (String)m.get("country");
							String name = (String)m.get("name");// : "Microsoft",
							String nsin = (String)m.get("nsin");// : "870747",
							String countryCode = (String)m.get("countryCode");// : "us",
							String branch = (String)m.get("branch");// : "Standardsoftware",
							String url = (String)m.get("url");// : "/aktien/Microsoft-Aktie-US5949181045"
							int n = url.lastIndexOf("-");
							String isin = "-";
							if (n > 0)
							{
								isin = url.substring(n + 1).strip();
							}
							String date = String.valueOf(m.get("date"));
							if (!"null".equals(date) && !"n.a.".equals(date))
							{
								String time = "00:00:00";
								String[] s = date.split("/");
								if (s.length == 2)
								{
									time = s[1].trim() + ":00";
								}
								else
								{
									System.err.printf("ERR: UNEXPECTED_DATEFORMAT isin:%s, date:%s, in file:%s%n", isin, date, path.toFile().getAbsolutePath());
								}
								s = s[0].split("\\.");
								final String day = s[0].trim();
								final String month = s[1].trim();
								final String stocksDateTime = String.format("%s-%s-%sT%s", year, month, day, time);
								final long stockDateLong = df.parse(stocksDateTime).getTime();
								if (stockDateLong > minAcceptedDate && stockDateLong < maxAcceptedDate)
								{
									String lastValue = ((String)m.get("last")).strip();// : "163,76 USD",
									n = lastValue.indexOf(" ");
									String currency = "-";
									Double last = 0.0;
									if (n > 0)
									{
										currency = lastValue.substring(n + 1).strip();
										last = getDouble(lastValue.substring(0, n));
									}
									else
									{
										last = getDouble(lastValue);
									}
									if (last > 0.0)
									{
										Map<String, String> m2 = (Map<String, String>)m.get("figures");
										Double MARKET_CAPITALIZATION = getDouble((String)m2.get("MARKET_CAPITALIZATION"));// :
										// "1.084.409,47",
										String sPerf4 = m2.get("PERFORMANCE_4_WEEKS").trim();
										if (sPerf4 != null && sPerf4.length() > 0)
										{
											Double PERFORMANCE_4_WEEKS = getDouble((String)m2.get("PERFORMANCE_4_WEEKS"));// :
																															// "4,91",
											Double PERFORMANCE_52_WEEKS = getDouble((String)m2.get("PERFORMANCE_52_WEEKS"));// :
																															// "58,87",
											Double PERFORMANCE_6_MONTHS = getDouble((String)m2.get("PERFORMANCE_6_MONTHS"));// :
																															// "16,72"
																															// //
											Double DIVIDEND = getDouble((String)m2.get("DIVIDEND_AMOUNT"));
											Double TURNOVER = getDouble((String)m2.get("TURNOVER"));
											/*
											 * INSERT cNsin, cName, cUrl, cCountry, cBranch, cCountryCode,
											 * cLast, cMarketCapitalization, cPerf4Weeks, cPerf6Months,
											 * cPerf1Year, cIsin, cUpdated, cCurrency, cDate, cDateLong,
											 * cDividend, cTurnover
											 */
											psInsert.setString(1, nsin);
											psInsert.setString(2, name);
											psInsert.setString(3, url);
											psInsert.setString(4, country);
											psInsert.setString(5, branch);
											psInsert.setString(6, countryCode);
											psInsert.setDouble(7, last);
											psInsert.setDouble(8, MARKET_CAPITALIZATION);
											psInsert.setDouble(9, PERFORMANCE_4_WEEKS);
											psInsert.setDouble(10, PERFORMANCE_6_MONTHS);
											psInsert.setDouble(11, PERFORMANCE_52_WEEKS);
											psInsert.setString(12, isin);
											psInsert.setLong(13, timeStamp);
											psInsert.setString(14, currency);
											psInsert.setString(15, stocksDateTime);
											psInsert.setLong(16, stockDateLong);
											psInsert.setDouble(17, DIVIDEND);
											psInsert.setDouble(18, TURNOVER);

											psDelete.setString(1, isin);

											psDelete.addBatch();
											psInsert.addBatch();
											int cnt = inc.incrementAndGet();
											if (cnt % 1000 == 0)
											{
												final int[] ndl = psDelete.executeBatch();
												connection.commit();
												final int[] nr = psInsert.executeBatch();
												connection.commit();
												// System.out.printf("updated masterdata with %d records -
												// current
												// position = %d\n", nr.length, cnt);
											}
										}
										else
										{
											System.out.printf("SKIPPING - isin=%s, no valid data in file:%s%n", isin, path.toFile().getAbsolutePath());
										}
									}
									else
									{
										System.out.printf("SKIPPING isin=%s, name=%s, url=%s - no last value%n", isin, name, url);
									}
								}
								else
								{
									System.out.printf("SKIPPING isin:%s, date:%s, in file:%s%n", isin, date, path.toFile().getAbsolutePath());
								}
							}
							else
							{
								System.out.printf("SKIPPING isin=%s, name=%s, url=%s - no date%n", isin, name, url);
							}
						}
						catch (Exception e2)
						{
							e2.printStackTrace();
						}
					});
					final int[] ndel = psDelete.executeBatch();
					connection.commit();
					final int[] nr = psInsert.executeBatch();
					connection.commit();
					// System.out.printf("updated masterdata with %d records - current position = %d%n",
					// nr.length, inc.get());
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


	private static Double getDouble(String optString)
	{
		if (optString == null || optString.length() == 0 || "n.a.".equals(optString) || "0".equals(optString))
		{ return (double)0.0; }

		String value = optString.replace(".", "").replace(",", ".").replace("%", "");
		try
		{

			return Double.parseDouble(value);
		}
		catch (Exception e)
		{
			System.out.printf("Handled exception %s - returning 0.0 as double\n", e.getMessage());
			return (double)0.0;
		}
	}
}
