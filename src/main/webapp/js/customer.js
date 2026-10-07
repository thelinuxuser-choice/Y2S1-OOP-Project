let selectedSlot = null;
let me = null;
let liveMap = null;

requireAuth(["CUSTOMER"]).then((data) => {
  if (!data) return;
  me = data;
  qs("#who").textContent = data.user.fullName;
  const sel = qs("#vehicleId");
  sel.innerHTML = (data.vehicles || []).map(
    (v) => `<option value="${v.vehicleId}">${v.plateNumber} (${v.vehicleType})</option>`
  ).join("") || `<option value="">No vehicle – add via API later</option>`;

  syncBookingLimits(true);
  ["start", "end"].forEach((prefix) => {
    ["Date", "Hour", "Min", "Ampm"].forEach((part) => {
      qs("#" + prefix + part).addEventListener("change", () => syncBookingLimits(false));
    });
  });
  setInterval(() => syncBookingLimits(false), 30000);

  const mapRoot = qs("#sec-map");
  liveMap = new LiveMapUI(mapRoot, {
    facilityId: 1,
    pollMs: 8000,
    selectable: true,
    typeFilterEl: qs("#typeFilter"),
    onSelect: (slot) => {
      selectedSlot = slot;
      if (!slot) {
        qs("#picked").textContent = "No slot selected";
        return;
      }
      const keyPart = slot.rateStrategyKey ? ` · key ${slot.rateStrategyKey}` : "";
      qs("#picked").textContent =
        `Selected ${slot.slotCode} (${slot.slotType}) · LKR ${slot.baseRate}/h${keyPart}`;
    }
  });
  liveMap.start().catch((e) => showMsg(qs("#msg"), e.message, false));
  loadRateInfo();
  wireTabs();
});

function pad2(n) {
  return String(n).padStart(2, "0");
}

function dateOnly(d) {
  return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate());
}

function to24(hour12, ampm) {
  let h = Number(hour12);
  if (ampm === "AM") return h === 12 ? 0 : h;
  return h === 12 ? 12 : h + 12;
}

function partsFromDate(d) {
  const h24 = d.getHours();
  const ampm = h24 >= 12 ? "PM" : "AM";
  let h12 = h24 % 12;
  if (h12 === 0) h12 = 12;
  const min = Math.floor(d.getMinutes() / 5) * 5;
  return { date: dateOnly(d), hour: String(h12), min: pad2(min), ampm };
}

function stampFromParts(date, hour, min, ampm) {
  return date + "T" + pad2(to24(hour, ampm)) + ":" + min;
}

function roundUp5(d) {
  const x = new Date(d.getTime());
  x.setSeconds(0, 0);
  const rem = x.getMinutes() % 5;
  if (rem !== 0) x.setMinutes(x.getMinutes() + (5 - rem));
  return x;
}

const CLOCK_MINUTES = ["00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"];

function slotOk(date, hour12, min, ampm, earliest) {
  return new Date(stampFromParts(date, hour12, min, ampm)).getTime() >= earliest.getTime();
}

function readPrefer(prefix, fallback) {
  const dateEl = qs("#" + prefix + "Date");
  const hourEl = qs("#" + prefix + "Hour");
  if (!dateEl || !hourEl || !hourEl.options.length || !dateEl.value) return partsFromDate(fallback);
  return {
    date: dateEl.value,
    hour: hourEl.value,
    min: qs("#" + prefix + "Min").value,
    ampm: qs("#" + prefix + "Ampm").value
  };
}

