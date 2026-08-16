# Third-Party Notices

LastMove includes third-party software listed below.

## Chesspresso

- **Component:** Chesspresso
- **Version:** 0.9.2
- **Purpose:** Chess game model and PGN, FEN, and SAN support.
- **Artifact:** `libs/repository/com/_0xab/chesspresso/0.9.2/chesspresso-0.9.2.jar`
- **Original author:** Bernhard Seybold
- **Source:** https://github.com/BernhardSeybold/Chesspresso
- **Release:** https://github.com/BernhardSeybold/Chesspresso/releases/tag/Chesspresso-0.9.2
- **License:** GNU Lesser General Public License (LGPL)

Chesspresso is bundled as a vendored Maven dependency so that LastMove can build reproducibly without relying on an external artifact repository.

The original Chesspresso source and applicable license terms remain available from its upstream project. When distributing LastMove binaries, include the applicable LGPL license text and preserve all required copyright and license notices.

## SQLite JDBC

- **Component:** SQLite JDBC
- **Version:** 3.45.3.0
- **Purpose:** JDBC driver for the local SQLite database.
- **Artifact:** `org.xerial:sqlite-jdbc:3.45.3.0`
- **Source:** https://github.com/xerial/sqlite-jdbc
- **License:** Apache License 2.0

SQLite JDBC is retrieved from Maven Central. When distributing LastMove binaries, include the applicable Apache 2.0 license text and preserve all required copyright and license notices.

## Flyway

- **Component:** Flyway
- **Version:** Managed by Spring Boot 3.3.2
- **Purpose:** Database schema migrations for local persistence.
- **Artifact:** `org.flywaydb:flyway-core`
- **Source:** https://github.com/flyway/flyway
- **License:** Apache License 2.0

Flyway is retrieved from Maven Central. When distributing LastMove binaries, include the applicable Apache 2.0 license text and preserve all required copyright and license notices.