import { useEffect, useState } from "react";

/** A fixed square prevents inherited menu text rules from clipping the initials. */
export default function UserAvatar({ name, src, className = "", label }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  const initial = Array.from(String(name || "用户").trim())[0]?.toUpperCase() || "用";
  const allowedSource = typeof src === "string" && /^(?:\/uploads\/avatars\/|blob:)/.test(src);

  return (
    <span
      className={`user-avatar profile-avatar ${className}`.trim()}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      {allowedSource && !failed ? (
        <img src={src} alt="" onError={() => setFailed(true)} />
      ) : (
        <span className="user-avatar-initial">{initial}</span>
      )}
    </span>
  );
}
