import { useEffect, useRef, useState } from "react";
import { Camera, Check, LoaderCircle, X } from "lucide-react";
import { saveAvatar } from "../api/profile";
import UserAvatar from "./UserAvatar";
import "../profile-avatar.css";

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

async function normalizedPhoto(file) {
  if (!ALLOWED_TYPES.has(file.type)) throw new Error("请选择 JPEG、PNG 或 WebP 格式的照片。");
  if (file.size > MAX_PHOTO_BYTES) throw new Error("照片不能超过 5 MB。");
  if (!file.size) throw new Error("这张照片是空文件，请重新选择。");
  const bytes = new Uint8Array(await file.slice(0, 12).arrayBuffer());
  const png = [137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => bytes[index] === value);
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  const webp = String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" &&
    String.fromCharCode(...bytes.slice(8, 12)) === "WEBP";
  if (!png && !jpeg && !webp) throw new Error("照片内容与图片格式不符，请重新选择。");

  // The browser decodes WebP as well as camera orientation. The server receives a
  // small standard PNG, validates it again, and never stores the original metadata.
  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error("这张照片无法读取，请重新选择 JPEG、PNG 或 WebP 图片。");
  }
  try {
    if (!bitmap.width || !bitmap.height || bitmap.width * bitmap.height > 16000000) {
      throw new Error("照片尺寸过大，请选择不超过 1600 万像素的图片。");
    }
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 512;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("当前浏览器无法处理照片，请换一个浏览器后重试。");
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    const side = Math.min(bitmap.width, bitmap.height);
    context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, 512, 512);
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) throw new Error("照片处理失败，请重新选择。");
    return new File([blob], "avatar.png", { type: "image/png" });
  } finally {
    bitmap.close();
  }
}

export default function ProfileAvatarDialog({ user, onClose, onSaved }) {
  const dialogRef = useRef(null);
  const inputRef = useRef(null);
  const selectionVersion = useRef(0);
  const [photo, setPhoto] = useState(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [processing, setProcessing] = useState(false);
  const [saving, setSaving] = useState(false);
  const busy = processing || saving;

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => {
      selectionVersion.current += 1;
      dialog.close();
    };
  }, []);

  useEffect(() => {
    if (!photo) {
      setPreview("");
      return undefined;
    }
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  async function choosePhoto(event) {
    const selected = event.target.files?.[0];
    event.target.value = "";
    if (!selected) return;
    const version = ++selectionVersion.current;
    setError("");
    setPhoto(null);
    setPreview("");
    setProcessing(true);
    try {
      const normalized = await normalizedPhoto(selected);
      if (version === selectionVersion.current) setPhoto(normalized);
    } catch (failure) {
      if (version === selectionVersion.current) {
        setError(failure?.message || "这张照片无法读取，请重新选择。");
      }
    } finally {
      if (version === selectionVersion.current) setProcessing(false);
    }
  }

  async function submitPhoto(event) {
    event.preventDefault();
    if (!photo || busy) return;
    setSaving(true);
    setError("");
    try {
      const profile = await saveAvatar(photo);
      setSaving(false);
      onSaved(profile);
      onClose();
    } catch (failure) {
      setError(failure?.message || "头像保存失败，请稍后重试。");
      setSaving(false);
    }
  }

  return (
    <dialog
      className="avatar-dialog"
      ref={dialogRef}
      aria-labelledby="avatar-dialog-title"
      aria-describedby="avatar-dialog-help"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) {
          const bounds = event.currentTarget.getBoundingClientRect();
          if (event.clientX < bounds.left || event.clientX > bounds.right ||
            event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
        }
      }}
    >
      <form onSubmit={submitPhoto}>
        <div className="avatar-dialog-heading">
          <div>
            <h2 id="avatar-dialog-title">更换头像</h2>
            <p>选一张能代表你的照片</p>
          </div>
          <button className="avatar-dialog-close" type="button" aria-label="关闭头像设置" disabled={busy} onClick={onClose}>
            <X size={20} aria-hidden="true" />
          </button>
        </div>
        <div className="avatar-preview-wrap" aria-busy={processing}>
          <UserAvatar name={user.name} src={preview || user.avatarUrl} className="avatar-preview" label="头像预览" />
          <span>{processing ? "正在处理照片…" : photo ? "照片会以居中的圆形展示" : "当前头像"}</span>
        </div>
        <input ref={inputRef} className="avatar-file-input" type="file" accept="image/jpeg,image/png,image/webp" onChange={choosePhoto} tabIndex={-1} aria-label="选择头像照片" />
        <button className="avatar-choose-button" type="button" disabled={busy} onClick={() => inputRef.current?.click()} autoFocus>
          <Camera size={18} aria-hidden="true" />{photo ? "重新选择照片" : "选择照片"}
        </button>
        <p className="avatar-dialog-help" id="avatar-dialog-help">支持 JPEG、PNG、WebP，最大 5 MB。照片居中裁剪，仅用于账号头像。</p>
        {error && <p className="avatar-dialog-error" role="alert">{error}</p>}
        <div className="avatar-dialog-actions">
          <button className="avatar-cancel-button" type="button" disabled={busy} onClick={onClose}>取消</button>
          <button className="avatar-save-button" type="submit" disabled={!photo || busy}>
            {saving ? <LoaderCircle size={17} className="avatar-saving" aria-hidden="true" /> : <Check size={17} aria-hidden="true" />}
            {saving ? "正在保存…" : "保存头像"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
