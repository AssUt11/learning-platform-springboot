import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  BookOpen,
  Brain,
  CheckCircle2,
  Eye,
  FileText,
  GraduationCap,
  Heart,
  LogOut,
  Maximize,
  PlayCircle,
  Plus,
  RefreshCcw,
  Route,
  Search,
  Sparkles,
  Star,
  TrendingUp,
  Upload,
  UserRound,
  Users,
  Wand2,
  ZoomIn,
  ZoomOut
} from "lucide-react";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8081/api";

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
  teacher: "/teacher",
  interests: "/interests"
};

const studentPages = new Set(["home", "catalog", "graph", "learning", "agents"]);

function pageFromPath(pathname) {
  const page = String(pathname || "").replace(/^\/+/, "") || "";
  if (page === "login") return "auth";
  return pagePaths[page] ? page : "";
}

function defaultPageForUser(savedUser, setupPending, routePage = "") {
  if (!savedUser) return "auth";
  if (!localStorage.getItem("learning_token")) return "auth";
  if (savedUser.role === "teacher") return "teacher";
  if (setupPending) return "interests";
  return studentPages.has(routePage) ? routePage : "home";
}

function clearAuthStorage() {
  localStorage.removeItem("learning_user");
  localStorage.removeItem("learning_token");
  localStorage.removeItem("learning_preferences");
  localStorage.removeItem("learning_needs_interest_setup");
}

function isAuthExpiredError(error) {
  const message = String(error?.message || "");
  return message.includes("Please log in first") || message.includes("permission");
}

function handleActionError(error, fallbackMessage) {
  if (isAuthExpiredError(error)) {
    clearAuthStorage();
    alert("登录状态已失效，请重新登录后再操作。");
    window.history.replaceState({ page: "auth" }, "", "/login");
    window.location.reload();
    return;
  }
  alert(`${fallbackMessage}：${error.message}`);
}

function api(path, options = {}) {
  const token = localStorage.getItem("learning_token");
  return fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  }).then(async (res) => {
    if (!res.ok) {
      const error = new Error((await res.json().catch(() => ({}))).message || "请求失败");
      error.status = res.status;
      throw error;
    }
    return res.json();
  });
}

