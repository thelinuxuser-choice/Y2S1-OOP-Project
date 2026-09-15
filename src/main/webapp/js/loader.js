/* parkED loader */
(function () {
  if (document.getElementById("pf-loader")) return;

  var loader = document.createElement("div");
  loader.id = "pf-loader";
  loader.setAttribute("aria-hidden", "true");
  loader.innerHTML =
    '<div class="pf-loader-inner">' +
    '<div class="pf-loader-brand">park<em>ED</em></div>' +
    '<div class="pf-loader-bar"><span></span></div>' +
    '<p class="pf-loader-hint">Loading your parking space</p>' +
    "</div>";
  document.documentElement.appendChild(loader);
  document.documentElement.classList.add("pf-loading");

  function finish() {
    requestAnimationFrame(function () {
      loader.classList.add("pf-loader-out");
      document.documentElement.classList.remove("pf-loading");
      document.documentElement.classList.add("pf-ready");
      setTimeout(function () {
        if (loader.parentNode) loader.parentNode.removeChild(loader);
      }, 700);
    });
  }

  var minMs = 1000;
  var start = Date.now();
  function maybeDone() {
    var left = minMs - (Date.now() - start);
    if (left > 0) setTimeout(finish, left);
    else finish();
  }

  if (document.readyState === "complete") maybeDone();
  else window.addEventListener("load", maybeDone);
  setTimeout(finish, 3000);

  window.addEventListener("load", function () {
    setTimeout(function () {
      document.querySelectorAll("[data-reveal]").forEach(function (el, i) {
        el.style.setProperty("--reveal-delay", 60 + i * 60 + "ms");
        el.classList.add("is-revealed");
      });
    }, minMs + 60);
  });
})();
