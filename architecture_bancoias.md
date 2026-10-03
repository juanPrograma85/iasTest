# BancoIAS - Arquitectura de la solución

## 1. Objetivo

Definir una solución Full Stack mínima y funcional para el core de aprobación de créditos de BancoIAS, utilizando:

- Backend Java + Spring Boot WebFlux.
- Frontend Angular.
- PostgreSQL como persistencia.
- DDD + Arquitectura Hexagonal.
- Modular Monolith como estrategia de organización.
- REST para registrar y consultar solicitudes.

La solución debe procesar solicitudes de crédito de forma consistente incluso cuando:

1. una misma `applicationReference` sea enviada más de una vez;
2. lleguen solicitudes prácticamente simultáneas para el mismo cliente;
3. varias solicitudes compitan por el cupo máximo acumulado.

Toda solicitud procesada debe conservar trazabilidad suficiente para conocer sus datos principales, decisión, fecha de procesamiento y razón general cuando corresponda.

No se requieren Kafka, microservicios, SSE, WebSockets, motor de riesgo externo ni integraciones externas.

---

## 2. Architectural Style

### Modular Monolith

Los módulos se despliegan como una sola aplicación, pero mantienen límites claros de responsabilidad y dependencias.

### DDD

Las reglas de aprobación y las invariantes del proceso pertenecen al dominio. Los casos de uso coordinan las operaciones y los controllers solamente adaptan HTTP.

### Hexagonal Architecture

El dominio y la aplicación dependen de ports. PostgreSQL, R2DBC y HTTP son detalles de infraestructura/presentación.

### REST

REST es suficiente para el alcance solicitado:

- `POST` para procesar una solicitud.
- `GET` para consultar una solicitud por `applicationReference`.
- `GET` para consultar solicitudes procesadas recientemente.

No se incorpora SSE porque el requisito no solicita actualización en tiempo real.

---

## 3. Bounded Contexts / Modules

### Credit Application

Es el bounded context principal.

Responsabilidades:

- recibir solicitudes;
- validar datos de entrada;
- validar reglas de aprobación;
- determinar `APPROVED` o `REJECTED`;
- registrar la fecha/hora de procesamiento;
- conservar la razón general de rechazo;
- garantizar idempotencia por `applicationReference`.

### Customer

Responsabilidades:

- mantener los clientes iniciales del ejercicio;
- conocer el estado del cliente;
- conocer el cupo máximo acumulado;
- mantener el valor actualmente aprobado dentro del contexto del ejercicio.

Datos iniciales:

| Customer | Status | Max approved amount |
|---|---|---:|
| CLI-1001 | ELIGIBLE | 10.000.000 |
| CLI-1002 | BLOCKED | 8.000.000 |
| CLI-2001 | ELIGIBLE | 15.000.000 |

No se construye un módulo administrativo de clientes.

### Shared

Contiene únicamente elementos realmente compartidos:

- identificadores;
- money/value objects;
- clock;
- errores;
- contratos comunes;
- utilidades técnicas.

No contiene reglas de aprobación ni persistencia específica de créditos.

---

## 4. Domain Responsibilities

La entidad/agregado principal es `CreditApplication`.

Una solicitud debe conservar como mínimo:

```text
applicationReference
customerId
amount
termMonths
status
processedAt
decisionReason
```

Estados:

```text
APPROVED
REJECTED
```

Una solicitud procesada no cambia posteriormente de decisión dentro del alcance del ejercicio.

### Invariantes

Una solicitud puede aprobarse únicamente si:

- `amount > 0`;
- `termMonths` está entre 6 y 60;
- el cliente existe;
- el cliente está `ELIGIBLE`;
- el nuevo aprobado no supera el cupo máximo acumulado.

Una solicitud rechazada:

- se persiste;
- conserva su razón general;
- no consume cupo.

Una solicitud aprobada:

- se persiste;
- incrementa el cupo aprobado acumulado del cliente exactamente una vez.

---

## 5. Application Layer

Los casos de uso son:

### `ProcessCreditApplicationUseCase`

Procesa una nueva solicitud.

Responsabilidades:

