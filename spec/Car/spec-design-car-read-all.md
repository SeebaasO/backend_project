---
title: Metodo getAllCars del CRUD de carros
version: 1.0
date_created: 2026-09-21
last_updated: 2026-09-21
owner: Equipo backend_project
tags: [design, api, car, rental, crud, read]
---

# Introduction

Esta especificacion define el metodo `getAllCars`: el listado publico del catalogo de carros disponibles para renta. Recorre las cuatro capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-car-entity.md`, que es la fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar la consulta del catalogo completo de carros vigentes, accesible sin autenticacion, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `GET /api/car/all`.
- Uso de la entidad `Car` y del metodo `CarRepository.findAllByAvailableTrue`.
- Metodo `CarService.getAllCars()`.
- Implementacion en `CarServiceImpl`.
- Regla de visibilidad: los carros despublicados (`available = false`) no se listan.

**Alcance excluido**:

- Definicion de `Car`, `CarRepository` y `CarResponse`: ver `spec-schema-car-entity.md`.
- Paginacion, ordenamiento y filtros por marca, precio o anio: fuera de alcance de esta iteracion.
- Las demas operaciones del CRUD.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El catalogo es informacion comercial publica: no contiene datos personales de clientes ni de la agencia.
- Todos los carros pertenecen a la unica agencia del sistema, por lo que un unico listado global es suficiente.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Endpoint publico** | Ruta que no recibe ni valida el header `Authorization`. Cualquier cliente puede invocarla. |
| **Carro vigente** | Fila de `cars` con `available = true`. Es lo que ve el publico. |
| **Carro despublicado** | Fila con `available = false`. Sigue existiendo en la base de datos pero no se lista. No es un carro borrado: el borrado elimina la fila. |
| **Capa service** | Clase bajo `service/impl` que contiene las reglas de negocio. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. La arma el controller; en error la arma `GlobalExceptionHandler`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: La operacion lee la entidad `Car` definida en `spec-schema-car-entity.md`. Esta especificacion no la modifica ni la persiste.
- **CON-001**: La entidad no se devuelve al cliente en ningun caso. El mapeo a `CarResponse` ocurre dentro del service.

### Capa repository

- **REQ-002**: La operacion usa exactamente un metodo: `List<Car> findAllByAvailableTrue()`.
- **CON-002**: No se usa `findAll()` con filtrado posterior en Java. El filtro por `available` se resuelve en SQL mediante el query method derivado.

### Capa service (interfaz)

- **REQ-003**: La interfaz `CarService` declara `List<CarResponse> getAllCars()`.
- **REQ-004**: El metodo **no** recibe `authHeader`. Es la unica familia de metodos del proyecto sin ese parametro, junto con `getCarById`.
- **CON-003**: La interfaz no lleva anotaciones y no importa `Car` ni `CarRepository`.

### Capa service (implementacion)

- **SEC-001**: Este metodo **no** invoca `jwtService.validateAccessToken`. La ausencia es deliberada y la convierte en una ruta publica. No agregar la llamada sin cambiar tambien el controller y esta especificacion.
- **SEC-002**: `CarResponse` no expone ningun dato personal, por lo que la exposicion publica no filtra informacion sensible. Si en el futuro la entidad incorpora datos internos (costo de adquisicion, aseguradora), el endpoint debe volverse autenticado.
- **REQ-005**: Se obtiene la lista con `carRepository.findAllByAvailableTrue()`.
- **REQ-006**: Si la lista esta vacia se lanza `NoContentException` con el mensaje `"No se encontraron carros"`.
- **REQ-007**: El mapeo a `CarResponse` se hace con `stream().map(...).toList()`, construyendo cada elemento con `CarResponse.builder()`.
- **GUD-001**: No se ordena la lista de forma explicita. El orden lo determina PostgreSQL; ningun cliente debe asumir un orden estable.

### Capa controller

- **REQ-008**: El endpoint es `GET /api/car/all`, declarado en `CarController` con `@GetMapping("/all")`.
- **REQ-009**: El metodo del controller no declara ningun parametro: ni `@RequestHeader`, ni `@RequestBody`, ni `@PathVariable`.
- **REQ-010**: La respuesta exitosa es `200 OK` con cuerpo `ApiResponse<List<CarResponse>>`: `result = true` y `data` con el array de carros.
- **REQ-011**: El controller arma el sobre con `ApiResponse.<List<CarResponse>>builder().result(true).data(carService.getAllCars()).build()`. El service no conoce `ApiResponse`.
- **CON-004**: El controller no contiene reglas de negocio, ni `try/catch`, ni acceso a `CarRepository`. Solo delega en el service y envuelve el resultado en el sobre `ApiResponse`.
- **CON-005**: `CarController` depende de la interfaz `CarService`, nunca de `CarServiceImpl`.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-006**: Consecuencia directa y deliberada del patron: la `NoContentException` de REQ-006 queda atrapada por ese `catch` y llega al cliente como `500` con el mensaje `"No se encontraron carros"`, no como `204`.
- **CON-007**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/car/all` |
| Headers obligatorios | ninguno |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `ApiResponse<List<CarResponse>>` |

