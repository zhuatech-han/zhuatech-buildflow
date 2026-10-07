[中文](README.md) | [English](README.en.md)

<p><img src="frontend/public/brand/logo.jpg" height="56" alt="ZhiHua Technology logo"></p>

# BuildFlow · Construction Project Costs and Settlement

**From contract schedules and site acceptance to phased settlement: check each project's quantities and amounts.**

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/) · **1.0.0 public source for learning / non-commercial use**. Commercial use requires prior written authorization under [LICENSE](LICENSE); this is not an OSI-approved license.

## Intended teams and workflow

For refurbishment, mechanical/electrical installation and small construction contractors, and software implementation teams. Java 21 / Spring Boot, Vue 3, MySQL and Flyway support one enterprise, multiple departments/projects, Chinese/English, mobile site input and a personally bound customer view. Core workflows need no third-party account.

Managers create contract quantities/subcontract rates; site members submit completed quantities/photos; other people independently accept; finance approves customer/subcontract bills based on accepted quantities and records completed offline receipts/payments. Unapproved changes, unaccepted work, billed quantities and outstanding amounts remain distinguishable.

```text
Draft contract/work schedule → activate frozen rates → quantities/photos → independent acceptance
                         ↑                                                → customer/subcontract bills
                independently approved variations                         → partial payments/retention release
Direct cost evidence → independent finance review                         → fully accepted and settled closure
```

### Implemented features

| Module | Operations |
|---|---|
| Contracts | Draft CRUD, department/manager/customer bindings, dates, draft work schedule CRUD, activation/freeze, quantity/budget variations |
| Team | Explicit site membership, immediate access withdrawal on removal |
| Work/acceptance | Quantities/dates/notes, actual PNG/JPEG uploads and independent accept/return; pending plus accepted quantities cannot exceed contract |
| Billing | Separate customer/subcontract progress billing, frozen rates, independent review, retention and evidence-based release |
| Cash records | Completed external transactions, partial payments, linked refund/reversal, exact retries and retained original history |
| Costs | Independent direct-cost approval, approved subcontract costs, planned/recognized margins, receivables/payables and CSV |
| Role interfaces | Manager/site/finance/customer; customer/site API responses also redact internal cost/subcontract fields |
| Administration | Accounts, roles, registered permission/menu descriptions, departments, cost dictionaries/settings, search/pagination, last-admin protection and audit |
| Deployment/security | MySQL persistence, Flyway, sessions/CSRF, project locks/versions, non-root applications and health checks |

### Known scope

One enterprise/currency and bounded small projects. Rates freeze at activation; approved variations adjust existing work quantities/other budgets. New work types or repricing require a separate project. Approved evidence is not overwritten. No deposits, materials purchasing/inventory, Gantt dependency scheduling, ERP/BIM/CAD, electronic signatures, tax invoices, payment gateways, currency conversion, offline synchronization or large-capacity guarantees. Interface labels are bilingual; business text is not automatically translated.

**Cash registration never moves actual funds.** Bills are not tax invoices. Retention percentages are manually agreed contract inputs, not preset legal rules. Recognized margin includes reviewed direct costs/subcontract bills, excluding overhead, taxes and unrecorded costs; it is not statutory financial reporting. No customer-adoption, revenue or certification claims are made.

## Actual running pages

Screenshots use synthetic TEST records in the running application, without actual customer data or mockups.

| Login | Role workspace |
|---|---|
| ![Login](docs/images/screenshots/login.jpg) | ![Workspace](docs/images/screenshots/workspace.jpg) |

Login: session authentication. Workspace: role/project-specific entry points and work.

**Contract quantities and acceptance**

![Work schedule](docs/images/screenshots/work-items.jpg)

Work schedule: frozen rates, approved quantities and site acceptance.

**Billing, retention and cash**

![Settlement](docs/images/screenshots/settlement.jpg)

Settlement: customer/subcontract quantities, retention and actual cash history.

**One bill preview**

![Bill preview](docs/images/screenshots/bill-preview.jpg)

