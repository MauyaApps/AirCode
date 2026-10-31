(function () {
  var frames = 0;
  var lastFrame = 0;
  function tick(now) {
    frames++;
    lastFrame = now;
    window.requestAnimationFrame(tick);
  }
  window.requestAnimationFrame(tick);

  function rect(element) {
    if (!element) return null;
    var r = element.getBoundingClientRect();
    return [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
  }

  function style(element, property) {
    return element ? window.getComputedStyle(element)[property] : null;
  }

  window.AirCodeDiag = window.AirCodeDiag || {};
  window.AirCodeDiag.diagnose = function () {
    var editor = document.querySelector(".cm-editor");
    var line = document.querySelector(".cm-line");
    var gutters = document.querySelector(".cm-gutters");
    return JSON.stringify({
      frames: frames,
      msSinceFrame: Math.round(performance.now() - lastFrame),
      visibility: document.visibilityState,
      focus: document.hasFocus(),
      inner: [window.innerWidth, window.innerHeight],
      pixelRatio: window.devicePixelRatio,
      scheme: style(document.documentElement, "colorScheme"),
      bodyBackground: style(document.body, "backgroundColor"),
      editorRect: rect(editor),
      guttersRect: rect(gutters),
      editorBackground: style(editor, "backgroundColor"),
      lineCount: document.querySelectorAll(".cm-line").length,
      firstLine: line ? line.textContent.slice(0, 24) : null,
      lineColor: style(line, "color"),
      lineOpacity: style(line, "opacity"),
      lineVisibility: style(line, "visibility"),
      gutterColor: style(gutters, "color"),
    });
  };

  window.AirCodeDiag.refresh = function () {
    var root = document.documentElement;
    root.style.opacity = "0.999";
    window.requestAnimationFrame(function () {
      root.style.opacity = "";
      window.dispatchEvent(new Event("resize"));
    });
  };
})();
