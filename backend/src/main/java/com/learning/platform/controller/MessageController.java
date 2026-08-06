package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.util.ApiResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public MessageController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/center")
    public Map<String, Object> center(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        Object userId = user.get("id");
        Map<String, Object> result = ApiResponses.ok();
        result.put("contacts", contacts(userId));
        result.put("messages", conversationMessages(userId));
        result.put("courseReplies", courseReplies(userId));
        result.put("teachingFeedback", "teacher".equals(user.get("role"))
                ? teachingFeedback(userId) : new ArrayList<Map<String, Object>>());
        Number unreadCount = jdbcTemplate.queryForObject(
                "select count(*) from direct_messages where recipient_id=? and is_read=0",
                Number.class, userId);
        result.put("unreadCount", unreadCount == null ? 0 : unreadCount.intValue());
        return result;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> send(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        long recipientId = positiveLong(body == null ? null : body.get("recipientId"), "recipientId");
        long senderId = ((Number) user.get("id")).longValue();
        if (senderId == recipientId) {
            throw new BusinessException(400, "不能给自己发送消息");
        }
        String content = requiredText(body == null ? null : body.get("content"), 2000);
        Integer recipientCount = jdbcTemplate.queryForObject(
                "select count(*) from users where id=? and role in('student','teacher')",
                Integer.class, recipientId);
        if (recipientCount == null || recipientCount == 0) {
            throw new BusinessException(404, "联系人不存在或暂不支持对话");
        }
        jdbcTemplate.update(
                "insert into direct_messages(sender_id,recipient_id,content) values(?,?,?)",
                senderId, recipientId, content);
        Map<String, Object> result = ApiResponses.ok();
        result.put("sent", true);
        return result;
    }

    @PostMapping("/read")
    public Map<String, Object> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        long senderId = positiveLong(body == null ? null : body.get("senderId"), "senderId");
        int updated = jdbcTemplate.update(
                "update direct_messages set is_read=1 where recipient_id=? and sender_id=? and is_read=0",
                user.get("id"), senderId);
        Map<String, Object> result = ApiResponses.ok();
        result.put("updated", updated);
        return result;
    }

    private List<Map<String, Object>> contacts(Object userId) {
        return jdbcTemplate.queryForList(
                "select u.id,u.name,u.email,u.role,"
                        + "(select dm.content from direct_messages dm "
                        + "where (dm.sender_id=u.id and dm.recipient_id=?) or (dm.sender_id=? and dm.recipient_id=u.id) "
                        + "order by dm.created_at desc,dm.id desc limit 1) lastMessage,"
                        + "(select dm.created_at from direct_messages dm "
                        + "where (dm.sender_id=u.id and dm.recipient_id=?) or (dm.sender_id=? and dm.recipient_id=u.id) "
                        + "order by dm.created_at desc,dm.id desc limit 1) lastMessageAt,"
                        + "(select count(*) from direct_messages dm where dm.sender_id=u.id and dm.recipient_id=? and dm.is_read=0) unreadCount "
                        + "from users u where u.id<>? and u.role in('student','teacher') "
                        + "order by (lastMessageAt is null),lastMessageAt desc,u.name limit 100",
                userId, userId, userId, userId, userId, userId);
    }

    private List<Map<String, Object>> conversationMessages(Object userId) {
        return jdbcTemplate.queryForList(
                "select recent.id,recent.senderId,recent.recipientId,recent.content,recent.isRead,recent.createdAt,"
                        + "sender.name senderName,sender.role senderRole,recipient.name recipientName,recipient.role recipientRole "
                        + "from (select dm.id,dm.sender_id senderId,dm.recipient_id recipientId,dm.content,"
                        + "dm.is_read isRead,dm.created_at createdAt from direct_messages dm "
                        + "where dm.sender_id=? or dm.recipient_id=? order by dm.created_at desc,dm.id desc limit 300) recent "
                        + "join users sender on sender.id=recent.senderId "
                        + "join users recipient on recipient.id=recent.recipientId "
                        + "order by recent.createdAt asc,recent.id asc",
                userId, userId);
    }

    private List<Map<String, Object>> courseReplies(Object userId) {
        return jdbcTemplate.queryForList(
                "select fr.id,fr.target_type targetType,fr.content,fr.created_at createdAt,"
                        + "case when fr.target_type='question' then q.content else rv.content end sourceContent,"
                        + "r.id courseId,r.title courseTitle,teacher.name actorName "
                        + "from course_feedback_replies fr "
                        + "left join course_questions q on fr.target_type='question' and q.id=fr.target_id "
                        + "left join course_reviews rv on fr.target_type='review' and rv.id=fr.target_id "
                        + "join resources r on r.id=coalesce(q.resource_id,rv.resource_id) "
                        + "join users teacher on teacher.id=fr.teacher_id "
                        + "where (fr.target_type='question' and q.student_id=?) "
                        + "or (fr.target_type='review' and rv.student_id=?) "
                        + "order by fr.created_at desc,fr.id desc limit 80",
                userId, userId);
    }

    private List<Map<String, Object>> teachingFeedback(Object teacherId) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.addAll(jdbcTemplate.queryForList(
                "select q.id,'question' itemType,q.content,q.created_at createdAt,"
                        + "r.id courseId,r.title courseTitle,student.id actorId,student.name actorName "
                        + "from course_questions q join resources r on r.id=q.resource_id "
                        + "join users student on student.id=q.student_id "
                        + "where r.teacher_id=? order by q.created_at desc limit 60",
                teacherId));
        result.addAll(jdbcTemplate.queryForList(
                "select rv.id,'review' itemType,rv.content,rv.updated_at createdAt,"
                        + "r.id courseId,r.title courseTitle,student.id actorId,student.name actorName "
                        + "from course_reviews rv join resources r on r.id=rv.resource_id "
                        + "join users student on student.id=rv.student_id "
                        + "where r.teacher_id=? order by rv.updated_at desc limit 60",
                teacherId));
        return result;
    }

    private long positiveLong(Object value, String fieldName) {
        try {
            long result = Long.parseLong(String.valueOf(value));
            if (result <= 0) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, fieldName + " must be a positive integer.");
        }
    }

    private String requiredText(Object value, int maximumLength) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (text.length() > maximumLength) {
            throw new BusinessException(400, "消息内容不能超过 " + maximumLength + " 字");
        }
        return text;
    }
}
