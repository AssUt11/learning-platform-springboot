import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { demoAvatar } from './demo-avatar.mjs';

// Explicit, additive demo data. A second run preserves everything the user has done in the demo.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const runtime = path.join(root, '.runtime');
const manifestPath = path.join(runtime, 'feature-demo-manifest.json');
const base = process.env.DEMO_BASE_URL || 'http://127.0.0.1:8081';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Demo setup is for the local project.');
const db = await mysql.createConnection({ host: '127.0.0.1', user: process.env.DB_USERNAME || 'root', password: process.env.DB_PASSWORD || 'root', database: process.env.DEMO_DATABASE || 'learning_platform', charset: 'utf8mb4' });
const apply = process.argv.includes('--apply');
const protectedTables = ['users', 'resources', 'courses', 'course_chapters', 'course_lessons', 'lesson_assets', 'course_legacy_migration', 'catalog_learning_upgrades', 'lesson_progress', 'learning_records', 'resource_favorites', 'student_preferences', 'student_learning_profiles', 'student_learning_paths', 'course_questions', 'course_reviews', 'course_notes', 'course_feedback_replies', 'direct_messages', 'ai_practice_sessions', 'ai_agent_conversation', 'ai_agent_message'];
const hash = row => crypto.createHash('sha256').update(JSON.stringify(row)).digest('hex');
async function snapshot() {
  const output = {};
  for (const table of protectedTables) {
    const [rows] = await db.query(`select * from ${table} order by 1`);
    output[table] = rows.map(row => ({ id: Object.values(row)[0], hash: hash(row) }));
  }
  return output;
}
async function api(account, route, method = 'GET', body) {
  const response = await fetch(base + '/api' + route, { method, headers: { 'Content-Type': 'application/json', ...(account?.token ? { Authorization: `Bearer ${account.token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  const result = await response.json();
  if (!response.ok) throw new Error(`${method} ${route}: ${response.status} ${result.message || ''}`);
  return result.code === 200 ? result.data : result;
}
async function insert(table, fields) {
  const entries = Object.entries(fields);
  const [result] = await db.query(`insert into ${table} (${entries.map(([name]) => name).join(',')}) values (${entries.map(() => '?').join(',')})`, entries.map(([, value]) => value));
  return result.insertId;
}
let manifest;
try { manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
async function saveManifest() { await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2)); }

try {
  const [sourceCourses] = await db.query("select r.*,s.stage_code from resources r left join course_learning_stages s on s.resource_id=r.id where r.status=1 and r.review_status='approved' and r.lifecycle_status='active' and r.title not like '【功能演示%' order by r.id");
  const primary = sourceCourses.find(course => Number(course.id) === 7) || sourceCourses.find(course => course.subject === 'Java');
  assert.ok(primary, 'An existing Java course is required.');
  const [videos] = await db.query("select a.*,l.duration_seconds from lesson_assets a join course_lessons l on l.id=a.lesson_id join (select min(id) id from lesson_assets where asset_type='video' and status=1 and transcoding_status='ready' and file_url is not null group by file_url) chosen on chosen.id=a.id order by a.id limit 2");
  assert.equal(videos.length, 2, 'Two existing local recordings are required.');
  for (const video of videos) await fs.access(path.join(root, 'backend', decodeURI(video.file_url)));
  if (!apply) {
    console.log(JSON.stringify({ mode: 'dry-run', existingDataset: Boolean(manifest?.datasetCreated), accounts: 5, courses: 6, publicCourses: 3, reviewExamples: ['待审核', '已驳回', '已下架'], scenarios: ['新手规划', '薄弱复习', '全部学完待测试', '进阶挑战', '教师备课与统计'], note: 'Use --apply to add this explicitly named demo dataset.' }, null, 2));
    process.exitCode = 0;
  } else if (manifest?.datasetCreated) {
    console.log('功能演示数据已经存在，保留现有演示账号的操作和学习记录。');
  } else {
    await fs.mkdir(runtime, { recursive: true });
    if (!manifest) {
      const before = await snapshot();
      const baselineFile = path.join(runtime, `feature-demo-existing-${Date.now()}.json`);
      await fs.writeFile(baselineFile, JSON.stringify(before));
      manifest = { version: 1, namespace: 'feature-demo-v1', createdAt: new Date().toISOString(), baselineFile, accounts: {}, courses: [], datasetCreated: false };
      await saveManifest();
    }
    const accountSpecs = [
      ['new', 'student', '【演示】新同学·首次规划', 'feature-demo-new@zhidu.local'],
      ['weak', 'student', '【演示】小林·需要复习', 'feature-demo-weak@zhidu.local'],
      ['ready', 'student', '【演示】小周·已学完待测试', 'feature-demo-ready@zhidu.local'],
      ['advanced', 'student', '【演示】小陈·进阶挑战', 'feature-demo-advanced@zhidu.local'],
      ['teacher', 'teacher', '【演示】陈老师·课程工作台', 'feature-demo-teacher@zhidu.local']
    ];
    const sessions = {};
    for (const [key, role, name, email] of accountSpecs) {
      let saved = manifest.accounts[key];
      if (!saved) {
        const [[existing]] = await db.query('select id from users where email=?', [email]);
        assert.ok(!existing, `The demo email already belongs to an untracked account: ${email}`);
        saved = { email, role, name, password: `Demo!${crypto.randomBytes(6).toString('base64url')}26` };
        // Save before registering so an interrupted run can recover without resetting a password.
        manifest.accounts[key] = saved; await saveManifest();
      }
      let account;
      const [[existing]] = await db.query('select id,name,role from users where email=?', [email]);
      if (existing) {
        assert.equal(existing.name, name); assert.equal(existing.role, role);
        account = await api(null, '/auth/login', 'POST', { email, password: saved.password });
      } else account = await api(null, '/auth/register', 'POST', { email, password: saved.password, name, role, ...(role === 'teacher' ? { teacherRegistrationCode: process.env.TEACHER_REGISTRATION_CODE || 'teacher-dev-2026' } : {}) });
      saved.id = account.user.id; sessions[key] = account; await saveManifest();
      const avatarOptions = { new: ['N', '3C538E'], weak: ['L', '38696B'], ready: ['Z', '73613D'], advanced: ['C', '5A5684'], teacher: ['T', '496F52'] };
      const avatarDirectory = path.join(root, 'backend/uploads/avatars');
      await fs.mkdir(avatarDirectory, { recursive: true });
      await fs.writeFile(path.join(avatarDirectory, `feature-demo-v1-${key}.png`), demoAvatar(...avatarOptions[key]));
      saved.avatarUrl = `/uploads/avatars/feature-demo-v1-${key}.png`;
      await db.query('update users set avatar_url=? where id=? and email=? and name=? and role=?', [saved.avatarUrl, saved.id, email, name, role]);
      await saveManifest();
    }
    const teacherId = manifest.accounts.teacher.id;
    const [[alreadyCreated]] = await db.query("select count(*) count from resources where teacher_id=? and title like '【功能演示%'", [teacherId]);
    assert.equal(Number(alreadyCreated.count), 0, 'Demo courses already exist. Preserve the runtime manifest instead of creating duplicates.');
    const pick = (subject, stage) => sourceCourses.find(course => course.subject === subject && (!stage || course.stage_code === stage)) || sourceCourses.find(course => course.subject === subject) || primary;
    const specs = [
      { key: 'java', title: '【功能演示】Java 入门与对象基础', subject: 'Java', stage: 'foundation', source: primary, state: 'approved', published: true },
      { key: 'database', title: '【功能演示】数据库事务与索引', subject: '数据库', stage: 'intermediate', source: pick('数据库'), state: 'approved', published: true },
      { key: 'project', title: '【功能演示】Java 项目与综合应用', subject: 'Java', stage: 'advanced', source: pick('Java', 'advanced'), state: 'approved', published: true },
      { key: 'pending', title: '【功能演示·待审核】Python 数据分析', subject: 'Python', stage: 'foundation', source: pick('Python'), state: 'pending', published: false },
      { key: 'rejected', title: '【功能演示·待修改】Docker 容器部署', subject: 'Docker', stage: 'intermediate', source: pick('Docker'), state: 'rejected', published: false },
      { key: 'offline', title: '【功能演示·已下架】Java 项目练习', subject: 'Java', stage: 'advanced', source: primary, state: 'approved', published: false }
    ];
    const sampleText = '# Java 对象与引用：功能演示课件\n\n这份文件用于体验资料下载、解析预览、教师确认和课时内 AI 引用。\n\n引用变量保存对象引用。赋值给另一个引用变量不会自动复制整个对象。\n创建 Course 对象后令 b=a，两个引用可能指向同一对象；修改 b 的状态，也可能通过 a 观察到该变化。\n\n封装让对象维护自己的有效状态。通过受控方法修改进度，可以检查完成课时数不小于零、且不超过总课时数。\n\n为每门课程创建独立进度对象，并通过受控方法修改状态；不要把所有实例的可变进度放在同一个静态字段中。\n';
    const sampleName = 'feature-demo-v1-java-notes.txt';
    await fs.writeFile(path.join(root, 'backend/uploads', sampleName), sampleText, 'utf8');
    await db.beginTransaction();
    try {
      for (const spec of specs) {
        const structurePublished = spec.published || spec.key === 'offline';
        const [sources] = await db.query("select ch.id chapterId,ch.title chapterTitle,ch.sort_order chapterOrder,l.id,l.title,a.content_text from course_lessons l join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id join lesson_assets a on a.lesson_id=l.id and a.status=1 and a.transcoding_status='ready' where c.legacy_resource_id=? and ch.status='published' and l.status='published' and l.lesson_type='article' and a.content_text is not null order by ch.sort_order,ch.id,l.sort_order,l.id", [spec.source.id]);
        const byChapter = new Map(); for (const source of sources) byChapter.set(source.chapterId, source);
        const selected = [...byChapter.values()].slice(0, 4); assert.equal(selected.length, 4, 'Each demo needs four real source chapters.');
        const [[direction]] = await db.query('select id,category_id from directions where name=? limit 1', [spec.subject]);
        const description = `功能演示样例：用于体验${spec.published ? '课程搜索、视频图文学习、个性化练习及学习反馈' : '教师管理、审核状态与内容治理'}。视频复用已有本地录播；课程知识以图文讲义为准。预置学习进度和成绩仅属于演示账号。`;
        const courseId = await insert('resources', { teacher_id: teacherId, category_id: direction.category_id, direction_id: direction.id, title: spec.title, subject: spec.subject, type: 'video', level: spec.stage === 'foundation' ? '入门' : spec.stage === 'advanced' ? '进阶' : '中级', estimated_minutes: 60, status: spec.published ? 1 : 0, review_status: spec.state, review_feedback: spec.state === 'rejected' ? '【预置演示反馈】请补充容器网络说明、实验步骤与录播摘要后重新提交。' : spec.state === 'pending' ? null : '【预置演示状态】用于功能体验，不代表真实投稿审核。', description, content: `${description}\n\n${selected.map(item => item.content_text).join('\n\n')}`, file_url: videos[0].file_url });
        const internalId = await insert('courses', { legacy_resource_id: courseId, teacher_id: teacherId, title: spec.title, subject: spec.subject, description });
        await db.query('update resources set course_id=? where id=? and teacher_id=?', [internalId, courseId, teacherId]);
        await insert('course_learning_stages', { resource_id: courseId, stage_code: spec.stage, updated_by: teacherId });
        const course = { key: spec.key, id: courseId, internalId, title: spec.title, subject: spec.subject, state: spec.state, published: spec.published, chapters: [], lessons: [] };
        for (let index = 0; index < selected.length; index++) {
          const source = selected[index], video = videos[index % videos.length];
          const chapterId = await insert('course_chapters', { course_id: internalId, title: `第${index + 1}章 · ${source.title.replace(/ \[教师修订\]$/, '')}`, description: '功能演示章节：观看视频、阅读讲义并检查理解。', sort_order: index + 1, status: structurePublished ? 'published' : 'draft' });
          course.chapters.push(chapterId);
          const videoId = await insert('course_lessons', { chapter_id: chapterId, title: `${source.title} · 演示视频`, lesson_type: 'video', duration_seconds: video.duration_seconds, free_preview: 1, sort_order: 1, status: structurePublished ? 'published' : 'ready' });
          const videoAssetId = await insert('lesson_assets', { lesson_id: videoId, asset_type: 'video', file_url: video.file_url, file_name: `临时演示视频 ${index % 2 ? 'B' : 'A'}（功能演示）.mp4`, file_size: video.file_size, mime_type: 'video/mp4', transcoding_status: 'ready', status: 1 });
          const articleText = `【功能演示讲义】以下内容来自平台已有公开课程，用于体验学习和测试。配套视频为复用的临时演示素材。\n\n${source.content_text}`;
          const articleId = await insert('course_lessons', { chapter_id: chapterId, title: source.title.replace(/ \[教师修订\]$/, ''), lesson_type: 'article', duration_seconds: Math.max(300, Math.ceil(articleText.length / 5)), free_preview: 1, sort_order: 2, status: structurePublished ? 'published' : 'ready' });
          const articleAssetId = await insert('lesson_assets', { lesson_id: articleId, asset_type: 'article', content_text: articleText, transcoding_status: 'ready', status: 1 });
          for (const sourceId of [videoId, articleId]) await insert('catalog_learning_upgrades', { source_lesson_id: sourceId, article_lesson_id: articleId, video_lesson_id: videoId, version: 'feature-demo-v1' });
          const [topics] = await db.query('select category_name,direction_name,topic_title from lesson_knowledge_topics where lesson_id=?', [source.id]);
          for (const lessonId of [videoId, articleId]) for (const topic of topics) await insert('lesson_knowledge_topics', { lesson_id: lessonId, ...topic });
          course.lessons.push({ id: videoId, assetId: videoAssetId, chapterId, type: 'video', title: `${source.title} · 演示视频`, duration: video.duration_seconds }, { id: articleId, assetId: articleAssetId, chapterId, type: 'article', title: source.title, duration: Math.max(300, Math.ceil(articleText.length / 5)), text: articleText, topics: topics.map(topic => topic.topic_title) });
        }
        const first = course.lessons[0];
        await insert('course_legacy_migration', { resource_id: courseId, previous_course_id: null, course_id: internalId, chapter_id: first.chapterId, lesson_id: first.id, asset_id: first.assetId });
        if (spec.key === 'java') {
          const documentId = await insert('course_lessons', { chapter_id: course.chapters[0], title: 'Java 对象与引用 · 可下载课件', lesson_type: 'document', duration_seconds: 300, free_preview: 1, sort_order: 3, status: 'published' });
          const assetId = await insert('lesson_assets', { lesson_id: documentId, asset_type: 'document', file_url: `/uploads/${sampleName}`, file_name: '功能演示-Java对象与引用.txt', file_size: Buffer.byteLength(sampleText), mime_type: 'text/plain', content_text: sampleText, transcoding_status: 'ready', status: 1 });
          course.lessons.push({ id: documentId, assetId, chapterId: course.chapters[0], type: 'document', title: 'Java 对象与引用 · 可下载课件', duration: 300, text: sampleText, topics: ['Java基础', '面向对象'] });
          const exerciseId = await insert('course_lessons', { chapter_id: course.chapters[3], title: '教师练习 · 两门课程的独立进度', lesson_type: 'exercise', duration_seconds: 300, free_preview: 1, sort_order: 3, status: 'published' });
          const exerciseText = '【预置教师练习】为 Java 和数据库两门课程分别创建进度对象。完成 Java 的一个课时后，检查数据库课程的已完成课时数是否保持不变。写出你采用的数据字段、修改方法和边界检查。\n参考要点：为每门课程创建独立进度对象；通过受控方法修改状态；已完成课时数应在零和总课时数之间。';
          const exerciseAsset = await insert('lesson_assets', { lesson_id: exerciseId, asset_type: 'exercise', content_text: exerciseText, transcoding_status: 'ready', status: 1 });
          course.lessons.push({ id: exerciseId, assetId: exerciseAsset, chapterId: course.chapters[3], type: 'exercise', title: '教师练习 · 两门课程的独立进度', duration: 300, text: exerciseText });
        }
        manifest.courses.push(course);
      }
      const main = manifest.courses.find(course => course.key === 'java');
      const articles = main.lessons.filter(lesson => lesson.type === 'article');
      const textLessons = main.lessons.filter(lesson => ['article', 'document'].includes(lesson.type));
      function question(lesson, variant = 0) {
        const core = lesson.text.split(/## (?:核心讲解|深入讲解)\s*/)[1]?.split('\n')[0] || '引用变量保存对象引用。';
        let reference = core.split('。')[0] + '。';
        if (!lesson.text.includes(reference)) reference = '引用变量保存对象引用。';
        if (lesson.id === articles[0].id && lesson.text.includes('引用变量保存对象引用')) return { stem: `【预置演示题${variant + 1}】引用变量保存什么？`, options: ['对象引用', '对象的自动副本', '磁盘文件路径', '线程运行状态'], answer: 0, explanation: '【功能演示样例，非实时 AI 生成】引用变量保存对象引用。赋值不会自动复制整个对象。', reference: lesson.text.split(/(?<=[。！？])|\n/).find(line => line.includes('引用变量保存对象引用')).trim(), sourceLessonId: lesson.id, sourceLessonTitle: lesson.title, knowledgePoints: lesson.topics || [lesson.title] };
        const correct = reference.split(/[，；。]/)[0];
        return { stem: `【预置演示题${variant + 1}】关于“${lesson.title}”，哪项表述符合讲义？`, options: [correct, '只改变页面颜色就能保证程序正确', '不检查任何输入也一定不会出错', '所有对象必须永久共享同一个可变状态'], answer: 0, explanation: '【功能演示样例，非实时 AI 生成】请结合讲义原句核对概念和适用条件。', reference, sourceLessonId: lesson.id, sourceLessonTitle: lesson.title, knowledgePoints: lesson.topics || [lesson.title] };
      }
      async function quiz(userId, lesson, difficulty, scope, answers, variant = 0) {
        const questions = [question(lesson, variant)];
        return insert('ai_practice_sessions', { quiz_id: crypto.randomUUID(), user_id: userId, course_id: main.id, lesson_id: lesson.id, difficulty, questions_json: JSON.stringify(questions), provider_code: 'demo-seed', model_code: '预置功能演示样例', practice_scope: scope, ...(answers == null ? {} : { answers_json: JSON.stringify(answers), score: answers[0] === questions[0].answer ? 100 : 0, submitted_at: new Date() }) });
      }
      for (const key of ['weak', 'ready', 'advanced']) {
        const studentId = manifest.accounts[key].id;
        await insert('student_preferences', { student_id: studentId, subject: 'Java' });
        await insert('student_preferences', { student_id: studentId, subject: '数据库' });
        const pathId = `feature-demo-v1-${key}-${studentId}`;
        const publicCourses = manifest.courses.filter(course => course.published);
        const nodes = publicCourses.map((course, index) => ({ id: `demo-step-${index + 1}`, order: index + 1, title: course.title, stage: ['foundation', 'intermediate', 'advanced'][index], courseId: course.id, subject: course.subject, goal: ['理解变量、引用与封装，完成独立对象练习。', '理解事务和查询方法，为课程进度保存设计数据。', '把对象状态与接口契约组合成小型项目。'][index], completionCriteria: '完成本步骤课程学习，用自测检查理解，并能独立解释一个案例。', reason: '【预置演示路线】用于展示阶段、进度和复习建议，不是本次 AI 生成。', estimatedMinutes: 60 }));
        const snapshot = { pathId, title: '【预置演示路径】从 Java 概念到项目应用', available: true, createdAt: new Date().toISOString(), graphUsed: false, nodes, links: nodes.slice(1).map((node, index) => ({ source: nodes[index].id, target: node.id })), analysis: '【功能演示样例】这是一份预置路线，未调用模型生成；学习状态与下一步建议会根据演示账号的课时进度和成绩实时更新。', evidence: [] };
        await insert('student_learning_paths', { path_id: pathId, user_id: studentId, snapshot_json: JSON.stringify(snapshot), neo4j_synced: 0 });
        await insert('student_learning_profiles', { user_id: studentId, answers_json: JSON.stringify({ subject: 'Java', background: key === 'advanced' ? 'projects' : 'basics', goal: 'project', targetStage: key === 'advanced' ? 'advanced' : 'foundation', dailyMinutes: 30, knownTopics: ['Java基础'], notes: '【功能演示档案】用课程学习进度系统练习 Java。' }), status: 'completed', active_path_id: pathId });
        let total = 0;
        for (const [index, lesson] of main.lessons.entries()) {
          const progress = key !== 'weak' ? 100 : index < 2 ? 100 : index === 2 ? 45 : index === 3 ? 35 : 0;
          total += progress;
          if (progress === 0) continue;
          await insert('lesson_progress', { user_id: studentId, lesson_id: lesson.id, last_position_seconds: Math.floor(lesson.duration * progress / 100), duration_seconds: lesson.duration, progress_percent: progress, completed: progress === 100 ? 1 : 0, last_studied_at: new Date(Date.now() - (key === 'weak' ? 8 : 0) * 86400000) });
        }
        await insert('learning_records', { student_id: studentId, resource_id: main.id, record_key: `resource:${main.id}`, subject: 'Java', progress: Math.round(total / main.lessons.length), duration_seconds: key === 'weak' ? 540 : 2400, score: key === 'weak' ? 25 : key === 'ready' ? null : 100, created_at: new Date(Date.now() - (key === 'weak' ? 8 : 0) * 86400000) });
        for (const course of publicCourses.slice(0, key === 'advanced' ? 3 : 2)) await insert('resource_favorites', { student_id: studentId, resource_id: course.id });
        await insert('course_notes', { resource_id: main.id, student_id: studentId, lesson_title: articles[0].title, content: '【演示笔记】引用赋值不等于复制对象。b=a 后，两个引用可能指向同一个对象。下一步：画出两个变量与对象之间的关系，再验证修改 b 后 a 的观察结果。' });
        if (key === 'weak') {
          for (let index = 0; index < 4; index++) await quiz(studentId, articles[0], '基础', 'lesson', [index === 0 ? 0 : 1], index);
          await quiz(studentId, articles[0], '基础', 'lesson', null, 4);
          const questionId = await insert('course_questions', { resource_id: main.id, student_id: studentId, content: '【演示提问】为什么 b=a 之后修改 b 的状态，a 也会看到变化？怎样才能让两门课程的进度互不影响？', answer_count: 1 });
          await insert('course_feedback_replies', { target_type: 'question', target_id: questionId, teacher_id: teacherId, content: '【演示教师回复】b=a 复制的是引用，两个变量可能指向同一对象。请为每门课程分别创建进度对象，再用方法检查修改边界。' });
          await insert('direct_messages', { sender_id: studentId, recipient_id: teacherId, content: '【预置演示私信】陈老师，我做完对象引用练习了，应该先复习什么？', is_read: 1 });
          await insert('direct_messages', { sender_id: teacherId, recipient_id: studentId, content: '【预置演示私信】请先画出 b=a 的引用关系，再到课程学习页选择“按学习情况”练习。', is_read: 0 });
        }
        if (key === 'ready') await quiz(studentId, articles[0], '基础', 'course', null, 5);
        if (key === 'advanced') {
          for (const lesson of textLessons) for (let index = 0; index < 4; index++) await quiz(studentId, lesson, index < 2 ? '基础' : '巩固', 'lesson', [0], index + 10);
          await quiz(studentId, articles.at(-1), '基础', 'course', [0], 20);
          await quiz(studentId, articles.at(-1), '巩固', 'course', [0], 21);
        }
        const reviewId = await insert('course_reviews', { resource_id: main.id, student_id: studentId, rating: key === 'weak' ? 4 : 5, content: '【演示评价】图文案例能帮助我理解对象状态。希望继续补充边界条件和独立进度练习。' });
        if (key === 'ready') await insert('course_feedback_replies', { target_type: 'review', target_id: reviewId, teacher_id: teacherId, content: '【演示教师回复】谢谢反馈，建议接着完成课程测试，再根据解析巩固薄弱点。' });
        const conversationId = await insert('ai_agent_conversation', { user_id: studentId, title: '【预置演示对话】如何安排 Java 复习' });
        const requestId = crypto.randomUUID();
        await insert('ai_agent_message', { conversation_id: conversationId, role: 'user', content: '【预置演示问题】每天只有30分钟，如何复习对象与引用？', status: 'completed', request_id: requestId });
        await insert('ai_agent_message', { conversation_id: conversationId, role: 'assistant', content: '【预置功能演示回复，非实时 AI 生成】可以先花10分钟阅读引用与对象的讲义，再用10分钟画出 b=a 的引用关系，最后用10分钟完成基础练习并记录错因。你可以在这里继续追问，新的回答将调用当前模型。', status: 'completed', request_id: requestId, provider_code: 'demo-seed', model_code: '预置功能演示样例' });
      }
      manifest.datasetCreated = true;
      await db.commit();
      await saveManifest();
      console.log(JSON.stringify({ addedAccounts: Object.keys(manifest.accounts).length, addedCourses: manifest.courses.length, publicCourses: manifest.courses.filter(course => course.published).length, mainCourseId: main.id, lessonsPerCourse: manifest.courses.map(course => ({ key: course.key, count: course.lessons.length })), manifest: manifestPath }, null, 2));
    } catch (error) { await db.rollback(); manifest.courses = []; manifest.datasetCreated = false; await saveManifest(); throw error; }
  }
} finally { await db.end(); }
