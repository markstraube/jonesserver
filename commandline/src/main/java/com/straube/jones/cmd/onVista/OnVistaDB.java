package com.straube.jones.cmd.onVista;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.onVista.Column.UNITS;

public class OnVistaDB
{

    public static void create()
        throws SQLException
    {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE `tOnVista` (");
        OnVistaModel.columns.forEach(col -> {
            if (col.unit == UNITS.NUMBER || col.unit == UNITS.EURO || col.unit == UNITS.PERCENT)
            {
                ddl.append("`").append(col.colName).append("` double DEFAULT NULL,");
            }
            else if (col.unit == UNITS.LONG)
            {
                ddl.append("`").append(col.colName).append("` decimal(14,0) DEFAULT 0,");
            }
            else if (col.unit == UNITS.PRIMARY)
            {
                ddl.append("`").append(col.colName).append("` varchar(100) NOT NULL,");
            }
            else if (col.unit == UNITS.AUTO)
            {
                ddl.append("`").append(col.colName).append("` timestamp DEFAULT current_timestamp(),");
            }
            else
            {
                ddl.append("`").append(col.colName).append("` varchar(100) DEFAULT NULL,");
            }
        });
        ddl.trimToSize();

        ddl.append(" PRIMARY KEY (`cIsin`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        ddl.trimToSize();
        try (Connection connection = DBConnection.getStocksConnection())
        {
            PreparedStatement stmnt = connection.prepareStatement(ddl.toString());
            stmnt.execute();
            connection.commit();
        }
    }
}
