package com.learning.platform.config;

import javax.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds profile metadata without replacing or resetting existing accounts. */
@Component
@DependsOn("schemaInitializer")
public class ProfileAvatarSchemaInitializer {

    private final JdbcTemplate jdbc;

    public ProfileAvatarSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        Integer count = jdbc.queryForObject(
            "select count(*) from information_schema.columns " +
            "where table_schema=database() and table_name='users' and column_name='avatar_url'",
            Integer.class
        );
        if (count == null || count == 0) {
            jdbc.execute("alter table users add column avatar_url varchar(255) null");
        }
    }
}
