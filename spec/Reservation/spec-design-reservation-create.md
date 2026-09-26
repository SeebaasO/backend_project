---
title: Metodo createReservation del CRUD de reservas
version: 1.1
date_created: 2026-09-23
last_updated: 2026-09-24
owner: Equipo backend_project
tags: [design, api, reservation, rental, crud, create]
---

# Introduction

Esta especificacion define el metodo `createReservation`: un usuario autenticado separa un carro entre dos fechas. Recorre las capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-reservation-entity.md`, fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar el alta de una `Reservation` de forma completa, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `POST /api/reservation`.
- Metodo `ReservationService.createReservation(String authHeader, CreateReservationRequest createReservationRequest)`.
- Implementacion en `ReservationServiceImpl`.
- Reglas: fechas coherentes, carro existente y publicado, sin solapamiento, precio calculado en servidor, despublicacion automatica del carro al reservarlo.

**Alcance excluido**:

- Definicion de entidad, repositorio y DTOs: ver `spec-schema-reservation-entity.md`.
- Pago o confirmacion de la reserva.
- Reservar a nombre de otro usuario: el dueno siempre es el usuario del token, incluso si es `admin`.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente tiene un token valido de cualquier rol.
- No existe `SecurityFilterChain`: el endpoint recibe y valida el header a mano.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header `Authorization`, formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims del token: `id`, `username`, `role`. |
| **Solapamiento** | Ver `spec-schema-reservation-entity.md` seccion 2. |
| **Dias rentados** | `ChronoUnit.DAYS.between(startDate, endDate)`. |
| **Excepcion de dominio** | `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: Se crea una nueva instancia de `Reservation` con `builder()`, sin `id`.
- **REQ-002**: `user` es la entidad `User` cargada con el `id` del token; `car` es la entidad `Car` cargada con `carId` del cuerpo.
- **CON-001**: `totalPrice` se calcula en el service (REQ-011); nunca proviene del cliente.

### Capa repository

- **REQ-003**: La operacion usa, en este orden:
  1. `CarRepository.findCarById(Integer id)`
  2. `ReservationRepository.existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(carId, endDate, startDate)`
  3. `UserRepository.findUserById(Integer id)`
  4. `ReservationRepository.save(Reservation reservation)`
  5. `CarRepository.save(Car car)`, con `car.available = false`
- **CON-002**: `ReservationServiceImpl` inyecta `ReservationRepository`, `CarRepository`, `UserRepository` y `JwtService`. Es legitimo: el service llama a repositorios, que es la capa siguiente.
- **GUD-001**: Se usa `UserRepository` (perfil, busca por id) y no `AuthRepository` (busca por username), respetando la decision de un repositorio por caso de uso.

### Capa service (interfaz)

- **REQ-004**: `ReservationService` declara `CreateReservationResponse createReservation(String authHeader, CreateReservationRequest createReservationRequest)`.
- **CON-003**: La interfaz no lleva anotaciones ni importa entidades ni repositorios.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion dentro del `try` es `JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);`.
- **SEC-002**: No hay restriccion de rol: cualquier usuario autenticado puede reservar.
- **REQ-005**: Si `!endDate.isAfter(startDate)` se lanza `BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio")`.
- **REQ-006**: Se carga el carro con `carRepository.findCarById(carId)`; si no existe, `NotFoundException("Carro no encontrado")`.
- **REQ-007**: Si `!car.getAvailable()` se lanza `BadRequestException("El carro no esta disponible")`.
- **REQ-008**: Si el metodo de solapamiento devuelve `true` se lanza `BadRequestException("El carro ya esta reservado en esas fechas")`.
- **REQ-009**: Se carga el usuario con `userRepository.findUserById(jwtValidate.getId())`.
- **REQ-010**: El orden de las validaciones es exactamente REQ-005 -> REQ-006 -> REQ-007 -> REQ-008. La validacion de fechas va primero porque no requiere base de datos.
- **REQ-011**: `totalPrice = ChronoUnit.DAYS.between(startDate, endDate) * car.getPricePerDay()`.
- **REQ-012**: Se persiste con `reservationRepository.save(reservation)` y se devuelve `CreateReservationResponse.builder().id(saved.getId()).build()`.
- **REQ-016**: Inmediatamente despues de guardar la reserva se despublica el carro: `car.setAvailable(false)` seguido de `carRepository.save(car)`. Ocurre en toda alta exitosa, sin excepcion.
- **CON-004**: No se modifica ningun otro campo del carro (`plate`, `brand`, `model`, `year`, `color`, `pricePerDay`) ni ningun campo del usuario. El unico efecto lateral permitido es `available = false`.
- **CON-005**: La comprobacion de solapamiento no es atomica. Dos peticiones concurrentes para el mismo carro y fechas pueden pasar ambas. Riesgo aceptado para esta iteracion; ver seccion 7.
- **CON-008**: `car.setAvailable(false)` se ejecuta siempre que se crea la reserva, sin importar si `startDate` es hoy o una fecha futura. La despublicacion no depende de que la reserva ya haya iniciado.

