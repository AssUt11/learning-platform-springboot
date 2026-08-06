-- 课程详情页社区能力与三端审核治理
alter table users modify column role enum('admin','teacher','student') not null;

alter table resources add column if not exists review_status enum('pending','approved','rejected') not null default 'approved' after status;
alter table resources add column if not exists review_feedback varchar(500) null after review_status;
alter table resources add column if not exists reviewed_by bigint null after review_feedback;
alter table resources add column if not exists reviewed_at timestamp null after reviewed_by;
create table if not exists course_questions (
  id bigint primary key auto_increment,
  resource_id bigint not null,
  student_id bigint not null,
  content varchar(500) not null,
  answer_count int not null default 0,
  created_at timestamp not null default current_timestamp,
  index idx_course_questions_resource_created(resource_id, created_at),
  constraint fk_questions_resource foreign key(resource_id) references resources(id) on delete cascade,
  constraint fk_questions_student foreign key(student_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists course_notes (
  id bigint primary key auto_increment,
  resource_id bigint not null,
  student_id bigint not null,
  lesson_title varchar(180) not null default '课程总览',
  content text not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_course_notes_student_resource(student_id, resource_id, updated_at),
  constraint fk_notes_resource foreign key(resource_id) references resources(id) on delete cascade,
  constraint fk_notes_student foreign key(student_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists course_reviews (
  id bigint primary key auto_increment,
  resource_id bigint not null,
  student_id bigint not null,
  rating tinyint not null,
  content varchar(1000) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_course_review_student(resource_id, student_id),
  index idx_course_reviews_resource_updated(resource_id, updated_at),
  constraint fk_reviews_resource foreign key(resource_id) references resources(id) on delete cascade,
  constraint fk_reviews_student foreign key(student_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists course_feedback_replies (
  id bigint primary key auto_increment,
  target_type enum('question','review') not null,
  target_id bigint not null,
  teacher_id bigint not null,
  content varchar(1000) not null,
  created_at timestamp not null default current_timestamp,
  index idx_feedback_replies_target(target_type, target_id, created_at),
  constraint fk_feedback_reply_teacher foreign key(teacher_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists admin_audit_logs (
  id bigint primary key auto_increment,
  admin_id bigint not null,
  action_type varchar(80) not null,
  target_type varchar(80) not null,
  target_id bigint null,
  detail varchar(500) null,
  created_at timestamp not null default current_timestamp,
  index idx_admin_audit_created(created_at),
  constraint fk_admin_audit_user foreign key(admin_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
