import { ArrowRight, GitBranch } from "lucide-react";

export default function CategoryGrid({ categories, onOpenCatalog }) {
  return (
    <section className="home-catalog-react">
      <div className="home-catalog-head">
        <div>
          <span className="eyebrow">学习路线</span>
          <h3>按方向探索课程与知识</h3>
        </div>
        <span>{categories.length} 个学习领域</span>
      </div>
      {categories.length ? (
        <div className="home-catalog-grid">
          {categories.map((category, index) => (
            <article className={`home-catalog-card category-tone-${index % 6}`} key={category.name}>
              <button className="home-catalog-main" onClick={() => onOpenCatalog(category.name)}>
                <span className="category-index">0{index + 1}</span>
                <strong>{category.name}</strong>
                <span><GitBranch size={14} />进入智慧图谱</span>
              </button>
              <div className="category-card-stats">
                <span>{category.courseCount || 0} 门课程</span>
                <span>{category.learnerCount || 0} 人学习</span>
                <span>{category.completionRate || 0}% 完成</span>
              </div>
              <div className="home-subcategory-list">
                {(category.nodes || []).slice(0, 5).map((node) => (
                  <button key={`${category.name}-${node.title}`} onClick={() => onOpenCatalog(category.name, node)}>
                    {node.title}<ArrowRight size={13} />
                  </button>
                ))}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <p className="empty-state-react">没有匹配的学习方向，换个关键词试试。</p>
      )}
    </section>
  );
}
