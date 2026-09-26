---
title: Metodo deleteCar del CRUD de carros (borrado fisico)
version: 2.0
date_created: 2026-09-21
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [design, api, car, rental, crud, delete]
---

# Introduction

Esta especificacion define el metodo `deleteCar`: la eliminacion de un carro del catalogo mediante **borrado fisico**, es decir una sentencia `DELETE` que quita la fila de la tabla `cars`. Recorre las cuatro capas implicadas (entity, repository, service y controller) y fija el contrato HTTP. Depende de `spec-schema-car-entity.md`, que es la fuente unica de verdad de la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar la baja definitiva de un `Car` por `id`, restringida al rol `admin`.

**Alcance incluido**:

- Endpoint `DELETE /api/car/{id}`.
- Uso de la entidad `Car` y de los metodos `CarRepository.findCarById` y `delete`.
- Metodo `CarService.deleteCar(String authHeader, Integer id)`.
- Implementacion en `CarServiceImpl`.
- Semantica irreversible del borrado.

**Alcance excluido**:

- Definicion de `Car`, `CarRepository` y `CarResponse`: ver `spec-schema-car-entity.md`.
- Borrado logico y papelera de reciclaje: descartados de forma explicita en esta version.
- Borrado en lote o por criterio distinto del `id`.
- Las demas operaciones del CRUD.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente ya obtuvo un token valido y su claim `rol` vale `admin`.
- No existe `SecurityFilterChain`: el endpoint recibe y valida el header a mano.
- No existe todavia la entidad `Rental`, por lo que ninguna fila externa referencia a `cars` y el borrado no puede violar una clave foranea.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con el formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims extraidos del token: `id`, `username`, `role`. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a borrar un carro. |
| **Borrado fisico** | Sentencia SQL `DELETE` sobre la tabla `cars`. La fila desaparece y el `id` deja de existir. Es irreversible desde la API. |
| **available** | Campo de la entidad que indica si el carro esta publicado y es rentable. **No** marca borrado: un carro borrado no tiene fila. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. La arma el controller; en error la arma `GlobalExceptionHandler`. |

## 3. Requirements, Constraints & Guidelines

### Capa entity

- **REQ-001**: La operacion elimina una instancia existente de `Car` definida en `spec-schema-car-entity.md`.
- **REQ-002**: No se invoca ningun setter sobre la entidad. La entidad se carga unicamente para comprobar que existe, para poder borrarla y para poder devolver sus datos.
- **CON-001**: Esta prohibido modificar `available` en este metodo. `available` significa "publicado"; despublicar es tarea de `PUT /api/car/{id}`, no de `DELETE`.

### Capa repository

- **REQ-003**: La operacion usa exactamente dos metodos: `findCarById(Integer id)` para cargar la fila y `delete(Car car)`, heredado de `JpaRepository`, para eliminarla.
- **REQ-004**: Se invoca `delete(Car)` sobre la entidad ya cargada, no `deleteById(Integer)`. La entidad hace falta de todos modos para construir la respuesta, y asi el "no existe" se resuelve en un unico punto (REQ-007).
- **CON-002**: No se invoca `save`. Un `DELETE` no persiste cambios de estado.

### Capa service (interfaz)

- **REQ-005**: La interfaz `CarService` declara `CarResponse deleteCar(String authHeader, Integer id)`.
- **REQ-006**: El metodo devuelve `CarResponse`, no `void`, para que el cliente reciba constancia de que fila concreta se elimino.
- **CON-003**: La interfaz no lleva anotaciones y no importa `Car` ni `CarRepository`.

