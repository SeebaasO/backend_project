---
title: Metodo getCarById del CRUD de carros
version: 1.0
date_created: 2026-09-21
last_updated: 2026-09-21
owner: Equipo backend_project
tags: [design, api, car, rental, crud, read]
---

# Introduction

Esta especificacion define el metodo `getCarById`: la consulta publica de un carro concreto por su identificador. Recorre las cuatro capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-car-entity.md`, que es la fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar la lectura de un unico `Car` por `id`, accesible sin autenticacion, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `GET /api/car/{id}`.
- Uso de la entidad `Car` y del metodo `CarRepository.findCarById`.
- Metodo `CarService.getCarById(Integer id)`.
- Implementacion en `CarServiceImpl`.
- Regla de visibilidad de los carros despublicados (`available = false`).

**Alcance excluido**:

- Definicion de `Car`, `CarRepository` y `CarResponse`: ver `spec-schema-car-entity.md`.
- Busqueda por placa u otros criterios: no hay endpoint publico para ello en esta iteracion.
- Las demas operaciones del CRUD.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente conoce el `id`, normalmente obtenido de `GET /api/car/all`.
- La ficha de un carro es informacion comercial publica.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Endpoint publico** | Ruta que no recibe ni valida el header `Authorization`. Cualquier cliente puede invocarla. |
| **PathVariable** | Segmento variable de la URL, enlazado a un parametro del metodo con `@PathVariable`. |
| **Carro despublicado** | Fila con `available = false`. Sigue existiendo y es consultable por id. Un carro borrado, en cambio, no tiene fila y produce `"Carro no encontrado"`. |
| **Capa service** | Clase bajo `service/impl` que contiene las reglas de negocio. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. La arma el controller; en error la arma `GlobalExceptionHandler`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: La operacion lee la entidad `Car` definida en `spec-schema-car-entity.md`. Esta especificacion no la modifica ni la persiste.
- **CON-001**: La entidad no se devuelve al cliente. El mapeo a `CarResponse` ocurre dentro del service.

### Capa repository

- **REQ-002**: La operacion usa exactamente un metodo: `Optional<Car> findCarById(Integer id)`.
- **CON-002**: No se usa `findById` heredado de `JpaRepository`, para mantener una unica convencion de nombres en el repositorio.

### Capa service (interfaz)

- **REQ-003**: La interfaz `CarService` declara `CarResponse getCarById(Integer id)`.
- **REQ-004**: El metodo **no** recibe `authHeader`, igual que `getAllCars`.
- **CON-003**: La interfaz no lleva anotaciones y no importa `Car` ni `CarRepository`.

### Capa service (implementacion)

- **SEC-001**: Este metodo **no** invoca `jwtService.validateAccessToken`. La ausencia es deliberada y la convierte en una ruta publica.
- **SEC-002**: La enumeracion de ids es posible por diseno: cualquiera puede recorrer `/api/car/1`, `/api/car/2`, etc. Es aceptable porque el recurso es publico y no contiene datos personales.
- **REQ-005**: Se obtiene el carro con `carRepository.findCarById(id)`.
- **REQ-006**: Si el `Optional` esta vacio se lanza `NotFoundException` con el mensaje `"Carro no encontrado"`.
- **REQ-007**: Un carro con `available = false` **si** se devuelve. El cliente distingue su estado leyendo el campo `available` de la respuesta.
- **REQ-008**: El mapeo a `CarResponse` se hace con `CarResponse.builder()` sobre las ocho propiedades.
- **GUD-001**: Se usa `orElseThrow` para pasar del `Optional` a la entidad; no se usan `isPresent()` mas `get()`.

### Capa controller

- **REQ-009**: El endpoint es `GET /api/car/{id}`, declarado en `CarController` con `@GetMapping("/{id}")`.
- **REQ-010**: El unico parametro del metodo es `@PathVariable Integer id`.
- **REQ-011**: La respuesta exitosa es `200 OK` con cuerpo `ApiResponse<CarResponse>`: `result = true` y `data` con la ficha del carro.
- **REQ-012**: El controller arma el sobre con `ApiResponse.<CarResponse>builder().result(true).data(carService.getCarById(id)).build()`. El service no conoce `ApiResponse`.
- **CON-004**: El controller no contiene reglas de negocio, ni `try/catch`, ni acceso a `CarRepository`. Solo delega en el service y envuelve el resultado en el sobre `ApiResponse`.
- **CON-005**: `CarController` depende de la interfaz `CarService`, nunca de `CarServiceImpl`.
- **CON-006**: Un `id` no numerico en la URL produce `MethodArgumentTypeMismatchException`, que `GlobalExceptionHandler` no maneja. Spring responde `400` por defecto. El service no se ejecuta.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-007**: Consecuencia directa y deliberada del patron: la `NotFoundException` de REQ-006 queda atrapada por ese `catch` y llega al cliente como `500` con el mensaje `"Carro no encontrado"`, no como `404`.
- **CON-008**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/car/{id}` |
| Headers obligatorios | ninguno |
| Parametro de ruta | `id` de tipo `Integer` |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `ApiResponse<CarResponse>` |

