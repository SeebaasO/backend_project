---
title: Metodo updateReservation del CRUD de reservas
version: 1.0
date_created: 2026-09-23
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, reservation, rental, crud, update]
---

# Introduction

Esta especificacion define el metodo `updateReservation`: el cambio de fechas de una reserva existente, **limitado a `startDate` y `endDate`**. El carro y el usuario de la reserva son inmutables. El precio se recalcula. Depende de `spec-schema-reservation-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la modificacion de fechas de una `Reservation` por `id`.

**Alcance incluido**:

- Endpoint `PUT /api/reservation/{id}`.
- Metodo `ReservationService.updateReservation(String authHeader, Integer id, UpdateReservationRequest updateReservationRequest)` y su implementacion.
- Recalculo de `totalPrice` y nueva verificacion de solapamiento excluyendo la propia reserva.

**Alcance excluido**:

- Cambio de carro: se cancela la reserva (`DELETE`) y se crea otra.
- Cambio de dueno: no existe.
- Actualizacion parcial (`PATCH`).

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto.

**Supuestos**: el cliente tiene un token valido.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Campo editable** | `startDate`, `endDate`. `totalPrice` cambia como consecuencia, nunca por el cliente. |
| **Campo inmutable** | `id`, `user`, `car`. |
| **Reserva iniciada** | `reservation.startDate` anterior o igual a `LocalDate.now()`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: Se invocan exactamente tres setters sobre la entidad cargada: `setStartDate`, `setEndDate`, `setTotalPrice`.
- **CON-001**: Prohibido invocar `setUser`, `setCar` o `setId`.
- **GUD-001**: Se usan setters y no `builder()` porque la entidad viene del contexto de persistencia, igual que `CarServiceImpl.updateCar`.

### Capa repository

- **REQ-002**: Se usan `findReservationById`, `existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan` y `save`.
- **CON-002**: **No** se usa el metodo de solapamiento del alta (`existsByCarIdAndStartDate...` sin `IdNot`): la reserva se solaparia consigo misma y toda edicion fallaria.
- **CON-003**: No se consulta `CarRepository` ni `UserRepository`: el carro ya viene cargado en `reservation.getCar()`.

### Capa service (interfaz)

- **REQ-003**: `ReservationService` declara `ReservationResponse updateReservation(String authHeader, Integer id, UpdateReservationRequest updateReservationRequest)`. Orden: `authHeader`, `id`, `request`.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion del `try` es `jwtService.validateAccessToken(authHeader)`.
- **REQ-004**: Cargar la reserva; si no existe, `NotFoundException("Reserva no encontrada")`.
- **SEC-002**: Verificar dueno-o-admin; si no, `ForbiddenException("No esta autorizado")`.
- **REQ-005**: Si la reserva ya inicio (`!reservation.getStartDate().isAfter(LocalDate.now())`), `BadRequestException("La reserva ya inicio y no se puede modificar")`. Aplica tambien al `admin`.
- **REQ-006**: Si `!endDate.isAfter(startDate)`, `BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio")`.
- **REQ-007**: Si `existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(car.getId(), id, endDate, startDate)` es `true`, `BadRequestException("El carro ya esta reservado en esas fechas")`.
- **REQ-008**: `totalPrice = ChronoUnit.DAYS.between(startDate, endDate) * reservation.getCar().getPricePerDay()`. Se usa la tarifa **actual** del carro.
- **REQ-009**: Orden exacto: token -> cargar -> dueno-o-admin -> iniciada -> fechas -> solapamiento -> setters -> `save` -> mapear.
- **REQ-010**: Se devuelve `ReservationResponse` con la reserva actualizada.
- **CON-004**: No se verifica `car.available`: la reserva ya existe y despublicar el carro no invalida reservas vigentes.
- **PAT-001**: Un solo `try` con `catch (Exception e) -> InternalServerErrorException`.

### Capa controller

- **REQ-011**: `@PutMapping("/{id}")` con parametros, en orden: `@RequestHeader(value = "Authorization") String authHeader`, `@PathVariable Integer id`, `@Valid @RequestBody UpdateReservationRequest updateReservationRequest`.
- **REQ-012**: Retorna `ResponseEntity<ApiResponse<ReservationResponse>>`.

## 4. Interfaces & Data Contracts

| Elemento | Valor |
|---|---|
| Metodo | `PUT` |
| Ruta | `/api/reservation/{id}` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Cuerpo | `UpdateReservationRequest` (`startDate`, `endDate`) |
| Respuesta exitosa | `200 OK` con `ApiResponse<ReservationResponse>` |

| Situacion | Codigo efectivo | `data.message` |
|---|---|---|
| Cuerpo invalido / fecha pasada / header ausente | `400` | respuesta por defecto de Spring |
| Token invalido | `500` | mensaje de `JwtAuthenticationException` |
| Reserva inexistente | `500` | `Reserva no encontrada` |
| No es dueno ni admin | `500` | `No esta autorizado` |
| Reserva ya iniciada | `500` | `La reserva ya inicio y no se puede modificar` |
| Fechas invertidas o iguales | `500` | `La fecha de fin debe ser posterior a la fecha de inicio` |
| Solapamiento con otra reserva | `500` | `El carro ya esta reservado en esas fechas` |

```json
PUT /api/reservation/12
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{ "startDate": "2026-10-02", "endDate": "2026-10-07" }
```

