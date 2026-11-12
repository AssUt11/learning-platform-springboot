import { useState } from "react";
import { Play } from "lucide-react";
import { getCourseArtwork, getCourseCoverUrl } from "../utils/courseArtwork";
import "../course-artwork.css";

const LOGO_ROOT = `${import.meta.env.BASE_URL}course-art/devicon/`;

function WindowFrame({ children }) {
  return (
    <>
      <rect x="1" y="1" width="202" height="142" rx="10" className="art-paper art-stroke" />
      <path d="M1 25H203" className="art-line" />
      <circle cx="15" cy="13" r="2" className="art-soft-fill" />
      <circle cx="23" cy="13" r="2" className="art-soft-fill" />
      <circle cx="31" cy="13" r="2" className="art-soft-fill" />
      {children}
    </>
  );
}

function TopicScene({ topic }) {
  switch (topic.scene) {
    case "terminal":
      return <WindowFrame>
        <text x="15" y="48" className="art-code art-accent">$ ls -l</text>
        <text x="15" y="69" className="art-code">drwxr-xr-x  logs/</text>
        <text x="15" y="90" className="art-code art-accent">$ ps aux | grep java</text>
        <path d="M15 107H119M15 118H163" className="art-line" />
        <path d="M15 132H24" className="art-strong-line" />
      </WindowFrame>;
    case "code":
      return <WindowFrame>
        {(topic.code || ["const course = {", "  lessons: [ ]", "}"]).map((line, index) =>
          <text key={line} x="15" y={45 + index * 16} className={`art-code${index === 0 ? " art-accent" : ""}`}>{line}</text>
        )}
      </WindowFrame>;
    case "database":
      return <>
        <ellipse cx="61" cy="30" rx="43" ry="15" className="art-tint art-stroke" />
        <path d="M18 30V95C18 115 104 115 104 95V30" className="art-paper art-stroke" />
        <path d="M18 51C18 71 104 71 104 51M18 73C18 93 104 93 104 73" className="art-line" />
        <ellipse cx="61" cy="30" rx="43" ry="15" className="art-tint art-stroke" />
        <path d="M104 76H128V55H143M128 76V112H143" className="art-strong-line" />
        <rect x="143" y="37" width="56" height="36" rx="5" className="art-paper art-stroke" />
        <rect x="143" y="94" width="56" height="36" rx="5" className="art-paper art-stroke" />
        <path d="M153 49H187M153 59H177M153 106H187M153 116H177" className="art-line" />
        <circle cx="128" cy="76" r="4" className="art-accent-fill" />
      </>;
    case "pipeline":
      return <>
        {[15, 58, 101].map((y, index) => <g key={y}>
          <rect x="0" y={y} width="54" height="30" rx="5" className="art-paper art-stroke" />
          <text x="12" y={y + 19} className="art-code">P{index + 1}</text>
          <path d={`M54 ${y + 15}H77L98 73`} className="art-line" />
        </g>)}
        <rect x="95" y="48" width="43" height="50" rx="7" className="art-tint art-stroke" />
        <path d="M106 62L119 73L106 84M138 73H157" className="art-strong-line" />
        <rect x="156" y="36" width="47" height="74" rx="6" className="art-paper art-stroke" />
        <path d="M167 53H192M167 65H186M167 77H192M167 89H182" className="art-line" />
      </>;
    case "probability":
      return <>
        <rect x="1" y="1" width="202" height="142" rx="10" className="art-paper art-stroke" />
        <path d="M23 112H186M23 112V27M55 112V64M85 112V43M115 112V69M145 112V89" className="art-line" />
        <rect x="41" y="83" width="20" height="29" rx="2" className="art-tint" />
        <rect x="73" y="51" width="20" height="61" rx="2" className="art-soft-fill" />
        <rect x="105" y="71" width="20" height="41" rx="2" className="art-tint" />
        <rect x="137" y="92" width="20" height="20" rx="2" className="art-soft-fill" />
        <path d="M32 105C52 105 53 44 83 44S112 105 177 105" className="art-strong-line" />
        <text x="22" y="131" className="art-code">E(X) = Σ xP(x)</text>
      </>;
    case "calculus":
      return <>
        <rect x="1" y="1" width="202" height="142" rx="10" className="art-paper art-stroke" />
        <path d="M25 111H183M40 125V20M40 81H180M80 111V28M120 111V28M160 111V28" className="art-grid" />
        <path d="M40 111C72 109 104 94 128 65V111Z" className="art-tint" />
        <path d="M33 113C83 110 133 82 173 24" className="art-strong-line" />
        <path d="M77 116L163 38" className="art-line" />
        <circle cx="128" cy="68" r="4" className="art-accent-fill" />
        <text x="119" y="132" className="art-code">f′(x)</text>
      </>;
    case "matrix":
      return <>
        <rect x="1" y="1" width="202" height="142" rx="10" className="art-paper art-stroke" />
        <path d="M27 35H18V107H27M84 35H93V107H84" className="art-strong-line" />
        <text x="34" y="60" className="art-math">2  1</text><text x="34" y="91" className="art-math">0  3</text>
        <path d="M108 73H130M124 68L130 73L124 78M148 114V34M128 94H188" className="art-line" />
        <path d="M148 94L183 45M148 94L175 82" className="art-strong-line" />
        <circle cx="183" cy="45" r="4" className="art-accent-fill" />
        <text x="34" y="131" className="art-code">Av = λv</text>
      </>;
    case "tree":
      return <>
        <path d="M101 36V54H42V76M101 54H160V76M42 100V115H15M42 115H70M160 100V115H133M160 115H189" className="art-line" />
        {[[101, 23], [42, 88], [160, 88], [15, 128], [70, 128], [133, 128], [189, 128]].map(([x, y], index) =>
          <g key={`${x}-${y}`}><rect x={x - 12} y={y - 12} width="24" height="24" rx="5" className={`${index === 0 ? "art-tint" : "art-paper"} art-stroke`} />
            <circle cx={x} cy={y} r="3" className={index === 0 ? "art-accent-fill" : "art-soft-fill"} /></g>
        )}
      </>;
    case "network":
    case "neural": {
      const layers = topic.scene === "neural" ? [[28, [29, 73, 117]], [101, [18, 55, 92, 129]], [175, [45, 101]]] : [[25, [31, 114]], [101, [73]], [178, [31, 114]]];
      return <>
        {layers.slice(0, -1).flatMap(([x, ys], i) => ys.flatMap((y) => layers[i + 1][1].map((ny) =>
          <path key={`${x}-${y}-${ny}`} d={`M${x} ${y}L${layers[i + 1][0]} ${ny}`} className="art-line" />
        )))}
        {layers.flatMap(([x, ys], i) => ys.map((y) => <circle key={`${x}-${y}`} cx={x} cy={y} r={topic.scene === "neural" ? 9 : 17} className={`${i === 1 ? "art-tint" : "art-paper"} art-stroke`} />))}
        {topic.scene === "network" && <path d="M94 73H108M101 66V80" className="art-strong-line" />}
      </>;
    }
    case "containers":
      return <>
        <rect x="1" y="1" width="202" height="142" rx="10" className="art-paper art-stroke" />
        {[19, 75, 131].map((x) => <g key={x}><rect x={x} y="27" width="47" height="52" rx="5" className="art-tint art-stroke" /><path d={`M${x + 10} 41H${x + 37}M${x + 10} 51H${x + 32}M${x + 10} 61H${x + 37}`} className="art-line" /></g>)}
        <path d="M21 98H181" className="art-line" /><text x="47" y="121" className="art-code">isolated · portable</text>
      </>;
    case "testing":
      return <WindowFrame>{[48, 79, 110].map((y) => <g key={y}>
        <rect x="15" y={y - 11} width="17" height="17" rx="4" className="art-tint" /><path d={`M19 ${y - 3}L23 ${y + 1}L29 ${y - 6}`} className="art-strong-line" />
        <path d={`M44 ${y - 4}H174M44 ${y + 5}H126`} className="art-line" />
      </g>)}</WindowFrame>;
    case "interface":
      return <WindowFrame>
        <rect x="12" y="36" width="41" height="96" rx="3" className="art-tint" />
        <path d="M22 50H44M22 64H39M22 78H44M22 92H39" className="art-line" />
        <rect x="64" y="36" width="125" height="33" rx="4" className="art-tint" />
        <path d="M65 82H180M65 93H161" className="art-line" />
        <rect x="65" y="107" width="52" height="22" rx="4" className="art-soft-fill" />
      </WindowFrame>;
    case "chart":
      return <WindowFrame>
        <path d="M22 117H184M22 117V43M22 82H184M22 50H184" className="art-grid" />
        <rect x="37" y="82" width="18" height="35" rx="2" className="art-tint" /><rect x="76" y="64" width="18" height="53" rx="2" className="art-soft-fill" />
        <rect x="115" y="73" width="18" height="44" rx="2" className="art-tint" /><rect x="154" y="46" width="18" height="71" rx="2" className="art-soft-fill" />
        <path d="M45 73L84 47L123 59L162 31" className="art-strong-line" />
      </WindowFrame>;
    case "security":
      return <>
        <path d="M102 9L155 29V73C155 106 128 126 102 139C76 126 49 106 49 73V29Z" className="art-paper art-stroke" />
        <rect x="77" y="64" width="50" height="40" rx="7" className="art-tint art-stroke" />
        <path d="M86 64V51A16 16 0 0 1 118 51V64" className="art-strong-line" />
        <circle cx="102" cy="81" r="4" className="art-accent-fill" /><path d="M102 85V92" className="art-strong-line" />
        <path d="M16 47H37M166 105H188M26 111H37M166 45H183" className="art-line" />
      </>;
    case "chip":
      return <>
        {[30, 58, 86, 114].map((v) => <g key={v}><path d={`M${v + 27} 8V30M${v + 27} 119V142M22 ${v}H48M155 ${v}H183`} className="art-line" /></g>)}
        <rect x="48" y="30" width="107" height="89" rx="10" className="art-paper art-stroke" />
        <rect x="68" y="50" width="67" height="49" rx="5" className="art-tint art-stroke" />
        <text x="81" y="80" className="art-math">CPU</text>
      </>;
    case "vision":
      return <WindowFrame>
        <rect x="16" y="38" width="94" height="90" rx="4" className="art-tint" /><path d="M17 115L42 82L67 105L91 68L110 95" className="art-line" />
        <rect x="45" y="63" width="43" height="43" rx="3" className="art-stroke art-transparent" /><circle cx="37" cy="56" r="7" className="art-soft-fill" />
        <path d="M124 52H184M124 70H169M124 88H184M124 106H158" className="art-line" />
      </WindowFrame>;
    case "language":
      return <>
        <rect x="20" y="7" width="129" height="136" rx="8" className="art-paper art-stroke" /><rect x="34" y="24" width="66" height="20" rx="3" className="art-tint" />
        <path d="M34 59H132M34 73H120M34 87H132M34 101H111M34 115H121" className="art-line" />
        <rect x="111" y="84" width="88" height="43" rx="7" className="art-tint art-stroke" /><text x="124" y="111" className="art-math">Aa · 文</text>
      </>;
    default:
      return <>
        <path d="M101 27C74 10 35 13 14 23V127C41 117 76 120 101 137C126 120 161 117 188 127V23C167 13 128 10 101 27Z" className="art-paper art-stroke" />
        <path d="M101 27V137M29 43C48 38 74 39 87 46M29 62C48 57 74 58 87 65M29 81C48 76 74 77 87 84M29 100C48 95 74 96 87 103M116 46C135 39 158 38 174 43M116 65C135 58 158 57 174 62M116 84C135 77 158 76 174 81M116 103C135 96 158 95 174 100" className="art-line" />
      </>;
  }
}