### Respuestas de error

Toda respuesta producida por `GlobalExceptionHandler` viaja como `ApiResponse<ErrorResponse>` con `result = false`. Las respuestas que Spring genera antes de llegar al controller **no** llevan el sobre.

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| `id` inexistente | `500` | `result = false`, `data.message = "Carro no encontrado"` |
| `id` no numerico en la URL | `400` | respuesta por defecto de Spring, sin sobre |
| Fallo de conexion a la base de datos | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de la causa |

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
GET /api/car/3
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
    "pricePerDay": 120000.0,
    "available": true
  }
}
```

### Capa repository (metodo usado)

```java
Optional<Car> findCarById(Integer id);
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Response.CarResponse;

public interface CarService {

    CarResponse getCarById(Integer id);
}
```

### Capa service: implementacion de referencia

```java
@Override
public CarResponse getCarById(Integer id) {

    try {
        Car car = carRepository.findCarById(id)
                .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

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
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<CarResponse>> getCarById(@PathVariable Integer id) {

    return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
            .result(true)
            .data(carService.getCarById(id))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given existe una fila con `id = 3` y `available = true`, When se invoca `GET /api/car/3` sin header `Authorization`, Then la respuesta es `200` con `result = true` y `data` conteniendo un `CarResponse` cuyos ocho campos coinciden con la fila.
- **AC-002**: Given existe una fila con `id = 4` y `available = false`, When se invoca `GET /api/car/4`, Then la respuesta es `200` con `data.available = false`.
- **AC-003**: Given no existe la fila con `id = 999`, When se invoca `GET /api/car/999`, Then la respuesta es `500` con el mensaje `"Carro no encontrado"`, por efecto de CON-007.
- **AC-004**: Given se invoca `GET /api/car/abc`, When Spring intenta convertir el path variable, Then la respuesta es `400` y `CarServiceImpl.getCarById` no llega a ejecutarse.
- **AC-005**: Given se envia un header `Authorization` con un token invalido, When se invoca el endpoint, Then la respuesta es `200`: el header se ignora por completo.
- **AC-006**: Given cualquier respuesta exitosa, When se inspecciona el cuerpo, Then el JSON raiz tiene exactamente `result` y `data`, y `data` contiene exactamente las ocho propiedades de `CarResponse`.
- **AC-007**: Given un `id` inexistente, When se invoca el endpoint, Then `result = false` y `data` contiene `message` y `statusCode`.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `CarServiceImpl.getCarById`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `CarRepository` mockeado con `@Mock`; `CarServiceImpl` con `@InjectMocks`. `JwtService` no participa en este metodo.
- **Casos minimos**: carro existente y publicado; carro existente despublicado; id inexistente o ya borrado (`Optional.empty()`).
- **Test Data Management**: los `Car` se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=CarServiceImplTest#getCarById_ok`; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (encontrado, no encontrado, rama de excepcion).
- **Performance Testing**: no aplica. La consulta es por clave primaria.

## 7. Rationale & Context

- La lectura por id es publica por la misma razon que el listado: un cliente potencial debe poder abrir el enlace de un carro concreto y ver su ficha antes de registrarse.
- Se devuelven tambien los carros con `available = false`, a diferencia del listado. Quien llega por id ya conoce el recurso, y saber que un carro existe pero no esta disponible es informacion util; ocultarlo produciria un `"Carro no encontrado"` enganioso.
- REQ-007 devuelve tambien los carros despublicados. No hay aqui ninguna fuga de registros borrados: el borrado es fisico y un carro eliminado simplemente no se encuentra. `available = false` significa solo "no publicado ahora", y saberlo es informacion util para quien ya conoce el recurso.
- Se usa `findCarById` y no `findById` para que todos los metodos del repositorio compartan la misma convencion de nombres, alineada con `UserRepository.findUserById`.
- CON-007 se documenta de forma explicita porque es contraintuitivo: un id inexistente produce `500` y no `404`. Es el comportamiento esperado del patron de un solo `catch`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - origen de la fila consultada en la tabla `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` disponibles en el entorno.

### Data Dependencies

- **DAT-001**: Filas de `cars` creadas por `POST /api/car`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller y la conversion del path variable.
- **PLT-002**: Spring Data JPA para la derivacion del query method `findCarById`.

### Compliance Dependencies

- Ninguna. El endpoint no expone datos personales.

## 9. Examples & Edge Cases

```java
// Caso normal: carro existente
// GET /api/car/3  ->  200 con la ficha

// Borde: carro despublicado. Se devuelve con available = false.
// GET /api/car/4  ->  200 { ..., "available": false }

// Borde: id inexistente
// GET /api/car/999 -> NotFoundException capturada por el catch -> 500 "Carro no encontrado"

// Borde: id no numerico
// GET /api/car/abc -> 400 de Spring, el service no se ejecuta

// Correcto: Optional resuelto con orElseThrow
Car car = carRepository.findCarById(id)
        .orElseThrow(() -> new NotFoundException("Carro no encontrado"));
```

```java
// Antipatron prohibido: isPresent + get
// Optional<Car> opt = carRepository.findCarById(id);
// if (opt.isPresent()) { Car car = opt.get(); }

// Antipatron prohibido: devolver la entidad
// public Car getCarById(Integer id) { ... }

// Antipatron prohibido: exigir token en un endpoint declarado publico
// JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);
```

## 10. Validation Criteria

1. `CarController` declara `@GetMapping("/{id}")` y su unico parametro es `@PathVariable Integer id`.
2. `CarService.getCarById` no declara el parametro `authHeader`.
3. `CarServiceImpl.getCarById` no contiene ninguna referencia a `jwtService`.
4. El metodo invoca `findCarById` y ningun otro metodo del repositorio.
5. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
6. El metodo no filtra por `available`: devuelve el carro encontrado cualquiera sea su estado.
7. El tipo de retorno declarado en `CarService.getCarById` es `CarResponse`, nunca `Car` ni `ApiResponse`.
8. `CarController.getCarById` declara `ResponseEntity<ApiResponse<CarResponse>>` y construye el sobre con `result(true)`.
9. `CarServiceImpl` no importa `ApiResponse`.

## 11. Related Specifications / Further Reading

- [spec-schema-car-entity.md](spec-schema-car-entity.md)
- [spec-design-car-create.md](Car/spec-design-car-create.md)
- [spec-design-car-read-all.md](spec-design-car-read-all.md)
- [spec-design-car-update.md](spec-design-car-update.md)
- [spec-design-car-delete.md](spec-design-car-delete.md)
- `../../CLAUDE.md`: secciones "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
