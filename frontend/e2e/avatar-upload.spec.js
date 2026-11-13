import { expect, test } from "@playwright/test";

const initialUser = { id: 731, name: "头像验证", email: "avatar@example.com", role: "student", avatarUrl: "" };
const uploadedUrl = "/uploads/avatars/11111111111111111111111111111111.png";

async function setup(page, initialAvatar = "") {
  let profile = { ...initialUser, avatarUrl: initialAvatar };
  let uploads = 0;
  let imageBytes;
  await page.addInitScript((user) => {
    if (!localStorage.getItem("learning_user")) localStorage.setItem("learning_user", JSON.stringify(user));
    localStorage.setItem("learning_token", "avatar-test-token");
  }, profile);
  await page.route("**/uploads/avatars/**", (route) => imageBytes && route.request().url().endsWith("11111111111111111111111111111111.png")
    ? route.fulfill({ contentType: "image/png", body: imageBytes })
    : route.fulfill({ status: 404, body: "missing" }));
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/profile") data = profile;
    if (path === "/api/profile/avatar") {
      expect(route.request().method()).toBe("PUT");
      const body = route.request().postDataBuffer();
      expect(body.includes(Buffer.from("image/png"))).toBe(true);
      expect(body.includes(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))).toBe(true);
      uploads += 1;
      profile = { ...profile, avatarUrl: uploadedUrl };
      data = profile;
    }
    if (path === "/api/preferences") data = { subjects: ["Java"] };
    if (path === "/api/learning/profile") data = { needsPlanning: false, status: "deferred" };
    if (path === "/api/categories") data = [];
    if (path === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/home");
  const imageData = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 30;
    canvas.height = 20;
    const context = canvas.getContext("2d");
    context.fillStyle = "#426a87";
    context.fillRect(0, 0, 30, 20);
    return canvas.toDataURL("image/png").split(",")[1];
  });
  imageBytes = Buffer.from(imageData, "base64");
  return { file: { name: "portrait.png", mimeType: "image/png", buffer: imageBytes }, uploadCount: () => uploads };
}

async function openAvatar(page) {
  await page.locator(".profile-trigger").click();
  await page.getByRole("menuitem", { name: /更换头像/ }).click();
  await expect(page.getByRole("dialog", { name: "更换头像" })).toBeVisible();
}

test("选择照片可预览并保存，刷新后从账号资料恢复头像", async ({ page }) => {
  const fixture = await setup(page);
  await openAvatar(page);
  await page.getByLabel("选择头像照片").setInputFiles(fixture.file);
  await expect(page.getByRole("img", { name: "头像预览" }).locator("img")).toHaveAttribute("src", /^blob:/);
  await page.getByRole("button", { name: "保存头像", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "更换头像" })).toHaveCount(0);
  await expect(page.locator(".profile-trigger .user-avatar img")).toHaveAttribute("src", uploadedUrl);
  expect(fixture.uploadCount()).toBe(1);
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("learning_user")).avatarUrl)).toBe(uploadedUrl);
  // The server, rather than the cached user object, must restore the persisted photo.
  await page.evaluate(() => {
    const user = JSON.parse(localStorage.getItem("learning_user"));
    delete user.avatarUrl;
    localStorage.setItem("learning_user", JSON.stringify(user));
  });
  await page.reload();
  await expect(page.locator(".profile-trigger .user-avatar img")).toHaveAttribute("src", uploadedUrl);
  expect(fixture.uploadCount()).toBe(1);
});

test("无效或过大的新照片不可提交，并清除之前待保存的选择", async ({ page }) => {
  const fixture = await setup(page);
  await openAvatar(page);
  const input = page.getByLabel("选择头像照片");
  await input.setInputFiles(fixture.file);
  await expect(page.getByRole("button", { name: "保存头像", exact: true })).toBeEnabled();
  await input.setInputFiles({ name: "unsafe.svg", mimeType: "image/svg+xml", buffer: Buffer.from("<svg/>") });
  await expect(page.getByRole("alert")).toContainText("JPEG、PNG 或 WebP");
  await expect(page.getByRole("button", { name: "保存头像", exact: true })).toBeDisabled();
  await input.setInputFiles({ name: "large.png", mimeType: "image/png", buffer: Buffer.alloc(5 * 1024 * 1024 + 1) });
  await expect(page.getByRole("alert")).toContainText("不能超过 5 MB");
  await expect(page.getByRole("button", { name: "保存头像", exact: true })).toBeDisabled();
  expect(fixture.uploadCount()).toBe(0);
});

test("损坏头像回退为居中字母，手机设置对话框不超出屏幕", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page, "/uploads/avatars/missing.png");
  const avatar = page.locator(".profile-trigger .user-avatar");
  await expect(avatar.locator(".user-avatar-initial")).toHaveText("头");
  await expect(avatar.locator("img")).toHaveCount(0);
  const circle = await avatar.boundingBox();
  const initial = await avatar.locator(".user-avatar-initial").boundingBox();
  expect(Math.abs(circle.x + circle.width / 2 - initial.x - initial.width / 2)).toBeLessThan(2);
  expect(Math.abs(circle.y + circle.height / 2 - initial.y - initial.height / 2)).toBeLessThan(2);
  await openAvatar(page);
  const dialog = await page.getByRole("dialog", { name: "更换头像" }).boundingBox();
  expect(dialog.x).toBeGreaterThanOrEqual(0);
  expect(dialog.x + dialog.width).toBeLessThanOrEqual(390);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog", { name: "更换头像" })).toHaveCount(0);
  await expect(page.locator(".profile-trigger")).toBeFocused();
});
