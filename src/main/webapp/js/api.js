// shared fetch helpers – session cookie goes with credentials

// Resolve /api/... against the current page so context path is kept
// e.g. /parking-reservation-system/login.html + api/auth/login
//   -> /parking-reservation-system/api/auth/login
function apiUrl(path) {
  const rel = String(path).replace(/^\//, "");
  const u = new URL(rel, window.location.href);
  // keep ?query — .pathname alone drops points=… on quote URLs
  return u.pathname + u.search;
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
    const text = await res.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch (e) {
      const hint = res.status >= 500
        ? "Server error (HTTP " + res.status + ") — redeploy WAR and verify MySQL + db.properties"
        : "Bad response (HTTP " + res.status + ") — not JSON (wrong URL or login redirect?)";
      throw Object.assign(new Error(hint), { status: res.status });
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

function escUi(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/"/g, "&quot;");
}

// In-page confirm / form. Resolves null on cancel, true when there are no fields,
// or { fieldName: value } when fields are filled.
function uiDialog(opts) {
  const o = opts || {};
  const fields = o.fields || [];
  return new Promise((resolve) => {
    const open = document.querySelector(".ui-dialog-back");
    if (open) open.remove();

    const back = document.createElement("div");
    back.className = "ui-dialog-back";
    const fieldHtml = fields.map((f, i) => {
      const type = f.type || "text";
      const id = "uiDlgField" + i;
      const bits = ['id="' + id + '"', 'name="' + escUi(f.name) + '"'];
      if (type !== "textarea") bits.push('type="' + escUi(type) + '"', 'value="' + escUi(f.value) + '"');
      if (f.min != null) bits.push('min="' + escUi(f.min) + '"');
      if (f.max != null) bits.push('max="' + escUi(f.max) + '"');
      if (f.step != null) bits.push('step="' + escUi(f.step) + '"');
      const control = type === "textarea"
        ? '<textarea id="' + id + '" name="' + escUi(f.name) + '" rows="3">' + escUi(f.value) + "</textarea>"
        : "<input " + bits.join(" ") + " />";
      return '<div class="field"><label for="' + id + '">' + escUi(f.label || "") + "</label>" + control + "</div>";
    }).join("");

    back.innerHTML =
      '<div class="ui-dialog" role="dialog" aria-modal="true">' +
      "<h3>" + escUi(o.title || "Confirm") + "</h3>" +
      (o.message ? "<p>" + escUi(o.message) + "</p>" : "") +
      fieldHtml +
      '<p class="ui-dialog-err" hidden></p>' +
      '<div class="ui-dialog-actions">' +
      '<button type="button" class="btn btn-outline" data-act="cancel">' + escUi(o.cancelLabel || "Cancel") + "</button>" +
      '<button type="button" class="btn ' + (o.danger ? "btn-destructive" : "btn-accent") + '" data-act="ok">' +
      escUi(o.confirmLabel || "OK") + "</button>" +
      "</div></div>";

    function finish(val) {
      document.removeEventListener("keydown", onKey);
      back.remove();
      resolve(val);
    }

    function showErr(text) {
      const el = back.querySelector(".ui-dialog-err");
      el.hidden = !text;
      el.textContent = text || "";
    }

    function collect() {
      if (!fields.length) return { ok: true, value: true };
      const out = {};
      for (let i = 0; i < fields.length; i++) {
        const f = fields[i];
        const el = back.querySelector("#uiDlgField" + i);
        const raw = el ? el.value : "";
        const v = raw.trim();
        if (f.type === "number") {
          const n = Number(v);
          if (v === "" || Number.isNaN(n)) return { ok: false, error: (f.label || "Value") + " is required" };
          if (f.min != null && n < Number(f.min)) return { ok: false, error: (f.label || "Value") + " cannot be below " + f.min };
          if (f.max != null && n > Number(f.max)) return { ok: false, error: (f.label || "Value") + " cannot be above " + f.max };
          out[f.name] = String(n);
        } else if (f.required && v === "") {
          return { ok: false, error: (f.label || "Value") + " is required" };
        } else {
          out[f.name] = raw;
        }
      }
      return { ok: true, value: out };
    }

    function submit() {
      const got = collect();
      if (!got.ok) {
        showErr(got.error);
        return;
      }
      finish(got.value);
    }

    function onKey(e) {
      if (e.key === "Escape") finish(null);
      if (e.key === "Enter" && e.target && e.target.tagName !== "TEXTAREA") {
        e.preventDefault();
        submit();
      }
    }

    back.addEventListener("click", (e) => {
      if (e.target === back) finish(null);
    });
    back.querySelector('[data-act="cancel"]').onclick = () => finish(null);
    back.querySelector('[data-act="ok"]').onclick = submit;
    back.querySelectorAll('input[type="number"]').forEach((el) => {
      const min = el.min === "" ? null : Number(el.min);
      el.addEventListener("keydown", (e) => {
        if (e.key === "-" || e.key === "Subtract") e.preventDefault();
      });
      el.addEventListener("input", () => {
        if (min == null || el.value === "") {
          el.classList.remove("num-blocked");
          return;
        }
        if (Number(el.value) < min) {
          el.classList.add("num-blocked");
          el.value = String(min);
        } else {
          el.classList.remove("num-blocked");
        }
      });
    });

    document.body.appendChild(back);
    document.addEventListener("keydown", onKey);
    const first = back.querySelector("input, textarea, [data-act='ok']");
    if (first) first.focus();
  });
}
