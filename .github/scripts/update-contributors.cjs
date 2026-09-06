const fs = require("node:fs");
const path = require("node:path");

const README_PATH = path.join(process.cwd(), "README.md");
const API_BASE = process.env.GITHUB_API_URL || "https://api.github.com";
const MAX_FORKS = Number(process.env.MAX_FORKS || 50);
const START_MARKER = "<!-- CONTRIBUTOR-STATS:START -->";
const END_MARKER = "<!-- CONTRIBUTOR-STATS:END -->";
const IGNORED_AUTOMATION_LOGINS = new Set(["github-actions[bot]"]);

function resolveRepositorySlug() {
  if (process.env.GITHUB_REPOSITORY) {
    return process.env.GITHUB_REPOSITORY;
  }

  const packageJsonPath = path.join(process.cwd(), "package.json");
  if (fs.existsSync(packageJsonPath)) {
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));
    const repositoryUrl =
      typeof packageJson.repository === "string"
        ? packageJson.repository
        : packageJson.repository?.url;

    const match = repositoryUrl?.match(
      /github\.com[:/](?<owner>[^/]+)\/(?<repo>[^/.]+)(?:\.git)?$/i,
    );

    if (match?.groups) {
      return `${match.groups.owner}/${match.groups.repo}`;
    }
  }

  throw new Error(
    "No se pudo detectar el repositorio. Define GITHUB_REPOSITORY o package.json#repository.",
  );
}

function apiHeaders() {
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2026-03-10",
    "User-Agent": "FridaMusic-contributor-stats",
  };

  if (process.env.GITHUB_TOKEN) {
    headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  }

  return headers;
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: apiHeaders() });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API ${response.status} para ${url}: ${body}`);
  }

  return response.json();
}

async function fetchPaged(pathname, maxItems = Number.POSITIVE_INFINITY) {
  const perPage = Math.min(100, maxItems);
  const items = [];

  for (let page = 1; items.length < maxItems; page += 1) {
    const separator = pathname.includes("?") ? "&" : "?";
    const pageItems = await fetchJson(
      `${API_BASE}${pathname}${separator}per_page=${perPage}&page=${page}`,
    );

    if (!Array.isArray(pageItems) || pageItems.length === 0) {
      break;
    }

    items.push(...pageItems);

    if (pageItems.length < perPage) {
      break;
    }
  }

  return items.slice(0, maxItems);
}

function isBotContributor(contributor) {
  const login = contributor.login || contributor.name || "";
  return (
    contributor.type === "Bot" ||
    login.endsWith("[bot]") ||
    /(?:^|[-_])bot$/i.test(login)
  );
}

function isIgnoredAutomationContributor(contributor) {
  const login = contributor.login || contributor.name || "";
  return IGNORED_AUTOMATION_LOGINS.has(login);
}

function contributorName(contributor) {
  if (contributor.login) {
    return `[@${contributor.login}](https://github.com/${contributor.login})`;
  }

  return contributor.name || "Anónimo";
}

function formatPercent(value) {
  return `${value.toFixed(1)}%`;
}

function formatDate(value) {
  if (!value) {
    return "—";
  }

  return value.slice(0, 10);
}

function buildOfficialContributionSection(contributors) {
  const humans = contributors.filter((contributor) => !isBotContributor(contributor));
  const bots = contributors.filter(
    (contributor) =>
      isBotContributor(contributor) && !isIgnoredAutomationContributor(contributor),
  );
  const humanCommits = humans.reduce(
    (total, contributor) => total + contributor.contributions,
    0,
  );
  const botCommits = bots.reduce(
    (total, contributor) => total + contributor.contributions,
    0,
  );

  const rows = humans.map((contributor) => {
    const percentage =
      humanCommits === 0 ? 0 : (contributor.contributions / humanCommits) * 100;

    return `| ${contributorName(contributor)} | ${contributor.contributions} | ${formatPercent(
      percentage,
    )} |`;
  });

  return [
    "### Repositorio oficial",
    "",
    `**Commits humanos visibles:** ${humanCommits} · **Commits de automatización externos:** ${botCommits}`,
    "",
    "| Colaborador | Commits | % de contribución humana |",
    "| --- | ---: | ---: |",
    ...(rows.length > 0
      ? rows
      : ["| Sin datos todavía | 0 | 0.0% |"]),
  ].join("\n");
}