### Capa controller

- **REQ-013**: `ReservationController` con `@RestController @AllArgsConstructor @RequestMapping("/api/reservation")`.
- **REQ-014**: `@PostMapping` con parametros `@RequestHeader(value = "Authorization") String authHeader` y `@Valid @RequestBody CreateReservationRequest createReservationRequest`, en ese orden.
- **REQ-015**: Devuelve `ResponseEntity<ApiResponse<CreateReservationResponse>>` con `result = true` y el id en `data`.
- **CON-006**: El controller no contiene logica, ni `try/catch`, ni acceso a repositorios. Depende de la interfaz `ReservationService`.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try` cerrado con `catch (Exception e) { throw new InternalServerErrorException(e.getMessage()); }`.
- **CON-007**: Toda excepcion de dominio lanzada dentro del `try` llega al cliente como `500` con su mensaje original. Es el patron elegido del proyecto.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/reservation` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Cuerpo | `CreateReservationRequest` (`carId`, `startDate`, `endDate`) |
| Respuesta exitosa | `200 OK` con `ApiResponse<CreateReservationResponse>` |

### Respuestas de error

| Situacion | Codigo efectivo | `data.message` |
|---|---|---|
| Cuerpo invalido (`@Valid`), fecha pasada, campo ausente | `400` | respuesta por defecto de Spring, sin sobre |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring, sin sobre |
| Token invalido o expirado | `500` | mensaje de `JwtAuthenticationException` |
| `endDate` igual o anterior a `startDate` | `500` | `La fecha de fin debe ser posterior a la fecha de inicio` |
| `carId` inexistente | `500` | `Carro no encontrado` |
| Carro con `available = false` | `500` | `El carro no esta disponible` |
| Fechas solapadas con otra reserva del carro | `500` | `El carro ya esta reservado en esas fechas` |

### Peticion y respuesta de ejemplo

```json
POST /api/reservation
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "carId": 3,
  "startDate": "2026-10-01",
  "endDate": "2026-10-04"
}
```

```json
HTTP/1.1 200 OK

{
  "result": true,
  "data": { "id": 12 }
}
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateReservationRequest;
import backend_project.backend_project.model.Response.CreateReservationResponse;

public interface ReservationService {

    CreateReservationResponse createReservation(String authHeader, CreateReservationRequest createReservationRequest);
}
```

### Capa service: implementacion de referencia

```java
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public CreateReservationResponse createReservation(String authHeader, CreateReservationRequest createReservationRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            LocalDate startDate = createReservationRequest.getStartDate();
            LocalDate endDate = createReservationRequest.getEndDate();

            if (!endDate.isAfter(startDate)) {
                throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
            }

            Car car = carRepository.findCarById(createReservationRequest.getCarId())
                    .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

            if (!car.getAvailable()) {
                throw new BadRequestException("El carro no esta disponible");
            }

            if (reservationRepository.existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(
                    car.getId(), endDate, startDate)) {
                throw new BadRequestException("El carro ya esta reservado en esas fechas");
            }

            User user = userRepository.findUserById(jwtValidate.getId());

            long days = ChronoUnit.DAYS.between(startDate, endDate);

            Reservation reservation = Reservation.builder()
                    .user(user)
                    .car(car)
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalPrice(days * car.getPricePerDay())
                    .build();

            Reservation saved = reservationRepository.save(reservation);

            car.setAvailable(false);
            carRepository.save(car);

            return CreateReservationResponse.builder()
                    .id(saved.getId())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }
}
```