/** Grey out past dates in the calendar and past hours/minutes/AM-PM in the time lists. */
function paintClock(prefix, earliest, prefer) {
  const dateEl = qs("#" + prefix + "Date");
  const hourEl = qs("#" + prefix + "Hour");
  const minEl = qs("#" + prefix + "Min");
  const apEl = qs("#" + prefix + "Ampm");
  const today = new Date();
  const far = new Date(today.getTime());
  far.setDate(far.getDate() + 60);
  dateEl.min = dateOnly(today);
  dateEl.max = dateOnly(far);

  let date = prefer.date;
  if (!date || date < dateEl.min) date = dateEl.min;
  if (date > dateEl.max) date = dateEl.max;
  dateEl.value = date;

  const periodOk = (ampm) => CLOCK_MINUTES.some((m) =>
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].some((h) => slotOk(date, h, m, ampm, earliest))
  );
  const ampm = periodOk(prefer.ampm) ? prefer.ampm : (periodOk("AM") ? "AM" : "PM");
  apEl.innerHTML =
    `<option value="AM"${periodOk("AM") ? "" : " disabled"}>AM</option>` +
    `<option value="PM"${periodOk("PM") ? "" : " disabled"}>PM</option>`;
  apEl.value = ampm;

  const hourOk = (h) => CLOCK_MINUTES.some((m) => slotOk(date, h, m, ampm, earliest));
  let hour = Number(prefer.hour);
  if (!hourOk(hour)) {
    hour = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].find((h) => hourOk(h)) || 12;
  }
  hourEl.innerHTML = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((h) =>
    `<option value="${h}"${hourOk(h) ? "" : " disabled"}>${pad2(h)}</option>`
  ).join("");
  hourEl.value = String(hour);

  const minOk = (m) => slotOk(date, Number(hourEl.value), m, ampm, earliest);
  let min = prefer.min;
  if (!minOk(min)) min = CLOCK_MINUTES.find((m) => minOk(m)) || "00";
  minEl.innerHTML = CLOCK_MINUTES.map((m) =>
    `<option value="${m}"${minOk(m) ? "" : " disabled"}>${m}</option>`
  ).join("");
  minEl.value = min;

  qs("#" + prefix + "Time").value = stampFromParts(dateEl.value, hourEl.value, minEl.value, apEl.value);
}

function syncBookingLimits(resetValues) {
  const startEarliest = roundUp5(new Date());
  const startPrefer = resetValues ? partsFromDate(startEarliest) : readPrefer("start", startEarliest);
  paintClock("start", startEarliest, startPrefer);

  const startAt = new Date(qs("#startTime").value);
  const endEarliest = new Date(startAt.getTime() + 5 * 60 * 1000);
  const endDefault = new Date(startAt.getTime() + 2 * 60 * 60 * 1000);
  let endPrefer = resetValues ? partsFromDate(endDefault) : readPrefer("end", endDefault);
  const chosenEnd = new Date(stampFromParts(endPrefer.date, endPrefer.hour, endPrefer.min, endPrefer.ampm));
  if (chosenEnd.getTime() < endEarliest.getTime()) endPrefer = partsFromDate(endDefault);
  paintClock("end", endEarliest, endPrefer);
}

function wireTabs() {
  document.querySelectorAll("[data-tab]").forEach((a) => {
    a.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelectorAll("[data-tab]").forEach((x) => x.classList.remove("active"));
      a.classList.add("active");
      ["map", "bookings", "pay", "loyalty", "feedback", "inq"].forEach((id) => {
        qs("#sec-" + id).hidden = id !== a.dataset.tab;
      });
      if (a.dataset.tab === "bookings") loadBookings();
      if (a.dataset.tab === "loyalty") loadLoyalty();
      if (a.dataset.tab === "pay") loadPayments();
      if (a.dataset.tab === "feedback") loadFbMine().catch((e) => showMsg(qs("#msg"), e.message, false));
      if (a.dataset.tab === "inq") loadInq();
      if (a.dataset.tab === "map" && liveMap) liveMap.load().catch(() => {});
    });
  });
}

function loadMap() {
  if (!liveMap) return Promise.resolve();
  return liveMap.load();
}

