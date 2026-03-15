package com.straube.jones.dataprovider.yahoo;


import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class YahooFundamentals
{

    public static void main(String[] args)
        throws Exception
    {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient client = HttpClient.newBuilder()
                                      .cookieHandler(cookieManager)
                                      .followRedirects(HttpClient.Redirect.ALWAYS)
                                      .build();

        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36";

        // 1. Erst die Quote-Seite direkt aufrufen (erzeugt gültigen Cookie)
        String ticker = "AAPL";
        HttpRequest pageRequest = HttpRequest.newBuilder()
                                             .uri(URI.create("https://finance.yahoo.com/quote/" + ticker))
                                             .header("User-Agent", userAgent)
                                             .header("Accept",
                                                     "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                             .header("Accept-Language", "en-US,en;q=0.5")
                                             .GET()
                                             .build();

        HttpResponse<String> pageResponse = client.send(pageRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Cookies nach Schritt 1: " + cookieManager.getCookieStore().getCookies());

        // 2. Crumb abrufen
        HttpRequest crumbRequest = HttpRequest.newBuilder()
                                              .uri(URI.create("https://query1.finance.yahoo.com/v1/test/getcrumb"))
                                              .header("User-Agent", userAgent)
                                              .header("Accept", "*/*")
                                              .header("Referer", "https://finance.yahoo.com/quote/" + ticker)
                                              .GET()
                                              .build();

        HttpResponse<String> crumbResponse = client.send(crumbRequest, HttpResponse.BodyHandlers.ofString());

        String crumb = crumbResponse.body().trim();
        System.out.println("Crumb: " + crumb);

        // Prüfen ob Crumb gültig ist (darf kein JSON sein)
        if (crumb.startsWith("{"))
        {
            System.err.println("Crumb ungültig – Cookie wurde nicht akzeptiert!");
            return;
        }

        // Crumb URL-encoden (enthält manchmal Sonderzeichen)
        String encodedCrumb = URLEncoder.encode(crumb, StandardCharsets.UTF_8);

        // 3. Key Statistics abrufen
        String url = String.format("https://query1.finance.yahoo.com/v10/finance/quoteSummary/%s"
                        + "?modules=defaultKeyStatistics,financialData&crumb=%s", ticker, encodedCrumb);

        HttpRequest dataRequest = HttpRequest.newBuilder()
                                             .uri(URI.create(url))
                                             .header("User-Agent", userAgent)
                                             .header("Accept", "application/json")
                                             .header("Referer", "https://finance.yahoo.com/quote/" + ticker)
                                             .GET()
                                             .build();

        HttpResponse<String> dataResponse = client.send(dataRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + dataResponse.statusCode());
        System.out.println("Response: " + dataResponse.body());
    }
}
