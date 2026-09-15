// shared fetch helpers – session cookie goes with credentials

// Resolve /api/... against the current page so context path is kept
// e.g. /parking-reservation-system/login.html + api/auth/login
//   -> /parking-reservation-system/api/auth/login
function apiUrl(path) {
  const rel = String(path).replace(/^\//, "");
  return new URL(rel, window.location.href).pathname;
}

const API = {
  async req(path, options = {}) {
    const url = apiUrl(path);
    const opts = {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options
    };
    const res = await fetch(url, opts);
    let json;
    try {
      json = await res.json();
    } catch (e) {
      throw Object.assign(new Error("Bad response (HTTP " + res.status + ")"), { status: res.status });
    }
    if (!json.ok) {
      const err = new Error(json.error || ("HTTP " + res.status));
      err.status = res.status;
      throw err;
    }
    return json.data;
  },
  get(path) {
    return this.req(path);
  },
  post(path, body) {
    return this.req(path, { method: "POST", body: JSON.stringify(body || {}) });
  },
  del(path) {
    return this.req(path, { method: "DELETE" });
  }
};

function qs(sel) {
  return document.querySelector(sel);
}

function showMsg(el, text, ok) {
  if (!el) return;
  el.className = "msg " + (ok ? "ok" : "err");
  el.textContent = text;
  el.hidden = false;
}

function requireAuth(roles) {
  return API.get("/api/auth/me")
    .then((data) => {
      if (roles && roles.length && !roles.includes(data.user.role)) {
        window.location.href = "index.html";
        return null;
      }
      return data;
    })
    .catch(() => {
      window.location.href = "login.html";
      return null;
    });
}

function logout() {
  return API.post("/api/auth/logout", {}).finally(() => {
    window.location.href = "login.html";
  });
}

function fmt(dt) {
  if (!dt) return "—";
  return String(dt).replace("T", " ").substring(0, 16);
}
