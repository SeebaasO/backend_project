---
title: Metodo deleteProduct del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [design, api, product, crud, delete]
---

# Introduction

Esta especificacion define el metodo `deleteProduct`: la eliminacion permanente de un producto del catalogo. Es la operacion irreversible del CRUD. Depende de `spec-schema-product-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar el borrado de un `Product` de forma completa y no ambigua, incluyendo su caracter destructivo y la respuesta devuelta al cliente.

**Alcance incluido**:

- Endpoint `DELETE /api/product/{id}`.
- Metodo `ProductService.deleteProduct(String authHeader, Integer id)`.
- Implementacion en `ProductServiceImpl`.
- Autorizacion por rol y tratamiento del `id` inexistente.

**Alcance excluido**:

- Borrado logico (campo `active` o `deletedAt`): no forma parte de esta version.
- Borrado en lote.
- Definicion de entidad, repositorio y DTOs: ver `spec-schema-product-entity.md`.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- Ninguna otra entidad referencia a `Product`, por lo que no existen restricciones de clave foranea que impidan el borrado.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **Borrado fisico** | Eliminacion definitiva de la fila mediante `DELETE` en SQL. El dato no es recuperable desde la aplicacion. |
| **Borrado logico** | Marcado de la fila como inactiva conservando el dato. No se usa en esta especificacion. |
| **Rol admin** | Valor literal `"admin"` en el claim `rol` del token. Unico rol autorizado a borrar. |
| **Idempotencia** | Propiedad por la que repetir la misma peticion produce el mismo estado final del sistema. |

## 3. Requirements, Constraints & Guidelines

### Contrato HTTP

- **REQ-001**: El endpoint es `DELETE /api/product/{id}`, declarado con `@DeleteMapping("/{id}")`.
- **REQ-002**: El metodo del controller recibe `@RequestHeader(value = "Authorization") String authHeader` como primer parametro y `@PathVariable Integer id` como segundo.
- **REQ-003**: La respuesta exitosa es `200 OK` con cuerpo `ProductResponse`, que contiene el estado del producto inmediatamente anterior a su eliminacion.
- **CON-001**: El controller no contiene logica ni `try/catch`. Delega en una sola linea.
- **CON-002**: No se devuelve `204 No Content`. El proyecto responde siempre con `ResponseEntity.ok(...)` y un cuerpo; ademas, `NoContentException` ya tiene otro uso en el codigo base.

### Seguridad

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`. Omitirla deja publica una operacion destructiva.
- **SEC-002**: Solo se autoriza si `jwtValidate.getRole().equals("admin")`. En caso contrario se lanza `ForbiddenException` con el mensaje `"No esta autorizado"`.
- **SEC-003**: La comprobacion de rol se realiza antes de consultar el repositorio, para no revelar a un solicitante no autorizado si un `id` existe.

### Reglas de negocio

- **REQ-004**: El producto se carga con `productRepository.findProductById(id)` y se resuelve con `orElseThrow(() -> new NotFoundException("Producto no encontrado"))`.
- **REQ-005**: El `ProductResponse` se construye **antes** de invocar el borrado, a partir de la entidad todavia cargada.
- **REQ-006**: El borrado se ejecuta con `productRepository.delete(product)`.
- **REQ-007**: El borrado es fisico y permanente. No se conserva copia ni marca de baja.
- **CON-003**: El borrado no es idempotente desde el punto de vista de la respuesta: la primera llamada devuelve `200` y la segunda falla por REQ-004, aunque el estado final del sistema sea el mismo.
- **GUD-001**: Se usa `delete(product)` sobre la entidad ya cargada y no `deleteById(id)`, para que la ausencia del producto se detecte en REQ-004 con un mensaje de dominio propio en lugar de depender del comportamiento de `deleteById`.

### Manejo de errores

- **PAT-001**: Cuerpo del metodo dentro de un unico `try`, cerrado con `catch (Exception e) { throw new InternalServerErrorException(e.getMessage()); }`.
- **CON-004**: Consecuencia directa y deliberada del patron: `ForbiddenException` (SEC-002) y `NotFoundException` (REQ-004) se lanzan dentro del `try`, son atrapadas por ese `catch` y llegan al cliente como `500` con su mensaje original, no como `403` ni `404`.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `DELETE` |
| Ruta | `/api/product/{id}` |
| Path variable | `id` de tipo `Integer`, obligatorio |
| Header obligatorio | `Authorization: Bearer <token>` |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `ProductResponse` del producto eliminado |

