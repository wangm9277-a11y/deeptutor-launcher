# DeepTutor Launcher

给手机用的 **DeepTutor 全屏壳**：Android WebView App + 可选 PWA。

## 默认地址

- Tailscale：`http://100.64.233.119:3782`（手机装了 Tailscale、电脑 DeepTutor 在跑时优先）
- WiFi：`http://192.168.31.137:3782`
- 本机：`http://127.0.0.1:3782`
- 可在 App 内改自定义 URL，并记住上次地址

## 安装 Android APK

1. 打开本仓库 **Actions** → 最新成功构建 → 下载 `deeptutor-debug-apk`，或到 **Releases** 下载 APK。
2. 手机允许「未知来源 / 安装未知应用」。
3. 安装后打开 **DeepTutor**。右上角是一个**很小的黑色设置齿轮**：点一下展开菜单（刷新 / 选地址 / 全屏），再点或点屏幕空白处收起；**按住拖动**可挪位置，下次打开还在原地。
4. 全屏会铺满到顶（含刘海区），并藏系统栏；点一下屏幕可临时显示设置按钮（约 3.5 秒后淡出）。

本地构建（需 JDK 17 + Android SDK）：

```bash
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

## PWA（备选）

GitHub Pages（`docs/`）：

https://wangm9277-a11y.github.io/deeptutor-launcher/

浏览器打开后点地址；可「添加到主屏幕」。注意：从 https 页面跳到局域网 http 一般没问题，但 iframe 嵌 http 会被拦，所以 PWA 用直接跳转。

## 权限说明

- 允许明文 HTTP（局域网 / Tailscale）
- JavaScript、DOM Storage 已开

## CI 说明

本仓库 OAuth 令牌缺少 `workflow` 范围，无法直接推送 `.github/workflows/`。
工作流模板在 `ci/build-apk.yml`：在 GitHub 网页上把它复制到 `.github/workflows/build-apk.yml` 即可启用 Actions 自动打包。
当前 Release APK 由本机构建上传（最新见 Releases）。
