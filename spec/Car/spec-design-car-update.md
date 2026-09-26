---
title: Metodo updateCar del CRUD de carros
version: 2.0
date_created: 2026-09-21
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, car, rental, crud, update]
---

# Introduction

Esta especificacion define el metodo `updateCar`: la edicion de un carro existente **limitada a dos campos**, `pricePerDay` y `available`. Los datos de identidad del vehiculo (`plate`, `brand`, `model`, `year`, `color`) son inmutables una vez creado. Recorre las cuatro capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-car-entity.md`, que es la fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar la actualizacion de la tarifa diaria y del estado de publicacion de un `Car` por `id`, restringida al rol `admin`, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `PUT /api/car/{id}`.
- Uso de la entidad `Car` y de los metodos `CarRepository.findCarById` y `save`.
- Metodo `CarService.updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest)`.
- Implementacion en `CarServiceImpl`.
- Semantica del campo `available`: despublicar y volver a publicar un carro.

**Alcance excluido**:

- Definicion de `Car`, `CarRepository`, `UpdateCarRequest` y `CarResponse`: ver `spec-schema-car-entity.md`.
- Edicion de `plate`, `brand`, `model`, `year` y `color`: **no existe ningun endpoint que los modifique**. Ver seccion 7.
- Actualizacion parcial (`PATCH`): los dos campos editables son obligatorios en el cuerpo.
- Las demas operaciones del CRUD.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente ya obtuvo un token valido y su claim `rol` vale `admin`.
- No existe `SecurityFilterChain`: el endpoint recibe y valida el header a mano.
- Corregir un dato de identidad mal capturado (por ejemplo una placa con un error de digitacion) se resuelve fuera de la API, directamente en la base de datos.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con el formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims extraidos del token: `id`, `username`, `role`. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a editar el catalogo. |
| **Campo editable** | `pricePerDay` o `available`. Son los dos unicos campos que esta operacion modifica. |
| **Campo inmutable** | `plate`, `brand`, `model`, `year`, `color`. Se fijan en el alta y no cambian nunca via API. |
| **Publicar / despublicar** | Paso de `available` entre `true` y `false` mediante este metodo. Es la unica forma de cambiar ese estado; no tiene relacion con el borrado, que elimina la fila. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. La arma el controller; en error la arma `GlobalExceptionHandler`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: La operacion modifica una instancia existente de `Car` definida en `spec-schema-car-entity.md`. La entidad no cambia: sigue teniendo sus ocho campos.
- **REQ-002**: Se invocan exactamente dos setters sobre la entidad cargada: `setPricePerDay(...)` y `setAvailable(...)`.
- **CON-001**: El `id` nunca se modifica. Se toma del path variable para localizar la fila y no del cuerpo.
- **CON-002**: Esta prohibido invocar `setPlate`, `setBrand`, `setModel`, `setYear` o `setColor` en este metodo. Son campos inmutables desde la API.
- **GUD-001**: Este es el unico punto del CRUD de carros donde se usan setters en lugar de `builder()`, porque se parte de una entidad ya gestionada por JPA. Es el mismo patron de `UserServiceImpl.updateUser`.

### Capa repository

- **REQ-003**: La operacion usa exactamente dos metodos: `findCarById(Integer id)` para cargar la fila y `save(Car car)` para persistir.
- **CON-003**: **No** se invoca `findCarByPlate`. Al no poder cambiar la placa, no hay nada que verificar: la unicidad ya la garantizo el alta y la restriccion `UNIQUE` de la tabla.
- **CON-004**: No se invoca ningun metodo de borrado.
- **GUD-002**: `findCarByPlate` permanece en `CarRepository` porque `createCar` si lo usa. No debe eliminarse del repositorio al aplicar esta especificacion.

### Capa service (interfaz)

- **REQ-004**: La interfaz `CarService` declara `CarResponse updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest)`. La firma no cambia respecto de la version anterior; lo que cambia es el contenido de `UpdateCarRequest`.
- **REQ-005**: El orden de parametros es `authHeader`, `id`, `request`. El header siempre va primero, como en todo metodo protegido del proyecto.
- **CON-005**: La interfaz no lleva anotaciones y no importa `Car` ni `CarRepository`.

### Capa model (payload)

- **REQ-006**: `UpdateCarRequest` expone exactamente dos campos:

| Campo | Tipo | Validaciones |
|---|---|---|
| `pricePerDay` | `Double` | `@NotNull`, `@Positive` |
| `available` | `Boolean` | `@NotNull` |

- **CON-006**: `UpdateCarRequest` **no** declara `plate`, `brand`, `model`, `year` ni `color`. Si el cliente los envia en el JSON, Jackson los descarta de forma silenciosa y no tienen ningun efecto.
- **CON-007**: Ambos campos son obligatorios. No hay actualizacion parcial: enviar solo `pricePerDay` produce `400` por `@NotNull` sobre `available`.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`.
- **SEC-002**: Solo se autoriza la edicion si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **REQ-007**: Se carga la entidad con `carRepository.findCarById(id)`; si el `Optional` esta vacio se lanza `NotFoundException` con el mensaje `"Carro no encontrado"`.
- **REQ-008**: Se aplican los dos setters de REQ-002 y se persiste con `carRepository.save(car)`.
- **REQ-009**: Se devuelve un `CarResponse` con las ocho propiedades de la entidad actualizada. La respuesta si incluye los campos inmutables, aunque no se hayan podido editar: el cliente necesita la ficha completa.
- **REQ-010**: `available` alterna la publicacion del carro: `false` lo retira del listado publico conservando la fila, `true` lo vuelve a publicar. **No** equivale a `DELETE /api/car/{id}`, que elimina la fila de forma irreversible.
- **CON-008**: No se normaliza ninguna cadena: esta operacion ya no recibe texto.
- **CON-009**: No se comparan los valores entrantes con los actuales ni se omite el `save` cuando nada cambia. La operacion es idempotente y siempre persiste.

