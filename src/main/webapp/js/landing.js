// ParkFlow landing motion — Tabela-style word stagger + scroll reveals
(function () {
  function splitTitles() {
    document.querySelectorAll("[data-split]").forEach(function (el) {
      if (el.dataset.splitDone) return;
      el.dataset.splitDone = "1";
      var html = el.innerHTML;
      // keep accent-word / scribble blocks intact
      var parts = [];
      var re = /(<span class="accent-word">[\s\S]*?<\/span>)|(<br\s*\/?>)|([^<\s]+)|(\s+)/g;
      var m;
      while ((m = re.exec(html)) !== null) {
        if (m[1]) {
          parts.push({ type: "accent", html: m[1] });
        } else if (m[2]) {
          parts.push({ type: "br" });
        } else if (m[4]) {
          parts.push({ type: "space", text: m[4] });
        } else if (m[3]) {
          parts.push({ type: "word", text: m[3] });
        }
      }
      el.innerHTML = parts
        .map(function (p, i) {
          if (p.type === "space") return p.text;
          if (p.type === "br") return "<br />";
          if (p.type === "accent") {
            return (
              '<span class="word-wrap" style="--i:' +
              i +
              '"><span class="word" style="animation-delay:' +
              i * 55 +
              'ms">' +
              p.html +
              "</span></span>"
            );
          }
          return (
            '<span class="word-wrap" style="--i:' +
            i +
            '"><span class="word" style="animation-delay:' +
            i * 55 +
            'ms">' +
            p.text +
            "</span></span>"
          );
        })
        .join("");
    });
  }

  function revealOnView() {
    var nodes = document.querySelectorAll("[data-reveal], [data-split], .lp-tile");
    if (!("IntersectionObserver" in window)) {
      nodes.forEach(function (n) {
        n.classList.add("is-in");
      });
      return;
    }
    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-in");
          if (entry.target.hasAttribute("data-split") || entry.target.classList.contains("lp-h1") || entry.target.classList.contains("lp-title")) {
            entry.target.classList.add("is-in");
          }
          // counters inside
          entry.target.querySelectorAll("[data-count]").forEach(animateCount);
          io.unobserve(entry.target);
        });
      },
      { threshold: 0.18, rootMargin: "0px 0px -8% 0px" }
    );
    nodes.forEach(function (n) {
      io.observe(n);
    });
    // hero title should run soon after loader
    var hero = document.querySelector(".lp-h1");
    if (hero) {
      setTimeout(function () {
        hero.classList.add("is-in");
      }, 1250);
    }
  }

  function animateCount(el) {
    if (el.dataset.counted) return;
    el.dataset.counted = "1";
    var target = parseInt(el.getAttribute("data-count"), 10) || 0;
    var dur = 1100;
    var start = performance.now();
    function tick(now) {
      var t = Math.min(1, (now - start) / dur);
      var eased = 1 - Math.pow(1 - t, 3);
      el.textContent = Math.round(target * eased);
      if (t < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  function tabs() {
    var tabs = document.querySelectorAll(".lp-tab");
    var panels = document.querySelectorAll(".lp-feature-panel");
    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        tabs.forEach(function (t) {
          t.classList.remove("active");
        });
        panels.forEach(function (p) {
          p.classList.remove("active");
        });
        tab.classList.add("active");
        var id = tab.getAttribute("data-tab");
        var panel = document.querySelector('.lp-feature-panel[data-panel="' + id + '"]');
        if (panel) panel.classList.add("active");
      });
    });
  }

  function headerScroll() {
    var header = document.getElementById("lpHeader");
    if (!header) return;
    function onScroll() {
      header.classList.toggle("is-scrolled", window.scrollY > 12);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  function drawer() {
    var btn = document.getElementById("lpMenu");
    var drawer = document.getElementById("lpDrawer");
    if (!btn || !drawer) return;
    btn.addEventListener("click", function () {
      drawer.classList.add("open");
    });
    drawer.addEventListener("click", function (e) {
      if (e.target === drawer || e.target.closest("a")) drawer.classList.remove("open");
    });
  }

  function slotMap() {
    var root = document.getElementById("slotMap");
    if (!root || !window.LiveMapUI) return;
    var map = new LiveMapUI(root, { facilityId: 1, pollMs: 8000 });
    map.start().catch(function () {});
  }

  function boot() {
    splitTitles();
    revealOnView();
    tabs();
    headerScroll();
    drawer();
    slotMap();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