function App() {
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem("learning_user") || "null"));
  const [page, setPageState] = useState(() => {
    const savedUser = JSON.parse(localStorage.getItem("learning_user") || "null");
    const setupPending = localStorage.getItem("learning_needs_interest_setup") === "1";
    return defaultPageForUser(savedUser, setupPending, pageFromPath(window.location.pathname));
  });
  const [preferences, setPreferences] = useState(() => JSON.parse(localStorage.getItem("learning_preferences") || "[]"));
  const [resources, setResources] = useState(seedResources);
  const [resourceMeta, setResourceMeta] = useState({ page: 1, size: 12, total: seedResources.length, totalPages: 1 });
  const [categoryTree, setCategoryTree] = useState(knowledgeCategories);
  const [catalogSubjects, setCatalogSubjects] = useState(fallbackCatalog);
  const [selectedSubject, setSelectedSubject] = useState(preferences[0] || "软件工程");
  const [needsInterestSetup, setNeedsInterestSetup] = useState(() => localStorage.getItem("learning_needs_interest_setup") === "1");

  const updateBrowserHistory = (nextPage, replace = false) => {
    const path = pagePaths[nextPage] || "/home";
    const state = { page: nextPage };
    if (replace) {
      window.history.replaceState(state, "", path);
      return;
    }
    if (window.location.pathname !== path || window.history.state?.page !== nextPage) {
      window.history.pushState(state, "", path);
    }
  };

  const navigatePage = (nextPage, options = {}) => {
    setPageState((current) => {
      const resolved = typeof nextPage === "function" ? nextPage(current) : nextPage;
      updateBrowserHistory(resolved, options.replace);
      return resolved;
    });
  };

  useEffect(() => {
    updateBrowserHistory(page, true);
    const onPopState = (event) => {
      const routePage = event.state?.page || pageFromPath(window.location.pathname);
      setPageState(defaultPageForUser(user, needsInterestSetup, routePage));
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [user?.id, user?.role, needsInterestSetup]);

  useEffect(() => {
    api("/resources").then((data) => {
      setResources(data.resources?.length ? data.resources : seedResources);
      setResourceMeta({
        page: data.page || 1,
        size: data.size || 12,
        total: data.total ?? data.resources?.length ?? seedResources.length,
        totalPages: data.totalPages || 1
      });
    }).catch(() => {});
  }, []);

  useEffect(() => {
    api("/knowledge/categories/tree").then((data) => {
      const tree = normalizeCategoryTree(Array.isArray(data.categories) ? data.categories : []);
      const resolvedTree = tree.length ? tree : knowledgeCategories;
      const items = catalogFromCategories(resolvedTree);
      setCategoryTree(resolvedTree);
      setCatalogSubjects(items);
      setSelectedSubject((current) => findCategoryPath(current, resolvedTree) ? current : items[0]?.name || "软件工程");
    }).catch(() => setCatalogSubjects(fallbackCatalog));
  }, []);

  useEffect(() => {
    if (!user || user.role !== "student") return;
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
    localStorage.setItem("learning_user", JSON.stringify(nextUser));
    if (token) localStorage.setItem("learning_token", token);
    setUser(nextUser);
    if (nextUser.role === "teacher") {
      setNeedsInterestSetup(false);
      localStorage.removeItem("learning_needs_interest_setup");
      navigatePage("teacher", { replace: true });
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
    navigatePage("auth", { replace: true });
  };

  return (
    <div className="app-shell">
      {user && page !== "interests" && <Topbar user={user} page={page} setPage={navigatePage} logout={logout} />}
      {page === "auth" && <AuthPage onDone={loginDone} />}
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
              .catch((error) => alert(`兴趣保存失败：${error.message}`));
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
          setPage={navigatePage}
          setSelectedSubject={setSelectedSubject}
        />
      )}
      {page === "graph" && <KnowledgeGraph subject={selectedSubject} categoryTree={categoryTree} setSelectedSubject={setSelectedSubject} />}
      {page === "learning" && <MyLearningPage setPage={navigatePage} setSelectedSubject={setSelectedSubject} />}
      {page === "agents" && <AgentsPage preferences={preferences} />}
      {page === "teacher" && <TeacherDashboard resources={resources} setResources={setResources} setResourceMeta={setResourceMeta} />}
    </div>
  );
}

function Topbar({ user, page, setPage, logout }) {
  const nav = user.role === "teacher"
    ? [{ id: "teacher", label: "教师端", icon: Upload }]
    : [
        { id: "home", label: "首页", icon: BookOpen },
        { id: "learning", label: "我的学习", icon: BarChart3 },
        { id: "agents", label: "智能分析", icon: Brain }
      ];
  return (
    <header className="topbar">
      <div className="brand">
        <GraduationCap size={28} />
        <div>
          <strong>智能导学系统</strong>
          <span>智能分析 + 知识图谱 + 个性化推荐</span>
        </div>
      </div>
      <nav>
        {nav.map((item) => {
          const Icon = item.icon;
          return (
            <button className={page === item.id ? "active" : ""} key={item.id} onClick={() => setPage(item.id)} title={item.label}>
              <Icon size={18} />
              {item.label}
            </button>
          );
        })}
      </nav>
      <button className="user-pill" onClick={logout} title="退出登录">
        <UserRound size={18} />
        {user.name}
        <LogOut size={16} />
      </button>
    </header>
  );
}

function AuthPage({ onDone }) {
  const [mode, setMode] = useState("login");
  const [role, setRole] = useState("student");
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const path = mode === "login" ? "/auth/login" : "/auth/register";
      const payload = mode === "login" ? { email: form.email, password: form.password } : { ...form, role };
      const data = await api(path, { method: "POST", body: JSON.stringify(payload) });
      onDone(data.user, data.token, mode, data.needsInterestSelection);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-hero">
        <div className="hero-copy">
          <span className="eyebrow">Personalized Learning System</span>
          <h1>基于智能分析与知识图谱的个性化学习平台</h1>
          <p>教师上传课程资源，学生选择兴趣方向，系统记录学习行为，并通过智能分析生成导学建议、学习画像、路径规划和资源推荐。</p>
        </div>
      </section>
      <section className="auth-panel">
        <div className="segmented">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>登录</button>
          <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>注册</button>
        </div>
        <form onSubmit={submit}>
          {mode === "register" && <input placeholder="姓名" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />}
          <input type="email" placeholder="邮箱" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <input type="password" placeholder="密码" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
          <div className="role-picker">
            <button type="button" className={role === "student" ? "active" : ""} onClick={() => setRole("student")}>学生</button>
            <button type="button" className={role === "teacher" ? "active" : ""} onClick={() => setRole("teacher")}>教师</button>
          </div>
          {error && <p className="error">{error}</p>}
          <button className="primary-btn" type="submit">
            {mode === "login" ? "登录系统" : "创建账号"}
            <ArrowRight size={18} />
          </button>
        </form>
      </section>
    </main>
  );
}

