# Vela 手机端开发计划

ZeroDevi1/Vela fork。本文件是已确认的实现顺序，不是愿望清单。
TV 模块：手势 / 竖屏 overlay 不跟；色彩、搜索、线路、收藏能下沉 data 层的共用。

参考图：K50 Ultra `/sdcard/DCIM/Screenshots/`（Hills `com.mountains.hills`、Yamby `com.hush.yamby`）。拉图用 skill `jellycine-screenshot-refs`。

验收机：**Redmi K50 Ultra（第一代骁龙 8+ / Android 15）**，可 adb。当前 adb 常连小米 12T `22081212C`，装包和截图可以；**MPV 色彩必须以 K50 实测为准**。

---

## 已确认分叉

- **手势（Yamby 混合）**：单击中间显隐控件；单击左/右快退/快进（分区约 2:1:2）；长按临时倍速。不要改回「单击就三区 seek」或「只双击 seek」。
- **长按倍速**：默认 **2.0x**，设置可改；松手恢复原速。
- **单击左右秒数**：跟设置里的快退/快进间隔（10/30），不写死 10。
- **小窗**：必须做 Android `PictureInPicture`。
- **聚合搜索**：所有已登录 Emby/Jellyfin 并发搜索，结果合并一列，顶部 chips 可筛服务器。点条目用该服务器 session。
- **品牌**：这一期不改显示名、icon、包名。仍用 `Vela` / `com.vela.app`。关于页可加致敬一行。
- **不对齐**：Hills Pro / 弹幕 / Anime4K / FSR。

---

## 优先级

1. **P0** MPV 色彩对齐 Exo
2. **P0** 手势 + 进度 HUD
3. **P1** Yamby overlay 按钮（含 HW+、小窗、章节、倍速）
4. **P1** 点标题悬浮详情（上视频下详情，可切集）
5. **P2** 多 Emby/Jellyfin 服务器
6. **P2** Hills 库排序芯片、库收藏 tab
7. **P2** 多库聚合搜索
8. **P2** 线路 Wi‑Fi / WAN 自动路由
9. **持续** K50 性能、界面收口

---

## Phase 0 — MPV 色彩（P0）

目标：同一条 SDR / HDR 片源，MPV 观感对齐 Exo。这是观感阻塞项，不顺手堆 UI。

### 现状

- 默认引擎 MPV：`vo=gpu-next`，`hwdec=mediacodec`，`video-output-levels=full`
- HDR→SDR 默认关；HDR 走 `DATASPACE_BT2020_PQ`，否则 `DATASPACE_V0_SRGB`
- `MpvPlayerController` 在 `PLAYBACK_RESTART` 后反射 `Surface.setDataSpace`
- Exo 色彩 OK

### 嫌疑

- `full` 电平 vs Android Surface 常按 limited
- `gpu-next` + 反射 dataspace，Android 15 / 小米色彩管线二次转换
- `mediacodec` 直出，色域 / gamma 和 Surface 对不齐

### 做法

- [ ] K50 固定片源：SDR 8bit、HDR10、杜比（若有），MPV vs Exo 并排截屏
- [x] 对齐 `target-prim`/`target-trc` 与 Surface dataspace（PQ/HLG 直出，SDR 走 bt.709+sRGB）
- [x] 播放页保留硬解/软解切换（Phase 2 overlay `HW+`），默认仍 MPV
- [ ] K50 实测验收（用户反馈色彩已暂时正常，仍以 K50 面板为准）

jniLibs 更新走 `scripts/sync-mpv-natives.sh`，源是 **mpv-android**（Yamby 同套 GLES），不是 Hills Vulkan，也不是把 ffmpeg/mpv 编进本仓库 Gradle。上游 `buildscripts` 只支持 Linux/macOS，**Windows / WSL 编不了**。默认 tag `2025-12-27`（libmpv 0.41.0 / FFmpeg 8 `LIBAVUTIL_60`）。`2026-08-11` 已是 FFmpeg 9，不要当 latest 直接灌。必须整套 so 替换，不能只换 `libmpv.so`，也不能跟 `org.jellycine.mpv` AAR 混装。

### 锚点

- `core/.../MpvPlayerController.kt`（`applySurfaceDataSpace`、`video-output-levels`、`target-prim`/`target-trc`）
- `core/.../PlayerPreferences.kt`（`DEFAULT_MPV_*`、`DEFAULT_MPV_HDR_TO_SDR_TONEMAPPING`）

### 验收

K50 上 SDR / HDR 不发灰、不过饱和、不偏绿；切 Exo 无明显色差。

---

## Phase 1 — 播放手势与进度 HUD（P0）

### 现状