### Respuestas de error

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| `id` no numerico | `400` | respuesta por defecto de Spring |
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring |
| Token invalido o expirado | `500` | `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Rol distinto de `admin` | `500` | `ErrorResponse` con mensaje `"No esta autorizado"` |
| `id` inexistente o ya borrado | `500` | `ErrorResponse` con mensaje `"Producto no encontrado"` |
| Fallo de persistencia | `500` | `ErrorResponse` con el mensaje de la causa |

### Peticion y respuesta de ejemplo

```json
DELETE /api/product/7
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
ProductResponse deleteProduct(String authHeader, Integer id);
```

### Implementacion de referencia

```java
@Override
public ProductResponse deleteProduct(String authHeader, Integer id) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        if (!jwtValidate.getRole().equals("admin")) {
            throw new ForbiddenException("No esta autorizado");
        }

        Product product = productRepository.findProductById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        ProductResponse productResponse = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();

        productRepository.delete(product);

        return productResponse;
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Controller

```java
@DeleteMapping("/{id}")
public ResponseEntity<ProductResponse> deleteProduct(
        @RequestHeader(value = "Authorization") String authHeader,
        @PathVariable Integer id) {

    return ResponseEntity.ok(productService.deleteProduct(authHeader, id));
}
```

### Interfaz completa de `ProductService`

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateProductRequest;
import backend_project.backend_project.model.Request.UpdateProductRequest;
import backend_project.backend_project.model.Response.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(String authHeader, CreateProductRequest createProductRequest);

    List<ProductResponse> getAllProducts(String authHeader);

    ProductResponse getProductById(String authHeader, Integer id);

    ProductResponse updateProduct(String authHeader, Integer id, UpdateProductRequest updateProductRequest);

    ProductResponse deleteProduct(String authHeader, Integer id);
}
```

## 5. Acceptance Criteria

- **AC-001**: Given existe el producto `id = 7` y un token valido de rol `admin`, When se invoca `DELETE /api/product/7`, Then la respuesta es `200` con el `ProductResponse` del producto eliminado, incluido su `id`.
- **AC-002**: Given la peticion de AC-001, When termina la operacion, Then la tabla `products` no contiene ninguna fila con `id = 7`.
- **AC-003**: Given la peticion de AC-001 ya ejecutada, When se repite la misma peticion, Then la respuesta es `500` con mensaje `"Producto no encontrado"`. Ver CON-003.
- **AC-004**: Given un token valido de rol `user`, When se invoca el endpoint sobre un `id` existente, Then la fila sigue presente en la tabla y la respuesta es `500` con mensaje `"No esta autorizado"`, por efecto de CON-004.
- **AC-005**: Given un token de rol `admin` y un `id` inexistente, When se invoca el endpoint, Then la respuesta es `500` con mensaje `"Producto no encontrado"` y ninguna fila resulta afectada.
- **AC-006**: Given un token expirado, When se invoca el endpoint, Then la respuesta es `500` y ninguna fila resulta afectada.
- **AC-007**: Given la URL `/api/product/abc`, When se invoca el endpoint, Then la respuesta es `400` y el service no se ejecuta.
- **AC-008**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y ninguna fila resulta afectada.
- **AC-009**: Given el producto `id = 7` existe, When se invoca el endpoint con rol `admin`, Then el cuerpo de la respuesta contiene los valores que tenia el producto antes del borrado, no valores nulos.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ProductServiceImpl.deleteProduct`; integracion opcional con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito.
- **Dobles**: `ProductRepository` y `JwtService` mockeados. Verificar con `verify(productRepository).delete(product)` en el caso exitoso y con `verify(productRepository, never()).delete(any())` en los casos denegado y no encontrado.
- **Casos minimos**: borrado correcto como `admin`; rol no autorizado; `id` inexistente; token invalido.
- **Test Data Management**: objetos `Product` construidos en memoria con `builder()`. Los tests unitarios no tocan la base de datos, por lo que el borrado no destruye datos reales.
- **Precaucion**: cualquier prueba manual de este endpoint debe ejecutarse contra la base de datos de desarrollo. El borrado es fisico y no hay forma de deshacerlo desde la aplicacion.
- **CI/CD Integration**: `./mvnw test -Dtest=ProductServiceImplTest#deleteProduct_ok`.
- **Coverage Requirements**: 100% de las ramas del metodo (exito, rol denegado, no encontrado, excepcion).
- **Performance Testing**: no aplica.

