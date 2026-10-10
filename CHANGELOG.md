# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Multi-module Maven build (`ums-api`, `ums-platform`, `ums-testkit`, `ums-core`, `ums-app`) with pinned dependency and plugin versions and enforcer rules.
- Quality gates: Spotless (google-java-format), Checkstyle design rules, SpotBugs + FindSecBugs, JaCoCo line-coverage check (≥ 80 % on platform/service/domain), third-party licence allow-list, Surefire (`*Test`) / Failsafe (`*IT`) split.
- Build profiles `fast`, default, `nightly`, `release` (PIT mutation ≥ 70 %, OWASP Dependency-Check CVSS ≥ 7).
- Versioned pre-commit hook (`scripts/hooks/pre-commit`) running the fast gate and gitleaks; `scripts/install-hooks.sh`.
