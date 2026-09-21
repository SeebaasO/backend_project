---
title: Metodo createProduct del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [design, api, product, crud, create]
---

# Introduction

Esta especificacion define el metodo `createProduct`: el alta de un producto en el catalogo. Cubre la capa controller, la interfaz de service, la implementacion y el contrato HTTP. Depende de `spec-schema-product-entity.md`, que define la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar de forma completa y no ambigua la operacion de creacion de un `Product`, para que pueda implementarse sin decisiones adicionales.

**Alcance incluido**:

- Endpoint `POST /api/product`.
- Metodo `ProductService.createProduct(String authHeader, CreateProductRequest createProductRequest)`.
- Implementacion en `ProductServiceImpl`.
- Validacion de token, autorizacion por rol y reglas de negocio del alta.

**Alcance excluido**:

- Definicion de `Product`, `ProductRepository`, `CreateProductRequest` y `ProductResponse`: ver `spec-schema-product-entity.md`.
- Las demas operaciones del CRUD.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente ya obtuvo un token valido via `POST /api/auth/login`.
- No existe `SecurityFilterChain`: cada endpoint recibe y valida el header a mano.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con el formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims extraidos del token: `id`, `username`, `role`. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a escribir en el catalogo. |
| **Capa service** | Clase bajo `service/impl` que contiene las reglas de negocio. |
| **Excepcion de dominio** | Una de `BadRequestException`, `NotFoundException`, `ForbiddenException`, `NoContentException`, `JwtAuthenticationException`. Todas extienden `RuntimeException`. |

## 3. Requirements, Constraints & Guidelines

### Contrato HTTP

- **REQ-001**: El endpoint es `POST /api/product`, declarado en `ProductController` con `@RequestMapping("/api/product")` a nivel de clase y `@PostMapping` sin path adicional.
- **REQ-002**: El metodo del controller recibe `@RequestHeader(value = "Authorization") String authHeader` como primer parametro y `@Valid @RequestBody CreateProductRequest createProductRequest` como segundo.
- **REQ-003**: La respuesta exitosa es `200 OK` con cuerpo `ProductResponse`. Se usa `ResponseEntity.ok(...)` por coherencia con `UserController`, aunque `201 Created` seria mas canonico en REST.
- **CON-001**: El controller no contiene logica, ni `try/catch`, ni acceso a `ProductRepository`. Solo delega en una linea.

### Seguridad

- **SEC-001**: La primera instruccion dentro del `try` del service es `jwtService.validateAccessToken(authHeader)`. Omitirla deja el endpoint publico.
- **SEC-002**: Solo se autoriza el alta si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **SEC-003**: El `id` del creador no se persiste. El catalogo es unico y no registra propietario.

### Reglas de negocio

- **REQ-004**: El `id` del producto lo genera la base de datos. Cualquier `id` enviado por el cliente se ignora, porque `CreateProductRequest` no expone ese campo.
- **REQ-005**: Se permiten nombres duplicados: no se consulta la existencia previa por `name`.
- **REQ-006**: El producto se construye con `Product.builder()` mapeando los cuatro campos del request uno a uno, sin transformaciones.
- **REQ-007**: Tras `productRepository.save(product)` se devuelve `ProductResponse` construido desde la entidad guardada, de modo que `id` viaje con su valor generado.
- **GUD-001**: Se asigna el resultado de `save` a una variable (`Product saved = productRepository.save(product);`) y se mapea desde ella. Es la forma garantizada de disponer del `id` generado.

### Validacion de entrada

- **REQ-008**: `@Valid` en el controller activa las anotaciones de `CreateProductRequest`. Un cuerpo invalido produce `MethodArgumentNotValidException` antes de entrar al service.
- **CON-002**: `GlobalExceptionHandler` no declara handler para `MethodArgumentNotValidException`. En consecuencia, un cuerpo invalido produce la respuesta `400` por defecto de Spring, no un `ErrorResponse`. Es el comportamiento actual del proyecto y esta especificacion no lo cambia.

### Manejo de errores

- **PAT-001**: El cuerpo del metodo va dentro de un unico `try`, cerrado con:

