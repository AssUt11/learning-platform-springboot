import { CheckCircle2, Search } from "lucide-react";
import CategoryCascader from "./CategoryCascader";

function HighlightText({ text, keyword }) {
  const value = String(text || "");
  const query = keyword.trim();
  if (!query) return value;
  const index = value.toLowerCase().indexOf(query.toLowerCase());
  if (index < 0) return value;
  return <>{value.slice(0, index)}<mark>{value.slice(index, index + query.length)}</mark>{value.slice(index + query.length)}</>;
}

export default function SearchPanel({
  query,
  onQueryChange,
  tree,
  filterPath,
  onFilterPathChange,
  type,
  onTypeChange,
  onSubmit,
  loading,
  suggestions,
  onOpenCatalog
}) {
  return (
    <form className="search-panel" onSubmit={onSubmit}>
      <label className="search-box">
        <Search size={18} />
        <input aria-label="搜索课程" placeholder="搜索课程、方向或知识点" value={query} onChange={(event) => onQueryChange(event.target.value)} />
        <button type="submit" disabled={loading}>{loading ? "搜索中" : "搜索"}</button>
      </label>
      <div className="resource-filter-row">
        <CategoryCascader tree={tree} value={filterPath} allowEmpty onChange={onFilterPathChange} />
        <select aria-label="资源类型" value={type} onChange={(event) => onTypeChange(event.target.value)}>
          <option value="">全部类型</option>
          <option value="video">视频课程</option>
          <option value="material">学习资料</option>
        </select>
        <button className="filter-confirm-button" type="submit" disabled={loading}>
          <CheckCircle2 size={16} />
          {loading ? "筛选中..." : "确认筛选"}
        </button>
      </div>
      {!!suggestions.length && (
        <div className="search-suggestions">
          {suggestions.map((group) => (
            <div className="suggestion-group" key={group.name}>
              <button type="button" className="suggestion-subject" onClick={() => onOpenCatalog(group.name)}>
                <strong><HighlightText text={group.name} keyword={query} /></strong>
                <span>查看图谱</span>
              </button>
              <div className="suggestion-options">
                {group.nodes.map((node) => (
                  <button type="button" key={`${group.name}-${node.title}`} onClick={() => onOpenCatalog(group.name, node)}>
                    <HighlightText text={node.title} keyword={query} />
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </form>
  );
}
