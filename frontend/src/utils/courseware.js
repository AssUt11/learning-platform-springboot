export function coursewareLocation(type, index) {
  return type === "PAGE" ? `第 ${index} 页` : type === "SLIDE" ? `第 ${index} 张幻灯片` : `第 ${index} 段`;
}
