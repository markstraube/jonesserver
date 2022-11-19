package com.straube.jones.cmd.onVista;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.onVista.OnVistaModel.UNITS;

public class OnVistaDB
{

    public static void create()
        throws SQLException
    {
        OnVistaModel model = new OnVistaModel();

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE `tOnVista2` (");
        model.columns.columns.forEach(col -> {
            if (col.unit == UNITS.NUMBER || col.unit == UNITS.EURO || col.unit == UNITS.PERCENT)
            {
                ddl.append("`").append(col.id.replace(".", "_")).append("` double DEFAULT NULL,");
            }
            else if (col.unit == UNITS.PRIMARY) 
            {
                ddl.append("`").append(col.id.replace(".", "_")).append("` varchar(100) NOT NULL,");
            }
            else
            {
                ddl.append("`").append(col.id.replace(".", "_")).append("` varchar(100) DEFAULT NULL,");
            }
        });
        ddl.trimToSize();
        // `cName` varchar(100) DEFAULT NULL,
        // `cUrl` varchar(255) DEFAULT NULL,
        // `cCountry` varchar(100) DEFAULT NULL,
        // `cBranch` varchar(100) DEFAULT NULL,
        // `cCountryCode` varchar(100) DEFAULT NULL,
        // `cLast` double DEFAULT NULL,
        // `cMarketCapitalization` double DEFAULT NULL,
        // `cPerf4Weeks` double DEFAULT NULL,
        // `cPerf6Months` double DEFAULT NULL,
        // `cPerf1Year` double DEFAULT NULL,
        // `cIsin` varchar(100) DEFAULT NULL,
        // `cUpdated` decimal(10,0) DEFAULT NULL,
        // `cCurrency` varchar(100) DEFAULT NULL,
        // `cDate` datetime DEFAULT NULL,
        // `cDateLong` decimal(10,0) DEFAULT NULL,
        // `cDivided` double DEFAULT NULL,
        // `cTurnover` double DEFAULT NULL,
        ddl.append(" PRIMARY KEY (`isin`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        ddl.trimToSize();
        Connection connection = DBConnection.getStocksConnection();
        PreparedStatement stmnt = connection.prepareStatement(ddl.toString());
        stmnt.execute();
        connection.close(); 
    }
}
