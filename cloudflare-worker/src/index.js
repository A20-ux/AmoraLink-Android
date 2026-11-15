export default {
  async fetch(request) {
    const url = new URL(request.url);

    const headers = {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization"
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers });
    }

    if (url.pathname === "/health" && request.method === "GET") {
      return Response.json({
        service: "Amora Link Relay",
        status: "online",
        version: "0.1.0",
        remote_commands_enabled: false
      }, { headers });
    }

    if (url.pathname === "/v1/commands") {
      return Response.json({
        success: false,
        message: "Command delivery is not configured yet."
      }, { status: 503, headers });
    }

    return Response.json({
      success: false,
      message: "Endpoint not found."
    }, { status: 404, headers });
  }
};
