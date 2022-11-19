package com.straube.jones.cmd.db;


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

	static Connection connection = null;

	static boolean initialized = false;

	/**
	 * @param host
	 * @param user
	 * @param password
	 * @param db
	 */
	public static Connection getConnection(final String host, final String user, final String password, final String db)
	{
		try
		{
			if (!initialized)
			{
				Class.forName("org.mariadb.jdbc.Driver");
				initialized = true;
			}
			if (connection == null || !connection.isValid(1000))
			{
				connection = null;

				System.out.println("Connecting to a selected database...");
				final String connectUrl = DB_URL.replace("${host}", host).replace("${db}", db);

				Properties prop = new Properties();
				prop.put("charSet", "UTF-8");
				prop.put("user", user);
				prop.put("password", password);
				prop.put("allowPublicKeyRetrieval","true");

				connection = DriverManager.getConnection(connectUrl, prop);
				connection.setAutoCommit(false);

				System.out.println("Connected database successfully...");
			}
		}
		catch (final Exception e)
		{
			// Handle errors for Class.forName
			e.printStackTrace();
			connection = null;
		}
		return connection;
	}


	public static Connection getStocksConnection()
	{
		return getConnection("192.168.178.142", "stocksdb", "ant0n10", "stocksdb");
	}


	public static void close()
	{
		try
		{
			if (connection != null && connection.isValid(1000))
			{
				connection.close();
			}
		}
		catch (Exception ignore)
		{}
		connection = null;
	}
}
