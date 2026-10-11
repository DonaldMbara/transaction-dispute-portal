---
inclusion: always
---
# Product: Transaction Dispute Portal
A technical-assessment, production-minded modular monolith for customers to view seeded bank transactions, submit disputes, and track dispute history; agents claim, release, investigate, and resolve disputes.

## Actors
- CUSTOMER: access own accounts, transactions, and disputes; create disputes for eligible transactions.
- AGENT: access agent queue and case details; claim unassigned disputes, release own claims, resolve own claims.

## Scope
- Seeded synthetic banking data; admin-controlled account provisioning with no public registration. Accounts are created only through the explicitly invoked non-web operational procedure; never automatic startup seeds.
- Customer submission form requires a reason and explanation.
- Agent resolution requires internal investigation notes and a separate customer-safe outcome message.
- ACCEPTED means the dispute was upheld, **not** that funds were refunded or reversed.
- No external bank integrations, payments, refunds, attachments, notifications, supervisors, microservices, or production identity provider unless explicitly approved.
- Brand direction: Capitec public-web-inspired desktop-first responsive portal, #2F70EF primary buttons, white surfaces and #F7F7F7 background; use docs/design/ui-flows.md tokens. Original assessment branding.
- This is a production-oriented design; production-ready claims require implemented/tested controls and operational verification. No API Gateway required for the single backend.
- Follow requirements -> design -> tasks -> implementation and obtain developer approval at phase boundaries; review/design requests do not authorize application implementation.

## Product quality bar
- Complete usable customer and agent workflows, mobile-responsive customer UI, desktop-usable agent UI.
- Clear validation, loading, empty, error, accessibility and concurrency-conflict states.
- README must explain tradeoffs and how to run and test locally.
