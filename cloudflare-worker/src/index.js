/* Amora Link Relay —— 设备注册 / 指令队列 / 轮询 / 回执。
 *
 * 鉴权模型：
 *  - 配对令牌（pairing token）由手机端生成，外部 AI/操作者凭它提交指令；Worker 只存其 SHA-256 哈希。
 *  - 设备注册后拿到 device_id + secret，手机端用这对凭证轮询；Worker 只存 secret 哈希。
 *  - Worker 只做排队与转发，不校验指令业务合法性；手机端 AiControlManager 仍会二次校验
 *    （配对、时间戳、编号防重、目标应用白名单）。
 *
 * KV 绑定：COMMAND_KV（在 wrangler.toml 配置 namespace id）。
 * 键位：
 *  bytoken:<sha256(pairingToken)> -> deviceId
 *  dev:<deviceId>                 -> { secretHash, tokenHash, deviceName, createdAt, lastSeen }
 *  q:<deviceId>                   -> [ { command_id, payload, createdAt } ]
 */

const CORS = {
  "Content-Type": "application/json; charset=utf-8",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization"
};

async function sha256Hex(str) {
  const data = new TextEncoder().encode(String(str || ""));
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function randomId(byteLen) {
  const arr = new Uint8Array(byteLen);
  crypto.getRandomValues(arr);
  return btoa(String.fromCharCode(...arr))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: CORS });
}

async function readBody(request) {
  try {
    return await request.json();
  } catch (_) {
    return null;
  }
}

const ALLOWED_ACTIONS = ["AI_LOCK", "AI_UNLOCK"];

// ---------- MCP（Model Context Protocol）Streamable HTTP 兼容层 ----------

const MCP_PROTOCOL_VERSION = "2024-11-05";

const MCP_TOOLS = [
  {
    name: "lock_companion_app",
    description: "向已配对手机下发锁定指令，把指定陪伴应用锁定在专注状态。",
    inputSchema: {
      type: "object",
      properties: {
        package_name: {
          type: "string",
          description: "目标陪伴应用包名，必须是手机端已选择的陪伴应用之一"
        },
        reason: {
          type: "string",
          description: "锁定原因，可选"
        }
      },
      required: ["package_name"]
    }
  },
  {
    name: "unlock_device",
    description: "解除手机端当前的 AI 锁定状态。",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "device_status",
    description: "查询已配对设备的在线状态、最后心跳时间与待处理指令数量。",
    inputSchema: { type: "object", properties: {} }
  }
];

function bearerToken(request) {
  const header = request.headers.get("Authorization") || "";
  if (header.startsWith("Bearer ")) return header.slice(7).trim();
  return "";
}

// 入队一条指令：统一补 command_id、timestamp_ms、auth_token，手机端二次校验。
async function enqueueCommand(kv, deviceId, token, action, extra = {}) {
  const commandId = randomId(16);
  const payload = {
    action,
    auth_token: token,
    command_id: commandId,
    timestamp_ms: Date.now(),
    ...extra
  };

  const queue = (await kv.get(`q:${deviceId}`, "json")) || [];
  queue.push({ command_id: commandId, payload, createdAt: Date.now() });
  while (queue.length > 50) queue.shift();
  await kv.put(`q:${deviceId}`, JSON.stringify(queue));
  return commandId;
}

function mcpResult(id, result) {
  return json({ jsonrpc: "2.0", id, result });
}

function mcpError(id, code, message, httpStatus = 200) {
  return json({ jsonrpc: "2.0", id, error: { code, message } }, httpStatus);
}

async function resolveDeviceByToken(kv, token) {
  if (!token) return null;
  const tokenHash = await sha256Hex(token);
  const deviceId = await kv.get(`bytoken:${tokenHash}`);
  return deviceId || null;
}

