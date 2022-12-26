package com.straube.jones.cmd;


import java.io.File;
import java.sql.SQLException;

import com.straube.jones.cmd.onVista.OnVistaCollector;
import com.straube.jones.cmd.onVista.OnVistaDB;
import com.straube.jones.cmd.onVista.OnVistaIndexer;

/**
 * Hello world!
 */
public class StocksGrabber
{
	public static void main(final String[] args) throws SQLException
	{
		final String dataRoot;
		if (args.length > 0 && args[0].length() > 0)
		{
			dataRoot = args[0];
		}
		else
		{
			dataRoot = "./data";
		}
		/** OnVista */
		//createDB();
		OnVistaCollector onVista = new OnVistaCollector(dataRoot);
		File targetFolder = onVista.getJsonFromFinder();
  		onVista.updateFinderJsonToDB(targetFolder);
		//OnVistaIndexer.index(targetFolder, dataRoot);

		System.out.println("Done - leaving program");
	}

	private static void createDB()
	{
		try
		{
			OnVistaDB.create();
		}
		catch (SQLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
