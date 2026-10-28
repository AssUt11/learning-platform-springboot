package com.learning.platform.ai.chat;

import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

/** Standalone chat has no course/lesson foreign keys and does not alter existing tutor data. */
@Component
@DependsOn("schemaInitializer")
public class AgentChatSchemaInitializer {
    private final JdbcTemplate jdbc;
    public AgentChatSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void initialize() {
        jdbc.execute("""
                create table if not exists ai_agent_conversation (
                  id bigint primary key auto_increment,
                  user_id bigint not null,
                  title varchar(160) not null,
                  created_at timestamp not null default current_timestamp,
                  updated_at timestamp not null default current_timestamp on update current_timestamp,
                  index idx_agent_chat_user(user_id,updated_at),
                  constraint fk_agent_chat_user foreign key(user_id) references users(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbc.execute("""
                create table if not exists ai_agent_message (
                  id bigint primary key auto_increment,
                  conversation_id bigint not null,
                  role enum('user','assistant') not null,
                  content longtext not null,
                  status enum('queued','generating','completed','failed','cancelled') not null,
                  request_id varchar(64) not null,
                  provider_code varchar(40) null,
                  model_code varchar(120) null,
                  prompt_tokens int null,
                  output_tokens int null,
                  duration_ms bigint null,
                  created_at timestamp not null default current_timestamp,
                  unique key uk_agent_chat_request(request_id,role),
                  index idx_agent_chat_messages(conversation_id,id),
                  constraint fk_agent_chat_conversation foreign key(conversation_id)
                    references ai_agent_conversation(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }
}
