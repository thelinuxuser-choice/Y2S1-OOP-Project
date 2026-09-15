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
      qs("#picked").textContent =
        `Selected ${slot.slotCode} (${slot.slotType}) · LKR ${slot.baseRate}/h`;
    }
  });
  liveMap.start().catch((e) => showMsg(qs("#msg"), e.message, false));
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
      if (a.dataset.tab === "inq") loadInq();
      if (a.dataset.tab === "map" && liveMap) liveMap.load().catch(() => {});
    });
  });
}

function loadMap() {
  if (!liveMap) return Promise.resolve();
  return liveMap.load();
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
      <td>${["PENDING","ACTIVE"].includes(r.status)
        ? `<button class="btn btn-outline btn-sm" onclick="cancelRes(${r.reservationId})">Cancel</button>` : ""}</td>
    </tr>`).join("") || `<tr><td colspan="7">No bookings yet</td></tr>`;
}

async function cancelRes(id) {
  try {
    await API.post("/api/reservations/cancel/" + id, {});
    showMsg(qs("#msg"), "Cancelled #" + id, true);
    loadBookings();
    loadMap();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnQuote").onclick = async () => {
  try {
    const id = qs("#payResId").value;
    const pts = qs("#payPts").value || 0;
    const q = await API.get("/api/payments/quote/" + id + "?points=" + pts);
    qs("#quoteBox").textContent = JSON.stringify(q, null, 2);
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
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadLoyalty() {
  const data = await API.get("/api/loyalty/dashboard");
  const a = data.account;
  qs("#loyBox").innerHTML = a
    ? `<p>Tier <span class="badge badge-ok">${a.tier}</span> · <strong>${a.points}</strong> points</p>`
    : `<p class="text-muted">Not enrolled yet</p>`;
  qs("#loyHist").innerHTML = (data.history || []).map((h) =>
    `<tr><td>${fmt(h.createdAt)}</td><td>${h.type}</td><td>${h.points}</td><td>${h.reference || ""}</td></tr>`
  ).join("");
}

qs("#btnEnroll").onclick = async () => {
  try {
    await API.post("/api/loyalty/enroll", {});
    loadLoyalty();
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
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

async function loadInq() {
  const list = await API.get("/api/inquiries/mine");
  qs("#inqBody").innerHTML = list.map((i) =>
    `<tr><td>${i.referenceNo}</td><td>${i.subject}</td><td>${i.status}</td></tr>`
  ).join("") || `<tr><td colspan="3">No tickets</td></tr>`;
}

qs("#btnInq").onclick = async () => {
  try {
    const i = await API.post("/api/inquiries/create", {
      category: qs("#inqCat").value,
      subject: qs("#inqSub").value,
      description: qs("#inqDesc").value
    });
    showMsg(qs("#msg"), "Opened " + i.referenceNo, true);
    loadInq();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};