### Capa controller

```java
@RestController
@AllArgsConstructor
@RequestMapping("/api/reservation")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateReservationResponse>> createReservation(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody CreateReservationRequest createReservationRequest) {

        return ResponseEntity.ok(ApiResponse.<CreateReservationResponse>builder()
                .result(true)
                .data(reservationService.createReservation(authHeader, createReservationRequest))
                .build());
    }
}
```

## 5. Acceptance Criteria

- **AC-001**: Given un token valido del usuario `5` y el carro `3` publicado con `pricePerDay = 120000.0` sin reservas, When se invoca `POST /api/reservation` con `[2026-10-01, 2026-10-04)`, Then la respuesta es `200` con `result = true`, `data.id` presente, y la fila creada tiene `user_id = 5`, `car_id = 3`, `total_price = 360000.0`.
- **AC-002**: Given la peticion de AC-001, When se consulta el carro `3` despues del alta, Then `available = false`.
- **AC-003**: Given el carro `3` ya tiene la reserva `[2026-10-01, 2026-10-04)`, When otro usuario reserva `[2026-10-03, 2026-10-05)`, Then la respuesta es `500` con `El carro ya esta reservado en esas fechas` y no se crea fila.
- **AC-004**: Given la reserva de AC-003, When otro usuario reserva `[2026-10-04, 2026-10-06)`, Then la operacion tiene exito.
- **AC-005**: Given `startDate = endDate`, When se invoca el endpoint, Then la respuesta es `500` con `La fecha de fin debe ser posterior a la fecha de inicio` y no se consulta el repositorio de carros.
- **AC-006**: Given `carId = 999` inexistente, When se invoca el endpoint, Then la respuesta es `500` con `Carro no encontrado`.
- **AC-007**: Given el carro `4` con `available = false`, When se invoca el endpoint con `carId = 4`, Then la respuesta es `500` con `El carro no esta disponible`.
- **AC-008**: Given un cuerpo con `userId = 99` y `totalPrice = 1.0` adicionales, When se invoca el endpoint, Then esos campos se ignoran: el dueno es el usuario del token y el precio es el calculado.
- **AC-009**: Given un cuerpo con `startDate` en el pasado, When se invoca el endpoint, Then la respuesta es `400` y el service no se ejecuta.
- **AC-010**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-011**: Given falla la validacion de fechas, carro inexistente, carro no disponible o solapamiento, When se invoca el endpoint, Then `carRepository.save` no se invoca y el carro conserva su `available` original.
- **AC-012**: Given un `startDate` igual a hoy (`LocalDate.now()`), When el alta tiene exito, Then el carro queda con `available = false` de inmediato, sin esperar a que la reserva "inicie".

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ReservationServiceImpl.createReservation`; integracion opcional con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito.
- **Dobles**: `ReservationRepository`, `CarRepository`, `UserRepository` y `JwtService` con `@Mock`; `ReservationServiceImpl` con `@InjectMocks`.
- **Casos minimos**: alta correcta; fechas invertidas; carro inexistente; carro no publicado; solapamiento; token invalido.
- **Verificaciones obligatorias**: `ArgumentCaptor<Reservation>` sobre `reservationRepository.save` para comprobar `totalPrice`, `user.id == jwtValidate.id` y `car.id == carId`; `ArgumentCaptor<Car>` sobre `carRepository.save` para comprobar `available == false` y que ningun otro campo del carro cambio; en cada rama de error, `verify(carRepository, never()).save(any())`.
- **CI/CD Integration**: `./mvnw test -Dtest=ReservationServiceImplTest`.
- **Coverage Requirements**: 100% de ramas del metodo.
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- El usuario sale del token y no del cuerpo para que nadie pueda reservar a nombre de otro. Por la misma razon el precio lo calcula el servidor.
- Se exige `car.available = true` porque un carro despublicado no debe poder reservarse aunque su calendario este libre.
- Al crear la reserva se despublica el carro (`available = false`) para que deje de aparecer en `GET /api/car/all` mientras esta reservado. Es una despublicacion automatica, no una baja: el carro sigue existiendo y puede volver a publicarse con `PUT /api/car/{id}` (ver `../Car/spec-design-car-update.md`).
- **Consecuencia importante**: esta regla vuelve el carro no reservable para nuevas fechas aunque queden huecos libres en su calendario, porque `createReservation` exige `car.available = true` (REQ-007) antes de llegar a comprobar solapamiento. En la practica, con esta version, un carro solo admite **una reserva activa a la vez**; republicarlo (`PUT` con `available = true`) es responsabilidad manual del `admin` y no ocurre solo al llegar `endDate`. Esto contradice parcialmente la seccion 2 de `spec-schema-reservation-entity.md` ("la disponibilidad por fechas se resuelve con el metodo de solapamiento, no con `car.available`"); ese documento se actualiza en la misma revision para reflejar este cambio.
- No se revierte `available` a `true` al borrar o al editar fechas de la reserva en esta version: republicar el carro es un paso manual del `admin`. Ver `spec-design-reservation-delete.md` seccion 7 para el riesgo documentado.
- La validacion de fechas `endDate > startDate` no puede hacerse con una sola anotacion de campo; se hace en el service, antes de cualquier consulta, para fallar barato.
- CON-005: una restriccion de exclusion en PostgreSQL (`EXCLUDE USING gist`) o un bloqueo pesimista resolverian la concurrencia, pero exigen SQL manual o `@Lock`, fuera del patron actual del proyecto. Para una sola agencia con trafico bajo el riesgo es aceptable.
- Todas las excepciones de dominio salen como `500` por el patron de un solo `catch`; es intencional (ver `CLAUDE.md`).

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - tablas `reservations`, `cars`, `users`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION`.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` con claims `sub`, `id`, `rol`.
- **DAT-002**: Carro existente creado por `POST /api/car`.

### Technology Platform Dependencies

- **PLT-001**: `java.time.LocalDate` y `java.time.temporal.ChronoUnit`.
- **PLT-002**: `JwtService` del proyecto.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Normal: 3 dias a 120000 -> 360000.0
// { "carId": 3, "startDate": "2026-10-01", "endDate": "2026-10-04" }

// Borde: reserva de un solo dia
// { "carId": 3, "startDate": "2026-10-01", "endDate": "2026-10-02" } -> totalPrice = 120000.0

// Borde: hoy como fecha de inicio. Valido por @FutureOrPresent.

// Borde: fechas invertidas -> 500 "La fecha de fin debe ser posterior a la fecha de inicio"

// Borde: el admin reserva. Se permite; la reserva queda a nombre del admin.

// Antipatron: parametros de solapamiento invertidos (compila, pero no detecta nada)
// existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(carId, startDate, endDate)

// Correcto: despublicar el carro justo despues de guardar la reserva
car.setAvailable(false);
carRepository.save(car);

// Antipatron: despublicar antes de que la reserva se haya guardado con exito
// car.setAvailable(false); carRepository.save(car);
// Reservation saved = reservationRepository.save(reservation);   // si esto falla, el carro queda despublicado sin reserva

// Antipatron: modificar otro campo del carro al reservar
// car.setPricePerDay(car.getPricePerDay() * 1.1);
```

