---
title: Metodo updateProduct del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [design, api, product, crud, update]
---

# Introduction

Esta especificacion define el metodo `updateProduct`: la actualizacion total de un producto existente. Depende de `spec-schema-product-entity.md`, que define la entidad, el repositorio y los DTOs.

## 1. Purpose & Scope

**Proposito**: especificar la modificacion de un `Product` existente de forma completa y no ambigua.

**Alcance incluido**:

- Endpoint `PUT /api/product/{id}`.
- Metodo `ProductService.updateProduct(String authHeader, Integer id, UpdateProductRequest updateProductRequest)`.
- Implementacion en `ProductServiceImpl`.
- Semantica de actualizacion total y autorizacion por rol.

**Alcance excluido**:

- Actualizacion parcial (`PATCH`): no forma parte de esta version.
- Ajuste aislado de stock mediante un endpoint dedicado.
- Definicion de entidad, repositorio y DTOs: ver `spec-schema-product-entity.md`.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- El cliente conoce el `id` del producto y envia siempre los cuatro campos modificables.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Actualizacion total** | Semantica de `PUT`: el cuerpo enviado sustituye por completo el valor de todos los campos modificables. |
| **Campo modificable** | Uno de `name`, `description`, `price`, `stock`. El `id` no es modificable. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a escribir. |
| **Entidad gestionada** | Instancia de `Product` cargada dentro de un contexto de persistencia activo. |

## 3. Requirements, Constraints & Guidelines

### Contrato HTTP

- **REQ-001**: El endpoint es `PUT /api/product/{id}`, declarado con `@PutMapping("/{id}")`.
- **REQ-002**: El metodo del controller recibe, en este orden: `@RequestHeader(value = "Authorization") String authHeader`, `@PathVariable Integer id`, `@Valid @RequestBody UpdateProductRequest updateProductRequest`.
- **REQ-003**: La respuesta exitosa es `200 OK` con cuerpo `ProductResponse` que refleja el estado ya actualizado.
- **CON-001**: El controller no contiene logica ni `try/catch`. Delega en una sola linea.

### Seguridad

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`. Omitirla deja el endpoint publico.
- **SEC-002**: Solo se autoriza si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **SEC-003**: La comprobacion de rol se realiza antes de consultar el repositorio, para no revelar por temporizacion ni por mensaje si un `id` existe cuando el solicitante no esta autorizado.

### Reglas de negocio

- **REQ-004**: El producto se carga con `productRepository.findProductById(id)` y se resuelve con `orElseThrow(() -> new NotFoundException("Producto no encontrado"))`.
- **REQ-005**: Se asignan los cuatro campos modificables con los setters generados por Lombok, replicando el estilo de `UserServiceImpl.updateUser`:

```java
product.setName(updateProductRequest.getName());
product.setDescription(updateProductRequest.getDescription());
product.setPrice(updateProductRequest.getPrice());
product.setStock(updateProductRequest.getStock());
```

- **REQ-006**: Se persiste con `productRepository.save(product)`.
- **REQ-007**: Se devuelve `ProductResponse` construido con `builder()` a partir de la entidad ya modificada, incluyendo el `id` original.
- **CON-002**: El `id` nunca se modifica. El `id` viaja solo en la URL; `UpdateProductRequest` no lo expone.
- **CON-003**: La actualizacion es total. Un campo omitido en el JSON llega como `null` al DTO y es rechazado por `@NotBlank` o `@NotNull` antes de entrar al service; no se interpreta como "dejar el valor actual".
- **GUD-001**: En esta operacion se usan setters y no `builder()`, porque se modifica una entidad ya existente. `builder()` sigue siendo obligatorio para construir el `ProductResponse`.

### Manejo de errores

- **PAT-001**: Cuerpo del metodo dentro de un unico `try`, cerrado con `catch (Exception e) { throw new InternalServerErrorException(e.getMessage()); }`.
- **CON-004**: Consecuencia directa y deliberada del patron: `ForbiddenException` (SEC-002) y `NotFoundException` (REQ-004) se lanzan dentro del `try`, son atrapadas por ese `catch` y llegan al cliente como `500` con su mensaje original, no como `403` ni `404`.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `PUT` |
| Ruta | `/api/product/{id}` |
| Path variable | `id` de tipo `Integer`, obligatorio |
| Header obligatorio | `Authorization: Bearer <token>` |
| Header obligatorio | `Content-Type: application/json` |
| Cuerpo | `UpdateProductRequest` con los cuatro campos |
| Respuesta exitosa | `200 OK` con `ProductResponse` |

### Respuestas de error

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Cuerpo invalido o incompleto | `400` | respuesta por defecto de Spring |
| `id` no numerico | `400` | respuesta por defecto de Spring |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring |
| Token invalido o expirado | `500` | `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `ErrorResponse` con mensaje `"No esta autorizado"` |
| `id` inexistente | `500` | `ErrorResponse` con mensaje `"Producto no encontrado"` |
| Fallo de persistencia | `500` | `ErrorResponse` con el mensaje de la causa |

