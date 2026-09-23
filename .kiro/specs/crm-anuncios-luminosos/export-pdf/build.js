const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const SRC = path.resolve("..", "design.md");
const IMGDIR = path.resolve("img");
const OUTMD = path.resolve("design-print.md");
const CONFIG = path.resolve("mermaid-config.json");
const PPTR = path.resolve("puppeteer.json");

let md = fs.readFileSync(SRC, "utf8");
const re = /```mermaid\s*([\s\S]*?)```/g;
let m; const blocks = [];
while ((m = re.exec(md)) !== null) blocks.push({ full: m[0], code: m[1] });
console.log("Diagramas encontrados: " + blocks.length);
const q = (s) => '"' + s + '"';

// idx 0 = arquitectura -> SVG (se regenera desde el contenido actual)
// idx 2 = ER comercial -> se divide en d2a + d2b (SVG pre-generados, sin cambios)
// resto -> PNG escala 3
blocks.forEach((b, idx) => {
  if (idx === 0) {
    const inFile = path.join(IMGDIR, "d0.mmd");
    const outFile = path.join(IMGDIR, "d0.svg");
    fs.writeFileSync(inFile, b.code.trim(), "utf8");
    const cmd = ["mmdc","-i",q(inFile),"-o",q(outFile),"-c",q(CONFIG),"-p",q(PPTR),"-b","white"].join(" ");
    try {
      execSync(cmd, { stdio: "inherit" });
      md = md.replace(b.full, '\n\n<div class="diagram"><img class="mermaid-diagram" src="img/d0.svg" alt="Arquitectura" /></div>\n\n');
      console.log("d0 -> SVG (regenerado)");
    } catch (e) { console.error("Fallo d0: " + e.message); }
    return;
  }
  if (idx === 2) {
    const rep = '\n\n<div class="diagram"><img class="mermaid-diagram" src="img/d2a.svg" alt="ER comercial" /></div>\n\n' +
                '\n\n<div class="diagram"><img class="mermaid-diagram" src="img/d2b.svg" alt="ER facturacion" /></div>\n\n';
    md = md.replace(b.full, rep);
    console.log("d2 -> 2 SVG (comercial + facturacion, sin cambios)");
    return;
  }
  const inFile = path.join(IMGDIR, "d" + idx + ".mmd");
  const outFile = path.join(IMGDIR, "d" + idx + ".png");
  fs.writeFileSync(inFile, b.code.trim(), "utf8");
  const cmd = ["mmdc","-i",q(inFile),"-o",q(outFile),"-c",q(CONFIG),"-p",q(PPTR),"-b","white","-s","3"].join(" ");
  try {
    execSync(cmd, { stdio: "inherit" });
    md = md.replace(b.full, '\n\n<div class="diagram"><img class="mermaid-diagram" src="img/d' + idx + '.png" alt="Diagrama ' + (idx+1) + '" /></div>\n\n');
    console.log("d" + idx + " -> PNG");
  } catch (e) { console.error("Fallo d" + idx + ": " + e.message); }
});

fs.writeFileSync(OUTMD, md, "utf8");
console.log("OK design-print.md");