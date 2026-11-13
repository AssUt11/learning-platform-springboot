-- 项目数据库增量表。课程正文在 catalog/foundation-courses.json，由后端启动时导入。
-- 保留标记，避免课程删除后被重启恢复，不覆盖教师编辑。
create table if not exists catalog_content_seeds (
  seed_key varchar(160) primary key,
  resource_id bigint null unique,
  created_at timestamp not null default current_timestamp,
  constraint fk_catalog_seed_resource foreign key(resource_id) references resources(id) on delete set null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- MySQL 保存业务内容，Neo4j 同步失败后自动重试。
create table if not exists catalog_graph_sync_state (
  state_key varchar(40) primary key,
  payload_hash char(64) null,
  status enum('pending','synced','failed') not null default 'pending',
  course_count int not null default 0,
  lesson_count int not null default 0,
  last_error varchar(300) null,
  synced_at timestamp null,
  updated_at timestamp not null default current_timestamp on update current_timestamp
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
