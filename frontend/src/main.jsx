import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { useRef } from "react";
import { flushSync } from "react-dom";
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  BookOpen,
  Brain,
  CheckCircle2,
  ChevronDown,
  CircleX,
  Clock3,
  Eye,
  FileText,
  GitBranch,
  GraduationCap,
  Heart,
  Maximize,
  MessageCircle,
  NotebookPen,
  PlayCircle,
  Plus,
  RefreshCcw,
  Route,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  TrendingUp,
  Upload,
  Users,
  Video,
  Wand2,
  Wrench,
  ZoomIn,
  ZoomOut
} from "lucide-react";
import HeaderNav from "./components/HeaderNav";
import LearningPathGraph from "./components/LearningPathGraph";
import CategoryCascader from "./components/CategoryCascader";
import SearchPanel from "./components/SearchPanel";
import CourseCard from "./components/CourseCard";
import CourseVideo from "./components/CourseVideo";
import CourseLearningPage from "./components/CourseLearningPage";
import request from "./utils/request";
import { clearAuthStorage, handleActionError } from "./utils/auth";
import { getCategories, getKnowledgeCategoryTree } from "./api/category";
import { addCourseNote, addCourseQuestion, deleteCourseNote, getCourseCommunity, getCourseDetail, getCourses, submitCourseReview } from "./api/course";
import { getCourseLearningProgress, getLessonPlayback, getResumeLearning } from "./api/learning";
import "./styles.css";

const api = request;

const subjects = ["Java", "C#", "Python", "数据库", "前端开发", "软件测试", "机器学习", "深度学习", "自然语言处理", "计算机视觉", "Hadoop", "Spark", "数据仓库", "数据可视化", "Linux", "Docker", "Kubernetes", "微服务", "Web安全", "密码学", "网络协议安全", "安全运维", "移动开发", "物联网", "嵌入式开发"];

const knowledgeCategories = [
  {
    label: "软件工程",
    value: "software_engineering",
    description: "面向软件项目开发、测试、协作和交付的综合学习大类。",
    children: [
      { label: "Java", value: "java", children: ["Java基础", "数据类型与变量", "流程控制", "面向对象", "集合框架", "异常处理", "IO流", "多线程", "JDBC", "Maven", "Spring", "Spring MVC", "Spring Boot", "MyBatis Plus"].map(categoryLeaf) },
      { label: "Python", value: "python", children: ["Python基础", "数据类型", "函数", "面向对象", "文件处理", "异常处理", "爬虫基础", "Flask", "Django", "数据分析", "自动化脚本"].map(categoryLeaf) },
      { label: "C#", value: "csharp", children: ["C#基础", "面向对象", "集合", "LINQ", "WinForms", "WPF", "ASP.NET", ".NET Core"].map(categoryLeaf) },
      { label: "前端开发", value: "frontend", children: ["HTML", "CSS", "JavaScript", "TypeScript", "DOM", "Ajax", "Axios", "Vue", "React", "Vite", "Element Plus"].map(categoryLeaf) },
      { label: "数据库", value: "database", children: ["SQL基础", "MySQL", "表设计", "索引", "事务", "锁机制", "Redis", "MongoDB", "数据库优化"].map(categoryLeaf) },
      { label: "软件测试", value: "software_testing", children: ["测试基础", "单元测试", "接口测试", "自动化测试", "性能测试", "测试报告"].map(categoryLeaf) }
    ]
  },
  {
    label: "人工智能",
    value: "ai",
    description: "从机器学习、深度学习到自然语言处理和视觉任务的学习大类。",
    children: [
      { label: "机器学习", value: "machine_learning", children: ["线性回归", "逻辑回归", "决策树", "随机森林", "SVM", "KNN", "模型评估"].map(categoryLeaf) },
      { label: "深度学习", value: "deep_learning", children: ["神经网络", "CNN", "RNN", "LSTM", "Transformer", "PyTorch"].map(categoryLeaf) },
      { label: "自然语言处理", value: "nlp", children: ["分词", "文本分类", "词向量", "BERT", "GPT", "大语言模型"].map(categoryLeaf) },
      { label: "计算机视觉", value: "computer_vision", children: ["图像处理", "图像分类", "目标检测", "图像分割", "OpenCV"].map(categoryLeaf) }
    ]
  },
  {
    label: "大数据",
    value: "big_data",
    description: "围绕数据采集、存储、计算、建模和可视化的学习大类。",
    children: [
      { label: "Hadoop", value: "hadoop", children: ["HDFS", "MapReduce", "YARN"].map(categoryLeaf) },
      { label: "Spark", value: "spark", children: ["Spark Core", "Spark SQL", "Spark Streaming", "MLlib"].map(categoryLeaf) },
      { label: "数据仓库", value: "data_warehouse", children: ["Hive", "数据建模", "ETL", "OLAP"].map(categoryLeaf) },
      { label: "数据可视化", value: "data_visualization", children: ["ECharts", "图表设计", "数据看板", "报表分析"].map(categoryLeaf) }
    ]
  },
  {
    label: "云计算",
    value: "cloud_computing",
    description: "面向 Linux、容器、集群编排和云原生服务治理的学习大类。",
    children: [
      { label: "Linux", value: "linux", children: ["常用命令", "文件权限", "Shell脚本", "进程管理"].map(categoryLeaf) },
      { label: "Docker", value: "docker", children: ["镜像", "容器", "Dockerfile", "Docker Compose", "容器部署"].map(categoryLeaf) },
      { label: "Kubernetes", value: "kubernetes", children: ["Pod", "Service", "Deployment", "ConfigMap", "Ingress"].map(categoryLeaf) },
      { label: "微服务", value: "microservices", children: ["服务注册", "服务发现", "网关", "配置中心", "分布式事务"].map(categoryLeaf) }
    ]
  },
  {
    label: "网络安全",
    value: "cyber_security",
    description: "覆盖 Web 安全、密码学、协议安全和安全运维的学习大类。",
    children: [
      { label: "Web安全", value: "web_security", children: ["SQL注入", "XSS", "CSRF", "文件上传漏洞", "权限绕过"].map(categoryLeaf) },
      { label: "密码学", value: "cryptography", children: ["对称加密", "非对称加密", "哈希算法", "数字签名"].map(categoryLeaf) },
      { label: "网络协议安全", value: "network_protocol_security", children: ["TCP/IP", "HTTP", "HTTPS", "DNS", "防火墙"].map(categoryLeaf) },
      { label: "安全运维", value: "security_ops", children: ["日志分析", "入侵检测", "漏洞扫描", "应急响应"].map(categoryLeaf) }
    ]
  },
  {
    label: "移动与物联网",
    value: "mobile_iot",
    description: "面向移动应用、设备通信、边缘计算和嵌入式开发的学习大类。",
    children: [
      { label: "移动开发", value: "mobile_development", children: ["Android基础", "Kotlin", "Flutter", "小程序开发", "移动端适配"].map(categoryLeaf) },
      { label: "物联网", value: "iot", children: ["传感器", "MQTT", "设备通信", "边缘计算", "智能硬件"].map(categoryLeaf) },
      { label: "嵌入式开发", value: "embedded", children: ["C语言", "单片机", "STM32", "串口通信", "RTOS"].map(categoryLeaf) }
    ]
  }
];

const graphSubtopicMap = {
  Java基础: ["变量与类型", "流程控制", "异常处理"],
  "数据类型与变量": ["基本类型", "引用类型", "类型转换", "变量作用域"],
  流程控制: ["条件分支", "循环结构", "跳转语句", "流程图设计"],
  面向对象: ["类与对象", "封装继承", "接口多态"],
  集合框架: ["List 与 Set", "Map 使用", "遍历与排序", "泛型集合"],
  IO流: ["字节流", "字符流", "缓冲流", "文件复制"],
  多线程: ["线程创建", "线程同步", "线程池", "并发安全"],
  JDBC: ["连接数据库", "PreparedStatement", "结果集处理", "事务提交"],
  "Spring Boot": ["项目结构", "配置文件", "Controller", "Service", "Mapper", "依赖注入", "REST API", "拦截器", "统一异常处理", "项目部署"],
  "C#基础": ["语法基础", "类型系统", "异常处理"],
  WinForms: ["窗体控件", "事件处理", "数据绑定"],
  "ASP.NET Core": ["路由中间件", "控制器", "依赖注入"],
  "Entity Framework": ["实体映射", "LINQ 查询", "迁移管理"],
  Python基础: ["数据类型", "函数模块", "异常处理"],
  数据分析: ["数据读取", "清洗转换", "统计分析"],
  网络爬虫: ["请求解析", "页面提取", "反爬处理"],
  Flask: ["路由视图", "模板渲染", "接口开发"],
  MySQL: ["表结构设计", "查询语句", "索引设计"],
  Redis: ["数据结构", "缓存策略", "过期淘汰"],
  SQL优化: ["执行计划", "慢查询", "索引调优"],
  数据库事务: ["事务隔离", "锁机制", "一致性"],
  "HTML/CSS": ["语义结构", "盒模型", "响应式布局"],
  JavaScript: ["作用域", "异步编程", "DOM 操作"],
  React: ["组件拆分", "状态管理", "接口请求"],
  Vue: ["模板指令", "响应式", "组件通信"],
  机器学习: ["特征工程", "模型训练", "效果评估"],
  深度学习: ["神经网络", "反向传播", "模型调参"],
  自然语言处理: ["文本预处理", "向量表示", "分类生成"],
  推荐系统: ["用户画像", "召回排序", "效果评估"],
  Dockerfile: ["基础镜像", "构建步骤", "多阶段构建", "镜像优化"],
  "SQL注入": ["注入原理", "联合查询", "盲注", "参数化防护"],
  MQTT: ["主题订阅", "消息发布", "QoS等级", "设备连接"]
};

function categoryLeaf(label) {
  return { label, value: label.toLowerCase().replace(/[^a-z0-9]+/g, "_") || label };
}

function graphSubtopics(label) {
  const base = graphSubtopicMap[label] || ["核心概念", "操作流程", "实战练习"];
  return [...base, "常见错误", "案例练习"];
}

function normalizeCategoryTree(items = []) {
  return items.map((item) => ({
    label: item.label || item.name || item.title,
    value: item.value || item.code || item.label || item.name || item.title,
    description: item.description,
    children: Array.isArray(item.children) ? normalizeCategoryTree(item.children) : []
  })).filter((item) => item.label);
}

function findCategoryPath(label, tree = knowledgeCategories, prefix = []) {
  for (const item of tree) {
    const path = [...prefix, item.label];
    if (item.label === label) return path;
    const found = findCategoryPath(label, item.children || [], path);
    if (found) return found;
  }
  return null;
}

function findCategoryByPath(path = [], tree = knowledgeCategories) {
  let list = tree;
  let current = null;
  for (const label of path) {
    current = list.find((item) => item.label === label);
    if (!current) return null;
    list = current.children || [];
  }
  return current;
}

function flattenCategoryMatches(item, text, path = []) {
  const currentPath = [...path, item.label];
  const ownMatch = item.label.toLowerCase().includes(text);
  const childMatches = (item.children || []).flatMap((child) => flattenCategoryMatches(child, text, currentPath));
  if (ownMatch && path.length) {
    return [{ title: item.label, path: currentPath, subjectRef: item.label }, ...childMatches];
  }
  return childMatches;
}

function catalogFromCategories(tree = knowledgeCategories, keyword = "") {
  const text = keyword.trim().toLowerCase();
  return tree.map((category) => {
    const children = category.children || [];
    const nodes = text
      ? (category.label.toLowerCase().includes(text)
          ? children.map((child) => ({ title: child.label, path: [category.label, child.label], subjectRef: child.label }))
          : flattenCategoryMatches(category, text))
      : children.map((child) => ({ title: child.label, path: [category.label, child.label], subjectRef: child.label }));
    return { name: category.label, description: category.description, path: [category.label], nodes };
  }).filter((item) => !text || item.name.toLowerCase().includes(text) || item.nodes.length);
}

function buildCategoryGraph(path = [], tree = knowledgeCategories) {
  const current = findCategoryByPath(path, tree) || tree[0] || knowledgeCategories[0];
  const resolvedPath = path.length ? path : [current.label];
  const children = resolvedPath.length >= 3
    ? graphSubtopics(current.label).map((label) => ({ label, children: [] }))
    : (current.children || []);
  const nodes = [{ id: `${current.label}-root`, label: current.label, type: "root", description: current.description }];
  const links = [];
  children.forEach((child, index) => {
    const id = `${current.label}-${index}`;
    const childPath = resolvedPath.length >= 3 ? resolvedPath : [...resolvedPath, child.label];
    const type = resolvedPath.length === 1 ? "direction" : resolvedPath.length === 2 ? "knowledge" : "point";
    nodes.push({
      id,
      label: child.label,
      type,
      level: resolvedPath.length,
      path: childPath,
      subjectRef: child.label,
      description: resolvedPath.length >= 3
        ? `${child.label} 是 ${current.label} 的详细学习点。`
        : `${child.label} 是 ${current.label} 下的${type === "direction" ? "学习方向" : "知识小类"}。`
    });
    links.push({ source: `${current.label}-root`, target: id, type: "包含" });
  });
  return { source: resolvedPath.length === 1 ? "category" : resolvedPath.length === 2 ? "direction" : "topic", nodes, links, path: resolvedPath };
}

const fallbackCatalog = catalogFromCategories(knowledgeCategories);

const seedResources = [
  { id: 1, title: "Java Spring Boot 接口实战", subject: "Java", type: "video", level: "入门", minutes: 48, teacher: "李老师", favoriteCount: 0 },
  { id: 2, title: "C# WinForms 小项目训练", subject: "C#", type: "material", level: "入门", minutes: 52, teacher: "王老师", favoriteCount: 0 },
  { id: 3, title: "Python Pandas 数据清洗", subject: "Python", type: "video", level: "入门", minutes: 55, teacher: "陈老师", favoriteCount: 0 },
  { id: 4, title: "MySQL 索引与事务优化", subject: "数据库", type: "material", level: "进阶", minutes: 63, teacher: "赵老师", favoriteCount: 0 },
  { id: 5, title: "React 组件化开发", subject: "前端开发", type: "video", level: "入门", minutes: 44, teacher: "周老师", favoriteCount: 0 },
  { id: 6, title: "机器学习模型入门", subject: "人工智能", type: "material", level: "进阶", minutes: 80, teacher: "林老师", favoriteCount: 0 },
  { id: 7, title: "软件测试用例设计", subject: "软件工程", type: "material", level: "入门", minutes: 42, teacher: "孙老师", favoriteCount: 0 },
  { id: 8, title: "Web 安全漏洞识别", subject: "网络安全", type: "video", level: "进阶", minutes: 68, teacher: "吴老师", favoriteCount: 0 },
  { id: 9, title: "Android 页面与数据交互", subject: "移动开发", type: "video", level: "入门", minutes: 58, teacher: "郑老师", favoriteCount: 0 },
  { id: 10, title: "Spark 数据处理实战", subject: "大数据", type: "material", level: "进阶", minutes: 76, teacher: "刘老师", favoriteCount: 0 },
  { id: 11, title: "Docker 到 Kubernetes 部署", subject: "云计算", type: "video", level: "进阶", minutes: 70, teacher: "黄老师", favoriteCount: 0 },
  { id: 12, title: "MQTT 物联网通信基础", subject: "物联网", type: "material", level: "入门", minutes: 46, teacher: "高老师", favoriteCount: 0 }
];

function buildResourceTextContent(item) {
  const title = item?.title || "课程资源";
  const subject = item?.subject || "通用学习";
  const description = item?.description || "本节课程用于建立基础理解，并通过练习把知识转化为可操作能力";
  return `【文本学习】${title}。${description}。本节课程适合在观看视频或下载资料之前先进行文字预习。学习时先明确课程目标：理解${subject}方向中的核心概念、常见术语、典型场景和基本工作流程；再把知识放到一个可执行的小任务中验证。阅读过程中请准备笔记，记录三个问题：这个知识点解决什么问题，它依赖哪些前置知识，实际项目中容易在哪一步出错。

第一阶段关注概念。不要急着记忆大量细节，先用自己的话解释课程标题中的关键词，并把它和已经学过的知识建立联系。如果遇到陌生名词，可以先写下猜测，再通过示例代码、流程图或操作截图验证。第二阶段关注流程。把课程内容拆成输入、处理、输出三个部分，说明每一步产生什么结果，为什么需要这样设计。第三阶段关注实践。选择一个最小练习，例如完成一个接口、编写一个查询、实现一个组件、整理一组数据或搭建一个简单环境。练习完成后要回看错误信息和调试过程，因为这些细节往往比最终结果更能反映掌握程度。

学习完成后，请用五句话总结：本节最重要的概念是什么，关键步骤有哪些，容易混淆的点在哪里，可以迁移到什么项目场景，下一步应该补强哪项能力。建议把完成度分为了解、会做、能解释、能迁移四级。如果只能跟着步骤完成，说明还停留在会做阶段；如果能够脱离教程复述原因并改造练习，才算真正掌握。本课程的文本学习内容也可以作为复习清单使用。再次复习时，请优先检查自己是否能独立画出流程、说出关键参数、解释一次失败案例，并把学习记录更新到平台中。这样系统后续生成学习路径、薄弱点分析和资源推荐时，才能更准确地反映你的真实状态。`;
}

const pagePaths = {
  auth: "/login",
  home: "/home",
  catalog: "/catalog",
  graph: "/graph",
  learning: "/learning",
  agents: "/agents",
  messages: "/messages",
  teacher: "/teacher",
  admin: "/admin",
  interests: "/interests",
  course: "/course",
  learn: "/learn"
};

const learnerPages = new Set(["home", "catalog", "graph", "learning", "agents", "messages", "course", "learn"]);
const teacherPages = new Set([...learnerPages, "teacher"]);

function pageFromPath(pathname) {
  const normalized = String(pathname || "");
  if (/^\/course\/\d+\/?$/.test(normalized)) return "course";
  if (/^\/learn\/\d+\/\d+\/?$/.test(normalized)) return "learn";
  const page = normalized.replace(/^\/+/, "") || "";
  if (page === "login") return "auth";
  return pagePaths[page] ? page : "";
}

function courseIdFromPath(pathname) {
  const match = String(pathname || "").match(/^\/(?:course|learn)\/(\d+)(?:\/\d+)?\/?$/);
  return match ? Number(match[1]) : null;
}

function lessonIdFromPath(pathname) {
  const match = String(pathname || "").match(/^\/learn\/\d+\/(\d+)\/?$/);
  return match ? Number(match[1]) : null;
}

function defaultPageForUser(savedUser, setupPending, routePage = "") {
  if (!savedUser) return "auth";
  if (!localStorage.getItem("learning_token")) return "auth";
    if (savedUser.role === "admin") return routePage === "course" ? "course" : "admin";
  if (savedUser.role === "teacher") return teacherPages.has(routePage) ? routePage : "home";
  if (setupPending) return "interests";
  return learnerPages.has(routePage) ? routePage : "home";
}

function readStoredJson(key, fallbackValue) {
  const rawValue = localStorage.getItem(key);
  if (!rawValue) return fallbackValue;

  try {
    return JSON.parse(rawValue) ?? fallbackValue;
  } catch {
    localStorage.removeItem(key);
    return fallbackValue;
  }
}

function readStoredUser() {
  const storedUser = readStoredJson("learning_user", null);
  return storedUser && typeof storedUser === "object" && !Array.isArray(storedUser) ? storedUser : null;
}

function readStoredPreferences() {
  const storedPreferences = readStoredJson("learning_preferences", []);
  return Array.isArray(storedPreferences) ? storedPreferences : [];
}

