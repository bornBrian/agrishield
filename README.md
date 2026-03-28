# AgriShield

## Project Title
AgriShield — Secure Agricultural Input Verification and Distribution Integrity Platform

## Problem Statement
Counterfeit agricultural inputs reduce crop yield, damage soil health, and create financial losses for farmers and distributors. Traditional paper-based tracking and weak post-market verification make it difficult to identify fake products, trace batch ownership, and respond quickly to fraud.

## What AgriShield Solves
AgriShield provides a secure verification and oversight platform for regulated agricultural input supply chains.

- Verifies serialized products and detects suspicious reuse patterns.
- Supports role-based operations for admin, regulator, manufacturer, distributor, and dealer users.
- Restricts farmers from web portal access (farmers are mobile-app users).
- Enables strong authentication flows, including password + TOTP, Google sign-in, Apple sign-in, and password recovery.
- Improves operational trust through auditable events and controlled access paths.

## Core Capabilities
- Product verification logic with anomaly-aware handling.
- Session-based authentication with protected routes.
- Google and Apple social login entry points (demo-enabled frontend flow with production API-ready structure).
- Forgot-password lifecycle: request reset code and complete password reset.
- Role checks that prevent farmer login to web portal and preserve intended channel separation.

## Security Posture and Threat Resistance
AgriShield is designed with practical security controls to reduce common attack vectors:

1. Access control
   - Role-gated UI and API paths.
   - Non-farmer web enforcement to reduce unauthorized channel usage.

2. Authentication hardening
   - Multi-factor support (TOTP for sensitive roles).
   - Session handling with protected route checks.

3. Password safety
   - Password reset flow with expiring one-time reset code.
   - Strong password policy checks in reset operation.

4. Transport and platform hardening
   - Intended private hosting behind HTTPS reverse proxy (Nginx).
   - Security headers and controlled ingress recommended in deployment design.

5. Abuse resistance
   - Existing rate-limit and verification guard patterns in security/verify modules.

## Current Technical State
- Java runtime target upgraded to Java 25 (latest LTS requested).
- Maven build/test path validated on Java 25.
- Frontend auth flow now includes:

## Supabase Database Setup (Recommended)
- AgriShield now supports external PostgreSQL via environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- Run interactive setup from CLI:
   - `Set-Location "D:\agrishield"`
   - `.\setup-supabase.ps1 -RunMigration`
- Use Supabase JDBC URL format:
   - `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require`
- After setup, use the same DB variables in your backend hosting environment (Railway/Render/Fly/VPS).
  - Username/password login
  - Google login
  - Apple login
  - Forgot password and reset
  - Farmer web-login rejection

## Impact If Deployed Well
- Higher trust in input authenticity for stakeholders.
- Faster counterfeit detection and response.
- Stronger regulator/manufacturer accountability.
- Reduced fraud exposure and improved supply-chain traceability.

## Future Roadmap
1. Production OAuth/OIDC integration
   - Replace demo social-token acceptance with provider-side ID token verification:
     - Google: OpenID Connect token verification with issuer/audience checks.
     - Apple: Sign in with Apple JWT verification and key rotation support.

2. Password reset channel integration
   - Replace preview reset code with real SMS/email delivery via provider gateways.
   - Add anti-abuse controls (IP + identity throttling, CAPTCHA where needed).

3. Backend auth service completion in deployable WAR module
   - Implement `/api/auth/*` endpoints in server module with database-backed users and sessions.

4. Audit and monitoring
   - Centralized logs, alerting, and anomaly dashboards.

5. Mobile-first farmer experience
   - Farmer verification and account flows moved to Android/iOS apps with offline resilience.

## Private Hosting Blueprint (Recommended)
- Runtime: Java 25
- App container: TomEE/Tomcat
- Data: PostgreSQL + Redis
- Edge: Nginx reverse proxy + TLS (Let’s Encrypt)
- Security baseline: non-root deployment user, SSH keys only, restricted firewall, secret management, backups, and log monitoring

## Professional Portfolio Summary
AgriShield demonstrates secure application architecture, role-oriented access control, practical authentication design, and modernization to current LTS Java for production readiness.
