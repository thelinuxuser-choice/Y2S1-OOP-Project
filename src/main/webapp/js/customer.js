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

  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  qs("#startTime").value = now.toISOString().slice(0, 16);
  const end = new Date(now.getTime() + 2 * 3600 * 1000);
  qs("#endTime").value = end.toISOString().slice(0, 16);

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
    const rules = (r.rules || []).filter((x) => x.active).map(
      (x) => `${x.ruleName}: ×${x.multiplier} (${x.strategyKey})`
    ).join(" · ");
    qs("#rateInfoBox").innerHTML =
      `<strong>Peak windows:</strong> ${(r.peakWindows || []).join(", ")}<br/>` +
      `<strong>Active rules:</strong> ${rules || "defaults"}<br/>` +
      `<strong>Loyalty:</strong> ${r.loyaltyRedeem || ""}`;
  } catch (e) {
    qs("#rateInfoBox").textContent = "Could not load rate info.";
  }
}

function formatQuote(q) {
  if (!q) return "";
  const lines = (q.breakdown || []).join("\n");
  return (
    `Base LKR ${q.baseAmount}\n` +
    `Discount LKR ${q.discountAmount}\n` +
    `Pay LKR ${q.finalAmount}\n\n` +
    lines
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
    showMsg(qs("#msg"), "Cancelled #" + id + " (refund if paid)", true);
    loadBookings();
    loadMap();
    loadPayments();
    loadLoyalty();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function rescheduleRes(id) {
  const start = prompt("New start (YYYY-MM-DDTHH:mm)", qs("#startTime").value);
  const end = prompt("New end (YYYY-MM-DDTHH:mm)", qs("#endTime").value);
  if (!start || !end) return;
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
    qs("#quoteBox").textContent = formatQuote(q);
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
    qs("#quoteBox").textContent = JSON.stringify(p, null, 2);
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
  if (!confirm("Leave loyalty program?")) return;
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
  const rating = prompt("Rating 1–5", oldRating);
  if (!rating) return;
  const comment = prompt("Comment (optional)", window.__fbComments[id] || "");
  try {
    await API.post("/api/feedback/update/" + id, { rating: Number(rating), comment });
    showMsg(qs("#msg"), "Review updated (original kept on file)", true);
    loadFbMine();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

async function delFb(id) {
  if (!confirm("Delete this feedback?")) return;
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
