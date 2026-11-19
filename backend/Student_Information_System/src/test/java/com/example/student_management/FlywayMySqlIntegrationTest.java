package com.example.student_management;

import com.example.student_management.service.notification.NotificationService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SIS_MYSQL_INTEGRATION", matches = "true")
class FlywayMySqlIntegrationTest {
    private static final String DATABASE_URL_PATTERN =
            "^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):\\d+/[A-Za-z0-9_]+(?:\\?.*)?$";
    private static final String JWT_SECRET = "ci-only-integration-secret-with-more-than-32-bytes";

    @Test
    void freshDatabaseMigratesAndStartsBackend() throws Exception {
        TestDatabase database = createTestDatabase("fresh");
        try {
            try (ConfigurableApplicationContext ignored = startBackend(database.url())) {
                assertEquals("17", currentFlywayVersion(database.url()));
                assertEquals(0, countRows(database.url(), "users"));
            }
        } finally {
            database.drop();
        }
    }

    @Test
    void baselineSixLegacyDatabaseMigratesWithoutLosingRows() throws Exception {
        TestDatabase database = createTestDatabase("legacy");
        try {
            Flyway.configure()
                    .dataSource(database.url(), database.username(), database.password())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("6"))
                    .load()
                    .migrate();

            try (Connection connection = database.connect();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users
                            (username, email, full_name, password_hash, role, status, created_at, updated_at)
                        VALUES
                            ('legacy-fixture', 'legacy-fixture@example.invalid', 'Legacy Fixture',
                             'fixture-hash', 'STUDENT', 'ACTIVE', NOW(6), NOW(6))
                        """);
                statement.execute("DROP TABLE flyway_schema_history");
            }

            try (ConfigurableApplicationContext ignored = startBackend(database.url())) {
                assertEquals("17", currentFlywayVersion(database.url()));
                assertEquals(1, countRows(database.url(),
                        "users WHERE username = 'legacy-fixture'"));
                assertEquals("int", recurringScheduleDayColumnType(database.url()));
            }
        } finally {
            database.drop();
        }
    }

    @Test
    void dismissingBroadcastNotificationOnlyHidesItForCurrentUser() throws Exception {
        TestDatabase database = createTestDatabase("dismissal");
        long notificationId;
        try {
            Flyway.configure()
                    .dataSource(database.url(), database.username(), database.password())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = database.connect();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users
                            (username, email, full_name, password_hash, role, status, created_at, updated_at)
                        VALUES
                            ('dismissal-user', 'dismissal-user@example.invalid', 'Dismissal User',
                             'fixture-hash', 'STUDENT', 'ACTIVE', NOW(6), NOW(6)),
                            ('other-user', 'other-user@example.invalid', 'Other User',
                             'fixture-hash', 'STUDENT', 'ACTIVE', NOW(6), NOW(6))
                        """);
                statement.executeUpdate("""
                        INSERT INTO notifications (content, created_at, is_read, target_role, title, type)
                        VALUES ('Shared notice body', NOW(6), b'0', 'STUDENT', 'Shared notice', 'ANNOUNCEMENT')
                        """);
                try (ResultSet result = statement.executeQuery(
                        "SELECT id FROM notifications WHERE title = 'Shared notice'")) {
                    if (!result.next()) {
                        throw new AssertionError("Shared notification fixture was not inserted.");
                    }
                    notificationId = result.getLong(1);
                }
            }

