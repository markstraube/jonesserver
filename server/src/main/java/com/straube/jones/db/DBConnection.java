package com.straube.jones.db;


import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * 
 */
public class DBConnection
{
    // JDBC driver name and database URL

    static final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    static final String DB_URL = "jdbc:mariadb://${host}/${db}";

    static boolean initialized = false;

    /**
     * @param host
     * @param user
     * @param password
     * @param db
     */
    public static Connection getConnection(final String host,
                                           final String user,
                                           final String password,
                                           final String db)
    {
        try
        {
            if (!initialized)
            {
                Class.forName("org.mariadb.jdbc.Driver");
                initialized = true;
            }
            // System.out.println("Connecting to a selected database...");
            final String connectUrl = DB_URL.replace("${host}", host).replace("${db}", db);

            Properties prop = new Properties();
            prop.put("charSet", "UTF-8");
            prop.put("user", user);
            prop.put("password", password);
            prop.put("allowPublicKeyRetrieval", "true");

            Connection connection = DriverManager.getConnection(connectUrl, prop);
            connection.setAutoCommit(false);
            // System.out.println("Connected database successfully...");
            return connection;
        }
        catch (final Exception e)
        {
            // Handle errors for Class.forName
            e.printStackTrace();
        }
        return null;
    }


    public static Connection getStocksConnection()
    {
        return getConnection("192.168.178.31", "stocksdb", "stocksdb", "StocksDB");
    }
}
