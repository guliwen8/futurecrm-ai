package com.futurecrm.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SQLiteConfig {
    @Bean
    public Integer enableSqliteForeignKeys(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("PRAGMA foreign_keys = ON");
        return 1;
    }
}
