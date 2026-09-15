/* Shared Slot Availability Map — fetches /api/map/slots and keeps chrome in sync */
(function (global) {
  var STATUS_UI = {
    AVAILABLE: { key: "open", cls: "open", label: "OPEN", icon: "✓" },
    RESERVED: { key: "reserved", cls: "reserved", label: "RESERVED", icon: "🔒" },
    OCCUPIED: { key: "occupied", cls: "occupied", label: "OCCUPIED", icon: "🚗" },
    MAINTENANCE: { key: "maint", cls: "maint", label: "MAINTENANCE", icon: "–" }
  };

  function pad(n) {
    return n < 10 ? "0" + n : "" + n;
  }

  function apiGet(path) {
    if (global.API && typeof global.API.get === "function") {
      return global.API.get(path);
    }
    var rel = String(path).replace(/^\//, "");
    var url = new URL(rel, global.location.href).pathname;
    return fetch(url, { credentials: "same-origin" }).then(function (res) {
      return res.json().then(function (json) {
        if (!json.ok) throw new Error(json.error || "Map load failed");
        return json.data;
      });
    });
  }

  function LiveMapUI(root, opts) {
    this.root = root;
    this.opts = opts || {};
    this.filter = "all";
    this.slots = [];
    this.floors = [];
    this.selectedId = null;
    this._poll = null;
    this._bound = false;
  }

  LiveMapUI.prototype.qs = function (sel) {
    return this.root.querySelector(sel);
  };

  LiveMapUI.prototype.tickClock = function () {
    var d = new Date();
    var h = d.getHours();
    var am = h >= 12 ? "PM" : "AM";
    var hr = h % 12;
    if (hr === 0) hr = 12;
    var clock = this.qs("#samClock") || document.getElementById("samClock");
    var dateEl = this.qs("#samDate") || document.getElementById("samDate");
    if (clock) {
      clock.textContent =
        pad(hr) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds()) + " " + am;
    }
    if (dateEl) {
      dateEl.textContent = d.toLocaleDateString("en-GB", {
        weekday: "short",
        day: "2-digit",
        month: "short",
        year: "numeric"
      });
    }
  };

  LiveMapUI.prototype.setText = function (id, text) {
    var el = this.qs("#" + id) || document.getElementById(id);
    if (el) el.textContent = text;
  };

  LiveMapUI.prototype.recount = function () {
    var open = 0,
      res = 0,
      occ = 0,
      maint = 0;
    var byFloor = {};

    this.slots.forEach(function (s) {
      var st = s.status;
      if (st === "AVAILABLE") open++;
      else if (st === "RESERVED") res++;
      else if (st === "OCCUPIED") occ++;
      else maint++;
      var fid = String(s.floorId);
      if (!byFloor[fid]) byFloor[fid] = { free: 0, total: 0, label: s.floorLabel };
      byFloor[fid].total++;
      if (st === "AVAILABLE") byFloor[fid].free++;
    });

    this.setText("cOpen", String(open));
    this.setText("cRes", String(res));
    this.setText("cOcc", String(occ));
    this.setText("cMaint", String(maint));

    var total = this.slots.length || 1;
    var pct = Math.round((open / total) * 100);
    this.setText("samFreePct", pct + "% free");
    var bar = this.qs("#samFreeBar") || document.getElementById("samFreeBar");
    if (bar) {
      bar.style.width = pct + "%";
      bar.setAttribute("aria-valuenow", String(pct));
    }

    Object.keys(byFloor).forEach(function (fid) {
      var pill = document.getElementById("floorFree-" + fid);
      if (pill) {
        pill.textContent = byFloor[fid].free + "/" + byFloor[fid].total + " free";
      }
    });

    var sub = this.qs(".sam-sub") || this.root.querySelector(".sam-sub");
    if (sub && this.floors.length) {
      sub.textContent =
        "Real-time bay status · " +
        this.floors.map(function (f) {
          return f.floorLabel;
        }).join(" & ");
    }
  };

  LiveMapUI.prototype.applyFilter = function (status) {
    this.filter = status || "all";
    var self = this;
    this.root.querySelectorAll(".sam-slot").forEach(function (el) {
      var show = self.filter === "all" || el.getAttribute("data-status") === self.filter;
      el.classList.toggle("is-hidden", !show);
    });
    this.root.querySelectorAll(".sam-filter").forEach(function (b) {
      b.classList.toggle("active", b.getAttribute("data-filter") === self.filter);
    });
    this.root.querySelectorAll(".sam-count").forEach(function (b) {
      var f = b.getAttribute("data-filter");
      b.classList.toggle("is-lime", f === "open" && (self.filter === "all" || self.filter === "open"));
    });
  };

  LiveMapUI.prototype.render = function () {
    var floorsEl = this.qs("#samFloors") || this.qs("#mapRoot");
    if (!floorsEl) return;

    var byFloor = {};
    var order = [];
    (this.floors || []).forEach(function (f) {
      var id = String(f.floorId);
      byFloor[id] = { label: f.floorLabel, slots: [], floorId: f.floorId };
      order.push(id);
    });
    this.slots.forEach(function (s) {
      var id = String(s.floorId);
      if (!byFloor[id]) {
        byFloor[id] = { label: s.floorLabel || ("Floor " + id), slots: [], floorId: s.floorId };
        order.push(id);
      }
      byFloor[id].slots.push(s);
    });

    var self = this;
    var selectable = !!this.opts.selectable;
    var html = order
      .map(function (fid, idx) {
        var floor = byFloor[fid];
        var dark = idx % 2 === 1;
        var cards = floor.slots
          .map(function (s) {
            var ui = STATUS_UI[s.status] || STATUS_UI.AVAILABLE;
            var sel =
              selectable && self.selectedId === s.slotId ? " selected" : "";
            var clickable =
              selectable && s.status === "AVAILABLE" ? " is-pickable" : "";
            var meta =
              s.slotType && s.status === "AVAILABLE"
                ? "<small>" + s.slotType + " · LKR " + s.baseRate + "/h</small>"
                : s.slotType
                  ? "<small>" + s.slotType + "</small>"
                  : "";
            return (
              '<article class="sam-slot ' +
              ui.cls +
              sel +
              clickable +
              '" data-status="' +
              ui.key +
              '" data-slot-id="' +
              s.slotId +
              '" data-floor="' +
              fid +
              '">' +
              '<span class="sam-ico">' +
              ui.icon +
              "</span>" +
              "<strong>" +
              s.slotCode +
              "</strong>" +
              "<em>" +
              ui.label +
              "</em>" +
              meta +
              "</article>"
            );
          })
          .join("");
        return (
          '<div class="lp-avail-floor' +
          (dark ? " dark-floor" : "") +
          '">' +
          '<div class="sam-floor-head">' +
          '<h3 class="lp-floor-label">' +
          floor.label +
          "</h3>" +
          '<span class="sam-floor-pill' +
          (dark ? " dark" : "") +
          '" id="floorFree-' +
          fid +
          '">0/0 free</span>' +
          "</div>" +
          '<div class="sam-grid" data-floor="' +
          fid +
          '">' +
          cards +
          "</div></div>"
        );
      })
      .join("");

    floorsEl.innerHTML = html || '<p class="text-muted text-sm">No slots for this facility.</p>';

    if (selectable) {
      floorsEl.querySelectorAll(".sam-slot.is-pickable").forEach(function (el) {
        el.addEventListener("click", function () {
          var id = Number(el.getAttribute("data-slot-id"));
          var slot = self.slots.find(function (s) {
            return s.slotId === id;
          });
          if (!slot) return;
          self.selectedId = id;
          floorsEl.querySelectorAll(".sam-slot").forEach(function (x) {
            x.classList.toggle("selected", Number(x.getAttribute("data-slot-id")) === id);
          });
          if (typeof self.opts.onSelect === "function") self.opts.onSelect(slot);
        });
      });
    }

    this.recount();
    this.applyFilter(this.filter);
  };

  LiveMapUI.prototype.load = function () {
    var self = this;
    var facilityId = this.opts.facilityId || 1;
    var type = "ALL";
    if (this.opts.typeFilterEl) {
      type = this.opts.typeFilterEl.value || "ALL";
    }
    var q =
      "/api/map/slots?facilityId=" +
      encodeURIComponent(facilityId) +
      "&type=" +
      encodeURIComponent(type);

    return apiGet(q)
      .then(function (data) {
        self.floors = data.floors || [];
        self.slots = data.slots || [];
        if (
          self.selectedId &&
          !self.slots.some(function (s) {
            return s.slotId === self.selectedId && s.status === "AVAILABLE";
          })
        ) {
          self.selectedId = null;
          if (typeof self.opts.onSelect === "function") self.opts.onSelect(null);
        }
        self.render();
        self.root.classList.remove("sam-error");
        return data;
      })
      .catch(function (err) {
        self.root.classList.add("sam-error");
        var floorsEl = self.qs("#samFloors") || self.qs("#mapRoot");
        if (floorsEl) {
          floorsEl.innerHTML =
            '<p class="text-muted text-sm">Could not load live map: ' +
            (err.message || "error") +
            "</p>";
        }
        throw err;
      });
  };

  LiveMapUI.prototype.bind = function () {
    if (this._bound) return;
    this._bound = true;
    var self = this;

    this.tickClock();
    setInterval(function () {
      self.tickClock();
    }, 1000);

    this.root.querySelectorAll(".sam-filter, .sam-count").forEach(function (btn) {
      btn.addEventListener("click", function () {
        self.applyFilter(btn.getAttribute("data-filter") || "all");
      });
    });

    var refresh =
      this.qs("#samRefresh") ||
      document.getElementById("samRefresh") ||
      document.getElementById("btnRefresh");
    if (refresh) {
      refresh.addEventListener("click", function () {
        refresh.classList.add("spin");
        self
          .load()
          .catch(function () {})
          .finally(function () {
            setTimeout(function () {
              refresh.classList.remove("spin");
            }, 500);
          });
      });
    }

    if (this.opts.typeFilterEl) {
      this.opts.typeFilterEl.addEventListener("change", function () {
        self.load().catch(function () {});
      });
    }

    var pollMs = this.opts.pollMs != null ? this.opts.pollMs : 8000;
    if (pollMs > 0) {
      this._poll = setInterval(function () {
        self.load().catch(function () {});
      }, pollMs);
    }
  };

  LiveMapUI.prototype.start = function () {
    this.bind();
    return this.load();
  };

  global.LiveMapUI = LiveMapUI;
})(window);
