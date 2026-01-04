-- StocksDB.tOnVista definition

CREATE TABLE `tOnVista` (
  `cIsin` varchar(100) NOT NULL,
  `cName` varchar(100) DEFAULT NULL,
  `cSymbol` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `cBranch` varchar(100) DEFAULT NULL,
  `cSector` varchar(100) DEFAULT NULL,
  `cCountryCode` varchar(100) DEFAULT NULL,
  `cLast` double DEFAULT NULL,
  `cExchange` varchar(100) DEFAULT NULL,
  `cDateLong` decimal(14,0) DEFAULT '0',
  `cCurrency` varchar(100) DEFAULT NULL,
  `cPerformance` double DEFAULT NULL,
  `cPerf1Year` double DEFAULT NULL,
  `cPerf6Months` double DEFAULT NULL,
  `cPerf4Weeks` double DEFAULT NULL,
  `cDividendYield` double DEFAULT NULL,
  `cDividend` double DEFAULT NULL,
  `cMarketCapitalization` double DEFAULT NULL,
  `cRiskRating` decimal(14,0) DEFAULT '0',
  `cEmployees` decimal(14,0) DEFAULT '0',
  `cTurnover` double DEFAULT NULL,
  `cUpdated` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`cIsin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;