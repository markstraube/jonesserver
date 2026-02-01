package com.straube.jones.cmd.db;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.straube.jones.cmd.db.Column.UNITS;

public class OnVistaDB
{
    public static void create(String tableName, List<Column> model)
        throws SQLException
    {
        Map<String, String> keyMap = new HashMap<>();

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE `").append(tableName).append("`(");
        model.forEach(col -> {
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
                keyMap.put("primaryKey", col.colName);
            }
            else if (col.unit == UNITS.DATE)
            {
                ddl.append("`").append(col.colName).append("` datetime NOT NULL,");
            }
            else if (col.unit == UNITS.TIMESTAMP)
            {
                ddl.append("`").append(col.colName).append("` timestamp NOT NULL,");
            }
            else if (col.unit == UNITS.AUTO)
            {
                ddl.append("`").append(col.colName).append("` timestamp DEFAULT current_timestamp(),");
            }
            else if (col.unit == UNITS.UNSIGNED)
            {
                ddl.append("`").append(col.colName).append("` unsigned DEFAULT 0,");
            }
            else
            {
                ddl.append("`").append(col.colName).append("` varchar(100) DEFAULT NULL,");
            }
        });
        ddl.trimToSize();

        ddl.append(" PRIMARY KEY (`")
           .append(keyMap.get("primaryKey"))
           .append("`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        ddl.trimToSize();
        try (Connection connection = DBConnection.getStocksConnection())
        {
            PreparedStatement stmnt = connection.prepareStatement(ddl.toString());
            stmnt.execute();
            connection.commit();
        }
    }
}
