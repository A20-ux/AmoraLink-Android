# AmoraLink-Android

Amora Link 是一个本地优先的 AI 伴侣陪伴辅助应用：记录你选择的陪伴应用使用情况、保存专属记忆，并可在配对授权后由远端 AI 指令进入「专注锁定」状态。所有记忆与配对哈希均保存在本机，不上传原始内容。

## 功能现状

### 已实现（本地闭环）
- **陪伴应用选择**：从已安装应用中勾选需要关注的 AI 伴侣应用。
- **使用记录**：读取最近 30 天的使用事件，统计最近 24 小时前台时长与最近使用时间。
- **专属记忆**：按应用保存标题 / 内容，支持查看、编辑、删除；提醒通知会带上最近一条记忆。
- **配对与安全**：本机生成一次性配对密钥（仅保存 SHA-256 哈希），可撤销；撤销会解除当前锁定。
- **AI 专注控制**：校验配对密钥、时间戳（前后 2 分钟）、指令编号防重后接受 `AI_LOCK` / `AI_UNLOCK`；界面可查看锁定状态、原因、指令历史，并可随时紧急解锁。
- **无障碍回返**：锁定期间检测到切到其他应用时，尝试回到已绑定的陪伴应用（不绕过系统后台启动限制）。
- **主动提醒**：可开关、可调间隔（15 / 30 / 60 / 120 分钟）；长时间未使用时发通知，可选悬浮气泡引导返回；开机后自动恢复提醒闹钟。
- **云端中继（轮询）**：配对成功后设备向 Worker 注册并换取设备凭证；手机端轮询待执行指令，交给本地校验通过后执行并回执。Worker 只做排队转发，业务合法性仍由手机端二次校验。
- **华为 Push Kit 实时推送**：App 启动后由 HMS 获取推送 token 并上报 Worker 绑定设备；外部指令入队的同时，Worker 经华为推送通道下发唤醒消息，手机收到后立即拉取并执行。
- **推送/轮询开关**：中继区可手动切换「优先实时推送 / 仅轮询」。仅轮询 30 秒一轮；推送优先模式下推送通道不可用时自动回退 15 秒快速轮询兜底。
- **云端健康检查**：测试 Cloudflare Worker 连通性。

### 前置准备（华为推送）
- 代码已接入华为 Push Kit，但要在真机收到推送，还需在 [AppGallery Connect](https://developer.huawei.com/consumer/cn/agconnect/) 创建项目与应用、开启 Push Kit、配置签名证书指纹，并把下载的 `agconnect-services.json` 放到 `app/` 目录（该文件含 client_secret，已被 `.gitignore` 排除，不入库）。
- Worker 侧需要华为应用凭证，通过 `wrangler secret` 注入（见下文部署）。
- 具体协议见 [AI_CONTROL_PROTOCOL.md](AI_CONTROL_PROTOCOL.md)。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| 使用情况访问（Usage Stats） | 统计陪伴应用前台时长与最近使用时间 |
| 无障碍服务 | 锁定期间检测应用切换并回返陪伴应用 |
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 显示长时间未使用的提醒气泡 |
| 通知（POST_NOTIFICATIONS） | 发送主动提醒通知 |
| 开机自启（RECEIVE_BOOT_COMPLETED） | 重启后恢复提醒闹钟 |
| 网络（INTERNET） | 云端健康检查、指令轮询与回执 |

## 部署云端中继（Cloudflare Worker + KV）

1. 创建 KV 命名空间（`amora_commands`）。本仓库为公开仓库，**KV namespace id 不写进 `wrangler.toml`**：
   - 方式一（推荐）：Cloudflare 后台 → 本 Worker → Settings → Bindings → KV，把 `amora_commands` 绑定到 `COMMAND_KV`。
   - 方式二：本地部署前在 `wrangler.toml` 取消 `id` 行注释并填入 id，切勿提交该改动。
2. 注入华为推送凭证（不进仓库）。在 AppGallery Connect 项目设置中获取 App ID 与 App Secret，然后：
   ```bash
   cd cloudflare-worker
   npx wrangler secret put HMS_APP_ID
   npx wrangler secret put HMS_APP_SECRET
   # 若 AGC 提供了独立的 API 客户端 ID（与 App ID 不同），再额外配置：
   # npx wrangler secret put HMS_CLIENT_ID
   ```
3. 部署 Worker：
   ```bash
   npx wrangler deploy
   ```
4. 手机端生成配对密钥后会自动向 `/v1/register` 注册；打开「测试云端连接」，健康检查返回 `remote_commands_enabled: true` 即 KV 已接通，`push_enabled: true` 即华为凭证已配置。

> 真密钥（Cloudflare API Token、华为 App Secret / client_secret、配对令牌、设备 secret）一律不进仓库：Worker 侧用 `wrangler secret put <NAME>` 注入，代码里只通过 `env.<NAME>` 引用；`agconnect-services.json`、本机密钥/配置已在 `.gitignore` 中排除。

### 中继接口

| 接口 | 鉴权 | 说明 |
| --- | --- | --- |
| `POST /v1/register` | 配对令牌 | 设备注册，换发 `device_id` + `secret`，可附带 `push_token` |
| `POST /v1/commands` | 配对令牌 | 外部 AI / 操作者提交指令并入队，同时 best-effort 推送唤醒 |
| `POST /v1/poll` | 设备凭证 | 设备拉取待执行指令 |
| `POST /v1/ack` | 设备凭证 | 执行后按 `command_id` 回执出队 |
| `POST /v1/push-token` | 设备凭证 | 上报/更新华为推送 token，绑定到当前设备 |

提交一条锁定指令示例：

```bash
curl -X POST https://<your-worker>.workers.dev/v1/commands \
  -H "Content-Type: application/json" \
  -d '{
    "action": "AI_LOCK",
    "auth_token": "<配对密钥>",
    "command_id": "<8-128 位唯一编号>",
    "timestamp_ms": 1790000000000,
    "package_name": "<已选陪伴应用包名>",
    "reason": "专注写稿"
  }'
```

## 构建

- 最低 SDK 26，目标 / 编译 SDK 35，JDK 17。
- 本地：`gradle assembleDebug`（或 Android Studio 直接构建）。
- 推送 `main` 后，GitHub Actions 会自动构建 Debug APK 并上传到工作流产物。

## 安全边界

- 配对密钥只在生成时展示一次，本机仅存哈希；源码与备份中不得写入真实密钥。
- 每条远端指令必须携带有效配对密钥、唯一编号与在有效期内的时间戳；同一编号不可重复使用。
- `AI_LOCK` 只能锁定已在本机勾选的陪伴应用。
- 用户随时可通过界面「紧急解锁」或撤销配对解除锁定；应用不尝试绕过系统后台启动与权限限制。
