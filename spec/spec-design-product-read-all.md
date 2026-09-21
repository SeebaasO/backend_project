---
title: Metodo getAllProducts del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [design, api, product, crud, read]
---

# Introduction

Esta especificacion define el metodo `getAllProducts`: el listado completo del catalogo de productos. Es una de las dos operaciones de lectura del CRUD; la lectura individual se especifica en `spec-design-product-read-by-id.md`. Depende de `spec-schema-product-entity.md`.

## 1. Purpose & Scope

**Proposito**: especificar la operacion de listado de productos de forma completa y no ambigua.

**Alcance incluido**:

- Endpoint `GET /api/product`.
- Metodo `ProductService.getAllProducts(String authHeader)`.
- Implementacion en `ProductServiceImpl`.
- Comportamiento ante catalogo vacio.

**Alcance excluido**:

- Paginacion, ordenamiento y filtros: no forman parte de esta version.
- Lectura por id y operaciones de escritura.
- Definicion de entidad, repositorio y DTOs: ver `spec-schema-product-entity.md`.

**Audiencia**: agentes de IA generativa y desarrolladores del proyecto `backend_project`.

**Supuestos**:

- Cualquier usuario autenticado puede consultar el catalogo, sin importar su rol.
- El volumen de productos es pequeno; devolver la lista completa en una sola respuesta es aceptable.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **authHeader** | Valor crudo del header HTTP `Authorization`, con el formato `Bearer <token>`. |
| **JwtValidate** | DTO con los claims extraidos del token: `id`, `username`, `role`. |
| **Catalogo vacio** | Estado en el que la tabla `products` no contiene ninguna fila. |
| **NoContentException** | Excepcion de dominio del proyecto asociada al codigo HTTP `204` en `GlobalExceptionHandler`. |

## 3. Requirements, Constraints & Guidelines

### Contrato HTTP

- **REQ-001**: El endpoint es `GET /api/product`, declarado con `@GetMapping` sin path adicional dentro de `@RequestMapping("/api/product")`.
- **REQ-002**: El metodo del controller recibe unicamente `@RequestHeader(value = "Authorization") String authHeader`.
- **REQ-003**: La respuesta exitosa es `200 OK` con cuerpo `List<ProductResponse>`.
- **CON-001**: El controller no contiene logica ni `try/catch`. Delega en una sola linea.

### Seguridad

- **SEC-001**: La primera instruccion dentro del `try` es `jwtService.validateAccessToken(authHeader)`. Omitirla deja el endpoint publico.
- **SEC-002**: No hay comprobacion de rol. Cualquier token valido autoriza la lectura, sea `admin` o `user`.
- **GUD-001**: El `JwtValidate` resultante no se usa en el cuerpo del metodo. La llamada se mantiene igualmente porque su efecto util es rechazar tokens invalidos.

### Reglas de negocio

- **REQ-004**: Se obtiene la lista con `productRepository.findAll()`, sin filtros.
- **REQ-005**: Si la lista esta vacia se lanza `NoContentException` con el mensaje `"No se encontraron productos"`, replicando el criterio de `UserServiceImpl.getUserAll`.
- **REQ-006**: El mapeo a DTO se hace con `stream().map(...).toList()`, construyendo cada `ProductResponse` con `builder()`.
- **CON-002**: No se aplica paginacion, orden explicito ni filtrado. El orden de las filas lo determina PostgreSQL y no esta garantizado.
- **CON-003**: La respuesta nunca contiene objetos `Product`.

### Manejo de errores

- **PAT-001**: Cuerpo del metodo dentro de un unico `try`, cerrado con `catch (Exception e) { throw new InternalServerErrorException(e.getMessage()); }`.
- **CON-004**: Consecuencia directa y deliberada del patron: la `NoContentException` de REQ-005 se lanza dentro del `try`, es atrapada por ese `catch` y llega al cliente como `500` con el mensaje `"No se encontraron productos"`, no como `204`. Es el comportamiento vigente en `getUserAll` y esta especificacion lo conserva.

## 4. Interfaces & Data Contracts

### Contrato HTTP

| Elemento | Valor |
|---|---|
| Metodo | `GET` |
| Ruta | `/api/product` |
| Header obligatorio | `Authorization: Bearer <token>` |
| Cuerpo de peticion | ninguno |
| Respuesta exitosa | `200 OK` con `List<ProductResponse>` |

### Respuestas de error

| Situacion | Codigo efectivo | Cuerpo |
|---|---|---|
| Header `Authorization` ausente | `400` | respuesta por defecto de Spring |
| Token invalido o expirado | `500` | `ErrorResponse` con el mensaje de `JwtAuthenticationException` |
| Catalogo vacio | `500` | `ErrorResponse` con mensaje `"No se encontraron productos"` |
| Fallo de acceso a datos | `500` | `ErrorResponse` con el mensaje de la causa |

### Respuesta de ejemplo

```json
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 7,
    "name": "Teclado mecanico",
    "description": "Teclado 87 teclas switch rojo",
    "price": 189000.0,
    "stock": 25
  },
  {
    "id": 8,
    "name": "Mouse optico",
    "description": "Mouse USB 1600 dpi",
    "price": 45000.0,
    "stock": 0
  }
]
```

### Interfaz de service

```java
List<ProductResponse> getAllProducts(String authHeader);
```

### Implementacion de referencia

