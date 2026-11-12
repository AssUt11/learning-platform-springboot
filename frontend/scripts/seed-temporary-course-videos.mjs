import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import mysql from "mysql2/promise";

// Explicit, one-time local demo import. Running again never replaces an existing active video.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const uploadRoot = path.resolve(process.env.APP_UPLOAD_DIR || path.join(root, "backend/uploads"));
const url = new URL((process.env.DB_URL || "jdbc:mysql://127.0.0.1:3306/learning_platform").replace(/^jdbc:/, ""));
const apply = process.argv.includes("--apply");
const db = await mysql.createConnection({
  host: url.hostname, port: Number(url.port || 3306), database: url.pathname.slice(1),
  user: process.env.DB_USERNAME || "root", password: process.env.DB_PASSWORD || "root"
});
let transaction = false;
try {
  const [rows] = await db.query(`select a.id,a.file_url,a.mime_type from lesson_assets a
    join course_lessons l on l.id=a.lesson_id join course_chapters ch on ch.id=l.chapter_id
    join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
    where a.asset_type='video' and a.status=1 and a.transcoding_status='ready'
      and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
      and ch.status='published' and l.status='published'
      and coalesce(a.file_name,'') not like '临时演示视频%' order by a.id`);
  const sources = new Map();
  for (const row of rows) {
    if (!row.file_url?.startsWith("/uploads/") || sources.has(row.file_url)) continue;
    // These two bundled files were measured in the browser: 105.511474 and 310.775873 seconds.
    const seconds = row.file_url.startsWith("/uploads/1779430126093-") ? 106
      : row.file_url.startsWith("/uploads/1779430545143-") ? 311 : 0;
    if (!seconds) continue;
    const localFile = path.resolve(uploadRoot, row.file_url.slice("/uploads/".length));
    if (!localFile.startsWith(uploadRoot + path.sep)) throw new Error("Video path leaves upload directory");
    const stat = await fs.stat(localFile);
    if (!stat.isFile() || !stat.size) throw new Error("Local demo video is empty or unavailable");
    sources.set(row.file_url, { ...row, seconds, size: stat.size });
  }
  if (!sources.size) throw new Error("No verified local demo recordings are available");
  await db.beginTransaction();
  transaction = true;
  const [targets] = await db.query(`select v.*,r.id publicCourseId from catalog_learning_upgrades u
    join course_lessons v on v.id=u.video_lesson_id join course_chapters ch on ch.id=v.chapter_id
    join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
    where r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
      and ch.status='published' and v.lesson_type='video' and v.status='draft'
      and not exists(select 1 from lesson_assets a where a.lesson_id=v.id and a.status=1)
    order by v.id for update`);
  const summary = { database: url.pathname.slice(1), apply, courses: new Set(targets.map(row => row.publicCourseId)).size, videos: targets.length, localFiles: sources.size };
  if (!apply || !targets.length) {
    await db.rollback();
    transaction = false;
    console.log(JSON.stringify(summary));
  } else {
    const ids = [...new Set(targets.map(row => row.publicCourseId))];
    const placeholders = ids.map(() => "?").join(",");
    const [resources] = await db.query(`select * from resources where id in (${placeholders})`, ids);
    const [progress] = await db.query("select * from lesson_progress");
    const runId = new Date().toISOString().replace(/[^0-9]/g, "");
    const backup = path.join(root, ".runtime", `temporary-videos-before-${runId}.json`);
    await fs.mkdir(path.dirname(backup), { recursive: true });
    await fs.writeFile(backup, JSON.stringify({ ...summary, resources, lessons: targets, progress }));
    const pool = [...sources.values()];
    const imported = [];
    for (const [index, lesson] of targets.entries()) {
      const source = pool[index % pool.length];
      const [asset] = await db.execute(`insert into lesson_assets
        (lesson_id,asset_type,file_url,file_name,file_size,mime_type,transcoding_status,status)
        values(?,'video',?,?,?,?,'ready',1)`,
        [lesson.id, source.file_url, `临时演示视频 ${String.fromCharCode(65 + index % pool.length)}.mp4`, source.size, source.mime_type || "video/mp4"]);
      const [updated] = await db.execute("update course_lessons set duration_seconds=?,status='published' where id=? and status='draft'", [source.seconds, lesson.id]);
      if (updated.affectedRows !== 1) throw new Error("Video lesson changed during import");
      imported.push({ courseId: lesson.publicCourseId, lessonId: lesson.id, assetId: asset.insertId, sourceAssetId: source.id });
    }
    await db.query(`update resources r set type='video',estimated_minutes=(
      select ceil(sum(l.duration_seconds)/60.0) from courses c join course_chapters ch on ch.course_id=c.id
      join course_lessons l on l.chapter_id=ch.id where c.legacy_resource_id=r.id
      and ch.status='published' and l.status='published') where r.id in (${placeholders})`, ids);
    const receipt = path.join(root, ".runtime", `temporary-videos-import-${runId}.json`);
    await fs.writeFile(receipt, JSON.stringify({ ...summary, backup, imported }));
    await db.commit();
    transaction = false;
    console.log(JSON.stringify({ ...summary, backup, receipt }));
  }
} finally {
  if (transaction) await db.rollback();
  await db.end();
}
