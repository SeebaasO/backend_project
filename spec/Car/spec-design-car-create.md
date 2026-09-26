---
title: Metodo createCar del CRUD de carros
version: 1.0
date_created: 2026-09-21
last_updated: 2026-09-21
owner: Equipo backend_project
tags: [design, api, car, rental, crud, create]
---

# Introduction

Esta especificacion define el metodo `createCar`: el alta de un carro en el catalogo de la agencia. Recorre las cuatro capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-car-entity.md`, que es la fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar de forma completa y no ambigua la operacion de creacion de un `Car`, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `POST /api/car`.
- Uso de la entidad `Car` y del metodo `CarRepository.findCarByPlate` / `save`.
- Metodo `CarService.createCar(String authHeader, CreateCarRequest createCarRequest)`.
- Implementacion en `CarServiceImpl`.
- Validacion de token, autorizacion por rol `admin` y reglas de negocio del alta.

**Alcance excluido**:

- Definicion de `Car`, `CarRepository`, `CreateCarRequest` y `CreateCarResponse`: ver `spec-schema-car-entity.md`.
- Las demas operaciones del CRUD.
- Carga de imagenes del vehiculo y asignacion a sucursal: fuera de alcance.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente ya obtuvo un token valido via `POST /api/auth/login` y su claim `rol` vale `admin`.
- No existe `SecurityFilterChain`: el endpoint recibe y valida el header a mano.
- Todos los carros pertenecen a la unica agencia del sistema.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con el formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims extraidos del token: `id`, `username`, `role`. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a escribir en el catalogo. |
| **Capa service** | Clase bajo `service/impl` que contiene las reglas de negocio. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. La arma el controller; en error la arma `GlobalExceptionHandler`. |
| **Placa normalizada** | Resultado de `plate.trim().toUpperCase()`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: La operacion usa la entidad `Car` definida en `spec-schema-car-entity.md`. Esta especificacion no la modifica.
- **REQ-002**: El alta construye la entidad con `Car.builder()` mapeando los seis campos del request y fijando `available(true)`.
- **CON-001**: El `id` no se asigna en codigo: lo genera la base de datos con `GenerationType.IDENTITY`.

### Capa repository

- **REQ-003**: La operacion usa exactamente dos metodos: `findCarByPlate(String plate)` para verificar unicidad y `save(Car car)` para persistir.
- **CON-002**: No se invoca ningun otro metodo del repositorio. En particular, no se lista el catalogo completo para buscar duplicados.

### Capa service (interfaz)

- **REQ-004**: La interfaz `CarService` declara `CreateCarResponse createCar(String authHeader, CreateCarRequest createCarRequest)`.
- **CON-003**: La interfaz no lleva anotaciones y no importa `Car` ni `CarRepository`. Solo tipos de `model`.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`. Omitirla deja el endpoint publico.
- **SEC-002**: Solo se autoriza el alta si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **SEC-003**: El `id` del usuario que crea el carro no se persiste. El catalogo es unico y no registra propietario.
- **REQ-005**: La placa se normaliza con `createCarRequest.getPlate().trim().toUpperCase()` antes de cualquier consulta y antes de persistir.
- **REQ-006**: Si `carRepository.findCarByPlate(placaNormalizada)` devuelve un `Optional` presente, se lanza `BadRequestException` con el mensaje `"La placa ya esta registrada"`.
- **REQ-007**: El carro se crea siempre con `available = true`, sin importar lo que envie el cliente: `CreateCarRequest` no expone ese campo.
- **REQ-008**: Tras `carRepository.save(car)` se devuelve un `CreateCarResponse` con el unico campo `id`, tomado de la entidad guardada. El alta **no** devuelve el resto de los datos del carro: el cliente ya los envio y puede recuperarlos con `GET /api/car/{id}`.
- **GUD-001**: Se asigna el resultado de `save` a una variable (`Car saved = carRepository.save(car);`) y se mapea desde ella. Es la forma garantizada de disponer del `id` generado.

### Capa controller