async function loadRateInfo() {
  try {
    const r = await API.get("/api/payments/rates");
    const rules = (r.rules || []).filter((x) => x.active);
    const ruleHtml = rules.length
      ? `<ul class="result-lines">${rules.map((x) =>
          `<li><span>${x.ruleName} <span class="muted">(${x.strategyKey})</span></span><strong>×${x.multiplier}</strong></li>`
        ).join("")}</ul>`
      : `<div class="result-empty">Using default rate rules</div>`;
    qs("#rateInfoBox").innerHTML =
      `<div class="result-panel-title" style="margin-bottom:0.55rem">Current rates</div>` +
      `<div class="result-kv" style="margin-bottom:0.75rem">` +
      `<div class="kv-row"><span>Peak windows</span><strong>${(r.peakWindows || []).join(" · ") || "—"}</strong></div>` +
      `<div class="kv-row"><span>Loyalty</span><strong>${r.loyaltyRedeem || "—"}</strong></div>` +
      `</div>` +
      ruleHtml;
  } catch (e) {
    qs("#rateInfoBox").innerHTML = `<div class="result-empty">Could not load rate info.</div>`;
  }
}

function formatQuote(q) {
  if (!q) return `<div class="result-empty">No quote</div>`;
  const lines = (q.breakdown || [])
    .map((line) => `<li><span>${escHtml(line)}</span></li>`)
    .join("");
  return (
    `<div class="result-panel-title">Quote breakdown</div>` +
    `<div class="result-stats">` +
    `<div class="result-stat"><span>Base</span><strong>LKR ${q.baseAmount}</strong></div>` +
    `<div class="result-stat"><span>Discount</span><strong>LKR ${q.discountAmount}</strong></div>` +
    `<div class="result-stat accent"><span>Pay now</span><strong>LKR ${q.finalAmount}</strong></div>` +
    `</div>` +
    (lines
      ? `<ul class="result-lines">${lines}</ul>`
      : `<div class="result-empty">No strategy lines</div>`)
  );
}

function formatPaymentReceipt(p) {
  if (!p) return `<div class="result-empty">No payment</div>`;
  const strat = (p.rateStrategy || "")
    .split(" | ")
    .filter(Boolean)
    .map((line) => `<li><span>${escHtml(line)}</span></li>`)
    .join("");
  return (
    `<div class="result-panel-title">Payment confirmed</div>` +
    `<div class="result-stats">` +
    `<div class="result-stat"><span>Invoice</span><strong>${escHtml(p.invoiceNo || "—")}</strong></div>` +
    `<div class="result-stat"><span>Status</span><strong>${escHtml(p.status || "PAID")}</strong></div>` +
    `<div class="result-stat accent"><span>Paid</span><strong>LKR ${p.finalAmount}</strong></div>` +
    `</div>` +
    `<div class="result-kv" style="margin-bottom:0.85rem">` +
    `<div class="kv-row"><span>Reservation</span><strong>#${p.reservationId}</strong></div>` +
    `<div class="kv-row"><span>Base</span><strong>LKR ${p.baseAmount}</strong></div>` +
    `<div class="kv-row"><span>Discount</span><strong>LKR ${p.discountAmount}</strong></div>` +
    `</div>` +
    (strat ? `<ul class="result-lines">${strat}</ul>` : "")
  );
}

