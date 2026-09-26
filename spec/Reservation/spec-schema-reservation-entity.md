---
title: Entidad Reservation, repositorio y contratos de datos del CRUD de reservas
version: 1.1
date_created: 2026-09-23
last_updated: 2026-09-24
owner: Equipo backend_project
tags: [schema, data, jpa, reservation, rental, crud]
---

# Introduction

Esta especificacion define la entidad JPA `Reservation`, su repositorio Spring Data y los DTOs (`Request` / `Response`) compartidos por las cinco operaciones del CRUD de reservas del sistema de renta de carros. Una reserva es la separacion de un carro concreto por un usuario concreto entre dos fechas. Es la base comun: las especificaciones de `create`, `read all`, `read by id`, `update` y `delete` dependen de este documento y no redefinen estos artefactos.

## 1. Purpose & Scope

**Proposito**: fijar el modelo de datos persistente de la reserva, sus dos llaves foraneas (`users` y `cars`) y los contratos de entrada/salida del recurso `Reservation` dentro del proyecto `backend_project`.

**Alcance incluido**:

- Clase `entity/Reservation.java` con relaciones `@ManyToOne` hacia `User` y `Car`.
- Interfaz `repository/ReservationRepository.java`.
- DTOs `model/Request/CreateReservationRequest.java`, `model/Request/UpdateReservationRequest.java`, `model/Response/ReservationResponse.java`, `model/Response/CreateReservationResponse.java`.
- Regla de calculo de `totalPrice` y regla de solapamiento de fechas.
- Impacto de las llaves foraneas sobre el borrado de carros y usuarios.

**Alcance excluido**:

- Logica de negocio de cada operacion (ver especificaciones por metodo).
- Pagos, depositos, seguros, entrega y devolucion fisica del vehiculo.
- Estados de ciclo de vida (`pendiente`, `confirmada`, `finalizada`): la reserva no tiene campo de estado. Ver seccion 7.
- Relacion inversa (`@OneToMany`) en `User` o `Car`: las entidades existentes **no** se modifican.

**Audiencia**: agentes de IA generativa y desarrolladores que implementen o modifiquen el CRUD de reservas.

**Supuestos**:

- Existen y estan operativas las entidades `User` (tabla `users`) y `Car` (tabla `cars`), definidas en el codigo y en `../Car/spec-schema-car-entity.md`.
- Existen `JwtService`, `GlobalExceptionHandler`, las excepciones de dominio y el sobre `ApiResponse<T>`.
- Todas las operaciones de reserva exigen token. No hay ninguna operacion publica.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Reserva** | Registro que asigna un `Car` a un `User` durante un rango de fechas `[startDate, endDate)`. |
| **FK** | Foreign Key. Llave foranea: columna que referencia la llave primaria de otra tabla. |
| **Dueno de la reserva** | El `User` referenciado por `reservation.user`. Coincide con el `id` del token que la creo. |
| **Rango semiabierto `[startDate, endDate)`** | `startDate` es el dia de recogida (incluido); `endDate` es el dia de devolucion (excluido del cobro). Una reserva `2026-10-01` a `2026-10-04` cobra 3 dias. |
| **Solapamiento** | Dos reservas del mismo carro se solapan si `existente.startDate < nueva.endDate` **y** `existente.endDate > nueva.startDate`. |
| **Dias rentados** | `ChronoUnit.DAYS.between(startDate, endDate)`. Siempre mayor o igual a 1. |
| **Reserva iniciada** | Reserva cuyo `startDate` es anterior o igual a `LocalDate.now()`. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. |

## 3. Requirements, Constraints & Guidelines

### Entidad

- **REQ-001**: La entidad se llama `Reservation` y vive en el paquete `backend_project.backend_project.entity`.
- **REQ-002**: Se mapea a la tabla `reservations` mediante `@Table(name = "reservations")`. No lleva `uniqueConstraints`: un mismo usuario puede reservar el mismo carro varias veces en fechas distintas.
- **REQ-003**: Campos exactos, en este orden:

| Campo | Tipo Java | Columna | Nulable | Notas |
|---|---|---|---|---|
| `id` | `Integer` | `id` | generado | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `user` | `User` | `user_id` | `nullable = false` | `@ManyToOne` + `@JoinColumn(name = "user_id", nullable = false)`. FK a `users.id` |
| `car` | `Car` | `car_id` | `nullable = false` | `@ManyToOne` + `@JoinColumn(name = "car_id", nullable = false)`. FK a `cars.id` |
| `startDate` | `LocalDate` | `start_date` | `nullable = false` | dia de recogida, incluido |
| `endDate` | `LocalDate` | `end_date` | `nullable = false` | dia de devolucion, excluido del cobro |
| `totalPrice` | `Double` | `total_price` | `nullable = false` | `dias rentados * car.pricePerDay`, calculado en el service |

