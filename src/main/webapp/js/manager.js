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
    qs("#fb").innerHTML = fb.map((f) =>
      `<div>${f.customer} · ${f.rating}★ · ${f.comment || "(no comment)"}</div>`
    ).join("") || "No feedback yet";

    const rev = await API.get("/api/payments/revenue");
    qs("#rev").innerHTML = rev.map((p) => `
      <tr>
        <td>${p.invoiceNo}</td><td>${p.reservationId}</td>
        <td>LKR ${p.finalAmount}</td><td><span class="badge">${p.status}</span></td>
        <td>${p.status === "PAID"
          ? `<button class="btn btn-outline btn-sm" onclick="refund(${p.paymentId})">Refund</button>` : ""}</td>
      </tr>`).join("") || `<tr><td colspan="5">No payments</td></tr>`;

    const inq = await API.get("/api/inquiries/");
    qs("#inq").innerHTML = inq.map((i) => `
      <tr>
        <td>${i.referenceNo}</td><td>${i.subject}</td><td><span class="badge badge-muted">${i.status}</span></td>
        <td class="row">
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'START')">Start</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'ESCALATE')">Escalate</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'RESOLVE')">Resolve</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'REOPEN')">Reopen</button>
          <button class="btn btn-outline btn-sm" onclick="trans(${i.inquiryId},'CLOSE')">Close</button>
        </td>
      </tr>`).join("") || `<tr><td colspan="4">No inquiries</td></tr>`;
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
}

qs("#btnOcc").onclick = refreshAll;

async function refund(id) {
  try {
    await API.post("/api/payments/refund/" + id, {});
    showMsg(qs("#msg"), "Refunded #" + id, true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
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
    await API.post("/api/inquiries/reply/" + qs("#replyId").value, {
      message: qs("#replyMsg").value
    });
    showMsg(qs("#msg"), "Reply sent", true);
    refreshAll();
  } catch (e) {
    showMsg(qs("#msg"), e.message, false);
  }
};

qs("#btnAddSlot").onclick = async () => {
  try {
    const s = await API.post("/api/admin/slots", {
      floorId: Number(qs("#newFloorId").value),
      slotCode: qs("#newSlotCode").value,
      slotType: qs("#newSlotType").value,
      baseRate: Number(qs("#newSlotRate").value)
    });
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
