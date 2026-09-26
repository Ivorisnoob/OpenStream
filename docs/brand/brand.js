// Shared drawing helpers for OpenStream release art. Every page renders at its exact output size;
// render.py screenshots them with headless Edge.

const NS = "http://www.w3.org/2000/svg";
const LANG = new URLSearchParams(location.search).get("lang") === "en" ? "en" : "ja";
document.documentElement.dataset.lang = LANG;

// The app icon's mark, in its 108-unit adaptive-icon space (same paths as ic_launcher_foreground.xml).
const MARK = {
  ring: "M73.97,49.39 A20.5,20.5 0 1,1 62.01,35.13",
  play: "M49.64,47.5 Q49.64,44.7 52.08,46.08 L63.58,52.62 Q66.01,54 63.58,55.38 L52.08,61.92 Q49.64,63.3 49.64,60.5 Z",
  spark: "M70.09,33.7 Q71.24,39.35 76.89,40.5 Q71.24,41.65 70.09,47.3 Q68.94,41.65 63.29,40.5 Q68.94,39.35 70.09,33.7 Z",
};

const INK = "#1A1030";
const SAKURA = "#FF5F8A";
const SAKURA_DEEP = "#E8457A";

function el(tag, attrs = {}, parent) {
  const node = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  if (parent) parent.appendChild(node);
  return node;
}

function rng(seed) {
  let s = seed % 2147483647;
  if (s <= 0) s += 2147483646;
  return () => (s = (s * 16807) % 2147483647) / 2147483647;
}

/** Draws the mark centred on (cx, cy) with the ring `size` px across. */
function mark(parent, { cx, cy, size, ink = INK, spark = "#fff" }) {
  const scale = size / 48.4; // ring outer diameter in icon units
  const g = el("g", { transform: `translate(${cx} ${cy}) scale(${scale}) translate(-54 -54)` }, parent);
  el("path", { d: MARK.ring, fill: "none", stroke: ink, "stroke-width": 7.4, "stroke-linecap": "round" }, g);
  el("path", { d: MARK.play, fill: ink }, g);
  el("path", { d: MARK.spark, fill: spark }, g);
  return g;
}

/** Manga screentone: a staggered dot grid whose dot size follows `weight(x, y)` in 0..1. */
function halftone(parent, { x0, y0, x1, y1, step, maxR, weight, fill }) {
  let d = "";
  let row = 0;
  for (let y = y0; y <= y1; y += step * 0.866, row++) {
    for (let x = x0 + (row % 2 ? step / 2 : 0); x <= x1; x += step) {
      const t = Math.min(1, weight(x, y));
      if (t <= 0.04) continue;
      const r = maxR * t;
      d += `M${(x - r).toFixed(1)},${y.toFixed(1)}a${r.toFixed(2)},${r.toFixed(2)} 0 1,0 ${(2 * r).toFixed(2)},0a${r.toFixed(2)},${r.toFixed(2)} 0 1,0 ${(-2 * r).toFixed(2)},0`;
    }
  }
  return el("path", { d, fill }, parent);
}

/** Manga focus lines: tapered wedges that point at (cx, cy) from beyond `outer`. */
function focusLines(parent, { cx, cy, inner, innerJitter, outer, count, minW, maxW, fill, seed = 7 }) {
  const rand = rng(seed);
  let d = "";
  for (let i = 0; i < count; i++) {
    const a = (i / count) * Math.PI * 2 + rand() * 0.02;
    const r0 = inner + rand() * innerJitter;
    const w = minW + Math.pow(rand(), 2.2) * (maxW - minW);
    const half = w / 2 / outer;
    const ax = cx + Math.cos(a) * r0, ay = cy + Math.sin(a) * r0;
    const bx = cx + Math.cos(a - half) * outer, by = cy + Math.sin(a - half) * outer;
    const qx = cx + Math.cos(a + half) * outer, qy = cy + Math.sin(a + half) * outer;
    d += `M${ax.toFixed(1)},${ay.toFixed(1)}L${bx.toFixed(1)},${by.toFixed(1)}L${qx.toFixed(1)},${qy.toFixed(1)}Z`;
  }
  return el("path", { d, fill }, parent);
}

