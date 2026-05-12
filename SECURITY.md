# Security Policy

  ## Supported Versions

  This repository contains the Bisq Relay service, including HTTP endpoints,
  push-notification delivery, APNS/FCM integration, deployment configuration, and
  monitoring support.

  Security fixes are applied to the active development branch and any deployed
  version currently operated for supported Bisq applications.

  | Version / Branch | Supported |
  | --- | --- |
  | `main` | :white_check_mark: |
  | Currently deployed Bisq Relay service versions | :white_check_mark: |
  | Active dependency-update or maintenance branches while under review | :white_check_mark: |
  | Old deployments, unsupported forks, or locally modified builds | :x: |

  Operators should deploy the latest reviewed version from the supported branch
  and rotate provider credentials if compromise is suspected.

  ## Reporting a Vulnerability

  Please do **not** report security vulnerabilities through public GitHub issues,
  pull requests, Matrix rooms, forums, or social media.

  Report suspected vulnerabilities privately through GitHub's **Report a
  vulnerability** flow on this repository's Security page. If that option is not
  available, open a minimal public issue asking maintainers to enable a private
  security reporting channel, but do not include exploit details.

  Include as much detail as possible:

  - affected branch, commit, deployment, endpoint, Docker image, or dependency;
  - affected component, such as `RelayController`, push notification controllers,
    APNS sender, FCM sender, metrics, request logging, masking, Docker
    configuration, or monitoring configuration;
  - whether the issue affects authentication or authorization, request validation,
    rate limiting, push-token handling, APNS/FCM credentials, notification
    contents, log redaction, metrics exposure, deployment secrets, or service
    availability;
  - whether the issue can expose user identifiers, push tokens, notification
    metadata, provider credentials, IP addresses, relay traffic patterns, logs, or
    operational secrets;
  - reproduction steps, HTTP requests/responses, logs with secrets redacted,
    configuration snippets, dependency findings, or proof of concept code where
    useful;
  - whether the issue depends on a malicious client, malformed request, leaked
    provider credential, misconfigured deployment, public monitoring endpoint,
    dependency vulnerability, or denial-of-service condition.

  Bisq is an open-source project maintained by contributors. Response times may
  vary, but reports involving credential exposure, unauthorized push delivery,
  push-token leakage, notification metadata leakage, log/metrics leakage, relay
  abuse, or denial of service against deployed relay infrastructure are treated as
  urgent security issues and will be triaged as quickly as possible.

  For lower-severity issues, maintainers will respond when contributor capacity is
  available.

  If the report is accepted, maintainers may coordinate a fix privately, prepare a
  patched deployment, rotate credentials, update dependencies, and publish an
  advisory after users and operators have had a reasonable opportunity to update.
  If the report is declined, maintainers will explain the reason when possible.

  Please give maintainers reasonable time to investigate and release mitigations
  before public disclosure. For severe or actively exploited issues, coordinate
  timing with maintainers so public details do not increase risk to users.

  Bisq does not currently guarantee a bug bounty. Security work may be eligible
  for Bisq DAO compensation if it qualifies under the project's contributor and
  critical-bug processes.
