# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.2] - 2025-11-17

- Changed plugin implementation type from `access_token` to `access-token`.

## [1.0.1] - 2025-11-05

### Security

- Enforce stricter OAuth client verification - only confidential, authenticated clients are now allowed to perform
  authorization.

## [1.0.0] - 2025-11-05

### Added

- Initial commit.

### Technical Details

- Built with Kotlin.
- Requires Java 21 or newer
- Compatible with Curity Identity Server 10.4.2
- Uses jose4j for JWT validation
- Jakarta Validation API 3.0.0 for configuration validation

[Unreleased]: https://github.com/curityio/access-token-authenticator/compare/v1.0.0...HEAD

[1.0.0]: https://github.com/curityio/access-token-authenticator/releases/tag/v1.0.0