### Capa service (implementacion)

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`.
- **SEC-002**: Solo se autoriza el borrado si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **SEC-003**: El borrado es destructivo e irreversible. La comprobacion de rol es la unica barrera: no hay confirmacion en dos pasos, ni copia de seguridad, ni papelera.
- **REQ-007**: Se carga la entidad con `carRepository.findCarById(id)`; si el `Optional` esta vacio se lanza `NotFoundException` con el mensaje `"Carro no encontrado"`.
- **REQ-008**: Se elimina la fila con `carRepository.delete(car)`.
- **REQ-009**: Se devuelve un `CarResponse` construido desde la entidad ya cargada, con los valores que la fila tenia antes de desaparecer, incluido su `available` original.
- **REQ-010**: El `CarResponse` se construye a partir del objeto `car` en memoria. Tras el `delete` ese objeto sigue siendo valido como dato plano aunque su fila ya no exista.
- **CON-004**: No se comprueba el valor de `available` antes de borrar. Un carro despublicado se borra igual que uno publicado.
- **CON-005**: Borrar dos veces el mismo `id` no es un caso especial: la segunda llamada no encuentra la fila y produce `NotFoundException` por REQ-007.

### Capa controller

- **REQ-011**: El endpoint es `DELETE /api/car/{id}`, declarado en `CarController` con `@DeleteMapping("/{id}")`.
- **REQ-012**: Los parametros del metodo son, en orden: `@RequestHeader(value = "Authorization") String authHeader` y `@PathVariable Integer id`.
- **REQ-013**: La respuesta exitosa es `200 OK` con cuerpo `ApiResponse<CarResponse>`: `result = true` y `data` con la ficha del carro eliminado. No se usa `204 No Content`, porque el cuerpo lleva informacion util y porque `NoContentException` ya ocupa ese significado en el proyecto.
- **REQ-014**: El controller arma el sobre con `ApiResponse.<CarResponse>builder().result(true).data(carService.deleteCar(authHeader, id)).build()`. El service no conoce `ApiResponse`.
- **CON-006**: El controller no contiene reglas de negocio, ni `try/catch`, ni acceso a `CarRepository`. Solo delega en el service y envuelve el resultado en el sobre `ApiResponse`.
- **CON-007**: `CarController` depende de la interfaz `CarService`, nunca de `CarServiceImpl`.
- **CON-008**: Un `id` no numerico en la URL produce `MethodArgumentTypeMismatchException`; Spring responde `400` por defecto y el service no se ejecuta.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-009**: Consecuencia directa y deliberada del patron: `ForbiddenException`, `NotFoundException` y `JwtAuthenticationException` lanzadas dentro del `try` llegan al cliente como `500` con el mensaje original, no como `403`, `404` ni `401`.
- **CON-010**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `DELETE` |
| Ruta | `/api/car/{id}` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Parametro de ruta | `id` de tipo `Integer` |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `ApiResponse<CarResponse>` y los datos de la fila eliminada |

### Respuestas de error

Toda respuesta producida por `GlobalExceptionHandler` viaja como `ApiResponse<ErrorResponse>` con `result = false`. Las respuestas que Spring genera antes de llegar al controller **no** llevan el sobre.

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| `id` no numerico en la URL | `400` | respuesta por defecto de Spring, sin sobre |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring, sin sobre |
| Token invalido o expirado | `500` | `result = false`, `data` = `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `result = false`, `data.message = "No esta autorizado"` |
| `id` inexistente o ya borrado | `500` | `result = false`, `data.message = "Carro no encontrado"` |

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
DELETE /api/car/3
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
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

### Capa repository (metodos usados)

```java
Optional<Car> findCarById(Integer id);

void delete(Car car);   // heredado de JpaRepository, no se redeclara
```

### Capa service: interfaz

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Response.CarResponse;

public interface CarService {

