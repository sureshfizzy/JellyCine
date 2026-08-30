# 已归档：手机端播放与库（2026-08）

上一轮 `TODO.md` 整份移到这里，工作文件清空后只保留新计划。
范围：ZeroDevi1/Vela fork 的手势、overlay、多服务器、聚合搜索、线路路由。

## 已确认分叉（已落地）

- 手势：单击中间显隐；单击左/右快退/快进（约 2:1:2）；长按临时倍速。
- 长按倍速默认 2.0x；单击左右秒数跟设置。
- 小窗：Android PictureInPicture。
- 聚合搜索：已登录 Emby/Jellyfin 并发搜索，chips 筛服务器。
- 品牌未改：仍 `Vela` / `com.vela.app`。
- 不对齐 Hills Pro / 弹幕 / Anime4K / FSR。

## 完成项

| Phase | 内容 | 状态 |
|---|---|---|
| 0 | MPV 色彩：`target-prim`/`target-trc` 与 Surface dataspace 对齐 | 代码完成；K50 面板实测未关 |
| 1 | 手势 + 进度 HUD + 预览 | 完成 |
| 2 | Overlay（HW+ / PiP / 章节 / 倍速） | 完成 |
| 3 | 播放中点标题悬浮详情 | 完成 |
| 4 | 多服务器页（DataStore，不开 Room） | 完成 |
| 5 | 库排序芯片 + 库内收藏 tab | 完成 |
| 6 | 多库聚合搜索 | 完成 |
| 7 | 线路 Wi‑Fi / WAN 自动路由 | 完成 |
| 8 | 默认关 deband；海报预取降并发 | 部分；`gpu` vs `gpu-next`、滑动掉帧未关 |
| 9 | 关于页 Based on Vela | 部分；详情/库收口未做 |

## 未完成遗留（未进新计划优先级）

- K50 上 SDR / HDR 与 Exo 并排验收（用户反馈暂时正常）。
- 评估 `gpu` vs `gpu-next`。
- 首页 / 库滑动掉帧再决定换 Coil。
- 详情全宽继续播放、演职人员页、库已看勾。

这些若和新计划冲突，以新 `TODO.md` 为准。杜比验收仍以 K50 为准。
