# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Comandos

```bash
./mvnw spring-boot:run                 # levantar API en http://localhost:8084
./mvnw clean package                   # compilar + empaquetar jar
./mvnw test                            # todos los tests
./mvnw test -Dtest=UserServiceImplTest # una sola clase de test
./mvnw test -Dtest=UserServiceImplTest#updatePassword_ok  # un solo metodo
./mvnw sonar:sonar                     # analisis Sonar (host en pom.xml: localhost:9000)
docker compose up --build              # app en contenedor, lee .env
```

Java 25, Spring Boot 4.1.1, Maven wrapper. En Windows usar `mvnw.cmd`.

Variables obligatorias en `.env` (las carga `BackendProkectApplication.main` con dotenv-java **antes** de arrancar Spring, y `application.properties` las resuelve como `${...}`): `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`.

Docs de API: `/swagger-ui.html`, `/v3/api-docs`, Scalar en `src/main/resources/static/scalar.html`. Health: `/actuator/health`.

## Arquitectura

API REST en capas, PostgreSQL + JPA (`ddl-auto=update`, sin migraciones), auth por JWT **manual**.

```
controller   -> recibe HTTP, valida body, devuelve ResponseEntity. Sin logica.
service      -> interfaz: el contrato publico de la capa.
service.impl -> implementacion: reglas de negocio, validacion de token, mapeo entity->Response.
repository   -> interfaz Spring Data JPA. Sin implementacion manual.
entity       -> tabla JPA. Nunca sale del backend.
model        -> DTOs: Request (entra), Response (sale), JwtValidate (claims del token).
exception    -> excepciones de dominio + GlobalExceptionHandler que las traduce a HTTP.
config       -> beans de infraestructura (hoy solo PasswordEncoder).
```

Regla de flujo: **una capa solo llama a la siguiente**. Controller -> Service (interfaz) -> Repository -> Entity. El controller nunca toca repository ni entity; el repository nunca conoce DTOs.

### Seguridad: no hay filtro de Spring Security (decision tomada, no modificar)

`SecurityConfig` solo expone el bean `BCryptPasswordEncoder`. **No existe SecurityFilterChain ni filtro JWT**, y asi se queda: no propongas migrar a filtros de Spring Security. Cada endpoint protegido recibe el header a mano:

```java
@RequestHeader(value = "Authorization") String authHeader
```

y lo pasa al service, que llama `jwtService.validateAccessToken(authHeader)` como **primera linea** del metodo. Esa llamada devuelve un `JwtValidate` con `id`, `username`, `role` sacados del token; de ahi sale el usuario actual y la autorizacion por rol (ej. `getUserAll` exige `role.equals("admin")` o lanza `ForbiddenException`). Si anades un endpoint protegido y olvidas esa llamada, queda publico.

El token se firma HS512 con claims `sub=username`, `id`, `rol`.

### Dos repositorios sobre la misma entidad (decision tomada, no modificar)

`AuthRepository` (registro/login, busca por username) y `UserRepository` (perfil, busca por id) extienden ambos `JpaRepository<User, Integer>`. Es intencional: un repositorio por caso de uso, no por entidad. No los unifiques.

### Manejo de errores en los services: un solo catch (decision tomada, no modificar)

Todas las excepciones personalizadas extienden `RuntimeException` directamente, sin clase base intermedia. Cada metodo de `UserServiceImpl` y `AuthServiceImpl` envuelve su cuerpo en un unico catch:

```java
try {
    ...
} catch (Exception e) {
    throw new InternalServerErrorException(e.getMessage());
}
```

Consecuencia a tener presente: una excepcion de dominio lanzada **dentro** del try (`BadRequestException`, `ForbiddenException`, `NotFoundException`, `NoContentException`, `JwtAuthenticationException`) la atrapa ese catch y sale al cliente como **500** con el mensaje original, no con su codigo propio. Los codigos 400/403/404/204/401 del `GlobalExceptionHandler` solo aplican a excepciones lanzadas fuera de un try de service.

Es el patron elegido: no lo cambies, no introduzcas una clase base comun ni multicatch a menos que se pida explicitamente.

## Como implementar un feature nuevo (ejemplo completo: `Product`)

Orden de creacion: entity -> repository -> model -> excepcion (si hace falta) -> interfaz service -> impl -> controller.

### 1. Entity - `entity/Product.java`

