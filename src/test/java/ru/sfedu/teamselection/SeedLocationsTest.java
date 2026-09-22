package ru.sfedu.teamselection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prod database is reset before a selection opens (vaimon/CapstoneProjectSystem#369); the demo
 * seed must not come back with it. Each case migrates its own empty database with the locations the
 * real config files declare, so dropping the prod override turns this red.
 */
class SeedLocationsTest {

    private static final String[] DEMO_TABLES = {"users", "students", "teams", "applications", "tracks"};

    @BeforeAll
    static void startContainer() {
        BasicTestContainerTest.POSTGRES.start();
    }

    @Test
    void prodProfileMigratesReferenceDataOnly() throws SQLException {
        String url = migrateFreshDatabase("seed_prod", locations("application-prod.yml"));

        for (String table : DEMO_TABLES) {
            assertThat(count(url, "SELECT count(*) FROM " + table)).as(table).isZero();
        }
        assertThat(roles(url)).containsExactlyInAnyOrder("STUDENT", "ADMIN");
        assertThat(count(url, "SELECT count(*) FROM technologies")).isPositive();
        assertThat(count(url, "SELECT count(*) FROM project_types")).isPositive();
    }

    @Test
    void defaultLocationsStillLoadTheSeed() throws SQLException {
        String url = migrateFreshDatabase("seed_default", locations("application.yml"));

        assertThat(count(url, "SELECT count(*) FROM teams")).isPositive();
        assertThat(count(url, "SELECT count(*) FROM applications")).isPositive();
    }

    private static String[] locations(String configFile) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(configFile));
        Properties properties = yaml.getObject();
        String locations = properties.getProperty("spring.flyway.locations");
        assertThat(locations).as("spring.flyway.locations in " + configFile).isNotBlank();
        return locations.split("\\s*,\\s*");
    }

    private static String migrateFreshDatabase(String name, String[] locations) throws SQLException {
        try (Connection admin = connect(BasicTestContainerTest.POSTGRES.getJdbcUrl());
             Statement statement = admin.createStatement()) {
            // the container is reused between runs, so a database from a previous run may still be there
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        }
        String url = BasicTestContainerTest.POSTGRES.getJdbcUrl()
                .replace("/" + BasicTestContainerTest.POSTGRES.getDatabaseName(), "/" + name);
        Flyway.configure()
                .dataSource(url, BasicTestContainerTest.POSTGRES.getUsername(),
                        BasicTestContainerTest.POSTGRES.getPassword())
                .locations(locations)
                .load()
                .migrate();
        return url;
    }

    private static long count(String url, String sql) throws SQLException {
        try (Connection connection = connect(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static List<String> roles(String url) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = connect(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT name FROM roles")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, BasicTestContainerTest.POSTGRES.getUsername(),
                BasicTestContainerTest.POSTGRES.getPassword());
    }
}
