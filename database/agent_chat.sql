-- Agent 对话增量建表脚本（MySQL 8）
-- 在项目已配置的数据库中执行；本机数据库为 learning_platform。
-- 依赖已存在的 users 表，与 AgentChatSchemaInitializer 的结构保持一致。
-- 可以重复执行，不清空已有会话，不修改其他业务表。

-- 一行对应一段会话，user_id 关联当前登录的学生或教师。
create table if not exists ai_agent_conversation (
  id bigint primary key auto_increment,
  user_id bigint not null,
  title varchar(160) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_agent_chat_user(user_id,updated_at),
  constraint fk_agent_chat_user foreign key(user_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 一行对应一条问题或回答，conversation_id 关联所属会话。
-- role: user=用户，assistant=模型；status 记录排队、生成、完成、失败或取消。
-- 同一轮问题与回答共享 request_id；模型与用量字段保存在回答行。
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
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