### Respuestas de error

Toda respuesta producida por `GlobalExceptionHandler` viaja como `ApiResponse<ErrorResponse>` con `result = false`.

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Catalogo sin carros vigentes | `500` | `result = false`, `data.message = "No se encontraron carros"` |
| Fallo de conexion a la base de datos | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de la causa |

```json
HTTP/1.1 500 Internal Server Error

{
  "result": false,
  "data": {
    "message": "No se encontraron carros",
    "statusCode": 500
  }
}
```

### Peticion de ejemplo

```json
GET /api/car/all
```

### Respuesta de ejemplo

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": true,
  "data": [
    {
      "id": 3,
      "plate": "ABC123",
      "brand": "Renault",
      "model": "Logan",
      "year": 2022,
      "color": "Blanco",
      "pricePerDay": 120000.0,
      "available": true
    },
    {
      "id": 5,
      "plate": "XYZ789",
      "brand": "Mazda",
      "model": "2",
      "year": 2021,
      "color": "Rojo",
      "pricePerDay": 95000.0,
      "available": true
    }
  ]
}
```

### Capa repository (metodo usado)

```java
List<Car> findAllByAvailableTrue();
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Response.CarResponse;

import java.util.List;

public interface CarService {

    List<CarResponse> getAllCars();
}
```

### Capa service: implementacion de referencia

```java
@Override
public List<CarResponse> getAllCars() {

    try {
        List<Car> cars = carRepository.findAllByAvailableTrue();

        if (cars.isEmpty()) {
            throw new NoContentException("No se encontraron carros");
        }

        return cars.stream()
                .map(car -> CarResponse.builder()
                        .id(car.getId())
                        .plate(car.getPlate())
                        .brand(car.getBrand())
                        .model(car.getModel())
                        .year(car.getYear())
                        .color(car.getColor())
                        .pricePerDay(car.getPricePerDay())
                        .available(car.getAvailable())
                        .build())
                .toList();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Capa controller

```java
@GetMapping("/all")
public ResponseEntity<ApiResponse<List<CarResponse>>> getAllCars() {

    return ResponseEntity.ok(ApiResponse.<List<CarResponse>>builder()
            .result(true)
            .data(carService.getAllCars())
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la tabla `cars` contiene dos filas con `available = true`, When se invoca `GET /api/car/all` sin header `Authorization`, Then la respuesta es `200` con `result = true` y `data` conteniendo un array de dos `CarResponse`.
- **AC-002**: Given la tabla contiene dos filas con `available = true` y una con `available = false`, When se invoca el endpoint, Then `data` tiene tamanio 2 y ningun elemento tiene `available = false`.
- **AC-003**: Given la tabla `cars` esta vacia, When se invoca el endpoint, Then la respuesta es `500` con el mensaje `"No se encontraron carros"`, por efecto de CON-006.
- **AC-004**: Given todas las filas de `cars` tienen `available = false`, When se invoca el endpoint, Then la respuesta es `500` con el mensaje `"No se encontraron carros"`.
- **AC-005**: Given se envia un header `Authorization` con un token invalido, When se invoca el endpoint, Then la respuesta es `200` con el listado: el header se ignora por completo.
- **AC-006**: Given cualquier respuesta exitosa, When se inspecciona el cuerpo, Then el JSON raiz tiene exactamente `result` y `data`, y cada elemento de `data` contiene exactamente las ocho propiedades de `CarResponse`.
- **AC-007**: Given el catalogo vacio, When se invoca el endpoint, Then `result = false` y `data` contiene `message` y `statusCode`; `data` no es un array.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `CarServiceImpl.getAllCars`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `CarRepository` mockeado con `@Mock`; `CarServiceImpl` con `@InjectMocks`. `JwtService` no participa en este metodo.
- **Casos minimos**: lista con elementos; lista vacia; verificacion de que se invoca `findAllByAvailableTrue` y nunca `findAll`.
- **Test Data Management**: los `Car` se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=CarServiceImplTest#getAllCars_ok`; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (lista con datos, lista vacia, rama de excepcion).
- **Performance Testing**: no aplica en esta iteracion. Si el catalogo supera unos cientos de filas debe introducirse paginacion.

## 7. Rationale & Context

- El listado es publico porque un catalogo de renta existe para ser visto antes de registrarse. Exigir token para mirar los carros disponibles impediria el caso de uso principal del negocio.
- Este endpoint rompe de forma consciente la regla general del proyecto ("todo endpoint valida el token como primera linea"). La excepcion se documenta aqui y en `spec-design-car-read-by-id.md`; cualquier otro endpoint sin validacion debe considerarse un defecto.
- Se filtra por `available = true` en la consulta y no en Java para no traer a memoria filas dadas de baja, que crecen de forma indefinida al no borrarse nunca.
- El listado publico oculta los carros despublicados; la lectura por id si los muestra. La diferencia es deliberada: el catalogo es una vitrina, la consulta por id es una verificacion puntual de un recurso conocido.
- Un carro eliminado no aparece en ninguna de las dos consultas, porque su fila ya no existe: el borrado es fisico. Ver `spec-design-car-delete.md`.
- CON-006 se documenta de forma explicita porque es contraintuitivo: un catalogo vacio produce `500` y no `204`. Es el comportamiento esperado del patron de un solo `catch`.
- No se implementa paginacion porque una agencia unica maneja un parque de vehiculos pequenio. Queda registrado como el primer cambio a realizar si el catalogo crece.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - origen de las filas de la tabla `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` disponibles en el entorno.

### Data Dependencies

- **DAT-001**: Filas de `cars` creadas por `POST /api/car`. Sin altas previas el endpoint responde con el error de catalogo vacio.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller.
- **PLT-002**: Spring Data JPA para la derivacion del query method `findAllByAvailableTrue`.

### Compliance Dependencies

- Ninguna. El endpoint no expone datos personales.

## 9. Examples & Edge Cases

```java
// Caso normal: catalogo con carros vigentes
// GET /api/car/all  ->  200 con lista

// Borde: catalogo vacio o todos dados de baja
// -> NoContentException capturada por el catch -> 500 "No se encontraron carros"

// Borde: header Authorization presente pero invalido. Se ignora, la respuesta es 200.

// Correcto: filtro en la consulta
List<Car> cars = carRepository.findAllByAvailableTrue();
```

```java
// Antipatron prohibido: filtrar en memoria
// List<Car> cars = carRepository.findAll().stream()
//         .filter(Car::getAvailable)
//         .toList();

// Antipatron prohibido: agregar validacion de token a un endpoint declarado publico
// sin actualizar el controller y esta especificacion
// JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

// Antipatron prohibido: devolver la entidad
// public List<Car> getAllCars() { ... }
```

## 10. Validation Criteria

1. `CarController` declara `@GetMapping("/all")` y el metodo no tiene parametros.
2. `CarService.getAllCars()` no declara el parametro `authHeader`.
3. `CarServiceImpl.getAllCars` no contiene ninguna referencia a `jwtService`.
4. El metodo invoca `findAllByAvailableTrue` y ningun otro metodo del repositorio.
5. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
6. El tipo de retorno declarado en `CarService.getAllCars` es `List<CarResponse>`, nunca `List<Car>` ni `ApiResponse`.
7. `CarController.getAllCars` declara `ResponseEntity<ApiResponse<List<CarResponse>>>` y construye el sobre con `result(true)`.
8. `CarServiceImpl` no importa `ApiResponse`.

## 11. Related Specifications / Further Reading

- [spec-schema-car-entity.md](spec-schema-car-entity.md)
- [spec-design-car-create.md](Car/spec-design-car-create.md)
- [spec-design-car-read-by-id.md](spec-design-car-read-by-id.md)
- [spec-design-car-update.md](spec-design-car-update.md)
- [spec-design-car-delete.md](spec-design-car-delete.md)
- `../../CLAUDE.md`: secciones "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
