const fs = require("node:fs");
const path = require("node:path");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const WEB_ROOT = path.resolve(__dirname, "..");
const OUTPUT_ROOT = path.join(REPO_ROOT, ".vercel-static");

const PUBLIC_ENTRIES = [
  "index.html",
  "beta.html",
  "beta.js",
  "privacy.html",
  "privacy.js",
  "script.js",
  "styles.css",
  "logo1.png",
  "app-ads.txt",
  "assets",
  "capturas web",
];

function copyEntry(entry) {
  const source = path.join(WEB_ROOT, entry);
  const destination = path.join(OUTPUT_ROOT, entry);

  if (!fs.existsSync(source)) {
    throw new Error(`Falta un recurso web requerido: ${entry}`);
  }

  fs.cpSync(source, destination, {
    recursive: true,
    force: true,
  });
}

fs.rmSync(OUTPUT_ROOT, { recursive: true, force: true });
fs.mkdirSync(OUTPUT_ROOT, { recursive: true });

for (const entry of PUBLIC_ENTRIES) {
  copyEntry(entry);
}

console.log(`Sitio estático preparado en ${path.relative(REPO_ROOT, OUTPUT_ROOT)}/`);
console.log(`Entradas publicadas: ${PUBLIC_ENTRIES.join(", ")}`);
