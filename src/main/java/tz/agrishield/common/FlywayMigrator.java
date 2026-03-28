package tz.agrishield.common;

import org.flywaydb.core.Flyway;

public final class FlywayMigrator {

    private FlywayMigrator() {
    }

    public static void migrate(String jdbcUrl, String username, String password) {
        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate();
    }
}
