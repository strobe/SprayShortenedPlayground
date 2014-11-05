name := """urls-shortener-spray"""

version := "1.0"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
    // Database
     "org.postgresql" % "postgresql" % "9.2-1002-jdbc4"
    ,"org.specs2" %% "specs2" % "2.4.2" % "test"
    )


// for sbt-revolver plugin (https://github.com/spray/sbt-revolver)
Revolver.settings

// flyway plugin //

Seq(flywaySettings: _*)

flywayUrl  := "jdbc:postgresql://localhost:5432/shortener_db" //?createDatabaseIfNotExist=true

flywayUser := "shortener_db"

flywayPassword := "carryx"

flywayDriver := "org.postgresql.Driver"

// end //