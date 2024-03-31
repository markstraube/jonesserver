cd /home/mark/Software
# java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:5000 -Ddata.root=./data -Dcommand=onVista -DcreateModel=false -jar commandline-1.1.0-SNAPSHOT.jar

# this command creates the historic exchange rates json file currencyDB.json
# java -Ddata.root=./data -Dcommand=importOeNB -DcreateModel=false -jar commandline-1.1.0-SNAPSHOT.jar

# this command is the daily update for ngStocks
java -Ddata.root=./data -Dcommand=onVista -DcreateModel=false -jar commandline-1.1.0-SNAPSHOT.jar

# for testing - this updates currencyDB with the exchange rates of today
# java -Ddata.root=./data -Dcommand=eurorates -DcreateModel=false -jar commandline-1.1.0-SNAPSHOT.jar

# table with historic stocks
# java -Ddata.root=./data -Dcommand=stocks -DcreateModel=false -jar commandline-1.1.0-SNAPSHOT.jar

