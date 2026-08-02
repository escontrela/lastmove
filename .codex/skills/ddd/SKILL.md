---
name: ddd-javafx-architecture
description: Design and scaffold layered Java architectures that combine Domain-Driven Design (DDD), JavaFX UI flow/navigation, and Spring-friendly bootstrapping. Use when asked to define or refactor package structures, write domain/application/infrastructure boundaries, implement JavaFX window management (including FXMLLoader controller injection), or provide migration/testing guidance for desktop-business Java apps.
---

# DDD JavaFX Architecture

## Overview

Use this skill to produce pragmatic, reusable DDD architecture guidance for Java projects with JavaFX and optional Spring Boot integration. Keep domain code framework-free, keep adapters thin, and centralize JavaFX scene/window orchestration.

## Workflow

1. Capture project constraints.
Collect package root, Java version, dependency policy (Spring or plain Java), persistence strategy (JPA/JDBC), and JavaFX constraints (single stage, modal usage, scene caching).

2. Define package layout by intent.
Propose packages that separate `domain`, `application`, `api/ui`, `infrastructure`, and `bootstrap`. Keep package names stable and avoid mixing framework annotations into domain classes.

3. Define core contracts first.
Write domain entities/value objects and domain ports (repository interfaces) before implementations. Add immutable commands/DTOs in application layer and use-case interfaces plus services.

4. Design adapter boundaries.
Keep REST controllers and JavaFX controllers thin. Translate transport/UI payloads into application commands and return simple responses/DTOs.

5. Centralize JavaFX flow.
Implement a `UiFlowManager` that owns scene transitions, modal dialogs, FXML loading, and controller factory setup when Spring DI is used.

6. Wire bootstrap and configuration.
Provide launcher/configuration that initializes DI context, stage lifecycle, and JavaFX loader integration. Keep wiring explicit and testable.

7. Specify infrastructure and verification.
Place repository implementations, migration files, and integration tests under infrastructure conventions. Recommend unit tests for domain/use-cases and focused integration tests for persistence.

## Output Rules

- Return concrete package trees and minimal compilable snippets.
- Use records for immutable DTOs/commands when supported by the JDK.
- Call out tradeoffs explicitly when multiple valid options exist (Spring-managed controllers vs manual construction, cached vs uncached scenes).
- For large responses, reference `references/ddd-javafx-guide.md` sections instead of repeating all examples.

## Resources

- Reference guide: `references/ddd-javafx-guide.md`
- Use the reference file when the user requests templates for entities, repositories, use-cases, JavaFX flow manager, Spring wiring, Flyway, or testing strategy details.
