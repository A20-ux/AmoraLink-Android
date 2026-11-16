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
        version: "0.2.0",
        remote_commands_enabled: !!env.COMMAND_KV
      });
    }

    const kv = env.COMMAND_KV;
    if (!kv) {
      return json({ success: false, message: "KV namespace is not configured." }, 503);
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

      await kv.put(`bytoken:${tokenHash}`, deviceId);
      await kv.put(
        `dev:${deviceId}`,
        JSON.stringify({
          secretHash,
          tokenHash,
          deviceName,
          createdAt: now,
          lastSeen: now
        })
      );

      return json({ success: true, device_id: deviceId, secret });
    }

    // 外部 AI/操作者提交指令：凭配对令牌入队。
    if (url.pathname === "/v1/commands" && request.method === "POST") {
      const body = await readBody(request);
      const token = String(body?.auth_token || "").trim();
      const tokenHash = await sha256Hex(token);
      const deviceId = await kv.get(`bytoken:${tokenHash}`);

      if (!deviceId) {
        return json({ success: false, message: "unpaired device" }, 401);
      }

      const action = String(body?.action || "");
      const commandId = String(body?.command_id || "").trim();

      if (!ALLOWED_ACTIONS.includes(action)) {
        return json({ success: false, message: "unsupported action" }, 400);
      }
      if (commandId.length < 8 || commandId.length > 128) {
        return json({ success: false, message: "invalid command_id" }, 400);
      }

      const queue = (await kv.get(`q:${deviceId}`, "json")) || [];
      queue.push({ command_id: commandId, payload: body, createdAt: Date.now() });
      while (queue.length > 50) queue.shift();
      await kv.put(`q:${deviceId}`, JSON.stringify(queue));

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
