let lastReport = null;

function lockMin(id, min, allowEmpty) {
  const el = typeof id === "string" ? qs("#" + id) : id;
  if (!el || el.dataset.minLocked === "1") return;
  el.dataset.minLocked = "1";
  el.min = String(min);
  el.addEventListener("keydown", (e) => {
    if (e.key === "-" || e.key === "Subtract") e.preventDefault();
  });
  const guard = () => {
    if (el.value === "") {
      el.classList.toggle("num-blocked", !allowEmpty);
      return;
    }
    const n = Number(el.value);
    if (el.value === "-" || Number.isNaN(n) || n < min) {
      el.classList.add("num-blocked");
      el.value = String(min);
      return;
    }
    el.classList.remove("num-blocked");
  };
  el.addEventListener("input", guard);
  el.addEventListener("change", guard);
}

function lockManagerNumbers() {
  lockMin("newRuleMult", 0, false);
  lockMin("newSlotRate", 0, false);
  lockMin("updSlotId", 1, true);
  lockMin("updSlotRate", 0, true);
  document.querySelectorAll("#loyRules input[type='number']").forEach((el) => lockMin(el, 0, false));
}

lockManagerNumbers();

requireAuth(["MANAGER", "ADMIN"]).then((d) => {
  if (!d) return;
  qs("#who").textContent = d.user.fullName;
  refreshAll();
  setInterval(refreshAll, 10000);
});

