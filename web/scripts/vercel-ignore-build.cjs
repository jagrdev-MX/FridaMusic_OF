const { execSync } = require("node:child_process");

const METRICS_COMMIT_PREFIX = "docs: actualizar metricas automaticas de contribucion";
const PUBLISHABLE_FILES = new Set(["vercel.json"]);
const PUBLISHABLE_PREFIXES = ["web/"];

function normalize(value) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

function readCommitMessage() {
  if (process.env.VERCEL_GIT_COMMIT_MESSAGE) {
    return process.env.VERCEL_GIT_COMMIT_MESSAGE;
  }

  try {
    return execSync("git log -1 --pretty=%B", { encoding: "utf8" });
  } catch (error) {
    console.warn(`No se pudo leer el mensaje del commit: ${error.message}`);
    return "";
  }
}

function readChangedFiles(revision) {
  try {
    return execSync(`git diff-tree --no-commit-id --name-only -r ${revision}`, {
      encoding: "utf8",
    })
      .split(/\r?\n/)
      .map((file) => file.trim())
      .filter(Boolean);
  } catch (error) {
    console.warn(`No se pudo leer el diff de ${revision}: ${error.message}`);
    return [];
  }
}

function isPublishableFile(file) {
  return (
    PUBLISHABLE_FILES.has(file) ||
    PUBLISHABLE_PREFIXES.some((prefix) => file.startsWith(prefix))
  );
}

const commitMessage = normalize(readCommitMessage());

if (commitMessage.startsWith(METRICS_COMMIT_PREFIX)) {
  const parentFiles = readChangedFiles("HEAD~1");

  if (parentFiles.some(isPublishableFile)) {
    console.log(
      "Commit automatico de metricas con cambios web publicables en el padre. Vercel continuara el build.",
    );
    process.exit(1);
  }

  console.log("Commit automatico de metricas detectado. Vercel omitira este build.");
  process.exit(0);
}

console.log("Cambio publicable detectado. Vercel continuara el build.");
process.exit(1);