1. validar entrada;
2. resolver el cliente;
3. aplicar reglas de aprobación;
4. garantizar que el `applicationReference` no genere una segunda solicitud;
5. proteger la actualización del cupo frente a concurrencia;
6. persistir el resultado;
7. devolver la decisión procesada.

### `GetCreditApplicationUseCase`

Consulta una solicitud mediante `applicationReference`.

### `GetRecentCreditApplicationsUseCase`

Obtiene las solicitudes procesadas recientemente, ordenadas por `processedAt DESC`.

No se requiere paginación avanzada.

---

## 6. Validation Strategy

Las validaciones se dividen en dos niveles.

### Validación de transporte

Spring WebFlux valida:

- campos requeridos;
- formato;
- tipos;
- valores básicos.

### Validación de dominio

El dominio/aplicación valida:

- monto positivo;
- plazo entre 6 y 60 meses;
- existencia del cliente;
- estado `ELIGIBLE`;
- cupo disponible.

La regla de cupo no debe depender de una lectura previa no protegida.

No se debe implementar:

```text
find customer
read approved amount
if available
update customer
```

sin una protección de concurrencia.

---

## 7. Concurrency Strategy

El requisito crítico es preservar el cupo máximo cuando llegan solicitudes simultáneas para el mismo cliente.

La estrategia propuesta es utilizar PostgreSQL como fuente de verdad y hacer la actualización del cupo de forma atómica dentro de una transacción.

El cliente mantiene:

```text
maxApprovedAmount
currentApprovedAmount
```

La aprobación intenta ejecutar conceptualmente:

```sql
UPDATE customer.customers
SET current_approved_amount = current_approved_amount + :amount,
    updated_at = CURRENT_TIMESTAMP
WHERE customer_id = :customerId
  AND status = 'ELIGIBLE'
  AND current_approved_amount + :amount <= max_approved_amount;
```

### Interpretación

- `1 row updated`: existe cupo y la solicitud puede continuar hacia `APPROVED`.
- `0 rows updated`: el cliente no está habilitado, no existe o no tiene cupo suficiente.

La operación y la inserción de la solicitud deben pertenecer a la misma transacción.

Si posteriormente falla la persistencia de la solicitud aprobada, el incremento del cupo se revierte mediante rollback.

### ¿Por qué esta estrategia?

Evita la condición de carrera:

```text
Request A -> lee 6M disponibles
Request B -> lee 6M disponibles
Request A -> aprueba 6M
Request B -> aprueba 6M
```

El `UPDATE` condicional serializa efectivamente la modificación del registro del cliente y garantiza que la segunda operación evalúe el valor actualizado.

No se utiliza un mutex en memoria porque no protege múltiples instancias del backend.

---

## 8. Transaction Boundary

La transacción debe ser corta.

Para una solicitud aprobada:

```text
BEGIN
  validar / resolver cliente
  actualizar cupo de forma atómica
  insertar CreditApplication APPROVED
COMMIT
```

Para una solicitud rechazada por reglas de negocio:

```text
BEGIN
  resolver/validar cliente
  insertar CreditApplication REJECTED
COMMIT
```

No debe mantenerse una transacción abierta durante llamadas externas porque no existen integraciones externas en este ejercicio.

---

## 9. Idempotency / Repeated References

`applicationReference` es la clave funcional de idempotencia.

En PostgreSQL debe existir:

```sql
UNIQUE (application_reference)
```

La aplicación debe tratar correctamente tres escenarios.

### Primera solicitud

```text
REF-001 -> no existe
          -> procesa
          -> persiste resultado
```

### Repetición con la misma información

```text
REF-001 -> ya existe
          -> no procesa nuevamente
          -> no consume cupo nuevamente
          -> devuelve el resultado original
```

### Repetición con información diferente

```text
REF-001 -> ya existe
          -> compara datos relevantes
          -> no modifica la solicitud original
          -> devuelve conflicto controlado
```

Se recomienda calcular/comparar una representación de los datos relevantes de la solicitud original:

```text
customerId
amount
termMonths
```

No se debe permitir que un segundo request sobrescriba la decisión original.

La restricción `UNIQUE` es la protección final contra carreras entre requests concurrentes con la misma referencia.