```java
@Override
public List<ProductResponse> getAllProducts(String authHeader) {

    try {
        JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            throw new NoContentException("No se encontraron productos");
        }

        return products.stream()
                .map(product -> ProductResponse.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .description(product.getDescription())
                        .price(product.getPrice())
                        .stock(product.getStock())
                        .build())
                .toList();
    } catch (Exception e) {
        throw new InternalServerErrorException(e.getMessage());
    }
}
```

### Controller

```java
@GetMapping
public ResponseEntity<List<ProductResponse>> getAllProducts(
        @RequestHeader(value = "Authorization") String authHeader) {

    return ResponseEntity.ok(productService.getAllProducts(authHeader));
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la tabla `products` contiene tres filas y un token valido de rol `user`, When se invoca `GET /api/product`, Then la respuesta es `200` con un arreglo de tres elementos.
- **AC-002**: Given la respuesta de AC-001, When se inspecciona cada elemento, Then contiene exactamente las claves `id`, `name`, `description`, `price` y `stock`.
- **AC-003**: Given un token valido de rol `admin`, When se invoca el endpoint, Then el resultado es identico al de un token de rol `user`: la lectura no discrimina por rol.
- **AC-004**: Given la tabla `products` esta vacia, When se invoca el endpoint, Then la respuesta es `500` con mensaje `"No se encontraron productos"`, por efecto de CON-004.
- **AC-005**: Given un token con firma alterada, When se invoca el endpoint, Then la respuesta es `500` y no se ejecuta ninguna consulta contra `products`.
- **AC-006**: Given la peticion sin header `Authorization`, When se invoca el endpoint, Then Spring responde `400` y el service no se ejecuta.
- **AC-007**: Given una fila con `stock = 0`, When se lista el catalogo, Then esa fila aparece en el resultado: el stock cero no oculta el producto.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre `ProductServiceImpl.getAllProducts`; integracion opcional con `MockMvc`.
- **Frameworks**: JUnit 5 y Mockito.
- **Dobles**: `productRepository.findAll()` mockeado para devolver una lista con elementos y, en otro test, `List.of()`.
- **Casos minimos**: lista con elementos; lista vacia; token invalido.
- **Test Data Management**: listas construidas en memoria con `Product.builder()`.
- **CI/CD Integration**: `./mvnw test -Dtest=ProductServiceImplTest#getAllProducts_ok`.
- **Coverage Requirements**: 100% de las ramas del metodo (lista con datos, lista vacia, excepcion).
- **Performance Testing**: no aplica con el volumen previsto. Si el catalogo supera algunos miles de filas, revisar CON-002 y anadir paginacion en una version futura de esta especificacion.

## 7. Rationale & Context

- La lectura se abre a todos los roles porque un catalogo de productos es informacion de consulta; restringirla a `admin` dejaria a los usuarios normales sin nada que hacer en la aplicacion.
- El `JwtValidate` se asigna a una variable aunque no se use despues, para no romper la simetria con el resto de metodos del proyecto y dejar visible que la validacion ocurrio.
- Se conserva el criterio de lanzar `NoContentException` ante lista vacia por coherencia con `getUserAll`, aunque un arreglo vacio con `200` seria mas habitual en REST. La coherencia interna es la regla del proyecto.
- CON-004 se documenta explicitamente: es el caso mas visible del efecto del patron de un solo `catch`, ya que un catalogo vacio es un estado normal al arrancar el sistema y produce un `500`.
- No se anade paginacion porque el requisito es una aplicacion sencilla. Introducirla obligaria a definir un DTO de pagina y complicaria el contrato sin necesidad actual.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - origen de las filas de la tabla `products`.

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
// Caso normal: catalogo con dos productos -> 200 con arreglo de dos elementos.

// Borde: catalogo vacio.
// El service lanza NoContentException dentro del try; el catch la convierte en
// InternalServerErrorException y el cliente recibe 500, no 204. Ver CON-004.

// Borde: un producto con stock 0 aparece igual en la lista.
// El filtrado por disponibilidad no forma parte de esta operacion.

// Borde: el orden de los elementos no esta garantizado.
// Los tests deben comparar por contenido, no por posicion.
```

```java
// Antipatron prohibido: devolver entidades.
// public List<Product> getAllProducts(String authHeader) { ... }

// Antipatron prohibido: consultar el repositorio desde el controller.
// return ResponseEntity.ok(productRepository.findAll());
```

## 10. Validation Criteria

1. `ProductController` declara `@GetMapping` sin path y devuelve `ResponseEntity<List<ProductResponse>>`.
2. La primera sentencia dentro del `try` es la llamada a `jwtService.validateAccessToken(authHeader)`.
3. El metodo no contiene ninguna comparacion contra `"admin"`.
4. El metodo contiene exactamente un bloque `try` y un `catch (Exception e)`.
5. El tipo de retorno declarado es `List<ProductResponse>`, nunca `List<Product>`.
6. La lista de respuesta se produce con `stream()` y `toList()`, sin bucles `for` acumulando en una lista mutable.

## 11. Related Specifications / Further Reading

- [spec-schema-product-entity.md](spec-schema-product-entity.md)
- [spec-design-product-read-by-id.md](spec-design-product-read-by-id.md)
- [spec-design-product-create.md](spec-design-product-create.md)
- [spec-design-product-update.md](spec-design-product-update.md)
- [spec-design-product-delete.md](spec-design-product-delete.md)
- `CLAUDE.md`: seccion "Manejo de errores en los services".
