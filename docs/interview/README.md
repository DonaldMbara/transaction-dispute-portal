# Technical Interview Preparation: 5 Core Architecture Guides

This directory contains 5 comprehensive, interview-ready README guides detailing each major service across the **Transaction Dispute Portal** stack (Spring Boot 3.5, Angular 20, PostgreSQL 17).

Each guide breaks down:
1. **Service Overview & ASCII Flowchart**
2. **Backend Implementation Details** (Controllers, Services, Mappers, Security, Business Rules)
3. **Frontend Interaction Mechanics** (Components, APIs, Interceptors, State Management, UI Guards)
4. **Database Handling & Schema Design** (PostgreSQL Tables, Indexes, Complex Constraints, Concurrency)
5. **High-Yield Interview Questions & Answers**

---

### Guide Index

| # | Guide | Primary Focus Areas |
|---|---|---|
| **01** | [01. Authentication & Session Security Service](./01-auth-session-security.md) | PBKDF2 Password Hashing (600k iterations), PostgreSQL-backed JDBC Sessions, Instant `auth_version` Invalidation, Defense-in-Depth CSRF Token Double-Submit, Rate Limiting |
| **02** | [02. Bank Account Management Service](./02-account-service.md) | Zero-Trust Principal Extraction, IDOR Prevention, PII Account Number Masking (`****6789`), Partial Unique Index for Single Primary Account Guarantee |
| **03** | [03. Transaction Ledger & Eligibility Service](./03-transaction-service.md) | Immutable Financial Ledger, Dynamic Dispute Eligibility Engine, Direction & Status Rules, Hexagonal Port Decoupling, Composite Index Cursor Pagination |
| **04** | [04. Customer Dispute Submission & Tracking Service](./04-customer-dispute-service.md) | SHA-256 Idempotency Replay Store, Pessimistic Row Locking (`FOR UPDATE`), Customer-Sanitized Timeline Mapping, Single Dispute Constraint |
| **05** | [05. Agent Dispute Triage & Resolution Service](./05-agent-dispute-service.md) | Triage Queue Architecture, Optimistic Concurrency Control (OCC) with HTTP `ETag` & `If-Match` (HTTP 412), State Machine Check Constraints, Segregated Resolution Notes |
