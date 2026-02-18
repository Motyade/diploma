package ru.retailhub.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource, FlywayProperties properties) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(properties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .baselineVersion(properties.getBaselineVersion())
                .load();
    }
}
