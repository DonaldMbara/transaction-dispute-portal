---
inclusion: manual
---
# Spec-driven engineering contract
- Follow Kiro Specs sequence: requirements -> design -> tasks -> implementation.
- Requirements: user stories, EARS-style testable acceptance criteria, nonfunctional/security requirements, scope exclusions and edge cases.
- Design: component boundaries, data flow, persistence constraints, API contracts, security and concurrency choices; reference steering instead of duplicating it.
- Tasks: small, ordered, independently verifiable changes with tests and acceptance checks; explicit dependencies.
- STOP after each phase and request developer approval. Do not implement code from a requirements-only prompt.
- Before changing an approved architecture decision, identify tradeoff and request approval.
- Implement only the currently approved task; show changed files, test commands, results and outstanding risks.
- Do not introduce extra services, libraries, features, speculative refactors or UI redesigns without a requirement.
- Keep a short ADR in docs/ for meaningful deviations. Never invent test success.
- During feature design explicitly load relevant backend/frontend/API/testing steering and reference the canonical design documents; fileMatch guidance may not load for Markdown-only work.
- Feature order: project-foundation, authentication, customer-transactions, dispute-submission, customer-dispute-history, agent-workflow, integration-hardening.