async function buildIndependentForkSection(owner, repo, baseBranch, forks) {
  const activeForks = [];

  for (const fork of forks) {
    const head = `${fork.owner.login}:${fork.default_branch}`;
    const compareUrl = `${API_BASE}/repos/${owner}/${repo}/compare/${encodeURIComponent(
      baseBranch,
    )}...${encodeURIComponent(head)}`;

    try {
      const comparison = await fetchJson(compareUrl);

      if (comparison.ahead_by > 0) {
        activeForks.push({
          fullName: fork.full_name,
          branch: fork.default_branch,
          aheadBy: comparison.ahead_by,
          pushedAt: fork.pushed_at,
        });
      }
    } catch (error) {
      console.warn(`No se pudo comparar ${fork.full_name}: ${error.message}`);
    }
  }

  activeForks.sort((a, b) => b.aheadBy - a.aheadBy);
  const totalAhead = activeForks.reduce((total, fork) => total + fork.aheadBy, 0);

  const rows = activeForks.map((fork) => {
    const percentage = totalAhead === 0 ? 0 : (fork.aheadBy / totalAhead) * 100;

    return `| [${fork.fullName}](https://github.com/${fork.fullName}) | \`${fork.branch}\` | ${fork.aheadBy} | ${formatPercent(
      percentage,
    )} | ${formatDate(fork.pushedAt)} |`;
  });

  return [
    "### Forks con trabajo independiente",
    "",
    `**Forks inspeccionados:** ${forks.length} · **Forks activos:** ${activeForks.length}`,
    "",
    "| Fork | Rama | Commits por delante | % de actividad independiente | Último push |",
    "| --- | --- | ---: | ---: | --- |",
    ...(rows.length > 0
      ? rows
      : ["| Sin forks activos detectados | — | 0 | 0.0% | — |"]),
  ].join("\n");
}

function replaceGeneratedBlock(readme, generatedContent) {
  const startIndex = readme.indexOf(START_MARKER);
  const endIndex = readme.indexOf(END_MARKER);

  if (startIndex === -1 || endIndex === -1 || endIndex < startIndex) {
    throw new Error("No se encontraron los marcadores de contribuciones automáticas en README.md.");
  }

  const before = readme.slice(0, startIndex + START_MARKER.length);
  const after = readme.slice(endIndex);

  return `${before}\n${generatedContent}\n${after}`;
}

async function main() {
  const repository = resolveRepositorySlug();
  const [owner, repo] = repository.split("/");
  const repositoryInfo = await fetchJson(`${API_BASE}/repos/${owner}/${repo}`);
  const contributors = await fetchPaged(
    `/repos/${owner}/${repo}/contributors?anon=1`,
  );
  const forks = await fetchPaged(
    `/repos/${owner}/${repo}/forks`,
    MAX_FORKS,
  );

  const generatedContent = [
    "_Esta sección se actualiza automáticamente con GitHub Actions._",
    "",
    buildOfficialContributionSection(contributors),
    "",
    await buildIndependentForkSection(
      owner,
      repo,
      repositoryInfo.default_branch,
      forks,
    ),
    "",
    "> Los porcentajes del repositorio oficial se calculan con commits humanos visibles. Los bots se separan para no distorsionar la métrica. Los forks muestran trabajo independiente que aún no necesariamente fue integrado al proyecto principal.",
    "",
    `Última actualización automática (UTC): \`${new Date()
      .toISOString()
      .slice(0, 10)}\``,
  ].join("\n");

  const readme = fs.readFileSync(README_PATH, "utf8");
  const updatedReadme = replaceGeneratedBlock(readme, generatedContent);
  fs.writeFileSync(README_PATH, updatedReadme, "utf8");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
