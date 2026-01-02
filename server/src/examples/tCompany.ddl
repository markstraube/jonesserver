-- StocksDB.tCompany definition

CREATE TABLE `tCompany` (
  `cId` varchar(36) NOT NULL,
  `cSymbol` varchar(20) NOT NULL,
  `cIsin` varchar(20) NOT NULL,
  `cShortName` varchar(150) NOT NULL,
  `cLongName` varchar(255) NOT NULL,
  `cCurrency` char(3) NOT NULL,
  `cInstrumentType` varchar(30) NOT NULL,
  `cFirstTradeDate` date DEFAULT NULL,
  `cExchangeName` varchar(255) NOT NULL,
  `cFullExchangeName` varchar(255) NOT NULL,
  `cExchangeTimezoneName` varchar(255) NOT NULL,
  `cTimezone` varchar(150) NOT NULL,
  `cHasPrePostMarketData` tinyint(1) DEFAULT '0',
  `cPriceHint` int DEFAULT NULL,
  `cDataGranularity` varchar(10) DEFAULT NULL,
  `cCreated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `cUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `tCompany_cId_IDX` (`cId`) USING BTREE,
  UNIQUE KEY `tCompany_cSymbol_IDX` (`cSymbol`) USING BTREE,
  UNIQUE KEY `tCompany_cIsin_IDX` (`cIsin`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;