qs("#btnBook").onclick = async () => {
  try {
    if (!selectedSlot) throw new Error("Pick an available slot first");
    const start = qs("#startTime").value;
    const end = qs("#endTime").value;
    const r = await API.post("/api/reservations/create", {
      slotId: selectedSlot.slotId,
      vehicleId: qs("#vehicleId").value ? Number(qs("#vehicleId").value) : null,
      startTime: start.length === 16 ? start + ":00" : start,
      endTime: end.length === 16 ? end + ":00" : end
    });
    showMsg(qs("#msg"), "Booked #" + r.reservationId + " token " + r.confirmationToken, true);
    qs("#payResId").value = r.reservationId;
    selectedSlot = null;
    qs("#picked").textContent = "No slot selected";
    await loadMap();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadBookings() {
  const list = await API.get("/api/reservations/history");
  qs("#bookBody").innerHTML = list.map((r) => `
    <tr>
      <td>${r.reservationId}</td>
      <td>${r.slotId}</td>
      <td>${fmt(r.startTime)}</td>
      <td>${fmt(r.endTime)}</td>
      <td>${r.status}</td>
      <td>${r.confirmationToken || ""}</td>
      <td class="row">${["PENDING","ACTIVE"].includes(r.status)
        ? `<button class="btn btn-outline btn-sm" onclick="cancelRes(${r.reservationId})">Cancel</button>
           <button class="btn btn-outline btn-sm" onclick="rescheduleRes(${r.reservationId})">Reschedule</button>` : ""}</td>
    </tr>`).join("") || `<tr><td colspan="7">No bookings yet</td></tr>`;
}

async function cancelRes(id) {
  try {
    await API.post("/api/reservations/cancel/" + id, {});
    showMsg(qs("#msg"), "Removed booking #" + id, true);
    loadBookings();
    loadMap();
    loadPayments();
    loadLoyalty();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function rescheduleRes(id) {
  const vals = await uiDialog({
    title: "Reschedule booking",
    message: "Booking #" + id,
    confirmLabel: "Save",
    fields: [
      { name: "start", label: "New start", type: "datetime-local", value: qs("#startTime").value, required: true },
      { name: "end", label: "New end", type: "datetime-local", value: qs("#endTime").value, required: true }
    ]
  });
  if (!vals) return;
  const start = vals.start;
  const end = vals.end;
  try {
    await API.post("/api/reservations/reschedule/" + id, {
      startTime: start.length === 16 ? start + ":00" : start,
      endTime: end.length === 16 ? end + ":00" : end
    });
    showMsg(qs("#msg"), "Rescheduled #" + id, true);
    loadBookings();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function loadPayments() {
  const list = await API.get("/api/payments/mine");
  const badge = (s) => {
    if (s === "REFUNDED") return `<span class="badge" style="background:#fecaca">REFUNDED</span>`;
    if (s === "PAID") return `<span class="badge badge-ok">PAID</span>`;
    return `<span class="badge">${s}</span>`;
  };
  qs("#payHist").innerHTML = list.map((p) => `
    <tr>
      <td>${p.invoiceNo || "—"}</td>
      <td>${p.reservationId}</td>
      <td>LKR ${p.finalAmount}</td>
      <td>${badge(p.status)}</td>
      <td class="text-sm text-muted">${((p.rateStrategy || "").length > 45 ? (p.rateStrategy || "").substring(0, 45) + "…" : (p.rateStrategy || ""))}</td>
    </tr>`).join("") || `<tr><td colspan="5">No payments yet</td></tr>`;
}

qs("#btnQuote").onclick = async () => {
  try {
    const id = qs("#payResId").value;
    const pts = qs("#payPts").value || 0;
    const q = await API.get("/api/payments/quote/" + id + "?points=" + pts);
    qs("#quoteBox").innerHTML = formatQuote(q);
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnPay").onclick = async () => {
  try {
    const p = await API.post("/api/payments/checkout", {
      reservationId: Number(qs("#payResId").value),
      pointsToRedeem: Number(qs("#payPts").value || 0)
    });
    showMsg(qs("#msg"), "Paid " + p.invoiceNo + " LKR " + p.finalAmount, true);
    qs("#quoteBox").innerHTML = formatPaymentReceipt(p);
    loadPayments();
    loadLoyalty();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadLoyalty() {
  const data = await API.get("/api/loyalty/dashboard");
  const a = data.account;
  const rules = data.rules || {};
  const ruleLine =
    `Earn: LKR ${rules.earn_lkr_per_point || "50"}/pt · Redeem: ${rules.redeem_points_per_lkr || "10"} pts/LKR · Min ${rules.min_redeem_points || "20"} pts`;
  qs("#loyBox").innerHTML = (a
    ? `<p>Tier <span class="badge badge-ok">${a.tier}</span> · <strong>${a.points}</strong> points</p>`
    : `<p class="text-muted">Not enrolled yet</p>`) +
    `<p class="text-sm text-muted">${ruleLine}</p>`;
  qs("#loyHist").innerHTML = (data.history || []).map((h) => {
    const ref = h.reference || "";
    const isRefund = ref.indexOf("REFUND") >= 0 || h.type === "ADJUST";
    return `<tr><td>${fmt(h.createdAt)}</td><td>${h.type}${isRefund ? " ↩" : ""}</td><td>${h.points}</td><td>${ref}</td></tr>`;
  }).join("") || `<tr><td colspan="4">No history</td></tr>`;

  const pays = await API.get("/api/payments/mine");
  qs("#loyPayStatus").innerHTML = pays.map((p) => `
    <tr>
      <td>${p.invoiceNo || "—"}</td>
      <td>${p.reservationId}</td>
      <td>${p.finalAmount}</td>
      <td>${p.status === "REFUNDED"
        ? "<span class=\"badge\" style=\"background:#fecaca\">REFUNDED · points reversed</span>"
        : p.status}</td>
    </tr>`).join("") || `<tr><td colspan="4">No payments</td></tr>`;
}

qs("#btnEnroll").onclick = async () => {
  try {
    const acc = await API.post("/api/loyalty/enroll", {});
    showMsg(qs("#msg"), "Enrolled — tier " + (acc.tier || "BRONZE"), true);
    loadLoyalty();
  } catch (e) {
    showMsg(qs("#msg"), e.message || "Enroll failed — run database/patch_usecase_gaps.sql if loyalty_config is missing", false);
  }
};

qs("#btnUnenroll").onclick = async () => {
  const ok = await uiDialog({
    title: "Leave loyalty",
    message: "You will leave the loyalty program. Points on this account will no longer be available.",
    confirmLabel: "Leave",
    danger: true
  });
  if (!ok) return;
  try {
    await API.post("/api/loyalty/unenroll", {});
    loadLoyalty();
    showMsg(qs("#msg"), "Unenrolled", true);
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnFb").onclick = async () => {
  try {
    await API.post("/api/feedback", {
      reservationId: Number(qs("#fbRes").value),
      rating: Number(qs("#fbRating").value),
      type: qs("#fbType").value,
      comment: qs("#fbComment").value
    });
    showMsg(qs("#msg"), "Thanks for the feedback", true);
    loadFbMine();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

function escHtml(s) {
  return String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/"/g, "&quot;");
}

window.__fbComments = {};

async function loadFbMine() {
  const list = await API.get("/api/feedback/mine");
  qs("#fbMine").innerHTML = list.map((f) => {
    window.__fbComments[f.feedbackId] = f.comment || "";
    const cur = `${f.rating}★ · ${escHtml((f.comment || "").substring(0, 80))}${(f.comment || "").length > 80 ? "…" : ""}`;
    const orig = f.edited
      ? `${f.originalRating}★ · ${escHtml((f.originalComment || "").substring(0, 80))}${(f.originalComment || "").length > 80 ? "…" : ""}`
      : "—";
    const status = f.edited
      ? `<span class="badge">Edited ${fmt(f.editedAt)}</span>`
      : `<span class="badge badge-ok">Original</span>`;
    return `
    <tr>
      <td>${f.feedbackId}</td>
      <td>${f.reservationId}</td>
      <td class="text-sm">${cur}</td>
      <td class="text-sm text-muted">${orig}</td>
      <td>${status}</td>
      <td class="row">
        <button class="btn btn-outline btn-sm" onclick="editFb(${f.feedbackId},${f.rating})">Edit</button>
        <button class="btn btn-outline btn-sm" onclick="delFb(${f.feedbackId})">Delete</button>
      </td>
    </tr>`;
  }).join("") || `<tr><td colspan="6">No reviews yet — complete a visit, then submit feedback above.</td></tr>`;
}

async function editFb(id, oldRating) {
  const vals = await uiDialog({
    title: "Edit review",
    confirmLabel: "Save",
    fields: [
      { name: "rating", label: "Rating (1–5)", type: "number", value: oldRating, min: 1, max: 5, step: "1", required: true },
      { name: "comment", label: "Comment (optional)", type: "textarea", value: window.__fbComments[id] || "", required: false }
    ]
  });
  if (!vals) return;
  const rating = vals.rating;
  const comment = vals.comment;
  try {
    await API.post("/api/feedback/update/" + id, { rating: Number(rating), comment });
    showMsg(qs("#msg"), "Review updated (original kept on file)", true);
    loadFbMine();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function delFb(id) {
  const ok = await uiDialog({
    title: "Delete review",
    message: "This feedback will be removed.",
    confirmLabel: "Delete",
    danger: true
  });
  if (!ok) return;
  try {
    await API.del("/api/feedback/" + id);
    loadFbMine();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

let inqSelectedId = null;

async function loadInq() {
  const list = await API.get("/api/inquiries/mine");
  qs("#inqBody").innerHTML = list.map((i) => `
    <tr class="inq-row${inqSelectedId === i.inquiryId ? " inq-row-active" : ""}" data-id="${i.inquiryId}">
      <td><strong>${i.referenceNo}</strong></td>
      <td>${escHtml(i.subject)}</td>
      <td><span class="badge badge-muted">${i.category || ""}</span></td>
      <td><span class="badge">${i.status}</span></td>
      <td><button class="btn btn-outline btn-sm" type="button" onclick="openInqThread(${i.inquiryId})">View conversation</button></td>
    </tr>`).join("") || `<tr><td colspan="5">No tickets yet — open one below.</td></tr>`;

  if (inqSelectedId && list.some((x) => x.inquiryId === inqSelectedId)) {
    openInqThread(inqSelectedId);
  } else if (list.length === 1) {
    openInqThread(list[0].inquiryId);
  }
}

async function openInqThread(id) {
  try {
    inqSelectedId = id;
    const data = await API.get("/api/inquiries/" + id);
    const inq = data.inquiry;
    const replies = data.replies || [];
    qs("#inqThreadTitle").textContent = inq.referenceNo + " — " + inq.subject;
    qs("#inqThreadHint").hidden = true;

    const opening = `
      <div class="inq-msg you">
        <span class="inq-msg-meta">You · ${fmt(inq.createdAt)}</span>
        ${escHtml(inq.description || "(no description)")}
      </div>`;
    const staff = replies.map((r) => `
      <div class="inq-msg staff">
        <span class="inq-msg-meta">${escHtml(r.staff || "Support")} · ${fmt(r.createdAt)}</span>
        ${escHtml(r.message)}
      </div>`).join("");
    qs("#inqThread").innerHTML = opening + (staff || `
      <p class="text-muted text-sm">No staff replies yet — we will notify you when someone responds.</p>`);

    document.querySelectorAll(".inq-row").forEach((row) => {
      row.classList.toggle("inq-row-active", Number(row.dataset.id) === id);
    });
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnInq").onclick = async () => {
  try {
    const i = await API.post("/api/inquiries/create", {
      category: qs("#inqCat").value,
      subject: qs("#inqSub").value,
      description: qs("#inqDesc").value
    });
    showMsg(qs("#msg"), "Opened " + i.referenceNo, true);
    qs("#inqSub").value = "";
    qs("#inqDesc").value = "";
    inqSelectedId = i.inquiryId;
    await loadInq();
    openInqThread(i.inquiryId);
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};
