package com.straube.jones;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.dto.BooleanResponse;
import com.straube.jones.dto.BranchDataResponse;
import com.straube.jones.dto.OnVistaReportResponse;
import com.straube.jones.dto.ServiceInfoResponse;
import com.straube.jones.dto.TableDataResponse;
import com.straube.jones.dto.UserPrefsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api")
@Tag(name = "Stocks API", description = "API für Aktieninformationen und Datenanalyse")
public class StocksController
{
	private static final String DATA_ROOT_FOLDER = System.getProperty(	"data.root",
																		"/home/mark/Software/data");
	private static final String FUNDAMENTALS_ROOT_FOLDER = DATA_ROOT_FOLDER + "/onVista/fundamentals/cache/";
	static
	{
		new File(FUNDAMENTALS_ROOT_FOLDER).mkdirs();
	}

	@Operation(summary = "Basis-Endpoint", description = "Gibt Informationen über den Service zurück")
	@ApiResponse(responseCode = "200", description = "Service-Informationen")
	@GetMapping(value = "/", produces = "application/json")
	public ServiceInfoResponse index()
	{
		return new ServiceInfoResponse();
	}


	@Operation(summary = "OnVista-Bericht abrufen", description = "Lädt einen OnVista-Bericht basierend auf der Short-URL")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Bericht erfolgreich abgerufen"),
							@ApiResponse(responseCode = "404", description = "Bericht nicht gefunden")})
	@GetMapping(path = "/onvista/aktien/kennzahlen/{short_url}", produces = "application/json")
	public OnVistaReportResponse getOnVistaReport(@Parameter(description = "Short-URL der OnVista-Aktie")
	@PathVariable("short_url")
	String shortUrl)
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
				if (e0 != null) {
					return OnVistaReportResponse.found(shortUrl, e0.text());
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return OnVistaReportResponse.notFound(shortUrl);
	}
	@Operation(summary = "Branchendaten abrufen", description = "Lädt Rohdaten für eine bestimmte Branche")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Daten erfolgreich abgerufen")})
	@GetMapping(path = "/stock/branch/data", produces = "application/json")
	public BranchDataResponse getRawDataForBranch(@Parameter(description = "Branche")
	@RequestParam(required = false)
	String branch,
													@Parameter(description = "Land")
													@RequestParam(required = false)
													String country,
													@Parameter(description = "Startzeit (Timestamp)")
													@RequestParam(value = "start_time", required = false)
													Long start)
	{
		if (start == null)
		{
			start = System.currentTimeMillis() - 6 * 30 * 24 * 60 * 60 * 1000L; // ~6 Month back
		}
		if (country == null)
		{
			country = "%";
		}
		Map<Long, Double> rawData = StockPointLoader.loadRawForBranch(branch, country, start);
		return new BranchDataResponse(branch, country, start, rawData);
	}


	@Operation(summary = "Aktienrohdaten abrufen", description = "Lädt Rohdaten für bestimmte ISINs")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Daten erfolgreich abgerufen")})
	@GetMapping(path = "/stock/data", produces = "application/json")
	public TableDataResponse getRawData(@Parameter(description = "Liste der ISINs")
	@RequestParam
	List<String> isin,
								@Parameter(description = "Startzeit (Timestamp)")
								@RequestParam(value = "start_time", required = false)
								Long start,
								@Parameter(description = "Datentyp")
								@RequestParam(required = false)
								Integer type)
	{
		if (start == null)
		{
			start = System.currentTimeMillis() - 6 * 30 * 24 * 60 * 60 * 1000L; // ~6 Month back
		}
		if (type == null)
		{
			type = 0;
		}

		// TableData direkt laden und zurückgeben
		return StockPointLoader.loadRaw(isin, start, type);
	}


	@Operation(summary = "Daten vorabladen", description = "Lädt Daten für eine ISIN vorab")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Vorabladen erfolgreich")})
	@GetMapping(path = "/stock_item/prefetch", produces = "application/json")
	public BooleanResponse prefetchData(@Parameter(description = "ISIN der Aktie")
	@RequestParam
	String isin)
	{
		boolean result = StockPointLoader.prefetchIsin(isin);
		if (result) {
			return BooleanResponse.success("Data prefetched successfully for ISIN: " + isin);
		} else {
			return BooleanResponse.failure("Failed to prefetch data for ISIN: " + isin);
		}
	}


	@Operation(summary = "Alle Aktien abrufen", description = "Lädt alle verfügbaren Aktieninformationen")
	@ApiResponse(responseCode = "200", description = "Aktieninformationen erfolgreich abgerufen")
	@GetMapping(path = "/stock_items", produces = "application/json")
	public Map<String, List<StockItem>> getStockItems()
	{
		return StocksLoader.load();
	}


	@Operation(summary = "Benutzereinstellungen setzen", description = "Speichert Benutzereinstellungen für ein bestimmtes Thema")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Einstellungen erfolgreich gespeichert"),
							@ApiResponse(responseCode = "500", description = "Fehler beim Speichern")})
	@PostMapping(path = "/prefs/{topic}", produces = "application/json")
	public UserPrefsResponse setUserPref(@Parameter(description = "Thema der Einstellungen")
	@PathVariable
	String topic,
										@Parameter(description = "Benutzereinstellungen als JSON")
										@RequestBody
										String userPrefs)
	{
		try
		{
			if ("filter".equals(topic))
			{
				UserPrefsRepo.saveFilter(userPrefs);
			}
			else
			{
				UserPrefsRepo.saveStocks(topic, userPrefs);
			}
			return UserPrefsResponse.success(topic, userPrefs);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return UserPrefsResponse.error(topic, "Failed to save preferences: " + e.getMessage());
		}
	}
	@Operation(summary = "Benutzereinstellungen abrufen", description = "Lädt Benutzereinstellungen für ein bestimmtes Thema")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Einstellungen erfolgreich abgerufen"),
							@ApiResponse(responseCode = "500", description = "Fehler beim Laden")})
	@GetMapping(path = "/prefs/{topic}", produces = "application/json")
	public UserPrefsResponse getUserPrefs(@Parameter(description = "Thema der Einstellungen")
	@PathVariable
	String topic)
	{
		try
		{
			String preferences;
			if ("filter".equals(topic))
			{
				preferences = UserPrefsRepo.getFilter();
			}
			else
			{
				preferences = UserPrefsRepo.getStocks(topic);
			}
			return UserPrefsResponse.success(topic, preferences);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return UserPrefsResponse.error(topic, "Failed to load preferences: " + e.getMessage());
		}
	}


	@Operation(summary = "Aktienchart-Bild abrufen", description = "Lädt ein PNG-Bild eines Aktiencharts")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Bild erfolgreich abgerufen", content = @Content(mediaType = "image/png")),
							@ApiResponse(responseCode = "404", description = "Bild nicht gefunden"),
							@ApiResponse(responseCode = "500", description = "Serverfehler")})
	@GetMapping(path = "/stock/image", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> getStockImage(@Parameter(description = "ISIN der Aktie")
	@RequestParam
	String isin,
											@Parameter(description = "Startzeit (Timestamp)")
											@RequestParam(value = "start_time", required = false)
											Long start,
											@Parameter(description = "Endzeit (Timestamp)")
											@RequestParam(value = "end_time", required = false)
											Long end,
											@Parameter(description = "Bildbreite")
											@RequestParam(required = false, defaultValue = "64")
											Integer width,
											@Parameter(description = "Bildhöhe")
											@RequestParam(required = false, defaultValue = "48")
											Integer height,
											@Parameter(description = "Pfad für Chart-Zeitraum")
											@RequestParam(required = false, defaultValue = "365")
											String path)
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
