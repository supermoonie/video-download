@echo off
@call mvn install:install-file -Dfile=AutoChrome-1.0-SNAPSHOT.jar -DgroupId=com.github.supermoonie -DartifactId=AutoChrome -Dversion=1.0-SNAPSHOT -Dpackaging=jar
@call mvn install:install-file -Dfile=http4J-1.0-SNAPSHOT-jar-with-dependencies.jar -DgroupId=com.github.supermoonie -DartifactId=http4J -Dversion=1.0-SNAPSHOT -Dpackaging=jar
pause