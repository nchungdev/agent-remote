# ADR-0001: Project Base Architecture, Security Gates & Multi-Agent Standards

- **Date**: 2026-10-31 | **Status**: Accepted | **Project**: agent-remote | **Stack**: Kotlin Multiplatform / Compose Multiplatform (KMP / CMP)

## Decision
- **Architecture Style**: `clean` | **Flow Pattern**: `mvi`
- **Reference Architecture Anchor**: [`chrisbanes/tivi`](https://github.com/chrisbanes/tivi)
- **Boundary Enforcement**: KMP sourceSet isolation + Konsist architecture tests in commonTest
- **File Size Policy**: `1 File = 1 Primary Responsibility`, warn at `300 LOC`, hard block at `450 LOC`.
- **Security & Git Gates**: Staged secret scanning, OWASP heuristics, Conventional Commits, and pre-push test/CVE verification via `.githooks/`.

### Selected Base Capabilities
- `solid-dry`
- `typed-errors`
- `di-core`
- `network-storage`
- `ui-nav`
- `arch-lint`
- `testing-ci`
