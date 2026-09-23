const fs = require("fs");
const path = require("path");
// Redimensiona con sharp si está disponible; si no, usa un canvas alternativo.
(async () => {
  let sharp;
  try { sharp = require("sharp"); } catch (e) { sharp = null; }
  const targets = [
    { file: "img/d0.png", maxH: 560, maxW: 900 }, // arquitectura
    { file: "img/d2.png", maxH: 560, maxW: 900 }  // ER
  ];
  if (!sharp) { console.log("NO_SHARP"); return; }
  for (const t of targets) {
    const p = path.resolve(t.file);
    const meta = await sharp(p).metadata();
    const ratio = Math.min(t.maxW / meta.width, t.maxH / meta.height);
    const w = Math.round(meta.width * ratio);
    const h = Math.round(meta.height * ratio);
    const tmp = p.replace(".png", "-r.png");
    await sharp(p).resize(w, h, { fit: "contain", background: { r:255,g:255,b:255,alpha:0 } }).png().toFile(tmp);
    fs.renameSync(tmp, p);
    console.log(t.file + " -> " + w + "x" + h);
  }
})();
