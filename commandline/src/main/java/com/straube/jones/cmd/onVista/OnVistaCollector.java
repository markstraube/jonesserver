package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.db.Column;
import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.db.StockCounterDB;
import com.straube.jones.cmd.db.OnVistaModel;
import com.straube.jones.cmd.html.HttpTools;

public class OnVistaCollector
{
	private static final Logger LOGGER = Logger.getLogger(OnVistaCollector.class.getName());

	static
	{
		try
		{
			File logDir = new File("./log");
			if (!logDir.exists())
			{
				logDir.mkdirs();
			}
			java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler(	"./log/commandline.log",
																							true);
			fileHandler.setFormatter(new java.util.logging.SimpleFormatter());
			LOGGER.addHandler(fileHandler);
			LOGGER.setUseParentHandlers(false);
		}
		catch (Exception e)
		{
			System.err.println("Fehler beim Konfigurieren des Loggers: " + e.getMessage());
		}
	}

	private File onvistaRoot;
	private File onvistaFinder;

	private static final String CONTINENT_NORDAMERIKA = "289";
	private static final String CONTINENT_EUROPA = "258";
	private static final String CONTINENT_ASIEN = "259";
	private static final String[] CONTINENTS = {CONTINENT_NORDAMERIKA, CONTINENT_EUROPA, CONTINENT_ASIEN};

	private static final String QUERY_NORTHAMERICA = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=289&cnMarketCapM1Range=5000000000;10000000000000";
	private static final String QUERY_EURO = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=258&cnMarketCapM1Range=5000000000;10000000000000";
	private static final String QUERY_ASIA = "https://www.onvista.de/aktien/finder?sort=doubleValues.cnMarketCapM1&order=DESC&idContinentCompany=259&cnMarketCapM1Range=5000000000;10000000000000";

	private static final String[] QUERIES = {QUERY_NORTHAMERICA, QUERY_EURO, QUERY_ASIA};

	final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSX");

	public OnVistaCollector(String dataRoot)
	{
		onvistaRoot = new File(dataRoot, "onVista");
		onvistaRoot.mkdirs();
		onvistaFinder = new File(onvistaRoot, "finder2");
		onvistaFinder.mkdirs();
	}


