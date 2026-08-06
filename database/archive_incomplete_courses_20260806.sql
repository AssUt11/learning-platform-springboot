-- 最终版内容治理：将已隐藏且确认不完整的八门历史课程正式归档。
-- 归档不会删除课程、章节、课时、学习记录或上传文件。

update resources
set lifecycle_status = 'archived',
    archived_at = coalesce(archived_at, current_timestamp),
    archive_reason = '历史课程内容或教师归属不完整，最终版整改时保留数据并正式归档',
    status = 0
where id in (1, 3, 5, 9, 11, 15, 16, 18)
  and review_status = 'approved'
  and status = 0;

insert into admin_audit_logs(admin_id, action_type, target_type, target_id, detail)
select admin_user.id,
       'RESOURCE_ARCHIVED',
       'resource',
       resource.id,
       concat('最终版整改归档：', resource.archive_reason)
from resources resource
join users admin_user on admin_user.role = 'admin'
where resource.id in (1, 3, 5, 9, 11, 15, 16, 18)
  and resource.lifecycle_status = 'archived'
  and not exists (
      select 1
      from admin_audit_logs audit
      where audit.action_type = 'RESOURCE_ARCHIVED'
        and audit.target_type = 'resource'
        and audit.target_id = resource.id
        and audit.detail like '最终版整改归档：%'
  );
