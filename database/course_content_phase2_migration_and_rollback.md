# 课程内容完整性整改记录与回滚说明

执行日期：2026-08-04
执行前快照：`database/backups/course_content_before_20260804.sql`

## 执行前核验

- 审核通过且 `status=1`：19 门。
- 统一校验识别出内容不完整的视频课程：8 门。
- 目标课程 ID：`1, 3, 5, 9, 11, 15, 16, 18`。
- 每门课程均保留原课程、章节、课时、旧资源、互动数据和学习记录。

## 已执行变化

通过 `CourseContentValidationService.unpublishIncompleteCourses` 在事务内重新计算问题课程，确认数量为 8 后，仅把上述课程的 `resources.status` 从 `1` 调整为 `0`。每门课程均写入 `admin_audit_logs`，操作类型为 `COURSE_CONTENT_UNPUBLISHED`。

未修改 `review_status`，因此管理端仍能找到这些课程并查看完整性原因。

## 回滚 SQL

回滚会重新公开不可播放课程，只应在确认需要恢复旧行为时执行。执行前先核对查询结果必须恰好为 8 行。

```sql
start transaction;

select id,title,status,review_status
from resources
where id in (1,3,5,9,11,15,16,18)
for update;

update resources
set status=1
where id in (1,3,5,9,11,15,16,18)
  and status=0
  and review_status='approved';

select row_count() restored_rows;

-- restored_rows 必须为 8；不是 8 时执行 rollback，而不是 commit。
commit;
```

完整恢复也可以在空库中导入执行前快照。快照包含：`resources`、`courses`、`course_chapters`、`course_lessons`、`lesson_assets`、学习记录、收藏、评价、问答和笔记。

## 双视频示例课程

- 稳定种子键：`demo-course-two-chapters-two-videos-v1`
- `resourceId=21`，`teacherId=14`
- 章节：`20`、`21`
- 独立视频课时：`20`、`21`
- 无视频草稿课时：`22`
- 视频资源：`12`、`13`
- 示例初始化通过 `course_demo_seeds.seed_key` 保证幂等，不复制本地视频文件。
