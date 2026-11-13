// Shared by cards and the course detail. Match the lesson subject before broad
// category names so a probability course is never illustrated as generic maths.
const TOPICS = [
  { match: /概率|统计|随机变量|方差/, key: "probability", title: "概率与统计", subtitle: "分布 · 期望 · 波动", scene: "probability", tone: "blue" },
  { match: /线性代数|矩阵|线性方程/, key: "algebra", title: "线性代数", subtitle: "向量 · 矩阵 · 变换", scene: "matrix", tone: "slate" },
  { match: /高等数学|微积分|极限|导数|积分/, key: "calculus", title: "高等数学", subtitle: "极限 · 变化 · 累积", scene: "calculus", tone: "blue" },
  { match: /英语|English|四六级/i, key: "english", title: "英语学习", subtitle: "阅读 · 表达 · 写作", scene: "language", tone: "teal" },
  { match: /政治|马克思|毛概|思修/, key: "politics", title: "思想与方法", subtitle: "实践 · 认识 · 分析", scene: "book", tone: "slate" },
  { match: /数据结构|算法/, key: "algorithms", title: "数据结构", subtitle: "组织数据，理解算法", scene: "tree", tone: "teal" },
  { match: /计算机组成|体系结构|存储层次/, key: "architecture", title: "计算机组成", subtitle: "运算 · 存储 · 指令", scene: "chip", tone: "slate" },
  { match: /操作系统|虚拟内存|进程同步/, key: "os", title: "操作系统", subtitle: "进程 · 内存 · 调度", scene: "terminal", tone: "slate", symbol: "linux" },
  { match: /计算机网络|TCP|HTTP|可靠传输/i, key: "network", title: "计算机网络", subtitle: "连接 · 协议 · 传输", scene: "network", tone: "blue" },
  { match: /Kubernetes|K8s/i, key: "kubernetes", title: "Kubernetes", subtitle: "编排服务，管理集群", scene: "network", tone: "blue", symbol: "kubernetes" },
  { match: /Docker|容器/i, key: "docker", title: "Docker", subtitle: "镜像 · 容器 · 持久化", scene: "containers", tone: "blue", symbol: "docker" },
  { match: /Spark|Shuffle/i, key: "spark", title: "Spark", subtitle: "分区 · 聚合 · 计算", scene: "pipeline", tone: "teal", symbol: "apachespark" },
  { match: /Hadoop|HDFS|分布式存储/i, key: "hadoop", title: "分布式存储", subtitle: "分块 · 副本 · 可靠性", scene: "database", tone: "teal", symbol: "hadoop" },
  { match: /数据仓库|维度|数仓/, key: "warehouse", title: "数据仓库", subtitle: "事实 · 维度 · 粒度", scene: "database", tone: "slate" },
  { match: /可视化|图表/, key: "analytics", title: "数据分析", subtitle: "从记录中发现关系", scene: "chart", tone: "blue" },
  { match: /SQL|数据库|索引|事务/i, key: "database", title: "数据库", subtitle: "查询 · 索引 · 事务", scene: "database", tone: "blue", symbol: "mysql" },
  { match: /Redis|缓存/i, key: "redis", title: "缓存与存储", subtitle: "读写 · 过期 · 一致性", scene: "database", tone: "slate", symbol: "redis" },
  { match: /TypeScript/i, key: "typescript", title: "TypeScript", subtitle: "类型 · 接口 · 异步", scene: "code", tone: "blue", symbol: "typescript", code: ["type Course = {", "  title: string", "  lessons: Lesson[]", "}"] },
  { match: /Vue/i, key: "vue", title: "Vue", subtitle: "组件 · 响应式 · 交互", scene: "code", tone: "teal", symbol: "vuejs", code: ["const course = ref()", "const lessons = [ ]", "watch(course, load)", "render(lessons)"] },
  { match: /React/i, key: "react", title: "React", subtitle: "组件 · 状态 · 交互", scene: "code", tone: "blue", symbol: "react", code: ["function Lesson() {", "  const [step] =", "    useState(1)", "  return <Course />", "}"] },
  { match: /Spring|SpringBoot/i, key: "spring", title: "Spring Boot", subtitle: "接口 · 服务 · 应用", scene: "code", tone: "teal", symbol: "spring", code: ["@RestController", "class CourseApi {", "  @GetMapping", "  Course find()", "}"] },
  { match: /Java(?!Script)|面向对象/i, key: "java", title: "Java", subtitle: "对象 · 并发 · 工程", scene: "code", tone: "slate", symbol: "java", code: ["class Course {", "  List<Step> steps;", "  void learn() {", "    steps.forEach(run);", "  }", "}"] },
  { match: /Python/i, key: "python", title: "Python", subtitle: "自动化 · 数据 · 实践", scene: "code", tone: "blue", symbol: "python", code: ["def learn(course):", "  for step in course:", "    result = run(step)", "    save(result)"] },
  { match: /C#|CSharp|WinForms|\.NET/i, key: "csharp", title: "C# / .NET", subtitle: "窗体 · 事件 · 应用", scene: "interface", tone: "slate", symbol: "csharp" },
  { match: /C\+\+/i, key: "cplusplus", title: "C++", subtitle: "对象 · 内存 · 性能", scene: "code", tone: "blue", symbol: "cplusplus", code: ["class Course {", " public:", "  void learn();", "};"] },
  { match: /软件测试|接口测试|测试用例|JUnit|Selenium/i, key: "testing", title: "软件测试", subtitle: "边界 · 权限 · 异常", scene: "testing", tone: "teal", symbol: "selenium" },
  { match: /计算机视觉|图像分类|卷积|OpenCV/i, key: "vision", title: "计算机视觉", subtitle: "像素 · 特征 · 识别", scene: "vision", tone: "teal", symbol: "opencv" },
  { match: /深度学习|神经网络|梯度/, key: "deep-learning", title: "深度学习", subtitle: "网络 · 梯度 · 优化", scene: "neural", tone: "slate", symbol: "pytorch" },
  { match: /自然语言|文本|NLP/i, key: "nlp", title: "语言与智能", subtitle: "文本 · 表示 · 分类", scene: "language", tone: "blue" },
  { match: /机器学习|人工智能|AI|回归/i, key: "machine-learning", title: "机器学习", subtitle: "建模 · 验证 · 泛化", scene: "neural", tone: "teal" },
  { match: /Linux|日志|命令行/i, key: "linux", title: "Linux", subtitle: "权限 · 进程 · 排查", scene: "terminal", tone: "slate", symbol: "linux" },
  { match: /Web安全|密码|认证|授权|TLS|安全/i, key: "security", title: "安全与信任", subtitle: "身份 · 权限 · 加密", scene: "security", tone: "blue" },
  { match: /物联网|MQTT|嵌入式|寄存器|中断/i, key: "embedded", title: "物联与嵌入式", subtitle: "设备 · 信号 · 控制", scene: "chip", tone: "teal", symbol: "c" },
  { match: /Git|版本控制/i, key: "git", title: "版本控制", subtitle: "提交 · 分支 · 协作", scene: "tree", tone: "slate", symbol: "git" },
  { match: /前端|JavaScript|HTML|CSS|网页/i, key: "web", title: "Web 开发", subtitle: "页面 · 交互 · 应用", scene: "interface", tone: "blue" }
];

const DEFAULT_ARTWORK = {
  key: "study", title: "课程学习", subtitle: "理解知识，动手实践", scene: "book", tone: "blue"
};

export function getCourseArtwork(item = {}) {
  // The title is the most precise source. A broad direction must not override it.
  const title = String(item.title || "");
  const context = `${item.directionName || ""} ${item.subject || ""} ${item.categoryName || ""}`;
  return TOPICS.find((topic) => topic.match.test(title)) ||
    TOPICS.find((topic) => topic.match.test(context)) || DEFAULT_ARTWORK;
}

export function getCourseCoverUrl(item = {}) {
  const candidate = String(item.coverUrl || item.thumbnailUrl || item.posterUrl || "").trim();
  // A fileUrl is a lesson/video asset, not a cover. Do not make browsers request
  // videos as images, and keep executable/data schemes out of an image source.
  if (/^https?:\/\//i.test(candidate) || /^\/(?!\/)/.test(candidate)) return candidate;
  return "";
}
