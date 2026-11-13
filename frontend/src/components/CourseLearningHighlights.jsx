import { ArrowUpRight, BookOpen } from "lucide-react";

function lessonExcerpt(lesson) {
  return String(lesson.description || lesson.contentText || "")
    .replace(/<[^>]*>/g, " ")
    .split(/\n+/)
    .map((line) => line.replace(/^\s*[#>*-]+\s*/, "").trim())
    .find((line) => line && line !== lesson.title && !/^第.*[章节]/.test(line)) || "打开课时，查看完整学习内容。";
}

export default function CourseLearningHighlights({ chapters = [], onOpenLesson, disabled }) {
  const lessons = chapters.flatMap((chapter) => chapter.lessons || []).slice(0, 3);
  if (!lessons.length) return null;
  return (
    <section className="course-learning-highlights" aria-labelledby="course-highlights-title">
      <h2 id="course-highlights-title"><BookOpen size={17} />这门课会学什么</h2>
      <ol>{lessons.map((lesson) => (
        <li key={lesson.id}>
          <button type="button" disabled={disabled} onClick={() => onOpenLesson(lesson)}>
            <span><strong>{lesson.title}</strong><small>{lessonExcerpt(lesson)}</small></span><ArrowUpRight size={17} />
          </button>
        </li>
      ))}</ol>
    </section>
  );
}
