---
title: Metodo deleteReservation del CRUD de reservas
version: 1.0
date_created: 2026-09-23
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, reservation, rental, crud, delete]
---

# Introduction

Esta especificacion define el metodo `deleteReservation`: la cancelacion de una reserva mediante **borrado fisico** de su fila. Libera las fechas del carro para otros usuarios. Depende de `spec-schema-reservation-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la eliminacion de una `Reservation` por `id`.

**Alcance incluido**:

- Endpoint `DELETE /api/reservation/{id}`.
- Metodo `ReservationService.deleteReservation(String authHeader, Integer id)` y su implementacion.
- Regla: el dueno solo cancela reservas no iniciadas; el `admin` cancela cualquiera.

**Alcance excluido**:

- Borrado logico o historico de cancelaciones (no hay campo `status`).
- Reembolsos o penalidades.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto.

**Supuestos**: el cliente tiene un token valido.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Borrado fisico** | `DELETE` de la fila. Irreversible. |
| **Cancelar** | En este proyecto, sinonimo de borrar la reserva. |
| **Reserva iniciada** | `reservation.startDate` anterior o igual a `LocalDate.now()`. |

## 3. Requirements, Constraints & Guidelines

- **REQ-001**: El repositorio usa `findReservationById(id)` y `delete(Reservation reservation)` heredado.
- **CON-001**: No se usa `deleteById`: la entidad ya esta cargada para verificar el dueno.
- **CON-002**: Borrar la reserva no borra ni modifica el `User` ni el `Car` (sin `cascade`).
- **REQ-002**: `ReservationService` declara `ReservationResponse deleteReservation(String authHeader, Integer id)`.
- **SEC-001**: La primera instruccion del `try` es `jwtService.validateAccessToken(authHeader)`.
- **REQ-003**: Si no existe: `NotFoundException("Reserva no encontrada")`.
- **SEC-002**: Si no es dueno ni `admin`: `ForbiddenException("No esta autorizado")`.
- **REQ-004**: Si el usuario **no** es `admin` y la reserva ya inicio, `BadRequestException("La reserva ya inicio y no se puede cancelar")`. El `admin` queda exento.
- **REQ-005**: Orden: token -> cargar -> dueno-o-admin -> iniciada -> mapear respuesta -> `delete`.
- **REQ-006**: Se devuelve el `ReservationResponse` de la reserva eliminada, igual que `deleteCar` devuelve el `CarResponse` eliminado.
- **CON-003**: El `ReservationResponse` se construye **antes** de `delete` o desde la entidad en memoria; la entidad sigue accesible tras `delete` porque es un objeto Java, pero construirlo antes deja clara la intencion.
- **REQ-007**: Controller: `@DeleteMapping("/{id}")` con `@RequestHeader(value = "Authorization") String authHeader` y `@PathVariable Integer id`; retorna `ResponseEntity<ApiResponse<ReservationResponse>>`.
- **PAT-001**: Un solo `try` con `catch (Exception e) -> InternalServerErrorException`.

## 4. Interfaces & Data Contracts

| Elemento | Valor |
|---|---|
| Metodo | `DELETE` |
| Ruta | `/api/reservation/{id}` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Respuesta exitosa | `200 OK` con `ApiResponse<ReservationResponse>` (la reserva eliminada) |

| Situacion | Codigo efectivo | `data.message` |
|---|---|---|
| `id` no numerico / header ausente | `400` | respuesta por defecto de Spring |
| Token invalido | `500` | mensaje de `JwtAuthenticationException` |
| Reserva inexistente | `500` | `Reserva no encontrada` |
| No es dueno ni admin | `500` | `No esta autorizado` |
| Dueno no admin con reserva iniciada | `500` | `La reserva ya inicio y no se puede cancelar` |

### Implementacion de referencia

```java
@Override
public ReservationResponse deleteReservation(String authHeader, Integer id) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        Reservation reservation = reservationRepository.findReservationById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

        boolean isAdmin = jwtValidate.getRole().equals("admin");

        if (!reservation.getUser().getId().equals(jwtValidate.getId()) && !isAdmin) {
            throw new ForbiddenException("No esta autorizado");
        }

        if (!isAdmin && !reservation.getStartDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("La reserva ya inicio y no se puede cancelar");
        }

        ReservationResponse response = toReservationResponse(reservation);

        reservationRepository.delete(reservation);

        return response;
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

