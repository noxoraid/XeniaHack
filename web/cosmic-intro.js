(function (g) {
  var script = document.currentScript;

  function play(opts) {
    var o = Object.assign(
      {
        src: "cosmic-rift-1080p.mp4",
        srcMobile: "cosmic-rift-720p.mp4",
        poster: "",
        once: true,
        key: "cosmicIntroSeen",
        skippable: true,
        background: "#02030a",
        fadeOut: 600,
        onEnd: null,
      },
      opts || {}
    );

    return new Promise(function (resolve) {
      if (o.once) {
        try {
          if (sessionStorage.getItem(o.key)) {
            if (o.onEnd) o.onEnd();
            return resolve();
          }
        } catch (e) {}
      }

      function mount() {
        var done = false;
        var prevOverflow = document.documentElement.style.overflow;
        document.documentElement.style.overflow = "hidden";

        var root = document.createElement("div");
        root.style.cssText =
          "position:fixed;inset:0;z-index:2147483647;display:flex;align-items:center;justify-content:center;opacity:1;transition:opacity " +
          o.fadeOut +
          "ms ease;background:" +
          o.background;

        var v = document.createElement("video");
        v.src = window.innerWidth <= 900 && o.srcMobile ? o.srcMobile : o.src;
        v.playsInline = true;
        v.setAttribute("playsinline", "");
        v.preload = "auto";
        if (o.poster) v.poster = o.poster;
        v.style.cssText =
          "width:100%;height:100%;object-fit:" +
          (window.innerWidth < window.innerHeight ? "contain" : "cover");
        root.appendChild(v);

        function finish() {
          if (done) return;
          done = true;
          try {
            sessionStorage.setItem(o.key, "1");
          } catch (e) {}
          document.removeEventListener("keydown", onKey);
          root.style.opacity = "0";
          try {
            v.pause();
          } catch (e) {}
          setTimeout(function () {
            if (root.parentNode) root.parentNode.removeChild(root);
            document.documentElement.style.overflow = prevOverflow;
            if (o.onEnd) o.onEnd();
            resolve();
          }, o.fadeOut);
        }

        function onKey(e) {
          if (e.key === "Escape") finish();
        }

        if (o.skippable) {
          var skip = document.createElement("button");
          skip.textContent = "SKIP";
          skip.style.cssText =
            "position:absolute;top:max(16px,env(safe-area-inset-top));right:16px;padding:8px 16px;border:1px solid rgba(255,255,255,.35);border-radius:999px;background:rgba(2,3,10,.45);color:#fff;font:600 12px/1 system-ui,sans-serif;letter-spacing:.2em;cursor:pointer";
          skip.addEventListener("click", finish);
          root.appendChild(skip);
          document.addEventListener("keydown", onKey);
        }

        v.addEventListener("ended", finish);
        v.addEventListener("error", finish);

        document.body.appendChild(root);

        var p = v.play();
        if (p && p.catch) {
          p.catch(function () {
            var tap = document.createElement("button");
            tap.textContent = "TAP TO ENTER";
            tap.style.cssText =
              "position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);padding:16px 34px;border:1px solid rgba(140,200,255,.7);border-radius:999px;background:rgba(10,12,40,.6);color:#fff;font:700 15px/1 system-ui,sans-serif;letter-spacing:.3em;cursor:pointer;box-shadow:0 0 40px rgba(80,160,255,.45)";
            tap.addEventListener("click", function () {
              if (tap.parentNode) tap.parentNode.removeChild(tap);
              v.currentTime = 0;
              v.muted = false;
              v.play().catch(finish);
            });
            root.appendChild(tap);
          });
        }
      }

      if (document.body) mount();
      else document.addEventListener("DOMContentLoaded", mount);
    });
  }

  g.CosmicIntro = {play: play};

  if (script && script.hasAttribute("data-auto")) {
    play({
      src: script.getAttribute("data-src") || undefined,
      srcMobile: script.getAttribute("data-src-mobile") || undefined,
      poster: script.getAttribute("data-poster") || "",
      once: script.getAttribute("data-once") !== "false",
    });
  }
})(window);
