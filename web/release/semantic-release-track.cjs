"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const ROOT = path.resolve(__dirname, "../..");
const CHANGELOG_PATH = path.join(ROOT, "web", "release", "CHANGELOG.md");
const WEB_PREFIX = "web/";
const RELEASE_TAG_SUFFIX = "-web";

const RELEASE_LEVELS = {
  major: 3,
  minor: 2,
  patch: 1
};

const COMMIT_LEVELS = {
  feat: "minor",
  fix: "patch",
  perf: "patch",
  refactor: "patch",
  docs: "patch",
  style: "patch",
  test: "patch",
  build: "patch",
  ci: "patch",
  chore: "patch"
};

function changedFiles(commitHash) {
  if (!commitHash) return [];

  try {
    return execFileSync(
      "git",
      ["diff-tree", "--no-commit-id", "--name-only", "-r", "-m", commitHash],
      { cwd: ROOT, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }
    )
      .split(/\r?\n/)
      .map((file) => file.trim())
      .filter(Boolean);
  } catch {
    return [];
  }
}

function touchesWeb(commit) {
  const hash = commit.hash || commit.gitHead || "";
  return changedFiles(hash).some((file) => file === "vercel.json" || file.startsWith(WEB_PREFIX));
}

function parseCommit(commit) {
  const message = String(commit.message || "");
  const header = String(commit.header || commit.subject || message.split(/\r?\n/, 1)[0]).trim();
  const conventional = header.match(/^([\w-]+)(?:\(([^)]+)\))?(!)?:\s+(.+)$/);
  const type = String(commit.type || conventional?.[1] || "").toLowerCase();
  const scope = String(commit.scope || conventional?.[2] || "").trim();
  const subject = String(commit.subject || conventional?.[4] || header).trim();
  const breaking = Boolean(commit.breaking) || Boolean(conventional?.[3]) || /BREAKING CHANGE:/i.test(message);

  if (!type || /^chore(?:\(release\))?/i.test(header)) return null;

  return {
    hash: String(commit.hash || commit.gitHead || "").slice(0, 7),
    type,
    scope,
    subject,
    breaking,
    releaseType: breaking ? "major" : COMMIT_LEVELS[type] || null
  };
}

function webCommits(context) {
  return (context.commits || [])
    .filter(touchesWeb)
    .map(parseCommit)
    .filter((commit) => commit && commit.releaseType);
}

function highestReleaseType(commits) {
  return commits.reduce((highest, commit) => {
    return RELEASE_LEVELS[commit.releaseType] > RELEASE_LEVELS[highest] ? commit.releaseType : highest;
  }, "patch");
}

function categoryFor(type) {
  if (type === "feat") return "### ✨ Features";
  if (type === "fix" || type === "perf") return "### 🐛 Fixes";
  return "### 🔧 Other changes";
}

function renderNotes(commits, version, lastTag) {
  const date = new Date().toISOString().slice(0, 10);
  const grouped = new Map();
  for (const commit of commits) {
    const category = categoryFor(commit.type);
    if (!grouped.has(category)) grouped.set(category, []);
    const scope = commit.scope ? `${commit.scope}: ` : "";
    const marker = commit.hash ? ` (${commit.hash})` : "";
    grouped.get(category).push(`- ${scope}${commit.subject}${marker}`);
  }

  const count = commits.length;
  const plural = count === 1 ? "commit" : "commits";
  const tag = `v${version}${RELEASE_TAG_SUFFIX}`;
  const sections = [
    `## [${tag}] - ${date}`,
    "",
    "**Owner:** jagrdev-MX",
    "**Project:** FridaMusic TM",
    "**Track:** Web",
    ""
  ];

  for (const [category, entries] of grouped) {
    sections.push(category, ...entries, "");
  }

  sections.push(
    "### 🚀 Release Notes",
    `- ES: Release de Web con ${count} ${plural} convencional${count === 1 ? "" : "es"} confirmado${count === 1 ? "" : "s"} desde ${lastTag} hasta ${tag}.`,
    `- EN: Web release with ${count} confirmed Conventional ${plural} from ${lastTag} to ${tag}.`
  );

  return sections.join("\n").trim();
}

async function analyzeCommits(_pluginConfig, context) {
  const commits = webCommits(context);
  return commits.length ? highestReleaseType(commits) : null;
}

async function generateNotes(_pluginConfig, context) {
  const commits = webCommits(context);
  const version = context.nextRelease?.version || "0.0.0";
  const lastTag = context.lastRelease?.gitTag || "the previous release";
  return renderNotes(commits, version, lastTag);
}

async function prepare(_pluginConfig, context) {
  const notes = String(context.nextRelease?.notes || "").trim();
  if (!notes) return;

  const current = fs.existsSync(CHANGELOG_PATH) ? fs.readFileSync(CHANGELOG_PATH, "utf8") : "# Web Changelog\n";
  const heading = `## [v${context.nextRelease.version}${RELEASE_TAG_SUFFIX}] - `;
  if (current.includes(heading)) return;

  const withHeader = current.trimStart().startsWith("# Web Changelog") ? current.trim() : `# Web Changelog\n\n${current.trim()}`;
  fs.writeFileSync(CHANGELOG_PATH, `${withHeader}\n\n${notes}\n`, "utf8");
}

module.exports = { analyzeCommits, generateNotes, prepare };
