# PBEMGS
 Play by Email Game Server

Licensed under the Apache 2.0 License.

Send an email to 'pbemgs@angryturtlestudios.com' with a subject line of 'info' for info!

Tools:
- MySql Workbench
- IntelliJ
- Maven
- Eclipse Temurin (JDK/JRE)
- draw.io (AWS Block Diagram/UML diagrams)

AWS Services used:
- RDS (relational DB) - Aurora
- SES (Simple Email Service)
- S3 (Storage - stores email body)
- Lambda (serverless processing)
- Route 53 (DNS, domain hosting) - angryturtlestudios.com
- Cloudwatch (Logs)
- EventBridge (Cron Job triggers)

Libraries:
- AWS SDK, Utils, etc
- JOOQ (DB interface)
- MySql connector
- Sun Mail (SMTP, MIME)
- Google Guava
- JUnit/Mockito (unit test)

TODOs:
- all games - add/remove logging as appropriate.
- all games - convert throw to assert as appropriate.
- surge: new maps
- surge: balance testing (max edge capacity, initial gate open capacity, etc)
- ataxx: standard board creation options
- triad: Same/Plus?
- controller: implement deactivate/activate
- controller/games: Implement a resign-game function/command chain (resign, confirm)
- controller - add stats command (my_results <gamename>, game_results <gamename>, leaderboards)