- **REQ-009**: El endpoint es `POST /api/car`, declarado en `CarController` con `@RequestMapping("/api/car")` a nivel de clase y `@PostMapping` sin path adicional.
- **REQ-010**: El metodo del controller recibe `@RequestHeader(value = "Authorization") String authHeader` como primer parametro y `@Valid @RequestBody CreateCarRequest createCarRequest` como segundo.
- **REQ-011**: La respuesta exitosa es `200 OK` con cuerpo `ApiResponse<CreateCarResponse>`: `result = true` y `data` con el `id` generado. Se usa `ResponseEntity.ok(...)` por coherencia con `UserController`, aunque `201 Created` seria mas canonico en REST.
- **REQ-016**: El controller arma el sobre con `ApiResponse.<CreateCarResponse>builder().result(true).data(carService.createCar(...)).build()`. El service no conoce `ApiResponse`.
- **CON-004**: El controller no contiene reglas de negocio, ni `try/catch`, ni acceso a `CarRepository`. Solo delega en el service y envuelve el resultado en el sobre `ApiResponse`.
- **CON-005**: `CarController` depende de la interfaz `CarService`, nunca de `CarServiceImpl`.

### Validacion de entrada

- **REQ-012**: `@Valid` en el controller activa las anotaciones de `CreateCarRequest`. Un cuerpo invalido produce `MethodArgumentNotValidException` antes de entrar al service.
- **CON-006**: `GlobalExceptionHandler` no declara handler para `MethodArgumentNotValidException`. En consecuencia, un cuerpo invalido produce la respuesta `400` por defecto de Spring, no un `ErrorResponse`. Es el comportamiento actual del proyecto y esta especificacion no lo cambia.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-007**: Consecuencia directa y deliberada del patron: la `ForbiddenException` de SEC-002, la `BadRequestException` de REQ-006 y cualquier `JwtAuthenticationException` lanzada por `validateAccessToken` quedan atrapadas por ese `catch` y llegan al cliente como `500` con el mensaje original, no como `403`, `400` ni `401`.
- **CON-008**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/car` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Header obligatorio | `Content-Type: application/json` |
| Cuerpo | `CreateCarRequest` |
| Respuesta exitosa | `200 OK` con `ApiResponse<CreateCarResponse>` |

### Respuestas de error

Toda respuesta producida por `GlobalExceptionHandler` viaja como `ApiResponse<ErrorResponse>` con `result = false`. Las respuestas generadas por Spring antes de llegar al controller **no** llevan el sobre.

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Cuerpo invalido (`@Valid`) | `400` | respuesta por defecto de Spring, sin sobre |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring, sin sobre |
| Token invalido o expirado | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `result = false`, `data.message = "No esta autorizado"` |
| Placa ya registrada | `500` | `result = false`, `data.message = "La placa ya esta registrada"` |
| Violacion de `UNIQUE` en carrera concurrente | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de la causa |

```json
HTTP/1.1 500 Internal Server Error

{
  "result": false,
  "data": {
    "message": "La placa ya esta registrada",
    "statusCode": 500
  }
}
```

### Peticion de ejemplo

```json
POST /api/car
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "plate": "ABC123",
  "brand": "Renault",
  "model": "Logan",
  "year": 2022,
  "color": "Blanco",
  "pricePerDay": 120000.0
}
```

### Respuesta de ejemplo

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": true,
  "data": {
    "id": 3
  }
}
```

### Capa repository (metodos usados)

```java
Optional<Car> findCarByPlate(String plate);

Car save(Car car);
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateCarRequest;
import backend_project.backend_project.model.Response.CreateCarResponse;

public interface CarService {

    CreateCarResponse createCar(String authHeader, CreateCarRequest createCarRequest);
}
```

### Capa service: implementacion de referencia

```java
@Service
@RequiredArgsConstructor
public class CarServiceImpl implements CarService {

    private final CarRepository carRepository;
    private final JwtService jwtService;

    @Override
    public CreateCarResponse createCar(String authHeader, CreateCarRequest createCarRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            if (!jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            String plate = createCarRequest.getPlate().trim().toUpperCase();

            carRepository.findCarByPlate(plate)
                    .ifPresent(c -> { throw new BadRequestException("La placa ya esta registrada"); });

            Car car = Car.builder()
                    .plate(plate)
                    .brand(createCarRequest.getBrand())
                    .model(createCarRequest.getModel())
                    .year(createCarRequest.getYear())
                    .color(createCarRequest.getColor())
                    .pricePerDay(createCarRequest.getPricePerDay())
                    .available(true)
                    .build();

            Car saved = carRepository.save(car);

            return CreateCarResponse.builder()
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
@RequestMapping("/api/car")
public class CarController {

    private final CarService carService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateCarResponse>> createCar(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody CreateCarRequest createCarRequest) {

        return ResponseEntity.ok(ApiResponse.<CreateCarResponse>builder()
                .result(true)
                .data(carService.createCar(authHeader, createCarRequest))
                .build());
    }
}
```