### Capa controller

- **REQ-011**: El endpoint es `PUT /api/car/{id}`, declarado en `CarController` con `@PutMapping("/{id}")`.
- **REQ-012**: Los parametros del metodo son, en orden: `@RequestHeader(value = "Authorization") String authHeader`, `@PathVariable Integer id`, `@Valid @RequestBody UpdateCarRequest updateCarRequest`.
- **REQ-013**: La respuesta exitosa es `200 OK` con cuerpo `ApiResponse<CarResponse>`: `result = true` y `data` con la ficha ya actualizada.
- **REQ-014**: El controller arma el sobre con `ApiResponse.<CarResponse>builder().result(true).data(carService.updateCar(authHeader, id, updateCarRequest)).build()`. El service no conoce `ApiResponse`.
- **CON-010**: El controller no contiene reglas de negocio, ni `try/catch`, ni acceso a `CarRepository`. Solo delega en el service y envuelve el resultado en el sobre `ApiResponse`.
- **CON-011**: `CarController` depende de la interfaz `CarService`, nunca de `CarServiceImpl`.

### Validacion de entrada

- **REQ-015**: `@Valid` activa las dos anotaciones de `UpdateCarRequest`. Omitir cualquiera de los dos campos produce `400` antes de entrar al service.
- **CON-012**: `GlobalExceptionHandler` no declara handler para `MethodArgumentNotValidException`; un cuerpo invalido produce la respuesta `400` por defecto de Spring, sin el sobre `ApiResponse`.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-013**: Consecuencia directa y deliberada del patron: `ForbiddenException`, `NotFoundException` y `JwtAuthenticationException` lanzadas dentro del `try` llegan al cliente como `500` con el mensaje original, no como `403`, `404` ni `401`.
- **CON-014**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `PUT` |
| Ruta | `/api/car/{id}` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Header obligatorio | `Content-Type: application/json` |
| Parametro de ruta | `id` de tipo `Integer` |
| Cuerpo | `UpdateCarRequest` (`pricePerDay`, `available`) |
| Respuesta exitosa | `200 OK` con `ApiResponse<CarResponse>` |

### Respuestas de error

Toda respuesta producida por `GlobalExceptionHandler` viaja como `ApiResponse<ErrorResponse>` con `result = false`. Las respuestas que Spring genera antes de llegar al controller **no** llevan el sobre.

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Cuerpo invalido o incompleto (`@Valid`) | `400` | respuesta por defecto de Spring, sin sobre |
| `id` no numerico en la URL | `400` | respuesta por defecto de Spring, sin sobre |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring, sin sobre |
| Token invalido o expirado | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `result = false`, `data.message = "No esta autorizado"` |
| `id` inexistente | `500` | `result = false`, `data.message = "Carro no encontrado"` |

```json
HTTP/1.1 500 Internal Server Error

{
  "result": false,
  "data": {
    "message": "Carro no encontrado",
    "statusCode": 500
  }
}
```

### Peticion de ejemplo

```json
PUT /api/car/3
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "pricePerDay": 135000.0,
  "available": true
}
```

### Respuesta de ejemplo

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": true,
  "data": {
    "id": 3,
    "plate": "ABC123",
    "brand": "Renault",
    "model": "Logan",
    "year": 2022,
    "color": "Blanco",
    "pricePerDay": 135000.0,
    "available": true
  }
}
```

### Capa model: payload

```java
// model/Request/UpdateCarRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCarRequest {

    @NotNull
    @Positive
    private Double pricePerDay;

    @NotNull
    private Boolean available;
}
```

### Capa repository (metodos usados)

```java
Optional<Car> findCarById(Integer id);