## 10. Validation Criteria

1. La primera sentencia del `try` es `jwtService.validateAccessToken(authHeader)`.
2. El metodo contiene un solo `try` y un solo `catch (Exception e)`.
3. El `Reservation` persistido toma `user` de `jwtValidate.getId()`, nunca del request.
4. `CarRepository.save` se invoca exactamente una vez por alta exitosa, y siempre despues de `reservationRepository.save`, con el carro en `available = false`.
5. `ReservationController` no importa repositorios, entidades ni `ReservationServiceImpl`.
6. El retorno del service es `CreateReservationResponse`; el del controller `ResponseEntity<ApiResponse<CreateReservationResponse>>`.
7. En cada rama de error (fechas invalidas, carro inexistente, carro no disponible, solapamiento), `carRepository.save` no se invoca.

## 11. Related Specifications / Further Reading

- [spec-schema-reservation-entity.md](spec-schema-reservation-entity.md)
- [spec-design-reservation-read-all.md](spec-design-reservation-read-all.md)
- [spec-design-reservation-read-by-id.md](spec-design-reservation-read-by-id.md)
- [spec-design-reservation-update.md](spec-design-reservation-update.md)
- [spec-design-reservation-delete.md](spec-design-reservation-delete.md)
- `../../CLAUDE.md`