            try (ConfigurableApplicationContext context = startBackend(database.url())) {
                NotificationService notificationService = context.getBean(NotificationService.class);
                assertEquals(1, notificationService.getMyNotifications("dismissal-user").size());
                assertEquals(1, notificationService.getMyNotifications("other-user").size());

                notificationService.markAsRead(notificationId, "dismissal-user");
                notificationService.deleteNotification(notificationId, "dismissal-user");

                assertEquals(0, notificationService.getMyNotifications("dismissal-user").size());
                assertEquals(1, notificationService.getMyNotifications("other-user").size());
            }
        } finally {
            database.drop();
        }
    }

    private static ConfigurableApplicationContext startBackend(String databaseUrl) {
        Map<String, Object> properties = Map.of(
                "spring.datasource.url", databaseUrl,
                "spring.datasource.username", requiredEnvironment("SIS_MYSQL_USERNAME"),
                "spring.datasource.password", requiredEnvironment("SIS_MYSQL_PASSWORD"),
                "spring.main.web-application-type", "none",
                "spring.sql.init.mode", "never",
                "app.jwt.secret", JWT_SECRET);

        return new SpringApplicationBuilder(StudentInformationSystemApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE)
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("mysqlIntegrationTest", properties)))
                .run();
    }

    private static TestDatabase createTestDatabase(String scenario) throws SQLException {
        String adminUrl = requiredEnvironment("SIS_MYSQL_ADMIN_URL");
        if (!adminUrl.matches(DATABASE_URL_PATTERN)) {
            throw new IllegalStateException(
                    "SIS_MYSQL_ADMIN_URL must target localhost or 127.0.0.1; refusing non-local database access.");
        }

        String databaseName = "sis_ci_" + scenario + "_" +
                UUID.randomUUID().toString().replace("-", "");
        String username = requiredEnvironment("SIS_MYSQL_USERNAME");
        String password = requiredEnvironment("SIS_MYSQL_PASSWORD");

        try (Connection connection = DriverManager.getConnection(
                adminUrl, requiredEnvironment("SIS_MYSQL_ADMIN_USERNAME"),
                requiredEnvironment("SIS_MYSQL_ADMIN_PASSWORD"));
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName +
                    "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        String databaseUrl = adminUrl.replaceFirst(
                "/[A-Za-z0-9_]+(?:\\?.*)?$", "/" + databaseName);
        return new TestDatabase(databaseUrl, databaseName, username, password, adminUrl);
    }

    private static String currentFlywayVersion(String databaseUrl) throws SQLException {
        try (Connection connection = connect(databaseUrl);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT version
                     FROM flyway_schema_history
                     WHERE success = 1
                     ORDER BY installed_rank DESC
                     LIMIT 1
                     """)) {
            if (!result.next()) {
                throw new AssertionError("Flyway did not record a successful migration.");
            }
            return result.getString(1);
        }
    }

    private static int countRows(String databaseUrl, String tableAndCondition) throws SQLException {
        if (!tableAndCondition.matches("[A-Za-z_]+(?: WHERE [A-Za-z_]+ = '[A-Za-z0-9_-]+')?")) {
            throw new IllegalArgumentException("Invalid test table selector.");
        }
        try (Connection connection = connect(databaseUrl);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableAndCondition)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String recurringScheduleDayColumnType(String databaseUrl) throws SQLException {
        try (Connection connection = connect(databaseUrl);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT DATA_TYPE
                     FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name = 'course_section_recurring_schedules'
                       AND column_name = 'day_of_week'
                     """)) {
            if (!result.next()) {
                throw new AssertionError("Recurring schedule day_of_week column is missing.");
            }
            return result.getString(1);
        }
    }

    private static Connection connect(String databaseUrl) throws SQLException {
        return DriverManager.getConnection(databaseUrl,
                requiredEnvironment("SIS_MYSQL_USERNAME"),
                requiredEnvironment("SIS_MYSQL_PASSWORD"));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for MySQL integration tests.");
        }
        return value;
    }

    private record TestDatabase(
            String url,
            String name,
            String username,
            String password,
            String adminUrl) {
        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        void drop() throws SQLException {
            try (Connection connection = DriverManager.getConnection(adminUrl,
                    requiredEnvironment("SIS_MYSQL_ADMIN_USERNAME"),
                    requiredEnvironment("SIS_MYSQL_ADMIN_PASSWORD"));
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + name + "`");
            }
        }
    }
}