async function handleMcp(request, kv) {
  const body = await readBody(request);
  const method = body?.method;
  const id = body?.id;

  if (method === "initialize") {
    return mcpResult(id, {
      protocolVersion: MCP_PROTOCOL_VERSION,
      capabilities: { tools: {} },
      serverInfo: { name: "amora-link-relay", version: "0.3.0" }
    });
  }

  // 客户端握手完成通知，无需返回内容。
  if (method === "notifications/initialized") {
    return new Response(null, { status: 202, headers: CORS });
  }

  if (method === "tools/list") {
    return mcpResult(id, { tools: MCP_TOOLS });
  }

  if (method === "tools/call") {
    const token = bearerToken(request);
    const deviceId = await resolveDeviceByToken(kv, token);
    if (!deviceId) {
      return mcpError(id, -32001, "unpaired or invalid bearer token", 401);
    }

    const toolName = body?.params?.name;
    const args = body?.params?.arguments || {};

    if (toolName === "lock_companion_app") {
      const packageName = String(args.package_name || "").trim();
      if (!packageName) {
        return mcpError(id, -32002, "package_name is required");
      }
      const reason = String(args.reason || "AI 专注请求").slice(0, 120);
      const commandId = await enqueueCommand(kv, deviceId, token, "AI_LOCK", {
        package_name: packageName,
        reason
      });
      return mcpResult(id, {
        content: [{ type: "text", text: JSON.stringify({ success: true, command_id: commandId, action: "AI_LOCK" }) }]
      });
    }

    if (toolName === "unlock_device") {
      const commandId = await enqueueCommand(kv, deviceId, token, "AI_UNLOCK");
      return mcpResult(id, {
        content: [{ type: "text", text: JSON.stringify({ success: true, command_id: commandId, action: "AI_UNLOCK" }) }]
      });
    }

    if (toolName === "device_status") {
      const dev = await kv.get(`dev:${deviceId}`, "json");
      const queue = (await kv.get(`q:${deviceId}`, "json")) || [];
      const now = Date.now();
      const lastSeen = dev?.lastSeen || 0;
      return mcpResult(id, {
        content: [{
          type: "text",
          text: JSON.stringify({
            device_id: deviceId,
            device_name: dev?.deviceName || "",
            online: lastSeen > 0 && now - lastSeen < 90_000,
            last_seen: lastSeen,
            pending_commands: queue.length
          })
        }]
      });
    }

    return mcpError(id, -32003, `unknown tool: ${toolName}`);
  }

  return mcpError(id, -32601, `method not found: ${method}`);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS });
    }

    if (url.pathname === "/health" && request.method === "GET") {
      return json({
        service: "Amora Link Relay",
        status: "online",
        version: "0.3.0",
        remote_commands_enabled: !!env.COMMAND_KV,
        mcp_endpoint: "/mcp"
      });
    }

    const kv = env.COMMAND_KV;
    if (!kv) {
      return json({ success: false, message: "KV namespace is not configured." }, 503);
    }

    // MCP Streamable HTTP 端点：JSON-RPC 2.0，Bearer 配对令牌鉴权。
    if (url.pathname === "/mcp" && request.method === "POST") {
      return handleMcp(request, kv);
    }

    // 设备注册：提交配对令牌，换发设备凭证（device_id + secret）。
    if (url.pathname === "/v1/register" && request.method === "POST") {
      const body = await readBody(request);
      const token = String(body?.token || "").trim();
      const deviceName = String(body?.device_name || "").slice(0, 60);

      if (token.length < 32 || token.length > 128) {
        return json({ success: false, message: "invalid pairing token" }, 400);
      }

      const tokenHash = await sha256Hex(token);
      let deviceId = await kv.get(`bytoken:${tokenHash}`);
      const secret = randomId(24);
      const secretHash = await sha256Hex(secret);
      const now = Date.now();

      if (!deviceId) {
        deviceId = randomId(12);
      }

      const existing = deviceId ? await kv.get(`dev:${deviceId}`, "json") : null;

      await kv.put(`bytoken:${tokenHash}`, deviceId);
      await kv.put(
        `dev:${deviceId}`,
        JSON.stringify({
          secretHash,
          tokenHash,
          deviceName,
          createdAt: existing?.createdAt || now,
          lastSeen: now
        })
      );

      return json({ success: true, device_id: deviceId, secret });
    }

    // 外部 AI/操作者提交指令：凭配对令牌入队。
    if (url.pathname === "/v1/commands" && request.method === "POST") {
      const body = await readBody(request);
      const token = String(body?.auth_token || "").trim();
      const deviceId = await resolveDeviceByToken(kv, token);

      if (!deviceId) {
        return json({ success: false, message: "unpaired device" }, 401);
      }

      const action = String(body?.action || "");
      if (!ALLOWED_ACTIONS.includes(action)) {
        return json({ success: false, message: "unsupported action" }, 400);
      }

      const extra = {};
      if (body.package_name != null) extra.package_name = String(body.package_name).trim();
      if (body.reason != null) extra.reason = String(body.reason).slice(0, 120);

      const commandId = await enqueueCommand(kv, deviceId, token, action, extra);
      return json({ success: true, command_id: commandId });
    }

    // 设备轮询：凭 device_id + secret 拉取待执行指令。
    if (url.pathname === "/v1/poll" && request.method === "POST") {
      const body = await readBody(request);
      const deviceId = String(body?.device_id || "").trim();
      const secret = String(body?.secret || "");

      const dev = deviceId ? await kv.get(`dev:${deviceId}`, "json") : null;
      if (!dev || (await sha256Hex(secret)) !== dev.secretHash) {
        return json({ success: false, message: "auth failed" }, 401);
      }

      dev.lastSeen = Date.now();
      await kv.put(`dev:${deviceId}`, JSON.stringify(dev));

      const queue = (await kv.get(`q:${deviceId}`, "json")) || [];
      return json({ success: true, commands: queue });
    }

    // 设备回执：执行完成后按 command_id 移出队列。
    if (url.pathname === "/v1/ack" && request.method === "POST") {
      const body = await readBody(request);
      const deviceId = String(body?.device_id || "").trim();
      const secret = String(body?.secret || "");
      const commandId = String(body?.command_id || "").trim();

      const dev = deviceId ? await kv.get(`dev:${deviceId}`, "json") : null;
      if (!dev || (await sha256Hex(secret)) !== dev.secretHash) {
        return json({ success: false, message: "auth failed" }, 401);
      }

      let queue = (await kv.get(`q:${deviceId}`, "json")) || [];
      queue = queue.filter((item) => item.command_id !== commandId);
      await kv.put(`q:${deviceId}`, JSON.stringify(queue));

      return json({ success: true });
    }

    return json({ success: false, message: "Endpoint not found." }, 404);
  }
};
