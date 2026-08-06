export default function CategoryCascader({ tree = [], value = [], onChange, allowEmpty = false }) {
  const first = value[0] || "";
  const second = value[1] || "";
  const third = value[2] || "";
  const firstNode = tree.find((item) => item.label === first);
  const secondNode = firstNode?.children?.find((item) => item.label === second);

  return (
    <div className="category-cascader">
      <select aria-label="课程大类" value={first} onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}>
        {allowEmpty && <option value="">全部大类</option>}
        {tree.map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
      <select aria-label="学习方向" value={second} onChange={(event) => onChange(event.target.value ? [first, event.target.value] : first ? [first] : [])} disabled={!firstNode}>
        <option value="">{firstNode ? "全部方向" : "先选大类"}</option>
        {(firstNode?.children || []).map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
      <select aria-label="知识点" value={third} onChange={(event) => onChange(event.target.value ? [first, second, event.target.value] : second ? [first, second] : first ? [first] : [])} disabled={!secondNode}>
        <option value="">{secondNode ? "全部知识点" : "先选方向"}</option>
        {(secondNode?.children || []).map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
    </div>
  );
}
