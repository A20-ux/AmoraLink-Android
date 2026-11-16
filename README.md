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
- **云端中继（轮询）**：配对成功后设备向 Worker 注册并换取设备凭证；手机端每 30 秒轮询待执行指令，交给本地校验通过后执行并回执。Worker 只做排队转发，业务合法性仍由手机端二次校验。
- **云端健康检查**：测试 Cloudflare Worker 连通性。

### 尚未实现
- **实时推送**：当前为 30 秒轮询的普通后台服务，进程被回收后需下次打开 App 拉起；FCM / 华为 Push Kit 等实时推送适配器未接入（`PushProvider` 接口与本地注册存储已就绪）。
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

1. 创建 KV 命名空间：
   ```bash
   cd cloudflare-worker
   npx wrangler kv namespace create amora_commands
   ```
   把返回的 `id` 填进 `wrangler.toml` 的 `COMMAND_KV` 绑定。
2. 部署 Worker：
   ```bash
   npx wrangler deploy
   ```
3. 手机端生成配对密钥后会自动向 `/v1/register` 注册；打开「测试云端连接」，健康检查返回 `remote_commands_enabled: true` 即 KV 已接通。

### 中继接口

| 接口 | 鉴权 | 说明 |
| --- | --- | --- |
| `POST /v1/register` | 配对令牌 | 设备注册，换发 `device_id` + `secret` |
| `POST /v1/commands` | 配对令牌 | 外部 AI / 操作者提交指令并入队 |
| `POST /v1/poll` | 设备凭证 | 设备拉取待执行指令 |
| `POST /v1/ack` | 设备凭证 | 执行后按 `command_id` 回执出队 |

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
