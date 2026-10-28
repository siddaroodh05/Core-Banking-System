# Core Banking System

A backend-focused core banking system built with **Java and Spring Boot**, organized as multiple services for authentication, account management, transaction processing, shared security, and API gateway routing.

The project focuses on backend engineering concerns such as **authentication, role-based authorization, transaction processing, data consistency, concurrency control, and service separation**.

A **React frontend** is included so the whole system can be used end to end: customer onboarding, balance checks, money transfers, a passbook, and a staff "bank desk". See [Frontend](#frontend).

## Architecture

```text
                  Frontend (React + Vite)
                           |
                           v
                    +--------------+
                    | API Gateway  |
                    +--------------+
                           |
             +-------------+-------------+
             |             |             |
             v             v             v
       +-----------+ +-----------+ +------------------+
       |   Auth    | |  Account  | |   Transaction    |
       |  Service  | |  Service  | |     Service      |
       +-----------+ +-----------+ +------------------+
             |             |             |
             v             v             v
       +-----------+ +-----------+ +------------------+
       | auth_db   | | account_db| |  transaction_db   |
       +-----------+ +-----------+ +------------------+

                    +----------------+
                    | Common-Security|
                    +----------------+
```

The repository currently contains:

* `Auth-Service`
* `Account-Service`
* `Transaction-services`
* `Common-Security`
* `Api-gate-way`
* `database-Schemas`
* `frontend`
* `demo`
* `docker-compose.yaml`

## Services

### Auth Service

Responsible for authentication and session-related functionality.

The authentication layer uses:

* JWT access tokens
* Refresh tokens
* Refresh-token expiration
* Session-based refresh-token handling
* Role-based authorization
* Secure cookie-based token handling

### Account Service

Handles account and customer-profile functionality.

The service contains functionality related to:

* Bank account creation
* Account information
* Customer profiles
* KYC-related operations
* Account status
* Administrative account/profile operations
* Account balance handling

### Transaction Service

Handles financial transaction processing.

The transaction layer includes functionality for:

* Payment/transfer processing
* Transaction history
* Transaction status handling
* Failed, pending, and successful transaction flows
* Transaction pagination
* Transaction-related validation and exceptions

The transaction implementation was structured around separate lifecycle services to keep pending, successful, and failed transaction handling distinct.

### Common Security

Shared security functionality is maintained in a separate module so that security-related components can be reused across services.

This module contains the common authentication/authorization infrastructure used by the backend services.

### API Gateway

The API Gateway acts as the entry point for client requests and routes requests to the appropriate backend service.

It also participates in JWT-based authentication and route-level authorization.

### Rate Limiting

The API Gateway enforces per-user rate limiting, backed by Redis, to protect backend services from abuse and accidental request storms.

**Configuration**
- **Limit:** 25 requests per user per 2-minute rolling window
- **Scope:** Enforced by authenticated user ID (extracted from the JWT), not by IP — so the limit follows the user regardless of network/device
- **Storage:** Redis, used to track per-user request counts with automatic expiry
- **Enforcement point:** API Gateway, before requests are routed to downstream services

**Why Redis:** rate limiting needs fast, shared counters that expire automatically — Redis is in-memory, supports atomic increment operations (`INCR`), and lets multiple gateway instances share the same rate-limit state consistently (important if the gateway is ever scaled horizontally, since an in-memory counter per instance wouldn't be consistent across replicas). TTL-based expiry also means the rolling window resets cleanly without needing a separate cleanup job.

**Behavior**
| Condition | Response |
|---|---|
| Under limit | Request proceeds normally |
| Over limit | `429 Too Many Requests` with a structured error body |

[📹 Watch rate-limit demo](demo/rate-limit-demo.mp4)

## Frontend

The `frontend/` folder contains a React single-page app, **Siddu Bank**, that exercises the whole backend end to end through the API Gateway. It is deliberately dependency-light: React 18 and Vite 5 only, with no UI library, router library, or state-management library.

### Customer features

| Area | What it does |
|---|---|
| Sign up / sign in | Register with email and password (live password-rule checklist), verify the email with a 6-digit OTP, then sign in. Unverified users who try to sign in are sent to the OTP screen. |
| Onboarding | New customers submit their KYC profile (name, date of birth, gender, Aadhaar, mobile, address) and then open a first account by choosing a type, a branch (IFSC) and a 6-digit PIN. |
| Overview | Passbook-style cover for the primary account, the five latest entries, and a banner while KYC is pending or rejected. |
| Accounts | Lists every linked account, lets the customer pick a primary account (remembered per user), and opens additional Savings or Current accounts. |
| Check balance | PIN-gated. The balance is shown for 20 seconds and then hidden again. |
| Send money | Three steps: details (with live receiver verification), PIN confirmation, then a status page with the receipt. |
| Passbook | Paginated transaction history, newest first, shown from the selected account's point of view (paid to / received from). Each entry opens a receipt. |
| Profile | View KYC status and linked accounts; edit name and address. |

### Bank desk (staff)

Users with the `ADMIN` or `MANAGER` role get a **Bank desk** with four tabs:

* **Customers:** paginated user list with status changes (Active, Deactivate, Blocked, Dormant). Admins can also assign and revoke roles (User, Support, Manager, Admin).
* **KYC review:** queue filtered by Pending, Verified or Rejected, plus search by Aadhaar number. Verify and Reject actions are available to admins on the profile returned by the Aadhaar search.
* **Accounts:** all accounts, filtered by Savings or Current.
* **Branches:** list of branches; admins can create new ones.

Staff who have no customer profile land directly on the desk instead of the onboarding flow.

### How the frontend talks to the backend

* **Single entry point:** every request goes to the API Gateway (`VITE_API_BASE_URL`, default `http://localhost:8080`) using `fetch` with `credentials: 'include'`.
* **Tokens stay out of JavaScript:** access and refresh tokens live in HttpOnly cookies. The only thing the app keeps client-side is the signed-in email and roles (in `sessionStorage`), because the auth service has no "who am I" endpoint.
* **Silent refresh:** when a non-auth request returns `401`, the client makes one shared `POST /Auth/refresh` call (concurrent requests wait on the same refresh) and replays the original request once. If that still fails, the session is cleared and the user is sent to the sign-in page.
* **Rate limiting:** a `429` from the gateway is shown as a friendly "too many requests" message.
* **Error handling:** all failures are normalised into an `ApiError` carrying the status, message and response body.

Endpoints used, grouped by service:

| Service | Endpoints |
|---|---|
| Auth | `POST /Auth/login`, `/Auth/register`, `/Auth/send-otp`, `/Auth/verify-otp`, `/Auth/refresh`, `/Auth/logout` |
| Auth (staff) | `GET /admin/auth/users`, `PATCH /admin/auth/users/status`, `POST /admin/auth/users/roles/assign`, `POST /admin/auth/users` (revoke role) |
| Account | `GET /profile/me`, `POST /profile/create-bank-Profile`, `POST /profile/name`, `PUT /profile/address` |
| Account | `GET /accounts/me`, `POST /accounts/create-bank-account`, `POST /accounts/check-balance`, `GET /accounts/internals/branches`, `POST /accounts/internals/verify-receiver` |
| Account (staff) | `GET /admin/accounts`, `GET /admin/accounts/profiles/kycstatus`, `POST /admin/accounts/kyc/search`, `PUT /admin/accounts/kycstatus/verify`, `PUT /admin/accounts/kycstatus/reject`, `POST /admin/accounts/branches` |
| Transaction | `POST /payment-transactions/transfer`, `POST /payment-transactions/history` |

### Idempotent transfers from the UI

The transfer flow is built to make a double payment impossible from the client side as well as the server side:

1. The user enters the transfer details. The receiver's 12-digit account number is checked against the backend as they type, and the form only continues when the account is active.
2. The details are kept as a draft in `sessionStorage` (the PIN is never stored) and the user moves on to the PIN step.
3. On confirmation, a fresh idempotency key (a UUID) is generated and the request is started. The user lands on `/transfer-status/<key>`.
4. If the request fails without a usable response (for example a timeout), the status page offers **Retry same payment**, which re-sends the same key so the backend can recognise the repeat.
5. The result is saved in `sessionStorage` so refreshing the page keeps the receipt. If the in-flight attempt is lost on refresh, the page tells the user to check the passbook before trying again.

Receipts distinguish **Success**, **Pending** (with a warning not to send the payment again) and **Failed** (with the failure reason from the backend). A failed response that still carries transaction details is rendered as a receipt rather than a generic error.

### Security-minded UI choices

* Tokens are never readable from JavaScript (HttpOnly cookies).
* Balances are never returned with the account list. They are fetched only after PIN verification and hidden again after 20 seconds.
* PIN fields are password inputs with autocomplete off, and PINs are never written to browser storage.
* Account numbers are masked (for example `••1234`) in account selectors and confirmation prompts.
* Staff-only screens and actions are hidden from other roles. This is a convenience only: authorization is enforced by the gateway and the backend services.
* Client-side validation (digit counts, password rules, minimum lengths) is for fast feedback and is not a security boundary.

### App structure

* **Routing:** a small hash-based router (`router.jsx`), so no server-side route configuration is needed. Route guarding lives in `App.jsx`: signed-out users only see sign-in, register and OTP screens; signed-in customers without a profile or account are redirected to onboarding; staff without a profile go straight to the desk.
* **State:** three React contexts. `AuthContext` holds the signed-in user, `BankContext` loads the profile and accounts once and tracks the primary account, and `ToastContext` shows notifications.
* **Styling:** plain CSS split into design tokens, base, layout, components and pages, with light and dark colour schemes.

| Route | Page |
|---|---|
| `/login`, `/register`, `/verify-otp` | Sign in, sign up, email OTP verification |
| `/onboarding` | KYC profile and first account |
| `/` | Overview |
| `/accounts`, `/accounts/create` | Account list, open a new account |
| `/balance/:account`, `/balance-result/:account` | PIN entry and balance result |
| `/transfer`, `/transfer-pin`, `/transfer-status/:key` | Send money flow |
| `/passbook`, `/transaction/:id` | History and receipt |
| `/profile` | Profile and KYC status |
| `/admin` | Bank desk (staff only) |

### Running the frontend

You need **Node.js 18 or newer** (required by Vite 5) and the backend running with the API Gateway on port `8080` (see [Docker](#docker)).

```bash
cd frontend
npm install
npm run dev
```

The app is served at `http://localhost:5173`.

| Command | Purpose |
|---|---|
| `npm run dev` | Start the Vite dev server on port 5173 |
| `npm run build` | Create a production build in `frontend/dist` |
| `npm run preview` | Serve the production build locally |

**Configuration:** `frontend/.env` holds a single setting.

```text
VITE_API_BASE_URL=http://localhost:8080
```

**CORS:** the API Gateway only accepts browser requests (with credentials) from the origins listed in its CORS configuration, which currently covers `http://localhost:5173` and one LAN address. If you serve the frontend from a different host or port, add that origin to `Api-gate-way/src/main/java/com/siddu/apigateway/Config/SecurityConfig.java`.

The frontend is not part of `docker-compose.yaml`; it runs separately with the Vite dev server.

## Key Engineering Areas

### Authentication & Authorization

The system uses JWT-based authentication with separate access and refresh token lifecycles.

Refresh-token handling includes token rotation and server-side session state.

Role-based access control is used to restrict administrative functionality.

### Transaction Consistency

Financial operations require careful handling of concurrent updates and failures.

The project uses Spring transaction management and optimistic locking for concurrent account updates.

The transaction service also separates different transaction states into dedicated lifecycle handling.

[📹 Watch optimistic locking demo](demo/concurrency-demo.mp4)

### Idempotent Transaction Processing

Transaction processing includes idempotency handling to prevent duplicate processing of repeated requests.
 
[📹 Watch Idempotent Request Handling demo](demo/idempotency-demo.mp4)

### Failure Handling

The services use validation and centralized exception-handling mechanisms to return controlled responses when operations fail.

### Service Separation

Authentication, account management, transaction processing, shared security, and gateway routing are separated into independent modules/services.

This keeps responsibilities isolated and allows each area to evolve independently.

## Technology Stack

| Category         | Technologies               |
| ---------------- | -------------------------- |
| Language         | Java                       |
| Backend          | Spring Boot                |
| Security         | Spring Security, JWT       |
| Persistence      | Spring Data JPA, Hibernate |
| Database         | PostgreSQL                 |
| API              | REST                       |
| Architecture     | Microservices              |
| Gateway          | Spring Cloud Gateway       |
| Rate limiting    | Redis                      |
| Frontend         | React 18, Vite 5, plain CSS |
| Containerization | Docker, Docker Compose     |
| Build            | Maven (backend), npm (frontend) |
| Development      | IntelliJ IDEA, Postman     |

## Database Architecture

The Docker Compose configuration defines separate PostgreSQL databases for the major services:

```text
auth_db
account_db
transaction_db
```

Each service connects to its corresponding database.

This keeps authentication, account, and transaction data separated at the service level.

## Docker

The repository contains Dockerfiles for the backend services and a root `docker-compose.yaml` for running the system as a multi-container setup.

The Compose configuration includes:

* PostgreSQL containers
* Auth Service
* Account Service
* Transaction Service
* API Gateway
* Redis (used by the gateway for rate limiting)
* PostgreSQL health checks
* Service startup dependencies

The frontend is not part of the Compose stack; see [Running the frontend](#running-the-frontend).

### Running with Docker Compose

Compose reads its secrets from a git-ignored `.env` file in the repository root. It expects these variables:

```text
POSTGRES_PASSWORD
JWT_SECRET_KEY
JWT_ACCESS_TOKEN_EXPIRATION
JWT_REFRESH_TOKEN_EXPIRATION
MAIL_USERNAME
MAIL_APP_PASSWORD
```

The mail credentials are used by the Auth Service for the signup verification email.

Make sure Docker Desktop is running, then from the repository root:

```bash
docker compose up
```

The API Gateway is configured to expose port:

```text
8080
```

> The exact Docker image build/run workflow should match the current Dockerfiles and local environment. If images have not been built yet, build the required service images before starting the Compose stack.

## Running Individual Services

Each Spring Boot service contains its own Maven wrapper and can be run independently.

From a service directory:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

## Project Structure

```text
Core-Banking-System/
│
├── Account-Service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
│
├── Auth-Service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
│
├── Transaction-services/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
│
├── Common-Security/
│   ├── src/
│   └── pom.xml
│
├── Api-gate-way/
│   ├── src/
│   └── pom.xml
│
├── database-Schemas/
│
├── frontend/
│   ├── src/
│   │   ├── api/            # fetch client (refresh + error handling) and per-service API modules
│   │   ├── components/     # layout, passbook cover, ledger, receipt, shared UI
│   │   ├── context/        # Auth, Bank (profile + accounts) and Toast providers
│   │   ├── pages/          # auth, onboarding, dashboard, transfer flow, passbook, profile, admin
│   │   ├── styles/         # tokens, base, layout, components, pages
│   │   ├── utils/          # money, date and account-number formatting helpers
│   │   ├── App.jsx         # route guarding and page selection
│   │   ├── router.jsx      # small hash-based router
│   │   └── main.jsx
│   ├── .env                # VITE_API_BASE_URL
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── demo/                   # demo videos linked from this README
├── docker-compose.yaml
├── pom.xml
└── mvnw
```

## Engineering Decisions

### Why separate services?

Authentication, account management, and transaction processing have different responsibilities and data requirements. Separating them keeps the system modular and allows service-specific changes without putting all functionality into a single application.

### Why optimistic locking?

Account balances can be affected by concurrent operations. Optimistic locking allows the application to detect conflicting updates instead of silently overwriting changes.

### Why a dedicated transaction lifecycle?

Transaction processing can move through different states, including pending, successful, and failed outcomes. Separating lifecycle handling keeps state-specific behavior easier to reason about and maintain.

### Why shared security?

Security components that are common across services are maintained in `Common-Security` instead of duplicating the same authentication/authorization infrastructure in every service.

## Current Scope

This project is primarily a **backend engineering project** focused on implementing and exploring core banking workflows, service separation, authentication, authorization, transaction processing, and data consistency. The React frontend exists to drive and demonstrate those backend features.

It is a learning and portfolio project and should **not be treated as production banking software**.

## What I Learned

The project was developed incrementally, with the architecture evolving as additional requirements were introduced.

The main areas explored through the implementation include:

* Designing Spring Boot microservices
* JWT authentication and refresh-token rotation
* Role-based access control
* Shared security components
* REST API design
* Transaction management
* Optimistic locking
* Idempotent transaction processing
* Transaction lifecycle design
* API Gateway routing
* PostgreSQL service-level data separation
* Docker-based local deployment
* Building a React client against a cookie-based JWT API with silent token refresh
* Making retries safe on the client with per-attempt idempotency keys
* Designing a PIN-gated, auto-hiding balance view and a role-aware staff desk

## Repository

[Core Banking System](https://github.com/digital-banking-platform/Core-Banking-System)