function App() {
  const [user, setUser] = useState(readStoredUser);
  const [page, setPageState] = useState(() => {
    const savedUser = readStoredUser();
    const setupPending = localStorage.getItem("learning_needs_interest_setup") === "1";
    return defaultPageForUser(savedUser, setupPending, pageFromPath(window.location.pathname));
  });
  const [preferences, setPreferences] = useState(readStoredPreferences);
  const [resources, setResources] = useState([]);
  const [resourceMeta, setResourceMeta] = useState({ page: 1, size: 12, total: 0, totalPages: 1 });
  const [categoryTree, setCategoryTree] = useState(knowledgeCategories);
  const [platformCategories, setPlatformCategories] = useState([]);
  const [catalogSubjects, setCatalogSubjects] = useState(fallbackCatalog);
  const [selectedSubject, setSelectedSubject] = useState(preferences[0] || "软件工程");
  const [selectedCourseId, setSelectedCourseId] = useState(() => courseIdFromPath(window.location.pathname));
  const [selectedLessonId, setSelectedLessonId] = useState(() => lessonIdFromPath(window.location.pathname));
  const [graphSelection, setGraphSelection] = useState(() => ({ view: new URLSearchParams(window.location.search).get("view") || "path", pathId: new URLSearchParams(window.location.search).get("pathId") }));
  const [needsInterestSetup, setNeedsInterestSetup] = useState(() => localStorage.getItem("learning_needs_interest_setup") === "1");
  const [authNotice, setAuthNotice] = useState("");
  const currentPathRef = useRef(window.location.pathname);
  const scrollPositionsRef = useRef(new Map());
  const scrollRestoreTokenRef = useRef(0);

  const scheduleScrollRestore = (top, retryAfterContentLoad = false) => {
    const token = scrollRestoreTokenRef.current + 1;
    scrollRestoreTokenRef.current = token;
    let attempts = 0;
    const applyPosition = () => {
      window.requestAnimationFrame(() => {
        if (scrollRestoreTokenRef.current !== token) return;
        window.scrollTo({ top, left: 0, behavior: "auto" });
        if (retryAfterContentLoad && Math.abs(window.scrollY - top) > 2 && attempts < 12) {
          attempts += 1;
          window.setTimeout(applyPosition, 60);
        }
      });
    };
    applyPosition();
  };

  const updateBrowserHistory = (nextPage, options = {}) => {
    const courseId = Number(options.courseId || selectedCourseId || 0) || null;
    const lessonId = Number(options.lessonId || selectedLessonId || 0) || null;
    let path = nextPage === "course" && courseId
      ? `/course/${courseId}`
      : nextPage === "learn" && courseId && lessonId
        ? `/learn/${courseId}/${lessonId}`
        : pagePaths[nextPage] || "/home";
    if (nextPage === "graph") {
      const selection = options.graphSelection || graphSelection;
      const query = new URLSearchParams({ view: selection.view });
      if (selection.pathId) query.set("pathId", selection.pathId);
      path += `?${query}`;
    }
    const state = { page: nextPage, ...(courseId ? { courseId } : {}), ...(lessonId ? { lessonId } : {}) };
    if (options.replace) {
      window.history.replaceState(state, "", path);
      return path;
    }
    if (window.location.pathname + window.location.search !== path || window.history.state?.page !== nextPage) {
      window.history.pushState(state, "", path);
    }
    return path;
  };

  const navigatePage = (nextPage, options = {}) => {
    const resolved = typeof nextPage === "function" ? nextPage(page) : nextPage;
    const nextCourseId = Number(options.courseId || selectedCourseId || 0) || null;
    const changingLessonInSameCourse = page === "learn"
      && resolved === "learn"
      && Number(nextCourseId) === Number(selectedCourseId);

    scrollPositionsRef.current.set(currentPathRef.current, window.scrollY);
    const nextGraphSelection = { view: options.view || "path", pathId: options.pathId || null };
    const nextPath = updateBrowserHistory(resolved, { ...options, ...(resolved === "graph" ? { graphSelection: nextGraphSelection } : {}) });
    currentPathRef.current = nextPath;

    const commitRoute = () => {
      if (["course", "learn"].includes(resolved) && options.courseId) setSelectedCourseId(Number(options.courseId));
      if (resolved === "learn" && options.lessonId) setSelectedLessonId(Number(options.lessonId));
      if (resolved === "graph") setGraphSelection(nextGraphSelection);
      setPageState(resolved);
    };
    const canTransition = typeof document.startViewTransition === "function"
      && !options.replace
      && !changingLessonInSameCourse;

    if (canTransition) {
      const transition = document.startViewTransition(() => flushSync(commitRoute));
      if (!changingLessonInSameCourse) {
        transition.updateCallbackDone.then(() => scheduleScrollRestore(0), () => scheduleScrollRestore(0));
      }
    } else {
      commitRoute();
      if (!changingLessonInSameCourse) scheduleScrollRestore(0);
    }
  };

  useEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration;
    window.history.scrollRestoration = "manual";
    currentPathRef.current = updateBrowserHistory(page, { replace: true, courseId: selectedCourseId, lessonId: selectedLessonId });
    const onPopState = (event) => {
      scrollPositionsRef.current.set(currentPathRef.current, window.scrollY);
      const routePage = event.state?.page || pageFromPath(window.location.pathname);
      const routeCourseId = Number(event.state?.courseId || courseIdFromPath(window.location.pathname) || 0) || null;
      const routeLessonId = Number(event.state?.lessonId || lessonIdFromPath(window.location.pathname) || 0) || null;
      const targetPath = window.location.pathname;
      const commitRoute = () => {
        if (routeCourseId) setSelectedCourseId(routeCourseId);
        if (routeLessonId) setSelectedLessonId(routeLessonId);
        if (routePage === "graph") setGraphSelection({ view: new URLSearchParams(window.location.search).get("view") || "path", pathId: new URLSearchParams(window.location.search).get("pathId") });
        setPageState(defaultPageForUser(user, needsInterestSetup, routePage));
      };
      currentPathRef.current = targetPath;
      if (typeof document.startViewTransition === "function") {
        const transition = document.startViewTransition(() => flushSync(commitRoute));
        const savedTop = scrollPositionsRef.current.get(targetPath) || 0;
        transition.updateCallbackDone.then(
          () => scheduleScrollRestore(savedTop, true),
          () => scheduleScrollRestore(savedTop, true)
        );
      } else {
        commitRoute();
        scheduleScrollRestore(scrollPositionsRef.current.get(targetPath) || 0, true);
      }
    };
    window.addEventListener("popstate", onPopState);
    return () => {
      window.removeEventListener("popstate", onPopState);
      window.history.scrollRestoration = previousScrollRestoration;
    };
  }, [user?.id, user?.role, needsInterestSetup]);

  useEffect(() => {
    const onUnauthorized = () => {
      const hadSession = Boolean(localStorage.getItem("learning_token") || localStorage.getItem("learning_user"));
      clearAuthStorage();
      setUser(null);
      setPreferences([]);
      setNeedsInterestSetup(false);
      if (hadSession) setAuthNotice("登录状态已失效，请重新登录。");
      setPageState("auth");
      window.history.replaceState({ page: "auth" }, "", "/login");
    };
    window.addEventListener("learning:unauthorized", onUnauthorized);
    return () => window.removeEventListener("learning:unauthorized", onUnauthorized);
  }, []);

  useEffect(() => {
    getKnowledgeCategoryTree().then((data) => {
      const tree = normalizeCategoryTree(Array.isArray(data.categories) ? data.categories : []);
      const resolvedTree = tree.length ? tree : knowledgeCategories;
      const items = catalogFromCategories(resolvedTree);
      setCategoryTree(resolvedTree);
      setCatalogSubjects(items);
      setSelectedSubject((current) => findCategoryPath(current, resolvedTree) ? current : items[0]?.name || "软件工程");
    }).catch(() => setCatalogSubjects(fallbackCatalog));
  }, []);

  useEffect(() => {
    getCategories().then((data) => setPlatformCategories(Array.isArray(data) ? data : [])).catch(() => setPlatformCategories([]));
  }, []);

  useEffect(() => {
    if (!user || !["student", "teacher"].includes(user.role)) return;
    if (needsInterestSetup) return;
    api("/preferences").then((data) => {
      const items = Array.isArray(data.subjects) ? data.subjects : [];
      localStorage.setItem("learning_preferences", JSON.stringify(items));
      setPreferences(items);
      setSelectedSubject(items[0] || "软件工程");
      setPageState((current) => current === "auth" ? "home" : current);
    }).catch(() => {
      localStorage.removeItem("learning_preferences");
      setPreferences([]);
      setPageState((current) => current === "auth" ? "home" : current);
    });
  }, [user?.id, needsInterestSetup]);

  const loginDone = async (nextUser, token, authMode, needsInterestSelection) => {
    setAuthNotice("");
    localStorage.setItem("learning_user", JSON.stringify(nextUser));
    if (token) localStorage.setItem("learning_token", token);
    setUser(nextUser);
    if (nextUser.role === "admin") {
      setNeedsInterestSetup(false);
      localStorage.removeItem("learning_needs_interest_setup");
      navigatePage("admin", { replace: true });
      return;
    }
    if (nextUser.role === "teacher") {
      setNeedsInterestSetup(false);
      localStorage.removeItem("learning_needs_interest_setup");
      navigatePage("home", { replace: true });
      return;
    }
    if (authMode === "register" || needsInterestSelection) {
      setNeedsInterestSetup(true);
      localStorage.setItem("learning_needs_interest_setup", "1");
      localStorage.removeItem("learning_preferences");
      setPreferences([]);
      setSelectedSubject("软件工程");
      navigatePage("interests", { replace: true });
      return;
    }
    setNeedsInterestSetup(false);
    localStorage.removeItem("learning_needs_interest_setup");
    try {
      const data = await api("/preferences");
      const items = Array.isArray(data.subjects) ? data.subjects : [];
      localStorage.setItem("learning_preferences", JSON.stringify(items));
      setPreferences(items);
      setSelectedSubject(items[0] || "软件工程");
      navigatePage("home", { replace: true });
    } catch {
      localStorage.removeItem("learning_preferences");
      setPreferences([]);
      navigatePage("home", { replace: true });
    }
  };

  const logout = () => {
    clearAuthStorage();
    setUser(null);
    setNeedsInterestSetup(false);
    setAuthNotice("");
    navigatePage("auth", { replace: true });
  };

  return (
    <div className="app-shell">
      {user && page !== "interests" && <HeaderNav user={user} page={page} onNavigate={navigatePage} onLogout={logout} />}
      {page === "auth" && <AuthPage onDone={loginDone} notice={authNotice} />}
      {page === "interests" && (
        <InterestSetupPage
          selected={preferences}
          catalogSubjects={catalogSubjects}
          logout={logout}
          onSave={(items) => {
            localStorage.setItem("learning_preferences", JSON.stringify(items));
            setPreferences(items);
            setSelectedSubject(items[0] || "软件工程");
            api("/preferences", { method: "POST", body: JSON.stringify({ subjects: items }) })
              .then(() => {
                setNeedsInterestSetup(false);
                localStorage.removeItem("learning_needs_interest_setup");
                navigatePage("home", { replace: true });
              })
              .catch((error) => handleActionError(error, "兴趣保存失败"));
          }}
        />
      )}
      {page === "home" && (
        <StudentHome
          resources={resources}
          setResources={setResources}
          resourceMeta={resourceMeta}
          setResourceMeta={setResourceMeta}
          preferences={preferences}
          catalogSubjects={catalogSubjects}
          categoryTree={categoryTree}
          platformCategories={platformCategories}
          setPage={navigatePage}
          setSelectedSubject={setSelectedSubject}
        />
      )}
      {page === "catalog" && (
        <StudentHome
          resources={resources}
          setResources={setResources}
          resourceMeta={resourceMeta}
          setResourceMeta={setResourceMeta}
          preferences={preferences}
          catalogSubjects={catalogSubjects}
          categoryTree={categoryTree}
          platformCategories={platformCategories}
          setPage={navigatePage}
          setSelectedSubject={setSelectedSubject}
        />
      )}
      {page === "graph" && (
        <LearningPathGraph key={user?.id} selection={graphSelection} setPage={navigatePage}>
        <KnowledgeGraph
          subject={selectedSubject}
          categoryTree={categoryTree}
          resources={resources}
          setSelectedSubject={setSelectedSubject}
          setPage={navigatePage}
        />
        </LearningPathGraph>
      )}
      {page === "course" && (
        <CourseDetailPage
          courseId={selectedCourseId}
          user={user}
          fallbackCourse={resources.find((item) => Number(item.id) === Number(selectedCourseId))}
          setResources={setResources}
          setPage={navigatePage}
          setSelectedSubject={setSelectedSubject}
        />
      )}
      {page === "learn" && (
        <CourseLearningPage
          courseId={selectedCourseId}
          lessonId={selectedLessonId}
          setPage={navigatePage}
        />
      )}
      {page === "learning" && <MyLearningPage setPage={navigatePage} setSelectedSubject={setSelectedSubject} />}
      {page === "agents" && <AgentsPage preferences={preferences} setPage={navigatePage} />}
      {page === "messages" && <MessagesPage user={user} setPage={navigatePage} />}
      {page === "teacher" && <TeacherDashboard resources={resources} setResources={setResources} setResourceMeta={setResourceMeta} />}
      {page === "admin" && <AdminDashboard setPage={navigatePage} />}
    </div>
  );
}

