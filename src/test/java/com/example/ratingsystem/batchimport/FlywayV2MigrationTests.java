package com.example.ratingsystem.batchimport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_v2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class FlywayV2MigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesAllMigrationsAndValidatesJpaMappings() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_name in ('answer_import_batches', 'answer_import_students',
                                      'answer_import_answers', 'answer_import_issues')
                """, Integer.class);
        assertEquals(4, tableCount);

        Integer sourcePathLength = jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                 where table_name = 'answer_import_students' and column_name = 'source_path'
                """, Integer.class);
        assertEquals(700, sourcePathLength);

        Integer taskTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_name in ('grading_tasks', 'grading_task_items')
                """, Integer.class);
        assertEquals(2, taskTableCount);

        Integer reviewedColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = 'exams' and column_name = 'standards_reviewed'
                """, Integer.class);
        assertEquals(1, reviewedColumnCount);

        Integer appliedMigrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                 where version in ('1', '2', '3') and success = true
                """, Integer.class);
        assertEquals(3, appliedMigrationCount);

        assertEquals(0, flyway.migrate().migrationsExecuted,
                "重复启动时不应重复执行已经成功的迁移");
        assertEquals(3, jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                 where version in ('1', '2', '3') and success = true
                """, Integer.class));
    }
}