```java
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

- **CON-003**: Consecuencia directa y deliberada del patron: la `ForbiddenException` de SEC-002 y cualquier `JwtAuthenticationException` lanzada por `validateAccessToken` quedan atrapadas por ese `catch` y llegan al cliente como `500` con el mensaje original, no como `403` ni `401`. Es el patron elegido en el proyecto y no debe modificarse en esta especificacion.
- **CON-004**: No se introduce multicatch, ni clase base comun de excepciones, ni relanzado selectivo.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/product` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Header obligatorio | `Content-Type: application/json` |
| Cuerpo | `CreateProductRequest` |
| Respuesta exitosa | `200 OK` con `ProductResponse` |

### Respuestas de error

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Cuerpo invalido (`@Valid`) | `400` | respuesta por defecto de Spring |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring (falta un parametro obligatorio) |
| Token invalido o expirado | `500` | `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `ErrorResponse` con mensaje `"No esta autorizado"` |
| Fallo de persistencia | `500` | `ErrorResponse` con el mensaje de la causa |

### Peticion de ejemplo

```json
POST /api/product
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "name": "Teclado mecanico",
  "description": "Teclado 87 teclas switch rojo",
  "price": 189000.0,
  "stock": 25
}
```

### Respuesta de ejemplo

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 7,
  "name": "Teclado mecanico",
  "description": "Teclado 87 teclas switch rojo",
  "price": 189000.0,
  "stock": 25
}
```

### Interfaz de service

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateProductRequest;
import backend_project.backend_project.model.Response.ProductResponse;

public interface ProductService {