Solo persistencia. Lombok `@Data @Builder @AllArgsConstructor @NoArgsConstructor`, id `IDENTITY`, columnas `nullable=false`, `uniqueConstraints` en la tabla si aplica.

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
@Table(name = "products", uniqueConstraints = {@UniqueConstraint(columnNames = {"code"})})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer ownerId;
}
```

### 2. Repository - `repository/ProductRepository.java`

Interfaz anotada `@Repository` que extiende `JpaRepository<Entidad, Integer>`. Solo query methods derivados del nombre; `Optional<T>` cuando el "no existe" es un caso de negocio, `T` plano cuando el id viene de un token ya validado.

```java
package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Product findProductById(Integer id);

    Optional<Product> findProductByCode(String code);

    List<Product> findAllByOwnerId(Integer ownerId);
}
```

### 3. Models - `model/Request/` y `model/Response/`

Request: validacion con `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Positive`). Response: **nunca** devuelvas la entity; expon solo los campos publicos (fijate que `UserResponse` omite `id` y `password`).

```java
// model/Request/CreateProductRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    @NotNull
    @Positive
    private Double price;
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
    private String code;
    private String name;
    private Double price;
}
```

### 4. Interfaz de service - `service/ProductService.java`

Contrato sin anotaciones. Firma tipica: `authHeader` como primer parametro en todo metodo protegido, Request como segundo, retorno siempre un Response o `List<Response>` (nunca la entity).

```java
package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateProductRequest;
import backend_project.backend_project.model.Response.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(String authHeader, CreateProductRequest createProductRequest);

    List<ProductResponse> getMyProducts(String authHeader);
}
```

### 5. Implementacion - `service/impl/ProductServiceImpl.java`

`@Service @RequiredArgsConstructor`, dependencias `private final` inyectadas por constructor. Estructura del metodo: `try` -> validar token -> cargar datos -> aplicar reglas (lanzando excepciones de dominio) -> persistir -> mapear a Response con el builder, y cerrar con `catch (Exception e) -> InternalServerErrorException`.

```java
package backend_project.backend_project.service.impl;

import backend_project.backend_project.entity.Product;
import backend_project.backend_project.exception.*;
import backend_project.backend_project.model.JwtValidate;
import backend_project.backend_project.model.Request.CreateProductRequest;
import backend_project.backend_project.model.Response.ProductResponse;
import backend_project.backend_project.repository.ProductRepository;
import backend_project.backend_project.service.JwtService;
import backend_project.backend_project.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final JwtService jwtService;

    @Override
    public ProductResponse createProduct(String authHeader, CreateProductRequest createProductRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            productRepository.findProductByCode(createProductRequest.getCode())
                    .ifPresent(p -> { throw new BadRequestException("El codigo ya esta en uso"); });

            Product product = Product.builder()
                    .code(createProductRequest.getCode())
                    .name(createProductRequest.getName())
                    .price(createProductRequest.getPrice())
                    .ownerId(jwtValidate.getId())
                    .build();

            productRepository.save(product);

            return ProductResponse.builder()
                    .code(product.getCode())
                    .name(product.getName())
                    .price(product.getPrice())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public List<ProductResponse> getMyProducts(String authHeader) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            List<Product> products = productRepository.findAllByOwnerId(jwtValidate.getId());

            if (products.isEmpty()) {
                throw new NoContentException("No se encontraron productos");
            }

            return products.stream()
                    .map(p -> ProductResponse.builder()
                            .code(p.getCode())
                            .name(p.getName())
                            .price(p.getPrice())
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }
}
```

### 6. Controller - `controller/ProductController.java`

`@RestController @AllArgsConstructor @RequestMapping("/api/<recurso>")`, depende de la **interfaz** del service. Cada metodo: una linea que delega y envuelve en `ResponseEntity.ok(...)`. Cero logica, cero try/catch (de eso se encarga `GlobalExceptionHandler`).

```java
package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.CreateProductRequest;
import backend_project.backend_project.model.Response.ProductResponse;
import backend_project.backend_project.service.ProductService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody CreateProductRequest createProductRequest) {

        return ResponseEntity.ok(productService.createProduct(authHeader, createProductRequest));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ProductResponse>> getMyProducts(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(productService.getMyProducts(authHeader));
    }
}
```

### 7. Excepciones - `exception/`

Las existentes cubren casi todo: `BadRequestException` (400), `NotFoundException` (404), `ForbiddenException` (403), `NoContentException` (204), `JwtAuthenticationException` (401), `InternalServerErrorException` (500). Todas extienden `RuntimeException` con un constructor `(String message)`.

Para anadir una nueva hacen falta **dos** pasos, o devolvera 500 generico:

```java
// 1. exception/ConflictException.java
package backend_project.backend_project.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

```java
// 2. handler en GlobalExceptionHandler
@ExceptionHandler(ConflictException.class)
public ResponseEntity<ErrorResponse> handleException(ConflictException conflictException) {
    ErrorResponse errorResponse = new ErrorResponse(
            conflictException.getMessage(),
            HttpStatus.CONFLICT.value());
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
}
```

El body de error siempre es `ErrorResponse { message, statusCode }`.

## Convenciones

- Paquete base `backend_project.backend_project`; subpaquete por capa. `model/Request` y `model/Response` van con inicial mayuscula (asi esta el codigo existente).
- Lombok en todo: `@Data @Builder @Getter @Setter @AllArgsConstructor @NoArgsConstructor` en entities y DTOs; `@RequiredArgsConstructor` en services; `@AllArgsConstructor` en controllers. Nada de `@Autowired` en campos.
- Construccion de objetos siempre con `.builder()`.
- Mensajes de error de cara al usuario en espanol.
- Sin migraciones: el esquema lo genera Hibernate desde las entities (`ddl-auto=update`). Un cambio de entity cambia la tabla al arrancar.