- 单击 = 显隐控件
- 双击左/中/右 2:1:2 = 快退 / 暂停 / 快进
- 横向滑动 seek，OSD 为左右 `±Ns`
- 拖进度条只改条旁 `当前/总时长`
- 无长按倍速

### 计划
- [x] 单击中间：仍显隐控件
- [x] 单击左 / 右：快退 / 快进（秒数走 `PlayerPreferences` 的 seek 间隔）
- [x] 长按：临时 2.0x（设置可改），OSD `2.0x`，松手恢复
- [x] 拖底部进度条：屏幕**上方**大号 `滑动到的时间 / 总时间`，松手消失
- [x] 拖动底部进度条时，在进度条上方显示对应时间点的画面预览，松手后消失
- [x] 屏幕横向滑动：最上方一条进度条跟手，松手 seek，滑动中可停

### 锚点

- `phone/.../player/GestureHelper.kt`
- `phone/.../player/PlayerGestureLayer.kt`
- `phone/.../player/GestureIndicators.kt`
- `phone/.../player/ControlsOverlay.kt`（`scrubPreviewProgress`）

---

## Phase 2 — 播放 overlay（P1，对齐 Yamby 23:58）

竖屏顶栏胶囊：`HW+`、小窗、画面比例、章节、更多。底栏：锁、±seek、播放、倍速 `±`。

| Yamby | 现状 | 计划 |
|---|---|---|
| HW+ | 仅设置里有硬解 | 播放中切 `mediacodec` / `no` |
| 小窗 | 无 | Android PiP，必须做 |
| 章节 | 进度条有标记，无列表 | 章节 sheet，点选 seek |
| 倍速 | 无 | overlay `1.0x ±`，同时服务长按 |
| 音轨 / 字幕 / 旋转 / 比例 | 已有 | 位置按截图收拢 |
| 更多 | 媒体信息 / 画质 | 露出已有偏好。不做 Anime4K / FSR |

- [x] 顶栏 HW+ / 小窗 / 比例 / 章节 / 更多
- [x] 底栏锁、±seek（秒数跟设置）、播放、倍速
- [x] `PictureInPicture`：进后台或点按钮进入；系统限制时 toast，不静默失败
- [x] 横屏同一套动作，不把竖屏按钮堆到画面中间
- [x] 修复进入 `PictureInPicture` 后视频被 `ON_PAUSE` 暂停；PiP 内保持进入前的播放状态

### 锚点

- `phone/.../player/ControlsOverlay.kt`（`PortraitPlayerOverlay`）
- `phone/.../player/PlayerScreen.kt` / `PlayerScreenSections.kt`
- `phone/.../player/PlayerDialogs.kt`

---

## Phase 3 — 点标题悬浮详情（P1，对齐 Yamby 23:45）

替换现在的全屏 `ModalBottomSheet`。

- [x] 视频留在上方 16:9，继续播
- [x] 下方可滚：海报、标题、集数、日期、简介、**演员**、导演
- [x] 剧集横滑，点集即切，不关详情
- [x] 上滑收起回到纯播放
- [x] 播放请求补 `People`（`LIBRARY_ITEM_FIELDS` 现在没有）

### 锚点

- `phone/.../player/PlaybackInfoSheet.kt`（现仅 title / year / rating / overview / 从头播放）
- `phone/.../player/PlayerScreen.kt`（`onTitleClick`）
- `data/.../BaseItemDto.kt`（`people`）
- `phone/.../media/LibraryBrowse.kt`（`LIBRARY_ITEM_FIELDS`）

---

## Phase 4 — 多服务器（P2，对齐 Hills / Yamby 服务器页）

现状：`SavedServer` 已能存多台，token 在 `SecureSessionStore`，切服走弹窗。缺 Hills 式列表和页内添加。

持久化继续用现有 **DataStore `auth_prefs` + `saved_servers_v1` JSON**，token 仍走 `SecureSessionStore`。不要另开 Room：会变成第二事实源，登录/切服/删服已经写这条路径。

- [x] 独立「服务器」页：头像、服务器名、绿点 + 用户名、溢出菜单、FAB `+`
- [x] 添加对话框：地址、协议、端口、可选路径、用户名、密码；连接成功即 `authenticateUser` upsert 并切到新会话
- [x] 点条目 `switchServer`；溢出可删非当前服、可给该服加用户
- [x] 首页顶栏 / 设置点服务器名进入此页，不再只靠弹窗
- [x] 打开应用先进入服务器页，点条目进入该服 Emby/Jellyfin 首页

### 锚点

- `data/.../AuthRepository.kt`（`SavedServer`、`authenticateUser`、`switchServer`、`removeSavedServer`）
- `phone/.../settings/ServersScreen.kt`
- `phone/.../settings/ServersViewModel.kt`

---

