const path = require("node:path");

module.exports = {
  branches: ["master"],
  tagFormat: "v${version}-web",
  plugins: [
    [path.resolve(__dirname, "semantic-release-track.cjs"), { track: "web" }],
    "@semantic-release/github",
    [
      "@semantic-release/git",
      {
        assets: ["web/release/CHANGELOG.md"],
        message: "chore(release): web ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
      }
    ]
  ]
};