    CarResponse deleteCar(String authHeader, Integer id);
}
```

### Capa service: implementacion de referencia

```java
@Override
public CarResponse deleteCar(String authHeader, Integer id) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        if (!jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        Car car = carRepository.findCarById(id)
                .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

        carRepository.delete(car);

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
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<CarResponse>> deleteCar(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id) {

    return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
            .result(true)
            .data(carService.deleteCar(authHeader, id))
            .build());
}
```

## 5. Acceptance Criteria

- **AC-001**: Given un token con `rol = "admin"` y existe el carro `id = 3`, When se invoca `DELETE /api/car/3`, Then la respuesta es `200` con `result = true` y `data` con los ocho campos que tenia la fila.
- **AC-002**: Given la peticion de AC-001, When termina la operacion, Then la tabla `cars` ya no contiene ninguna fila con `id = 3` y el conteo total disminuyo en uno.
- **AC-003**: Given la peticion de AC-001, When se invoca despues `GET /api/car/3`, Then la respuesta es `500` con el mensaje `"Carro no encontrado"`.
- **AC-004**: Given la peticion de AC-001, When se invoca de nuevo `DELETE /api/car/3`, Then la respuesta es `500` con el mensaje `"Carro no encontrado"` y ninguna otra fila resulta afectada.
- **AC-005**: Given el carro `id = 4` tiene `available = false`, When se invoca `DELETE /api/car/4`, Then la fila se elimina igual que cualquier otra y la respuesta es `200` con `data.available = false`.
- **AC-006**: Given la peticion de AC-001, When se crea despues un carro nuevo con la misma placa `"ABC123"`, Then el alta tiene exito: la restriccion `UNIQUE` quedo liberada al desaparecer la fila.
- **AC-007**: Given no existe el carro `id = 999`, When se invoca `DELETE /api/car/999`, Then la respuesta es `500` con el mensaje `"Carro no encontrado"`.
- **AC-008**: Given un token con `rol = "user"`, When se invoca el endpoint, Then no se elimina ninguna fila y la respuesta es `500` con el mensaje `"No esta autorizado"`.
- **AC-009**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-010**: Given cualquier respuesta exitosa, When se inspecciona el JSON raiz, Then tiene exactamente dos propiedades, `result` y `data`, con `result = true`.
- **AC-011**: Given cualquier error lanzado dentro del service, When se inspecciona la respuesta, Then `result = false` y `data` contiene `message` y `statusCode`.
- **AC-012**: Given cualquier ejecucion de este metodo, When se inspeccionan las llamadas al repositorio, Then `save` no se invoca ni una sola vez.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `CarServiceImpl.deleteCar`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `CarRepository` y `JwtService` mockeados con `@Mock`; `CarServiceImpl` con `@InjectMocks`.
- **Casos minimos**: borrado correcto; id inexistente; rol no autorizado; token invalido; borrado de un carro con `available = false`.
- **Verificaciones obligatorias**: `verify(carRepository).delete(car)` con el mismo objeto devuelto por `findCarById`; y `verify(carRepository, never()).save(any())`.
- **Integracion**: prueba con `@DataJpaTest` que cuenta las filas antes y despues para confirmar que la fila desaparece de verdad. Es la unica forma de distinguir un borrado fisico de uno logico.
- **Test Data Management**: los `Car` se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=CarServiceImplTest#deleteCar_ok`; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (camino feliz, rol denegado, no encontrado, rama de excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- El borrado es fisico: un carro que sale del parque de la agencia deja de existir para el sistema y no tiene sentido conservar su fila. Mantener registros muertos indefinidamente obliga a filtrar en cada consulta y hace crecer la tabla sin aportar nada.
- Esta version reemplaza el borrado logico de la 1.0. La consecuencia mas importante es que `available` recupera un unico significado: "publicado y rentable". Ya no marca a la vez "dado de baja", que era la deuda tecnica registrada en `spec-schema-car-entity.md`. Un carro despublicado y uno eliminado son ahora estados claramente distintos: el primero tiene fila, el segundo no.
- Desaparece la comprobacion `"El carro ya esta dado de baja"`: sin marcador de baja no hay doble baja posible. El segundo `DELETE` sobre el mismo `id` simplemente no encuentra la fila.
- Se borra con `delete(car)` y no con `deleteById(id)` porque la entidad ya esta cargada para validar la existencia y para construir la respuesta. Usar `deleteById` obligaria a una consulta redundante o a perder los datos que se devuelven.
- Se devuelve la ficha completa en lugar de `204` para que el cliente confirme exactamente que vehiculo se elimino. Con una operacion irreversible, ese acuse importa mas que la pureza del codigo HTTP.
- La placa liberada (AC-006) es un efecto deseado: si el vehiculo salio del parque, su placa puede volver a darse de alta legitimamente, por ejemplo si el carro regresa.
- **Riesgo asumido**: la operacion es irreversible y no hay confirmacion adicional. Cuando exista la entidad `Rental`, borrar un carro con rentas asociadas violara la clave foranea o dejara historico huerfano; esta especificacion debera ampliarse entonces con una comprobacion previa de rentas, o volver al borrado logico con un campo `active` propio y separado de `available`.
- CON-009 se documenta de forma explicita porque es contraintuitivo: borrar un id inexistente produce `500` y no `404`. Es el comportamiento esperado del patron de un solo `catch`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino de la sentencia `DELETE` sobre la tabla `cars`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno; sin ellas `JwtService` no puede validar el token.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claims `sub`, `id`, `rol`.
- **DAT-002**: Fila existente en `cars` creada por `POST /api/car`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller.
- **PLT-002**: `JwtService` del propio proyecto - dependencia interna obligatoria de `CarServiceImpl`.

### Compliance Dependencies

- Ninguna. No hay obligacion de conservar el historico de vehiculos.

## 9. Examples & Edge Cases

```java
// Caso normal: admin elimina un carro
// DELETE /api/car/3 -> 200 con la ficha del carro eliminado
// La fila desaparece de la tabla cars.

// Borde: segundo DELETE sobre el mismo id
// -> NotFoundException capturada por el catch -> 500 "Carro no encontrado"

// Borde: carro despublicado. Se borra igual, sin comprobar available.
// DELETE /api/car/4  (available = false)  -> 200

// Borde: placa liberada. Tras borrar "ABC123" se puede volver a crear con esa placa.

// Correcto: cargar, borrar, responder desde el objeto en memoria
Car car = carRepository.findCarById(id)
        .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

carRepository.delete(car);
```

```java
// Antipatron prohibido: borrado logico. Ya no es el comportamiento de este metodo.
// car.setAvailable(false);
// carRepository.save(car);

// Antipatron prohibido: deleteById, que obliga a consultar dos veces
// carRepository.deleteById(id);

// Antipatron prohibido: devolver void y responder 204
// public void deleteCar(String authHeader, Integer id) { ... }

// Antipatron prohibido: rechazar el borrado de un carro despublicado
// if (!car.getAvailable()) { throw new BadRequestException("El carro ya esta dado de baja"); }
```

## 10. Validation Criteria

1. `CarController` declara `@DeleteMapping("/{id}")` con los dos parametros de REQ-012 en ese orden y devuelve `ResponseEntity<ApiResponse<CarResponse>>`.
2. `CarController` no importa `CarRepository`, `Car` ni `CarServiceImpl`.
3. La primera sentencia dentro del `try` de `deleteCar` es la llamada a `jwtService.validateAccessToken(authHeader)`.
4. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
5. `deleteCar` invoca unicamente `findCarById` y `delete` del repositorio.
6. El cuerpo de `deleteCar` no contiene ninguna llamada a `save` ni ningun setter sobre la entidad.
7. Tras ejecutar el metodo contra una base real, la fila correspondiente ya no existe en `cars`.
8. El tipo de retorno declarado en `CarService.deleteCar` es `CarResponse`, nunca `Car`, `void` ni `ApiResponse`.
9. `CarServiceImpl` no importa `ApiResponse`: el sobre solo lo arma el controller.

## 11. Related Specifications / Further Reading

- [spec-schema-car-entity.md](spec-schema-car-entity.md)
- [spec-design-car-create.md](spec-design-car-create.md)
- [spec-design-car-read-all.md](spec-design-car-read-all.md)
- [spec-design-car-read-by-id.md](spec-design-car-read-by-id.md)
- [spec-design-car-update.md](spec-design-car-update.md)
- `../../CLAUDE.md`: secciones "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
