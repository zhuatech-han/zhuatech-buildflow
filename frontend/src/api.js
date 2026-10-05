// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let csrf;
/** 请求超时解除等待，允许保留表单后重试；幂等键仍由业务表单保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function request(url, options = {}) {
  try {
    return await fetch(url, { ...options, signal: AbortSignal.timeout(20000) });
  } catch {
    throw new Error("NETWORK_ERROR");
  }
}
/** 同源请求；CSRF 令牌保存在内存，会话由 HttpOnly Cookie 管理。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, method = "GET", body) {
  if (!csrf || path === "/auth/csrf") {
    const response = await request("/api/auth/csrf");
    if (!response.ok) throw new Error("NETWORK_ERROR");
    csrf = await response.json();
    if (path === "/auth/csrf") return csrf;
  }
  const response = await request("/api" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(method === "GET" ? {} : { [csrf.header]: csrf.token }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const value = await response.json().catch(() => ({ code: "NETWORK_ERROR" }));
  if (!response.ok) {
    if (response.status === 401) csrf = null;
    throw new Error(value.code || "NETWORK_ERROR");
  }
  return value;
}
/** 会话结束后重新生成 CSRF 引导。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetCsrf() {
  csrf = null;
}
/** 下载已权限校验的成本报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function downloadReport() {
  await api("/auth/me");
  const a = document.createElement("a");
  a.href = "/api/reports.csv";
  a.download = "buildflow-costs.csv";
  document.body.append(a);
  a.click();
  a.remove();
}
/** 使用同源CSRF和会话上传现场图片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function upload(path, file) {
  await api("/auth/csrf");
  const data = new FormData();
  data.append("file", file);
  const r = await request("/api" + path, {
    method: "POST",
    headers: { [csrf.header]: csrf.token },
    body: data,
  });
  const v = await r.json();
  if (!r.ok) throw new Error(v.code || "NETWORK_ERROR");
  return v;
}
