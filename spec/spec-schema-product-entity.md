---
title: Entidad Product, repositorio y contratos de datos del CRUD de productos
version: 1.0
date_created: 2026-09-19
last_updated: 2026-09-19
owner: Equipo backend_project
tags: [schema, data, jpa, product, crud]
---

# Introduction

Esta especificacion define la entidad JPA `Product`, su repositorio Spring Data y los DTOs (`Request` / `Response`) compartidos por los cinco metodos del CRUD de productos. Es la base comun: las especificaciones de `create`, `read all`, `read by id`, `update` y `delete` dependen de este documento y no redefinen estos artefactos.

## 1. Purpose & Scope

**Proposito**: fijar el modelo de datos persistente y los contratos de entrada/salida del recurso `Product` dentro del proyecto `backend_project`, una API REST Spring Boot en capas con autenticacion JWT manual.

**Alcance incluido**:

- Clase `entity/Product.java`.
- Interfaz `repository/ProductRepository.java`.
- DTOs `model/Request/CreateProductRequest.java`, `model/Request/UpdateProductRequest.java`, `model/Response/ProductResponse.java`.
- Reglas de generacion de esquema (Hibernate `ddl-auto=update`).

**Alcance excluido**:

- Logica de negocio de cada operacion (ver especificaciones por metodo).
- Endpoints HTTP (ver especificaciones por metodo).
- Migraciones SQL versionadas: el proyecto no las usa.

**Audiencia**: agentes de IA generativa y desarrolladores que implementen o modifiquen el CRUD de productos.

**Supuestos**:

- La aplicacion es sencilla: el producto guarda solo datos basicos y no tiene relaciones con otras entidades.
- Un producto no pertenece a un usuario concreto; el catalogo es unico y compartido.
- El proyecto ya tiene operativos `JwtService`, `GlobalExceptionHandler` y las excepciones de dominio.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **JPA** | Jakarta Persistence API. Especificacion de mapeo objeto-relacional que usa Hibernate como implementacion. |
| **Entity** | Clase Java anotada con `@Entity` que se mapea a una tabla. Nunca se devuelve al cliente. |
| **DTO** | Data Transfer Object. Objeto plano de transporte entre capas. |
| **Request** | DTO que entra desde el cliente HTTP. Lleva anotaciones de validacion. |
| **Response** | DTO que sale hacia el cliente HTTP. Solo expone campos publicos. |
| **JWT** | JSON Web Token. Token firmado HS512 que transporta los claims `sub`, `id`, `rol`. |
| **ddl-auto=update** | Modo de Hibernate que crea o altera tablas al arrancar segun las entidades, sin borrar columnas existentes. |
| **Lombok** | Libreria que genera getters, setters, constructores y builders en tiempo de compilacion. |

## 3. Requirements, Constraints & Guidelines

### Entidad

- **REQ-001**: La entidad se llama `Product` y vive en el paquete `backend_project.backend_project.entity`.
- **REQ-002**: Se mapea a la tabla `products` mediante `@Table(name = "products")`.
- **REQ-003**: Campos exactos, en este orden:

| Campo | Tipo Java | Columna | Nulable | Notas |
|---|---|---|---|---|
| `id` | `Integer` | `id` | generado | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `name` | `String` | `name` | `nullable = false` | nombre comercial del producto |
| `description` | `String` | `description` | `nullable = false` | texto libre corto |
| `price` | `Double` | `price` | `nullable = false` | valor unitario, mayor que cero |
| `stock` | `Integer` | `stock` | `nullable = false` | unidades disponibles, cero o mayor |

- **REQ-004**: La entidad se anota con `@Data @Builder @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Entity @Table(...)`, igual que `User`.
- **CON-001**: La entidad no define `uniqueConstraints`. Dos productos pueden compartir `name`; el identificador de negocio es el `id`.
- **CON-002**: La entidad nunca se serializa hacia el cliente. Toda salida HTTP usa `ProductResponse`.
- **CON-003**: No se agregan campos de auditoria (`createdAt`, `ownerId`, etc.). El requisito es una aplicacion sencilla con datos basicos.

### Repositorio

- **REQ-005**: `ProductRepository` es una interfaz anotada `@Repository` que extiende `JpaRepository<Product, Integer>`.
- **REQ-006**: Metodos requeridos:

| Firma | Origen | Uso |
|---|---|---|
| `Optional<Product> findProductById(Integer id)` | derivado | lectura por id, actualizacion y borrado |
| `List<Product> findAll()` | heredado | listado completo |
| `Product save(Product product)` | heredado | alta y actualizacion |
| `void delete(Product product)` | heredado | borrado |

