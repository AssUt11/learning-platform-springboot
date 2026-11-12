import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import mysql from "mysql2/promise";

// Read-only inventory. Run after restarting the backend to include the prepared video lesson IDs.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const expansions = JSON.parse(await fs.readFile(path.join(root, "backend/src/main/resources/catalog/lesson-expansions.json"), "utf8"));
const url = new URL((process.env.DB_URL || "jdbc:mysql://127.0.0.1:3306/learning_platform").replace(/^jdbc:/, ""));
const db = await mysql.createConnection({
  host: url.hostname, port: Number(url.port || 3306), database: url.pathname.slice(1),
  user: process.env.DB_USERNAME || "root", password: process.env.DB_PASSWORD || "root"
});
try {
  const [[{ upgraded }]] = await db.query("select count(*) upgraded from information_schema.tables where table_schema=database() and table_name='catalog_learning_upgrades'");
  const [rows] = await db.query(`select r.id courseId,r.title courseTitle,l.id sourceLessonId,l.title lessonTitle,
    ${upgraded ? "u.video_lesson_id" : "case when l.lesson_type='video' then l.id else null end"} videoLessonId,
    ${upgraded ? "v.status" : "case when l.lesson_type='video' then l.status else 'draft' end"} videoStatus,
    (select a.file_name from lesson_assets a where a.lesson_id=${upgraded ? "u.video_lesson_id" : "l.id"}
      and a.asset_type='video' and a.status=1 and a.transcoding_status='ready' order by a.id limit 1) videoFileName
    from resources r join courses c on c.legacy_resource_id=r.id join course_chapters ch on ch.course_id=c.id
    join course_lessons l on l.chapter_id=ch.id
    ${upgraded ? "left join catalog_learning_upgrades u on u.source_lesson_id=l.id left join course_lessons v on v.id=u.video_lesson_id" : ""}
    where r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
    and ch.status='published' and l.status='published' order by r.id,l.sort_order,l.id`);
  const headers = ["课程编号", "课程名称", "原课时编号", "课时名称", "视频课时编号", "状态", "建议素材文件名", "本地文件路径", "实测时长秒"];
  const records = rows.filter(row => expansions[row.lessonTitle]).map(row => [
    row.courseId, row.courseTitle, row.sourceLessonId, row.lessonTitle, row.videoLessonId || "",
    row.videoFileName?.startsWith("临时演示视频") ? "临时演示，待正式录播" : row.videoStatus === "published" ? "已有录播" : "待提供素材",
    `${row.courseId}-${row.sourceLessonId}-${row.lessonTitle.replace(/[<>:"/\\|?*]/g, "_")}.mp4`, "", ""
  ]);
  const quote = value => `"${String(value).replaceAll('"', '""')}"`;
  const output = path.resolve(process.argv[2] || path.join(root, "docs/课程录播素材清单.csv"));
  await fs.mkdir(path.dirname(output), { recursive: true });
  await fs.writeFile(output, "\ufeff" + [headers, ...records].map(row => row.map(quote).join(",")).join("\r\n") + "\r\n", "utf8");
  console.log(`已导出 ${records.length} 个录播课时清单：${output}`);
} finally { await db.end(); }