/** Four-point sparkle like the one in the mark, centred on (x, y). */
function sparkle(parent, { x, y, r, fill = "#fff" }) {
  const i = r * 0.17;
  const d = `M${x},${y - r}Q${x + i},${y - i} ${x + r},${y}Q${x + i},${y + i} ${x},${y + r}Q${x - i},${y + i} ${x - r},${y}Q${x - i},${y - i} ${x},${y - r}Z`;
  return el("path", { d, fill }, parent);
}

/**
 * Material 3 Expressive shapes (MaterialShapes), traced as smooth outlines.
 * kind: cookie4 | cookie9 | cookie12 | sunny | clover4 | flower | softBurst | circle
 */
function shapePath({ kind, cx, cy, r, rot = 0 }) {
  const specs = {
    cookie4: [4, 0.12, 1], cookie9: [9, 0.06, 1], cookie12: [12, 0.045, 1],
    sunny: [8, 0.07, 1], softBurst: [10, 0.1, 1], flower: [8, 0.16, 0.7], clover4: [4, 0.26, 0.55],
    circle: [1, 0, 1],
  };
  const [n, depth, sharp] = specs[kind];
  const steps = 360;
  let d = "";
  for (let i = 0; i <= steps; i++) {
    const a = (i / steps) * Math.PI * 2;
    // Lobes at cos = 1, indents between; `sharp` < 1 pinches the indents like clover/flower.
    const wave = Math.pow(Math.abs(Math.cos((n * a) / 2)), sharp);
    const rr = r * (1 - depth + depth * wave);
    const x = cx + rr * Math.cos(a + rot), y = cy + rr * Math.sin(a + rot);
    d += (i ? "L" : "M") + x.toFixed(1) + "," + y.toFixed(1);
  }
  return d + "Z";
}

/** The Material 3 Expressive wavy progress indicator: a wave for the played part, a flat track after. */
function wavyProgress(parent, { x, y, width, progress, color, track, stroke = 8, amp = 5, wavelength = 36 }) {
  const end = x + width * progress;
  let d = `M${x},${y}`;
  for (let px = x; px <= end; px += 2) {
    d += `L${px.toFixed(1)},${(y + amp * Math.sin(((px - x) / wavelength) * Math.PI * 2)).toFixed(1)}`;
  }
  el("path", { d, fill: "none", stroke: color, "stroke-width": stroke, "stroke-linecap": "round", "stroke-linejoin": "round" }, parent);
  const gap = stroke * 1.6;
  el("line", { x1: end + gap, y1: y, x2: x + width, y2: y, stroke: track, "stroke-width": stroke, "stroke-linecap": "round" }, parent);
  el("circle", { cx: x + width, cy: y, r: stroke / 2.6, fill: color }, parent);
}

/** Draws the mark into an existing <svg viewBox="30 30 48 48"> (used inside small brand pills). */
function markInto(svgEl, spark = "#fff") {
  el("path", { d: MARK.ring, fill: "none", stroke: INK, "stroke-width": 7.4, "stroke-linecap": "round" }, svgEl);
  el("path", { d: MARK.play, fill: INK }, svgEl);
  el("path", { d: MARK.spark, fill: spark }, svgEl);
}

/** A MaterialShape drawn like a comic panel: optional hard ink shadow, fill, ink outline. */
function inkShape(parent, { kind, cx, cy, r, rot = 0, fill, outline = 4, shadow = 10 }) {
  const d = shapePath({ kind, cx, cy, r, rot });
  if (shadow) el("path", { d, fill: INK, transform: `translate(${shadow} ${shadow})` }, parent);
  el("path", { d, fill }, parent);
  if (outline) el("path", { d, fill: "none", stroke: INK, "stroke-width": outline, "stroke-linejoin": "round" }, parent);
  return d;
}

