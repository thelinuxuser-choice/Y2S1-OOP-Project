let lastReport = null;

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
  const mult = prompt("New multiplier", old);
  if (!mult) return;
  await API.post("/api/payments/rules/update/" + id, { multiplier: Number(mult) });
  refreshAll();
}

async function toggleRule(id, active) {
  await API.post("/api/payments/rules/update/" + id, { active });
  refreshAll();
}

async function delRule(id) {
  if (!confirm("Delete rule #" + id + "?")) return;
  await API.del("/api/payments/rules/" + id);
  refreshAll();
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
      <input id="loy_${k}" data-key="${k}" value="${rules[k] || ""}" />
    </div>`).join("");
}

qs("#btnSaveLoyRules").onclick = async () => {
  try {
    const body = {};
    LOY_KEYS.forEach(([k]) => {
      body[k] = qs("#loy_" + k).value;
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
    await API.post("/api/payments/rules", {
      ruleName: qs("#newRuleName").value,
      strategyKey: key,
      multiplier: Number(qs("#newRuleMult").value)
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
  const rating = prompt("Rating 1–5", old);
  const comment = prompt("Comment", "");
  if (!rating) return;
  await API.post("/api/feedback/manager/update/" + id, { rating: Number(rating), comment });
  refreshAll();
}

async function mgrDelFb(id) {
  if (!confirm("Delete feedback #" + id + "?")) return;
  await API.del("/api/feedback/" + id);
  refreshAll();
}

qs("#btnGenReport").onclick = async () => {
  try {
    lastReport = await API.get("/api/payments/revenue/report");
    const r = lastReport;
    qs("#revSummary").textContent =
      `Net LKR ${r.netRevenueLkr} · Paid ${r.countPaid} · Refunded ${r.countRefunded} · Generated ${r.generatedAt}`;
    qs("#revReport").style.display = "block";
    qs("#revReport").textContent = JSON.stringify(r, null, 2);
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
    const payload = {
      floorId: Number(qs("#newFloorId").value),
      slotCode: qs("#newSlotCode").value,
      slotType: qs("#newSlotType").value,
      baseRate: Number(qs("#newSlotRate").value)
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
    const body = { status: qs("#updSlotStatus").value };
    if (qs("#updSlotRate").value) body.baseRate = Number(qs("#updSlotRate").value);
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

qs("#btnDeactSlot").onclick = async () => {
  try {
    const id = qs("#updSlotId").value;
    await API.post("/api/admin/slots/" + id + "/deactivate", {});
    showMsg(qs("#msg"), "Slot #" + id + " set to maintenance", true);
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