- **REQ-004**: La entidad se anota con `@Data @Builder @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Entity @Table(...)`, igual que `User` y `Car`.
- **REQ-005**: Las relaciones son unidireccionales: `Reservation` conoce a `User` y a `Car`; ni `User` ni `Car` declaran una coleccion de reservas.
- **CON-001**: Se usa el `fetch` por defecto de `@ManyToOne` (`EAGER`). No se declara `FetchType.LAZY`, porque los services no usan `@Transactional` y el mapeo a `ReservationResponse` lee `user` y `car` despues de salir del repositorio.
- **CON-002**: No se declara `cascade`. Guardar o borrar una reserva nunca crea, modifica ni borra el `User` ni el `Car` referenciados.
- **CON-003**: La entidad nunca se serializa hacia el cliente. Toda salida HTTP usa `ReservationResponse`, salvo el alta, que usa `CreateReservationResponse`.
- **CON-004**: No se agrega campo `status` ni campos de auditoria (`createdAt`, `updatedAt`). Cancelar una reserva es borrarla (ver `spec-design-reservation-delete.md`).
- **CON-005**: `totalPrice` es un valor congelado: se calcula con la tarifa del carro en el momento del alta o de la ultima modificacion de fechas. Un cambio posterior de `car.pricePerDay` no lo recalcula.
- **GUD-001**: `@Data` sobre una entidad con relaciones es seguro aqui porque las relaciones son unidireccionales: `toString`, `equals` y `hashCode` no entran en ciclo.

### Repositorio

- **REQ-006**: `ReservationRepository` es una interfaz anotada `@Repository` que extiende `JpaRepository<Reservation, Integer>`.
- **REQ-007**: Metodos requeridos:

| Firma | Origen | Uso |
|---|---|---|
| `Optional<Reservation> findReservationById(Integer id)` | derivado | lectura por id, actualizacion y borrado |
| `List<Reservation> findAllByUserId(Integer userId)` | derivado (`user.id`) | listado de reservas del usuario del token |
| `boolean existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(Integer carId, LocalDate endDate, LocalDate startDate)` | derivado | solapamiento en el alta |
| `boolean existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(Integer carId, Integer id, LocalDate endDate, LocalDate startDate)` | derivado | solapamiento en la edicion, excluyendo la propia reserva |
| `List<Reservation> findAll()` | heredado | listado completo para `admin` |
| `Reservation save(Reservation reservation)` | heredado | alta y actualizacion |
| `void delete(Reservation reservation)` | heredado | borrado fisico |

- **CON-006**: Los metodos de solapamiento reciben los parametros en el orden del nombre del metodo: el primer `LocalDate` es la **fecha de fin nueva** (se compara con `startDate` existente), el segundo es la **fecha de inicio nueva** (se compara con `endDate` existente). Invertirlos rompe la deteccion sin producir error de compilacion.
- **CON-007**: El repositorio no contiene implementacion manual, ni `@Query` JPQL, ni conoce DTOs.
- **GUD-002**: `findAllByUserId` y `existsByCarId...` navegan la propiedad anidada (`user.id`, `car.id`); Spring Data lo resuelve desde el nombre sin `@Query`.

### DTOs

