name := """urls-shortener-spray"""

version := "1.0"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
    // Config
    "com.github.kxbmap"       %% "configs"           % "0.2.2" withSources()
    // Database
    ,"org.postgresql"         %  "postgresql"        % "9.2-1002-jdbc4"
    ,"com.typesafe.slick"     %% "slick"             % "2.1.0"
    ,"org.slf4j"              %  "slf4j-nop"         % "1.6.4"
    ,"org.specs2"             %% "specs2"            % "2.4.2" % "test"
    // Slick Joda Mapper
    ,"joda-time"              % "joda-time"          % "2.4"
    ,"org.joda"               % "joda-convert"       % "1.6"
    ,"com.github.tototoshi"   %% "slick-joda-mapper" % "1.2.0"
    // slick-pg - additional data types mapping
    ,"com.github.tminglei"    %% "slick-pg"          % "0.6.5.3"
)


// for sbt-revolver plugin (https://github.com/spray/sbt-revolver)
Revolver.settings

// flyway plugin //

Seq(flywaySettings: _*)

flywayUrl  := "jdbc:postgresql://localhost:5432/shortener_db" //?createDatabaseIfNotExist=true

flywayUser := "shortener_db"

flywayPassword := "password"

flywayDriver := "org.postgresql.Driver"

// end //

scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-language:_",
    "-target:jvm-1.7",
    "-encoding", "UTF-8"
)