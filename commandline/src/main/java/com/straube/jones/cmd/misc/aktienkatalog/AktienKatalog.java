package com.straube.jones.cmd.misc.aktienkatalog;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import com.straube.jones.cmd.db.DBConnection;

/**
 * @author mark
 *
 */
public class AktienKatalog
{
    private static final Logger LOGGER = Logger.getLogger(AktienKatalog.class.getName());
    private static final String API_URL = "https://aktienkatalog.de/api/convert-ticker-isin-wkn";

    private File rootFolder;

    public AktienKatalog(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "aktienkatalog");
        this.rootFolder.mkdirs();
    }


    /**
     * die Methode holt die WKN und das Kürzel zu einer ISIN von der API von aktienkatalog.de
     * Dazu lädt sie die vorhandenen ISINs aus der Datenbank, Tabelle tOnVista und fragt für jede ISIN die API ab.
     * Der erhaltende JSON-String wird gesammelt und nachdem alle ISIN abgefragt sind werden alle Results in eine Datei StocksCode.json geschrieben.
        REQUEST EXAMPLE:
        Beispiel für eine POST-Anfrage an die API von aktienkatalog.de
        POST https://aktienkatalog.de/api/convert-ticker-isin-wkn
        Accept: application/json
        Content-Type: application/x-www-form-urlencoded
    
        type=isin&search=GB00B1YW4409
    
        RESPONSE EXAMPLE:
        Beispiel für eine JSON-Antwort von der API von aktienkatalog.de
        {
            "name": "3I Group PLC",
            "code": "III.L",
            "isin": "GB00B1YW4409",
            "wkn": "A0MU9Q",
            "logo": ".\/assets\/img\/placeholder.png"
        }        
     **/
    public boolean fetchCodes()
    {
        try (Connection connection = DBConnection.getStocksConnection())
        {
            LOGGER.log(Level.INFO, "Loading ISINs from tOnVista");

            // ISINs aus Datenbank laden
            List<String> isins = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT cIsin FROM tOnVista");
                            ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    isins.add(rs.getString("cIsin"));
                }
            }

            LOGGER.log(Level.INFO, () -> isins.size() + " ISINs loaded");

            // JSON-Objekt für alle Ergebnisse (mit ISIN als Key)
            JSONObject stocksCodeJson = new JSONObject();

            // Für jede ISIN die API abfragen
            final int[] count = {0};
            for (String isin : isins)
            {
                try
                {
                    fetchAndStoreCode(isin, stocksCodeJson);

                    count[0]++ ;
                    if (count[0] % 100 == 0)
                    {
                        final int currentCount = count[0];
                        final int totalIsins = isins.size();
                        LOGGER.log(Level.INFO,
                                   () -> currentCount + " of " + totalIsins + " ISINs fetched");
                    }
                }
                catch (InterruptedException e)
                {
                    LOGGER.log(Level.WARNING, () -> "Error fetching ISIN: " + isin);
                    Thread.currentThread().interrupt();
                    return false;
                }
                catch (Exception e)
                {
                    LOGGER.log(Level.WARNING, () -> "Error fetching ISIN: " + isin);
                }
            }

            // Alle Ergebnisse in Datei schreiben
            File outputFile = new File(rootFolder, "StocksCode.json");
            Files.writeString(Paths.get(outputFile.toURI()),
                              stocksCodeJson.toString(2),
                              StandardCharsets.UTF_8,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);

            final int finalCount = count[0];
            LOGGER.log(Level.INFO,
                       () -> "Done! " + finalCount
                                       + " Codes written to "
                                       + outputFile.getAbsolutePath());

            return true;
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error fetching codes", e);
            return false;
        }
    }


    private void fetchAndStoreCode(String isin, JSONObject stocksCodeJson)
        throws Exception
    {
        // POST-Parameter erstellen
        List<NameValuePair> formData = new ArrayList<>();
        formData.add(new BasicNameValuePair("type", "isin"));
        formData.add(new BasicNameValuePair("search", isin));

        // API abfragen mit explizitem Content-Type
        String response = postRequest(API_URL, formData);

        if (response != null && !response.isEmpty())
        {
            // Response parsen und mit ISIN als Key speichern
            JSONObject responseJson = new JSONObject(response);
            stocksCodeJson.put(isin, responseJson);
        }

        // Kurze Pause zwischen Anfragen
        Thread.sleep(1000);
    }


    private String postRequest(String url, List<NameValuePair> formData)
        throws IOException
    {
        try (CloseableHttpClient httpclient = HttpClients.createDefault())
        {
            HttpPost httppost = new HttpPost(url);

            httppost.setEntity(new UrlEncodedFormEntity(formData));
            httppost.setHeader("Accept", "application/json");
            httppost.setHeader("Content-Type", "application/x-www-form-urlencoded");

            ResponseHandler<String> responseHandler = response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };

            return httpclient.execute(httppost, responseHandler);
        }
    }

    public static void main(String[] args)
    {
        // Root-Folder für die Daten (z.B. "data")
        String rootFolder = args.length > 0 ? args[0] : "data";
        
        LOGGER.log(Level.INFO, () -> "Starting AktienKatalog with root folder: " + rootFolder);
        
        AktienKatalog katalog = new AktienKatalog(rootFolder);
        boolean success = katalog.fetchCodes();
        
        if (success)
        {
            LOGGER.log(Level.INFO, "AktienKatalog completed successfully");
            System.exit(0);
        }
        else
        {
            LOGGER.log(Level.SEVERE, "AktienKatalog finished with errors");
            System.exit(1);
        }
    }
}
