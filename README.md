Url shortened server sample.
====================

## Db initialization

1. Open /src/main/resources/application.config and set your database credentials
   in following format:

         urls_service {
           secret = "your_secret_code"
           db_driver = "org.postgresql.Driver"
           db_url = "jdbc:postgresql://localhost:5432/database_name"
           db_user = "database_username"
           db_password = "databse_password"
         }

2. Open build.sbt and change following setting to your database credentials:

         flywayUrl  := "jdbc:postgresql://localhost:5432/shortener_db"

         flywayUser := "database_username"

         flywayPassword := "databse_password"

         flywayDriver := "org.postgresql.Driver"

3. Run 'sbt' or 'activator' tool at root of project and call following commands
   in your terminal:

         1. > update
         2. > flywayInit
         3. > flywayMigrate


## Tests

Run 'sbt test' or 'activator test' command in your terminal

## Run

Run 'sbt run' or 'activator run' command in your terminal

## HTTP API Endpoints

|Path                  | Method | Description                                         | Parameters
|:---------------------|:-------| :---------------------------------------------------|:-----------
| "/"                  | GET    | index html page                                     |
| "/ping"              | GET    | simple PONG response                                |
| "/token"             | GET    | returned token Json for new or exist user           | user_id: Int, secret: String
| "/link"              | POST   | returned Link Json with url and code                | token: String, url: String, code: Option[String], folder_id: Option[String] ! @note: **Link should be a valid http:// or https:// url**
| "/link"              | GET    | returned list of links as Json                      | token: String,  offset: Option[Int], limit: Option[Int]
| "/link/:code"        | POST   | redirected to GET /link/:code                       | referer: String, remote_ip: String
| "/link/:code"        | GET    | returned Json with link adn related clicks, folders | token: String
| "/link/:code/clicks" | GET    | returned Json with list of clicks                   | token: String,  offset: Option[Int], limit: Option[Int]
| "/folder"            | GET    | returned Json with list of folders                  | token: String
| "/folder/:id"        | GET    | returned Json with list of links ib folder          | token: String,  offset: Option[Int], limit: Option[Int]

All POST request parametersh should be send as json with Content-Type: application/json headers.
For example body of POST request to /link:

{ "token":"be79ded6", "url":"http://www.google.com"}


