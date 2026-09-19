package com.example.ratingsystem.batchimport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in migration test for a dedicated MySQL 8 test database.
 *
 * <p>This test never cleans or drops a schema. Set the three environment variables below only
 * for a disposable, dedicated database whose name ends in {@code _test}. The ordinary test suite
 * skips this class so it can never write to the developer's {@code rating_system} database.</p>
 */
@EnabledIfEnvironmentVariable(named = "RATING_MYSQL_MIGRATION_TEST_URL", matches = ".+")
class MySqlFlywayV2MigrationTests {

    @Test
    void appliesV1AndV2OnRealMySql8AndCreatesSafeUniqueIndexes() throws Exception {
        String url = requiredEnvironment("RATING_MYSQL_MIGRATION_TEST_URL");
        String username = requiredEnvironment("RATING_MYSQL_MIGRATION_TEST_USERNAME");
        String password = requiredEnvironment("RATING_MYSQL_MIGRATION_TEST_PASSWORD");
        String databaseName = databaseName(url);

        assertTrue(databaseName.endsWith("_test"),
                "迁移测试只允许连接名称以 _test 结尾的专用数据库");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertTrue(mysqlMajorVersion(connection) == 8, "该测试必须在 MySQL 8 上运行");
            assertSchemaIsEmptyOrAlreadyMigrated(connection);
        }

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(1, scalarInt(connection,
                    "select count(*) from flyway_schema_history where version = '2' and success = 1"));
            assertEquals(4, scalarInt(connection, """
                    select count(*) from information_schema.tables
                     where table_schema = database()
                       and table_name in ('answer_import_batches', 'answer_import_students',
                                          'answer_import_answers', 'answer_import_issues')
                    """));
            assertEquals(700, scalarInt(connection, """
                    select character_maximum_length from information_schema.columns
                     where table_schema = database()
                       and table_name = 'answer_import_students'
                       and column_name = 'source_path'
                    """));
            assertEquals(List.of("batch_id", "source_path"), indexColumns(connection,
                    "answer_import_students", "uk_import_student_source"));
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置");
        }
        return value;
    }

    private String databaseName(String jdbcUrl) {
        String uriValue = jdbcUrl.replaceFirst("^jdbc:", "");
        String path = URI.create(uriValue).getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("MySQL 测试 URL 必须明确指定数据库名");
        }
        return path.substring(1);
    }

    private int mysqlMajorVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select version()")) {
            result.next();
            return Integer.parseInt(result.getString(1).split("\\.")[0]);
        }
    }

    private void assertSchemaIsEmptyOrAlreadyMigrated(Connection connection) throws SQLException {
        int applicationTables = scalarInt(connection, """
                select count(*) from information_schema.tables
                 where table_schema = database()
                   and table_name <> 'flyway_schema_history'
                """);
        if (applicationTables == 0) {
            return;
        }
        int successfulV2 = scalarInt(connection, """
                select count(*) from information_schema.tables
                 where table_schema = database() and table_name = 'flyway_schema_history'
                """) == 0 ? 0 : scalarInt(connection,
                "select count(*) from flyway_schema_history where version = '2' and success = 1");
        assertEquals(1, successfulV2,
                "专用测试库不是空库，且没有已经成功的 V2；测试拒绝清理或覆盖该数据库");
    }

    private int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private List<String> indexColumns(Connection connection, String table, String index) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = """
                select column_name from information_schema.statistics
                 where table_schema = database() and table_name = '%s' and index_name = '%s'
                 order by seq_in_index
                """.formatted(table, index);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                columns.add(result.getString(1));
            }
        }
        return columns;
    }
}