Car save(Car car);
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.UpdateCarRequest;
import backend_project.backend_project.model.Response.CarResponse;

public interface CarService {

    CarResponse updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest);
}
```

### Capa service: implementacion de referencia

```java
@Override
public CarResponse updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        if (!jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        Car car = carRepository.findCarById(id)
                .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

        car.setPricePerDay(updateCarRequest.getPricePerDay());
        car.setAvailable(updateCarRequest.getAvailable());

        carRepository.save(car);

        return CarResponse.builder()
                .id(car.getId())
                .plate(car.getPlate())
                .brand(car.getBrand())
                .model(car.getModel())
                .year(car.getYear())
                .color(car.getColor())
                .pricePerDay(car.getPricePerDay())
                .available(car.getAvailable())
                .build();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Capa controller

```java
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<CarResponse>> updateCar(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id,
        @Valid @RequestBody UpdateCarRequest updateCarRequest) {

    return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
            .result(true)
            .data(carService.updateCar(authHeader, id, updateCarRequest))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given un token con `rol = "admin"` y existe el carro `id = 3`, When se invoca `PUT /api/car/3` con `pricePerDay = 135000.0` y `available = true`, Then la respuesta es `200` con `result = true`, `data.pricePerDay = 135000.0`, y la fila de `cars` queda actualizada.
- **AC-002**: Given la peticion de AC-001, When se comparan los campos inmutables antes y despues, Then `plate`, `brand`, `model`, `year` y `color` conservan exactamente su valor previo.
- **AC-003**: Given un cuerpo que ademas incluye `"plate": "ZZZ999"` y `"color": "Negro"`, When se invoca el endpoint, Then la operacion tiene exito, esos dos campos se ignoran y la fila conserva su placa y color originales.
- **AC-004**: Given el carro `id = 4` tiene `available = false`, When se invoca `PUT /api/car/4` con `available = true`, Then la fila queda con `available = true` y vuelve a aparecer en `GET /api/car/all`.
- **AC-004b**: Given el carro `id = 4` tiene `available = true`, When se invoca `PUT /api/car/4` con `available = false`, Then la fila sigue existiendo en `cars` y el conteo total no cambia: despublicar no es borrar.
- **AC-005**: Given no existe el carro `id = 999`, When se invoca `PUT /api/car/999`, Then la respuesta es `500` con el mensaje `"Carro no encontrado"` y no se modifica ninguna fila.
- **AC-006**: Given un token con `rol = "user"`, When se invoca el endpoint, Then no se modifica ninguna fila y la respuesta es `500` con el mensaje `"No esta autorizado"`.
- **AC-007**: Given un cuerpo sin el campo `available`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@NotNull` y el service no se ejecuta.
- **AC-008**: Given un cuerpo con `pricePerDay = 0.0`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@Positive` y el service no se ejecuta.
- **AC-009**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-010**: Given cualquier respuesta exitosa, When se inspecciona el JSON raiz, Then tiene exactamente dos propiedades, `result` y `data`, con `result = true`.
- **AC-011**: Given cualquier error lanzado dentro del service, When se inspecciona la respuesta, Then `result = false` y `data` contiene `message` y `statusCode`.
- **AC-012**: Given cualquier ejecucion de este metodo, When se inspeccionan las llamadas al repositorio, Then `findCarByPlate` no se invoca ni una sola vez.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `CarServiceImpl.updateCar`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `CarRepository` y `JwtService` mockeados con `@Mock`; `CarServiceImpl` con `@InjectMocks`.
- **Casos minimos**: edicion correcta de tarifa; republicacion de un carro despublicado; id inexistente; rol no autorizado; token invalido.
- **Verificaciones negativas obligatorias**: `verify(carRepository, never()).findCarByPlate(any())`; y con `ArgumentCaptor<Car>` sobre `save`, comprobar que los cinco campos inmutables del `Car` capturado coinciden con los de la entidad cargada.
- **Test Data Management**: los `Car` y los `UpdateCarRequest` se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=CarServiceImplTest#updateCar_ok`; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (camino feliz, rol denegado, no encontrado, rama de excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- La placa identifica legalmente a un vehiculo concreto. Permitir cambiarla convertiria la edicion en una suplantacion: la fila `id = 3` dejaria de representar el mismo carro fisico, y cualquier renta historica asociada a ese `id` quedaria apuntando a otro vehiculo. Por eso es inmutable.
- Marca, linea, anio y color son atributos fisicos del vehiculo, no datos de gestion. Si cambian, es que se trata de otro carro: la operacion correcta es dar de baja el actual y crear uno nuevo, no editar.
- Los dos campos que si cambian son los unicos que pertenecen a la operacion diaria de la agencia: cuanto cuesta rentarlo hoy (`pricePerDay`) y si esta publicado (`available`).
- Al desaparecer la edicion de placa desaparece con ella toda la verificacion de unicidad: sin `findCarByPlate`, sin normalizacion `trim().toUpperCase()` y sin la `BadRequestException` de placa duplicada. Es la simplificacion mas visible de esta version respecto de la 1.0.
- Se mantiene `PUT` y no `PATCH` porque el cuerpo trae el conjunto completo de campos editables. La semantica sigue siendo un reemplazo total de lo editable.
- `UpdateCarRequest` conserva `available` porque este endpoint es el unico camino para publicar o despublicar un carro sin eliminarlo. Es la alternativa reversible al `DELETE`, que si es definitivo.
- Se usan setters y no `builder()` porque la entidad viene del contexto de persistencia; reconstruirla con builder obligaria a repetir los cinco campos inmutables y arriesgaria perderlos.
- **Consecuencia aceptada**: un error de digitacion en la placa, la marca o el anio al crear un carro no se puede corregir por la API. Las salidas son dar de baja el registro y crear uno nuevo, o corregir la fila directamente en PostgreSQL. Si esto resulta incomodo en la practica, la solucion correcta es un endpoint aparte y explicito de correccion, no reabrir el `PUT`.
- CON-013 se documenta de forma explicita porque es contraintuitivo: editar un id inexistente produce `500` y no `404`. Es el comportamiento esperado del patron de un solo `catch`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino de la actualizacion en la tabla `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno; sin ellas `JwtService` no puede validar el token.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claims `sub`, `id`, `rol`.
- **DAT-002**: Fila existente en `cars` creada por `POST /api/car`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller y starter `validation` para `@Valid`.
- **PLT-002**: `JwtService` del propio proyecto - dependencia interna obligatoria de `CarServiceImpl`.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal: admin sube la tarifa
// PUT /api/car/3  { "pricePerDay": 135000.0, "available": true }  -> 200

// Borde: republicar un carro despublicado
// PUT /api/car/4  { "pricePerDay": 90000.0, "available": true }
// -> vuelve a aparecer en GET /api/car/all

// Borde: despublicar sin borrar. La fila permanece; DELETE la eliminaria.
// PUT /api/car/4  { "pricePerDay": 90000.0, "available": false }

// Borde: el cliente envia campos inmutables. Jackson los descarta; no hay error.
// { "plate": "ZZZ999", "color": "Negro", "pricePerDay": 90000.0, "available": true }
// -> 200, la fila conserva su placa y su color

// Borde: tarifa cero. Rechazada por @Positive antes de llegar al service. -> 400
// { "pricePerDay": 0.0, "available": true }

// Correcto: solo dos setters
car.setPricePerDay(updateCarRequest.getPricePerDay());
car.setAvailable(updateCarRequest.getAvailable());
```

```java
// Antipatron prohibido: tocar campos inmutables
// car.setPlate(updateCarRequest.getPlate());
// car.setColor(updateCarRequest.getColor());

// Antipatron prohibido: verificar unicidad de placa. Ya no hay placa que verificar.
// carRepository.findCarByPlate(plate).ifPresent(...);

// Antipatron prohibido: reconstruir la entidad con builder al editar
// Car actualizado = Car.builder().id(id).pricePerDay(...).build();  // pierde los inmutables

// Antipatron prohibido: tomar el id del cuerpo en lugar del path variable
// Car car = carRepository.findCarById(updateCarRequest.getId()) ...
```

## 10. Validation Criteria

1. `UpdateCarRequest` declara exactamente dos campos, `pricePerDay` y `available`, con las validaciones de REQ-006.
2. `CarController` declara `@PutMapping("/{id}")` con los tres parametros de REQ-012 en ese orden y devuelve `ResponseEntity<ApiResponse<CarResponse>>`.
3. `CarController` no importa `CarRepository`, `Car` ni `CarServiceImpl`.
4. La primera sentencia dentro del `try` de `updateCar` es la llamada a `jwtService.validateAccessToken(authHeader)`.
5. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
6. `updateCar` invoca unicamente `findCarById` y `save` del repositorio; el texto `findCarByPlate` no aparece en el cuerpo del metodo.
7. Los unicos setters invocados sobre la entidad son `setPricePerDay` y `setAvailable`.
8. El tipo de retorno declarado en `CarService.updateCar` es `CarResponse`, nunca `Car` ni `ApiResponse`.
9. `CarServiceImpl` no importa `ApiResponse`: el sobre solo lo arma el controller.

## 11. Related Specifications / Further Reading

- [spec-schema-car-entity.md](spec-schema-car-entity.md)
- [spec-design-car-create.md](spec-design-car-create.md)
- [spec-design-car-read-all.md](spec-design-car-read-all.md)
- [spec-design-car-read-by-id.md](spec-design-car-read-by-id.md)
- [spec-design-car-delete.md](spec-design-car-delete.md)
- `../../CLAUDE.md`: secciones "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