    ProductResponse createProduct(String authHeader, CreateProductRequest createProductRequest);
}
```

### Implementacion de referencia

```java
@Override
public ProductResponse createProduct(String authHeader, CreateProductRequest createProductRequest) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        if (!jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        Product product = Product.builder()
                .name(createProductRequest.getName())
                .description(createProductRequest.getDescription())
                .price(createProductRequest.getPrice())
                .stock(createProductRequest.getStock())
                .build();

        Product saved = productRepository.save(product);

        return ProductResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .description(saved.getDescription())
                .price(saved.getPrice())
                .stock(saved.getStock())
                .build();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Controller

```java
@PostMapping
public ResponseEntity<ProductResponse> createProduct(
        @RequestHeader(value = "Authorization") String authHeader,
        @Valid @RequestBody CreateProductRequest createProductRequest) {

    return ResponseEntity.ok(productService.createProduct(authHeader, createProductRequest));
}
```

## 5. Acceptance Criteria

- **AC-001**: Given un token valido con `rol = "admin"` y un cuerpo valido, When se invoca `POST /api/product`, Then la respuesta es `200` con un `ProductResponse` cuyo `id` no es nulo y cuyos cuatro campos restantes coinciden con el cuerpo enviado.
- **AC-002**: Given la peticion de AC-001, When termina la operacion, Then existe en la tabla `products` una fila nueva con esos valores.
- **AC-003**: Given un token valido con `rol = "user"`, When se invoca `POST /api/product`, Then no se persiste ninguna fila y la respuesta es `500` con el mensaje `"No esta autorizado"`, por efecto de CON-003.
- **AC-004**: Given un token expirado, When se invoca `POST /api/product`, Then no se persiste ninguna fila y la respuesta es `500` con el mensaje producido por `validateAccessToken`.
- **AC-005**: Given un cuerpo con `price = -5.0`, When se invoca el endpoint, Then Spring rechaza la peticion con `400` y `ProductServiceImpl.createProduct` no llega a ejecutarse.
- **AC-006**: Given un cuerpo sin el campo `stock`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@NotNull`.
- **AC-007**: Given un producto ya existente con `name = "Mouse"`, When se crea otro con el mismo `name`, Then la operacion tiene exito y coexisten dos filas con `name = "Mouse"` e `id` distinto.
- **AC-008**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ProductServiceImpl.createProduct`; integracion opcional sobre el endpoint con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito (`spring-boot-starter-test`).
- **Dobles**: `ProductRepository` y `JwtService` mockeados con `@Mock`; `ProductServiceImpl` con `@InjectMocks`.
- **Casos minimos**: alta correcta como `admin`; rol no autorizado; token invalido; verificacion de que `save` recibe un `Product` con los valores del request mediante `ArgumentCaptor`.
- **Test Data Management**: los objetos se construyen en cada test con `builder()`. Sin base de datos en los tests unitarios.
- **CI/CD Integration**: `./mvnw test -Dtest=ProductServiceImplTest#createProduct_ok` para un caso puntual; `./mvnw test` para la suite.
- **Coverage Requirements**: 100% de las ramas de este metodo (camino feliz, rama de rol denegado, rama de excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- La escritura se restringe a `admin` reproduciendo el criterio ya usado en `UserServiceImpl.getUserAll`, donde la comprobacion de rol es una comparacion literal contra `"admin"`. Mantener un unico criterio evita inventar un modelo de permisos nuevo en una aplicacion sencilla.
- Se devuelve `200` en lugar de `201` para no divergir de `UserController`, que responde `ResponseEntity.ok(...)` en todas sus operaciones. La consistencia interna pesa mas que la pureza REST en este proyecto.
- No se valida unicidad de `name` porque la entidad no declara restriccion unica. Validar en el service lo que la base de datos no garantiza produciria una falsa sensacion de integridad ante peticiones concurrentes.
- La captura del retorno de `save` es necesaria: el `id` generado es el unico dato que el cliente no envio y si necesita recibir.
- CON-003 se documenta de forma explicita porque es contraintuitivo. Quien pruebe el endpoint con un token de rol `user` vera `500` y podria creer que hay un defecto; es el comportamiento esperado del patron de un solo `catch`.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino de la insercion en la tabla `products`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno; sin ellas `JwtService` no puede validar el token.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claims `sub`, `id`, `rol`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web` para el controller y starter `validation` para `@Valid`.
- **PLT-002**: `JwtService` del propio proyecto - dependencia interna obligatoria de `ProductServiceImpl`.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal: admin crea producto
// POST /api/product  ->  200 con id generado

// Borde: stock cero. Valido, se persiste tal cual.
{ "name": "Mouse", "description": "Mouse optico", "price": 45000.0, "stock": 0 }

// Borde: precio cero. Rechazado por @Positive antes de llegar al service. -> 400
{ "name": "Mouse", "description": "Mouse optico", "price": 0.0, "stock": 3 }

// Borde: campos extra en el JSON. Jackson los ignora por defecto; el alta funciona.
{ "name": "Mouse", "description": "Mouse optico", "price": 45000.0, "stock": 3, "color": "negro" }

// Borde: rol user. El service lanza ForbiddenException, el catch la convierte
// en InternalServerErrorException y el cliente recibe 500, no 403. Ver CON-003.
```

```java
// Antipatron prohibido: relanzar de forma selectiva para "arreglar" el codigo HTTP
// } catch (ForbiddenException e) {
//     throw e;
// } catch (Exception e) {
//     throw new InternalServerErrorException(e.getMessage());
// }
```

## 10. Validation Criteria

1. `ProductController` declara `@PostMapping` sin path y devuelve `ResponseEntity<ProductResponse>`.
2. `ProductController` no importa `ProductRepository`, `Product` ni `ProductServiceImpl`; depende solo de la interfaz `ProductService`.
3. La primera sentencia dentro del `try` de `createProduct` es la llamada a `jwtService.validateAccessToken(authHeader)`.
4. El metodo contiene exactamente un bloque `try` y un bloque `catch (Exception e)`.
5. `createProduct` no invoca ningun metodo de busqueda del repositorio: solo `save`.
6. El tipo de retorno declarado es `ProductResponse`, nunca `Product`.

## 11. Related Specifications / Further Reading

- [spec-schema-product-entity.md](spec-schema-product-entity.md)
- [spec-design-product-read-all.md](spec-design-product-read-all.md)
- [spec-design-product-read-by-id.md](spec-design-product-read-by-id.md)
- [spec-design-product-update.md](spec-design-product-update.md)
- [spec-design-product-delete.md](spec-design-product-delete.md)
- `CLAUDE.md`: seccion "Manejo de errores en los services" y "Seguridad: no hay filtro de Spring Security".
