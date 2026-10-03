# BancoIAS - Core de aprobación de créditos

Arquitectura: [architecture_bancoias.md](architecture_bancoias.md)

## Base de datos (PostgreSQL 16 en Docker)

### Requisitos

- Docker Desktop en ejecución (Docker Compose v2).

### Levantar

```powershell
Copy-Item .env.example .env     # ajustar credenciales locales si se desea
docker compose up -d --wait
```

Las migraciones en `db/migrations` se ejecutan automáticamente **solo la primera vez** (volumen vacío):

| Archivo | Contenido |
|---|---|
| `V1__create_schemas.sql` | Schemas `customer` y `credit` (uno por módulo) |
| `V2__create_customer_tables.sql` | `customer.customers` con invariante `current_approved_amount <= max_approved_amount` |
| `V3__create_credit_tables.sql` | `credit.credit_applications` con `UNIQUE(application_reference)`, FK, CHECKs y trigger append-only |
| `V4__seed_customers.sql` | Seed idempotente de CLI-1001, CLI-1002, CLI-2001 |

Conexión: `localhost:5433` (5432 suele estar ocupado por instalaciones locales), base/usuario/contraseña según `.env`.

### Verificar el modelo

```powershell
# Casos de uso (transacción con ROLLBACK, no deja datos)
docker exec bancoias-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /db-tests/use_cases.sql'

# Concurrencia real con sesiones simultáneas (usa una base temporal que se elimina al final)
.\db\tests\concurrency-test.ps1
```

### Reiniciar desde cero

```powershell
docker compose down -v
docker compose up -d --wait
```

### Garantías del modelo

| Caso de uso | Garantía en PostgreSQL |
|---|---|
| Cupo nunca excedido (concurrencia) | `UPDATE` condicional atómico + `CHECK (current_approved_amount <= max_approved_amount)` |
| Rechazo no consume cupo | El `UPDATE` afecta 0 filas; solo se inserta la solicitud `REJECTED` |
| Aprobación consume cupo una sola vez | `UPDATE` + `INSERT` en la misma transacción; si el `INSERT` falla, rollback del cupo |
| Idempotencia por referencia | `UNIQUE (application_reference)` |
| Decisión inmutable | Trigger que bloquea `UPDATE`/`DELETE` en `credit.credit_applications` |
| Monto, plazo, estado y razón válidos | `CHECK` de `amount > 0`, `term_months BETWEEN 6 AND 60`, status y razón coherentes |
| Cliente existente | FK `credit_applications.customer_id -> customers.customer_id` |
| Consulta de recientes | Índice `(processed_at DESC, id DESC)` |

## Backend (Spring Boot 3.5 WebFlux + R2DBC, Java 17)

`docker compose up -d --build --wait` levanta PostgreSQL y el backend.

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| API | http://localhost:8080/api/v1/credit-applications |

### Pruebas

No requieren Maven local; se ejecutan en un contenedor con acceso a Docker (Testcontainers levanta un PostgreSQL efímero con las mismas migraciones):

```powershell
docker run --rm -v "${PWD}:/workspace" -v bancoias-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /workspace/backend `
  maven:3.9.9-eclipse-temurin-17 mvn -B test
```

Cobertura: dominio, casos de uso, mapeo HTTP de errores, idempotencia, concurrencia real (6M+6M, 6M+4M, 12×2M, misma referencia simultánea) y consultas.

### Contrato de respuestas

| Situación | HTTP | Persiste |
|---|---:|:---:|
| Solicitud nueva `APPROVED` / `REJECTED` (cliente BLOCKED o cupo insuficiente) | 201 | Sí |
| Repetición idéntica (`idempotentReplay: true`) | 200 | No |
| Misma referencia con datos diferentes | 409 | No |
| Monto ≤ 0, plazo fuera de 6-60, cliente inexistente | 422 | No |
| JSON/campos inválidos | 400 | No |
| Referencia inexistente | 404 | - |

`GET /api/v1/customers/{customerId}` (solo lectura, usado por el formulario para validar el cliente): `200 {customerId, status}` sin exponer montos, o `404 CUSTOMER_NOT_FOUND`.

## Frontend (Angular 20, standalone + signals)

Con `docker compose up -d --build --wait` queda disponible en **http://localhost:4200** (Nginx sin privilegios; `/api` se reenvía al backend, por lo que no requiere CORS).

### Desarrollo local

Requiere Node 20+ y el backend en `localhost:8080` (por ejemplo, `docker compose up -d postgres backend`):

```powershell
cd frontend
npm ci
npm start                                          # http://localhost:4200 con proxy /api -> :8080
npx ng test --watch=false --browsers=ChromeHeadless
npx ng build
```

### Cómo se creó el proyecto

```powershell
npx -y @angular/cli@20 new frontend --routing --style=scss --ssr=false --skip-git --defaults --package-manager=npm
```

Estructura:

```text
src/app/
├── core/
│   ├── http/          api-base-url.token, api-error.mapper
│   ├── models/        api-error.model
│   └── services/      api-client.service
├── features/credit-applications/
│   ├── components/    status-badge, application-detail, api-error-alert
│   ├── models/        contratos del API (request/response, estados, razones)
│   ├── pipes/         etiquetas de estado y razón
│   ├── services/      credit-applications.service (POST, GET por referencia, GET recientes)
│   ├── state/         credit-applications.store (signals)
│   ├── validators/    monto > 0, plazo entero
│   └── pages/         credit-application-form, recent-applications
├── app.routes.ts
└── app.config.ts
```