	public File getJsonFromFinder()
	{
		final LocalDate date = LocalDate.now().minusDays(1);
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		final String baseName = date.format(dtf);
		final File targetFolder = new File(onvistaFinder, baseName);
		targetFolder.mkdirs();

		OnVistaParser.init(onvistaRoot);

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
		OnVistaModel.getModel().forEach(col -> {
			if (col.unit != Column.UNITS.PRIMARY)
			{
				onVistaQueryUrl.append(col.id).append(",");
			}
		});
		final String onVistaUrl = onVistaQueryUrl.toString();
		try
		{
			boolean stop = false; // Kein break/continue -> Lint-konform
			for (int page = 0; page < 10 && !stop; page++ )
			{
				File jsonFile = new File(folder, String.format("%s-%02d.json", prefix, page));
				if (!jsonFile.exists())
				{
					File htmlFile = new File(folder, String.format("%s-%02d.html", prefix, page));
					String htmlString = HttpTools.downloadFromWebToFile(onVistaUrl.replace(	"${PAGE}",
																							String.valueOf(page)),
																		htmlFile,
																		false);
					if (htmlString == null)
					{
						LOGGER.log(Level.FINE, () -> "Keine weiteren Seiten (htmlString == null) - Ende");
						stop = true;
					}
					else
					{
						JSONObject jo = parseHtml(htmlString);
						if (jo == null)
						{
							LOGGER.log(Level.FINE, () -> "Parser lieferte null - Ende");
							stop = true;
						}
						else
						{
							try (FileWriter writer = new FileWriter(jsonFile, StandardCharsets.UTF_8))
							{
								jo.write(writer, 4, 4);
							}
						}
					}
					// Falls jsonFile existiert -> keine Aktion, nächste Iteration
				}
			} // end for
		} // end try
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Fehler beim Ausführen der Query", e);
		}
	}


	private JSONObject parseHtml(String htmlString)
	{
		Document doc = Jsoup.parse(htmlString);
		// Header extrahieren
		List<String> lHeaders = new ArrayList<>();
		Elements elHeaders = doc.select("#finderResults > thead > tr > th");
		if (elHeaders.isEmpty())
		{ return null; }
		elHeaders.forEach(header -> {
			Elements e = header.select("span:nth-child(1)");
			if (e.text() == null || e.text().isEmpty())
			{
				e = header.select("span:nth-child(2)");
			}
			lHeaders.add(e.text());
		});
		// Werte extrahieren
		List<List<Object>> lValues = new ArrayList<>();
		Elements elRows = doc.select("#finderResults > tbody > tr");
		if (elRows != null)
		{
			elRows.forEach(row -> {
				List<Object> lRow = OnVistaParser.parseRow(row);
				if (lRow != null)
				{
					lValues.add(lRow);
				}
				// Ungültige Zeilen werden ignoriert
			});
		}
		Map<String, Object> m = new HashMap<>();
		m.put("cols", lHeaders);
		m.put("values", lValues);
		return new JSONObject(m);
	}


	public void updateFinderJsonToOnVistaTable(File targetFolder)
		throws SQLException
	{
		// Zusätzlich: Aktuellen Kurs je ISIN als Zeitreihen-Eintrag in tStocks speichern.
		// Zeitstempel gemäß Folder-Namen (yyyy-MM-dd) analog StocksParser (06:00:00Z)
		String folderName = targetFolder.getName();
		try
		{
			java.time.Instant timeStamp = java.time.Instant.parse(folderName + "T06:00:00.00Z");
			java.time.ZonedDateTime zonedDate = timeStamp.atZone(java.time.ZoneId.systemDefault());
			java.time.DayOfWeek dow = zonedDate.getDayOfWeek();
			if (dow.equals(java.time.DayOfWeek.SATURDAY) || dow.equals(java.time.DayOfWeek.SUNDAY))
			{
				LOGGER.log(Level.INFO, "Skipping tStocks update due to weekend folder: {0}", folderName);
				return; // Keine Aktualisierung am Wochenende
			}
		}
		catch (Exception ex)
		{
			throw new SQLException("Ungültiger Ordnername für Zeitstempel: " + folderName, ex);
		}
		DirectoryStream.Filter<Path> filter = file -> {
			final String fileName = file.toFile().getName();
			return (fileName.endsWith(".json"));
		};

		Path dirName = targetFolder.toPath();

		try (final Connection connection = DBConnection.getStocksConnection())
		{
			// PHASE 1: tOnVista Tabelle aktualisieren
			LOGGER.log(Level.INFO, "Phase 1: Aktualisiere tOnVista Tabelle");
			
			StringBuilder updateSql = new StringBuilder("UPDATE tOnVista SET ");
			List<Column> model = OnVistaModel.getModel();
			for (int i = 1; i < model.size(); i++)
			{
				Column c = model.get(i);
				if (!"cIsin".equalsIgnoreCase(c.colName))
				{
					updateSql.append(c.colName).append("=?,");
				}
			}
			updateSql.deleteCharAt(updateSql.length() - 1);
			updateSql.append(" WHERE cIsin=?");

			AtomicInteger totalUpdates = new AtomicInteger(0);
			try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
			{
				for (Path path : paths)
				{
					try (final PreparedStatement psUpdate = connection.prepareStatement(updateSql.toString()))
					{
						String jsonString = FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8);
						JSONObject jo = new JSONObject(jsonString);
						JSONArray ar = jo.getJSONArray("values");
						
						ar.forEach(e -> {
							try
							{
								if (e instanceof JSONArray jsonarray)
								{
									List<Object> list = jsonarray.toList();
									if (accepted(list))
									{
										setParamsForUpdate(psUpdate, list);
										psUpdate.addBatch();
									}
								}
							}
							catch (Exception e2)
							{
								LOGGER.log(Level.WARNING, "Fehler beim Vorbereiten des Updates", e2);
							}
						});
						
						int[] updateResults = psUpdate.executeBatch();
						connection.commit();
						totalUpdates.addAndGet(updateResults.length);
						
						long skipped = java.util.Arrays.stream(updateResults).filter(r -> r == 0).count();
						if (skipped > 0)
						{
							LOGGER.log(Level.FINE, () -> "Skipped (no existing ISIN) OnVista records: " + skipped);
						}
					}
					catch (Exception e1)
					{
						LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren von tOnVista", e1);
					}
				}
			}
			LOGGER.log(Level.INFO, () -> "Phase 1 abgeschlossen: " + totalUpdates.get() + " Updates verarbeitet");

			// PHASE 2: tStocks Tabelle aktualisieren - Daten aus tOnVista laden und übertragen
			LOGGER.log(Level.INFO, "Phase 2: Aktualisiere tStocks Tabelle aus tOnVista");
			
			AtomicInteger totalStockInserts = new AtomicInteger(0);
			
			try (final PreparedStatement psLoadOnVista = connection.prepareStatement("SELECT cIsin, cLast, cCurrency, cDateLong FROM tOnVista");
				 final PreparedStatement psDelete = connection.prepareStatement("DELETE FROM tYahoo WHERE cIsin = ? AND cSequence = ?");
				 final PreparedStatement psInsert = connection.prepareStatement("INSERT INTO tYahoo (cID, cIsin, cLast, cCurrency, cDateLong, cDate, cSequence) VALUES(?,?,?,?,?,?,?)");
				 final java.sql.ResultSet rs = psLoadOnVista.executeQuery())
			{
				while (rs.next())
				{
					try
					{
						String isin = rs.getString("cIsin");
						double last = rs.getDouble("cLast");
						String currency = "EUR";
						long dateLong = rs.getLong("cDateLong");
						
						// Berechne dayOfCentury
						int dayOfCentury = getDayOfCentury(dateLong);
						
						if (dayOfCentury == Integer.MAX_VALUE)
						{
							LOGGER.log(Level.WARNING, () -> "Ungültiger Timestamp für ISIN " + isin + ", cDateLong: " + dateLong);
							continue;
						}
						
						// Berechne cDate (SQL Timestamp) aus cDateLong
						java.sql.Timestamp cDate = new java.sql.Timestamp(dateLong);
						
						// Delete-Statement zum Batch hinzufügen
						psDelete.setString(1, isin);
						psDelete.setInt(2, dayOfCentury);
						psDelete.addBatch();
						
						// Insert-Statement zum Batch hinzufügen
						psInsert.setString(1, java.util.UUID.randomUUID().toString());
						psInsert.setString(2, isin);
						psInsert.setDouble(3, last);
						psInsert.setString(4, currency);
						psInsert.setLong(5, dateLong);
						psInsert.setTimestamp(6, cDate);
						psInsert.setInt(7, dayOfCentury);
						psInsert.addBatch();
						
						totalStockInserts.incrementAndGet();
						
						// Batch alle 100 Einträge ausführen
						if (totalStockInserts.get() % 100 == 0)
						{
							psDelete.executeBatch();
							psInsert.executeBatch();
							connection.commit();
							
							final int count = totalStockInserts.get();
							LOGGER.log(Level.INFO, () -> count + " tStocks Records verarbeitet");
						}
					}
					catch (Exception e2)
					{
						LOGGER.log(Level.WARNING, "Fehler beim Verarbeiten eines tOnVista Records", e2);
					}
				}
				
				// Verbleibende Batch-Einträge ausführen
				psDelete.executeBatch();
				psInsert.executeBatch();
				connection.commit();
				LOGGER.log(Level.INFO, () -> totalStockInserts.get() + " tStocks Records verarbeitet");
			}
			catch (Exception e1)
			{
				LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren von tStocks aus tOnVista", e1);
				throw e1;
			}
			
			LOGGER.log(Level.INFO, () -> "Phase 2 abgeschlossen: " + totalStockInserts.get() + " tStocks Records aktualisiert");
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Fehler im Update-Prozess", e);
			throw new SQLException("Update-Prozess fehlgeschlagen", e);
		}
	}


	/**
	 * Setzt die Parameter für das UPDATE Statement. Reihenfolge: Alle Nicht-PK Spalten in der in OnVistaModel
	 * definierten Reihenfolge (cRef..cUpdated) gefolgt von cIsin im WHERE.
	 */
	private void setParamsForUpdate(PreparedStatement stmnt, List<Object> params)
		throws SQLException
	{
		try
		{
			// Original Indizes aus parseRow:
			// 0 ISIN, 1 ref, 2 name, 3 WKN, 4 branch, 5 sector, 6 country,
			// 7 quote, 8 exchange, 9 dateLong, 10 currency, 11 performance,
			// 12 perfW52, 13 perfM6, 14 perfW4, 15 divYield, 16 dividend,
			// 17 capitalization, 18 risk, 19 employees, 20 turnover

			String isin = String.valueOf(params.get(0));
			Double quote = OnVistaParser.makeDouble(params.get(7));
			String currency = String.valueOf(params.get(10));
			Double capitalization = OnVistaParser.makeDouble(params.get(17));
			capitalization = recalcCapitalization(isin, quote, currency, capitalization);

			int idx = 1;
			stmnt.setString(idx++ , String.valueOf(params.get(1))); // cRef
			stmnt.setString(idx++ , String.valueOf(params.get(2))); // cName
			stmnt.setString(idx++ , String.valueOf(params.get(3))); // cNsin (WKN)
			stmnt.setString(idx++ , String.valueOf(params.get(4))); // cBranch
			stmnt.setString(idx++ , String.valueOf(params.get(5))); // cSector
			stmnt.setString(idx++ , String.valueOf(params.get(6))); // cCountryCode
			stmnt.setDouble(idx++ , quote); // cLast
			stmnt.setString(idx++ , String.valueOf(params.get(8))); // cExchange
			stmnt.setLong(idx++ , OnVistaParser.makeLong(params.get(9))); // cDateLong
			stmnt.setString(idx++ , currency); // cCurrency
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(11))); // cPerformance
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(12))); // cPerf1Year
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(13))); // cPerf6Months
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(14))); // cPerf4Weeks
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(15))); // cDividendYield
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(16))); // cDividend
			stmnt.setDouble(idx++ , capitalization); // cMarketCapitalization
			stmnt.setLong(idx++ , OnVistaParser.makeLong(params.get(18))); // cRiskRating
			stmnt.setLong(idx++ , OnVistaParser.makeLong(params.get(19))); // cEmployees
			stmnt.setDouble(idx++ , OnVistaParser.makeDouble(params.get(20))); // cTurnover
			stmnt.setTimestamp(idx++ , new Timestamp(System.currentTimeMillis())); // cUpdated

			// WHERE cIsin
			stmnt.setString(idx, isin); // letzter Parameter (WHERE cIsin) ohne unnötige idx-Erhöhung
		}
		catch (Exception ex)
		{
			throw new SQLException("Fehler beim Setzen der Update-Parameter", ex);
		}
	}


	private Double recalcCapitalization(String isin, Double quote, String currency, Double fallBack)
	{
		double result = fallBack == null ? 0d : fallBack;
		long stockCount = StockCounterDB.getStockCounter(isin);
		if (stockCount != 0)
		{
			try
			{
				if ("GBP".equalsIgnoreCase(currency))
				{
					result = CurrencyDB.getAsEuro(	currency,
													stockCount * quote / 100,
													System.currentTimeMillis());
					if (result == 0)
					{
						result = CurrencyDB.getAsEuro(	currency,
														(fallBack == null ? 0d : fallBack) / 100,
														System.currentTimeMillis());
					}
				}
				else
				{
					result = CurrencyDB.getAsEuro(currency, stockCount * quote, System.currentTimeMillis());
					if (result == 0 && fallBack != null)
					{
						result = fallBack;
					}
				}
			}
			catch (Exception ignore)
			{
				LOGGER.log(Level.FINE, () -> "Fehler bei Kapitalisierungsberechnung für ISIN=" + isin); // Stacktrace
																										// auf
																										// FINE
																										// Ebene
																										// optional
			}
		}
		return result;
	}


	private boolean accepted(List<Object> list)
	{
		if (list.size() < 3 || !(list.get(2) instanceof String))
		{ return false; }
		String name = list.get(2).toString();
		final List<String> tokens = Arrays.asList("ADR", "CDR", "RMB", "NVDR", "GDR", "YC1", "YC 1");
		for (String token : tokens)
		{
			if (name.contains(token))
			{ return false; }
		}
		return true;
	}

	/**
	 * Berechnet die Anzahl der Tage seit dem 1.1.2000
	 * @param timestamp Unix-Timestamp in Millisekunden
	 * @return Anzahl der Tage seit 1.1.2000, oder Integer.MAX_VALUE bei ungültigem/zu frühem Timestamp
	 */
	public static int getDayOfCentury(long timestamp)
	{
		// Referenzdatum: 1.1.2000 00:00:00 UTC
		LocalDate referenceDate = LocalDate.of(2000, 1, 1);
		long referenceDateMillis = referenceDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
		
		// Prüfe ob Timestamp ungültig oder vor 1.1.2000
		if (timestamp < referenceDateMillis)
		{
			return Integer.MAX_VALUE;
		}
		
		// Konvertiere Timestamp zu LocalDate
		LocalDate date = java.time.Instant.ofEpochMilli(timestamp)
			.atZone(java.time.ZoneId.systemDefault())
			.toLocalDate();
		
		// Berechne Tage zwischen Referenzdatum und gegebenem Datum
		long days = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, date);
		
		// Prüfe auf Overflow
		if (days > Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}
		
		return (int) days;
	}

	public static void main(String[] args)
	{
		LOGGER.log(Level.INFO, "Starting cSequence update in tStocks table");
		
		try (Connection connection = DBConnection.getStocksConnection())
		{
			// Hole alle unterschiedlichen Zeitstempel
			String selectQuery = "SELECT DISTINCT cDateLong FROM tStocks";
			String updateQuery = "UPDATE tStocks SET cSequence = ? WHERE cDateLong = ?";
			
			int updatedCount = 0;
			int errorCount = 0;
			
			try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery);
			     PreparedStatement updateStmt = connection.prepareStatement(updateQuery))
			{
				var resultSet = selectStmt.executeQuery();
				
				while (resultSet.next())
				{
					long dateLong = resultSet.getLong("cDateLong");
					
					// Berechne day of century
					int dayOfCentury = getDayOfCentury(dateLong);
					
					if (dayOfCentury == Integer.MAX_VALUE)
					{
						LOGGER.log(Level.WARNING, 
							() -> "Invalid timestamp, cDateLong: " + dateLong);
						errorCount++;
						continue;
					}
					
					// Update alle Records mit diesem cDateLong
					updateStmt.setInt(1, dayOfCentury);
					updateStmt.setLong(2, dateLong);
					int affectedRows = updateStmt.executeUpdate();
					
					updatedCount++;
					
					LOGGER.log(Level.FINE, 
						() -> "Updated " + affectedRows + " records for cDateLong " + dateLong + " -> dayOfCentury " + dayOfCentury);
					
					if (updatedCount % 100 == 0)
					{
						final int count = updatedCount;
						LOGGER.log(Level.INFO, () -> count + " distinct timestamps processed");
						connection.commit();
					}
				}
				
				// Final commit
				connection.commit();
				
				final int finalUpdated = updatedCount;
				final int finalErrors = errorCount;
				LOGGER.log(Level.INFO, 
					() -> "Update completed: " + finalUpdated + " distinct timestamps processed, " + finalErrors + " errors");
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error updating cSequence in tStocks", e);
		}
	}
}
