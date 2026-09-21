---
title: Metodo getProductById del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [design, api, product, crud, read]
---

# Introduction

Esta especificacion define el metodo `getProductById`: la consulta de un producto individual por su identificador. Complementa `spec-design-product-read-all.md` y depende de `spec-schema-product-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la lectura de un unico `Product` por `id`, incluyendo el tratamiento del caso "no existe".

**Alcance incluido**:

- Endpoint `GET /api/product/{id}`.
- Metodo `ProductService.getProductById(String authHeader, Integer id)`.
- Implementacion en `ProductServiceImpl`.

**Alcance excluido**:

- Busqueda por nombre u otros criterios.
- Listado completo y operaciones de escritura.
- Definicion de entidad, repositorio y DTOs: ver `spec-schema-product-entity.md`.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- Cualquier usuario autenticado puede consultar un producto, sin importar su rol.
- El cliente obtuvo el `id` de una respuesta previa del listado o del alta.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con formato `Bearer <token>`. |
| **Path variable** | Segmento variable de la URL, capturado con `@PathVariable`. |
| **NotFoundException** | Excepcion de dominio del proyecto asociada al codigo HTTP `404` en `GlobalExceptionHandler`. |
| **Optional** | Contenedor de `java.util` que representa un valor posiblemente ausente. |

## 3. Requirements, Constraints & Guidelines

### Contrato HTTP

- **REQ-001**: El endpoint es `GET /api/product/{id}`, declarado con `@GetMapping("/{id}")`.
- **REQ-002**: El metodo del controller recibe `@RequestHeader(value = "Authorization") String authHeader` como primer parametro y `@PathVariable Integer id` como segundo.
- **REQ-003**: La respuesta exitosa es `200 OK` con cuerpo `ProductResponse`.
- **CON-001**: El controller no contiene logica ni `try/catch`. Delega en una sola linea.
- **CON-002**: El orden de parametros es siempre `authHeader` primero, en todos los metodos del proyecto.

### Seguridad

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`. Omitirla deja el endpoint publico.
- **SEC-002**: No hay comprobacion de rol. Cualquier token valido autoriza la lectura.
- **SEC-003**: No existe comprobacion de propiedad del registro: el catalogo es unico y los productos no tienen dueno.

### Reglas de negocio

- **REQ-004**: La busqueda usa `productRepository.findProductById(id)`, que devuelve `Optional<Product>`.
- **REQ-005**: Si el `Optional` esta vacio se lanza `NotFoundException` con el mensaje `"Producto no encontrado"`.
- **REQ-006**: La resolucion del `Optional` se escribe con `orElseThrow`:

```java
Product product = productRepository.findProductById(id)
        .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
```

- **REQ-007**: El mapeo a `ProductResponse` se hace con `builder()`, incluyendo `id`.
- **CON-003**: No se usa `isPresent()` mas `get()`, ni comparaciones contra `null`.

### Manejo de errores

- **PAT-001**: Cuerpo del metodo dentro de un unico `try`, cerrado con `catch (Exception e) { throw new InternalServerErrorException(e.getMessage()); }`.
- **CON-004**: Consecuencia directa y deliberada del patron: la `NotFoundException` de REQ-005 se lanza dentro del `try`, es atrapada por ese `catch` y llega al cliente como `500` con el mensaje `"Producto no encontrado"`, no como `404`.
- **CON-005**: Un `id` no numerico en la URL (por ejemplo `/api/product/abc`) produce `MethodArgumentTypeMismatchException` antes de entrar al service. `GlobalExceptionHandler` no la maneja, por lo que Spring responde con su error por defecto (`400`).

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/product/{id}` |
| Path variable | `id` de tipo `Integer`, obligatorio |
| Header obligatorio | `Authorization: Bearer <token>` |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `ProductResponse` |

### Respuestas de error

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| `id` no numerico | `400` | respuesta por defecto de Spring |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring |
| Token invalido o expirado | `500` | `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| `id` inexistente | `500` | `ErrorResponse` con mensaje `"Producto no encontrado"` |
| Fallo de acceso a datos | `500` | `ErrorResponse` con el mensaje de la causa |

### Peticion y respuesta de ejemplo

```json
GET /api/product/7
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

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
ProductResponse getProductById(String authHeader, Integer id);
```

### Implementacion de referencia