## Phase 5 — 库排序 + 收藏（P2，对齐 Hills 08:18）

### 排序

现状：`librarySortFields()` + 右下 `SortFAB` / `SortBottomSheet`。缺标题、比特率、随机；UI 不是顶栏芯片。

Hills 字段：加入日期、标题、公众评分、影评人评分、出品年份、首映日期、官方评级、播放日期、播放时长、比特率、大小、随机。

- [x] 顶栏芯片「加入日期 ↓」下拉，去掉或降级 `SortFAB`
- [x] 对齐 Hills 字段；分辨率 / 封装 / 帧率 / 导演 / 播放次数放「更多」
- [x] 补 `Bitrate`、`Random`；「标题」用 `SortName`（不要再用文件名标签冒充标题）

### 收藏

现状：底栏 `Favorites` + `getFavoriteItems()`，仅当前服务器。库 tab 无「收藏」。

- [x] 底栏收藏页保留
- [x] 库 tab 加「收藏」（Hills：全部 / 继续观看 / 收藏 / 类型 / 标签 / 合集）
- [x] 跨服收藏跟 Phase 6 一起，本阶段仍打当前服务器

### 锚点

- `phone/.../media/LibraryBrowse.kt`
- `phone/.../media/ViewAllScreen.kt`（`SortFAB` / `SortBottomSheet` / `LibraryBrowseTab`）
- `phone/.../favorites/Favorites.kt`

---

## Phase 6 — 多库聚合搜索（P2）

现状：`SearchViewModel` 只打活动服务器 + 可选 Seerr。

- [x] 对所有已登录、token 有效的 `SavedServer` 并发 `searchItems`
- [x] 结果带 `serverId` / 服务器名徽章
- [x] chips：全部 / 各服务器
- [x] 点条目用该服务器 session 开详情 / 播放
- [x] 单服务器失败降级提示，不让整次搜索挂掉
- [x] 限并发，避免 K50 上同时打爆 4+ 台

### 锚点

- `phone/.../search/SearchViewModel.kt` / `SearchContainer.kt`
- `data/.../AuthRepository.kt`（`observeSavedServers`）

---

## Phase 7 — 线路自动路由（P2）

现状：`SavedServer.lines` + 手动 `addServerLine` / `switchServerLine`。无 Wi‑Fi / 蜂窝自动选线。

- [x] Wi‑Fi：优先 `isLan()` 线路，探活成功再用
- [x] 蜂窝 / 非局域网：走 WAN
- [x] 当前线路超时 / 失败：切备用，toast 一次
- [x] 设置里可关自动路由、可强制某条
- [x] 切线只换 base URL，token 仍按 `SavedServer.id`

不做全局负载均衡。

### 锚点

- `data/.../model/SavedServer.kt`
- `data/.../repository/AuthRepository.kt`（`addServerLine` / `switchServerLine` / `setAutoRouteEnabled`）
- `data/.../network/ServerLineRouting.kt`
- `data/.../network/NetworkModule.kt`（Wi‑Fi / 蜂窝观察与线路失败回调）

---

## Phase 8 — K50 性能（持续）

8+ Gen1 / Android 15。先动这些，不要先换库。

- [x] 默认关 deband（现在默认开）
- [ ] 色彩修完后评估 `gpu` vs `gpu-next`
- [x] `ImagePreloader.prefetchSemaphore` 12 → 按内存 / 网络降到 6–8
- [x] 海报预取质量 / 尺寸收一档
- [ ] 首页 / 库滑动掉帧再决定换 Coil 或自写解码

换依赖只在：色彩或硬解在 libmpv 绑定层修不动时。

### 锚点

- `phone/.../home/Dashboard.kt`（`ImagePreloader`）
- `PlayerPreferences.DEFAULT_MPV_DEBAND`

---

## Phase 9 — 界面收口（持续）

对照 Hills 详情（唐宫奇案 23:46–47）和库页。

- [ ] 详情：全宽继续播放、导演一行、继续观看横滑、演职人员页
- [ ] 库：tab 位置、已看勾（排序芯片与「全部」文案已落地）
- [ ] 跟现有深色 token，不引入 Hills 粉橙硬编码
- [x] 关于页：`Based on Vela`

改名 / 改包 / 改 icon 放到产品稳定之后。覆盖安装不能丢登录。

---

## 实现约束

- 播放默认竖屏 16:9，已在 `PlayerPreferences` / `PlayerScreen`。
- 多线路 token 按服务器 id，不按 URL。
- 中文字符串进 `values-zh-rCN` 和 `values-zh`。
- 真机验证走 K50；12T 只做辅助截图。
- 提交信息：`<type>(<scope>): <subject>`，type 限定 feat/fix/docs/style/refactor/perf/test/build/ci/chore/revert。
