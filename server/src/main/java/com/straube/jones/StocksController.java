package com.straube.jones;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.stocks.StockItem;
import com.straube.jones.dataprovider.stocks.StockPointLoader;
import com.straube.jones.dataprovider.stocks.StocksLoader;
import com.straube.jones.dataprovider.stocks.TableData;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api")
@Tag(name = "Stocks API", description = "API für Aktieninformationen und Datenanalyse")
public class StocksController
{
	private static final String DATA_ROOT_FOLDER = System.getProperty("data.root", "/home/mark/Software/data");
	private static final String FUNDAMENTALS_ROOT_FOLDER = DATA_ROOT_FOLDER + "/onVista/fundamentals/cache/";
	static
	{
		new File(FUNDAMENTALS_ROOT_FOLDER).mkdirs();
	}

	@Operation(summary = "Basis-Endpoint", description = "Gibt den Namen des Services zurück")
	@ApiResponse(responseCode = "200", description = "Service-Name")
	public @RequestMapping(value = "/") String index()
	{
		return "StocksServer";
	}

	@Operation(summary = "OnVista-Bericht abrufen", description = "Lädt einen OnVista-Bericht basierend auf der Short-URL")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Bericht erfolgreich abgerufen"),
		@ApiResponse(responseCode = "404", description = "Bericht nicht gefunden")
	})
	@RequestMapping(path = "/onvista/aktien/kennzahlen/{shortUrl}")
	public String getOnVistaReport(@Parameter(description = "Short-URL der OnVista-Aktie") @PathVariable String shortUrl)
	{
		String[] segs = shortUrl.split("-");
		File htmlFile = new File(FUNDAMENTALS_ROOT_FOLDER, segs[segs.length - 1] + ".html");
		if (htmlFile.exists())
		{
			try
			{
				String html = new String(Files.readAllBytes(htmlFile.toPath()));
				final Document doc0 = Jsoup.parse(html, "UTF-8");
				final Element e0 = doc0	.select("#__next > div.ov-content > div > section > div.col.col-12.inner-spacing--medium-top.ov-snapshot-tabs > div > section > div.col.grid.col--sm-4.col--md-8.col--lg-9.col--xl-9 > div:nth-child(2) > div > div > p")
										.first();
				return e0.text();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return "No Data";
	}


	@Operation(summary = "Branchendaten abrufen", description = "Lädt Rohdaten für eine bestimmte Branche")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Daten erfolgreich abgerufen")
	})
	@RequestMapping(path = "/stock/branch/data", produces = "application/json")
	public Map<Long, Double> getRawDataForBranch(
		@Parameter(description = "Branche") @RequestParam(required = false) String branch, 
		@Parameter(description = "Land") @RequestParam(required = false) String country, 
		@Parameter(description = "Startzeit (Timestamp)") @RequestParam(required = false) Long start)
	{
		if (start == null)
		{
			start = System.currentTimeMillis() - 6 * 30 * 24 * 60 * 60 * 1000L; // ~6 Month back
		}
		if (country == null)
		{
			country = "%";
		}
		return StockPointLoader.loadRawForBranch(branch, country, start);
	}


	@Operation(summary = "Aktienrohdaten abrufen", description = "Lädt Rohdaten für bestimmte ISINs")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Daten erfolgreich abgerufen")
	})
	@RequestMapping(path = "/stock/data", produces = "application/json")
	public TableData getRawData(
		@Parameter(description = "Liste der ISINs") @RequestParam List<String> isin, 
		@Parameter(description = "Startzeit (Timestamp)") @RequestParam(required = false) Long start, 
		@Parameter(description = "Datentyp") @RequestParam(required = false) Integer type)
	{
		if (start == null)
		{
			start = System.currentTimeMillis() - 6 * 30 * 24 * 60 * 60 * 1000L; // ~6 Month back
		}
		if (type == null)
		{
			type = 0;
		}
		return StockPointLoader.loadRaw(isin, start, type);
	}


	@Operation(summary = "Daten vorabladen", description = "Lädt Daten für eine ISIN vorab")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Vorabladen erfolgreich")
	})
	@RequestMapping(path = "/stockItem/prefetch", produces = "application/json")
	public boolean prefetchData(@Parameter(description = "ISIN der Aktie") @RequestParam String isin)
	{
		return StockPointLoader.prefetchIsin(isin);
	}


	@Operation(summary = "Alle Aktien abrufen", description = "Lädt alle verfügbaren Aktieninformationen")
	@ApiResponse(responseCode = "200", description = "Aktieninformationen erfolgreich abgerufen")
	@RequestMapping(path = "/stockItems", produces = "application/json")
	public Map<String, List<StockItem>> getStockItems()
	{
		return StocksLoader.load();
	}


	@Operation(summary = "Benutzereinstellungen setzen", description = "Speichert Benutzereinstellungen für ein bestimmtes Thema")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Einstellungen erfolgreich gespeichert"),
		@ApiResponse(responseCode = "500", description = "Fehler beim Speichern")
	})
	@PostMapping(path = "/prefs/{topic}", produces = "application/json")
	public String setUserPref(
		@Parameter(description = "Thema der Einstellungen") @PathVariable String topic, 
		@Parameter(description = "Benutzereinstellungen als JSON") @RequestBody String userPrefs)
	{
		try
		{
			switch (topic)
			{
			case "filter":
				UserPrefsRepo.saveFilter(userPrefs);
				break;

			default:
				UserPrefsRepo.saveStocks(topic, userPrefs);
				break;

			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return userPrefs;
	}


	@Operation(summary = "Benutzereinstellungen abrufen", description = "Lädt Benutzereinstellungen für ein bestimmtes Thema")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Einstellungen erfolgreich abgerufen"),
		@ApiResponse(responseCode = "500", description = "Fehler beim Laden")
	})
	@RequestMapping(path = "/prefs/{topic}", produces = "application/json")
	public String getUserPrefs(@Parameter(description = "Thema der Einstellungen") @PathVariable String topic)
	{
		try
		{
			switch (topic)
			{
			case "filter":
				return UserPrefsRepo.getFilter();

			default:
				return UserPrefsRepo.getStocks(topic);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return "[]";
		}
	}


	@Operation(summary = "Aktienchart-Bild abrufen", description = "Lädt ein PNG-Bild eines Aktiencharts")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Bild erfolgreich abgerufen", 
			content = @Content(mediaType = "image/png")),
		@ApiResponse(responseCode = "404", description = "Bild nicht gefunden"),
		@ApiResponse(responseCode = "500", description = "Serverfehler")
	})
	@GetMapping(path = "/stock/image", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> getStockImage(
		@Parameter(description = "ISIN der Aktie") @RequestParam String isin,
		@Parameter(description = "Startzeit (Timestamp)") @RequestParam(required = false) Long start,
		@Parameter(description = "Endzeit (Timestamp)") @RequestParam(required = false) Long end,
		@Parameter(description = "Bildbreite") @RequestParam(required = false, defaultValue = "64") Integer width,
		@Parameter(description = "Bildhöhe") @RequestParam(required = false, defaultValue = "48") Integer height,
		@Parameter(description = "Pfad für Chart-Zeitraum") @RequestParam(required = false, defaultValue = "365") String path)
	{
		try
		{
			String dir = String.format("%s/%sx%s/%s.png", path, width, height, isin);
			File imageFile = new File(DATA_ROOT_FOLDER, dir);

			if (!imageFile.exists())
			{ return ResponseEntity.notFound().build(); }
			byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ResponseEntity.internalServerError().build();
		}
	}
}
