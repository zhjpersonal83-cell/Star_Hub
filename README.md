# Star Hub

A bilingual workspace for calling official AI model APIs from one website. Built with Java, plain HTML/CSS/JavaScript and a WAR deployed in an existing Tomcat 9 runtime.

[Open Star Hub](https://starhub.zhongjiehuang.com) · [Portfolio](https://www.zhongjiehuang.com/projects/star-hub/) · [Model catalogue](docs/model-catalog.md) · [Verification notes](docs/verification.md)

## What you can do

- Choose from 7 official providers and 60 integration entries, including 4 batch entries: OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Alibaba Qwen, Moonshot Kimi and xAI.
- Chat with progressively revealed streaming replies and use image understanding where supported.
- Generate images or speech, transcribe audio and create embeddings through the implemented OpenAI endpoints.
- Submit OpenAI JSONL batch jobs, recover paginated job lists, inspect status, retrieve output/error files and export results.
- Use OpenAI realtime voice with microphone input, audio playback, interruption and explicit session controls.
- See only the settings supported by the selected model, with a second validation step on the server.
- Inspect official API addresses, key-management links and the actual Java/JavaScript call source in a normally closed, read-only viewer.

The current catalogue uses the manufacturers' own official APIs. Third-party model gateways, hosted copies of other manufacturers' models and custom endpoint URLs are rejected by the backend. Unsupported video, music, 3D and other former gateway-only integrations are not presented as callable features.

Every current integration requires the user's own provider API key. Account access, regional availability, free credits and billing are controlled by the provider. A catalogue entry does not guarantee that every account can call it or that every model is covered.

## Getting started

1. Open the website. English is the default; switch to Chinese in the guide or page header.
2. Choose an official provider, follow its key-management link and enter your own API key.
3. Check the connection and select a model. Connection details can stay collapsed while the API address and source viewer remain accessible.

Language, connection-panel state and the last model selected for each provider are remembered in the current browser. An unavailable saved model falls back to a current selection.

The connection check only requests the official model list; it does not generate paid text or media. Acceptance of a key does not establish model entitlement or sufficient balance. Qwen's Beijing model-list check needs an additional workspace address and is explicitly shown as unavailable. Network or unsupported-check responses can be skipped; invalid-key responses require a corrected key.

## Privacy and inspectable calls

The request path is **browser → this site's Java service → the selected manufacturer's official API**. An explicit destination allowlist is used and upstream redirects are disabled.

API keys stay in page/request memory and must be entered again after refresh. They are excluded from chat persistence. Optional local chat saving is **off by default**; when enabled it stores conversation text, titles and drafts in that browser. Attachments, keys and raw media results are excluded. Disabling saving deletes the stored history copy while retaining the current page's conversations. Individual deletion and undo are available.

Saved text is limited to approximately 2 million characters overall and 500,000 per message. Storage failures or oversized conversations do not replace the previous successful save; the interface offers export. Clearing site data removes local preferences and saved chats. Batch files and jobs are stored in the user's OpenAI account.

The source viewer exposes 24 actual Java and JavaScript files with SHA-256 fingerprints. Maven bundles the current files unchanged under `WEB-INF/classes/call-source/`. `GET /api/source` lists them; `GET /api/source?file=server/ModelGateway.java` reads an allowlisted file. The endpoint does not read arbitrary server files, environment variables or credentials.

## Build and test

Requirements: JDK 11+, Python 3, Maven 3.9+ and Node.js 20+ for frontend tests. The build helper can use an installed PyCharm JBR and the included `.tools/apache-maven-3.9.9` distribution. These are local build tools; deployment shares the existing Tomcat JVM.

```sh
./scripts/build.sh
```

Output: `target/starhub.war`. The build regenerates the catalogue from `research/official-models.json`, validates official-provider ownership, runs tests and packages the application and inspectable source.

The latest release passed **39 Java tests and 6 JavaScript tests**. They cover protocol adapters, streaming, official routing, rejected gateways, batch/file workflows, realtime bridging, parameter constraints, source access, connection checks, local storage, language and model preferences. Tests use simulated upstream services rather than paid provider credentials. See [verification notes](docs/verification.md) for the distinction between automated, browser and live-provider evidence.

## Deploy in the existing Tomcat 9

The deployment helper assumes Nginx, Tomcat 9 and the domain certificate already exist. Upload the current files to a staging directory:

```sh
scp target/starhub.war scripts/deploy-server.sh deploy/nginx.conf deploy/starhub-proxy.conf root@SERVER:/opt/starhub/staging/
ssh root@SERVER 'bash /opt/starhub/staging/deploy-server.sh /opt/starhub/staging'
```

The script backs up the current release, stops Tomcat 9, clears Tomcat file logs, installs this WAR, validates Nginx, waits 30 seconds, starts Tomcat, checks health and reloads Nginx. It rolls back on failure. Stopping the shared Tomcat temporarily interrupts its other applications; only the Star Hub WAR is replaced.

Health: `GET /api/health`. JSON endpoints `POST /api/run`, `POST /api/batch` and `POST /api/check` require `X-Starhub-Client: web`. `STARHUB_PUBLIC_ORIGIN` can configure the permitted site origin; it cannot add third-party API destinations.

## Repository contents

| Path | Purpose |
| --- | --- |
| `src/main/java/com/starhub/` | Servlets, provider protocols, streaming, media, batches, realtime voice, parameter validation and source access |
| `src/main/webapp/` | Bilingual interface, styles and browser interactions |
| `src/main/resources/` | Generated catalogue and official platform links |
| `src/test/` | Java and JavaScript regression tests |
| `research/official-models.json` | Reproducible model catalogue with per-model settings and references |
| `scripts/` | Catalogue generation, build and deployment |
| `deploy/` | Nginx and certificate-renewal configuration |
| `docs/` | Current catalogue, settings and verification notes |
| `.tools/` | Maven build distribution, including its third-party notices |
| `target/starhub.war` | Current deployment artifact |

This repository mirrors the current local project files, including the build tools and latest WAR. Transient build output, operating-system metadata, logs and private environment files are excluded. English documentation accompanies the deliberately bilingual application.