```json
HTTP/1.1 200 OK

{
  "result": true,
  "data": {
    "id": 12, "userId": 5, "username": "sotalvaro",
    "carId": 3, "plate": "ABC123", "brand": "Renault", "model": "Logan",
    "startDate": "2026-10-02", "endDate": "2026-10-07", "totalPrice": 600000.0
  }
}
```

### Implementacion de referencia

```java
@Override
public ReservationResponse updateReservation(String authHeader, Integer id, UpdateReservationRequest updateReservationRequest) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        Reservation reservation = reservationRepository.findReservationById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

        if (!reservation.getUser().getId().equals(jwtValidate.getId())
                && !jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        if (!reservation.getStartDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("La reserva ya inicio y no se puede modificar");
        }

        LocalDate startDate = updateReservationRequest.getStartDate();
        LocalDate endDate = updateReservationRequest.getEndDate();

        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
        }

        Car car = reservation.getCar();

        if (reservationRepository.existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(
                car.getId(), reservation.getId(), endDate, startDate)) {
            throw new BadRequestException("El carro ya esta reservado en esas fechas");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate);

        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setTotalPrice(days * car.getPricePerDay());

        reservationRepository.save(reservation);

        return toReservationResponse(reservation);
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

```java
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<ReservationResponse>> updateReservation(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id,
        @Valid @RequestBody UpdateReservationRequest updateReservationRequest) {

    return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
            .result(true)
            .data(reservationService.updateReservation(authHeader, id, updateReservationRequest))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la reserva `12` del usuario `5`, carro `3` a `120000.0`, `[2026-10-01, 2026-10-04)`, hoy `2026-09-23`, When `5` envia `[2026-10-02, 2026-10-07)`, Then `200`, `data.totalPrice = 600000.0` y la fila queda actualizada.
- **AC-002**: Given la peticion de AC-001, Then `car_id` y `user_id` de la fila no cambian.
- **AC-003**: Given la reserva `12` es la unica del carro `3`, When se mueve a un rango que se cruza con su propio rango anterior, Then la operacion tiene exito (no se solapa consigo misma).
- **AC-004**: Given el carro `3` tiene otra reserva `13` en `[2026-10-05, 2026-10-08)`, When la `12` se mueve a `[2026-10-02, 2026-10-06)`, Then `500` con `El carro ya esta reservado en esas fechas`.
- **AC-005**: Given la reserva `12` con `startDate` igual a hoy, When se intenta modificar, Then `500` con `La reserva ya inicio y no se puede modificar`.
- **AC-006**: Given un cuerpo con `carId = 4` adicional, Then se ignora y la reserva sigue en el carro `3`.
- **AC-007**: Given el usuario `6` (rol `user`), When modifica la reserva `12`, Then `500` con `No esta autorizado` y no se modifica la fila.
- **AC-008**: Given un cuerpo sin `endDate`, Then `400` y el service no se ejecuta.

## 6. Test Automation Strategy

- **Test Levels**: unitario con Mockito.
- **Casos minimos**: edicion correcta; tercero; admin; iniciada; fechas invertidas; solapamiento; inexistente.
- **Verificaciones**: `verify(reservationRepository, never()).existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(any(), any(), any())`; `ArgumentCaptor<Reservation>` sobre `save` para comprobar que `car` y `user` son los mismos objetos cargados.
- **Nota de fechas**: los tests usan fechas relativas a `LocalDate.now()` (`now().plusDays(n)`) para no caducar.
- **Coverage Requirements**: 100% de ramas.

## 7. Rationale & Context

- Cambiar el carro de una reserva equivale a otra reserva: otra tarifa, otro calendario. Se mantiene inmutable por la misma razon que la placa de un carro.
- Bloquear la edicion de reservas iniciadas evita reescribir el historico de un alquiler en curso. Aplica tambien al `admin` para mantener una sola regla; correcciones excepcionales se hacen en base de datos.
- Se usa la tarifa actual del carro al recalcular porque la edicion es, en la practica, un nuevo acuerdo de fechas. Si el cliente quiere conservar el precio original, no debe cambiar las fechas.
- El metodo de solapamiento con `IdNot` es imprescindible; sin el, mover una reserva un dia fallaria siempre contra si misma.

## 8. Dependencies & External Integrations

- **EXT-001**: PostgreSQL - tabla `reservations`.
- **DAT-001**: Token JWT con claims `id` y `rol`.
- **PLT-001**: `java.time.LocalDate`, `ChronoUnit`.

## 9. Examples & Edge Cases

```java
// Borde: acortar la reserva un dia -> totalPrice baja
// Borde: mismas fechas que ya tiene -> exito, idempotente, siempre persiste
// Borde: tarifa del carro cambio desde el alta -> totalPrice se recalcula con la nueva

// Antipatron: usar el metodo de solapamiento del alta
// reservationRepository.existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(...)  // choca consigo misma

// Antipatron: cambiar el carro
// reservation.setCar(carRepository.findCarById(request.getCarId())...);
```

## 10. Validation Criteria

1. `UpdateReservationRequest` declara exactamente `startDate` y `endDate`.
2. Los unicos setters invocados son `setStartDate`, `setEndDate`, `setTotalPrice`.
3. Primera sentencia del `try`: `jwtService.validateAccessToken(authHeader)`.
4. Se usa el metodo de solapamiento con `IdNot`.
5. Un solo `try` / `catch (Exception e)`.

## 11. Related Specifications / Further Reading

- [spec-schema-reservation-entity.md](spec-schema-reservation-entity.md)
- [spec-design-reservation-create.md](spec-design-reservation-create.md)
- [spec-design-reservation-delete.md](spec-design-reservation-delete.md)
- `../../CLAUDE.md`
