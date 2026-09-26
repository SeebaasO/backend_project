---
title: Metodo getAllReservations del CRUD de reservas
version: 1.0
date_created: 2026-09-23
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, reservation, rental, crud, read]
---

# Introduction

Esta especificacion define el metodo `getAllReservations`: el listado de reservas visible para el usuario autenticado. Un usuario comun ve solo sus reservas; un `admin` ve todas. Depende de `spec-schema-reservation-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la consulta de todas las reservas accesibles para el portador del token.

**Alcance incluido**:

- Endpoint `GET /api/reservation/all`.
- Metodo `ReservationService.getAllReservations(String authHeader)` y su implementacion.
- Filtro por rol: `admin` -> todas; otro rol -> solo las propias.

**Alcance excluido**:

- Paginacion, ordenamiento configurable y filtros por fecha o por carro.
- Consulta publica de disponibilidad de un carro: no forma parte de esta iteracion.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto.

**Supuestos**: el cliente tiene un token valido.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Reservas propias** | Reservas cuyo `user.id` coincide con `jwtValidate.getId()`. |
| **Rol admin** | Literal `"admin"` en el claim `rol`. |
| **NoContentException** | Excepcion de dominio para "lista vacia". |

## 3. Requirements, Constraints & Guidelines

### Capa repository

- **REQ-001**: Si el rol es `admin` se usa `reservationRepository.findAll()`; en otro caso `reservationRepository.findAllByUserId(jwtValidate.getId())`.
- **CON-001**: No se usa ningun otro metodo del repositorio.

### Capa service (interfaz)

- **REQ-002**: `ReservationService` declara `List<ReservationResponse> getAllReservations(String authHeader)`.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion del `try` es `jwtService.validateAccessToken(authHeader)`.
- **SEC-002**: Un usuario sin rol `admin` nunca recibe reservas de otro usuario. El filtro se decide solo con el token; no existe parametro de consulta que lo altere.
- **REQ-003**: Si la lista resultante esta vacia se lanza `NoContentException("No se encontraron reservas")`.
- **REQ-004**: Cada `Reservation` se mapea a `ReservationResponse` con los diez campos de `spec-schema-reservation-entity.md` REQ-011 (se permite el helper `toReservationResponse`, GUD-003).
- **PAT-001**: Un solo `try` con `catch (Exception e) -> InternalServerErrorException`.
- **CON-002**: Por el patron de un solo `catch`, la lista vacia llega al cliente como `500` con `No se encontraron reservas`, no como `204`.

### Capa controller

- **REQ-005**: `@GetMapping("/all")` con parametro `@RequestHeader(value = "Authorization") String authHeader`.
- **REQ-006**: Devuelve `ResponseEntity<ApiResponse<List<ReservationResponse>>>` con `result = true`.
- **CON-003**: Sin logica ni `try/catch` en el controller.

## 4. Interfaces & Data Contracts

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/reservation/all` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Respuesta exitosa | `200 OK` con `ApiResponse<List<ReservationResponse>>` |

| Situacion | Codigo efectivo | `data.message` |
|---|---|---|
| Header ausente | `400` | respuesta por defecto de Spring |
| Token invalido | `500` | mensaje de `JwtAuthenticationException` |
| Sin reservas | `500` | `No se encontraron reservas` |

```json
HTTP/1.1 200 OK

{
  "result": true,
  "data": [
    {
      "id": 12, "userId": 5, "username": "sotalvaro",
      "carId": 3, "plate": "ABC123", "brand": "Renault", "model": "Logan",
      "startDate": "2026-10-01", "endDate": "2026-10-04", "totalPrice": 360000.0
    }
  ]
}
```

### Implementacion de referencia

```java
@Override
public List<ReservationResponse> getAllReservations(String authHeader) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        List<Reservation> reservations = jwtValidate.getRole().equals("admin")
                ? reservationRepository.findAll()
                : reservationRepository.findAllByUserId(jwtValidate.getId());

        if (reservations.isEmpty()) {
            throw new NoContentException("No se encontraron reservas");
        }

        return reservations.stream()
                .map(this::toReservationResponse)
                .toList();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

```java
@GetMapping("/all")
public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAllReservations(
        @RequestHeader(value = "Authorization") String authHeader) {

    return ResponseEntity.ok(ApiResponse.<List<ReservationResponse>>builder()
            .result(true)
            .data(reservationService.getAllReservations(authHeader))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given el usuario `5` (rol `user`) tiene 2 reservas y el usuario `6` tiene 1, When `5` invoca `GET /api/reservation/all`, Then `data` tiene 2 elementos, todos con `userId = 5`.
- **AC-002**: Given los mismos datos, When un `admin` invoca el endpoint, Then `data` tiene 3 elementos.
- **AC-003**: Given el usuario `7` no tiene reservas, When invoca el endpoint, Then la respuesta es `500` con `No se encontraron reservas`.
- **AC-004**: Given un usuario comun, When se ejecuta el metodo, Then `findAll()` no se invoca.
- **AC-005**: Ningun elemento de `data` contiene `password`, `role`, `firstname` ni `lastname`.

## 6. Test Automation Strategy

- **Test Levels**: unitario con Mockito.
- **Casos minimos**: usuario con reservas; admin; lista vacia; token invalido.
- **Verificaciones**: `verify(reservationRepository, never()).findAll()` para rol `user`; `verify(reservationRepository, never()).findAllByUserId(any())` para `admin`.
- **CI/CD Integration**: `./mvnw test -Dtest=ReservationServiceImplTest`.
- **Coverage Requirements**: 100% de ramas.
- **Performance Testing**: no aplica; volumen esperado bajo (una agencia).

## 7. Rationale & Context

- Un unico endpoint `/all` con filtro por rol evita dos rutas (`/mine` y `/all`) con la misma forma de respuesta, y sigue la tabla de casos de `CLAUDE.md` (`GET /all` -> lista).
- El filtro depende solo del token para que un usuario no pueda pedir reservas ajenas manipulando la URL.
- Sin paginacion: el volumen de una sola agencia no lo justifica en esta iteracion.

## 8. Dependencies & External Integrations

- **EXT-001**: PostgreSQL - tabla `reservations`.
- **DAT-001**: Token JWT con claims `id` y `rol`.
- **PLT-001**: `JwtService` del proyecto.

## 9. Examples & Edge Cases

```java
// rol "user"  -> findAllByUserId(jwtValidate.getId())
// rol "admin" -> findAll()
// rol con mayusculas "Admin" -> NO es admin (equals exacto, igual que el resto del proyecto)
// lista vacia -> 500 "No se encontraron reservas"
```

## 10. Validation Criteria

1. Primera sentencia del `try`: `jwtService.validateAccessToken(authHeader)`.
2. El controller recibe `authHeader`; el endpoint no es publico.
3. El retorno del service es `List<ReservationResponse>`.
4. Un solo `try` / `catch (Exception e)`.

## 11. Related Specifications / Further Reading

- [spec-schema-reservation-entity.md](spec-schema-reservation-entity.md)
- [spec-design-reservation-read-by-id.md](spec-design-reservation-read-by-id.md)
- `../../CLAUDE.md`
