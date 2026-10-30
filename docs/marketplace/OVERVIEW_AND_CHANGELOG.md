# Marketplace 上架文案 / Overview & Changelog

本文件用于 JetBrains Marketplace 的 **Overview** 和 **What's new** 页面，包含中英文对照和截图上传顺序。

## Vendor Details 填法

| 字段 | 建议值 |
| --- | --- |
| Public Vendor Name | `tonkane` |
| Vendor ID | `tonkane`（若被占用，用 `tonkane-ai`） |
| Vendor Status | 个人免费插件选 Non-trader；公司/商业分发选 Trader |
| Email | 你的长期有效邮箱 |
| Show email on Vendor Profile | 按需勾选，公开邮箱可能收到推广邮件 |
| Newsletter | 可不勾选 |

## 预览 / Preview

![设置页](01-settings.jpg)

![审查报告](02-commit-review.jpg)

![本地历史](03-review-history.jpg)

![问题定位](04-findings.jpg)

## 截图 / Screenshots

Marketplace 新插件的概览图片不能在 `plugin.xml` 描述里内嵌，需要进入插件编辑页：

`https://plugins.jetbrains.com/plugin/<pluginId>/edit` → **Plugin Screenshots** → 上传下列图片。

Source code URL: `https://github.com/tonkane/ai-code-review-idea-plugin`

上传顺序：

1. `01-settings.jpg` — 设置页
2. `02-commit-review.jpg` — 审查报告
3. `03-review-history.jpg` — 本地历史
4. `04-findings.jpg` — 问题定位

建议在上传后为每张图填写对应说明：

| 图片 | English caption | 中文说明 |
| --- | --- | --- |
| 01 | Connection settings and editable prompt | 连接设置与可编辑提示词 |
| 02 | Review checked changes in the Commit tool window | 在 Commit 工具窗审查勾选文件 |
| 03 | Local review history | 本地审查历史 |
| 04 | Click a finding to jump to `file:line` | 双击问题跳转到文件行 |

> 注：JetBrains Marketplace 当前对新插件主要使用独立的 **Plugin Screenshots** 轮播，不使用描述里的 `<img>`。所以概览页的图片应通过上面的编辑页上传，`plugin.xml` 只保留文字说明。

## Overview / 概览

### English

**tonkane AI Code Review** reviews checked pending changes or a single commit from the Git Log with any OpenAI-compatible chat API (OpenAI, DeepSeek, Qwen, Moonshot, Zhipu, SiliconFlow, Ollama, or a custom gateway).

- Review checked files in the Commit tool window, or right-click one commit in Git Log.
- Fixed four-dimension Markdown template: standards, business logic, performance, and security.
- Editable prompt, Chinese/English UI and prompt templates, and stable report structure.
- Configurable endpoint (including port), model, API key, headers, timeout, and output file.
- Local review history, clickable findings, and automatic report reload.
- API keys are stored in the IDE credential store, not in plugin settings XML.

**Data note:** only the selected diff and prompt are sent to the endpoint you configure. The plugin itself does not collect telemetry.

### 中文

**tonkane AI Code Review** 使用任意 OpenAI 兼容接口，审查 Git 待提交区中勾选的更改，或 Git 历史记录中选中的单次提交（支持 OpenAI、DeepSeek、通义千问、Moonshot、智谱、SiliconFlow、Ollama 及自定义网关）。

- 在 Commit 工具窗勾选文件审查，或在 Git Log 中右键单条提交审查。
- 固定四维 Markdown 模板：标准规范、业务逻辑、性能、安全。
- 提示词可编辑，支持中英文界面与默认模板，报告结构稳定。
- 可配置接口地址（含端口）、模型、API Key、请求头、超时和输出文件。
- 本地审查历史、可点击的问题定位、报告自动重新加载。
- API Key 保存在 IDE 凭据库，不写入插件设置 XML。

**数据说明：** 仅将选中的 diff 和提示词发送到您配置的接口；插件本身不收集遥测数据。

## What's new / 更新说明

### 1.1.7

**English:** Maintenance release; no functional changes.

**中文：** 维护版本，无功能变更。

### 1.1.1

**English:** Keep the review/history actions in the bottom commit action bar only.

**中文：** 审查/历史操作仅保留在底部提交操作栏。

### 1.1.0

**English:** Add Chinese/English UI and prompt templates, Git Log commit review, local review history, and clickable finding navigation.

**中文：** 新增中英文界面与提示词模板、Git Log 提交审查、本地审查历史和可点击的问题定位。

### 1.0.3

**English:** Reload an already-open `CODE_REVIEW.md` after each review.

**中文：** 每次审查后自动重新加载已打开的 `CODE_REVIEW.md`。

### 1.0.2

**English:** Fix the connection test status getting stuck behind the modal Settings dialog.

**中文：** 修复设置窗口内“测试连接”状态一直卡住的问题。

### 1.0.1

**English:** Add the action to the Commit tool window toolbar (new UI), not only the context menu.

**中文：** 除右键菜单外，在 Commit 工具窗工具栏也增加操作入口。

### 1.0.0

**English:** Initial release.

**中文：** 首次发布。