---

## 10. Data Model

### `customer.customers`

```text
id
customer_id
status
max_approved_amount
current_approved_amount
created_at
updated_at
```

Constraints:

- `customer_id` UNIQUE.
- `status` NOT NULL.
- `max_approved_amount >= 0`.
- `current_approved_amount >= 0`.
- `current_approved_amount <= max_approved_amount`.

### `credit.credit_applications`

```text
id
application_reference
customer_id
amount
term_months
status
decision_reason
processed_at
created_at
```

Constraints:

- `id` PK.
- `application_reference` UNIQUE.
- `customer_id` NOT NULL.
- `amount > 0`.
- `term_months BETWEEN 6 AND 60`.
- `status` NOT NULL.
- `processed_at` NOT NULL.

Índices:

```text
UNIQUE(application_reference)
INDEX(customer_id, processed_at DESC)
INDEX(processed_at DESC)
```

La referencia al cliente debe estar protegida mediante FK si ambos módulos comparten la misma base PostgreSQL.

---

## 11. Persistence Ports

### Customer

```text
CustomerRepositoryPort
```

Operaciones relevantes:

```text
findByCustomerId()
tryIncreaseApprovedAmount()
```

`tryIncreaseApprovedAmount()` representa la operación atómica de persistencia.

### Credit Application

```text
CreditApplicationRepositoryPort
```

Operaciones:

```text
findByApplicationReference()
save()
findRecent()
```

El dominio no conoce R2DBC, PostgreSQL ni SQL.

---

## 12. Spring WebFlux and Persistence

El backend obligatorio es Spring Boot WebFlux.

Para mantener el modelo reactivo de extremo a extremo, se recomienda:

```text
Spring WebFlux
      ↓
Reactive Application Services
      ↓
R2DBC
      ↓
PostgreSQL
```

Se debe evitar utilizar JPA/JDBC bloqueante directamente dentro de los handlers WebFlux.

Las transacciones reactivas deben delimitarse mediante la infraestructura de Spring/R2DBC.

El hecho de utilizar WebFlux no elimina la necesidad de una estrategia de concurrencia en PostgreSQL.

---

## 13. API Contracts

### Procesar solicitud

```http
POST /api/v1/credit-applications
Content-Type: application/json
```

Request:

```json
{
  "applicationReference": "REF-001",
  "customerId": "CLI-1001",
  "amount": 6000000,
  "termMonths": 24
}
```

### Nueva solicitud procesada

```http
201 Created
```

Response:

```json
{
  "applicationReference": "REF-001",
  "customerId": "CLI-1001",
  "amount": 6000000,
  "termMonths": 24,
  "status": "APPROVED",
  "processedAt": "2026-10-03T15:20:00Z",
  "decisionReason": "CREDIT_LIMIT_AVAILABLE",
  "idempotentReplay": false
}
```

### Repetición idéntica

Puede responder:

```http
200 OK
```

con el mismo resultado original:

```json
{
  "applicationReference": "REF-001",
  "status": "APPROVED",
  "idempotentReplay": true
}
```

No se genera otra solicitud ni se consume cupo nuevamente.

### Misma referencia con datos diferentes

```http
409 Conflict
```

La respuesta debe indicar que la referencia ya fue procesada y no puede reutilizarse con información diferente.

No se modifica la solicitud original.

---

## 14. Query by Reference

```http
GET /api/v1/credit-applications/{applicationReference}
```

Response:

```json
{
  "applicationReference": "REF-001",
  "customerId": "CLI-1001",
  "amount": 6000000,
  "termMonths": 24,
  "status": "APPROVED",
  "processedAt": "2026-10-03T15:20:00Z",
  "decisionReason": "CREDIT_LIMIT_AVAILABLE"
}
```

Si no existe:

```http
404 Not Found
```

---

## 15. Recent Applications

```http
GET /api/v1/credit-applications/recent
```

Puede aceptar opcionalmente un parámetro sencillo como:

```text
limit=20
```

No se requiere paginación avanzada.

Response:

```json
{
  "items": [
    {
      "applicationReference": "REF-003",
      "customerId": "CLI-2001",
      "amount": 5000000,
      "termMonths": 36,
      "status": "APPROVED",
      "processedAt": "2026-10-03T15:25:00Z",
      "decisionReason": "CREDIT_LIMIT_AVAILABLE"
    }
  ]
}
```

La consulta debe utilizar `ORDER BY processed_at DESC` y un índice adecuado.

---

## 16. Error Mapping

El API debe diferenciar errores funcionales de errores técnicos.

Ejemplos:

| Situación | HTTP |
|---|---:|
| JSON/formato inválido | 400 |
| Monto <= 0 | 422 |
| Plazo fuera de 6-60 | 422 |
| Cliente inexistente | 422 |
| Cliente BLOCKED | 422 |
| Cupo insuficiente | 422 |
| Solicitud inexistente | 404 |
| Referencia repetida con datos diferentes | 409 |
| Error inesperado | 500 |

Las respuestas deben tener una estructura consistente.

Ejemplo:

```json
{
  "code": "CREDIT_LIMIT_EXCEEDED",
  "message": "The requested amount exceeds the available credit limit.",
  "timestamp": "2026-10-03T15:30:00Z"
}
```

---

## 17. Angular Integration

La aplicación Angular debe contener como mínimo:

```text
src/app/
├── core/
│   ├── services/
│   │   └── api-client
│   └── models/
├── features/
│   └── credit-applications/
│       ├── models/
│       ├── services/
│       ├── state/
│       └── pages/
│           ├── credit-application-form/
│           └── recent-applications/
└── app.routes.ts
```

### Formulario

Debe permitir ingresar:

- `applicationReference`;
- `customerId`;
- `amount`;
- `termMonths`.

Validaciones iniciales:

```text
applicationReference required
customerId required
amount > 0
termMonths >= 6
termMonths <= 60
```

Las validaciones del frontend son solamente de experiencia de usuario.

El backend sigue siendo la autoridad sobre las reglas de negocio.

### Resultado

Después de procesar la solicitud, Angular debe mostrar claramente:

```text
Application Reference
Customer
Amount
Term
Status
Processed At
Decision Reason
```

El frontend debe diferenciar visualmente:

```text
APPROVED
REJECTED
```

sin asumir que una respuesta exitosa HTTP significa que el crédito fue aprobado.

### Solicitudes recientes

Angular consulta:

```http
GET /api/v1/credit-applications/recent
```

y muestra:

- referencia;
- cliente;
- monto;
- plazo;
- estado;
- fecha;
- razón.

No es necesario implementar WebSockets, SSE ni polling.

---

## 18. Seed Data

PostgreSQL debe inicializar los tres clientes del ejercicio:

```text
CLI-1001 / ELIGIBLE / 10.000.000
CLI-1002 / BLOCKED  / 8.000.000
CLI-2001 / ELIGIBLE / 15.000.000
```

El seed debe ser idempotente y no debe insertar solicitudes ficticias aprobadas.

Al iniciar la aplicación:

```text
currentApprovedAmount = 0
```

para los clientes.

---

## 19. Testing Strategy

### Domain tests

Probar como mínimo:

- monto menor o igual a cero -> rechazo;
- plazo menor a 6 -> rechazo;
- plazo mayor a 60 -> rechazo;
- cliente inexistente -> rechazo;
- cliente BLOCKED -> rechazo;
- cupo insuficiente -> rechazo;
- solicitud dentro del cupo -> aprobación;
- solicitud rechazada no consume cupo.

### Idempotency tests

Probar:

```text
REF-001 -> APPROVED
REF-001 -> mismo resultado
```

y verificar que el cupo solamente se consuma una vez.

También:

```text
REF-001 -> amount=6M
REF-001 -> amount=7M
```

y verificar:

- la primera decisión permanece;
- la segunda no modifica la primera;
- se devuelve conflicto controlado.

### Concurrency tests

Ejemplo:

```text
Cliente: CLI-1001
Cupo: 10M

Request A: 6M
Request B: 6M
```

Resultado esperado:

```text
A -> APPROVED
B -> REJECTED
```

El total aprobado debe ser exactamente:

```text
6M
```

Nunca:

```text
12M
```

También probar:

```text
Request A: 6M
Request B: 4M
```

El resultado esperado es que ambas puedan aprobarse si la serialización de las transacciones permite que el total no supere 10M.

### Persistence integration tests

Ejecutar contra PostgreSQL real o un contenedor de PostgreSQL:

- unique `applicationReference`;
- actualización atómica del cupo;
- rollback de la transacción;
- consultas recientes;
- concurrencia real.

### API tests

Probar:

- POST exitoso;
- POST rechazado funcionalmente;
- repetición idéntica;
- referencia con datos diferentes;
- GET por referencia;
- GET recientes;
- errores HTTP.

### Angular tests

Probar:

- validaciones del formulario;
- construcción correcta del request;
- visualización de APPROVED;
- visualización de REJECTED;
- consulta de solicitudes recientes;
- manejo de errores del backend.

---

## 20. Architectural Decisions

| Problema | Decisión | Motivo | Trade-off |
|---|---|---|---|
| Organización | Modular Monolith | Alcance pequeño y límites claros | Menos aislamiento que microservicios |
| Dominio | DDD + Hexagonal | Separar reglas de negocio de infraestructura | Mayor estructura para un MVP |
| Backend | Spring Boot WebFlux | Requisito obligatorio y modelo reactivo | Mayor cuidado con operaciones bloqueantes |
| Persistencia | PostgreSQL + R2DBC | Persistencia real y compatible con WebFlux | Requiere conocimiento de R2DBC |
| Concurrencia | UPDATE condicional atómico | Evitar sobrepasar el cupo | Parte de la regla queda expresada en SQL |
| Idempotencia | UNIQUE(application_reference) + comparación de payload | Evitar doble procesamiento | Requiere tratar conflictos de reintentos |
| Cupo | currentApprovedAmount persistido | Evitar recalcular todo el historial en cada aprobación | Debe mantenerse consistente mediante transacción |
| Realtime | No utilizar SSE | No existe requisito de tiempo real | El frontend consulta mediante REST |
| Clientes | Seed PostgreSQL | No requiere módulo administrativo | Los datos son estáticos para el ejercicio |
| Broker | No utilizar Kafka | Fuera del alcance | No hay distribución/event streaming |
| Microservicios | No utilizar | Complejidad innecesaria para el alcance | Modularidad se mantiene dentro de un proceso |

---

## 21. Main Processing Flow

```text
Angular
   |
   | POST /api/v1/credit-applications
   v
CreditApplicationController
   |
   v
ProcessCreditApplicationUseCase
   |
   +--> check applicationReference
   |
   +--> validate customer
   |
   +--> validate amount / term
   |
   +--> atomic customer credit-limit update
   |
   +--> persist application
   |
   v
CreditApplicationResult
   |
   v
Angular
```

### Approval path

```text
Request
   ↓
Validate
   ↓
Customer ELIGIBLE?
   ↓ yes
Atomic cupo update
   ↓ success
Persist APPROVED
   ↓
COMMIT
   ↓
Return result
```

### Rejection path

```text
Request
   ↓
Validate
   ↓
Rule fails
   ↓
Persist REJECTED
   ↓
No cupo consumed
   ↓
COMMIT
   ↓
Return result
```

---

## 22. Concurrency Example

Cliente:

```text
CLI-1001
maxApprovedAmount = 10M
currentApprovedAmount = 0
```

Llegan simultáneamente:

```text
Request A = 6M
Request B = 6M
```

PostgreSQL serializa las modificaciones del registro del cliente.

Resultado posible:

```text
A:
currentApprovedAmount 0 -> 6M
APPROVED

B:
currentApprovedAmount = 6M
6M + 6M > 10M
REJECTED
```

Estado final:

```text
currentApprovedAmount = 6M
```

La base de datos nunca debe quedar con:

```text
currentApprovedAmount > maxApprovedAmount
```

---

## 23. Idempotency Example

Primera petición:

```json
{
  "applicationReference": "REF-001",
  "customerId": "CLI-1001",
  "amount": 6000000,
  "termMonths": 24
}
```

Resultado:

```text
APPROVED
```

Segunda petición idéntica:

```text
REF-001
CLI-1001
6M
24
```

Resultado:

```text
original result
no new application
no additional cupo consumption
```

Petición posterior diferente:

```text
REF-001
CLI-1001
7M
24
```

Resultado:

```text
409 Conflict
original application remains unchanged
```

---

## 24. AI Usage Documentation

La prueba permite utilizar IA. La solución debe documentar brevemente:

### Actividades

Por ejemplo:

- revisión de alternativas arquitectónicas;
- generación inicial de código;
- generación de pruebas;
- revisión de casos de concurrencia;
- apoyo en documentación.

### Validación

Todo resultado generado por IA debe ser revisado mediante:

- lectura del código;
- ejecución de pruebas;
- pruebas de concurrencia;
- pruebas contra PostgreSQL;
- validación de los requisitos funcionales.

### Información sensible

No enviar a herramientas de IA:

- credenciales;
- contraseñas;
- secretos;
- tokens;
- información real de clientes;
- datos financieros reales.

Utilizar únicamente los datos ficticios suministrados por BancoIAS.

---

## 25. Implementation Roadmap

1. Crear proyecto Spring Boot WebFlux.
2. Configurar PostgreSQL + R2DBC.
3. Crear schemas/tablas/migraciones.
4. Crear seed de clientes.
5. Implementar dominio de `CreditApplication`.
6. Implementar `CustomerRepositoryPort`.
7. Implementar `CreditApplicationRepositoryPort`.
8. Implementar operación atómica de cupo.
9. Implementar `ProcessCreditApplicationUseCase`.
10. Implementar idempotencia por `applicationReference`.
11. Implementar GET por referencia.
12. Implementar GET recientes.
13. Implementar controllers WebFlux.
14. Crear frontend Angular.
15. Crear formulario de solicitud.
16. Mostrar resultado.
17. Crear listado de solicitudes recientes.
18. Implementar tests unitarios.
19. Implementar tests de integración PostgreSQL.
20. Implementar tests de concurrencia.
21. Documentar decisiones técnicas y uso de IA.

---

## 26. Estructura del Backend

```text
src/main/java/.../
├── config/
│
├── modules/
│   ├── customer/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   └── ports/
│   │   ├── application/
│   │   │   └── usecases/
│   │   └── infrastructure/
│   │       └── persistence/
│   │
│   └── credit/
│       ├── domain/
│       │   ├── model/
│       │   ├── events/
│       │   └── ports/
│       ├── application/
│       │   ├── usecases/
│       │   └── services/
│       ├── infrastructure/
│       │   └── persistence/
│       │       └── postgres/
│       └── presentation/
│           └── http/
│               ├── controllers/
│               └── dto/
│
└── shared/
    ├── domain/
    ├── application/
    └── infrastructure/
```

---

## 27. Angular Structure

```text
src/app/
├── core/
│   ├── http/
│   ├── models/
│   └── services/
│
├── features/
│   └── credit-applications/
│       ├── models/
│       ├── services/
│       ├── state/
│       └── pages/
│           ├── credit-application-form/
│           └── recent-applications/
│
├── app.routes.ts
└── app.config.ts
```

---

## 28. Final Validation

La solución debe demostrar:

- procesamiento de solicitudes;
- validación de monto;
- validación de plazo;
- validación de cliente;
- validación de estado `ELIGIBLE`;
- control de cupo máximo;
- persistencia de aprobadas y rechazadas;
- rechazo sin consumo de cupo;
- idempotencia por `applicationReference`;
- protección ante referencias repetidas con información diferente;
- protección ante solicitudes simultáneas para el mismo cliente;
- consulta por referencia;
- consulta de solicitudes recientes;
- integración real Angular -> Spring WebFlux -> PostgreSQL;
- pruebas automatizadas;
- documentación de decisiones técnicas;
- documentación del uso de IA.

La arquitectura debe mantenerse deliberadamente pequeña: no agregar Kafka, microservicios, SSE, WebSockets, Outbox, Redis, CQRS ni un motor de riesgo porque no son necesarios para cumplir el alcance de BancoIAS.