- **REQ-008**: `CreateReservationRequest` expone `carId` (`@NotNull @Positive`), `startDate` (`@NotNull @FutureOrPresent`) y `endDate` (`@NotNull @Future`).
- **REQ-009**: `CreateReservationRequest` **no** expone `userId` ni `totalPrice`. El usuario sale siempre del token y el precio siempre lo calcula el servidor.
- **REQ-010**: `UpdateReservationRequest` expone exactamente dos campos: `startDate` (`@NotNull @FutureOrPresent`) y `endDate` (`@NotNull @Future`). El carro y el usuario de una reserva son inmutables.
- **REQ-011**: `ReservationResponse` expone, aplanados: `id`, `userId`, `username`, `carId`, `plate`, `brand`, `model`, `startDate`, `endDate`, `totalPrice`. No anida `User`, `Car`, `UserResponse` ni `CarResponse`.
- **REQ-012**: `ReservationResponse` **nunca** expone `password`, `firstname`, `lastname` ni `role` del usuario.
- **REQ-013**: `CreateReservationResponse` expone un unico campo, `id`. Es la salida exclusiva del alta.
- **REQ-014**: Los cuatro DTOs se anotan con `@Data @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- **CON-008**: La regla `endDate > startDate` no se expresa con anotaciones: la valida el service con `BadRequestException`. Las anotaciones solo garantizan presencia y que las fechas no esten en el pasado.
- **CON-009**: Las fechas viajan en JSON con formato ISO-8601 `yyyy-MM-dd` (`"2026-10-01"`), el formato por defecto de Jackson para `LocalDate` en Spring Boot. No se declara `@JsonFormat`.

### Reglas de negocio comunes

- **REQ-015**: Calculo del precio: `ChronoUnit.DAYS.between(startDate, endDate) * car.getPricePerDay()`.
- **REQ-016**: Autorizacion sobre una reserva existente: se permite la operacion si `reservation.getUser().getId().equals(jwtValidate.getId())` **o** `jwtValidate.getRole().equals("admin")`. En otro caso `ForbiddenException("No esta autorizado")`.
- **GUD-003**: El mapeo `Reservation -> ReservationResponse` se repite en cuatro operaciones. Se permite extraerlo a un metodo `private ReservationResponse toReservationResponse(Reservation reservation)` dentro de `ReservationServiceImpl`. No se crea una clase mapper aparte.

### Impacto de las llaves foraneas

- **CON-010**: Con la FK `car_id`, `DELETE /api/car/{id}` sobre un carro con reservas viola la restriccion y PostgreSQL rechaza el borrado. La excepcion llega al cliente como `500`. Este comportamiento es aceptado en esta iteracion; ver seccion 7.
- **CON-011**: Lo mismo aplica a cualquier borrado futuro de un `User` con reservas.

### Persistencia y esquema

- **CON-012**: No hay migraciones. Hibernate genera la tabla `reservations` y sus dos FK al arrancar (`ddl-auto=update`).
- **GUD-004**: `totalPrice` se modela como `Double` por coherencia con `Car.pricePerDay`. Misma deuda tecnica documentada en la entidad `Car`.

## 4. Interfaces & Data Contracts

### Capa entity

```java
package backend_project.backend_project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;
}
```

### Tabla generada

| Columna | Tipo PostgreSQL | Restriccion |
|---|---|---|
| `id` | `serial` / `integer` | `PRIMARY KEY`, autoincremental |
| `user_id` | `integer` | `NOT NULL`, `FOREIGN KEY REFERENCES users(id)` |
| `car_id` | `integer` | `NOT NULL`, `FOREIGN KEY REFERENCES cars(id)` |
| `start_date` | `date` | `NOT NULL` |
| `end_date` | `date` | `NOT NULL` |
| `total_price` | `double precision` | `NOT NULL` |

```text
users (1) ────< reservations >──── (1) cars
   id  <── user_id          car_id ──>  id