Bill preview: the selected bill's quantities, net payments and balance.

| Accounts | Project costs |
|---|---|
| ![Accounts](docs/images/screenshots/accounts.jpg) | ![Reports](docs/images/screenshots/reports.jpg) |

Accounts: departments, roles and enabled state. Reports: accepted revenue, direct/subcontract costs and outstanding amounts.

**Roles and scopes**

![Roles](docs/images/screenshots/roles.jpg)

Roles: interface permissions, departments and actual project associations.

<img src="docs/images/screenshots/site-mobile.jpg" height="440" alt="Mobile site interface">

Mobile site: personally assigned quantities and image submissions in a narrow layout.

## Requirements, installation and first use

Docker/Compose v2 and Python 3. Source development needs Java 21 / Maven 3.9, Node 24.19.0+ / npm and MySQL 8.4. Backend: Spring Boot 4.0.7, Security, JPA and Flyway; MariaDB JDBC 3.5.10 accesses MySQL. Frontend: Vue 3.5.40 / Vite 8.1.5 / Nginx. Exact dependencies are in manifests/lock files; initial builds require public registries.

```sh
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
```

Open [http://127.0.0.1:8104/](http://127.0.0.1:8104/); [health](http://127.0.0.1:8104/actuator/health). Change private `WEB_PORT` if occupied; leave other projects running. Default binding is loopback.

Username `admin`; read `ADMIN_PASSWORD` in your ignored `.env`. The generator creates strong random passwords with mode 0600 and refuses to overwrite existing configuration; start directly if it exists. No fixed public password is provided. An empty database initializes roles, permissions, menus, dictionaries/settings and the administrator, without customers/contracts. Restarts do not reset accounts. Create manager, site, finance and optional customer accounts, then contracts/work and explicit members. See [Operations](docs/manual.md); detailed linked manuals are currently in Chinese.

### Configuration

| Name | Purpose |
|---|---|
| `MYSQL_ROOT_PASSWORD` / `DATABASE_PASSWORD` | Independent strong database passwords; example values are empty |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | First administrator initialization only |
| `WEB_PORT` / `BIND_ADDRESS` | Defaults 8104 / 127.0.0.1 |
| `COOKIE_SECURE` | true for HTTPS, false for local HTTP |
| `DATABASE_URL` / `DATABASE_USER` | Optional external MySQL in private controlled configuration |

See [.env.example](.env.example). MySQL/backend have no published host ports. Host development needs an independent database or controlled loopback mapping; inject connection/account/password/admin values without putting them in history/source:

```sh
mvn -f backend/pom.xml spring-boot:run
```

In another repository-root terminal:

```sh
cd frontend
npm ci
npm run dev
```

Vite proxies local backend port 8080 in development. Production Nginx uses backend service names, without hardcoded localhost addresses in the client. Host source execution does not automatically read `.env`.

## Architecture, structure and database

```text
backend/  cn.zhuatech.buildflow: identity, projects, acceptance, costs and settlement
frontend/ Vue role interfaces, mobile site and administration
scripts/  Private initialization, actual HTTP acceptance and release checks
docs/     Operations, API, database, security, tests and deployment
```

Browser → same-origin Nginx → Spring Security/services → JPA/MySQL. Database `zhuatech_buildflow` has 18 application tables plus Flyway history; [V1 schema](backend/src/main/resources/db/migration/V1__build_schema.sql) defines foreign keys, unique/index and quantity/amount constraints. Images are database binaries restored with other facts, not private-computer file references. See [Database](docs/database.md) and [Architecture](docs/architecture.md).

Project writes lock the project and check revision. Pending/accepted quantities share capacity; customer/subcontract billed quantities separately cannot exceed acceptance. Frozen rates determine bill amounts, rounded HALF_UP per line. BigDecimal handles amounts; quantities allow four decimals, money two. Site accounts read assigned work without prices/finance; linked customers read own customer rates/bills without subcontract/internal costs. APIs/downloads enforce filtering, not just menus.

Dates/timestamps use UTC; site-date checks use UTC today, without an enterprise multi-timezone construction calendar. Lists are bounded at 10,000 per type with client search/sort and ten-row pages. This is not unlimited server pagination or distributed throughput.

## Testing

From the repository root:

```sh
mvn -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
python3 scripts/release-check.py
git diff --check
```

Docker image builds execute backend tests. Isolated H2 unit/HTTP integration uses dynamic credentials and checks decimal/quantity/retention rules, full approval/cost/billing/cash workflows, independence, duplicate measurement, versions, scoped response privacy, membership withdrawal, CSRF, refund/reversal retries, attachments and last-admin protection. H2 is not actual MySQL acceptance; see [Testing](docs/testing.md).

For a **dedicated disposable local database**, inject its private administrator password through controlled environment configuration and run:

```sh
BASE_URL=http://127.0.0.1:8104 ALLOW_TEST_DATA=1 python3 scripts/smoke.py
```

`ADMIN_PASSWORD` must already be present in that process environment; do not type it into history or publish it. The script writes TEST records and stores private test state in ignored output, so never target a business database. Photo upload validation uses explicitly synthetic input, not an assertion of genuine site evidence. Separate restart/restoration checks must reauthenticate roles and compare projects, quantities, bills, entries and original image bytes/checksums. The existing smoke script has no persistence-verify option.

## Deployment, upgrades and backup recovery

See [Deployment](docs/deployment.md). Before upgrading, pause writes and securely retain image/configuration versions and a complete MySQL backup. Use restricted private storage outside public source, mode 0600. Include `--hex-blob` in logical backups to retain original images. Backups contain account hashes, customer/contract information and evidence; do not print credentials/content or upload them.

Restore into a new Compose project, separate web port and fresh volume. Start MySQL, import the complete backup before application initialization, then start matching backend/frontend images. Verify Flyway, logins, projects, work acceptance, bills, cash histories and image checksums. Validate upgrades on restored copies first. Add higher migrations; never modify applied V1, delete history or use repair to conceal differences. JPA validates only. Rollback requires matching application/database versions and a verified preupgrade backup.

External hosting needs authorization, HTTPS, trusted access/proxies, secure cookies, least privilege, independent strong secrets, verified database certificates and backup monitoring. Named volumes do not supply automatic backup. `docker compose down` retains data. **Remove volumes only for explicitly disposable test resources after checking project labels.**

## Troubleshooting and operational limits

Startup: service health/logs, required configuration and MySQL version. Quantity conflicts: pending/accepted/billed quantities. Independent review: another authorized account. Version conflict: refresh/review. Closure: full acceptance/billing, no pending review, released retention and zero balances. Never delete original cash entries or other containers/volumes to balance records. [API](docs/api.md) describes errors/scopes.

Refunds link original payments and stay within their remaining net amounts. Full reversals require the exact original amount and valid downstream/balance constraints; original entries with downstream refunds/reversals cannot be overwritten. Exact account/key/payload retries do not duplicate entries. Retention releases require full contract acceptance and actual release evidence. Project closure prevents new business entries.

Protect `.env`, sessions, actual customer/site images and backups from source control. See [Security](docs/security.md). Software does not guarantee actual construction quality, funds, statutory accounting or unverified production suitability.

## License, feedback and contact

Own code uses [ZhuaTech Non-Commercial Source License 1.0](LICENSE), permitting personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** Private deployment, paid delivery, resale, SaaS and commercial customization require authorization. Preserve attribution, website, copyright, license and licensing contacts. This is publicly readable non-commercial source, not an OSI-approved license. Third-party rights remain separate; see [Notices](docs/third-party.md).

Submit redacted reproducible issues/tested contributions; privately report security concerns through official contacts. See [Contributing](docs/contributing.md). Software is provided as is and actual commercial services are governed by written agreement; no unverified production-readiness claim is made.

For commercial licensing, in-depth custom development, deployment or system integration, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