export default function CourseCover({ item = {}, variant = "card", showBadge = true, className = "" }) {
  const topic = getCourseArtwork(item);
  const source = getCourseCoverUrl(item);
  const [failedSource, setFailedSource] = useState("");
  const hasImage = source && failedSource !== source;

  return (
    <span className={`course-artwork course-artwork--${variant} course-artwork--${topic.tone} ${className}`} data-course-art={topic.key} aria-hidden="true">
      {hasImage ? <img className="course-artwork-photo" src={source} alt="" loading="lazy" decoding="async" onError={() => setFailedSource(source)} /> :
        <svg className="course-artwork-canvas" viewBox="0 0 400 236" focusable="false">
          <path d="M157 0V236" className="art-divider" />
          {topic.symbol ? <image href={`${LOGO_ROOT}${topic.symbol}.svg`} x="22" y="63" width="48" height="48" /> :
            <path d="M23 74C39 66 57 66 70 73V111C54 104 39 104 23 111ZM70 73C85 66 101 66 114 74V111C99 104 84 104 70 111ZM70 74V111" className="art-paper art-stroke" />}
          <text x="22" y="145" className="art-topic-title">{topic.title}</text>
          <text x="22" y="169" className="art-topic-subtitle">{topic.subtitle}</text>
          <g transform="translate(176 55) scale(.96)"><TopicScene topic={topic} /></g>
        </svg>
      }
      {showBadge && <span className="course-artwork-badge">{item.type === "video" ? "视频课" : "图文课"}</span>}
      {item.type === "video" && <span className="course-artwork-play"><Play size={17} strokeWidth={1.75} />视频课程</span>}
    </span>
  );
}

export function CourseArtworkCredit() {
  return (
    <details className="course-artwork-credit">
      <summary>封面素材说明</summary>
      <p>
        学科图示由知度课堂绘制；技术标识来自{" "}
        <a href="https://github.com/devicons/devicon" target="_blank" rel="noreferrer">Devicon</a>
        ，按 <a href={`${LOGO_ROOT}LICENSE`} target="_blank" rel="noreferrer">MIT 许可证</a>提供。
        技术标识仅用于识别课程主题。
      </p>
    </details>
  );
}
