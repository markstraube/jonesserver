package com.straube.jones;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.stocks.StockItem;
import com.straube.jones.dataprovider.stocks.StockPointLoader;
import com.straube.jones.dataprovider.stocks.StockPoints;
import com.straube.jones.dataprovider.stocks.StocksLoader;
import com.straube.jones.dataprovider.stocks.TableData;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;

@RestController
@RequestMapping(path = "/api")
public class StocksController
{
	private static final String HTML_ROOT_FOLDER = System.getProperty("data.root", "./data") + "/onVista/fundamentals/cache/";
	static
	{
		new File(HTML_ROOT_FOLDER).mkdirs();
	}

	public @RequestMapping(value = "/") String index()
	{
		return "index";
	}


	@RequestMapping(path = "/onvista/aktien/kennzahlen/{shortUrl}")
	public String getOnVistaReport(@PathVariable
	String shortUrl)
	{
		String[] segs = shortUrl.split("-");
		File htmlFile = new File(HTML_ROOT_FOLDER, segs[segs.length - 1] + ".html");
		if (htmlFile.exists())
		{
			try
			{
				String html = new String(Files.readAllBytes(htmlFile.toPath()));
				final Document doc0 = Jsoup.parse(html, "UTF-8");
				final Element e0 = doc0	.select("#__next > div.ov-content > div > section > div.col.col-12.inner-spacing--medium-top.ov-snapshot-tabs > div > section > div.col.grid.col--sm-4.col--md-8.col--lg-9.col--xl-9 > div:nth-child(2) > div > div > p").first();
				final String text = e0.text();
				
				return text;
			}
			catch (IOException e)
			{
				e.printStackTrace();
				//htmlFile.delete();
			}
		}
		return "No Data";		
	}


	@RequestMapping(path = "/stock/points/{isin}", produces = "application/json")
	public StockPoints getStockPoints(@PathVariable
	String isin)
	{
		return StockPointLoader.load(isin);
	}


	@RequestMapping(path = "/stock/data", produces = "application/json")
	public TableData getRawData(@RequestParam
	List<String> isin, @RequestParam(required = false)
	Long start, @RequestParam(required = false)
	Integer type)
	{
		if (start == null)
		{
			start = System.currentTimeMillis() - 6 * 30 * 24 * 60 * 60 * 1000; // ~6 Month back
		}
		if (type == null)
		{
			type = 0;
		}
		return StockPointLoader.loadRaw(isin, start, type);
	}

	@RequestMapping(path = "/stockItem/prefetch", produces = "application/json")
	public boolean prefetchData(@RequestParam
	String isin)
	{
		return StockPointLoader.prefetchIsin(isin);
	}

	@RequestMapping(path = "/stockItems", produces = "application/json")
	public Map<String, List<StockItem>> getStockItems()
	{
		return StocksLoader.load();
	}


	@PostMapping(path = "/prefs/{topic}", produces = "application/json")
	public String setUserPref(@PathVariable
	String topic, @RequestBody
	String userPrefs)
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


	@RequestMapping(path = "/prefs/{topic}", produces = "application/json")
	public String getUserPrefs(@PathVariable
	String topic)
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
}