```java
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<ReservationResponse>> deleteReservation(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id) {

    return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
            .result(true)
            .data(reservationService.deleteReservation(authHeader, id))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la reserva `12` del usuario `5` con `startDate` futura, When `5` invoca `DELETE /api/reservation/12`, Then `200`, `data.id = 12` y la fila ya no existe.
- **AC-002**: Given AC-001, When otro usuario reserva el carro `3` en las mismas fechas, Then la operacion tiene exito: las fechas quedaron libres.
- **AC-003**: Given AC-001, Then el usuario `5` y el carro `3` siguen existiendo sin cambios.
- **AC-004**: Given la reserva `12` con `startDate` igual a hoy, When `5` (rol `user`) la borra, Then `500` con `La reserva ya inicio y no se puede cancelar` y la fila permanece.
- **AC-005**: Given la misma reserva iniciada, When un `admin` la borra, Then `200` y la fila desaparece.
- **AC-006**: Given el usuario `6` (rol `user`), When borra la reserva `12`, Then `500` con `No esta autorizado`.
- **AC-007**: Given no existe la reserva `999`, Then `500` con `Reserva no encontrada`.
- **AC-008**: Given el carro `3` queda sin reservas tras AC-001, When se invoca `DELETE /api/car/3`, Then el carro se puede borrar (ya no hay FK que lo impida).

## 6. Test Automation Strategy

- **Test Levels**: unitario con Mockito.
- **Casos minimos**: dueno reserva futura; dueno reserva iniciada; admin reserva iniciada; tercero; inexistente.
- **Verificaciones**: `verify(reservationRepository).delete(reservation)` en exito; `verify(reservationRepository, never()).delete(any())` en cada rama de error; `verify(reservationRepository, never()).deleteById(any())`.
- **Coverage Requirements**: 100% de ramas.

## 7. Rationale & Context

- Borrado fisico por coherencia con `DELETE /api/car/{id}` y porque el alcance pedido es un CRUD basico sin historico.
- Borrar la fila es lo que libera las fechas: el metodo de solapamiento solo ve filas existentes.
- El `admin` puede cancelar reservas iniciadas para cubrir casos operativos (devolucion anticipada, error de registro). El usuario no, para que no pueda anular un alquiler en curso.
- Cancelar reservas de un carro es el camino para poder borrar el carro despues, dado que la FK `car_id` bloquea el `DELETE` del carro (ver `spec-schema-reservation-entity.md` CON-010).
- **Riesgo aceptado**: no queda rastro de reservas canceladas. Si se requiere auditoria, se agrega `status` y el borrado pasa a ser logico.

## 8. Dependencies & External Integrations

- **EXT-001**: PostgreSQL - tabla `reservations`.
- **DAT-001**: Token JWT con claims `id` y `rol`.

## 9. Examples & Edge Cases

```java
// Normal: dueno cancela reserva futura -> 200, fila eliminada
// Borde: dueno cancela reserva que empieza hoy -> 500 "La reserva ya inicio y no se puede cancelar"
// Borde: admin cancela reserva en curso -> 200
// Borde: doble DELETE sobre el mismo id -> el segundo da 500 "Reserva no encontrada"

// Antipatron: borrar sin cargar (se salta dueno-o-admin)
// reservationRepository.deleteById(id);
```

## 10. Validation Criteria

1. Primera sentencia del `try`: `jwtService.validateAccessToken(authHeader)`.
2. Se usa `delete(reservation)`, nunca `deleteById`.
3. `CarRepository` y `UserRepository` no se invocan en este metodo.
4. Un solo `try` / `catch (Exception e)`.
5. El retorno del service es `ReservationResponse`.

## 11. Related Specifications / Further Reading

- [spec-schema-reservation-entity.md](spec-schema-reservation-entity.md)
- [spec-design-reservation-update.md](spec-design-reservation-update.md)
- [../Car/spec-design-car-delete.md](../Car/spec-design-car-delete.md)
- `../../CLAUDE.md`
