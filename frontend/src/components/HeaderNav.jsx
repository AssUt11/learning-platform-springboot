import { useEffect, useRef, useState } from "react";
import {
  BarChart3,
  BookOpen,
  Bot,
  Brain,
  Camera,
  ChevronDown,
  GitBranch,
  GraduationCap,
  LogOut,
  Menu,
  MessageCircle,
  ShieldCheck,
  Upload,
  X
} from "lucide-react";
import { getProfile } from "../api/profile";
import ProfileAvatarDialog from "./ProfileAvatarDialog";
import UserAvatar from "./UserAvatar";
import "../profile-avatar.css";

export default function HeaderNav({ user, page, onNavigate, onLogout, onUserChange }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [avatarStatus, setAvatarStatus] = useState("");
  const menuRef = useRef(null);
  const headerRef = useRef(null);
  const navigationToggleRef = useRef(null);
  const profileTriggerRef = useRef(null);
  const onUserChangeRef = useRef(onUserChange);
  onUserChangeRef.current = onUserChange;
  const activePage = ["catalog", "course", "learn"].includes(page) ? "home" : page;
  const navigation =
    user.role === "admin"
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

  useEffect(() => {
    if (!onUserChangeRef.current) return undefined;
    const controller = new AbortController();
    getProfile({ signal: controller.signal })
      .then((profile) => {
        if (!controller.signal.aborted && String(profile.id) === String(user.id)) {
          onUserChangeRef.current?.(profile);
        }
      })
      .catch(() => {
        // A temporary profile lookup failure must not block navigation or sign-in.
      });
    return () => controller.abort();
  }, [user.id]);

  useEffect(() => {
    const closeMenu = (event) => {
      if (!menuRef.current?.contains(event.target)) setMenuOpen(false);
      if (!headerRef.current?.contains(event.target)) setNavigationOpen(false);
    };
    const closeWithEscape = (event) => {
      if (event.key !== "Escape") return;
      if (menuOpen) profileTriggerRef.current?.focus();
      else if (navigationOpen) navigationToggleRef.current?.focus();
      setMenuOpen(false);
      setNavigationOpen(false);
    };
    document.addEventListener("pointerdown", closeMenu);
    document.addEventListener("keydown", closeWithEscape);
    return () => {
      document.removeEventListener("pointerdown", closeMenu);
      document.removeEventListener("keydown", closeWithEscape);
    };
  }, [menuOpen, navigationOpen]);

  useEffect(() => {
    setMenuOpen(false);
    setNavigationOpen(false);
  }, [page]);

  const go = (target) => {
    setMenuOpen(false);
    setNavigationOpen(false);
    onNavigate(target);
  };

  return (
    <header className="topbar" ref={headerRef}>
      <button
        className="brand brand-button"
        type="button"
        onClick={() => go(user.role === "admin" ? "admin" : "home")}
        aria-label="返回首页"
      >
        <span className="brand-mark">
          <GraduationCap size={23} strokeWidth={1.75} aria-hidden="true" />
        </span>
        <div>
          <strong>知度课堂</strong>
          <span>课程 · 图谱 · 成长</span>
        </div>
      </button>
      <button
        className="navigation-toggle"
        type="button"
        aria-label={navigationOpen ? "收起导航" : "展开导航"}
        aria-expanded={navigationOpen}
        aria-controls="primary-navigation"
        ref={navigationToggleRef}
        onClick={() => {
          setNavigationOpen((value) => !value);
          setMenuOpen(false);
        }}
      >
        {navigationOpen ? <X size={20} aria-hidden="true" /> : <Menu size={20} aria-hidden="true" />}
      </button>
      <nav
        id="primary-navigation"
        className={navigationOpen ? "navigation-open" : ""}
        aria-label="主导航"
      >
        {navigation.map((item) => {
          const Icon = item.icon;
          return (
            <button
              className={activePage === item.id ? "active" : ""}
              aria-current={activePage === item.id ? "page" : undefined}
              type="button"
              key={item.id}
              onClick={() => go(item.id)}
              title={item.label}
            >
              <Icon size={18} strokeWidth={1.75} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
      <div className="profile-menu-wrap" ref={menuRef}>
        <button
          className="profile-trigger"
          ref={profileTriggerRef}
          type="button"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => {
            setMenuOpen((value) => !value);
            setNavigationOpen(false);
          }}
        >
          <UserAvatar name={user.name || roleLabel} src={user.avatarUrl} />
          <span className="profile-trigger-copy">
            <strong>{user.name}</strong>
            <small>{roleLabel}</small>
          </span>
          <ChevronDown className={menuOpen ? "rotated" : ""} size={15} />
        </button>
        {menuOpen && (
          <div className="profile-dropdown" role="menu">
            <div className="profile-summary">
              <UserAvatar name={user.name || roleLabel} src={user.avatarUrl} className="large" />
              <div>
                <strong>{user.name}</strong>
                <span>{user.email || `${roleLabel}账号`}</span>
              </div>
            </div>
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setMenuOpen(false);
                setAvatarStatus("");
                setAvatarOpen(true);
              }}
            >
              <Camera size={17} />
              <span>
                <strong>更换头像</strong>
                <small>选择你喜欢的照片</small>
              </span>
            </button>
            {avatarStatus && <p className="profile-avatar-status">{avatarStatus}</p>}
            {user.role !== "admin" && (
              <button type="button" role="menuitem" onClick={() => go("messages")}>
                <MessageCircle size={17} />
                <span>
                  <strong>消息中心</strong>
                  <small>查看回复并与他人对话</small>
                </span>
              </button>
            )}
            {user.role === "student" && (
              <button type="button" role="menuitem" onClick={() => go("planning")}>
                <Brain size={17} />
                <span>
                  <strong>调整学习目标</strong>
                  <small>重新规划适合你的学习路线</small>
                </span>
              </button>
            )}
            {user.role === "teacher" && (
              <button type="button" role="menuitem" onClick={() => go("teacher")}>
                <Upload size={17} />
                <span>
                  <strong>上传或修改课程</strong>
                  <small>进入教师课程工作台</small>
                </span>
              </button>
            )}
            {user.role === "admin" && (
              <button type="button" role="menuitem" onClick={() => go("admin")}>
                <ShieldCheck size={17} />
                <span>
                  <strong>管理控制台</strong>
                  <small>审核内容与维护网站</small>
                </span>
              </button>
            )}
            <button
              className="profile-logout"
              type="button"
              role="menuitem"
              onClick={() => {
                setMenuOpen(false);
                onLogout();
              }}
            >
              <LogOut size={17} />
              <span>
                <strong>退出登录</strong>
                <small>安全退出当前账号</small>
              </span>
            </button>
          </div>
        )}
      </div>
      {avatarOpen && (
        <ProfileAvatarDialog
          user={user}
          onSaved={(profile) => {
            onUserChangeRef.current?.(profile);
            setAvatarStatus("头像已更新");
          }}
          onClose={() => {
            setAvatarOpen(false);
            requestAnimationFrame(() => profileTriggerRef.current?.focus());
          }}
        />
      )}
    </header>
  );
}