async function refreshAll() {
  try {
    const occ = await API.get("/api/map/occupancy?facilityId=1");
    qs("#occ").innerHTML = Object.entries(occ.counts)
      .map(([k, v]) => `<div class="row"><span class="badge">${k}</span><strong>${v}</strong></div>`).join("");

    const sum = await API.get("/api/feedback/summary?facilityId=1");
    qs("#sum").textContent = `Average ${Number(sum.average || 0).toFixed(2)} from ${sum.total} reviews`;

    const fb = await API.get("/api/feedback/recent");
    qs("#fb").innerHTML = fb.slice(0, 5).map((f) =>
      `<div>${f.customer} · ${f.rating}★ · ${f.comment || "(no comment)"}</div>`
    ).join("") || "No feedback yet";

    const rev = await API.get("/api/payments/revenue");
    qs("#rev").innerHTML = rev.map((p) => `
      <tr>
        <td>${p.invoiceNo || "—"}</td><td>${p.reservationId}</td>
        <td>LKR ${p.finalAmount}</td><td><span class="badge">${p.status}</span></td>
        <td>${p.status === "PAID"
          ? `<button class="btn btn-outline btn-sm" onclick="refund(${p.paymentId})">Refund</button>` : ""}</td>
      </tr>`).join("") || `<tr><td colspan="5">No payments</td></tr>`;

    const inq = await API.get("/api/inquiries/");
    qs("#inq").innerHTML = inq.map((i) => `
      <tr>
        <td><strong>${i.inquiryId}</strong></td>
        <td>${i.referenceNo}</td><td>${i.subject}</td><td><span class="badge badge-muted">${i.status}</span></td>
        <td class="row">
          <button class="btn btn-outline btn-sm" onclick="pickReply(${i.inquiryId},'${i.referenceNo}')">Reply</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'START')">Start</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'ESCALATE')">Escalate</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'RESOLVE')">Resolve</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'REOPEN')">Reopen</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'CLOSE')">Close</button>
        </td>
      </tr>`).join("") || `<tr><td colspan="5">No inquiries</td></tr>`;

    await loadFloors();
    await loadStrategyKeys();
    await loadRateRules();
    await loadLoyaltyRules();
    await loadFbAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnOcc").onclick = refreshAll;

async function loadFloors() {
  const floors = await API.get("/api/admin/floors?facilityId=1");
  const opts = floors.map((f) =>
    `<option value="${f.floorId}">#${f.floorId} — ${f.floorLabel}</option>`
  ).join("");
  const createSel = qs("#newFloorId");
  if (createSel) createSel.innerHTML = opts;
  const moveSel = qs("#updSlotFloor");
  if (moveSel) {
    moveSel.innerHTML = `<option value="">— leave on current floor —</option>` + opts;
  }
}

qs("#btnAddFloor").onclick = async () => {
  try {
    const label = qs("#newFloorLabel").value.trim();
    if (!label) throw new Error("Floor label required");
    const f = await API.post("/api/admin/floors", { facilityId: 1, floorLabel: label });
    showMsg(qs("#msg"), "Created floor #" + f.floorId + " — " + f.floorLabel, true);
    qs("#newFloorLabel").value = "";
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadStrategyKeys() {
  const keys = await API.get("/api/payments/keys");
  const ruleSel = qs("#newRuleKey");
  if (ruleSel) {
    ruleSel.innerHTML = keys.map((k) =>
      `<option value="${k.keyCode}">${k.keyCode} — ${k.label}</option>`
    ).join("") || `<option value="">No keys yet</option>`;
  }
  const slotOpts = keys.filter((k) => k.scope === "SLOT").map((k) =>
    `<option value="${k.keyCode}">${k.keyCode} — ${k.label}</option>`
  ).join("");
  const newSlot = qs("#newSlotStrategyKey");
  if (newSlot) {
    newSlot.innerHTML = `<option value="">None</option>` + slotOpts;
  }
  const updSlot = qs("#updSlotStrategyKey");
  if (updSlot) {
    updSlot.innerHTML =
      `<option value="">— leave unchanged —</option><option value="NONE">Clear key</option>` + slotOpts;
  }
}

qs("#btnAddStrategyKey").onclick = async () => {
  try {
    await API.post("/api/payments/keys", {
      keyCode: qs("#newKeyCode").value,
      label: qs("#newKeyLabel").value,
      scope: "SLOT"
    });
    showMsg(qs("#msg"), "Strategy key created", true);
    qs("#newKeyCode").value = "";
    qs("#newKeyLabel").value = "";
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadRateRules() {
  const rules = await API.get("/api/payments/rules");
  qs("#rateRules").innerHTML = rules.map((r) => `
    <tr>
      <td>${r.ruleId}</td><td>${r.ruleName}</td><td>${r.strategyKey}</td><td>${r.multiplier}</td>
      <td>${r.active ? "Yes" : "No"}</td>
      <td class="row">
        <button class="btn btn-outline btn-sm" onclick="updRule(${r.ruleId},${r.multiplier})">Edit ×</button>
        <button class="btn btn-outline btn-sm" onclick="toggleRule(${r.ruleId},${!r.active})">${r.active ? "Disable" : "Enable"}</button>
        <button class="btn btn-outline btn-sm" onclick="delRule(${r.ruleId})">Delete</button>
      </td>
    </tr>`).join("") || `<tr><td colspan="6">No rules — run seed.sql</td></tr>`;
}

async function updRule(id, old) {
  const vals = await uiDialog({
    title: "Edit multiplier",
    message: "Rule #" + id,
    confirmLabel: "Save",
    fields: [{ name: "mult", label: "New multiplier", type: "number", value: old, min: 0, step: "0.01", required: true }]
  });
  if (!vals) return;
  try {
    await API.post("/api/payments/rules/update/" + id, { multiplier: Number(vals.mult) });
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function toggleRule(id, active) {
  await API.post("/api/payments/rules/update/" + id, { active });
  refreshAll();
}

async function delRule(id) {
  const ok = await uiDialog({
    title: "Delete rule",
    message: "Rule #" + id + " will be removed.",
    confirmLabel: "Delete",
    danger: true
  });
  if (!ok) return;
  try {
    await API.del("/api/payments/rules/" + id);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

const LOY_KEYS = [
  ["earn_lkr_per_point", "LKR spent per 1 point earned"],
  ["redeem_points_per_lkr", "Points per LKR discount"],
  ["min_redeem_points", "Minimum redeem"],
  ["tier_silver", "Silver tier at points"],
  ["tier_gold", "Gold tier at points"],
  ["tier_platinum", "Platinum tier at points"]
];

async function loadLoyaltyRules() {
  const rules = await API.get("/api/loyalty/rules");
  qs("#loyRules").innerHTML = LOY_KEYS.map(([k, label]) => `
    <div class="field">
      <label for="loy_${k}">${label}</label>
      <input id="loy_${k}" data-key="${k}" type="number" min="0" step="1" value="${rules[k] || ""}" />
    </div>`).join("");
  lockManagerNumbers();
}

qs("#btnSaveLoyRules").onclick = async () => {
  try {
    const body = {};
    LOY_KEYS.forEach(([k]) => {
      const n = Number(qs("#loy_" + k).value);
      if (Number.isNaN(n) || n < 0) throw new Error("Loyalty values cannot be negative");
      body[k] = String(n);
    });
    await API.post("/api/loyalty/rules", body);
    showMsg(qs("#msg"), "Loyalty rules saved", true);
    loadLoyaltyRules();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnAddRule").onclick = async () => {
  try {
    const key = qs("#newRuleKey").value;
    if (!key) throw new Error("Pick or create a strategy key first");
    const mult = Number(qs("#newRuleMult").value);
    if (Number.isNaN(mult) || mult < 0) throw new Error("Multiplier cannot be negative");
    await API.post("/api/payments/rules", {
      ruleName: qs("#newRuleName").value,
      strategyKey: key,
      multiplier: mult
    });
    showMsg(qs("#msg"), "Rule created", true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadFbAll() {
  const list = await API.get("/api/feedback/all");
  qs("#fbAll").innerHTML = list.map((f) => `
    <tr>
      <td>${f.feedbackId}</td><td>${f.customer}</td><td>${f.reservationId}</td>
      <td>${f.rating}★</td><td>${(f.comment || "").substring(0, 50)}</td>
      <td class="row">
        <button class="btn btn-outline btn-sm" onclick="mgrEditFb(${f.feedbackId},${f.rating})">Edit</button>
        <button class="btn btn-outline btn-sm" onclick="mgrDelFb(${f.feedbackId})">Delete</button>
      </td>
    </tr>`).join("") || `<tr><td colspan="6">No feedback</td></tr>`;
}

async function mgrEditFb(id, old) {
  const vals = await uiDialog({
    title: "Edit feedback",
    message: "Feedback #" + id,
    confirmLabel: "Save",
    fields: [
      { name: "rating", label: "Rating (1–5)", type: "number", value: old, min: 1, max: 5, step: "1", required: true },
      { name: "comment", label: "Comment", type: "textarea", value: "", required: false }
    ]
  });
  if (!vals) return;
  try {
    await API.post("/api/feedback/manager/update/" + id, { rating: Number(vals.rating), comment: vals.comment });
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function mgrDelFb(id) {
  const ok = await uiDialog({
    title: "Delete feedback",
    message: "Feedback #" + id + " will be removed.",
    confirmLabel: "Delete",
    danger: true
  });
  if (!ok) return;
  try {
    await API.del("/api/feedback/" + id);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnGenReport").onclick = async () => {
  try {
    lastReport = await API.get("/api/payments/revenue/report");
    const r = lastReport;
    qs("#revSummary").textContent =
      "Report generated " + (r.generatedAt || "").replace("T", " ").substring(0, 19);
    const panel = qs("#revMetrics");
    panel.style.display = "block";
    panel.innerHTML =
      `<div class="result-panel-title">Revenue summary</div>` +
      `<div class="result-stats">` +
      `<div class="result-stat accent"><span>Net revenue</span><strong>LKR ${r.netRevenueLkr}</strong></div>` +
      `<div class="result-stat"><span>Paid invoices</span><strong>${r.countPaid}</strong></div>` +
      `<div class="result-stat"><span>Refunded</span><strong>${r.countRefunded}</strong></div>` +
      `</div>` +
      `<div class="result-kv">` +
      `<div class="kv-row"><span>Gross paid</span><strong>LKR ${r.totalPaidLkr != null ? r.totalPaidLkr : "—"}</strong></div>` +
      `<div class="kv-row"><span>Total refunded</span><strong>LKR ${r.totalRefundedLkr != null ? r.totalRefundedLkr : "—"}</strong></div>` +
      `<div class="kv-row"><span>Line items</span><strong>${(r.rows || []).length}</strong></div>` +
      `</div>`;
    if (r.rows && r.rows.length) {
      qs("#rev").innerHTML = r.rows.map((p) => `
        <tr>
          <td>${p.invoiceNo || "—"}</td>
          <td>${p.reservationId}</td>
          <td>LKR ${p.finalAmount}</td>
          <td>${p.status}</td>
          <td></td>
        </tr>`).join("");
    }
    qs("#btnDownloadCsv").style.display = "inline-flex";
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnDownloadCsv").onclick = () => {
  if (!lastReport || !lastReport.rows) return;
  const lines = ["invoice,reservation,amount,status,paid_at"];
  lastReport.rows.forEach((p) => {
    lines.push([
      p.invoiceNo || "",
      p.reservationId,
      p.finalAmount,
      p.status,
      p.paidAt || ""
    ].join(","));
  });
  const blob = new Blob([lines.join("\n")], { type: "text/csv" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "parkED-revenue-report.csv";
  a.click();
};

async function refund(id) {
  try {
    await API.post("/api/payments/refund/" + id, {});
    showMsg(qs("#msg"), "Refunded #" + id, true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

function pickReply(id, ref) {
  qs("#replyId").value = ref || String(id);
  qs("#replyMsg").focus();
}

async function trans(id, action) {
  try {
    await API.post("/api/inquiries/transition/" + id, { action });
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnReply").onclick = async () => {
  try {
    const raw = qs("#replyId").value.trim();
    const body = { message: qs("#replyMsg").value };
    if (/^INQ-/i.test(raw)) {
      body.referenceNo = raw;
    } else {
      body.inquiryId = Number(raw);
    }
    await API.post("/api/inquiries/reply", body);
    showMsg(qs("#msg"), "Reply sent", true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnAddSlot").onclick = async () => {
  try {
    const baseRate = Number(qs("#newSlotRate").value);
    if (Number.isNaN(baseRate) || baseRate < 0) throw new Error("Base rate cannot be negative");
    const payload = {
      floorId: Number(qs("#newFloorId").value),
      slotCode: qs("#newSlotCode").value,
      slotType: qs("#newSlotType").value,
      baseRate
    };
    const sk = qs("#newSlotStrategyKey").value;
    if (sk) payload.rateStrategyKey = sk;
    const s = await API.post("/api/admin/slots", payload);
    showMsg(qs("#msg"), "Created slot #" + s.slotId, true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnUpdSlot").onclick = async () => {
  try {
    const id = qs("#updSlotId").value;
    if (id !== "" && Number(id) < 1) throw new Error("Slot ID must be 1 or higher");
    const body = { status: qs("#updSlotStatus").value };
    if (qs("#updSlotRate").value) {
      const rate = Number(qs("#updSlotRate").value);
      if (Number.isNaN(rate) || rate < 0) throw new Error("Rate cannot be negative");
      body.baseRate = rate;
    }
    const sk = qs("#updSlotStrategyKey").value;
    if (sk) body.rateStrategyKey = sk;
    const fl = qs("#updSlotFloor").value;
    if (fl) body.floorId = Number(fl);
    await API.post("/api/admin/slots/" + id + "/update", body);
    showMsg(qs("#msg"), "Slot #" + id + " updated", true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnDeleteSlot").onclick = async () => {
  try {
    const id = qs("#updSlotId").value;
    if (!id || Number(id) < 1) throw new Error("Enter a slot ID of 1 or higher");
    const ok = await uiDialog({
      title: "Delete slot",
      message: "Slot #" + id + " will be removed from the map.",
      confirmLabel: "Delete",
      danger: true
    });
    if (!ok) return;
    await API.post("/api/admin/slots/" + id + "/delete", {});
    showMsg(qs("#msg"), "Slot #" + id + " deleted", true);
    qs("#updSlotId").value = "";
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadAudit() {
  try {
    const rows = await API.get("/api/audit/?limit=40");
    qs("#auditBody").innerHTML = rows.map((a) => `
      <tr>
        <td>${a.createdAt}</td><td>${a.userId || "—"}</td>
        <td>${a.action}</td><td>${a.details || ""}</td>
      </tr>`).join("") || `<tr><td colspan="4">No audit rows</td></tr>`;
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}
qs("#btnAudit").onclick = loadAudit;
loadAudit();
