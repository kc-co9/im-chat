# Web OpenAPI And IAM Introspection Design

## Context

Servlet MVC applications already consume `im-web`, while `im-message-server` separately declares Springdoc and IAM Server currently exposes no API documentation endpoint. This duplicates a generic Web dependency and leaves documentation availability inconsistent.

The Audit BFF also enters a login loop after a successful OAuth callback. Spring Authorization Server performs Introspection through `OAuth2AuthorizationService.findByToken(rawToken, null)`. The current authorization transformer restores the same raw value into every stored credential when the requested type is unknown. Spring can therefore resolve the submitted Access Token as an already consumed authorization code and return `active=false`.

## Decision

`im-web` owns the common Springdoc WebMVC UI dependency. It does not own service-specific paths, scanning packages or security exposure. Each consuming application keeps those choices in its own configuration and SecurityFilterChain. IAM Server explicitly depends on `im-web` and exposes its authenticated documentation UI at `/api/doc.html`; `im-message-server` removes its duplicate Springdoc declaration.

When rebuilding a Spring `OAuth2Authorization` for an unknown requested Token type, the transformer compares the submitted raw Token with each stored digest. Only the matching credential receives the raw value; all other credentials retain their stored digest placeholders. Explicit Token type lookups keep their current behavior. Spring's default Introspection Provider remains the protocol owner.

JSON persistence cannot retain the runtime type of values in `Map<String, Object>`. The Spring authorization transformer therefore restores standard Token time Claims to `Instant` while rebuilding `OAuth2Authorization`: authoritative `iat` and `exp` values come from the credential period, while a persisted `nbf` string or epoch value is converted explicitly. The database transformer preserves the JSON representation without depending on Spring's runtime Claim contract.

## Security and compatibility

Adding Springdoc to `im-web` makes documentation support available to Servlet MVC consumers but does not make any route anonymous. The consuming application's security policy remains authoritative. IAM documentation therefore requires an authenticated IAM administrator Session.

The Introspection fix does not change Token format, digest storage, client registration or Claims. It only restores the correct credential identity for Spring's standard lookup contract.

## Verification

- A configuration test proves IAM has Springdoc and the approved documentation paths.
- An adapter test proves unknown-type lookup restores the submitted Access Token only as an Access Token.
- Plugin, IAM and Message tests verify dependency consolidation and behavior.
- Repository quick and full verification remain the completion gates.
