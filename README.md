# PBEMGS
 Play by Email Game Server

Licensed under the Apache 2.0 License.

Send an email to 'pbemgs@angryturtlestudios.com' with a subject line of 'info' for info!

Tools:
- MySql Workbench
- IntelliJ
- Maven
- Eclipse Temurin (JDK/JRE)
- draw.io (System Design/UML diagrams)

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
- JUnit/Mockito (unit test)

TODOs:
- Refactor standard error message handling on ninetac and ataxx
- ninetac/ataxx - add/remove logging as appropriate.
- ninetac/ataxx - convert throw to assert as appropriate.
- surge: new maps
- surge: balance testing (max edge capacity, initial gate open capacity, etc)
- controller: implement deactivate/activate
- controller/games: Implement a resign-game function/command chain (resign, confirm)