function AuthPage({ onDone, notice }) {
  const [mode, setMode] = useState("login");
  const [role, setRole] = useState("student");
  const [form, setForm] = useState({ name: "", email: "", password: "", teacherRegistrationCode: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError("");
    setSubmitting(true);
    try {
      const path = mode === "login" ? "/auth/login" : "/auth/register";
      const payload = mode === "login"
        ? { email: form.email, password: form.password }
        : {
            name: form.name,
            email: form.email,
            password: form.password,
            role,
            ...(role === "teacher" ? { teacherRegistrationCode: form.teacherRegistrationCode.trim() } : {})
          };
      const data = await api(path, { method: "POST", body: JSON.stringify(payload) });
      await onDone(data.user, data.token, mode, data.needsInterestSelection);
    } catch (err) {
      setError(err?.message || "登录请求失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-hero">
        <div className="hero-copy">
          <span className="eyebrow">ZHI DU LEARNING</span>
          <h1>让每一次学习<br />都有清晰的方向</h1>
          <p>从课程体系到知识图谱，把分散的内容整理成一条看得见、走得通的学习路径。</p>
          <div className="auth-proof" aria-label="平台能力">
            <span><b>6</b> 大学习领域</span>
            <span><b>100+</b> 知识节点</span>
            <span><b>持续</b> 记录进度</span>
          </div>
        </div>
      </section>
      <section className="auth-panel">
        <div className="auth-panel-head">
          <span className="brand-mark"><GraduationCap size={22} /></span>
          <div>
            <span className="eyebrow">欢迎回来</span>
            <h2>{mode === "login" ? "登录学习空间" : "创建你的学习空间"}</h2>
            <p>{mode === "login" ? "继续上次的学习进度。" : "只需一分钟，即可开始规划课程。"}</p>
          </div>
        </div>
        <div className="segmented">
          <button disabled={submitting} className={mode === "login" ? "active" : ""} onClick={() => { setMode("login"); setError(""); }}>登录</button>
          <button disabled={submitting} className={mode === "register" ? "active" : ""} onClick={() => { setMode("register"); setError(""); }}>注册</button>
        </div>
        <form onSubmit={submit}>
          {mode === "register" && <label className="field-label"><span>姓名</span><input placeholder="请输入姓名" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>}
          <label className="field-label"><span>邮箱</span><input type="email" placeholder="name@example.com" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></label>
          <label className="field-label"><span>密码</span><input type="password" placeholder="请输入密码" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></label>
          {mode === "register" && (
            <div className="role-field">
              <span>使用身份</span>
              <div className="role-picker">
                <button type="button" disabled={submitting} className={role === "student" ? "active" : ""} onClick={() => setRole("student")}>学生</button>
                <button type="button" disabled={submitting} className={role === "teacher" ? "active" : ""} onClick={() => setRole("teacher")}>教师</button>
              </div>
            </div>
          )}
          {mode === "register" && role === "teacher" && (
            <label className="field-label">
              <span>教师注册码</span>
              <input
                type="password"
                autoComplete="off"
                placeholder="请输入教师注册码"
                value={form.teacherRegistrationCode}
                onChange={(event) => setForm({ ...form, teacherRegistrationCode: event.target.value })}
                required
              />
            </label>
          )}
          {(error || notice) && <p className="error" role="alert">{error || notice}</p>}
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? "正在提交..." : mode === "login" ? "登录系统" : "创建账号"}
            <ArrowRight size={18} />
          </button>
        </form>
      </section>
    </main>
  );
}

function InterestSetupPage({ selected, catalogSubjects, onSave, logout }) {
  const [items, setItems] = useState(() => [...new Set(Array.isArray(selected) ? selected : [])].slice(0, 3));
  const catalog = catalogSubjects.length ? catalogSubjects : fallbackCatalog;
  const toggle = (subject) => setItems((prev) => {
    if (prev.includes(subject)) return prev.filter((item) => item !== subject);
    return prev.length >= 3 ? prev : [...prev, subject];
  });
  return (
    <main className="preference-page-react">
      <div className="preference-top-react">
        <div className="page-title">
          <span className="eyebrow">首次兴趣选择</span>
          <h2>选择你感兴趣的学习方向</h2>
          <p>选择一到三个学习方向，首页会优先整理相关课程与知识节点。之后也可以随时调整。</p>
        </div>
        <button className="preference-logout-react" onClick={logout}>退出登录</button>
      </div>
      <div className="subject-grid">
        {catalog.map((subject) => (
          <button className={items.includes(subject.name) ? "subject-card active" : "subject-card"} key={subject.name} onClick={() => toggle(subject.name)}>
            <CheckCircle2 size={22} />
            <strong>{subject.name}</strong>
            <span>{(subject.nodes || []).slice(0, 3).map((node) => node.title).join(" / ")}</span>
          </button>
        ))}
      </div>
      <button className="primary-btn floating-save-react" disabled={!items.length} onClick={() => onSave(items)}>保存选择，进入学习空间</button>
    </main>
  );
}

function StudentHome({ resources, setResources, resourceMeta, setResourceMeta, preferences, catalogSubjects, categoryTree, platformCategories, setPage, setSelectedSubject }) {
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState({ categoryId: "", directionId: "", type: "" });
  const [confirmedSelection, setConfirmedSelection] = useState({ query: "", categoryId: "", directionId: "", type: "" });
  const [filterPath, setFilterPath] = useState([]);
  const [loadingResources, setLoadingResources] = useState(false);
  const [resourceError, setResourceError] = useState("");
  const resourceRequestRef = useRef(0);
  const searchTimerRef = useRef(null);
  const submittedQueryRef = useRef("");
  const lastRequestRef = useRef({ page: 1, selection: confirmedSelection });
  const normalizedQuery = query.trim().toLowerCase();
  const tree = categoryTree?.length ? categoryTree : knowledgeCategories;
  const visibleCatalog = catalogFromCategories(tree, query).map((item) => {
    const stats = (platformCategories || []).find((category) => category.name === item.name) || {};
    return { ...item, ...stats, name: item.name, nodes: item.nodes };
  });
  const matchedSuggestionGroups = normalizedQuery ? visibleCatalog.map((item) => ({ ...item, nodes: (item.nodes || []).slice(0, 8) })).filter((item) => item.nodes.length) : [];
  const updateResource = (id, updates) => {
    setResources((items) => items.map((item) => item.id === id ? { ...item, ...updates } : item));
  };
  const displayResources = useMemo(() => {
    const seen = new Set();
    return resources.filter((item) => {
      const key = String(item.id);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [resources]);
  const loadResources = async (page = 1, selection = confirmedSelection) => {
    clearTimeout(searchTimerRef.current);
    const requestId = ++resourceRequestRef.current;
    submittedQueryRef.current = selection.query;
    lastRequestRef.current = { page, selection };
    setLoadingResources(true);
    setResourceError("");
    try {
      const data = await getCourses({
        pageNum: page,
        pageSize: resourceMeta?.size || 12,
        keyword: selection.query,
        categoryId: selection.categoryId,
        directionId: selection.directionId,
        type: selection.type
      });
      if (requestId !== resourceRequestRef.current) return;
      setResources(data.list || []);
      setResourceMeta({
        page: data.pageNum || page,
        size: data.pageSize || 12,
        total: data.total || 0,
        totalPages: data.totalPages || 1
      });
    } catch (error) {
      if (requestId === resourceRequestRef.current) setResourceError(error?.message || "课程加载失败，请重试");
    } finally {
      if (requestId === resourceRequestRef.current) setLoadingResources(false);
    }
  };
  useEffect(() => {
    loadResources(1);
    return () => {
      resourceRequestRef.current += 1;
      clearTimeout(searchTimerRef.current);
    };
  }, []);
  const confirmSelection = (event) => {
    event.preventDefault();
    const selection = { query: query.trim(), ...filters };
    setConfirmedSelection(selection);
    loadResources(1, selection);
    document.getElementById("featured-courses")?.scrollIntoView({ behavior: "smooth" });
  };
  const changeFilterPath = (path) => {
    if (path[2]) setQuery(path[2]);
    else if (query === filterPath[2]) setQuery("");
    setFilterPath(path);
    const category = (platformCategories || []).find((item) => item.name === path[0]);
    const direction = (category?.directions || []).find((item) => item.name === path[1]);
    setFilters((current) => ({
      ...current,
      categoryId: category?.id || "",
      directionId: direction?.id || ""
    }));
  };
  useEffect(() => {
    if (query.trim() === submittedQueryRef.current) return undefined;
    searchTimerRef.current = setTimeout(() => {
      const selection = { query: query.trim(), ...filters };
      setConfirmedSelection(selection);
      loadResources(1, selection);
    }, 450);
    return () => clearTimeout(searchTimerRef.current);
  }, [query]);
  const selectCourseTab = (label) => {
    const selection = { query: label, categoryId: "", directionId: "", type: "" };
    setQuery(label);
    setFilterPath([]);
    setFilters({ categoryId: "", directionId: "", type: "" });
    setConfirmedSelection(selection);
    loadResources(1, selection);
  };
  const openCatalog = (subject, node) => {
    const nodeTitle = typeof node === "string" ? node : node?.title;
    const targetPath = typeof node === "object" && Array.isArray(node.path)
      ? node.path
      : findCategoryPath(nodeTitle || subject, tree) || [subject];
    const targetSubject = targetPath[targetPath.length - 1] || subject;
    setSelectedSubject(targetSubject);
    if (nodeTitle && targetSubject === subject) {
      api("/learning/records", { method: "POST", body: JSON.stringify({ subject: targetSubject, knowledgeNode: nodeTitle, progress: 10 }) }).catch(() => {});
    }
    setPage("graph", { view: "catalog" });
  };

  const spotlightCategories = visibleCatalog.slice(0, 6);
  const heroSubject = preferences[0] || visibleCatalog[0]?.name || "软件工程";
  const heroCategory = visibleCatalog.find((item) => item.name === heroSubject) || visibleCatalog[0];
  const graphPreviewLabels = (heroCategory?.nodes || []).slice(0, 5).map((item) => item.title);

  return (
    <main className="content studio-home">
      <section className="studio-hero" aria-label="知度课堂学习主张">
        <div className="studio-hero-copy">
          <span className="studio-kicker">KNOWLEDGE, CONNECTED.</span>
          <h1>让知识形成结构，<br />让成长<span>看得见。</span></h1>
          <p>知度课堂把课程、章节与知识关系放进同一个学习空间。你不只是在观看内容，而是在建立一套可以迁移的能力体系。</p>
          <form className="studio-search" onSubmit={confirmSelection}>
            <Search size={19} />
            <input aria-label="搜索课程或知识点" placeholder="搜索课程、技术方向或知识点" value={query} onChange={(event) => setQuery(event.target.value)} />
            <button type="submit" disabled={loadingResources}>{loadingResources ? "搜索中" : "开始探索"}<ArrowRight size={16} /></button>
          </form>
          <div className="studio-hero-actions">
            <button onClick={() => openCatalog(heroSubject)}>打开我的智慧图谱<GitBranch size={17} /></button>
            <button onClick={() => document.getElementById("featured-courses")?.scrollIntoView({ behavior: "smooth" })}>查看课程目录<ChevronDown size={17} /></button>
          </div>
          <div className="studio-metrics">
            <div><strong>{platformCategories.length || visibleCatalog.length}</strong><span>学习领域</span></div>
            <div><strong>{resourceMeta?.total ?? displayResources.length}</strong><span>系统课程</span></div>
            <div><strong>100+</strong><span>知识节点</span></div>
          </div>
        </div>

        <aside className="studio-map-card">
          <div className="studio-map-head">
            <div><span>YOUR LEARNING MAP</span><strong>{heroSubject} · 能力结构</strong></div>
            <button onClick={() => openCatalog(heroSubject)}>展开图谱<ArrowRight size={14} /></button>
          </div>
          <div className="studio-map-stage">
            <span className="studio-map-ring ring-large" />
            <span className="studio-map-ring ring-small" />
            <button className="studio-map-node map-root" onClick={() => openCatalog(heroSubject)}><Brain size={24} /><strong>{heroSubject}</strong><small>当前主线</small></button>
            {graphPreviewLabels.map((label, index) => (
              <button className={`studio-map-node map-node-${index + 1}`} key={label} onClick={() => openCatalog(heroSubject, { title: label })}>{label}</button>
            ))}
          </div>
          <div className="studio-map-foot">
            <span><i />正在学习</span>
            <span>{graphPreviewLabels.length} 个关联方向</span>
          </div>
        </aside>
      </section>

      <nav className="studio-discipline-ribbon" aria-label="热门学习领域">
        {spotlightCategories.map((category, index) => (
          <button key={category.name} onClick={() => openCatalog(category.name)}>
            <span>0{index + 1}</span>
            <strong>{category.name}</strong>
            <small>{(category.nodes || []).slice(0, 2).map((node) => node.title).join(" · ")}</small>
            <ArrowRight size={15} />
          </button>
        ))}
      </nav>

      <section className="studio-course-section" id="featured-courses">
        <div className="studio-section-head">
          <div>
            <span className="studio-kicker">CURATED COURSES</span>
            <h2>从一门好课，进入一个完整领域</h2>
          </div>
          <details className="studio-filter-panel">
            <summary>精确筛选课程<ChevronDown size={15} /></summary>
            <SearchPanel
              query={query}
              onQueryChange={setQuery}
              tree={tree}
              filterPath={filterPath}
              onFilterPathChange={changeFilterPath}
              type={filters.type}
              onTypeChange={(type) => setFilters((current) => ({ ...current, type }))}
              onSubmit={confirmSelection}
              loading={loadingResources}
              suggestions={matchedSuggestionGroups}
              onOpenCatalog={(subject, node) => {
                if (node?.title) setQuery(node.title);
                openCatalog(subject, node);
              }}
            />
          </details>
        </div>
        <div className="studio-course-tabs" role="list" aria-label="课程筛选">
          {["本周精选", "人工智能", "Java", "前端开发", "云计算"].map((label, index) => (
            <button className={confirmedSelection.query === (index ? label : "") ? "active" : ""} key={label} onClick={() => selectCourseTab(index ? label : "")}>{label}</button>
          ))}
          <span>{loadingResources ? "正在更新" : `${resourceMeta?.total ?? displayResources.length} 门课程可学`}</span>
        </div>
        {resourceError && <div className="inline-error-state" role="alert"><span>{resourceError}</span><button type="button" disabled={loadingResources} onClick={() => loadResources(lastRequestRef.current.page, lastRequestRef.current.selection)}>重新加载</button></div>}
        <section className="resource-grid studio-course-grid">
          {displayResources.map((item) => (
            <CourseCard
              item={item}
              key={item.id}
              onUpdate={updateResource}
              onOpenCourse={(course) => setPage("course", { courseId: course.id })}
              onOpenGraph={(direction) => {
                setSelectedSubject(direction);
                setPage("graph", { view: "catalog" });
              }}
            />
          ))}
          {!displayResources.length && !loadingResources && !resourceError && <p className="empty-state-react">暂无匹配课程，可以换个关键词或让教师上传相关资源。</p>}
        </section>
        <div className="pager-row">
          <button disabled={loadingResources || (resourceMeta?.page || 1) <= 1} onClick={() => loadResources((resourceMeta?.page || 1) - 1)}>上一页</button>
          <span>{resourceMeta?.page || 1} / {resourceMeta?.totalPages || 1}</span>
          <button disabled={loadingResources || (resourceMeta?.page || 1) >= (resourceMeta?.totalPages || 1)} onClick={() => loadResources((resourceMeta?.page || 1) + 1)}>下一页</button>
        </div>
      </section>

      <section className="studio-method">
        <div className="studio-method-intro">
          <span className="studio-kicker">A BETTER WAY TO LEARN</span>
          <h2>课程结束，能力才刚刚开始。</h2>
          <p>用结构化课程建立基础，用图谱确认关系，再用学习记录和 AI 分析形成长期反馈。</p>
        </div>
        <div className="studio-method-grid">
          <article><span>01</span><GitBranch size={23} /><h3>知识关系可视化</h3><p>看见前置知识、关联章节和能力边界，不再盲目选课。</p></article>
          <article><span>02</span><Brain size={23} /><h3>AI 学习分析</h3><p>基于真实进度发现薄弱点，生成下一阶段的学习建议。</p></article>
          <article><span>03</span><Route size={23} /><h3>持续成长档案</h3><p>课程、笔记、提问与完成度共同沉淀为个人学习资产。</p></article>
        </div>
      </section>
    </main>
  );
}

function formatCommunityDate(value) {
  if (!value) return "刚刚";
  return String(value).replace("T", " ").slice(0, 16);
}

function lessonMinutes(lesson) {
  return Math.max(0, Math.ceil(Number(lesson?.durationSeconds || 0) / 60));
}

function CourseDetailPage({ courseId, user, fallbackCourse, setResources, setPage, setSelectedSubject }) {
  const [course, setCourse] = useState(fallbackCourse || null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detailReload, setDetailReload] = useState(0);
  const [activeTab, setActiveTab] = useState("chapters");
  const [community, setCommunity] = useState({ questions: [], notes: [], reviews: [], replies: [], reviewSummary: {} });
  const [questionText, setQuestionText] = useState("");
  const [noteDraft, setNoteDraft] = useState({ lessonTitle: "课程总览", content: "" });
  const [reviewDraft, setReviewDraft] = useState({ rating: 5, content: "" });
  const [busyAction, setBusyAction] = useState("");
  const [notice, setNotice] = useState("");
  const [learningPlayer, setLearningPlayer] = useState({
    open: false,
    lesson: null,
    status: "idle",
    videoUrl: null,
    assetId: null,
    reasonCode: null
  });
  const learningVideoRef = useRef(null);
  const playbackAbortRef = useRef(null);
  const playbackRequestRef = useRef(0);

  const loadCommunity = async () => {
    if (!courseId) return;
    try {
      const data = await getCourseCommunity(courseId);
      setCommunity({
        questions: Array.isArray(data.questions) ? data.questions : [],
        notes: Array.isArray(data.notes) ? data.notes : [],
        reviews: Array.isArray(data.reviews) ? data.reviews : [],
        replies: Array.isArray(data.replies) ? data.replies : [],
        reviewSummary: data.reviewSummary || {}
      });
    } catch {
      setCommunity({ questions: [], notes: [], reviews: [], replies: [], reviewSummary: {} });
    }
  };

  useEffect(() => {
    if (!courseId) {
      setError("没有找到要打开的课程。");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    let cancelled = false;
    Promise.allSettled([getCourseDetail(courseId), getCourseCommunity(courseId)]).then(([detailResult, communityResult]) => {
      if (cancelled) return;
      if (detailResult.status === "fulfilled") {
        setCourse(detailResult.value);
      } else {
        setError(detailResult.reason?.message || "课程详情加载失败");
      }
      if (communityResult.status === "fulfilled") {
        const data = communityResult.value || {};
        setCommunity({
          questions: Array.isArray(data.questions) ? data.questions : [],
          notes: Array.isArray(data.notes) ? data.notes : [],
          reviews: Array.isArray(data.reviews) ? data.reviews : [],
          replies: Array.isArray(data.replies) ? data.replies : [],
          reviewSummary: data.reviewSummary || {}
        });
      }
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [courseId, detailReload]);

  const outline = course?.outline || {};
  const chapters = Array.isArray(outline.chapters) ? outline.chapters : [];
  const knowledgeNodes = Array.isArray(course?.knowledgeNodes) && course.knowledgeNodes.length
    ? course.knowledgeNodes.slice(0, 8)
    : chapters.map((chapter) => ({ name: chapter.title.replace(/^第\s*\d+\s*章\s*·\s*/, "") }));
  const progress = Math.max(0, Math.min(Number(course?.progress || 0), 100));
  const totalLessons = chapters.reduce((sum, chapter) => sum + chapter.lessons.length, 0);
  const averageRating = Number(community.reviewSummary?.averageRating || 0);
  const adminPreview = user?.role === "admin";
  const courseStudyText = learningPlayer.lesson?.contentText || course?.content || buildResourceTextContent(course || fallbackCourse || {});
  const activeLessonType = learningPlayer.lesson?.lessonType || null;
  const activeLessonResourceUrl = activeLessonType === "video"
    ? learningPlayer.videoUrl
    : learningPlayer.lesson?.playbackUrl || null;

  const clearCurrentVideo = () => {
    const video = learningVideoRef.current;
    if (!video) return;
    video.pause();
    video.removeAttribute("src");
    video.load();
  };

  const cancelPlaybackRequest = () => {
    playbackRequestRef.current += 1;
    playbackAbortRef.current?.abort();
    playbackAbortRef.current = null;
  };

  const closeLearning = () => {
    cancelPlaybackRequest();
    clearCurrentVideo();
    setLearningPlayer({ open: false, lesson: null, status: "idle", videoUrl: null, assetId: null, reasonCode: null });
  };

  useEffect(() => () => {
    playbackAbortRef.current?.abort();
    clearCurrentVideo();
  }, []);

  useEffect(() => {
    if (!learningPlayer.open) return;
    const frame = requestAnimationFrame(() => {
      document.getElementById("course-player")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => cancelAnimationFrame(frame);
  }, [learningPlayer.open, learningPlayer.lesson?.title]);

  const openLearning = async (lesson = null) => {
    if (!course?.id) return;
    const selectedLesson = lesson || chapters[0]?.lessons?.[0];
    if (!selectedLesson) {
      setNotice("这门课程还没有可学习的真实课时，请等待教师完善目录。");
      return;
    }
    if (!adminPreview) {
      setPage("learn", { courseId: course.id, lessonId: selectedLesson.id });
      return;
    }
    cancelPlaybackRequest();
    clearCurrentVideo();
    const requestId = playbackRequestRef.current;
    const controller = new AbortController();
    playbackAbortRef.current = controller;
    setNotice("");
    setActiveTab("chapters");
    setLearningPlayer({
      open: true,
      lesson: selectedLesson,
      status: selectedLesson.lessonType === "video" ? "loading" : "ready",
      videoUrl: null,
      assetId: null,
      reasonCode: null
    });
    if (selectedLesson.lessonType !== "video") {
      playbackAbortRef.current = null;
      setNotice("课程学习区已打开，可以开始学习本课时内容。");
      return;
    }
    try {
      const playback = await getLessonPlayback(course.id, selectedLesson.id, { signal: controller.signal });
      if (requestId !== playbackRequestRef.current || Number(playback.lessonId) !== Number(selectedLesson.id)) return;
      playbackAbortRef.current = null;
      if (playback.playable && playback.videoUrl) {
        setLearningPlayer({ open: true, lesson: selectedLesson, status: "playable", videoUrl: playback.videoUrl, assetId: playback.assetId, reasonCode: null });
        setNotice("本课时独立视频已打开。");
        return;
      }
      const status = playback.reasonCode === "ASSET_NOT_READY" ? "processing" : "unavailable";
      setLearningPlayer({ open: true, lesson: selectedLesson, status, videoUrl: null, assetId: null, reasonCode: playback.reasonCode });
      setNotice(playback.reasonCode === "ASSET_NOT_READY" ? "视频正在处理中。" : "本课时暂无视频。");
    } catch (playbackError) {
      if (playbackError?.code === "ERR_CANCELED" || requestId !== playbackRequestRef.current) return;
      playbackAbortRef.current = null;
      clearCurrentVideo();
      setLearningPlayer({ open: true, lesson: selectedLesson, status: "error", videoUrl: null, assetId: null, reasonCode: "PLAYBACK_REQUEST_FAILED" });
      setNotice(playbackError?.message || "课时视频加载失败，请重试。");
    }
  };

  const startLearning = async () => {
    if (!course?.id || loading || busyAction) return;
    setBusyAction("learning");
    try {
      if (adminPreview) {
        await openLearning(null);
        return;
      }
      const courseProgress = await getCourseLearningProgress(course.id);
      const allLessons = chapters.flatMap((chapter) => chapter.lessons || []);
      const resumeLesson = allLessons.find((item) => Number(item.id) === Number(courseProgress.lastLessonId));
      await openLearning(resumeLesson || null);
    } catch (actionError) {
      handleActionError(actionError, "打开学习页失败");
    } finally {
      setBusyAction("");
    }
  };

  const completeLearning = async () => {
    if (!course?.id || busyAction) return;
    if (adminPreview) {
      setNotice("管理员预览不会写入学习进度。");
      return;
    }
    setBusyAction("complete-learning");
    try {
      await api("/learning/records", { method: "POST", body: JSON.stringify({ resourceId: course.id, progress: 100 }) });
      setCourse((current) => ({ ...current, progress: 100 }));
      setResources((items) => items.map((item) => Number(item.id) === Number(course.id) ? { ...item, progress: 100 } : item));
      setNotice("本课程已标记完成，学习进度更新为 100%。");
    } catch (actionError) {
      handleActionError(actionError, "课程完成状态保存失败");
    } finally {
      setBusyAction("");
    }
  };

  const submitQuestion = async (event) => {
    event.preventDefault();
    const content = questionText.trim();
    if (!content || busyAction) return;
    setBusyAction("question");
    try {
      await addCourseQuestion(courseId, content);
      setQuestionText("");
      await loadCommunity();
    } catch (actionError) {
      handleActionError(actionError, "问题发布失败");
    } finally {
      setBusyAction("");
    }
  };

  const submitNote = async (event) => {
    event.preventDefault();
    if (!noteDraft.content.trim() || busyAction) return;
    setBusyAction("note");
    try {
      await addCourseNote(courseId, noteDraft);
      setNoteDraft((current) => ({ ...current, content: "" }));
      await loadCommunity();
    } catch (actionError) {
      handleActionError(actionError, "笔记保存失败");
    } finally {
      setBusyAction("");
    }
  };

  const removeNote = async (noteId) => {
    if (busyAction) return;
    setBusyAction(`note-${noteId}`);
    try {
      await deleteCourseNote(courseId, noteId);
      await loadCommunity();
    } catch (actionError) {
      handleActionError(actionError, "笔记删除失败");
    } finally {
      setBusyAction("");
    }
  };

  const submitReview = async (event) => {
    event.preventDefault();
    if (!reviewDraft.content.trim() || busyAction) return;
    setBusyAction("review");
    try {
      await submitCourseReview(courseId, reviewDraft);
      setReviewDraft({ rating: 5, content: "" });
      await loadCommunity();
    } catch (actionError) {
      handleActionError(actionError, "评价提交失败");
    } finally {
      setBusyAction("");
    }
  };

  const openRelatedGraph = () => {
    const subject = course?.directionName || course?.subject || "软件工程";
    setSelectedSubject(subject);
    setPage("graph", { view: "catalog" });
  };

  if (loading) {
    return <main className="course-detail-loading"><span className="studio-kicker">LOADING COURSE</span><h2>正在建立课程学习空间...</h2></main>;
  }

  if (error || !course) {
    return <main className="course-detail-loading"><h2 role="alert">{error || "课程不存在或已下架"}</h2><button onClick={() => setDetailReload((value) => value + 1)}>重新加载</button><button onClick={() => setPage("home")}>返回首页</button></main>;
  }

  const tabs = [
    { id: "chapters", label: "章节", count: totalLessons, icon: BookOpen },
    { id: "questions", label: "问答", count: community.questions.length, icon: MessageCircle },
    { id: "notes", label: "笔记", count: community.notes.length, icon: NotebookPen },
    { id: "reviews", label: "评价", count: community.reviews.length, icon: Star },
    { id: "graph", label: "知识图谱", count: knowledgeNodes.length, icon: GitBranch }
  ];

  return (
    <main className="course-detail-page">
      <section className="course-detail-hero">
        <div className="course-detail-hero-inner">
          <button className="course-back-button" onClick={() => setPage("home")}><ArrowRight size={15} />返回课程大厅</button>
          <div className="course-hero-layout">
            <div className="course-hero-copy">
              <div className="course-breadcrumb"><span>{course.categoryName || "课程"}</span><i />{course.directionName || course.subject}</div>
              <span className="course-status-badge">系统课程 · 持续更新</span>
              <h1>{course.title}</h1>
              <p>{course.description || `围绕 ${course.subject} 的核心能力组织章节、练习与知识关系，帮助你从理解走向独立实践。`}</p>
              <div className="course-hero-meta">
                <span><Clock3 size={17} />{Math.ceil(Number(outline.totalDurationSeconds || course.minutes * 60 || 0) / 60)} 分钟</span>
                <span><Users size={17} />{course.learnerCount || 0} 人学习</span>
                <span><Star size={17} />{averageRating ? averageRating.toFixed(1) : "暂无评分"}</span>
                <span><BarChart3 size={17} />{course.difficulty || course.level || "入门"}</span>
              </div>
              <div className="course-instructor"><span>{String(course.teacher || "平台讲师").slice(0, 1)}</span><div><strong>{course.teacher || "平台讲师"}</strong><small>{course.directionName || course.subject} 课程讲师</small></div></div>
            </div>
            <aside className="course-enroll-card">
              <button type="button" className="course-enroll-visual" onClick={startLearning} disabled={loading || !!busyAction} aria-label={course.type === "video" ? "播放课程视频" : "打开课程学习资料"}>
                {course.type === "video" ? <PlayCircle size={52} /> : <BookOpen size={48} />}
                <strong>{course.type === "video" ? "点击播放课程" : "点击打开课程"}</strong>
                <span>{course.type === "video" ? "视频课程" : "图文学习资料"}</span>
              </button>
              <div className="course-enroll-body">
                <div className="course-enroll-progress"><span><b style={{ width: `${progress}%` }} /></span><strong>{progress}%</strong></div>
                <button onClick={startLearning} disabled={loading || !!busyAction}>{busyAction === "learning" ? "正在打开..." : progress > 0 ? "继续学习" : "开始学习"}<ArrowRight size={16} /></button>
                <p><CheckCircle2 size={15} />包含 {chapters.length} 章、{totalLessons} 节内容</p>
                <p><CheckCircle2 size={15} />课程图谱与章节同步关联</p>
                <p><CheckCircle2 size={15} />学习记录永久保留</p>
              </div>
            </aside>
          </div>
        </div>
      </section>

      <nav className="course-tabbar" aria-label="课程内容导航">
        <div>
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return <button className={activeTab === tab.id ? "active" : ""} key={tab.id} onClick={() => setActiveTab(tab.id)}><Icon size={17} />{tab.label}<span>{tab.count}</span></button>;
          })}
        </div>
      </nav>

      <section className="course-detail-content" id="course-content">
        <div className="course-content-main">
          {notice && <p className="course-notice"><CheckCircle2 size={16} />{notice}</p>}

          {learningPlayer.open && (
            <section className="course-learning-player" id="course-player" aria-label="课程学习区">
              <header>
                <div><span>NOW LEARNING</span><h2>{learningPlayer.lesson?.title || "课程导学"}</h2></div>
                <button type="button" onClick={closeLearning}>收起学习区</button>
              </header>
              {activeLessonType === "video" && learningPlayer.status === "loading" ? (
                <div className="course-player-empty"><RefreshCcw size={46} /><div><strong>正在加载当前课时视频</strong><p>切换课时后不会继续显示上一课时内容。</p></div></div>
              ) : activeLessonType === "video" && learningPlayer.status === "playable" && activeLessonResourceUrl ? (
                <CourseVideo ref={learningVideoRef} key={`${learningPlayer.lesson?.id}-${learningPlayer.assetId}`} src={activeLessonResourceUrl} title={learningPlayer.lesson?.title || course.title} autoPlay />
              ) : activeLessonType === "video" ? (
                <div className="course-player-empty">
                  <PlayCircle size={54} />
                  <div><strong>{learningPlayer.status === "processing" ? "视频正在处理中" : learningPlayer.status === "error" ? "课时视频加载失败" : "本课时暂无视频"}</strong><p>每个课时只读取自己绑定的视频，不会重复播放其他课时的视频。</p>{learningPlayer.status === "error" && <button type="button" onClick={() => openLearning(learningPlayer.lesson)}>重新加载</button>}</div>
                </div>
              ) : activeLessonType === "document" && activeLessonResourceUrl ? (
                <div className="course-player-empty"><FileText size={48} /><div><strong>本课时为文档学习</strong><p><a href={activeLessonResourceUrl} target="_blank" rel="noreferrer">打开课程文档</a></p></div></div>
              ) : (
                <div className="course-reading-label"><BookOpen size={20} /><span>{activeLessonType === "exercise" ? "练习课时" : "图文课程 · 阅读学习"}</span></div>
              )}
              {activeLessonType !== "video" && activeLessonType !== "document" && (
                <article className="course-reading-content">
                  {courseStudyText.split(/\n+/).filter(Boolean).map((paragraph, index) => <p key={`course-reading-${index}`}>{paragraph}</p>)}
                </article>
              )}
              <footer>
                <span><Clock3 size={15} />本节约 {lessonMinutes(learningPlayer.lesson)} 分钟</span>
                <button type="button" onClick={completeLearning} disabled={busyAction === "complete-learning" || adminPreview}>{adminPreview ? "管理员预览" : busyAction === "complete-learning" ? "保存中..." : progress >= 100 ? "课程已完成" : "完成本课"}<CheckCircle2 size={16} /></button>
              </footer>
            </section>
          )}

          {activeTab === "chapters" && (
            <>
              <section className="course-overview-card">
                <span className="studio-kicker">COURSE OUTCOME</span>
                <h2>完成课程后，你将建立这些能力</h2>
                <div className="course-outcomes">
                  {knowledgeNodes.slice(0, 6).map((node, index) => <span key={node.id || node.name || index}><CheckCircle2 size={16} />{node.name || node.title}</span>)}
                </div>
              </section>
              <div className="course-section-heading"><div><span className="studio-kicker">CURRICULUM</span><h2>课程章节</h2></div><span>{chapters.length} 章 · {totalLessons} 节</span></div>
              <div className="curriculum-list">
                {chapters.map((chapter, chapterIndex) => (
                  <details key={chapter.id} open={chapterIndex === 0 ? true : undefined}>
                    <summary><span>0{chapterIndex + 1}</span><div><strong>{chapter.title}</strong><small>{chapter.description}</small></div><ChevronDown size={18} /></summary>
                    <div className="lesson-list">
                      {chapter.lessons.map((lesson, lessonIndex) => (
                        <button key={lesson.id || lesson.title} onClick={() => openLearning(lesson)}><span>{lesson.lessonType === "video" ? <PlayCircle size={18} /> : lesson.lessonType === "document" ? <FileText size={18} /> : <BookOpen size={18} />}{lesson.title}</span><small>{lesson.lessonType === "video" ? lesson.assetReady ? "立即播放" : "视频待上传" : lesson.assetReady ? "立即学习" : "内容待配置"} · {lessonMinutes(lesson)} 分钟</small></button>
                      ))}
                    </div>
                  </details>
                ))}
                {!chapters.length && <p className="empty-state-react">教师尚未发布课程目录，暂时没有可学习的课时。</p>}
              </div>
            </>
          )}

          {activeTab === "questions" && (
            <section className="community-panel">
              <div className="course-section-heading"><div><span className="studio-kicker">COURSE Q&A</span><h2>课程问答</h2></div><span>围绕课程内容进行讨论</span></div>
              <form className="community-composer" onSubmit={submitQuestion}>
                <textarea value={questionText} onChange={(event) => setQuestionText(event.target.value)} maxLength={500} placeholder="描述你在学习中遇到的问题，清晰的问题更容易获得有效回答。" />
                <div><small>{questionText.length}/500</small><button disabled={!questionText.trim() || busyAction === "question"}>{busyAction === "question" ? "发布中..." : "发布问题"}<Send size={15} /></button></div>
              </form>
              <div className="community-list">
                {community.questions.map((item) => <article key={item.id}><span className="community-avatar">{String(item.authorName || "学").slice(0, 1)}</span><div><header><strong>{item.authorName || "课程学员"}</strong><small>{formatCommunityDate(item.createdAt)}</small></header><p>{item.content}</p><footer><MessageCircle size={14} />{item.answerCount || 0} 条教师回复</footer>{community.replies.filter((reply) => reply.targetType === "question" && Number(reply.targetId) === Number(item.id)).map((reply) => <div className="teacher-reply" key={reply.id}><strong>{reply.teacherName || "课程教师"} 回复</strong><p>{reply.content}</p><small>{formatCommunityDate(reply.createdAt)}</small></div>)}</div></article>)}
                {!community.questions.length && <div className="community-empty"><MessageCircle size={28} /><strong>还没有课程问题</strong><p>成为第一个提出高质量问题的学习者。</p></div>}
              </div>
            </section>
          )}

          {activeTab === "notes" && (
            <section className="community-panel">
              <div className="course-section-heading"><div><span className="studio-kicker">PRIVATE NOTES</span><h2>我的课程笔记</h2></div><span>仅自己可见</span></div>
              <form className="community-composer note-composer" onSubmit={submitNote}>
                <select value={noteDraft.lessonTitle} onChange={(event) => setNoteDraft((current) => ({ ...current, lessonTitle: event.target.value }))}>
                  <option>课程总览</option>
                  {chapters.flatMap((chapter) => chapter.lessons).map((lesson) => <option key={lesson.title}>{lesson.title}</option>)}
                </select>
                <textarea value={noteDraft.content} onChange={(event) => setNoteDraft((current) => ({ ...current, content: event.target.value }))} maxLength={3000} placeholder="记录关键概念、代码思路或还需要验证的问题..." />
                <div><small>{noteDraft.content.length}/3000</small><button disabled={!noteDraft.content.trim() || busyAction === "note"}>{busyAction === "note" ? "保存中..." : "保存笔记"}<NotebookPen size={15} /></button></div>
              </form>
              <div className="note-list">
                {community.notes.map((item) => <article key={item.id}><header><span>{item.lessonTitle || "课程总览"}</span><small>{formatCommunityDate(item.updatedAt || item.createdAt)}</small></header><p>{item.content}</p><button onClick={() => removeNote(item.id)} disabled={busyAction === `note-${item.id}`}>删除</button></article>)}
                {!community.notes.length && <div className="community-empty"><NotebookPen size={28} /><strong>开始记录第一条笔记</strong><p>笔记会与你的账号绑定，在下次学习时继续使用。</p></div>}
              </div>
            </section>
          )}

          {activeTab === "reviews" && (
            <section className="community-panel">
              <div className="course-section-heading"><div><span className="studio-kicker">COURSE REVIEWS</span><h2>课程评价</h2></div><span>{community.reviewSummary?.reviewCount || 0} 位学习者参与</span></div>
              <div className="review-summary"><strong>{averageRating ? averageRating.toFixed(1) : "—"}</strong><div><span>{[1,2,3,4,5].map((value) => <Star key={value} size={18} className={value <= Math.round(averageRating) ? "filled" : ""} />)}</span><p>{averageRating ? "来自真实学习记录的评价" : "等待第一条课程评价"}</p></div></div>
              <form className="community-composer review-composer" onSubmit={submitReview}>
                <div className="rating-picker"><span>我的评分</span>{[1,2,3,4,5].map((value) => <button type="button" key={value} className={value <= reviewDraft.rating ? "active" : ""} onClick={() => setReviewDraft((current) => ({ ...current, rating: value }))}><Star size={20} /></button>)}</div>
                <textarea value={reviewDraft.content} onChange={(event) => setReviewDraft((current) => ({ ...current, content: event.target.value }))} maxLength={1000} placeholder="分享课程最有价值的部分，以及你希望继续改进的地方。" />
                <div><small>{reviewDraft.content.length}/1000</small><button disabled={!reviewDraft.content.trim() || busyAction === "review"}>{busyAction === "review" ? "提交中..." : "提交评价"}<Send size={15} /></button></div>
              </form>
              <div className="community-list review-list">
                {community.reviews.map((item) => <article key={item.id}><span className="community-avatar">{String(item.authorName || "学").slice(0, 1)}</span><div><header><strong>{item.authorName || "课程学员"}</strong><span>{Array.from({ length: 5 }, (_, index) => <Star key={index} size={13} className={index < item.rating ? "filled" : ""} />)}</span><small>{formatCommunityDate(item.updatedAt || item.createdAt)}</small></header><p>{item.content}</p>{community.replies.filter((reply) => reply.targetType === "review" && Number(reply.targetId) === Number(item.id)).map((reply) => <div className="teacher-reply" key={reply.id}><strong>{reply.teacherName || "课程教师"} 回复</strong><p>{reply.content}</p><small>{formatCommunityDate(reply.createdAt)}</small></div>)}</div></article>)}
              </div>
            </section>
          )}

          {activeTab === "graph" && (
            <section className="course-graph-panel">
              <div className="course-section-heading"><div><span className="studio-kicker">RELATED KNOWLEDGE GRAPH</span><h2>与本课程相关的知识图谱</h2></div><button onClick={openRelatedGraph}>进入完整图谱<ArrowRight size={15} /></button></div>
              <p>图谱将课程章节放回完整知识体系中，帮助你确认前置能力、当前重点与后续方向。</p>
              <div className="course-graph-stage">
                <span className="course-graph-orbit orbit-a" /><span className="course-graph-orbit orbit-b" />
                <button className="course-graph-root" onClick={openRelatedGraph}><GitBranch size={24} /><strong>{course.directionName || course.subject}</strong><small>课程主线</small></button>
                {knowledgeNodes.slice(0, 8).map((node, index) => <button className={`course-graph-node course-graph-node-${index + 1}`} key={node.id || node.name || index} onClick={openRelatedGraph}>{node.name || node.title}</button>)}
              </div>
              <div className="course-graph-insight"><Sparkles size={20} /><div><strong>学习建议</strong><p>优先完成前两个知识节点，再进入工程化实践章节，可以减少概念跳跃带来的理解负担。</p></div></div>
            </section>
          )}
        </div>

        <aside className="course-content-side">
          <section><span className="studio-kicker">COURSE AT A GLANCE</span><h3>课程信息</h3><dl><div><dt>难度</dt><dd>{course.difficulty || course.level || "入门"}</dd></div><div><dt>章节</dt><dd>{chapters.length} 章</dd></div><div><dt>内容</dt><dd>{totalLessons} 节</dd></div><div><dt>学习进度</dt><dd>{progress}%</dd></div></dl></section>
          <section><span className="studio-kicker">KNOWLEDGE PATH</span><h3>关联知识</h3><div className="course-side-nodes">{knowledgeNodes.slice(0, 6).map((node, index) => <button key={node.id || node.name || index} onClick={() => setActiveTab("graph")}>{node.name || node.title}<ArrowRight size={13} /></button>)}</div></section>
        </aside>
      </section>
    </main>
  );
}

function ResourceCard({ item, onUpdate }) {
  const Icon = item.type === "video" ? PlayCircle : FileText;
  const [busyAction, setBusyAction] = useState("");
  const [status, setStatus] = useState("");
  const progress = Math.max(0, Math.min(Number(item.progress || 0), 100));
  const textContent = item.content || buildResourceTextContent(item);
  useEffect(() => {
    if (!status) return undefined;
    const timer = setTimeout(() => setStatus(""), 2600);
    return () => clearTimeout(timer);
  }, [status]);
  const saveProgress = async (nextProgress) => {
    if (!onUpdate || busyAction) return;
    setBusyAction(`progress-${nextProgress}`);
    setStatus("");
    try {
      await api("/learning/records", { method: "POST", body: JSON.stringify({ resourceId: item.id, progress: nextProgress }) });
      onUpdate(item.id, { progress: Math.max(progress, nextProgress) });
      setStatus(nextProgress >= 100 ? "已标记完成" : "已记录开始学习");
    } catch (error) {
      handleActionError(error, "学习记录保存失败");
    } finally {
      setBusyAction("");
    }
  };
  const toggleFavorite = async () => {
    if (!onUpdate || busyAction) return;
    setBusyAction("favorite");
    setStatus("");
    try {
      const data = await api(`/resources/${item.id}/favorite`, { method: "POST" });
      onUpdate(item.id, { favorited: data.favorited, favoriteCount: data.favoriteCount });
      setStatus(data.favorited ? "已加入收藏" : "已取消收藏");
    } catch (error) {
      handleActionError(error, "收藏操作失败");
    } finally {
      setBusyAction("");
    }
  };

  return (
    <article className="resource-card">
      <div className="resource-icon"><Icon size={28} /></div>
      <div>
        <span>{item.subject} / {item.level}</span>
        <h3>{item.title}</h3>
        <p>{item.teacher || "平台教师"} / {item.minutes || 45} 分钟</p>
      </div>
      <div className="resource-meta">
        <span><Heart size={14} /> {item.favoriteCount || 0}</span>
      </div>
      {item.fileUrl && item.type === "video" && (
        <CourseVideo className="resource-video" src={item.fileUrl} title={item.title} />
      )}
      {item.fileUrl && item.type !== "video" && (
        <a className="resource-link" href={encodeURI(item.fileUrl)} target="_blank" rel="noreferrer">打开学习资料</a>
      )}
      {!item.fileUrl && <p className="resource-empty">暂无上传文件</p>}
      <details className="resource-text-study">
        <summary>
          <BookOpen size={15} />
          文本学习
          <span>{textContent.length} 字</span>
        </summary>
        <div>
          {textContent.split(/\n+/).map((paragraph, index) => (
            <p key={`${item.id}-text-${index}`}>{paragraph}</p>
          ))}
        </div>
      </details>
      {onUpdate && (
        <>
          <div className="resource-progress-line">
            <span style={{ width: `${progress}%` }} />
            <b>学习进度 {progress}%</b>
          </div>
          {status && <p className="resource-action-status">{status}</p>}
          <div className="resource-actions">
            <button disabled={!!busyAction} onClick={() => saveProgress(20)}>
              {busyAction === "progress-20" ? "记录中..." : progress > 0 ? "继续学习" : "开始学习"}
            </button>
            <button disabled={!!busyAction} onClick={() => saveProgress(100)}>
              {busyAction === "progress-100" ? "保存中..." : progress >= 100 ? "已完成" : "完成学习"}
            </button>
            <button disabled={!!busyAction} className={item.favorited ? "active" : ""} onClick={toggleFavorite}>
              <Heart size={15} />
              {busyAction === "favorite" ? "处理中..." : item.favorited ? "已收藏" : "收藏"}
            </button>
          </div>
        </>
      )}
    </article>
  );
}

function KnowledgeGraph({ subject, categoryTree, resources = [], setSelectedSubject, setPage }) {
  const tree = categoryTree?.length ? categoryTree : knowledgeCategories;
  const initialPath = findCategoryPath(subject, tree) || [tree[0]?.label || "软件工程"];
  const [categoryPath, setCategoryPath] = useState(initialPath);
  const [draftCategoryPath, setDraftCategoryPath] = useState(initialPath);
  const [graphData, setGraphData] = useState(() => buildCategoryGraph(initialPath, tree));
  const [graphLoading, setGraphLoading] = useState(true);
  const [graphNotice, setGraphNotice] = useState("");
  const [activeId, setActiveId] = useState(`${initialPath[initialPath.length - 1]}-root`);
  const [zoom, setZoom] = useState(0.92);
  const [layout, setLayout] = useState("radial");
  const [keyword, setKeyword] = useState("");
  const [openingCourse, setOpeningCourse] = useState(false);
  const [animatedNodes, setAnimatedNodes] = useState([]);
  const stageRef = React.useRef(null);
  const simulationRef = React.useRef(new Map());
  const draggingRef = React.useRef(null);
  const suppressClickRef = React.useRef(false);
  const graphRequestRef = React.useRef(0);

  const loadNeo4jGraph = async (path) => {
    const resolvedPath = path.length ? path : [tree[0]?.label || "软件工程"];
    const target = resolvedPath[resolvedPath.length - 1];
    const requestId = ++graphRequestRef.current;
    setGraphLoading(true);
    setGraphNotice("");
    try {
      const params = new URLSearchParams({ subject: target, path: resolvedPath.join(" > ") });
      const data = await api(`/knowledge-graph?${params.toString()}`);
      if (requestId !== graphRequestRef.current) return;
      const nodes = Array.isArray(data.nodes) ? data.nodes.filter((node) => node?.id && node?.label) : [];
      const links = Array.isArray(data.links) ? data.links : [];
      if (!nodes.length) throw new Error("Neo4j 中暂无对应知识节点");
      const apiGraph = {
        source: data.source || "neo4j",
        nodes,
        links,
        stats: data.stats || {},
        path: resolvedPath
      };
      const nextGraph = apiGraph.source === "neo4j"
        ? apiGraph
        : { ...buildCategoryGraph(resolvedPath, tree), stats: apiGraph.stats };
      setGraphData(nextGraph);
      const root = nextGraph.nodes.find((node) => node.type === "root" || node.label === target) || nextGraph.nodes[0];
      setActiveId(root.id);
      setGraphNotice(apiGraph.source === "neo4j" ? "" : `${data.fallbackReason || "Neo4j 暂不可用"}，当前按原三级分类显示备用图谱`);
    } catch (error) {
      if (requestId !== graphRequestRef.current) return;
      const fallback = buildCategoryGraph(resolvedPath, tree);
      setGraphData(fallback);
      setActiveId(fallback.nodes[0]?.id || `${target}-root`);
      setGraphNotice(error?.message || "Neo4j 图谱加载失败，当前显示本地备用图谱");
    } finally {
      if (requestId === graphRequestRef.current) setGraphLoading(false);
    }
  };

  useEffect(() => {
    const nextPath = findCategoryPath(subject, tree) || [tree[0]?.label || "软件工程"];
    setCategoryPath(nextPath);
    setDraftCategoryPath(nextPath);
    setKeyword("");
    loadNeo4jGraph(nextPath);
  }, [subject, categoryTree]);

  const visibleNodes = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return graphData.nodes;
    const matchedIds = new Set();
    graphData.nodes.forEach((node) => {
      if (String(node.label || "").toLowerCase().includes(text)) matchedIds.add(node.id);
    });
    let changed = true;
    while (changed) {
      changed = false;
      graphData.links.forEach((link) => {
        if (matchedIds.has(link.source) || matchedIds.has(link.target)) {
          if (!matchedIds.has(link.source) || !matchedIds.has(link.target)) changed = true;
          matchedIds.add(link.source);
          matchedIds.add(link.target);
        }
      });
    }
    return graphData.nodes.filter((node) => matchedIds.has(node.id));
  }, [graphData, keyword]);

  const visibleIds = useMemo(() => new Set(visibleNodes.map((node) => node.id)), [visibleNodes]);
  const visibleLinks = useMemo(
    () => graphData.links.filter((link) => visibleIds.has(link.source) && visibleIds.has(link.target)),
    [graphData.links, visibleIds]
  );

  const positioned = useMemo(() => {
    const currentLabel = categoryPath[categoryPath.length - 1] || subject;
    const rootIndex = visibleNodes.findIndex((node) => node.type === "root" || node.label === currentLabel);
    const ordered = rootIndex > 0
      ? [visibleNodes[rootIndex], ...visibleNodes.filter((_, index) => index !== rootIndex)]
      : visibleNodes;
    const root = ordered[0] ? { ...ordered[0], x: 50, y: 50 } : null;
    const directChildIds = new Set(
      root ? visibleLinks.filter((link) => link.source === root.id).map((link) => link.target) : []
    );
    const primary = ordered.filter((node, index) => index > 0 && (
      directChildIds.has(node.id) || (!node.parentId && node.type !== "point" && node.type !== "knowledge")
    ));
    const primaryIds = new Set(primary.map((node) => node.id));
    const nested = ordered.filter((node, index) => index > 0 && !primaryIds.has(node.id));
    const primarySlots = [
      [50, 14], [78, 28], [82, 58], [62, 82],
      [38, 82], [18, 58], [22, 28], [50, 88]
    ];
    const parentPositions = {};
    const primaryPositioned = primary.map((node, index) => {
      let x;
      let y;
      if (layout === "layer") {
        [x, y] = primarySlots[index % primarySlots.length];
      } else {
        const angle = (Math.PI * 2 * index) / Math.max(primary.length, 1) - Math.PI / 2;
        x = 50 + Math.cos(angle) * 32;
        y = 50 + Math.sin(angle) * 28;
      }
      parentPositions[node.id] = { x, y };
      return { ...node, x, y };
    });
    const nestedPositioned = nested.map((node) => {
      const parent = parentPositions[node.parentId] || { x: 50, y: 50 };
      const baseAngle = Math.atan2(parent.y - 50, parent.x - 50);
      const siblings = nested.filter((item) => item.parentId === node.parentId);
      const siblingIndex = siblings.findIndex((item) => item.id === node.id);
      const siblingCount = siblings.length || 1;
      const sector = Math.min((Math.PI * 2 * 0.82) / Math.max(primary.length, 1), 1.5);
      const angle = siblingCount === 1
        ? baseAngle
        : baseAngle - sector / 2 + (siblingIndex / (siblingCount - 1)) * sector;
      const radiusX = layout === "layer" ? 43 : 45;
      const radiusY = layout === "layer" ? 38 : 40;
      return {
        ...node,
        x: Math.max(6, Math.min(50 + Math.cos(angle) * radiusX, 94)),
        y: Math.max(6, Math.min(50 + Math.sin(angle) * radiusY, 94))
      };
    });
    return [root, ...primaryPositioned, ...nestedPositioned].filter(Boolean);
  }, [visibleNodes, visibleLinks, subject, layout, categoryPath]);

  useEffect(() => {
    const next = new Map();
    positioned.forEach((node, index) => {
      const previous = simulationRef.current.get(node.id);
      const jitter = index === 0 ? 0 : 4;
      next.set(node.id, {
        ...node,
        tx: node.x,
        ty: node.y,
        x: previous?.x ?? Math.max(7, Math.min(node.x + (Math.random() - 0.5) * jitter, 93)),
        y: previous?.y ?? Math.max(7, Math.min(node.y + (Math.random() - 0.5) * jitter, 93)),
        vx: previous?.vx ?? 0,
        vy: previous?.vy ?? 0,
        phase: previous?.phase ?? index * 0.73
      });
    });
    simulationRef.current = next;
    setAnimatedNodes(Array.from(next.values()));
  }, [positioned]);

  useEffect(() => {
    let frameId;
    let lastTime = performance.now();
    const tick = (time) => {
      const dt = Math.min((time - lastTime) / 16.67, 2);
      lastTime = time;
      const nodes = Array.from(simulationRef.current.values());
      const draggingId = draggingRef.current?.id;
      nodes.forEach((node, index) => {
        const isRoot = node.type === "root";
        const isDragging = node.id === draggingId;
        const floatX = isRoot ? 0 : Math.sin(time / 1200 + node.phase) * 0.04;
        const floatY = isRoot ? 0 : Math.cos(time / 1350 + node.phase) * 0.04;
        if (!isDragging) {
          const pull = isRoot ? 0.12 : 0.045;
          node.vx += (node.tx - node.x) * pull * dt + floatX;
          node.vy += (node.ty - node.y) * pull * dt + floatY;
        }
        for (let j = index + 1; j < nodes.length; j += 1) {
          const other = nodes[j];
          const dx = node.x - other.x;
          const dy = node.y - other.y;
          const distance = Math.max(Math.sqrt(dx * dx + dy * dy), 0.1);
          const minDistance = node.type === "root" || other.type === "root" ? 17 : node.type === "point" || other.type === "point" ? 10 : 14;
          if (distance < minDistance) {
            const force = (minDistance - distance) * 0.012 * dt;
            const fx = (dx / distance) * force;
            const fy = (dy / distance) * force;
            if (node.id !== draggingId) {
              node.vx += fx;
              node.vy += fy;
            }
            if (other.id !== draggingId) {
              other.vx -= fx;
              other.vy -= fy;
            }
          }
        }
      });
      nodes.forEach((node) => {
        if (node.id !== draggingId) {
          node.vx *= 0.84;
          node.vy *= 0.84;
          node.x = Math.max(5.5, Math.min(node.x + node.vx * dt, 94.5));
          node.y = Math.max(5.5, Math.min(node.y + node.vy * dt, 94.5));
        }
      });
      setAnimatedNodes(nodes.map((node) => ({ ...node })));
      frameId = requestAnimationFrame(tick);
    };
    frameId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameId);
  }, []);

  const renderedNodes = animatedNodes.length === positioned.length ? animatedNodes : positioned;
  const nodeById = useMemo(() => Object.fromEntries(renderedNodes.map((node) => [node.id, node])), [renderedNodes]);
  const active = graphData.nodes.find((node) => node.id === activeId) || renderedNodes[0] || graphData.nodes[0];
  const completion = graphData.stats?.completionRate ?? 0;
  const achievement = graphData.stats?.achievementRate ?? 0;
  const currentLabel = categoryPath[categoryPath.length - 1] || subject;
  const learnedCount = Math.max(0, active?.label === currentLabel ? 0 : 1);
  const hasIntegratedThirdLevel = categoryPath.length === 1 && graphData.nodes.some((node) => node.type === "knowledge");
  const totalKnowledgeCount = hasIntegratedThirdLevel
    ? graphData.nodes.filter((node) => node.type === "knowledge").length
    : Math.max(graphData.nodes.length - 1, 0);
  const activePath = active?.path || categoryPath;
  const activeCategory = findCategoryByPath(activePath, tree);
  const activeCanDrill = active?.type !== "root" && activePath.length < 3 && !!activeCategory?.children?.length;
  const neo4jLevelLabel = categoryPath.length === 1 ? "二级方向" : categoryPath.length === 2 ? "三级知识点" : "当前知识点";
  const primaryLegendLabel = graphData.source === "neo4j" ? neo4jLevelLabel : graphData.source === "category" ? "二级方向" : graphData.source === "direction" ? "三级知识点" : "详细知识点";
  const nodeTypeLabel = (node) => {
    if (graphData.source === "neo4j" && node?.type === "root") return categoryPath.length === 1 ? "一级大类" : categoryPath.length === 2 ? "二级方向" : "三级知识点";
    if (node?.type === "root") return graphData.source === "category" ? "一级大类" : graphData.source === "direction" ? "二级方向" : "三级知识点";
    if (node?.type === "direction") return "二级方向";
    if (node?.type === "knowledge") return "三级知识点";
    return "详细知识点";
  };
  const openNode = (node) => {
    setActiveId(node.id);
    const nextPath = node.path || findCategoryPath(node.label, tree);
    const nextCategory = findCategoryByPath(nextPath || [], tree);
    if (nextPath && nextPath.length <= 3 && nextCategory?.children?.length) {
      setKeyword("");
      setLayout("radial");
      setCategoryPath(nextPath);
      setDraftCategoryPath(nextPath);
      setSelectedSubject?.(nextPath[nextPath.length - 1]);
      loadNeo4jGraph(nextPath);
      return;
    }
    if (node.label !== currentLabel) {
      const learningSubject = nextPath?.[1] || categoryPath[1] || currentLabel;
      api("/learning/records", { method: "POST", body: JSON.stringify({ subject: learningSubject, knowledgeNode: node.label, progress: 10 }) }).catch(() => {});
    }
  };
  const learnNode = (node) => {
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      return;
    }
    openNode(node);
  };
  const openKnowledgeCourse = async (node) => {
    if (!node || openingCourse) return;
    openNode(node);
    setOpeningCourse(true);
    const target = String(node.label || "").trim();
    const path = Array.isArray(node.path) && node.path.length ? node.path : categoryPath;
    const direction = path[1] || categoryPath[1] || currentLabel;
    const terms = Array.from(new Set([target, direction, currentLabel].filter(Boolean)));
    const matchesTerms = (course) => {
      const text = [course.title, course.subject, course.directionName, course.categoryName, course.description]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return terms.some((term) => text.includes(String(term).toLowerCase()));
    };
    const courseScore = (course) => {
      const title = String(course.title || "").toLowerCase();
      const subjectName = String(course.subject || "").toLowerCase();
      const directionName = String(course.directionName || "").toLowerCase();
      const targetText = target.toLowerCase();
      let score = 0;
      if (title.includes(targetText)) score += 30;
      if (subjectName === targetText || directionName === targetText) score += 24;
      if (subjectName.includes(targetText) || directionName.includes(targetText)) score += 16;
      terms.slice(1).forEach((term) => {
        const value = String(term).toLowerCase();
        if (title.includes(value)) score += 8;
        if (subjectName.includes(value) || directionName.includes(value)) score += 6;
      });
      return score + Number(course.learnerCount || 0) / 10000;
    };

    try {
      const directResult = await getCourses({ keyword: target, pageNum: 1, pageSize: 12 });
      let candidates = [...(directResult.list || [])];
      if (!candidates.length && direction && direction !== target) {
        const directionResult = await getCourses({ keyword: direction, pageNum: 1, pageSize: 12 });
        candidates = directionResult.list || [];
      }
      candidates.push(...resources.filter(matchesTerms));
      const uniqueCourses = Array.from(new Map(candidates.map((course) => [Number(course.id), course])).values())
        .filter((course) => Number(course.id) > 0)
        .sort((left, right) => courseScore(right) - courseScore(left));
      if (uniqueCourses.length) {
        setPage?.("course", { courseId: uniqueCourses[0].id });
        return;
      }
      setSelectedSubject?.(direction || target);
      setPage?.("catalog");
    } catch {
      const fallback = resources.filter(matchesTerms).sort((left, right) => courseScore(right) - courseScore(left))[0];
      if (fallback?.id) {
        setPage?.("course", { courseId: fallback.id });
      } else {
        setSelectedSubject?.(direction || target);
        setPage?.("catalog");
      }
    } finally {
      setOpeningCourse(false);
    }
  };
  const confirmCategoryPath = () => {
    const nextPath = draftCategoryPath.length ? draftCategoryPath : [tree[0]?.label || "软件工程"];
    setCategoryPath(nextPath);
    setDraftCategoryPath(nextPath);
    setSelectedSubject?.(nextPath[nextPath.length - 1]);
    setKeyword("");
    loadNeo4jGraph(nextPath);
  };
  const openFullscreen = () => {
    document.querySelector(".graph-canvas")?.requestFullscreen?.();
  };
  const updateDraggedNode = (event) => {
    const dragging = draggingRef.current;
    if (!dragging || !stageRef.current) return;
    if (Math.abs(event.clientX - dragging.startX) + Math.abs(event.clientY - dragging.startY) > 6) {
      suppressClickRef.current = true;
    }
    const rect = stageRef.current.getBoundingClientRect();
    const x = Math.max(5.5, Math.min(((event.clientX - rect.left) / rect.width) * 100, 94.5));
    const y = Math.max(5.5, Math.min(((event.clientY - rect.top) / rect.height) * 100, 94.5));
    const node = simulationRef.current.get(dragging.id);
    if (node) {
      node.x = x;
      node.y = y;
      node.vx = 0;
      node.vy = 0;
      setAnimatedNodes(Array.from(simulationRef.current.values()).map((item) => ({ ...item })));
    }
  };
  const startDragNode = (event, node) => {
    draggingRef.current = { id: node.id, startX: event.clientX, startY: event.clientY };
    suppressClickRef.current = false;
    setActiveId(node.id);
    event.currentTarget.setPointerCapture?.(event.pointerId);
    updateDraggedNode(event);
  };
  const stopDragNode = () => {
    draggingRef.current = null;
  };

  return (
    <main className="graph-page">
      <div className="graph-toolbar-top">
        <div className="graph-toolbar-title">
          <span className="eyebrow">Neo4j 知识图谱</span>
          <h2>{currentLabel}</h2>
          <div className={`graph-source-status ${graphLoading || graphData.source === "neo4j" ? "connected" : "fallback"}`}>
            <i />
            {graphLoading ? "正在连接 Neo4j" : graphData.source === "neo4j" ? "Neo4j 实时数据" : "备用数据"}
          </div>
        </div>
        <div className="graph-toolbar-actions">
          <div className="graph-filter-confirm">
            <CategoryCascader tree={tree} value={draftCategoryPath} onChange={setDraftCategoryPath} />
            <button type="button" className="graph-confirm-button" onClick={confirmCategoryPath}>确认查看<ArrowRight size={15} /></button>
            <small>只看一级大类时，方向保留“全部方向”即可</small>
          </div>
          <label className="graph-search">
            <Search size={17} />
            <input placeholder="搜索知识点" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
          </label>
          <button onClick={() => setLayout((value) => value === "radial" ? "layer" : "radial")}>布局切换</button>
          <span>已点亮 {learnedCount}/{totalKnowledgeCount}</span>
        </div>
      </div>
      <aside className="graph-stats">
        <div><BookOpen size={24} /><span>知识点总数</span><strong>{totalKnowledgeCount}</strong></div>
        <div><BarChart3 size={24} /><span>学习完成率</span><strong>{completion}%</strong></div>
        <div><CheckCircle2 size={24} /><span>学习达成率</span><strong>{achievement}%</strong></div>
      </aside>
      <section className="graph-canvas">
        {graphLoading && <div className="graph-data-message loading">正在从 Neo4j 加载知识节点与关系...</div>}
        {!graphLoading && graphNotice && <div className="graph-data-message warning">{graphNotice}</div>}
        <div
          className="graph-stage"
          ref={stageRef}
          style={{ transform: `scale(${zoom})` }}
          onPointerMove={updateDraggedNode}
          onPointerUp={stopDragNode}
          onPointerLeave={stopDragNode}
        >
          <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            {visibleLinks.map((link, index) => {
              const source = nodeById[link.source];
              const target = nodeById[link.target];
              if (!source || !target) return null;
              return <line key={`${link.source}-${link.target}-${index}`} x1={source.x} y1={source.y} x2={target.x} y2={target.y} />;
            })}
            {!visibleLinks.length && renderedNodes.slice(1).map((node) => <line key={node.id} x1="50" y1="50" x2={node.x} y2={node.y} />)}
          </svg>
          {renderedNodes.map((node, index) => (
            <button
              key={node.id}
              className={`${index === 0 ? "center-node" : "graph-node"} ${node.type === "direction" ? "track-node" : ""} ${node.type === "knowledge" ? "course-node" : ""} ${node.type === "point" ? "point-node" : ""} ${hasIntegratedThirdLevel && node.type === "knowledge" ? "integrated-third-node" : ""} ${active?.id === node.id ? "active" : ""}`}
              style={{ left: `${node.x}%`, top: `${node.y}%` }}
              onPointerDown={(event) => startDragNode(event, node)}
              onClick={() => learnNode(node)}
            >
              {node.label}
              {index === 0 && <span>{nodeTypeLabel(node)}</span>}
            </button>
          ))}
        </div>
        <div className="graph-legend">
          <span><i className="root-dot" /> 根节点</span>
          <span><i className="unit-dot" /> {primaryLegendLabel}</span>
          <span><i className="point-dot" /> {hasIntegratedThirdLevel ? "三级知识点（已整合）" : "点击节点继续下钻"}</span>
        </div>
        <div className="graph-tools">
          <button title="缩小" onClick={() => setZoom((value) => Math.max(value - 0.1, 0.45))}><ZoomOut size={18} /></button>
          <span className="graph-zoom-value">{Math.round(zoom * 100)}%</span>
          <button title="放大" onClick={() => setZoom((value) => Math.min(value + 0.1, 1.3))}><ZoomIn size={18} /></button>
          <button title="重置" onClick={() => { setZoom(0.92); setKeyword(""); }}><RefreshCcw size={18} /></button>
          <button title="全屏" onClick={openFullscreen}><Maximize size={18} /></button>
        </div>
      </section>
      <aside className="node-detail">
        <span className="eyebrow">当前节点</span>
        <h3>{active?.label || subject}</h3>
        <p>{active?.description || "系统会记录你的知识点点击与学习行为，后续用于路径规划、薄弱点分析和课程推荐。"}</p>
        <div className="node-detail-meta">
          <span>{graphData.source === "neo4j" ? "Neo4j 图谱" : graphData.source === "mysql" ? "MySQL 备用图谱" : "本地备用图谱"}</span>
          <span>{nodeTypeLabel(active)}</span>
        </div>
        <button
          className="primary-btn"
          disabled={openingCourse}
          onClick={() => active && (activeCanDrill ? openNode(active) : openKnowledgeCourse(active))}
        >
          {activeCanDrill ? `进入 ${active?.label} 图谱` : openingCourse ? "正在匹配课程…" : "学习该知识点"}
        </button>
      </aside>
    </main>
  );
}

function MyLearningPage({ setPage, setSelectedSubject }) {
  const [learning, setLearning] = useState({
    summary: {},
    subjectProgress: [],
    resourceRecords: [],
    nodeRecords: [],
    favorites: [],
    recentRecords: [],
    resume: null,
    resumeCourse: null
  });
  const [loading, setLoading] = useState(true);
  const [learningError, setLearningError] = useState("");
  const [completingResourceId, setCompletingResourceId] = useState(null);

  const loadLearning = async () => {
    setLoading(true);
    setLearningError("");
    try {
      const [data, resume] = await Promise.all([api("/learning/summary"), getResumeLearning()]);
      let resumeCourse = null;
      if (resume?.available) {
        try {
          resumeCourse = await getCourseLearningProgress(resume.courseId);
        } catch {
          resumeCourse = null;
        }
      }
      setLearning({
        summary: data.summary || {},
        subjectProgress: data.subjectProgress || [],
        resourceRecords: data.resourceRecords || [],
        nodeRecords: data.nodeRecords || [],
        favorites: data.favorites || [],
        recentRecords: data.recentRecords || [],
        resume: resume?.available ? resume : null,
        resumeCourse
      });
    } catch (error) {
      setLearningError(error?.message || "学习数据加载失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadLearning();
  }, []);

  const completeResource = async (resourceId) => {
    if (!resourceId || completingResourceId) return;
    setCompletingResourceId(resourceId);
    setLearningError("");
    try {
      await api("/learning/records", { method: "POST", body: JSON.stringify({ resourceId, progress: 100 }) });
      await loadLearning();
    } catch (error) {
      setLearningError(error?.message || "学习进度更新失败，请稍后重试");
    } finally {
      setCompletingResourceId(null);
    }
  };

  const openGraph = (subject) => {
    setSelectedSubject(subject || "Java");
    setPage("graph", { view: "catalog" });
  };

  const summary = learning.summary || {};
  const legacyAvgProgress = Number(summary.avgResourceProgress || summary.avgresourceprogress || 0);
  const resumeCourseProgress = Number(learning.resumeCourse?.progressPercent || 0);
  const avgProgress = legacyAvgProgress || resumeCourseProgress;
  const completedCount = Math.max(
    Number(summary.completedResourceCount || summary.completedresourcecount || 0),
    learning.resumeCourse?.progressPercent === 100 ? 1 : 0
  );
  const learnedResourceCount = Math.max(
    Number(summary.learnedResourceCount || summary.learnedresourcecount || 0),
    learning.resume ? 1 : 0
  );
  const learnedNodeCount = Number(summary.learnedNodeCount || summary.learnednodecount || 0);
  const favoriteCount = Number(summary.favoriteCount || summary.favoritecount || 0);
  const lastActiveAt = summary.lastActiveAt || summary.lastactiveat || learning.resume?.lastStudiedAt;
  const showEmptyStates = !loading && !learningError;

  return (
    <main className="content my-learning-page">
      <div className="home-head">
        <div>
          <span className="eyebrow">我的学习</span>
          <h2>学习进度与个人记录</h2>
          <p>汇总已学资源、知识点点击、收藏课程和最近学习动态。</p>
        </div>
        <button className="primary-btn" onClick={() => learning.resume
          ? setPage("learn", { courseId: learning.resume.courseId, lessonId: learning.resume.lessonId })
          : setPage("home")}>
          <BookOpen size={18} />
          {learning.resume ? `继续：${learning.resume.lessonTitle}` : "选择课程开始学习"}
        </button>
      </div>

      {learningError && (
        <div className="inline-error-state" role="alert">
          <span>{learningError}</span>
          <button type="button" onClick={loadLearning} disabled={loading}>{loading ? "重试中..." : "重新加载"}</button>
        </div>
      )}

      <section className="learning-overview">
        <AnalyticsCard icon={BookOpen} label="已学资源" value={learnedResourceCount} tone="blue" />
        <AnalyticsCard icon={CheckCircle2} label="已完成资源" value={completedCount} tone="green" />
        <AnalyticsCard icon={TrendingUp} label="平均进度" value={`${avgProgress}%`} tone="teal" />
        <AnalyticsCard icon={Heart} label="收藏资源" value={favoriteCount} tone="orange" />
      </section>

      <section className="learning-layout">
        <div className="learning-main">
          <section className="analytics-panel">
            <div className="panel-head">
              <div>
                <span className="eyebrow">方向进度</span>
                <h3>按学习方向统计</h3>
              </div>
              <BarChart3 size={22} />
            </div>
            <div className="subject-progress-list">
              {loading && <p className="muted">正在加载学习进度...</p>}
              {showEmptyStates && !learning.subjectProgress.length && <EmptyLearningState setPage={setPage} />}
              {learning.subjectProgress.map((item) => {
                const subject = item.subject || "未分类";
                const progress = Number(item.avgProgress || item.avgprogress || 0);
                return (
                  <button className="subject-progress-row" key={subject} onClick={() => openGraph(subject)}>
                    <div>
                      <strong>{subject}</strong>
                      <span>{item.recordCount || item.recordcount || 0} 条记录 / 完成 {item.completedCount || item.completedcount || 0}</span>
                    </div>
                    <ProgressBar value={progress} />
                  </button>
                );
              })}
            </div>
          </section>

          <section className="analytics-panel">
            <div className="panel-head">
              <div>
                <span className="eyebrow">已学课程</span>
                <h3>最近学习过的资源</h3>
              </div>
              <PlayCircle size={22} />
            </div>
            <div className="learning-resource-list">
              {learning.resume && !learning.resourceRecords.length && (
                <article className="learning-resource-row" key={`lesson-progress-${learning.resume.courseId}`}>
                  <div className="learning-resource-icon"><PlayCircle size={22} /></div>
                  <div>
                    <span>最近学习 / 课时进度</span>
                    <strong>{learning.resume.courseTitle}</strong>
                    <small>{learning.resume.lessonTitle} / {formatLearningDate(learning.resume.lastStudiedAt)}</small>
                  </div>
                  <ProgressBar value={resumeCourseProgress} />
                  <button onClick={() => setPage("learn", { courseId: learning.resume.courseId, lessonId: learning.resume.lessonId })}>
                    继续学习
                  </button>
                </article>
              )}
              {learning.resourceRecords.map((item) => {
                const resourceId = item.resourceId || item.resourceid;
                const progress = Number(item.progress || 0);
                return (
                  <article className="learning-resource-row" key={item.id}>
                    <div className="learning-resource-icon">
                      {item.type === "video" ? <PlayCircle size={22} /> : <FileText size={22} />}
                    </div>
                    <div>
                      <span>{item.subject} / {item.level}</span>
                      <strong>{item.title}</strong>
                      <small>{item.teacher || "平台教师"} / {formatLearningDate(item.lastLearnedAt || item.lastlearnedat)}</small>
                    </div>
                    <ProgressBar value={progress} />
                    <button disabled={Boolean(completingResourceId)} onClick={() => completeResource(resourceId)}>
                      {completingResourceId === resourceId ? "更新中..." : "标记完成"}
                    </button>
                  </article>
                );
              })}
              {showEmptyStates && !learning.resourceRecords.length && !learning.resume && <p className="muted">暂无已学资源，打开课程课时后会记录在这里。</p>}
            </div>
          </section>
        </div>

        <aside className="learning-side">
          <section className="analytics-panel learning-activity-card">
            <div className="panel-head">
              <div>
                <span className="eyebrow">最近动态</span>
                <h3>{lastActiveAt ? formatLearningDate(lastActiveAt) : "暂无学习记录"}</h3>
              </div>
              <Route size={22} />
            </div>
            <div className="activity-list">
              {learning.recentRecords.map((item) => (
                <div className="activity-item" key={item.id}>
                  <i className={item.recordType === "resource" ? "activity-resource" : "activity-node"} />
                  <div>
                    <strong>{item.title}</strong>
                    <span>{item.subject} / 进度 {item.progress || 0}%</span>
                    <small>{formatLearningDate(item.createdAt || item.createdat)}</small>
                  </div>
                </div>
              ))}
              {showEmptyStates && !learning.recentRecords.length && <p className="muted">学习课程或点击知识点后，这里会显示时间线。</p>}
            </div>
          </section>

          <section className="analytics-panel">
            <div className="panel-head">
              <div>
                <span className="eyebrow">知识点</span>
                <h3>最近点亮的知识点</h3>
              </div>
              <Sparkles size={22} />
            </div>
            <div className="node-chip-list">
              {learning.nodeRecords.map((item) => (
                <button key={item.id} onClick={() => openGraph(item.subject)}>
                  <strong>{item.knowledgeNode || item.knowledgenode}</strong>
                  <span>{item.subject} / {item.progress || 0}%</span>
                </button>
              ))}
              {showEmptyStates && !learning.nodeRecords.length && <p className="muted">还没有知识点学习记录。</p>}
            </div>
          </section>

          <section className="analytics-panel">
            <div className="panel-head">
              <div>
                <span className="eyebrow">我的收藏</span>
                <h3>已收藏资源</h3>
              </div>
              <Star size={22} />
            </div>
            <div className="favorite-mini-list">
              {learning.favorites.map((item) => (
                <button type="button" className="favorite-mini-row" key={item.id} onClick={() => setPage("course", { courseId: item.id })} aria-label={`查看收藏课程：${item.title}`}>
                  <Heart size={16} />
                  <div>
                    <strong>{item.title}</strong>
                    <span>{item.subject} / {item.type === "video" ? "视频" : "资料"}</span>
                  </div>
                </button>
              ))}
              {showEmptyStates && !learning.favorites.length && <p className="muted">还没有收藏资源。</p>}
            </div>
          </section>
        </aside>
      </section>
    </main>
  );
}

function EmptyLearningState({ setPage }) {
  return (
    <div className="empty-state-react">
      暂无学习记录，先去首页选择一门课程开始学习。
      <button onClick={() => setPage("home")}>去首页</button>
    </div>
  );
}

function ProgressBar({ value }) {
  const safeValue = Math.max(0, Math.min(Number(value || 0), 100));
  return (
    <div className="learning-progress">
      <span style={{ width: `${safeValue}%` }} />
      <b>{safeValue}%</b>
    </div>
  );
}

function formatLearningDate(value) {
  if (!value) return "暂无记录";
  const date = new Date(value);
  if (!Number.isNaN(date.getTime())) {
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false
    }).format(date).replaceAll("/", "-");
  }
  return String(value).replace("T", " ").slice(0, 19);
}

function AgentsPage({ preferences, setPage }) {
  const [outputs, setOutputs] = useState({});
  const [reports, setReports] = useState([]);
  const [busyAgents, setBusyAgents] = useState({});
  const [reportsLoading, setReportsLoading] = useState(true);
  const [reportsError, setReportsError] = useState("");
  const [reportAction, setReportAction] = useState("");
  const agents = [
    { id: "guide", title: "课程诊断", icon: Wand2, desc: "分析学习行为，推荐需要补强的课程与知识点。" },
    { id: "analysis", title: "学习分析报告", icon: FileText, desc: "生成 Word 学习分析报告，指出问题和改进建议。" },
    { id: "path", title: "学习路径分析", icon: Route, desc: "结合掌握程度生成适合当前阶段的学习路径。" },
    { id: "recommend", title: "学习资源分析", icon: Sparkles, desc: "根据学习记录和偏好推荐视频、资料与课程。" }
  ];
  const loadReports = async () => {
    setReportsLoading(true);
    setReportsError("");
    try {
      const data = await api("/agents/reports");
      setReports(data.reports || []);
    } catch (error) {
      setReportsError(error?.message || "报告历史加载失败，请稍后重试");
    } finally {
      setReportsLoading(false);
    }
  };
  const runAgent = async (id) => {
    if (busyAgents[id]) return;
    setBusyAgents((prev) => ({ ...prev, [id]: true }));
    setOutputs((prev) => ({ ...prev, [id]: null }));
    try {
      const data = await api(`/agents/${id}`, {
        method: "POST",
        body: JSON.stringify({ preferences }),
        timeout: 150000
      });
      setOutputs((prev) => ({
        ...prev,
        [id]: {
          text: data?.result || (data?.downloadUrl ? "学习分析报告已生成" : "分析已完成"),
          error: false,
          model: data?.model || "",
          tools: Array.isArray(data?.toolsUsed) ? data.toolsUsed : [],
          sources: Array.isArray(data?.sources) ? data.sources : [],
          graphUsed: Boolean(data?.graphUsed),
          learningPath: data?.learningPath || null
        }
      }));
      if (id === "analysis") await loadReports();
    } catch (error) {
      setOutputs((prev) => ({
        ...prev,
        [id]: { text: `分析失败：${error?.message || "请稍后重试"}`, error: true }
      }));
    } finally {
      setBusyAgents((prev) => ({ ...prev, [id]: false }));
    }
  };
  const deleteReport = async (id) => {
    if (!id || reportAction) return;
    setReportAction(`delete-${id}`);
    setReportsError("");
    try {
      await api(`/agents/reports/${id}`, { method: "DELETE" });
      await loadReports();
    } catch (error) {
      setReportsError(error?.message || "报告删除失败，请稍后重试");
      handleActionError(error, "报告删除失败");
    } finally {
      setReportAction("");
    }
  };
  const downloadReport = async (report) => {
    if (!report?.id || reportAction) return;
    setReportAction(`download-${report.id}`);
    setReportsError("");
    try {
      const rawUrl = report.downloadUrl || report.downloadurl || report.reportUrl || report.reporturl || `/agents/reports/${report.id}/download`;
      const downloadUrl = rawUrl.startsWith("/api/") ? rawUrl.slice(4) : rawUrl;
      if (!downloadUrl.startsWith("/agents/reports/")) throw new Error("报告下载地址无效");
      const blob = await api(downloadUrl, { responseType: "blob" });
      if (!(blob instanceof Blob) || !blob.size) throw new Error("下载的报告文件为空");
      const suggestedName = report.fileName || report.filename || `学习分析报告-${report.id}.docx`;
      const fileName = String(suggestedName).replace(/[\\/:*?"<>|]/g, "_");
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
    } catch (error) {
      setReportsError(error?.message || "报告下载失败，请稍后重试");
      handleActionError(error, "报告下载失败");
    } finally {
      setReportAction("");
    }
  };
  useEffect(() => {
    loadReports();
  }, []);
  return (
    <main className="content">
      <div className="page-title compact">
        <span className="eyebrow">学习分析</span>
        <h2>把学习记录转化为下一步行动</h2>
      </div>
      <section className="agent-grid">
        {agents.map((agent) => {
          const Icon = agent.icon;
          return (
            <article className="agent-card" key={agent.id}>
              <Icon size={28} />
              <h3>{agent.title}</h3>
              <p>{agent.desc}</p>
              <button disabled={Boolean(busyAgents[agent.id])} onClick={() => runAgent(agent.id)}>
                {busyAgents[agent.id] ? "分析中..." : "开始分析"}
              </button>
              {outputs[agent.id] && (
                <div className={`agent-output${outputs[agent.id].error ? " error" : ""}`} role={outputs[agent.id].error ? "alert" : undefined}>
                  {outputs[agent.id].text}
                  {outputs[agent.id].learningPath && <div className="agent-path-action"><p>{outputs[agent.id].learningPath.neo4jSynced ? "个人路径已生成并写入 Neo4j。" : "个人路径已保存，Neo4j 恢复后可同步。"}</p><button onClick={() => setPage("graph", { pathId: outputs[agent.id].learningPath.pathId })}>查看我的路径图谱<GitBranch size={16} /></button></div>}
                  {!outputs[agent.id].error && outputs[agent.id].model && (
                    <small>由 {outputs[agent.id].model} 生成 · 调用 {outputs[agent.id].tools.length} 个工具</small>
                  )}
                  {!outputs[agent.id].error && outputs[agent.id].tools.length > 0 && (
                    <div className="agent-evidence">
                      <span>工具：{outputs[agent.id].tools.map(agentToolLabel).join("、")}</span>
                      <span>依据：{outputs[agent.id].sources.length} 项{outputs[agent.id].graphUsed ? "（含知识图谱）" : ""}</span>
                    </div>
                  )}
                </div>
              )}
            </article>
          );
        })}
      </section>
      <section className="analytics-panel report-history">
        <div className="panel-head">
          <div>
            <span className="eyebrow">报告历史</span>
            <h3>已生成的学习分析报告</h3>
          </div>
          <FileText size={22} />
        </div>
        <div className="report-list">
          {reportsError && (
            <div className="inline-error-state" role="alert">
              <span>{reportsError}</span>
              <button type="button" onClick={loadReports} disabled={reportsLoading}>{reportsLoading ? "重试中..." : "重新加载"}</button>
            </div>
          )}
          {reportsLoading && <p className="muted">正在加载报告历史...</p>}
          {reports.map((report) => (
            <div className="report-row" key={report.id}>
              <div>
                <strong>{report.outputResult || report.outputresult || "学习分析报告"}</strong>
                <span>{String(report.createdAt || report.createdat || "").replace("T", " ")}</span>
              </div>
              <div className="report-actions">
                <button disabled={Boolean(reportAction)} onClick={() => downloadReport(report)}>
                  {reportAction === `download-${report.id}` ? "下载中..." : "下载"}
                </button>
                <button disabled={Boolean(reportAction)} onClick={() => deleteReport(report.id)}>
                  {reportAction === `delete-${report.id}` ? "删除中..." : "删除"}
                </button>
              </div>
            </div>
          ))}
          {!reportsLoading && !reportsError && !reports.length && <p className="muted">暂无报告，运行“学习分析报告”后会出现在这里。</p>}
        </div>
      </section>
    </main>
  );
}

function agentToolLabel(tool) {
  const labels = {
    get_learning_profile: "学习概况",
    get_weak_knowledge: "薄弱知识点",
    get_learning_resources: "课程资源",
    get_graph_prerequisites: "图谱先修关系"
  };
  return labels[tool] || tool;
}

function MessagesPage({ user, setPage }) {
  const [data, setData] = useState({ contacts: [], messages: [], courseReplies: [], teachingFeedback: [], unreadCount: 0 });
  const [selectedContactId, setSelectedContactId] = useState(null);
  const [activeView, setActiveView] = useState("chat");
  const [searchText, setSearchText] = useState("");
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");

  const field = (item, key) => item?.[key] ?? item?.[key.toLowerCase()];
  const loadCenter = async (silent = false) => {
    if (!silent) setLoading(true);
    setError("");
    try {
      const result = await api("/messages/center");
      const nextData = {
        contacts: result.contacts || [],
        messages: result.messages || [],
        courseReplies: result.courseReplies || [],
        teachingFeedback: result.teachingFeedback || [],
        unreadCount: Number(result.unreadCount || 0)
      };
      setData(nextData);
      setSelectedContactId((current) => {
        if (current && nextData.contacts.some((contact) => Number(contact.id) === Number(current))) return current;
        const recent = nextData.contacts.find((contact) => field(contact, "lastMessage"));
        return recent?.id || nextData.contacts[0]?.id || null;
      });
    } catch (loadError) {
      if (!silent) setError(loadError?.message || "消息中心加载失败，请稍后重试");
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    loadCenter();
    const timer = window.setInterval(() => loadCenter(true), 15000);
    return () => window.clearInterval(timer);
  }, []);

  const selectContact = async (contact) => {
    setSelectedContactId(contact.id);
    setActiveView("chat");
    try {
      await api("/messages/read", { method: "POST", body: JSON.stringify({ senderId: contact.id }) });
      setData((current) => ({
        ...current,
        unreadCount: Math.max(0, current.unreadCount - Number(field(contact, "unreadCount") || 0)),
        contacts: current.contacts.map((item) => Number(item.id) === Number(contact.id) ? { ...item, unreadCount: 0 } : item),
        messages: current.messages.map((message) => Number(field(message, "senderId")) === Number(contact.id)
          && Number(field(message, "recipientId")) === Number(user.id) ? { ...message, isRead: 1 } : message)
      }));
    } catch {
      // 即使已读状态更新失败，也允许继续查看当前对话。
    }
  };

  const sendMessage = async (event) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content || !selectedContactId || sending) return;
    setSending(true);
    setError("");
    try {
      await api("/messages", { method: "POST", body: JSON.stringify({ recipientId: selectedContactId, content }) });
      setDraft("");
      await loadCenter(true);
    } catch (sendError) {
      setError(sendError?.message || "消息发送失败，请稍后重试");
    } finally {
      setSending(false);
    }
  };

  const filteredContacts = data.contacts.filter((contact) => {
    const text = searchText.trim().toLowerCase();
    return !text || `${contact.name || ""} ${contact.role || ""}`.toLowerCase().includes(text);
  });
  const selectedContact = data.contacts.find((contact) => Number(contact.id) === Number(selectedContactId));
  const currentMessages = data.messages.filter((message) => {
    const senderId = Number(field(message, "senderId"));
    const recipientId = Number(field(message, "recipientId"));
    return (senderId === Number(user.id) && recipientId === Number(selectedContactId))
      || (senderId === Number(selectedContactId) && recipientId === Number(user.id));
  });
  const courseUpdates = [...data.courseReplies.map((item) => ({ ...item, updateKind: "reply" })),
    ...data.teachingFeedback.map((item) => ({ ...item, updateKind: field(item, "itemType") || "feedback" }))]
    .sort((left, right) => String(field(right, "createdAt") || "").localeCompare(String(field(left, "createdAt") || "")));
  const roleName = (role) => role === "teacher" ? "教师" : role === "student" ? "学生" : "用户";

  return (
    <main className="content message-center-page">
      <section className="message-center-hero">
        <div><span className="studio-kicker">MESSAGES & REPLIES</span><h1>消息中心</h1><p>集中查看课程回复、学生反馈，并与平台中的老师或同学直接交流。</p></div>
        <div className="message-summary"><MessageCircle size={23} /><span>未读消息</span><strong>{data.unreadCount}</strong></div>
      </section>

      <div className="message-view-tabs">
        <button className={activeView === "chat" ? "active" : ""} onClick={() => setActiveView("chat")}>对话消息</button>
        <button className={activeView === "updates" ? "active" : ""} onClick={() => setActiveView("updates")}>课程回复与反馈 <span>{courseUpdates.length}</span></button>
      </div>
      {error && <div className="inline-error-state" role="alert"><span>{error}</span><button onClick={() => loadCenter()}>重新加载</button></div>}

      {activeView === "chat" ? (
        <section className="message-workspace">
          <aside className="contact-panel">
            <header><div><span className="eyebrow">联系人</span><h2>开始对话</h2></div><Users size={20} /></header>
            <label className="contact-search"><Search size={16} /><input value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="搜索老师或同学" /></label>
            <div className="contact-list">
              {filteredContacts.map((contact) => (
                <button className={Number(contact.id) === Number(selectedContactId) ? "active" : ""} key={contact.id} onClick={() => selectContact(contact)}>
                  <span className="contact-avatar">{String(contact.name || "用").slice(0, 1)}</span>
                  <span className="contact-copy"><strong>{contact.name}</strong><small>{field(contact, "lastMessage") || roleName(contact.role)}</small></span>
                  {Number(field(contact, "unreadCount") || 0) > 0 && <b>{field(contact, "unreadCount")}</b>}
                </button>
              ))}
              {!loading && !filteredContacts.length && <p className="message-empty-copy">暂无可用联系人。</p>}
            </div>
          </aside>

          <section className="conversation-panel">
            {selectedContact ? (
              <>
                <header><span className="contact-avatar large">{String(selectedContact.name || "用").slice(0, 1)}</span><div><h2>{selectedContact.name}</h2><p>{roleName(selectedContact.role)} · 可以发送课程问题和学习建议</p></div></header>
                <div className="conversation-stream">
                  {currentMessages.map((message) => {
                    const mine = Number(field(message, "senderId")) === Number(user.id);
                    return <article className={mine ? "mine" : "theirs"} key={message.id}><span>{message.content}</span><small>{formatLearningDate(field(message, "createdAt"))}</small></article>;
                  })}
                  {!loading && !currentMessages.length && <div className="conversation-empty"><MessageCircle size={30} /><strong>还没有对话记录</strong><p>发送第一条消息，开始交流课程或学习问题。</p></div>}
                </div>
                <form className="message-composer" onSubmit={sendMessage}>
                  <textarea maxLength={2000} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={`给 ${selectedContact.name} 发送消息…`} />
                  <div><small>{draft.length}/2000</small><button disabled={sending || !draft.trim()} type="submit">{sending ? "发送中…" : "发送消息"}<Send size={16} /></button></div>
                </form>
              </>
            ) : <div className="conversation-empty centered"><Users size={34} /><strong>选择一个联系人</strong><p>从左侧选择老师或同学后即可开始对话。</p></div>}
          </section>
        </section>
      ) : (
        <section className="course-update-panel">
          <header><div><span className="eyebrow">COURSE ACTIVITY</span><h2>{user.role === "teacher" ? "学生反馈与收到的回复" : "老师给你的课程回复"}</h2></div><MessageCircle size={22} /></header>
          <div className="course-update-list">
            {courseUpdates.map((item, index) => {
              const isReply = item.updateKind === "reply";
              const courseTitle = field(item, "courseTitle") || "相关课程";
              const actorName = field(item, "actorName") || (isReply ? "课程教师" : "课程学员");
              const kindLabel = isReply ? (field(item, "targetType") === "review" ? "回复了你的评价" : "回复了你的问题") : item.updateKind === "review" ? "提交了课程评价" : "提出了课程问题";
              return <article key={`${item.updateKind}-${item.id}-${index}`}>
                <span className="contact-avatar">{String(actorName).slice(0, 1)}</span>
                <div><header><strong>{actorName}</strong><span>{kindLabel}</span><small>{formatLearningDate(field(item, "createdAt"))}</small></header><h3>{courseTitle}</h3>{field(item, "sourceContent") && <blockquote>你原来的内容：{field(item, "sourceContent")}</blockquote>}<p>{item.content}</p></div>
                {field(item, "courseId") && <button onClick={() => setPage("course", { courseId: field(item, "courseId") })}>查看课程<ArrowRight size={14} /></button>}
              </article>;
            })}
            {!loading && !courseUpdates.length && <div className="conversation-empty"><MessageCircle size={30} /><strong>暂时没有课程回复</strong><p>课程中的提问、评价和教师回复会集中显示在这里。</p></div>}
          </div>
        </section>
      )}
    </main>
  );
}

function TeacherDashboard({ resources, setResources, setResourceMeta }) {
  const editorRef = useRef(null);
  const titleInputRef = useRef(null);
  const [form, setForm] = useState({ title: "", subject: "Java", type: "video", level: "入门", description: "", content: "" });
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [resourceNotice, setResourceNotice] = useState("");
  const [resourceError, setResourceError] = useState("");
  const [analytics, setAnalytics] = useState(() => ({
    summary: {},
    resourceStats: [],
    popularResources: [],
    warningStudents: [],
    weakNodes: [],
    reviews: [],
    questions: []
  }));
  const [analyticsLoading, setAnalyticsLoading] = useState(true);
  const [analyticsError, setAnalyticsError] = useState("");
  const [replyDrafts, setReplyDrafts] = useState({});
  const [replyingKey, setReplyingKey] = useState("");

  const loadAnalytics = async () => {
    setAnalyticsLoading(true);
    setAnalyticsError("");
    try {
      const data = await api("/teacher/analytics");
      setAnalytics({
        summary: data.summary || {},
        resourceStats: data.resourceStats || [],
        popularResources: data.popularResources || [],
        warningStudents: data.warningStudents || [],
        weakNodes: data.weakNodes || [],
        reviews: data.reviews || [],
        questions: data.questions || []
      });
    } catch (error) {
      setAnalyticsError(error?.message || "教学分析数据加载失败，请稍后重试");
    } finally {
      setAnalyticsLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
    refreshResources().catch((error) => setResourceError(error?.message || "投稿列表加载失败"));
  }, []);

  const resetForm = () => {
    setEditingId(null);
    setResourceNotice("");
    setResourceError("");
    setForm({ title: "", subject: "Java", type: "video", level: "入门", description: "", content: "" });
    setFileInputKey((current) => current + 1);
  };

  const refreshResources = async () => {
    const data = await api("/teacher/resources");
    setResources(data.resources || []);
    setResourceMeta?.({
      page: data.page || 1,
      size: data.size || 12,
      total: data.total || 0,
      totalPages: data.totalPages || 1
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    if (saving || deletingId !== null) return;
    setSaving(true);
    setResourceNotice("");
    setResourceError("");
    try {
      if (editingId) {
        await api(`/teacher/resources/${editingId}`, { method: "PUT", body: JSON.stringify(form) });
      } else {
        const formData = new FormData();
        Object.entries(form).forEach(([key, value]) => {
          if (value !== undefined && value !== null) formData.append(key, value);
        });
        await api("/teacher/resources", { method: "POST", body: formData });
      }
      const successMessage = editingId ? "修改已提交，等待管理员重新审核" : "课程作品已提交，管理员审核通过后将向学生发布";
      resetForm();
      setResourceNotice(successMessage);
      try {
        await refreshResources();
      } catch (refreshError) {
        setResourceError(`${successMessage}，但课程列表刷新失败：${refreshError?.message || "请手动刷新"}`);
        handleActionError(refreshError, "课程列表刷新失败");
      }
      await loadAnalytics();
    } catch (error) {
      setResourceError(error?.message || "资源保存失败，请稍后重试");
      handleActionError(error, "资源保存失败");
    } finally {
      setSaving(false);
    }
  };

  const startEdit = (item) => {
    if (!item?.id || saving || deletingId !== null) return;
    setResourceNotice(`正在编辑：${item.title}`);
    setResourceError("");
    setEditingId(item.id);
    setForm({
      title: item.title || "",
      subject: item.subject || "Java",
      type: item.type || "material",
      level: item.level || "入门",
      description: item.description || "",
      content: item.content || buildResourceTextContent(item)
    });
    window.requestAnimationFrame(() => {
      editorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      titleInputRef.current?.focus({ preventScroll: true });
    });
  };

  const deleteResource = async (id) => {
    if (!id || saving || deletingId !== null) return;
    setDeletingId(id);
    setResourceNotice("");
    setResourceError("");
    try {
      await api(`/teacher/resources/${id}`, { method: "DELETE" });
      if (editingId === id) resetForm();
      setResourceNotice("课程资源已删除");
      try {
        await refreshResources();
      } catch (refreshError) {
        setResourceError(`资源已删除，但课程列表刷新失败：${refreshError?.message || "请手动刷新"}`);
        handleActionError(refreshError, "课程列表刷新失败");
      }
      await loadAnalytics();
    } catch (error) {
      setResourceError(error?.message || "资源删除失败，请稍后重试");
      handleActionError(error, "资源删除失败");
    } finally {
      setDeletingId(null);
    }
  };

  const submitTeacherReply = async (type, item) => {
    const key = `${type}-${item.id}`;
    const content = String(replyDrafts[key] || "").trim();
    if (!content || replyingKey) return;
    setReplyingKey(key);
    setAnalyticsError("");
    try {
      await api(`/teacher/feedback/${type}/${item.id}/reply`, {
        method: "POST",
        body: JSON.stringify({ content })
      });
      setReplyDrafts((current) => ({ ...current, [key]: "" }));
      setResourceNotice("回复已发送，学生可在课程页面查看。");
      await loadAnalytics();
    } catch (error) {
      setAnalyticsError(error?.message || "回复发送失败，请稍后重试");
    } finally {
      setReplyingKey("");
    }
  };

  return (
    <main className="content teacher-dashboard">
      <div className="page-title compact">
        <span className="eyebrow">TEACHER STUDIO</span>
        <h2>课程创作、审核反馈与学习洞察</h2>
        <p>教师可上传视频和文字课程；所有新作品与修改都会进入管理员审核，审核通过后才向学生开放。</p>
      </div>

      <section className="teacher-layout">
        <section id="teacher-course-editor" className="teacher-course-editor" ref={editorRef}>
          <div className="page-title compact">
            <span className="eyebrow">资源管理</span>
            <h2>{editingId ? "修改课程作品" : "提交视频或文字课程"}</h2>
          </div>
          {resourceNotice && <p className="inline-success-state" role="status">{resourceNotice}</p>}
          {resourceError && <p className="inline-error-state" role="alert">{resourceError}</p>}
          <form className="teacher-form" onSubmit={submit} aria-busy={saving}>
            <input ref={titleInputRef} placeholder="课程或资源标题" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <select value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })}>{subjects.map((s) => <option key={s}>{s}</option>)}</select>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="video">视频</option><option value="material">学习资料</option></select>
            <select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })}><option>入门</option><option>进阶</option><option>项目实战</option></select>
            <textarea placeholder="内容简介" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <textarea className="content-textarea" placeholder="文本学习内容，不填则系统自动生成不少于 500 字的学习正文" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
            {!editingId && <input key={fileInputKey} type="file" accept="video/*,.pdf,.doc,.docx,.ppt,.pptx,.txt" onChange={(e) => setForm({ ...form, file: e.target.files?.[0] })} />}
            <button className="primary-btn" type="submit" disabled={saving || deletingId !== null}>
              <Plus size={18} /> {saving ? "提交中..." : editingId ? "提交重新审核" : "提交管理员审核"}
            </button>
            {editingId && <button type="button" disabled={saving || deletingId !== null} onClick={resetForm}>取消编辑</button>}
          </form>
        </section>

        <section>
          <div className="page-title compact">
            <span className="eyebrow">班级学习看板</span>
            <h2>教师页面数据概览</h2>
          </div>
          {analyticsError && (
            <div className="inline-error-state" role="alert">
              <span>{analyticsError}</span>
              <button type="button" onClick={loadAnalytics} disabled={analyticsLoading}>{analyticsLoading ? "重试中..." : "重新加载"}</button>
            </div>
          )}
          <section className="teacher-overview">
            <div className="analytics-grid">
              <AnalyticsCard icon={Users} label="学生总人数" value={analytics.summary.studentCount || 0} tone="blue" />
              <AnalyticsCard icon={TrendingUp} label="近7天活跃" value={analytics.summary.activeStudents || 0} tone="green" />
              <AnalyticsCard icon={CheckCircle2} label="课程完成率" value={`${analytics.summary.courseCompletionRate || 0}%`} tone="teal" />
              <AnalyticsCard icon={BookOpen} label="资源总数" value={analytics.summary.resourceCount || resources.length} tone="orange" />
              <AnalyticsCard icon={Clock3} label="待审核作品" value={analytics.summary.pendingCount || 0} tone="orange" />
            </div>
          </section>

          <section className="teacher-insights">
            <div className="analytics-panel">
              <div className="panel-head">
                <div>
                  <span className="eyebrow">学生预警列表</span>
                  <h3>需要关注的学生</h3>
                </div>
                <AlertTriangle size={22} />
              </div>
              <div className="warning-list">
                {analyticsLoading && <p className="muted">正在加载预警数据...</p>}
                {!analyticsLoading && !analytics.warningStudents.length && <p className="muted">暂无需要预警的学生</p>}
                {analytics.warningStudents.map((student) => (
                  <div className="warning-item" key={student.id}>
                    <div>
                      <strong>{student.name}</strong>
                      <span>{formatWarningReason(student.reason)}</span>
                    </div>
                    <div className="warning-metrics">
                      <span>{student.avgProgress || 0}% 进度</span>
                      <span>{student.inactiveDays || 0} 天未活跃</span>
                      <b className={student.riskLevel === "high" ? "risk-high" : "risk-medium"}>{student.riskLevel === "high" ? "高风险" : "中风险"}</b>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="analytics-panel">
              <div className="panel-head">
                <div>
                  <span className="eyebrow">热门与薄弱</span>
                  <h3>教学关注点</h3>
                </div>
                <BarChart3 size={22} />
              </div>
              <div className="rank-block">
                <h4>热门课程</h4>
                {analytics.popularResources.slice(0, 4).map((item, index) => (
                  <div className="rank-row" key={item.id}>
                    <span>{index + 1}</span>
                    <strong>{item.title}</strong>
                    <em>{item.playCount || 0} 次学习</em>
                  </div>
                ))}
                {!analytics.popularResources.length && <p className="muted">暂无学习数据</p>}
              </div>
              <div className="rank-block">
                <h4>薄弱知识点</h4>
                {analytics.weakNodes.slice(0, 4).map((item, index) => (
                  <div className="rank-row" key={`${item.subject}-${item.node}-${index}`}>
                    <span>{index + 1}</span>
                    <strong>{item.node}</strong>
                    <em>{item.avgProgress || 0}% 平均进度</em>
                  </div>
                ))}
                {!analytics.weakNodes.length && <p className="muted">暂无薄弱知识点数据</p>}
              </div>
            </div>
          </section>
        </section>
      </section>

      <section className="analytics-panel resource-analysis">
        <div className="panel-head">
          <div>
            <span className="eyebrow">课程资源数据分析</span>
            <h3>每个资源的播放次数、完成率、收藏数、平均学习进度</h3>
          </div>
          <Eye size={22} />
        </div>
        <ResourceStatsTable rows={analytics.resourceStats} loading={analyticsLoading} />
      </section>

      <section className="teacher-feedback-section">
        <div className="page-title compact">
          <span className="eyebrow">STUDENT VOICE</span>
          <h2>学生评价与课程反馈</h2>
          <p>集中查看学生对你所发布课程的评价和问题，及时优化章节内容。</p>
        </div>
        <div className="teacher-feedback-grid">
          <section className="analytics-panel">
            <div className="panel-head"><div><span className="eyebrow">课程评价</span><h3>最新学生评价</h3></div><Star size={21} /></div>
            <div className="teacher-feedback-list">
              {analytics.reviews.map((item) => {
                const replyKey = `review-${item.id}`;
                return <article key={item.id}>
                  <header><strong>{item.resourceTitle}</strong><span>{Array.from({ length: 5 }, (_, index) => <Star key={index} size={12} className={index < item.rating ? "filled" : ""} />)}</span></header>
                  <p>{item.content}</p>
                  <small>{item.authorName} · {formatCommunityDate(item.updatedAt)} · 已回复 {item.replyCount || 0} 次</small>
                  <div className="teacher-reply-form">
                    <textarea maxLength={1000} value={replyDrafts[replyKey] || ""} onChange={(event) => setReplyDrafts((current) => ({ ...current, [replyKey]: event.target.value }))} placeholder="回复这条课程评价…" />
                    <button type="button" disabled={!String(replyDrafts[replyKey] || "").trim() || Boolean(replyingKey)} onClick={() => submitTeacherReply("review", item)}>{replyingKey === replyKey ? "发送中…" : "回复"}<Send size={13} /></button>
                  </div>
                </article>;
              })}
              {!analytics.reviews.length && <p className="muted">暂时没有学生评价。</p>}
            </div>
          </section>
          <section className="analytics-panel">
            <div className="panel-head"><div><span className="eyebrow">课程问答</span><h3>学生最近提出的问题</h3></div><MessageCircle size={21} /></div>
            <div className="teacher-feedback-list">
              {analytics.questions.map((item) => {
                const replyKey = `question-${item.id}`;
                return <article key={item.id}>
                  <header><strong>{item.resourceTitle}</strong><span>{item.answerCount || 0} 条讨论</span></header>
                  <p>{item.content}</p>
                  <small>{item.authorName} · {formatCommunityDate(item.createdAt)} · 教师回复 {item.replyCount || 0} 次</small>
                  <div className="teacher-reply-form">
                    <textarea maxLength={1000} value={replyDrafts[replyKey] || ""} onChange={(event) => setReplyDrafts((current) => ({ ...current, [replyKey]: event.target.value }))} placeholder="回复学生问题…" />
                    <button type="button" disabled={!String(replyDrafts[replyKey] || "").trim() || Boolean(replyingKey)} onClick={() => submitTeacherReply("question", item)}>{replyingKey === replyKey ? "发送中…" : "回复"}<Send size={13} /></button>
                  </div>
                </article>;
              })}
              {!analytics.questions.length && <p className="muted">暂时没有课程问题。</p>}
            </div>
          </section>
        </div>
      </section>

      <section>
        <div className="page-title compact">
          <span className="eyebrow">SUBMISSIONS</span>
          <h2>我的课程作品与审核状态</h2>
        </div>
        <div className="teacher-course-list">
          {resources.map((item) => (
            <div className={`teacher-resource-item${editingId === item.id ? " is-editing" : ""}`} key={item.id}>
              <div className={`review-status-chip ${item.reviewStatus || "pending"}`}>
                {item.reviewStatus === "approved" ? "已通过" : item.reviewStatus === "rejected" ? "已驳回" : "审核中"}
              </div>
              <ResourceCard item={item} />
              {item.reviewFeedback && <p className="teacher-review-feedback"><MessageCircle size={14} /><span><strong>管理员反馈</strong>{item.reviewFeedback}</span></p>}
              <div className="teacher-resource-actions">
                <button type="button" aria-controls="teacher-course-editor" aria-pressed={editingId === item.id} disabled={saving || deletingId !== null} onClick={() => startEdit(item)}>编辑</button>
                <button disabled={saving || deletingId !== null} onClick={() => deleteResource(item.id)}>
                  {deletingId === item.id ? "删除中..." : "删除"}
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}

function AdminDashboard({ setPage }) {
  const [data, setData] = useState({ summary: {}, pendingResources: [], resources: [], reviews: [], questions: [], health: {}, auditLogs: [] });
  const [activeTab, setActiveTab] = useState(() => {
    const storedTab = sessionStorage.getItem("learning_admin_active_tab");
    return ["review", "content", "community", "maintenance"].includes(storedTab) ? storedTab : "review";
  });
  const [feedbacks, setFeedbacks] = useState({});
  const [loading, setLoading] = useState(true);
  const [busyAction, setBusyAction] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    sessionStorage.setItem("learning_admin_active_tab", activeTab);
  }, [activeTab]);
  const [error, setError] = useState("");

  const loadDashboard = async () => {
    setLoading(true);
    setError("");
    try {
      const result = await api("/admin/dashboard");
      setData({
        summary: result.summary || {},
        pendingResources: result.pendingResources || [],
        resources: result.resources || [],
        reviews: result.reviews || [],
        questions: result.questions || [],
        health: result.health || {},
        auditLogs: result.auditLogs || []
      });
    } catch (loadError) {
      setError(loadError?.message || "管理数据加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  const runAction = async (key, requestAction, successMessage) => {
    if (busyAction) return;
    setBusyAction(key);
    setNotice("");
    setError("");
    try {
      const result = await requestAction();
      setNotice(result?.detail || successMessage);
      await loadDashboard();
    } catch (actionError) {
      setError(actionError?.message || "操作失败，请稍后重试");
    } finally {
      setBusyAction("");
    }
  };

  const reviewResource = (resource, action) => {
    const feedback = String(feedbacks[resource.id] || "").trim();
    if (action === "reject" && !feedback) {
      setError("驳回课程时需要填写明确的修改意见。");
      return;
    }
    runAction(`review-${resource.id}`, () => api(`/admin/resources/${resource.id}/review`, {
      method: "POST",
      body: JSON.stringify({ action, feedback })
    }), action === "approve" ? "课程已审核通过并发布" : "课程已驳回并反馈给教师");
  };

  const tabs = [
    { id: "review", label: "内容审核", count: data.pendingResources.length },
    { id: "content", label: "课程与视频", count: data.resources.length },
    { id: "community", label: "评论与问答", count: data.reviews.length + data.questions.length },
    { id: "maintenance", label: "站点维护", count: data.health.publicCourseIssueCount || 0 }
  ];

  return (
    <main className="content admin-dashboard">
      <section className="admin-hero">
        <div><span className="studio-kicker">ADMIN OPERATIONS</span><h1>内容治理与站点运行中心</h1><p>审核教师作品、管理课程与社区内容，并通过安全检查快速发现和修复常见数据问题。</p></div>
        <div className="admin-live-status"><span><i />系统运行中</span><strong>{data.summary.pendingCount || 0}</strong><small>项内容等待处理</small></div>
      </section>

      {notice && <p className="inline-success-state" role="status">{notice}</p>}
      {error && <div className="inline-error-state" role="alert"><span>{error}</span><button onClick={loadDashboard}>重新加载</button></div>}

      <section className="admin-summary-grid">
        <article><Users size={20} /><span>学生账户</span><strong>{data.summary.studentCount || 0}</strong></article>
        <article><GraduationCap size={20} /><span>教师账户</span><strong>{data.summary.teacherCount || 0}</strong></article>
        <article><Clock3 size={20} /><span>待审核作品</span><strong>{data.summary.pendingCount || 0}</strong></article>
        <article><Video size={20} /><span>已发布课程</span><strong>{data.summary.publishedCount || 0}</strong></article>
        <article><MessageCircle size={20} /><span>社区内容</span><strong>{Number(data.summary.reviewCount || 0) + Number(data.summary.questionCount || 0)}</strong></article>
      </section>

      <nav className="admin-tabs">
        {tabs.map((tab) => <button className={activeTab === tab.id ? "active" : ""} key={tab.id} onClick={() => setActiveTab(tab.id)}>{tab.label}<span>{tab.count}</span></button>)}
      </nav>

      {loading ? <div className="admin-loading">正在同步管理数据...</div> : (
        <section className="admin-workspace">
          {activeTab === "review" && (
            <div>
              <div className="admin-section-head"><div><span className="studio-kicker">REVIEW QUEUE</span><h2>教师课程投稿审核</h2></div><p>只有审核通过的课程才会进入学生端。</p></div>
              <div className="admin-review-list">
                {data.pendingResources.map((item) => (
                  <article key={item.id}>
                    <div className="admin-resource-icon">{item.type === "video" ? <Video size={23} /> : <FileText size={23} />}</div>
                    <div className="admin-resource-copy"><span>{item.subject} · {item.type === "video" ? "视频课程" : "文字资料"}</span><h3>{item.title}</h3><p>提交教师：{item.teacher} · {item.chapterCount || 0} 章 · {item.publishedLessonCount || 0}/{item.totalLessonCount || 0} 节 · {item.playableVideoCount || 0} 个视频</p>{!item.contentComplete && <small className="admin-content-warning">{(item.reasons || []).join("；")}</small>}</div>
                    <textarea value={feedbacks[item.id] || ""} onChange={(event) => setFeedbacks((current) => ({ ...current, [item.id]: event.target.value }))} maxLength={500} placeholder="填写审核意见；驳回时必填" />
                    <div className="admin-review-actions"><button type="button" disabled={!!busyAction} onClick={() => setPage("course", { courseId: item.id })}><Eye size={15} />预览</button><button className="approve" disabled={!!busyAction || !item.contentComplete || item.ownershipMissing} onClick={() => reviewResource(item, "approve")}><CheckCircle2 size={15} />通过并发布</button><button className="reject" disabled={!!busyAction} onClick={() => reviewResource(item, "reject")}><CircleX size={15} />驳回修改</button></div>
                  </article>
                ))}
                {!data.pendingResources.length && <div className="admin-empty"><CheckCircle2 size={30} /><strong>审核队列已清空</strong><p>当前没有等待管理员处理的课程作品。</p></div>}
              </div>
            </div>
          )}

          {activeTab === "content" && (
            <div>
              <div className="admin-section-head"><div><span className="studio-kicker">CONTENT LIBRARY</span><h2>课程与视频管理</h2></div><p>控制已审核内容是否向学生开放。</p></div>
              <div className="admin-table-wrap">
                <table className="admin-table"><thead><tr><th>课程资源</th><th>教师</th><th>结构</th><th>视频</th><th>完整性</th><th>可见性</th><th>操作</th></tr></thead><tbody>
                  {data.resources.map((item) => {
                    const archived = item.lifecycleStatus === "archived";
                    return (
                      <tr key={item.id}>
                        <td><strong>{item.title}</strong><small>{item.subject} · {item.type === "video" ? "视频" : "文字"}</small></td>
                        <td>{item.ownershipMissing ? "教师待补充" : item.teacher}</td>
                        <td>{item.chapterCount || 0} 章<small>{item.publishedLessonCount || 0}/{item.totalLessonCount || 0} 节</small></td>
                        <td>{item.playableVideoCount || 0} 个</td>
                        <td><span className={`review-status-chip ${item.contentComplete ? "approved" : "rejected"}`}>{item.contentComplete ? "完整" : "不完整"}</span>{!item.contentComplete && <small title={(item.reasons || []).join("；")}>{(item.reasons || ["内容待补充"])[0]}</small>}</td>
                        <td>{archived ? "已归档" : Number(item.status) === 1 ? "已上架" : "已下架"}{archived && <small title={item.archiveReason || "历史课程归档"}>保留数据</small>}</td>
                        <td>
                          <button type="button" disabled={!!busyAction} onClick={() => setPage("course", { courseId: item.id })}>预览</button>
                          <button disabled={!!busyAction || archived || item.reviewStatus !== "approved" || (Number(item.status) !== 1 && (!item.contentComplete || item.ownershipMissing))} onClick={() => runAction(`visibility-${item.id}`, () => api(`/admin/resources/${item.id}/visibility`, { method: "POST", body: JSON.stringify({ visible: Number(item.status) !== 1 }) }), Number(item.status) === 1 ? "课程已下架" : "课程已恢复上架")}>{Number(item.status) === 1 ? "下架" : "恢复"}</button>
                          <button disabled={!!busyAction || Number(item.status) === 1} onClick={() => runAction(`archive-${item.id}`, () => api(`/admin/resources/${item.id}/archive`, { method: "POST", body: JSON.stringify({ archived: !archived, reason: archived ? "" : "管理员在课程内容治理中归档" }) }), archived ? "课程已取消归档，保持下架状态" : "课程已归档并保留历史数据")}>{archived ? "取消归档" : "归档"}</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody></table>
              </div>
            </div>
          )}

          {activeTab === "community" && (
            <div>
              <div className="admin-section-head"><div><span className="studio-kicker">COMMUNITY MODERATION</span><h2>评价、反馈与问答管理</h2></div><p>清理违规内容，同时保留正常学习反馈。</p></div>
              <div className="admin-community-grid">
                <section><header><Star size={18} /><strong>课程评价</strong><span>{data.reviews.length}</span></header>{data.reviews.map((item) => <article key={item.id}><div><strong>{item.resourceTitle}</strong><small>{item.authorName} · {item.rating} 星</small><p>{item.content}</p></div><button title="删除评价" disabled={!!busyAction} onClick={() => runAction(`review-delete-${item.id}`, () => api(`/admin/reviews/${item.id}`, { method: "DELETE" }), "评价已删除")}><Trash2 size={15} /></button></article>)}</section>
                <section><header><MessageCircle size={18} /><strong>课程问答</strong><span>{data.questions.length}</span></header>{data.questions.map((item) => <article key={item.id}><div><strong>{item.resourceTitle}</strong><small>{item.authorName} · {formatCommunityDate(item.createdAt)}</small><p>{item.content}</p></div><button title="删除问题" disabled={!!busyAction} onClick={() => runAction(`question-delete-${item.id}`, () => api(`/admin/questions/${item.id}`, { method: "DELETE" }), "问题已删除")}><Trash2 size={15} /></button></article>)}</section>
              </div>
            </div>
          )}

          {activeTab === "maintenance" && (
            <div>
              <div className="admin-section-head"><div><span className="studio-kicker">SITE HEALTH</span><h2>站点检查与快速修复</h2></div><button className="admin-repair-button" disabled={!!busyAction} onClick={() => runAction("repair", () => api("/admin/repair", { method: "POST" }), "站点数据检查与修复已完成")}><Wrench size={16} />{busyAction === "repair" ? "修复中..." : "运行安全修复"}</button></div>
              <div className="admin-health-grid"><article><ShieldCheck size={22} /><span>数据库连接</span><strong>{data.health.database ? "正常" : "异常"}</strong></article><article><AlertTriangle size={22} /><span>公开课程问题</span><strong>{data.health.publicCourseIssueCount || 0}</strong></article><article><Wrench size={22} /><span>待治理课程</span><strong>{data.health.incompleteCourseCount || 0}</strong></article><article><Clock3 size={22} /><span>待审核内容</span><strong>{data.health.pendingReview || 0}</strong></article><article><Eye size={22} /><span>归档课程</span><strong>{data.health.archivedResources || 0}</strong></article></div>
              {!!data.health.incompleteCourses?.length && <div className="admin-audit"><h3>课程内容完整性问题</h3>{data.health.incompleteCourses.map((item) => <div key={`health-${item.courseId}`}><span>课程 #{item.courseId}</span><strong>{item.title}</strong><small>{(item.reasons || []).join("；")}</small></div>)}</div>}
              <div className="admin-audit"><h3>最近管理操作</h3>{data.auditLogs.map((item) => <div key={item.id}><span>{item.actionType}</span><strong>{item.detail || `${item.targetType} #${item.targetId || "system"}`}</strong><small>{item.adminName} · {formatCommunityDate(item.createdAt)}</small></div>)}</div>
            </div>
          )}
        </section>
      )}
    </main>
  );
}

function AnalyticsCard({ icon: Icon, label, value, tone }) {
  return (
    <article className={`analytics-card ${tone}`}>
      <Icon size={24} />
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function formatWarningReason(reason) {
  const map = {
    "No learning records": "暂无学习记录",
    "No learning activity for over 7 days": "超过7天未产生学习记录",
    "Too many weak knowledge nodes": "薄弱知识点较多",
    "Average learning progress is low": "平均学习进度偏低",
    "Needs teacher attention": "需要教师关注"
  };
  return map[reason] || reason || "需要教师关注";
}

function ResourceStatsTable({ rows, loading }) {
  if (loading) {
    return <p className="muted">正在加载资源统计...</p>;
  }
  if (!rows.length) {
    return <p className="muted">暂无资源统计数据</p>;
  }
  return (
    <div className="data-table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>资源名称</th>
            <th>方向</th>
            <th><Eye size={15} /> 播放次数</th>
            <th><CheckCircle2 size={15} /> 完成率</th>
            <th><Star size={15} /> 收藏数</th>
            <th>平均进度</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item) => (
            <tr key={item.id}>
              <td>{item.title}</td>
              <td>{item.subject}</td>
              <td>{item.playCount || 0}</td>
              <td>{item.completionRate || 0}%</td>
              <td>{item.favoriteCount || 0}</td>
              <td>
                <div className="progress-cell">
                  <span style={{ width: `${Math.min(Number(item.avgProgress || 0), 100)}%` }} />
                  <b>{item.avgProgress || 0}%</b>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const rootElement = document.getElementById("root");
const appRoot = window.__zhiduLearningRoot || createRoot(rootElement);
window.__zhiduLearningRoot = appRoot;
appRoot.render(<App />);

