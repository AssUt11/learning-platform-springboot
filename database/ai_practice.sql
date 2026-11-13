-- 已有数据库的增量脚本。先选择目标数据库；不包含 DROP/TRUNCATE 或初始化业务数据。
-- 后端 PracticeSchemaInitializer 在启动时执行相同建表语句。
create table if not exists ai_practice_sessions (
  id bigint primary key auto_increment,
  quiz_id varchar(36) not null unique,
  user_id bigint not null,
  course_id bigint not null,
  lesson_id bigint not null,
  difficulty varchar(20) not null,
  questions_json mediumtext not null,
  answers_json text null,
  score int null,
  provider_code varchar(40) not null,
  model_code varchar(160) not null,
  created_at timestamp(3) not null default current_timestamp(3),
  submitted_at timestamp(3) null,
  index idx_practice_user_lesson(user_id,lesson_id,id),
  constraint fk_practice_user foreign key(user_id) references users(id) on delete cascade,
  constraint fk_practice_course foreign key(course_id) references resources(id) on delete restrict,
  constraint fk_practice_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