```

### Capa repository

```java
package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    Optional<Reservation> findReservationById(Integer id);

    List<Reservation> findAllByUserId(Integer userId);

    boolean existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(
            Integer carId, LocalDate endDate, LocalDate startDate);

    boolean existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(
            Integer carId, Integer id, LocalDate endDate, LocalDate startDate);
}
```

### DTOs

```java
// model/Request/CreateReservationRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReservationRequest {

    @NotNull
    @Positive
    private Integer carId;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    @Future
    private LocalDate endDate;
}
```

```java
// model/Request/UpdateReservationRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReservationRequest {

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    @Future
    private LocalDate endDate;
}
```

```java
// model/Response/ReservationResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Integer id;
    private Integer userId;
    private String username;
    private Integer carId;
    private String plate;
    private String brand;
    private String model;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalPrice;
}
```

```java
// model/Response/CreateReservationResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReservationResponse {

    private Integer id;
}
```

### Ejemplo JSON de `ReservationResponse` dentro del sobre

```json
{
  "result": true,
  "data": {
    "id": 12,
    "userId": 5,
    "username": "sotalvaro",
    "carId": 3,
    "plate": "ABC123",
    "brand": "Renault",
    "model": "Logan",
    "startDate": "2026-10-01",
    "endDate": "2026-10-04",
    "totalPrice": 360000.0
  }
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la aplicacion arranca con `users` y `cars` existentes, When Hibernate procesa las entidades, Then existe la tabla `reservations` con las seis columnas de la seccion 4 y dos FK: `user_id -> users.id` y `car_id -> cars.id`.
- **AC-002**: Given una `Reservation` con `car = null`, When se invoca `save`, Then la persistencia falla por violacion de `NOT NULL`.
- **AC-003**: Given una `Reservation` cuyo `car.id = 999` no existe en `cars`, When se invoca `save`, Then la persistencia falla por violacion de FK.
- **AC-004**: Given el usuario `5` tiene dos reservas y el usuario `6` una, When se invoca `findAllByUserId(5)`, Then la lista tiene tamanio 2.
- **AC-005**: Given el carro `3` tiene una reserva `[2026-10-01, 2026-10-04)`, When se invoca `existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(3, 2026-10-05, 2026-10-03)`, Then devuelve `true`.
- **AC-006**: Given la misma reserva de AC-005, When se consulta el rango `[2026-10-04, 2026-10-06)`, Then devuelve `false`: devolver y recoger el mismo dia no es solapamiento.
- **AC-007**: Given la reserva `12` es la unica del carro `3`, When se invoca `existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(3, 12, ...)` con cualquier rango, Then devuelve `false`.
- **AC-008**: Given un `CreateReservationRequest` con `startDate` de ayer, When se valida, Then se reporta violacion de `@FutureOrPresent`.
- **AC-009**: Given un `CreateReservationRequest` sin `carId`, When se valida, Then se reporta violacion de `@NotNull`.
- **AC-010**: El tipo `ReservationResponse` no importa `User`, `Car`, `UserResponse` ni `CarResponse`.
- **AC-011**: Given se borra una `Reservation`, When se consultan `users` y `cars`, Then el usuario y el carro referenciados siguen existiendo.
- **AC-012**: Given el carro `3` tiene al menos una reserva, When se invoca `DELETE /api/car/3`, Then la fila del carro no se borra y la respuesta es `500`.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre validaciones de DTO; integracion sobre el repositorio.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Repositorio**: `@DataJpaTest` contra PostgreSQL de desarrollo, cubriendo `findAllByUserId` y los dos metodos de solapamiento con rangos que se tocan, se contienen, se cruzan y se separan.
- **DTOs**: `Validator` de `Validation.buildDefaultValidatorFactory()`, un caso valido y uno invalido por anotacion.
- **Test Data Management**: cada test persiste su `User` y su `Car` con `builder()` antes de la `Reservation`. `@DataJpaTest` revierte la transaccion.
- **CI/CD Integration**: `./mvnw test`. En Windows: `mvnw.cmd test`.
- **Coverage Requirements**: cobertura de linea minima 80% en `entity`, `model` y `repository`.
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- Las dos FK son el requisito central: la reserva no tiene sentido sin un usuario y un carro reales, y la base de datos debe impedir referencias a filas inexistentes. Por eso son `@ManyToOne` con `nullable = false` y no simples columnas `Integer userId` / `carId`.
- Se usa `@ManyToOne` hacia la entidad y no un `Integer` porque el `ReservationResponse` necesita `username`, `plate`, `brand` y `model`: con la relacion, una sola carga trae todo.
- Las relaciones son unidireccionales para no tocar `User` ni `Car` y evitar ciclos de serializacion y de `toString` generados por Lombok.
- El rango semiabierto `[startDate, endDate)` es la convencion de las agencias de renta: el dia de devolucion el carro queda libre para otro cliente. Evita que dos reservas consecutivas se consideren solapadas.
- **Actualizado en v1.1**: crear una reserva **si** cambia `car.available` a `false` (ver `spec-design-reservation-create.md` REQ-016). El campo pasa a tener doble senal: "publicado en el catalogo" (ver `../Car/spec-schema-car-entity.md` CON-003) y, de forma practica en esta version, "sin reserva activa". La deteccion de solapamiento por fechas (`existsByCarId...`) se mantiene como segunda barrera, pero en la practica ya no se alcanza para un carro con una reserva activa, porque `createReservation` exige `available = true` antes de llegar a comprobarla. Consecuencia: con esta version, un carro solo admite una reserva activa a la vez, y republicarlo tras la reserva es un paso manual del `admin` via `PUT /api/car/{id}`.
- `totalPrice` se persiste en lugar de calcularse al leer, porque la tarifa del carro puede cambiar despues y el precio acordado no debe moverse con ella.
- No hay campo `status` porque el alcance pedido es un CRUD basico: una reserva existe o no existe. Si mas adelante se necesita historico de cancelaciones, se agrega `status` y el borrado pasa a ser logico.
- **Riesgo documentado**: CON-010 cambia el comportamiento del `DELETE /api/car/{id}` descrito en `../Car/spec-design-car-delete.md` (seccion 7 ya lo anticipaba). La correccion recomendada, fuera de esta iteracion, es una comprobacion previa en `CarServiceImpl.deleteCar` que lance `BadRequestException("El carro tiene reservas asociadas")`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - aloja la tabla `reservations` y hace cumplir las FK hacia `users` y `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Archivo `.env` con las variables obligatorias del proyecto.

### Data Dependencies

- **DAT-001**: Tabla `users` - fila del usuario autenticado.
- **DAT-002**: Tabla `cars` - fila del carro reservado.

### Technology Platform Dependencies

- **PLT-001**: Java 25 - `java.time.LocalDate` y `ChronoUnit`.
- **PLT-002**: Spring Boot 4.1.1 con starters `data-jpa`, `validation` y `web` (Jackson con soporte `java.time`).
- **PLT-003**: Lombok.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Construccion correcta en el alta
Reservation reservation = Reservation.builder()
        .user(user)
        .car(car)
        .startDate(LocalDate.of(2026, 10, 1))
        .endDate(LocalDate.of(2026, 10, 4))
        .totalPrice(3 * car.getPricePerDay())   // 3 dias
        .build();

// Solapamiento: orden de parametros = (carId, endDate nueva, startDate nueva)
boolean ocupado = reservationRepository.existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(
        car.getId(), request.getEndDate(), request.getStartDate());

// Borde: reservas consecutivas. [01,04) y [04,06) NO se solapan.
// Borde: reserva contenida. [01,10) y [03,05) SI se solapan.
// Borde: startDate == endDate. 0 dias: lo rechaza el service con BadRequestException.

// Mapeo a response, sin exponer la entidad ni datos sensibles del usuario
ReservationResponse response = ReservationResponse.builder()
        .id(reservation.getId())
        .userId(reservation.getUser().getId())
        .username(reservation.getUser().getUsername())
        .carId(reservation.getCar().getId())
        .plate(reservation.getCar().getPlate())
        .brand(reservation.getCar().getBrand())
        .model(reservation.getCar().getModel())
        .startDate(reservation.getStartDate())
        .endDate(reservation.getEndDate())
        .totalPrice(reservation.getTotalPrice())
        .build();

// Antipatron: tomar el usuario del cuerpo
// .user(userRepository.findUserById(request.getUserId()))

// Antipatron: aceptar el precio del cliente
// .totalPrice(request.getTotalPrice())

// Correcto desde v1.1: marcar el carro como no disponible al reservar (ver spec-design-reservation-create.md REQ-016)
car.setAvailable(false);
carRepository.save(car);
```

## 10. Validation Criteria

1. Existen los seis archivos del recurso: `entity/Reservation.java`, `repository/ReservationRepository.java`, `model/Request/CreateReservationRequest.java`, `model/Request/UpdateReservationRequest.java`, `model/Response/ReservationResponse.java`, `model/Response/CreateReservationResponse.java`.
2. `Reservation` declara exactamente los seis campos de REQ-003; `user` y `car` con `@ManyToOne` y `@JoinColumn(nullable = false)`.
3. `User.java` y `Car.java` no se modifican.
4. `ReservationRepository` declara solo los cuatro metodos de la seccion 4 y no importa clases de `model`.
5. Ningun archivo bajo `model/` importa clases de `entity`.
6. `CreateReservationRequest` no declara `userId` ni `totalPrice`.
7. `./mvnw clean package` compila sin errores.
8. Tras arrancar, PostgreSQL muestra la tabla `reservations` con dos FK.

## 11. Related Specifications / Further Reading

- [spec-design-reservation-create.md](spec-design-reservation-create.md)
- [spec-design-reservation-read-all.md](spec-design-reservation-read-all.md)
- [spec-design-reservation-read-by-id.md](spec-design-reservation-read-by-id.md)
- [spec-design-reservation-update.md](spec-design-reservation-update.md)
- [spec-design-reservation-delete.md](spec-design-reservation-delete.md)
- [../Car/spec-schema-car-entity.md](../Car/spec-schema-car-entity.md)
- [../Car/spec-design-car-delete.md](../Car/spec-design-car-delete.md)
- `../../CLAUDE.md`: arquitectura por capas y decisiones cerradas.