- **CON-004**: El repositorio no contiene implementacion manual, ni `@Query` JPQL, ni conoce DTOs.
- **GUD-001**: Se devuelve `Optional<Product>` porque "el producto no existe" es un caso de negocio que produce `NotFoundException`. Contrasta con `UserRepository.findUserById`, que devuelve `User` plano porque el id proviene de un token ya validado.

### DTOs

- **REQ-007**: `CreateProductRequest` expone `name`, `description`, `price`, `stock`. Validaciones: `@NotBlank` en `name` y `description`; `@NotNull @Positive` en `price`; `@NotNull @PositiveOrZero` en `stock`.
- **REQ-008**: `UpdateProductRequest` expone los mismos cuatro campos con las mismas validaciones. Es una actualizacion total (PUT), no parcial.
- **REQ-009**: `ProductResponse` expone `id`, `name`, `description`, `price`, `stock`. Incluye `id` porque el cliente lo necesita para invocar `read by id`, `update` y `delete`.
- **REQ-010**: Los tres DTOs se anotan con `@Data @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- **CON-005**: Los paquetes se escriben con inicial mayuscula: `model.Request` y `model.Response`, tal como el codigo existente.
- **PAT-001**: Todo objeto se construye con `.builder()`, nunca con `new` mas setters.

### Persistencia y esquema

- **CON-006**: No hay migraciones. Hibernate genera y altera la tabla `products` al arrancar segun `spring.jpa.hibernate.ddl-auto=update`.
- **CON-007**: Con `ddl-auto=update` una columna renombrada o eliminada en la entidad no se borra en la base de datos. Cambios destructivos exigen intervencion manual en PostgreSQL.
- **GUD-002**: `price` se modela como `Double` por coherencia con el ejemplo del proyecto y por la simplicidad requerida. `BigDecimal` seria preferible para dinero real; queda documentado como deuda tecnica consciente.

## 4. Interfaces & Data Contracts

### Entidad

```java
package backend_project.backend_project.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer stock;
}
```

### Tabla generada

| Columna | Tipo PostgreSQL | Restriccion |
|---|---|---|
| `id` | `serial` / `integer` | `PRIMARY KEY`, autoincremental |
| `name` | `varchar(255)` | `NOT NULL` |
| `description` | `varchar(255)` | `NOT NULL` |
| `price` | `double precision` | `NOT NULL` |
| `stock` | `integer` | `NOT NULL` |

### Repositorio

```java
package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Optional<Product> findProductById(Integer id);
}
```

### DTOs

```java
// model/Request/CreateProductRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private Double price;

    @NotNull
    @PositiveOrZero
    private Integer stock;
}
```

```java
// model/Request/UpdateProductRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private Double price;

    @NotNull
    @PositiveOrZero
    private Integer stock;
}
```

```java
// model/Response/ProductResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Integer id;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
}
```

### Ejemplo JSON de `ProductResponse`

```json
{
  "id": 7,
  "name": "Teclado mecanico",
  "description": "Teclado 87 teclas switch rojo",
  "price": 189000.0,
  "stock": 25
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la aplicacion arranca con una base PostgreSQL vacia, When Hibernate procesa las entidades, Then existe la tabla `products` con las cinco columnas de la seccion 4 y `id` autoincremental.
- **AC-002**: Given un `Product` con `name` nulo, When se invoca `productRepository.save(product)`, Then la persistencia falla por violacion de `NOT NULL`.
- **AC-003**: Given la tabla `products` contiene una fila con `id = 7`, When se invoca `productRepository.findProductById(7)`, Then se devuelve un `Optional` presente cuyo `Product` tiene `id = 7`.
- **AC-004**: Given la tabla `products` no contiene `id = 999`, When se invoca `productRepository.findProductById(999)`, Then se devuelve `Optional.empty()`.
- **AC-005**: Given un `CreateProductRequest` con `price = 0.0`, When se valida con `jakarta.validation`, Then se reporta violacion de `@Positive`.
- **AC-006**: Given un `CreateProductRequest` con `stock = 0`, When se valida, Then no hay violaciones: cero es un stock valido.
- **AC-007**: Given un `CreateProductRequest` con `name` compuesto solo de espacios, When se valida, Then se reporta violacion de `@NotBlank`.
- **AC-008**: El tipo `ProductResponse` no contiene ninguna referencia al tipo `Product`, verificable por inspeccion de sus imports.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre validaciones de DTO; integracion sobre el repositorio.
- **Frameworks**: JUnit 5 y Mockito, provistos por `spring-boot-starter-test`.
- **Repositorio**: prueba con `@DataJpaTest` apuntando a PostgreSQL de desarrollo, o prueba unitaria con `ProductRepository` mockeado cuando solo se verifica la interaccion.
- **DTOs**: prueba con `Validator` obtenido de `Validation.buildDefaultValidatorFactory()`, cubriendo un caso valido y uno invalido por cada anotacion.
- **Test Data Management**: cada test crea sus propios `Product` con `Product.builder()`. `@DataJpaTest` revierte la transaccion al finalizar; no se requiere limpieza manual.
- **CI/CD Integration**: `./mvnw test` ejecuta la suite completa. En Windows: `mvnw.cmd test`.
- **Coverage Requirements**: cobertura de linea minima 80% en `entity`, `model` y `repository`.
- **Performance Testing**: no aplica a esta especificacion.

## 7. Rationale & Context

- Cinco campos y cero relaciones responden al requisito explicito de una aplicacion sencilla con datos basicos. Categorias, proveedores o imagenes quedan fuera deliberadamente.
- Se omite `ownerId` porque el catalogo es unico: los productos no pertenecen a un usuario. La autorizacion se resuelve por rol en la capa de servicio, no por propiedad del registro.
- Se omite `uniqueConstraints` sobre `name` para no bloquear nombres repetidos legitimos en un catalogo sencillo.
- `ProductResponse` si expone `id`, a diferencia de `UserResponse` que lo oculta, porque el `id` del producto es un dato publico necesario para direccionar el recurso via URL. El `id` de usuario no lo es: viaja dentro del token.
- Un solo `ProductResponse` sirve a las cinco operaciones. Multiplicar DTOs de salida por operacion no aporta valor con cinco campos.
- `CreateProductRequest` y `UpdateProductRequest` son clases separadas pese a tener hoy los mismos campos, para que evolucionen de forma independiente sin acoplar el alta a la edicion.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - base de datos relacional que aloja la tabla `products`. Conexion JDBC con las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Archivo `.env` en la raiz del proyecto con las variables obligatorias. `BackendProkectApplication.main` las carga con dotenv-java antes de arrancar Spring.

### Data Dependencies

- Ninguna. La tabla `products` no se alimenta de fuentes externas.

### Technology Platform Dependencies

- **PLT-001**: Java 25 - version del proyecto, requerida para compilar.
- **PLT-002**: Spring Boot 4.1.1 con starters `data-jpa` y `validation`.
- **PLT-003**: Lombok - obligatorio; toda la base de codigo depende de sus anotaciones.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Construccion correcta: siempre con builder, sin id (lo genera la base de datos)
Product product = Product.builder()
        .name("Teclado mecanico")
        .description("Teclado 87 teclas switch rojo")
        .price(189000.0)
        .stock(25)
        .build();

// Mapeo entity -> response. Nunca se devuelve la entity.
ProductResponse response = ProductResponse.builder()
        .id(product.getId())
        .name(product.getName())
        .description(product.getDescription())
        .price(product.getPrice())
        .stock(product.getStock())
        .build();

// Borde: id inexistente. El Optional vacio es el caso de negocio "no encontrado".
Product found = productRepository.findProductById(999)
        .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

// Borde: stock cero es valido y debe persistirse. No confundir con producto invalido.
Product agotado = Product.builder()
        .name("Mouse")
        .description("Mouse optico")
        .price(45000.0)
        .stock(0)
        .build();

// Antipatron: no construir con new mas setters.
// Product p = new Product(); p.setName("X");
```

## 10. Validation Criteria

1. Existen los cinco archivos: `entity/Product.java`, `repository/ProductRepository.java`, `model/Request/CreateProductRequest.java`, `model/Request/UpdateProductRequest.java`, `model/Response/ProductResponse.java`.
2. `Product` declara exactamente los cinco campos de REQ-003, con `@Column(nullable = false)` en los cuatro no generados.
3. `ProductRepository` no declara metodos ajenos a los de REQ-006 ni importa clases de `model`.
4. Ningun archivo bajo `model/` importa `backend_project.backend_project.entity.Product`.
5. `./mvnw clean package` compila sin errores.
6. Tras arrancar la aplicacion, la tabla `products` en PostgreSQL muestra las cinco columnas con los tipos de la seccion 4.

## 11. Related Specifications / Further Reading

- [spec-design-product-create.md](spec-design-product-create.md)
- [spec-design-product-read-all.md](spec-design-product-read-all.md)
- [spec-design-product-read-by-id.md](spec-design-product-read-by-id.md)
- [spec-design-product-update.md](spec-design-product-update.md)
- [spec-design-product-delete.md](spec-design-product-delete.md)
- `CLAUDE.md` en la raiz del repositorio: arquitectura por capas y decisiones cerradas.