## 5. Acceptance Criteria

- **AC-001**: Given un token valido con `rol = "admin"` y un cuerpo valido con placa no registrada, When se invoca `POST /api/car`, Then la respuesta es `200` con `result = true` y `data` conteniendo exactamente una propiedad, `id`, con valor no nulo.
- **AC-002**: Given la peticion de AC-001, When termina la operacion, Then existe en la tabla `cars` una fila nueva con esos valores y `available = true`.
- **AC-003**: Given un cuerpo con `plate = "  abc123 "`, When se crea el carro, Then la fila persistida tiene `plate = "ABC123"`. La respuesta no incluye la placa: se verifica consultando `GET /api/car/{id}` con el `id` devuelto.
- **AC-004**: Given ya existe un carro con `plate = "ABC123"`, When se invoca el alta con esa misma placa, Then no se persiste ninguna fila y la respuesta es `500` con el mensaje `"La placa ya esta registrada"`, por efecto de CON-007.
- **AC-005**: Given un token valido con `rol = "user"`, When se invoca `POST /api/car`, Then no se persiste ninguna fila y la respuesta es `500` con el mensaje `"No esta autorizado"`.
- **AC-006**: Given un token expirado, When se invoca `POST /api/car`, Then no se persiste ninguna fila y la respuesta es `500` con el mensaje producido por `validateAccessToken`.
- **AC-007**: Given un cuerpo con `pricePerDay = -1.0`, When se invoca el endpoint, Then Spring rechaza la peticion con `400` y `CarServiceImpl.createCar` no llega a ejecutarse.
- **AC-008**: Given un cuerpo sin el campo `year`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@NotNull`.
- **AC-009**: Given un cuerpo que incluye `"available": false`, When se crea el carro, Then el campo se ignora y la fila persistida tiene `available = true`.
- **AC-010**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-011**: Given un alta exitosa, When se inspecciona `data`, Then contiene unicamente la propiedad `id`: no aparecen `plate`, `brand`, `model`, `year`, `color`, `pricePerDay` ni `available`.
- **AC-012**: Given el `id` devuelto en `data.id` por un alta exitosa, When se invoca `GET /api/car/{id}` con ese valor, Then la respuesta es `200` con los datos completos del carro recien creado en su propio `data`.
- **AC-013**: Given cualquier error lanzado dentro del service, When se inspecciona la respuesta, Then el JSON raiz tiene `result = false` y `data` contiene `message` y `statusCode`.
- **AC-014**: Given una respuesta exitosa, When se inspecciona el JSON raiz, Then tiene exactamente dos propiedades: `result` y `data`.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `CarServiceImpl.createCar`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `CarRepository` y `JwtService` mockeados con `@Mock`; `CarServiceImpl` con `@InjectMocks`.
- **Casos minimos**: alta correcta como `admin`; placa duplicada; rol no autorizado; token invalido; normalizacion de placa verificada con `ArgumentCaptor<Car>` sobre `save`.
- **Test Data Management**: los objetos se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=CarServiceImplTest#createCar_ok` para un caso puntual; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (camino feliz, rol denegado, placa duplicada, rama de excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- La escritura se restringe a `admin` reproduciendo el criterio ya usado en `UserServiceImpl.getUserAll`, donde la comprobacion de rol es una comparacion literal contra `"admin"`. Mantener un unico criterio evita inventar un modelo de permisos nuevo.
- Si se valida unicidad de placa, a diferencia del CRUD de productos: la placa es un identificador legal y dos filas con la misma placa representarian el mismo vehiculo fisico. La comprobacion en el service existe para dar un mensaje legible; la garantia real es la restriccion `UNIQUE`.
- La normalizacion a mayusculas evita que `abc123` y `ABC123` convivan como dos vehiculos distintos, cosa que la restriccion `UNIQUE` de PostgreSQL no impediria por ser sensible a mayusculas.
- `available = true` fijo en el alta simplifica el contrato: dar de alta un carro significa publicarlo. Para crear un carro inactivo se crea y luego se da de baja con `DELETE`.
- Se devuelve `200` en lugar de `201` para no divergir de `UserController`. La consistencia interna pesa mas que la pureza REST en este proyecto.
- La captura del retorno de `save` es necesaria: el `id` generado es el unico dato que el cliente no envio y si necesita recibir.
- El alta devuelve un DTO propio, `CreateCarResponse`, con solo el `id`, en lugar de reutilizar `CarResponse`. Reutilizarlo obligaria a rellenar ocho campos que el cliente acaba de enviar, o a devolverlo con siete propiedades nulas, lo que produciria un contrato confuso. Un DTO de una sola propiedad expresa exactamente lo que el alta aporta: el identificador generado.
- El resto de los datos se obtienen con `GET /api/car/{id}`, que ya existe y es publico. Por eso ninguna informacion queda inaccesible tras el cambio.
- Esta es la unica operacion del CRUD con un Response propio. `read all`, `read by id`, `update` y `delete` siguen usando `CarResponse`.
- CON-007 se documenta de forma explicita porque es contraintuitivo. Quien pruebe el endpoint con placa duplicada vera `500` y podria creer que hay un defecto; es el comportamiento esperado del patron de un solo `catch`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino de la insercion en la tabla `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno; sin ellas `JwtService` no puede validar el token.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claims `sub`, `id`, `rol`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller y starter `validation` para `@Valid`.
- **PLT-002**: `JwtService` del propio proyecto - dependencia interna obligatoria de `CarServiceImpl`.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal: admin crea carro
// POST /api/car  ->  200 con id generado y available = true

// Borde: placa en minusculas y con espacios. Se normaliza a "ABC123".
{ "plate": " abc123 ", "brand": "Renault", "model": "Logan", "year": 2022, "color": "Blanco", "pricePerDay": 120000.0 }

// Borde: precio cero. Rechazado por @Positive antes de llegar al service. -> 400
{ "plate": "XYZ789", "brand": "Mazda", "model": "2", "year": 2021, "color": "Rojo", "pricePerDay": 0.0 }

// Borde: anio futuro lejano. 2101 viola @Max(2100). -> 400
{ "plate": "XYZ789", "brand": "Mazda", "model": "2", "year": 2101, "color": "Rojo", "pricePerDay": 90000.0 }

// Borde: campos extra en el JSON. Jackson los ignora por defecto; el alta funciona
// y "available" enviado por el cliente no tiene ningun efecto.
{ "plate": "XYZ789", "brand": "Mazda", "model": "2", "year": 2021, "color": "Rojo", "pricePerDay": 90000.0, "available": false }

// Borde: rol user. El service lanza ForbiddenException, el catch la convierte
// en InternalServerErrorException y el cliente recibe 500, no 403. Ver CON-007.
```

```java
// Antipatron prohibido: relanzar de forma selectiva para "arreglar" el codigo HTTP
// } catch (ForbiddenException e) {
//     throw e;
// } catch (Exception e) {
//     throw new InternalServerErrorException(e.getMessage());
// }

// Antipatron prohibido: buscar duplicados cargando todo el catalogo
// carRepository.findAll().stream().anyMatch(c -> c.getPlate().equals(plate));
```

## 10. Validation Criteria

1. `CarController` declara `@PostMapping` sin path y devuelve `ResponseEntity<ApiResponse<CreateCarResponse>>`.
2. `CarController` no importa `CarRepository`, `Car` ni `CarServiceImpl`; depende solo de la interfaz `CarService`.
3. La primera sentencia dentro del `try` de `createCar` es la llamada a `jwtService.validateAccessToken(authHeader)`.
4. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
5. `createCar` invoca unicamente `findCarByPlate` y `save` del repositorio.
6. `createCar` fija `available(true)` de forma literal en el builder.
7. El tipo de retorno declarado en `CarService.createCar` es `CreateCarResponse`, nunca `Car`, `CarResponse` ni `ApiResponse`.
8. `CreateCarResponse` declara exactamente un campo: `id`.
9. `CarServiceImpl` no importa `ApiResponse`: el sobre solo lo arma el controller.

## 11. Related Specifications / Further Reading

- [spec-schema-car-entity.md](spec-schema-car-entity.md)
- [spec-design-car-read-all.md](spec-design-car-read-all.md)
- [spec-design-car-read-by-id.md](spec-design-car-read-by-id.md)
- [spec-design-car-update.md](spec-design-car-update.md)
- [spec-design-car-delete.md](spec-design-car-delete.md)
- `../../CLAUDE.md`: secciones "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
