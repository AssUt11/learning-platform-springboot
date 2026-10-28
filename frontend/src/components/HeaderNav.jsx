import { useEffect, useRef, useState } from "react";
import { BarChart3, BookOpen, Bot, Brain, ChevronDown, GitBranch, GraduationCap, LogOut, MessageCircle, ShieldCheck, Upload } from "lucide-react";

export default function HeaderNav({ user, page, onNavigate, onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const navigation = user.role === "admin"
    ? [{ id: "admin", label: "管理控制台", icon: ShieldCheck }]
    : user.role === "teacher"
      ? [
        { id: "home", label: "首页", icon: BookOpen },
        { id: "graph", label: "智慧图谱", icon: GitBranch },
        { id: "learning", label: "我的学习", icon: BarChart3 },
        { id: "agents", label: "AI 学伴", icon: Brain },
        { id: "messages", label: "消息", icon: MessageCircle },
        { id: "chat", label: "Agent 对话", icon: Bot },
        { id: "teacher", label: "课程上传/管理", icon: Upload }
      ]
      : [
        { id: "home", label: "首页", icon: BookOpen },
        { id: "graph", label: "智慧图谱", icon: GitBranch },
        { id: "learning", label: "我的学习", icon: BarChart3 },
        { id: "agents", label: "AI 学伴", icon: Brain },
        { id: "messages", label: "消息", icon: MessageCircle },
        { id: "chat", label: "Agent 对话", icon: Bot }
      ];
  const roleLabel = user.role === "teacher" ? "教师" : user.role === "admin" ? "管理员" : "学生";
  const avatarText = String(user.name || roleLabel).trim().slice(0, 1).toUpperCase();

  useEffect(() => {
    const closeMenu = (event) => {
      if (!menuRef.current?.contains(event.target)) setMenuOpen(false);
    };
    document.addEventListener("pointerdown", closeMenu);
    return () => document.removeEventListener("pointerdown", closeMenu);
  }, []);

  const go = (target) => {
    setMenuOpen(false);
    onNavigate(target);
  };

  return (
    <header className="topbar">
      <button className="brand brand-button" onClick={() => onNavigate(user.role === "admin" ? "admin" : "home")} aria-label="返回首页">
        <span className="brand-mark"><GraduationCap size={23} /></span>
        <div>
          <strong>知度课堂</strong>
          <span>课程 · 图谱 · 成长</span>
        </div>
      </button>
      <nav aria-label="主导航">
        {navigation.map((item) => {
          const Icon = item.icon;
          return (
            <button className={page === item.id ? "active" : ""} key={item.id} onClick={() => onNavigate(item.id)} title={item.label}>
              <Icon size={18} />
              {item.label}
            </button>
          );
        })}
      </nav>
      <div className="profile-menu-wrap" ref={menuRef}>
        <button className="profile-trigger" type="button" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((value) => !value)}>
          <span className="profile-avatar">{avatarText}</span>
          <span className="profile-trigger-copy"><strong>{user.name}</strong><small>{roleLabel}</small></span>
          <ChevronDown className={menuOpen ? "rotated" : ""} size={15} />
        </button>
        {menuOpen && (
          <div className="profile-dropdown" role="menu">
            <div className="profile-summary">
              <span className="profile-avatar large">{avatarText}</span>
              <div><strong>{user.name}</strong><span>{user.email || `${roleLabel}账号`}</span></div>
            </div>
            {user.role !== "admin" && <button type="button" role="menuitem" onClick={() => go("messages")}><MessageCircle size={17} /><span><strong>消息中心</strong><small>查看回复并与他人对话</small></span></button>}
            {user.role === "student" && <button type="button" role="menuitem" onClick={() => go("planning")}><Brain size={17} /><span><strong>调整学习目标</strong><small>重新规划适合你的学习路线</small></span></button>}
            {user.role === "teacher" && <button type="button" role="menuitem" onClick={() => go("teacher")}><Upload size={17} /><span><strong>上传或修改课程</strong><small>进入教师课程工作台</small></span></button>}
            {user.role === "admin" && <button type="button" role="menuitem" onClick={() => go("admin")}><ShieldCheck size={17} /><span><strong>管理控制台</strong><small>审核内容与维护网站</small></span></button>}
            <button className="profile-logout" type="button" role="menuitem" onClick={() => { setMenuOpen(false); onLogout(); }}><LogOut size={17} /><span><strong>退出登录</strong><small>安全退出当前账号</small></span></button>
          </div>
        )}
      </div>
    </header>
  );
}