## 7. Rationale & Context

- Se devuelve el producto eliminado en lugar de un cuerpo vacio para que el cliente pueda confirmar exactamente que registro desaparecio y, si lo necesita, reconstruirlo manualmente. Es la unica traza que queda del dato.
- El `ProductResponse` se construye antes del `delete` (REQ-005) porque tras la eliminacion la entidad deja de estar gestionada y leer sus campos despues seria fragil.
- Se elige borrado fisico por el requisito de simplicidad. El borrado logico obligaria a anadir un campo de estado a la entidad y a filtrarlo en las dos operaciones de lectura, propagando complejidad por todo el CRUD.
- Se usa `delete(product)` y no `deleteById(id)` para que "no existe" produzca un mensaje de dominio propio y para dejar explicito en el codigo que el registro fue leido antes de destruirse.
- La comprobacion de rol precede a la carga (SEC-003) por la misma razon que en `updateProduct`: un solicitante no autorizado no debe poder deducir que ids existen.
- CON-004 se documenta de forma explicita porque en una operacion destructiva es critico no confundir un `500` por falta de permisos con un fallo del servidor. El borrado no se ejecuto en ninguno de esos casos.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - destino del `DELETE` sobre la tabla `products`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Variables `JWT_SECRET` y `JWT_EXPIRATION` disponibles en el entorno.
- **INF-002**: Politica de respaldo de la base de datos - unica via de recuperacion ante un borrado no deseado, dado que la aplicacion no ofrece ninguna.

### Data Dependencies

- **DAT-001**: Token JWT emitido por `AuthService` - formato `Bearer <token>`, firmado HS512, con claim `rol`.

### Technology Platform Dependencies

- **PLT-001**: Spring Boot starter `web`.
- **PLT-002**: `JwtService` del propio proyecto.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Caso normal: admin borra el producto 7 -> 200 con el estado previo del producto.

// Borde: segunda llamada sobre el mismo id.
// -> 500 "Producto no encontrado". El estado final es el mismo, la respuesta no. Ver CON-003.

// Borde: rol user. ForbiddenException dentro del try -> 500 "No esta autorizado",
// y la fila permanece intacta. Ver CON-004.

// Borde: producto con stock 0. Se borra igual; el stock no condiciona la eliminacion.
```

```java
// Antipatron prohibido: construir el response despues del delete.
// productRepository.delete(product);
// return ProductResponse.builder().id(product.getId())...   // entidad ya desasociada

// Antipatron prohibido: borrar sin leer antes.
// productRepository.deleteById(id);   // no distingue "existia" de "no existia"

// Antipatron prohibido: cargar el producto antes de comprobar el rol.
```

## 10. Validation Criteria

1. `ProductController` declara `@DeleteMapping("/{id}")` y devuelve `ResponseEntity<ProductResponse>`.
2. La firma del service es `deleteProduct(String authHeader, Integer id)`, con `authHeader` en primera posicion.
3. La primera sentencia dentro del `try` es la llamada a `jwtService.validateAccessToken(authHeader)` y la comprobacion de rol es la siguiente.
4. La construccion del `ProductResponse` precede en el codigo a la llamada a `delete`.
5. El metodo invoca `productRepository.delete(product)` y no `deleteById`.
6. El metodo contiene exactamente un bloque `try` y un `catch (Exception e)`.
7. El tipo de retorno declarado es `ProductResponse`, nunca `void` ni `Product`.

## 11. Related Specifications / Further Reading

- [spec-schema-product-entity.md](spec-schema-product-entity.md)
- [spec-design-product-create.md](spec-design-product-create.md)
- [spec-design-product-read-all.md](spec-design-product-read-all.md)
- [spec-design-product-read-by-id.md](spec-design-product-read-by-id.md)
- [spec-design-product-update.md](spec-design-product-update.md)
- `CLAUDE.md`: seccion "Manejo de errores en los services".