/** Standard sakura field: two screentone washes. */
function sakuraField(svg, { width, height, seed = 0 }) {
  halftone(svg, {
    x0: 0, y0: 0, x1: width, y1: height, step: 17, maxR: 7.2, fill: SAKURA_DEEP,
    weight: (x, y) => Math.max(((y - height * 0.4) - x * 0.55) / (height * 0.56), 0),
  });
  halftone(svg, {
    x0: width * 0.45, y0: 0, x1: width, y1: height * 0.6, step: 13, maxR: 5, fill: SAKURA_DEEP,
    weight: (x, y) => ((x - width * 0.7) * 0.6 - y + 40) / 300,
  });
}

/** Focus lines that fade toward the left third so type over them stays clean. */
function fadedFocusLines(svg, { width, height, cx, cy, inner, count = 150, id = "lines", fadeFrom = 0.1, fadeTo = 0.62, vertical = false }) {
  const defs = el("defs", {}, svg);
  const g = el("linearGradient", vertical ? { id: id + "g", x1: 0, x2: 0, y1: 0, y2: 1 } : { id: id + "g", x1: 0, x2: 1, y1: 0, y2: 0 }, defs);
  el("stop", { offset: fadeFrom, "stop-color": "#fff", "stop-opacity": 0.12 }, g);
  el("stop", { offset: (fadeFrom + fadeTo) / 2, "stop-color": "#fff", "stop-opacity": 0.3 }, g);
  el("stop", { offset: fadeTo, "stop-color": "#fff", "stop-opacity": 1 }, g);
  const mask = el("mask", { id }, defs);
  el("rect", { width, height, fill: `url(#${id}g)` }, mask);
  const layer = el("g", { mask: `url(#${id})` }, svg);
  focusLines(layer, {
    cx, cy, inner, innerJitter: 70, outer: Math.hypot(width, height), count,
    minW: 3, maxW: 26, fill: "rgba(255,255,255,0.55)", seed: 11,
  });
}

/** Draws a manga speech-bubble tail from `bubble` (a DOM element) toward `tip`, into an SVG layer above it. */
function bubbleTail(layer, bubble, tip, base = 30) {
  const stage = layer.getBoundingClientRect();
  const b = bubble.getBoundingClientRect();
  const box = { l: b.left - stage.left, t: b.top - stage.top, r: b.right - stage.left, btm: b.bottom - stage.top };
  const mx = (box.l + box.r) / 2, my = (box.t + box.btm) / 2;
  const dx = tip.x - mx, dy = tip.y - my;
  const half = base / 2;
  const radius = parseFloat(getComputedStyle(bubble).borderTopLeftRadius) || 0;
  // Keep the base on the straight part of the edge, clear of the rounded corners.
  const clamp = (v, lo, hi) => (lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v)));
  const inset = 3, cover = 14;
  let p1, p2, i1, i2;
  if (Math.abs(dx) * (box.btm - box.t) > Math.abs(dy) * (box.r - box.l)) {
    const s = dx > 0 ? -1 : 1, x = (dx > 0 ? box.r : box.l) + s * inset;
    const y = clamp(tip.y, box.t + radius + half, box.btm - radius - half);
    p1 = [x, y - half]; p2 = [x, y + half]; i1 = [x + s * cover, y - half]; i2 = [x + s * cover, y + half];
  } else {
    const s = dy > 0 ? -1 : 1, y = (dy > 0 ? box.btm : box.t) + s * inset;
    const x = clamp(tip.x, box.l + radius + half, box.r - radius - half);
    p1 = [x - half, y]; p2 = [x + half, y]; i1 = [x - half, y + s * cover]; i2 = [x + half, y + s * cover];
  }
  // One seamless white fill from inside the bubble to the tip, then ink on the two legs only.
  el("path", { d: `M${i1}L${p1}L${tip.x},${tip.y}L${p2}L${i2}Z`, fill: "#fff" }, layer);
  el("path", { d: `M${p1}L${tip.x},${tip.y}L${p2}`, fill: "none", stroke: INK, "stroke-width": 4, "stroke-linejoin": "round" }, layer);
}
