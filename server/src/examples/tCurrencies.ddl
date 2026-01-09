-- StocksDB.tCurrencies definition

CREATE TABLE `tCurrencies` (
  `cId` int unsigned NOT NULL AUTO_INCREMENT,
  `cCurrency` varchar(6) NOT NULL,
  `cDate` date DEFAULT NULL,
  `cDayCounter` int unsigned DEFAULT NULL,
  `cValue` double DEFAULT NULL,
  PRIMARY KEY (`cId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;