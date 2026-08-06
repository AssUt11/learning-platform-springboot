# Sprint 1 数据迁移与回滚说明

## 本次迁移基线

- 执行时间：2026-08-04
- 数据库备份：`backend/backups/sprint1-20260804-105408/learning_platform.sql`
- 上传文件备份：`backend/backups/sprint1-20260804-105408/uploads/`
- 完整性校验：`backend/backups/sprint1-20260804-105408/SHA256SUMS.txt`
- 迁移前：`courses=17`、`resources=19`、含文件资源 `2`、`learning_records=15`
- 迁移后：新增映射课程 `19`、章节 `19`、课时 `19`、课时资源 `11`；旧资源和旧学习记录数量不变。

备份目录已加入 `.gitignore`，只保留在当前开发机，不提交数据库和视频副本。

## 兼容策略

1. 对外课程 ID 仍使用 `resources.id`，收藏、评价、问答、笔记、审核和旧学习记录无需改 ID。
2. `courses.legacy_resource_id` 将公开课程 ID 映射到新的内部课程主体。
3. `course_legacy_migration` 保存首次迁移产生的课程、章节、课时、资源 ID，并保存迁移前的 `resources.course_id`。
4. 初始化重复执行时先检查迁移记录，不会重复创建章节、课时或课时资源。
5. 原视频文件只保存一份；`lesson_assets.file_url` 引用原 `/uploads/...` 路径。

## 优先回滚方式（不删表）

如果新版应用需要临时回退：

1. 停止新版后端，部署迁移前版本。
2. 新增表可以保留，旧版代码不会读取它们。
3. 如旧版依赖原 `resources.course_id`，先核对目标行，再执行：

```sql
select r.id, r.course_id, m.previous_course_id, m.course_id as migrated_course_id
from resources r
join course_legacy_migration m on m.resource_id = r.id
order by r.id;

update resources r
join course_legacy_migration m on m.resource_id = r.id
set r.course_id = m.previous_course_id
where r.course_id = m.course_id;
```

以上更新只恢复由本迁移绑定且当前仍指向迁移课程的行，不执行 `DROP`、`TRUNCATE`，也不删除视频。

## 完整恢复（仅在数据异常时）

完整恢复会覆盖当前数据库，只能在停止写入并确认需要放弃迁移后新增数据时执行：

1. 再次备份当前数据库与 `backend/uploads`。
2. 校验 `SHA256SUMS.txt`。
3. 将 `learning_platform.sql` 恢复到单独的临时数据库并核对数量。
4. 经人工确认后再切换数据库，不直接覆盖生产库。

禁止为回滚直接删除旧课程、旧资源、学习记录或上传文件。
