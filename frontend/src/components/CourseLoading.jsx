// Reserve the course layout without suggesting that missing data is already loaded.
export default function CourseLoading({ reading = false }) {
  return (
    <main className={`course-skeleton ${reading ? "is-reading" : ""}`} aria-busy="true">
      <h1 role="status">{reading ? "正在加载课程内容…" : "正在建立课程学习空间…"}</h1>
      <div className="course-skeleton-layout" aria-hidden="true">
        <div className="course-skeleton-sidebar">
          <i className="skeleton-line short" />
          <i className="skeleton-line" />
          <i className="skeleton-line" />
          <i className="skeleton-line short" />
        </div>
        <div className="course-skeleton-content">
          <i className="skeleton-line title" />
          <i className="skeleton-line" />
          <i className="skeleton-line" />
          <i className="skeleton-line short" />
          <div className="skeleton-reading-area" />
        </div>
      </div>
    </main>
  );
}
