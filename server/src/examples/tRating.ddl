-- StocksDB.tRatings definition

CREATE TABLE `tRatings` (
  `cid` int unsigned NOT NULL AUTO_INCREMENT,
  `cSymbol` varchar(16) NOT NULL,
  `cShort` varchar(32) DEFAULT NULL,
  `cMid` varchar(32) DEFAULT NULL,
  `cLong` varchar(32) DEFAULT NULL,
  `cDayCounter` int unsigned DEFAULT NULL,
  PRIMARY KEY (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;