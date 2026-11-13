/** Small, safe renderer for our authored lesson headings, diagrams and paragraphs. */
export default function LessonReading({ text = "" }) {
  return <div className="lesson-reading">
    {String(text).split(/\n\s*\n/).filter(Boolean).map((block, index) => {
      const lines = block.trim().split("\n");
      return <div key={index}>{lines.map((line, lineIndex) => {
        if (/^#{1,3}\s/.test(line)) return <h2 key={lineIndex}>{line.replace(/^#{1,3}\s+/, "")}</h2>;
        if (line.startsWith("流程图：")) return <figure className="lesson-flow" key={lineIndex}>
          <figcaption>知识流程</figcaption>
          <ol>{line.slice(4).split("→").map((step, stepIndex) => <li key={stepIndex}>{step.trim()}</li>)}</ol>
        </figure>;
        return <p key={lineIndex}>{line}</p>;
      })}</div>;
    })}
  </div>;
}
