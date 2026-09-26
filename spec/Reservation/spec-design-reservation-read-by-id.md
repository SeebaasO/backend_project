---
title: Metodo getReservationById del CRUD de reservas
version: 1.0
date_created: 2026-09-23
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, reservation, rental, crud, read]
---

# Introduction

Esta especificacion define el metodo `getReservationById`: el detalle de una reserva por `id`, accesible solo para su dueno o para un `admin`. Depende de `spec-schema-reservation-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la consulta del detalle de una `Reservation`.

**Alcance incluido**:

- Endpoint `GET /api/reservation/{id}`.
- Metodo `ReservationService.getReservationById(String authHeader, Integer id)` y su implementacion.
- Regla de autorizacion dueno-o-admin.

**Alcance excluido**: consulta por otros criterios (placa, fechas, usuario).

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto.

**Supuestos**: el cliente tiene un token valido.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Dueno** | Usuario con `id == reservation.user.id`. |
| **Dueno-o-admin** | Regla de `spec-schema-reservation-entity.md` REQ-016. |

## 3. Requirements, Constraints & Guidelines

- **REQ-001**: El repositorio usa solo `reservationRepository.findReservationById(id)`.
- **REQ-002**: `ReservationService` declara `ReservationResponse getReservationById(String authHeader, Integer id)`. Orden de parametros: `authHeader`, `id`.
- **SEC-001**: La primera instruccion del `try` es `jwtService.validateAccessToken(authHeader)`.
- **REQ-003**: Si el `Optional` esta vacio: `NotFoundException("Reserva no encontrada")`.
- **SEC-002**: Si el usuario no es dueno ni `admin`: `ForbiddenException("No esta autorizado")`.
- **REQ-004**: El orden es: validar token -> cargar reserva -> verificar dueno-o-admin -> mapear.
- **REQ-005**: Se devuelve `ReservationResponse` con los diez campos.
- **REQ-006**: Controller: `@GetMapping("/{id}")` con `@RequestHeader(value = "Authorization") String authHeader` y `@PathVariable Integer id`; retorna `ResponseEntity<ApiResponse<ReservationResponse>>`.
- **PAT-001**: Un solo `try` con `catch (Exception e) -> InternalServerErrorException`.
- **CON-001**: `NotFoundException` y `ForbiddenException` llegan como `500` con su mensaje, por el patron de un solo `catch`.
- **CON-002**: Consecuencia de REQ-004: un usuario ajeno distingue "no existe" de "existe pero no es tuya" por el mensaje. Se acepta; los ids de reserva no son informacion sensible.

## 4. Interfaces & Data Contracts

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/reservation/{id}` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Respuesta exitosa | `200 OK` con `ApiResponse<ReservationResponse>` |

| Situacion | Codigo efectivo | `data.message` |
|---|---|---|
| `id` no numerico / header ausente | `400` | respuesta por defecto de Spring |
| Token invalido | `500` | mensaje de `JwtAuthenticationException` |
| Reserva inexistente | `500` | `Reserva no encontrada` |
| Reserva de otro usuario (sin rol admin) | `500` | `No esta autorizado` |

### Implementacion de referencia

```java
@Override
public ReservationResponse getReservationById(String authHeader, Integer id) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        Reservation reservation = reservationRepository.findReservationById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

        if (!reservation.getUser().getId().equals(jwtValidate.getId())
                && !jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        return toReservationResponse(reservation);
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<ReservationResponse>> getReservationById(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id) {

    return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
            .result(true)
            .data(reservationService.getReservationById(authHeader, id))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la reserva `12` pertenece al usuario `5`, When `5` invoca `GET /api/reservation/12`, Then `200` con `result = true` y `data.id = 12`.
- **AC-002**: Given la misma reserva, When un `admin` la consulta, Then `200`.
- **AC-003**: Given la misma reserva, When el usuario `6` (rol `user`) la consulta, Then `500` con `No esta autorizado`.
- **AC-004**: Given no existe la reserva `999`, When se consulta, Then `500` con `Reserva no encontrada`.
- **AC-005**: Given la peticion sin header, Then Spring responde `400`.

## 6. Test Automation Strategy

- **Test Levels**: unitario con Mockito.
- **Casos minimos**: dueno; admin; tercero; inexistente; token invalido.
- **Coverage Requirements**: 100% de ramas.
- **CI/CD Integration**: `./mvnw test -Dtest=ReservationServiceImplTest`.

## 7. Rationale & Context

- A diferencia de `GET /api/car/{id}`, este endpoint no es publico: una reserva contiene datos del usuario (`username`) y sus planes de viaje.
- `id` se compara con `equals` y no con `==` porque son `Integer`: `==` falla fuera del cache `-128..127`.

## 8. Dependencies & External Integrations

- **EXT-001**: PostgreSQL - tabla `reservations`.
- **DAT-001**: Token JWT con claims `id` y `rol`.

## 9. Examples & Edge Cases

```java
// Correcto: equals entre Integer
reservation.getUser().getId().equals(jwtValidate.getId())

// Antipatron: == entre Integer. Funciona con id 5, falla con id 500.
// reservation.getUser().getId() == jwtValidate.getId()
```

## 10. Validation Criteria

1. Primera sentencia del `try`: `jwtService.validateAccessToken(authHeader)`.
2. La verificacion dueno-o-admin existe y usa `equals`.
3. Un solo `try` / `catch (Exception e)`.

## 11. Related Specifications / Further Reading

- [spec-schema-reservation-entity.md](spec-schema-reservation-entity.md)
- [spec-design-reservation-read-all.md](spec-design-reservation-read-all.md)
- `../../CLAUDE.md`
