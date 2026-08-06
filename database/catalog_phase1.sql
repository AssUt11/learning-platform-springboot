use learning_platform;

create table if not exists categories (
  id bigint primary key auto_increment,
  name varchar(100) not null unique,
  description varchar(500),
  icon varchar(100),
  sort_order int not null default 0,
  status tinyint not null default 1,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_categories_status_sort(status, sort_order)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists directions (
  id bigint primary key auto_increment,
  category_id bigint not null,
  name varchar(100) not null,
  description varchar(500),
  sort_order int not null default 0,
  status tinyint not null default 1,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_direction_category_name(category_id, name),
  index idx_directions_category_status(category_id, status, sort_order),
  constraint fk_directions_category foreign key(category_id) references categories(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into categories(name, description, sort_order) values
('软件工程', '面向软件开发、测试、协作和交付的综合学习大类', 1),
('人工智能', '从机器学习、深度学习到自然语言处理和视觉任务', 2),
('大数据', '覆盖数据采集、存储、计算、建模和可视化', 3),
('云计算', '面向 Linux、容器、集群编排与云原生服务治理', 4),
('网络安全', '覆盖 Web 安全、密码学、协议安全和安全运维', 5),
('移动与物联网', '面向移动应用、设备通信、边缘计算和嵌入式开发', 6)
on duplicate key update description=values(description), sort_order=values(sort_order), status=1;

insert into directions(category_id, name, sort_order)
select c.id, x.name, x.sort_order
from categories c
join (
  select '软件工程' category_name, 'Java' name, 1 sort_order union all
  select '软件工程', 'Python', 2 union all select '软件工程', 'C#', 3 union all
  select '软件工程', '前端开发', 4 union all select '软件工程', '数据库', 5 union all select '软件工程', '软件测试', 6 union all
  select '人工智能', '机器学习', 1 union all select '人工智能', '深度学习', 2 union all
  select '人工智能', '自然语言处理', 3 union all select '人工智能', '计算机视觉', 4 union all
  select '大数据', 'Hadoop', 1 union all select '大数据', 'Spark', 2 union all
  select '大数据', '数据仓库', 3 union all select '大数据', '数据可视化', 4 union all
  select '云计算', 'Linux', 1 union all select '云计算', 'Docker', 2 union all
  select '云计算', 'Kubernetes', 3 union all select '云计算', '微服务', 4 union all
  select '网络安全', 'Web安全', 1 union all select '网络安全', '密码学', 2 union all
  select '网络安全', '网络协议安全', 3 union all select '网络安全', '安全运维', 4 union all
  select '移动与物联网', '移动开发', 1 union all select '移动与物联网', '物联网', 2 union all
  select '移动与物联网', '嵌入式开发', 3
) x on x.category_name=c.name
on duplicate key update sort_order=values(sort_order), status=1;

-- 现有数据库由 SchemaInitializer 自动补齐以下课程资源字段：
-- category_id、direction_id、estimated_minutes、learner_count、status。