function InterestSetupPage({ selected, catalogSubjects, onSave, logout }) {
  const [items, setItems] = useState(selected);
  const catalog = catalogSubjects.length ? catalogSubjects : fallbackCatalog;
  const toggle = (subject) => setItems((prev) => prev.includes(subject) ? prev.filter((x) => x !== subject) : [...prev, subject]);
  return (
    <main className="preference-page-react">
      <div className="preference-top-react">
        <div className="page-title">
          <span className="eyebrow">首次兴趣选择</span>
          <h2>选择你感兴趣的学习方向</h2>
          <p>像短视频应用选择兴趣一样，先挑选几个方向。系统会保存到数据库，并在首页优先推荐这些方向的课程。</p>
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
      <button className="primary-btn floating-save-react" disabled={!items.length} onClick={() => onSave(items)}>保存兴趣并进入首页</button>
    </main>
  );
}

function CategoryCascader({ tree = knowledgeCategories, value = [], onChange, allowEmpty = false }) {
  const first = value[0] || "";
  const second = value[1] || "";
  const third = value[2] || "";
  const firstNode = tree.find((item) => item.label === first);
  const secondNode = firstNode?.children?.find((item) => item.label === second);
  const changeFirst = (label) => onChange(label ? [label] : []);
  const changeSecond = (label) => onChange(label ? [first, label] : first ? [first] : []);
  const changeThird = (label) => onChange(label ? [first, second, label] : second ? [first, second] : first ? [first] : []);
  return (
    <div className="category-cascader">
      <select value={first} onChange={(event) => changeFirst(event.target.value)}>
        {allowEmpty && <option value="">全部大类</option>}
        {tree.map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
      <select value={second} onChange={(event) => changeSecond(event.target.value)} disabled={!firstNode}>
        <option value="">{firstNode ? "全部方向" : "先选大类"}</option>
        {(firstNode?.children || []).map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
      <select value={third} onChange={(event) => changeThird(event.target.value)} disabled={!secondNode}>
        <option value="">{secondNode ? "全部知识点" : "先选方向"}</option>
        {(secondNode?.children || []).map((item) => <option key={item.label} value={item.label}>{item.label}</option>)}
      </select>
    </div>
  );
}

function StudentHome({ resources, setResources, resourceMeta, setResourceMeta, preferences, catalogSubjects, categoryTree, setPage, setSelectedSubject }) {
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState({ subject: "", type: "" });
  const [filterPath, setFilterPath] = useState([]);
  const [loadingResources, setLoadingResources] = useState(false);
  const normalizedQuery = query.trim().toLowerCase();
  const tree = categoryTree?.length ? categoryTree : knowledgeCategories;
  const visibleCatalog = catalogFromCategories(tree, query);
  const matchedSuggestionGroups = normalizedQuery ? visibleCatalog.map((item) => ({ ...item, nodes: (item.nodes || []).slice(0, 8) })).filter((item) => item.nodes.length) : [];
  const updateResource = (id, updates) => {
    setResources((items) => items.map((item) => item.id === id ? { ...item, ...updates } : item));
  };
  const displayResources = useMemo(() => {
    const seen = new Set();
    return resources.filter((item) => {
      const key = String(item.title || item.id).trim();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [resources]);
  const loadResources = async (page = 1) => {
    setLoadingResources(true);
    const params = new URLSearchParams({ page: String(page), size: String(resourceMeta?.size || 12) });
    if (query.trim()) params.set("keyword", query.trim());
    if (filters.subject) params.set("subject", filters.subject);
    if (filters.type) params.set("type", filters.type);
    try {
      const data = await api(`/resources?${params.toString()}`);
      setResources(data.resources || []);
      setResourceMeta({
        page: data.page || page,
        size: data.size || 12,
        total: data.total || 0,
        totalPages: data.totalPages || 1
      });
    } finally {
      setLoadingResources(false);
    }
  };
  const openCatalog = (subject, node) => {
    const nodeTitle = typeof node === "string" ? node : node?.title;
    const targetPath = typeof node === "object" && Array.isArray(node.path) ? node.path : [subject];
    const targetSubject = targetPath[targetPath.length - 1] || subject;
    setSelectedSubject(targetSubject);
    if (nodeTitle && targetSubject === subject) {
      api("/learning/records", { method: "POST", body: JSON.stringify({ subject: targetSubject, knowledgeNode: nodeTitle, progress: 10 }) }).catch(() => {});
    }
    setPage("graph");
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      loadResources(1).catch(() => {});
    }, 250);
    return () => clearTimeout(timer);
  }, [query, filters.subject, filters.type]);

  return (
    <main className="content">
      <div className="home-head">
        <div>
          <span className="eyebrow">首页推荐</span>
          <h2>课程目录与学习推荐</h2>
        </div>
        <div className="search-panel">
          <label className="search-box">
            <Search size={18} />
            <input placeholder="搜索大类、小类或课程" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
          <div className="resource-filter-row">
            <CategoryCascader
              tree={tree}
              value={filterPath}
              allowEmpty
              onChange={(path) => {
                setFilterPath(path);
                setFilters({ ...filters, subject: path[1] || "" });
              }}
            />
            <select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
              <option value="">全部类型</option>
              <option value="video">视频</option>
              <option value="material">资料</option>
            </select>
          </div>
          {!!matchedSuggestionGroups.length && (
            <div className="search-suggestions">
              {matchedSuggestionGroups.map((group) => (
                <div className="suggestion-group" key={group.name}>
                  <button className="suggestion-subject" onClick={() => openCatalog(group.name)}>
                    <strong>{group.name}</strong>
                    <span>进入图谱</span>
                  </button>
                  <div className="suggestion-options">
                    {group.nodes.map((node) => (
                      <button key={`${group.name}-${node.title}`} onClick={() => {
                        setQuery(node.title);
                        openCatalog(group.name, node);
                      }}>
                        {node.title}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
      <section className="home-catalog-react">
        <div className="home-catalog-head">
          <div>
            <span className="eyebrow">课程目录</span>
            <h3>全部学习大类</h3>
          </div>
          <span>{visibleCatalog.length} 个大类</span>
        </div>
        {visibleCatalog.length ? (
          <div className="home-catalog-grid">
            {visibleCatalog.map((subject) => (
              <article className="home-catalog-card" key={subject.name}>
                <button className="home-catalog-main" onClick={() => openCatalog(subject.name)}>
                  <strong>{subject.name}</strong>
                  <span>进入图谱</span>
                </button>
                <div className="home-subcategory-list">
                  {(subject.nodes || []).map((node) => (
                    <button key={`${subject.name}-${node.title}`} onClick={() => openCatalog(subject.name, node)}>
                      {node.title}
                    </button>
                  ))}
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="empty-state-react">没有匹配的大类或小类，换个关键词试试。</p>
        )}
      </section>
      <div className="page-title compact">
        <span className="eyebrow">推荐课程</span>
        <h2>{loadingResources ? "正在加载课程" : `共 ${resourceMeta?.total || displayResources.length} 个资源`}</h2>
      </div>
      <section className="resource-grid">
        {displayResources.map((item) => <ResourceCard item={item} key={item.id} onUpdate={updateResource} />)}
        {!displayResources.length && <p className="empty-state-react">暂无匹配课程，可以换个关键词或让教师上传相关资源。</p>}
      </section>
      <div className="pager-row">
        <button disabled={(resourceMeta?.page || 1) <= 1} onClick={() => loadResources((resourceMeta?.page || 1) - 1)}>上一页</button>
        <span>{resourceMeta?.page || 1} / {resourceMeta?.totalPages || 1}</span>
        <button disabled={(resourceMeta?.page || 1) >= (resourceMeta?.totalPages || 1)} onClick={() => loadResources((resourceMeta?.page || 1) + 1)}>下一页</button>
      </div>
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
        <video className="resource-video" controls preload="metadata" src={encodeURI(item.fileUrl)}>
          当前浏览器不支持视频播放
        </video>
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

function KnowledgeGraph({ subject, categoryTree, setSelectedSubject }) {
  const tree = categoryTree?.length ? categoryTree : knowledgeCategories;
  const initialPath = findCategoryPath(subject, tree) || [tree[0]?.label || "软件工程"];
  const [categoryPath, setCategoryPath] = useState(initialPath);
  const [graphData, setGraphData] = useState(() => buildCategoryGraph(initialPath, tree));
  const [activeId, setActiveId] = useState(`${initialPath[initialPath.length - 1]}-root`);
  const [zoom, setZoom] = useState(0.92);
  const [layout, setLayout] = useState("radial");
  const [keyword, setKeyword] = useState("");
  const [animatedNodes, setAnimatedNodes] = useState([]);
  const stageRef = React.useRef(null);
  const simulationRef = React.useRef(new Map());
  const draggingRef = React.useRef(null);
  const suppressClickRef = React.useRef(false);

  useEffect(() => {
    const nextPath = findCategoryPath(subject, tree) || [tree[0]?.label || "软件工程"];
    const nextGraph = buildCategoryGraph(nextPath, tree);
    setCategoryPath(nextPath);
    setGraphData(nextGraph);
    setActiveId(nextGraph.nodes[0]?.id || `${nextPath[nextPath.length - 1]}-root`);
  }, [subject, categoryTree]);

  const visibleNodes = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return graphData.nodes;
    const matchedIds = new Set();
    graphData.nodes.forEach((node) => {
      if (String(node.label || "").toLowerCase().includes(text)) matchedIds.add(node.id);
    });
    graphData.links.forEach((link) => {
      if (matchedIds.has(link.source) || matchedIds.has(link.target)) {
        matchedIds.add(link.source);
        matchedIds.add(link.target);
      }
    });
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
    const primary = ordered.filter((node, index) => index > 0 && !node.parentId && node.type !== "point");
    const points = ordered.filter((node) => node.parentId || node.type === "point");
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
    const pointPositioned = points.map((node, index) => {
      const parent = parentPositions[node.parentId] || { x: 50, y: 50 };
      const baseAngle = Math.atan2(parent.y - 50, parent.x - 50);
      const spread = 0.44;
      const siblingIndex = points.filter((item) => item.parentId === node.parentId).findIndex((item) => item.id === node.id);
      const siblingCount = points.filter((item) => item.parentId === node.parentId).length || 1;
      const angle = baseAngle + (siblingIndex - (siblingCount - 1) / 2) * spread;
      const radiusX = layout === "layer" ? 43 : 45;
      const radiusY = layout === "layer" ? 38 : 40;
      return {
        ...node,
        x: Math.max(6, Math.min(50 + Math.cos(angle) * radiusX, 94)),
        y: Math.max(6, Math.min(50 + Math.sin(angle) * radiusY, 94))
      };
    });
    return [root, ...primaryPositioned, ...pointPositioned].filter(Boolean);
  }, [visibleNodes, subject, layout, categoryPath]);

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
  const totalKnowledgeCount = Math.max(graphData.nodes.length - 1, 0);
  const activePath = active?.path || categoryPath;
  const activeCategory = findCategoryByPath(activePath, tree);
  const activeCanDrill = active?.type !== "root" && activePath.length < 3 && !!activeCategory?.children?.length;
  const primaryLegendLabel = graphData.source === "category" ? "二级方向" : graphData.source === "direction" ? "三级知识点" : "详细知识点";
  const nodeTypeLabel = (node) => {
    if (node?.type === "root") return graphData.source === "category" ? "一级大类" : graphData.source === "direction" ? "二级方向" : "三级知识点";
    if (node?.type === "direction") return "二级方向";
    if (node?.type === "knowledge") return "三级知识点";
    return "详细知识点";
  };
  const learnNode = (node) => {
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      return;
    }
    setActiveId(node.id);
    const nextPath = node.path;
    const nextCategory = findCategoryByPath(nextPath || [], tree);
    if (nextPath && nextPath.length <= 3 && nextCategory?.children?.length) {
      setKeyword("");
      setLayout("radial");
      setCategoryPath(nextPath);
      setSelectedSubject?.(nextPath[nextPath.length - 1]);
      const nextGraph = buildCategoryGraph(nextPath, tree);
      setGraphData(nextGraph);
      setActiveId(nextGraph.nodes[0]?.id || node.id);
      return;
    }
    if (node.label !== currentLabel) {
      api("/learning/records", { method: "POST", body: JSON.stringify({ subject: categoryPath[1] || currentLabel, knowledgeNode: node.label, progress: 10 }) }).catch(() => {});
    }
  };
  const changeCategoryPath = (path) => {
    const nextPath = path.length ? path : [tree[0]?.label || "软件工程"];
    const nextGraph = buildCategoryGraph(nextPath, tree);
    setCategoryPath(nextPath);
    setSelectedSubject?.(nextPath[nextPath.length - 1]);
    setGraphData(nextGraph);
    setActiveId(nextGraph.nodes[0]?.id || `${nextPath[nextPath.length - 1]}-root`);
    setKeyword("");
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
          <span className="eyebrow">知识图谱</span>
          <h2>{currentLabel}</h2>
        </div>
        <div className="graph-toolbar-actions">
          <CategoryCascader tree={tree} value={categoryPath} onChange={changeCategoryPath} />
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
              className={`${index === 0 ? "center-node" : "graph-node"} ${node.type === "direction" ? "track-node" : ""} ${node.type === "knowledge" ? "course-node" : ""} ${node.type === "point" ? "point-node" : ""} ${active?.id === node.id ? "active" : ""}`}
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
          <span><i className="point-dot" /> 下钻后显示下一层</span>
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
          <span>{graphData.source === "neo4j" ? "Neo4j 图谱" : "本地图谱"}</span>
          <span>{nodeTypeLabel(active)}</span>
        </div>
        <button className="primary-btn" onClick={() => active && learnNode(active)}>{activeCanDrill ? `进入 ${active?.label} 图谱` : "学习该知识点"}</button>
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
    recentRecords: []
  });
  const [loading, setLoading] = useState(true);

  const loadLearning = async () => {
    setLoading(true);
    try {
      const data = await api("/learning/summary");
      setLearning({
        summary: data.summary || {},
        subjectProgress: data.subjectProgress || [],
        resourceRecords: data.resourceRecords || [],
        nodeRecords: data.nodeRecords || [],
        favorites: data.favorites || [],
        recentRecords: data.recentRecords || []
      });
    } catch {
      setLearning((current) => current);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadLearning();
  }, []);

  const completeResource = async (resourceId) => {
    await api("/learning/records", { method: "POST", body: JSON.stringify({ resourceId, progress: 100 }) });
    await loadLearning();
  };

  const openGraph = (subject) => {
    setSelectedSubject(subject || "Java");
    setPage("graph");
  };

  const summary = learning.summary || {};
  const avgProgress = Number(summary.avgResourceProgress || summary.avgresourceprogress || 0);
  const completedCount = Number(summary.completedResourceCount || summary.completedresourcecount || 0);
  const learnedResourceCount = Number(summary.learnedResourceCount || summary.learnedresourcecount || 0);
  const learnedNodeCount = Number(summary.learnedNodeCount || summary.learnednodecount || 0);
  const favoriteCount = Number(summary.favoriteCount || summary.favoritecount || 0);
  const lastActiveAt = summary.lastActiveAt || summary.lastactiveat;

  return (
    <main className="content my-learning-page">
      <div className="home-head">
        <div>
          <span className="eyebrow">我的学习</span>
          <h2>学习进度与个人记录</h2>
          <p>汇总已学资源、知识点点击、收藏课程和最近学习动态。</p>
        </div>
        <button className="primary-btn" onClick={() => setPage("home")}>
          <BookOpen size={18} />
          继续学习
        </button>
      </div>

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
              {!loading && !learning.subjectProgress.length && <EmptyLearningState setPage={setPage} />}
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
                    <button onClick={() => completeResource(resourceId)}>标记完成</button>
                  </article>
                );
              })}
              {!loading && !learning.resourceRecords.length && <p className="muted">暂无已学资源，点击课程卡片的“开始学习”后会记录在这里。</p>}
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
              {!loading && !learning.recentRecords.length && <p className="muted">学习课程或点击知识点后，这里会显示时间线。</p>}
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
              {!loading && !learning.nodeRecords.length && <p className="muted">还没有知识点学习记录。</p>}
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
                <div className="favorite-mini-row" key={item.id}>
                  <Heart size={16} />
                  <div>
                    <strong>{item.title}</strong>
                    <span>{item.subject} / {item.type === "video" ? "视频" : "资料"}</span>
                  </div>
                </div>
              ))}
              {!loading && !learning.favorites.length && <p className="muted">还没有收藏资源。</p>}
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
  return String(value).replace("T", " ").slice(0, 19);
}

function AgentsPage({ preferences }) {
  const [outputs, setOutputs] = useState({});
  const [reports, setReports] = useState([]);
  const agents = [
    { id: "guide", title: "课程智能分析助手", icon: Wand2, desc: "分析学习行为，推荐需要补强的课程与知识点。" },
    { id: "analysis", title: "学习分析报告", icon: FileText, desc: "生成 Word 学习分析报告，指出问题和改进建议。" },
    { id: "path", title: "学习路径分析", icon: Route, desc: "结合掌握程度生成适合当前阶段的学习路径。" },
    { id: "recommend", title: "学习资源分析", icon: Sparkles, desc: "根据学习记录和偏好推荐视频、资料与课程。" }
  ];
  const loadReports = async () => {
    try {
      const data = await api("/agents/reports");
      setReports(data.reports || []);
    } catch {
      setReports([]);
    }
  };
  const runAgent = async (id) => {
    setOutputs((prev) => ({ ...prev, [id]: "生成中..." }));
    try {
      const data = await api(`/agents/${id}`, { method: "POST", body: JSON.stringify({ preferences }) });
      setOutputs((prev) => ({ ...prev, [id]: data.result || data.downloadUrl || "已生成" }));
      if (id === "analysis") await loadReports();
    } catch {
      setOutputs((prev) => ({ ...prev, [id]: "演示结果：建议先补充 Java 基础语法与数据库 SQL 查询，再进入项目实战。" }));
    }
  };
  const deleteReport = async (id) => {
    try {
      await api(`/agents/reports/${id}`, { method: "DELETE" });
      await loadReports();
    } catch (error) {
      alert(`报告删除失败：${error.message}`);
    }
  };
  useEffect(() => {
    loadReports();
  }, []);
  return (
    <main className="content">
      <div className="page-title compact">
        <span className="eyebrow">智能分析</span>
        <h2>基于学习数据的智能导学服务</h2>
      </div>
      <section className="agent-grid">
        {agents.map((agent) => {
          const Icon = agent.icon;
          return (
            <article className="agent-card" key={agent.id}>
              <Icon size={28} />
              <h3>{agent.title}</h3>
              <p>{agent.desc}</p>
              <button onClick={() => runAgent(agent.id)}>运行智能分析</button>
              {outputs[agent.id] && <div className="agent-output">{outputs[agent.id]}</div>}
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
          {reports.map((report) => (
            <div className="report-row" key={report.id}>
              <div>
                <strong>{report.outputResult || report.outputresult || "学习分析报告"}</strong>
                <span>{String(report.createdAt || report.createdat || "").replace("T", " ")}</span>
              </div>
              <div className="report-actions">
                <a href={encodeURI(report.reportUrl || report.reporturl || report.report_url || "")} target="_blank" rel="noreferrer">下载</a>
                <button onClick={() => deleteReport(report.id)}>删除</button>
              </div>
            </div>
          ))}
          {!reports.length && <p className="muted">暂无报告，运行“学习分析报告”后会出现在这里。</p>}
        </div>
      </section>
    </main>
  );
}

function TeacherDashboard({ resources, setResources, setResourceMeta }) {
  const [form, setForm] = useState({ title: "", subject: "Java", type: "video", level: "入门", description: "", content: "" });
  const [editingId, setEditingId] = useState(null);
  const [analytics, setAnalytics] = useState(() => ({
    summary: {},
    resourceStats: [],
    popularResources: [],
    warningStudents: [],
    weakNodes: []
  }));
  const [analyticsLoading, setAnalyticsLoading] = useState(true);

  const loadAnalytics = async () => {
    setAnalyticsLoading(true);
    try {
      const data = await api("/teacher/analytics");
      setAnalytics({
        summary: data.summary || {},
        resourceStats: data.resourceStats || [],
        popularResources: data.popularResources || [],
        warningStudents: data.warningStudents || [],
        weakNodes: data.weakNodes || []
      });
    } catch {
      setAnalytics((prev) => prev);
    } finally {
      setAnalyticsLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, []);

  const resetForm = () => {
    setEditingId(null);
    setForm({ title: "", subject: "Java", type: "video", level: "入门", description: "", content: "" });
  };

  const refreshResources = async () => {
    const data = await api("/resources?page=1&size=12");
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
    try {
      if (editingId) {
        await api(`/teacher/resources/${editingId}`, { method: "PUT", body: JSON.stringify(form) });
      } else {
        const newItem = { id: Date.now(), ...form, minutes: 45, teacher: "当前教师", favoriteCount: 0 };
        setResources([newItem, ...resources]);
        const formData = new FormData();
        Object.entries(form).forEach(([key, value]) => formData.append(key, value));
        await api("/teacher/resources", { method: "POST", body: formData });
      }
      await refreshResources();
      await loadAnalytics();
      resetForm();
    } catch (error) {
      alert(`资源保存失败：${error.message}`);
    }
  };

  const startEdit = (item) => {
    setEditingId(item.id);
    setForm({
      title: item.title || "",
      subject: item.subject || "Java",
      type: item.type || "material",
      level: item.level || "入门",
      description: item.description || "",
      content: item.content || buildResourceTextContent(item)
    });
  };

  const deleteResource = async (id) => {
    try {
      await api(`/teacher/resources/${id}`, { method: "DELETE" });
      await refreshResources();
      await loadAnalytics();
    } catch (error) {
      alert(`资源删除失败：${error.message}`);
    }
  };

  return (
    <main className="content teacher-dashboard">
      <div className="page-title compact">
        <span className="eyebrow">教师端</span>
        <h2>资源管理与班级学习分析</h2>
        <p>在教师页面内完成资源上传、班级学习看板、学生预警和课程资源数据分析。</p>
      </div>

      <section className="teacher-layout">
        <section>
          <div className="page-title compact">
            <span className="eyebrow">资源管理</span>
            <h2>{editingId ? "编辑课程资源" : "上传视频和学习资料"}</h2>
          </div>
          <form className="teacher-form" onSubmit={submit}>
            <input placeholder="课程或资源标题" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <select value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })}>{subjects.map((s) => <option key={s}>{s}</option>)}</select>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option value="video">视频</option><option value="material">学习资料</option></select>
            <select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })}><option>入门</option><option>进阶</option><option>项目实战</option></select>
            <textarea placeholder="内容简介" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <textarea className="content-textarea" placeholder="文本学习内容，不填则系统自动生成不少于 500 字的学习正文" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
            {!editingId && <input type="file" accept="video/*,.pdf,.doc,.docx,.ppt,.pptx,.txt" onChange={(e) => setForm({ ...form, file: e.target.files?.[0] })} />}
            <button className="primary-btn" type="submit"><Plus size={18} /> {editingId ? "保存修改" : "发布到平台"}</button>
            {editingId && <button type="button" onClick={resetForm}>取消编辑</button>}
          </form>
        </section>

        <section>
          <div className="page-title compact">
            <span className="eyebrow">班级学习看板</span>
            <h2>教师页面数据概览</h2>
          </div>
          <section className="teacher-overview">
            <div className="analytics-grid">
              <AnalyticsCard icon={Users} label="学生总人数" value={analytics.summary.studentCount || 0} tone="blue" />
              <AnalyticsCard icon={TrendingUp} label="近7天活跃" value={analytics.summary.activeStudents || 0} tone="green" />
              <AnalyticsCard icon={CheckCircle2} label="课程完成率" value={`${analytics.summary.courseCompletionRate || 0}%`} tone="teal" />
              <AnalyticsCard icon={BookOpen} label="资源总数" value={analytics.summary.resourceCount || resources.length} tone="orange" />
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

      <section>
        <div className="page-title compact">
          <span className="eyebrow">平台课程</span>
          <h2>已发布课程资源</h2>
        </div>
        <div className="teacher-course-list">
          {resources.map((item) => (
            <div className="teacher-resource-item" key={item.id}>
              <ResourceCard item={item} />
              <div className="teacher-resource-actions">
                <button onClick={() => startEdit(item)}>编辑</button>
                <button onClick={() => deleteResource(item.id)}>删除</button>
              </div>
            </div>
          ))}
        </div>
      </section>
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

createRoot(document.getElementById("root")).render(<App />);