```java
@Override
public ProductResponse getProductById(String authHeader, Integer id) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        Product product = productRepository.findProductById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Controller

```java
@GetMapping("/{id}")
public ResponseEntity<ProductResponse> getProductById(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id) {

    return ResponseEntity.ok(productService.getProductById(authHeader, id));
}
```

## 5. Acceptance Criteria

- **AC-001**: Given existe un producto con `id = 7` y un token valido de rol `user`, When se invoca `GET /api/product/7`, Then la respuesta es `200` con un `ProductResponse` cuyo `id` es `7`.
- **AC-002**: Given la respuesta de AC-001, When se inspecciona el cuerpo, Then contiene exactamente las claves `id`, `name`, `description`, `price` y `stock`.
- **AC-003**: Given no existe producto con `id = 999`, When se invoca `GET /api/product/999`, Then la respuesta es `500` con mensaje `"Producto no encontrado"`, por efecto de CON-004.
- **AC-004**: Given un token valido de rol `admin`, When se invoca el endpoint, Then el resultado es identico al de un token de rol `user`.
- **AC-005**: Given un token expirado, When se invoca el endpoint, Then la respuesta es `500` y no se ejecuta ninguna consulta contra `products`.
- **AC-006**: Given la URL `/api/product/abc`, When se invoca el endpoint, Then la respuesta es `400` y el service no se ejecuta.
- **AC-007**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-008**: Given un producto con `stock = 0`, When se consulta por su `id`, Then se devuelve normalmente con `"stock": 0`.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ProductServiceImpl.getProductById`; integracion opcional con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito.
- **Dobles**: `productRepository.findProductById(id)` mockeado para devolver `Optional.of(product)` y, en otro test, `Optional.empty()`.
- **Casos minimos**: id existente; id inexistente; token invalido.
- **Test Data Management**: objetos `Product` construidos en memoria con `builder()`.
- **CI/CD Integration**: `./mvnw test -Dtest=ProductServiceImplTest#getProductById_ok`.
- **Coverage Requirements**: 100% de las ramas del metodo (encontrado, no encontrado, excepcion).
- **Performance Testing**: no aplica. La busqueda usa la clave primaria.

## 7. Rationale & Context

- `findProductById` devuelve `Optional` porque, a diferencia del `id` de usuario que viene de un token ya validado, el `id` de producto lo escribe el cliente en la URL y puede no existir. La ausencia es un caso de negocio previsto, no un error de programacion.
- Se prefiere `orElseThrow` sobre `isPresent()` mas `get()` porque expresa la regla en una sola sentencia y hace imposible el acceso a un `Optional` vacio.
- La lectura individual no comprueba rol por la misma razon que el listado: el catalogo es informacion de consulta para cualquier usuario autenticado.
- CON-004 se documenta explicitamente porque "producto inexistente" es el error mas frecuente que vera un cliente de esta API, y recibira `500` en lugar del `404` que sugiere el nombre de la excepcion.
- CON-005 se documenta para evitar que se atribuya al service un `400` que en realidad genera el binding de Spring antes de invocarlo.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - origen de la fila consultada en la tabla `products`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web`.
- **PLT-002**: `JwtService` del propio proyecto.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal
// GET /api/product/7 -> 200 con el ProductResponse del producto 7

// Borde: id inexistente.
// NotFoundException lanzada dentro del try -> 500 con "Producto no encontrado". Ver CON-004.

// Borde: id no numerico. /api/product/abc -> 400 de Spring, el service no se ejecuta. Ver CON-005.

// Borde: id negativo. /api/product/-1 es sintacticamente valido para Integer;
// la consulta no encuentra fila y se aplica la regla de REQ-005.
```

```java
// Antipatron prohibido: desempaquetar el Optional a mano.
// Optional<Product> opt = productRepository.findProductById(id);
// if (opt.isPresent()) { ... } else { throw new NotFoundException("..."); }

// Antipatron prohibido: devolver la entidad.
// public Product getProductById(String authHeader, Integer id) { ... }
```

## 10. Validation Criteria

1. `ProductController` declara `@GetMapping("/{id}")` y devuelve `ResponseEntity<ProductResponse>`.
2. La firma del service es `getProductById(String authHeader, Integer id)`, con `authHeader` en primera posicion.
3. La primera sentencia dentro del `try` es la llamada a `jwtService.validateAccessToken(authHeader)`.
4. El metodo resuelve el `Optional` con `orElseThrow` y no contiene `isPresent`, `get()` ni comparaciones con `null`.
5. El metodo contiene exactamente un bloque `try` y un `catch (Exception e)`.
6. El tipo de retorno declarado es `ProductResponse`, nunca `Product` ni `Optional<Product>`.

## 11. Related Specifications / Further Reading

- [spec-schema-product-entity.md](spec-schema-product-entity.md)
- [spec-design-product-read-all.md](spec-design-product-read-all.md)
- [spec-design-product-create.md](spec-design-product-create.md)
- [spec-design-product-update.md](spec-design-product-update.md)
- [spec-design-product-delete.md](spec-design-product-delete.md)
- `CLAUDE.md`: seccion "Manejo de errores en los services".