### Peticion y respuesta de ejemplo

```json
PUT /api/product/7
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "name": "Teclado mecanico RGB",
  "description": "Teclado 87 teclas switch rojo con iluminacion",
  "price": 210000.0,
  "stock": 18
}
```

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 7,
  "name": "Teclado mecanico RGB",
  "description": "Teclado 87 teclas switch rojo con iluminacion",
  "price": 210000.0,
  "stock": 18
}
```

### Interfaz de service

```java
ProductResponse updateProduct(String authHeader, Integer id, UpdateProductRequest updateProductRequest);
```

### Implementacion de referencia

```java
@Override
public ProductResponse updateProduct(String authHeader, Integer id, UpdateProductRequest updateProductRequest) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        if (!jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        Product product = productRepository.findProductById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        product.setName(updateProductRequest.getName());
        product.setDescription(updateProductRequest.getDescription());
        product.setPrice(updateProductRequest.getPrice());
        product.setStock(updateProductRequest.getStock());

        productRepository.save(product);

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
@PutMapping("/{id}")
public ResponseEntity<ProductResponse> updateProduct(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id,
        @Valid @RequestBody UpdateProductRequest updateProductRequest) {

    return ResponseEntity.ok(productService.updateProduct(authHeader, id, updateProductRequest));
}
```

## 5. Acceptance Criteria

- **AC-001**: Given existe el producto `id = 7` y un token valido de rol `admin`, When se invoca `PUT /api/product/7` con cuerpo valido, Then la respuesta es `200` con un `ProductResponse` cuyo `id` es `7` y cuyos cuatro campos restantes coinciden con el cuerpo enviado.
- **AC-002**: Given la peticion de AC-001, When termina la operacion, Then la fila `id = 7` de la tabla `products` contiene los valores nuevos y no se creo ninguna fila adicional.
- **AC-003**: Given un token valido de rol `user`, When se invoca el endpoint, Then la fila no cambia y la respuesta es `500` con mensaje `"No esta autorizado"`, por efecto de CON-004.
- **AC-004**: Given un token de rol `admin` y un `id` inexistente, When se invoca el endpoint, Then la respuesta es `500` con mensaje `"Producto no encontrado"` y no se crea ninguna fila.
- **AC-005**: Given un cuerpo sin el campo `description`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@NotBlank` y el service no se ejecuta.
- **AC-006**: Given un cuerpo con `price = 0.0`, When se invoca el endpoint, Then la respuesta es `400` por violacion de `@Positive`.
- **AC-007**: Given un cuerpo que incluye un campo `id` con valor distinto al de la URL, When se invoca el endpoint, Then ese campo se ignora y el producto actualizado conserva el `id` de la URL.
- **AC-008**: Given un token expirado, When se invoca el endpoint, Then la respuesta es `500` y no se ejecuta ninguna consulta contra `products`.
- **AC-009**: Given un cuerpo con `stock = 0`, When se invoca el endpoint con rol `admin`, Then la actualizacion tiene exito y la fila queda con `stock = 0`.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ProductServiceImpl.updateProduct`; integracion opcional con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito.
- **Dobles**: `ProductRepository` y `JwtService` mockeados; `findProductById` devuelve `Optional.of(product)` o `Optional.empty()` segun el caso.
- **Casos minimos**: actualizacion correcta como `admin`; rol no autorizado; `id` inexistente; token invalido.
- **Verificacion adicional**: con `ArgumentCaptor<Product>` sobre `save`, comprobar que la entidad guardada conserva el `id` original y lleva los cuatro campos nuevos.
- **Test Data Management**: objetos construidos en memoria con `builder()`.
- **CI/CD Integration**: `./mvnw test -Dtest=ProductServiceImplTest#updateProduct_ok`.
- **Coverage Requirements**: 100% de las ramas del metodo (exito, rol denegado, no encontrado, excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- Se elige `PUT` con semantica total en lugar de `PATCH` parcial porque con cuatro campos el cliente puede enviarlos todos sin coste, y la actualizacion parcial obligaria a distinguir entre "campo ausente" y "campo puesto a null", complejidad innecesaria en una aplicacion sencilla.
- La comprobacion de rol precede a la carga del producto (SEC-003) para que un usuario no autorizado reciba siempre la misma respuesta, exista o no el `id` consultado.
- Se usan setters sobre la entidad cargada, igual que en `UserServiceImpl.updateUser`, en vez de reconstruirla con `builder()`. Reconstruir con builder exigiria copiar el `id` a mano y abriria la puerta a crear una fila nueva por descuido.
- Se llama a `save` de forma explicita aunque la entidad pudiera estar gestionada y sincronizarse por dirty checking: el metodo no es `@Transactional`, por lo que depender del dirty checking no es seguro aqui.
- El `id` se toma de la URL y no del cuerpo para que el recurso quede identificado por su direccion y no exista la posibilidad de una discrepancia entre ambos.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino del `UPDATE` sobre la tabla `products`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claim `rol`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starters `web` y `validation`.
- **PLT-002**: `JwtService` del propio proyecto.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal: admin actualiza los cuatro campos -> 200 con el estado nuevo.

// Borde: cuerpo identico al estado actual.
// La operacion es idempotente: se ejecuta el save y se devuelve 200 con los mismos valores.

// Borde: rol user. ForbiddenException dentro del try -> 500 "No esta autorizado". Ver CON-004.

// Borde: id inexistente. NotFoundException dentro del try -> 500 "Producto no encontrado".

// Borde: campo omitido en el JSON.
// { "name": "X", "price": 100.0, "stock": 2 }  ->  400 por @NotBlank en description.
// No significa "conservar la descripcion actual". Ver CON-003.
```

```java
// Antipatron prohibido: reconstruir la entidad con builder en una actualizacion.
// Product actualizado = Product.builder()
//         .name(request.getName())
//         ...
//         .build();            // sin id -> inserta una fila nueva
// productRepository.save(actualizado);

// Antipatron prohibido: cargar el producto antes de comprobar el rol.
```

## 10. Validation Criteria

1. `ProductController` declara `@PutMapping("/{id}")` y devuelve `ResponseEntity<ProductResponse>`.
2. La firma del service es `updateProduct(String authHeader, Integer id, UpdateProductRequest updateProductRequest)`, en ese orden.
3. La primera sentencia dentro del `try` es la llamada a `jwtService.validateAccessToken(authHeader)` y la comprobacion de rol es la siguiente.
4. La carga del producto ocurre despues de la comprobacion de rol.
5. El metodo no invoca `setId` sobre la entidad.
6. El metodo contiene exactamente un bloque `try` y un `catch (Exception e)`.
7. El tipo de retorno declarado es `ProductResponse`, nunca `Product`.

## 11. Related Specifications / Further Reading

- [spec-schema-product-entity.md](spec-schema-product-entity.md)
- [spec-design-product-create.md](spec-design-product-create.md)
- [spec-design-product-read-all.md](spec-design-product-read-all.md)
- [spec-design-product-read-by-id.md](spec-design-product-read-by-id.md)
- [spec-design-product-delete.md](spec-design-product-delete.md)
- `CLAUDE.md`: seccion "Manejo de errores en los services".
