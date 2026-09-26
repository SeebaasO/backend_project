---
title: Entidad Car, repositorio y contratos de datos del CRUD de carros
version: 2.0
date_created: 2026-09-21
last_updated: 2026-09-23
owner: Equipo backend_project
tags: [schema, data, jpa, car, rental, crud]
---

# Introduction

Esta especificacion define la entidad JPA `Car`, su repositorio Spring Data y los DTOs (`Request` / `Response`) compartidos por las cinco operaciones del CRUD de carros del sistema de renta. Es la base comun: las especificaciones de `create`, `read all`, `read by id`, `update` y `delete` dependen de este documento y no redefinen estos artefactos.

## 1. Purpose & Scope

**Proposito**: fijar el modelo de datos persistente y los contratos de entrada/salida del recurso `Car` dentro del proyecto `backend_project`, una API REST Spring Boot en capas con autenticacion JWT manual, cuyo dominio es la renta de carros.

**Alcance incluido**:

- Clase `entity/Car.java`.
- Interfaz `repository/CarRepository.java`.
- DTOs `model/Request/CreateCarRequest.java`, `model/Request/UpdateCarRequest.java`, `model/Response/CarResponse.java`, `model/Response/CreateCarResponse.java`.
- Uso del sobre estandar `model/Response/ApiResponse.java` como envoltura de toda salida HTTP.
- Reglas de generacion de esquema (Hibernate `ddl-auto=update`).
- Semantica del campo `available` y del borrado fisico.

**Alcance excluido**:

- Logica de negocio de cada operacion (ver especificaciones por metodo).
- Endpoints HTTP (ver especificaciones por metodo).
- Entidad `Rental` (reserva o alquiler efectivo): fuera del alcance de esta iteracion.
- Entidad `Agency`: todos los carros pertenecen a una unica agencia implicita, por lo que no se modela.
- Migraciones SQL versionadas: el proyecto no las usa.

**Audiencia**: agentes de IA generativa y desarrolladores que implementen o modifiquen el CRUD de carros.

**Supuestos**:

- Existe una sola agencia. Ningun carro guarda referencia a sucursal, ciudad ni propietario.
- El catalogo es unico y compartido: un carro no pertenece a un usuario.
- El proyecto ya tiene operativos `JwtService`, `GlobalExceptionHandler` y las excepciones de dominio.
- La lectura del catalogo es publica (sin token); la escritura exige rol `admin`.

## 2. Definitions

| Termino | Definicion |
|---|---|
| **JPA** | Jakarta Persistence API. Especificacion de mapeo objeto-relacional que usa Hibernate como implementacion. |
| **Entity** | Clase Java anotada con `@Entity` que se mapea a una tabla. Nunca se devuelve al cliente. |
| **DTO** | Data Transfer Object. Objeto plano de transporte entre capas. |
| **Request** | DTO que entra desde el cliente HTTP. Lleva anotaciones de validacion. |
| **Response** | DTO que sale hacia el cliente HTTP. Solo expone campos publicos. Viaja siempre dentro de `ApiResponse`. |
| **Sobre (`ApiResponse<T>`)** | Envoltura estandar de toda respuesta HTTP del proyecto: `{ "result": boolean, "data": T }`. `result = true` en exito, `false` en error. |
| **JWT** | JSON Web Token. Token firmado HS512 que transporta los claims `sub`, `id`, `rol`. |
| **Placa (`plate`)** | Identificador legal del vehiculo. Identificador de negocio unico del carro. |
| **Borrado fisico** | Eliminacion de la fila con una sentencia `DELETE`. Es el unico borrado del CRUD de carros y es irreversible. |
| **Despublicar** | Marcar `available = false` conservando la fila. No es un borrado: el carro sigue existiendo y se puede volver a publicar con `PUT`. |
| **ddl-auto=update** | Modo de Hibernate que crea o altera tablas al arrancar segun las entidades, sin borrar columnas existentes. |
| **Lombok** | Libreria que genera getters, setters, constructores y builders en tiempo de compilacion. |

## 3. Requirements, Constraints & Guidelines

### Entidad

- **REQ-001**: La entidad se llama `Car` y vive en el paquete `backend_project.backend_project.entity`.
- **REQ-002**: Se mapea a la tabla `cars` mediante `@Table(name = "cars", uniqueConstraints = {@UniqueConstraint(columnNames = {"plate"})})`.
- **REQ-003**: Campos exactos, en este orden:

| Campo | Tipo Java | Columna | Nulable | Notas |
|---|---|---|---|---|
| `id` | `Integer` | `id` | generado | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `plate` | `String` | `plate` | `nullable = false` | placa, unica, almacenada en mayusculas |
| `brand` | `String` | `brand` | `nullable = false` | marca, ej. `Renault` |
| `model` | `String` | `model` | `nullable = false` | linea o modelo comercial, ej. `Logan` |
| `year` | `Integer` | `car_year` | `nullable = false` | anio del vehiculo; la columna se renombra para evitar la palabra clave SQL `year` |
| `color` | `String` | `color` | `nullable = false` | color visible del vehiculo |
| `pricePerDay` | `Double` | `price_per_day` | `nullable = false` | tarifa diaria de renta, mayor que cero |
| `available` | `Boolean` | `available` | `nullable = false` | `true` = publicado y rentable; `false` = retirado del catalogo publico pero conservado |

- **REQ-004**: La entidad se anota con `@Data @Builder @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Entity @Table(...)`, igual que `User`.
- **CON-001**: La entidad nunca se serializa hacia el cliente. Toda salida HTTP usa `CarResponse`, salvo el alta, que usa `CreateCarResponse`. Ambos viajan dentro del sobre `ApiResponse`.
- **CON-002**: No se agregan campos de auditoria (`createdAt`, `updatedAt`, `ownerId`) ni `agencyId`. Una sola agencia y un catalogo sencillo no los justifican.
- **CON-003**: `available` tiene un unico significado: si el carro esta publicado y es rentable. **No** marca borrado. Un carro eliminado no tiene fila: el borrado es fisico (`DELETE`), ver `spec-design-car-delete.md`.

### Repositorio

- **REQ-005**: `CarRepository` es una interfaz anotada `@Repository` que extiende `JpaRepository<Car, Integer>`.
- **REQ-006**: Metodos requeridos:

| Firma | Origen | Uso |
|---|---|---|
| `Optional<Car> findCarById(Integer id)` | derivado | lectura por id, actualizacion y borrado |
| `Optional<Car> findCarByPlate(String plate)` | derivado | verificacion de placa unica en alta y edicion |
| `List<Car> findAllByAvailableTrue()` | derivado | listado publico del catalogo vigente |
| `Car save(Car car)` | heredado | alta y actualizacion |
| `void delete(Car car)` | heredado | borrado fisico de la fila |

- **CON-004**: El repositorio no contiene implementacion manual, ni `@Query` JPQL, ni conoce DTOs.
- **CON-005**: `delete(Car)` se usa tal como lo hereda `JpaRepository`: no se redeclara en la interfaz. `deleteById` no se usa, porque la entidad ya esta cargada cuando se borra (ver `spec-design-car-delete.md` REQ-004).
- **GUD-001**: Se devuelve `Optional<Car>` porque "el carro no existe" es un caso de negocio que produce `NotFoundException`. Contrasta con `UserRepository.findUserById`, que devuelve `User` plano porque el id proviene de un token ya validado.

### DTOs

- **REQ-007**: `CreateCarRequest` expone `plate`, `brand`, `model`, `year`, `color`, `pricePerDay`. Validaciones: `@NotBlank` en `plate`, `brand`, `model` y `color`; `@NotNull @Min(1900) @Max(2100)` en `year`; `@NotNull @Positive` en `pricePerDay`.
- **REQ-008**: `CreateCarRequest` **no** expone `available`. El alta siempre publica el carro con `available = true`.
- **REQ-009**: `UpdateCarRequest` expone exactamente dos campos: `pricePerDay` (`@NotNull @Positive`) y `available` (`@NotNull`). Son los unicos campos editables del carro. `plate`, `brand`, `model`, `year` y `color` son inmutables desde la API: se fijan en el alta y ningun endpoint los modifica. Ver `spec-design-car-update.md`.
- **REQ-010**: `CarResponse` expone `id`, `plate`, `brand`, `model`, `year`, `color`, `pricePerDay`, `available`. Incluye `id` porque el cliente lo necesita para invocar `read by id`, `update` y `delete`. Lo usan `read all`, `read by id`, `update` y `delete`.
- **REQ-011**: `CreateCarResponse` expone un unico campo, `id`. Es la salida exclusiva del alta (`POST /api/car`); ninguna otra operacion lo usa.
- **REQ-012**: Los cuatro DTOs se anotan con `@Data @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.

### Sobre estandar de respuesta

- **REQ-014**: Toda respuesta HTTP del recurso `Car` se serializa como `ApiResponse<T>`, con dos propiedades: `result` de tipo `Boolean` y `data` de tipo `T`.
- **REQ-015**: `ApiResponse` vive en `model/Response/ApiResponse.java`, es generico y no conoce ningun tipo concreto del dominio.
- **REQ-016**: En respuestas de exito `result = true` y `data` contiene: el `CreateCarResponse` en el alta, el `CarResponse` en lectura por id, actualizacion y baja, y un array de `CarResponse` en el listado.
- **REQ-017**: En respuestas de error `result = false` y `data` contiene un `ErrorResponse` con `message` y `statusCode`. Lo construye `GlobalExceptionHandler`, no los controllers.
- **CON-010**: El sobre lo arma el **controller**, no el service. Los metodos de `CarService` siguen devolviendo `CarResponse`, `CreateCarResponse` o `List<CarResponse>` en crudo.
- **CON-011**: `ApiResponse` es transversal a todo el proyecto: `AuthController` y `UserController` tambien lo usan. No es un artefacto del CRUD de carros y no debe duplicarse por recurso.
- **PAT-002**: El sobre se construye con el builder generico explicito: `ApiResponse.<CarResponse>builder().result(true).data(...).build()`.
- **CON-006**: Los paquetes se escriben con inicial mayuscula: `model.Request` y `model.Response`, tal como el codigo existente.
- **PAT-001**: Todo objeto se construye con `.builder()`, nunca con `new` mas setters.

### Normalizacion de la placa

- **REQ-013**: Antes de consultar unicidad y antes de persistir, la capa de servicio normaliza la placa con `plate.trim().toUpperCase()`.
- **CON-007**: La unicidad efectiva la garantiza la restriccion `UNIQUE` de la tabla. La comprobacion previa en el servicio es una cortesia para devolver un mensaje legible, no una garantia ante peticiones concurrentes.
- **GUD-002**: No se valida el formato de placa con `@Pattern`. El proyecto no fija un pais objetivo y una expresion regular colombiana (`^[A-Z]{3}[0-9]{2,3}$`) rechazaria placas validas de otros paises.

### Persistencia y esquema

- **CON-008**: No hay migraciones. Hibernate genera y altera la tabla `cars` al arrancar segun `spring.jpa.hibernate.ddl-auto=update`.
- **CON-009**: Con `ddl-auto=update` una columna renombrada o eliminada en la entidad no se borra en la base de datos. Cambios destructivos exigen intervencion manual en PostgreSQL.
- **GUD-003**: `pricePerDay` se modela como `Double` por coherencia con el resto del proyecto y por simplicidad. `BigDecimal` seria preferible para dinero real; queda documentado como deuda tecnica consciente.

## 4. Interfaces & Data Contracts

### Capa entity

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
@Table(name = "cars", uniqueConstraints = {@UniqueConstraint(columnNames = {"plate"})})
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String plate;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(name = "car_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String color;

    @Column(name = "price_per_day", nullable = false)
    private Double pricePerDay;

    @Column(nullable = false)
    private Boolean available;
}
```

### Tabla generada

| Columna | Tipo PostgreSQL | Restriccion |
|---|---|---|
| `id` | `serial` / `integer` | `PRIMARY KEY`, autoincremental |
| `plate` | `varchar(255)` | `NOT NULL`, `UNIQUE` |
| `brand` | `varchar(255)` | `NOT NULL` |
| `model` | `varchar(255)` | `NOT NULL` |
| `car_year` | `integer` | `NOT NULL` |
| `color` | `varchar(255)` | `NOT NULL` |
| `price_per_day` | `double precision` | `NOT NULL` |
| `available` | `boolean` | `NOT NULL` |

### Capa repository

```java
package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarRepository extends JpaRepository<Car, Integer> {

    Optional<Car> findCarById(Integer id);

    Optional<Car> findCarByPlate(String plate);

    List<Car> findAllByAvailableTrue();
}
```

### DTOs

```java
// model/Request/CreateCarRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCarRequest {

    @NotBlank
    private String plate;

    @NotBlank
    private String brand;

    @NotBlank
    private String model;

    @NotNull
    @Min(1900)
    @Max(2100)
    private Integer year;

    @NotBlank
    private String color;

    @NotNull
    @Positive
    private Double pricePerDay;
}
```

```java
// model/Request/UpdateCarRequest.java
package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCarRequest {

    @NotNull
    @Positive
    private Double pricePerDay;

    @NotNull
    private Boolean available;
}
```

```java
// model/Response/CarResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarResponse {

    private Integer id;
    private String plate;
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private Double pricePerDay;
    private Boolean available;
}
```

```java
// model/Response/CreateCarResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCarResponse {

    private Integer id;
}
```

### Sobre estandar

```java
// model/Response/ApiResponse.java
package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private Boolean result;
    private T data;
}
```

### Ejemplo JSON de `CarResponse` dentro del sobre

```json
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

### Ejemplo JSON de error

```json
{
  "result": false,
  "data": {
    "message": "Carro no encontrado",
    "statusCode": 500
  }
}
```

## 5. Acceptance Criteria

- **AC-001**: Given la aplicacion arranca con una base PostgreSQL vacia, When Hibernate procesa las entidades, Then existe la tabla `cars` con las ocho columnas de la seccion 4, `id` autoincremental y restriccion `UNIQUE` sobre `plate`.
- **AC-002**: Given un `Car` con `plate` nulo, When se invoca `carRepository.save(car)`, Then la persistencia falla por violacion de `NOT NULL`.
- **AC-003**: Given existe una fila con `plate = "ABC123"`, When se guarda otro `Car` con la misma placa, Then la persistencia falla por violacion de la restriccion `UNIQUE`.
- **AC-004**: Given la tabla `cars` contiene una fila con `id = 3`, When se invoca `carRepository.findCarById(3)`, Then se devuelve un `Optional` presente cuyo `Car` tiene `id = 3`.
- **AC-005**: Given la tabla `cars` no contiene `id = 999`, When se invoca `carRepository.findCarById(999)`, Then se devuelve `Optional.empty()`.
- **AC-006**: Given la tabla contiene dos carros con `available = true` y uno con `available = false`, When se invoca `carRepository.findAllByAvailableTrue()`, Then la lista devuelta tiene tamanio 2 y ninguno de sus elementos tiene `available = false`.
- **AC-006b**: Given la tabla contiene una fila con `id = 3`, When se invoca `carRepository.delete(car)` sobre esa entidad, Then la fila desaparece de `cars` y el conteo total disminuye en uno.
- **AC-007**: Given un `CreateCarRequest` con `pricePerDay = 0.0`, When se valida con `jakarta.validation`, Then se reporta violacion de `@Positive`.
- **AC-008**: Given un `CreateCarRequest` con `year = 1899`, When se valida, Then se reporta violacion de `@Min`.
- **AC-009**: Given un `CreateCarRequest` con `plate` compuesto solo de espacios, When se valida, Then se reporta violacion de `@NotBlank`.
- **AC-010**: Given un `UpdateCarRequest` sin `available`, When se valida, Then se reporta violacion de `@NotNull`.
- **AC-010b**: Given el tipo `UpdateCarRequest`, When se inspeccionan sus campos declarados, Then son exactamente dos: `pricePerDay` y `available`.
- **AC-011**: El tipo `CarResponse` no contiene ninguna referencia al tipo `Car`, verificable por inspeccion de sus imports.
- **AC-012**: El tipo `CreateCarResponse` declara exactamente un campo, `id`, de tipo `Integer`, y tampoco referencia al tipo `Car`.
- **AC-013**: Given cualquier respuesta exitosa de un endpoint de `/api/car`, When se inspecciona el JSON raiz, Then contiene exactamente dos propiedades, `result` y `data`, con `result = true`.
- **AC-014**: Given cualquier respuesta de error de un endpoint de `/api/car`, When se inspecciona el JSON raiz, Then `result = false` y `data` contiene `message` y `statusCode`.
- **AC-015**: El tipo `ApiResponse` no importa ningun tipo del paquete `entity` ni ningun Response concreto: es generico.

## 6. Test Automation Strategy

- **Test Levels**: unitario sobre validaciones de DTO; integracion sobre el repositorio.
- **Frameworks**: JUnit 5 y Mockito, provistos por `spring-boot-starter-test`.
- **Repositorio**: prueba con `@DataJpaTest` apuntando a PostgreSQL de desarrollo, cubriendo `findCarById`, `findCarByPlate` y `findAllByAvailableTrue`.
- **DTOs**: prueba con `Validator` obtenido de `Validation.buildDefaultValidatorFactory()`, cubriendo un caso valido y uno invalido por cada anotacion.
- **Test Data Management**: cada test crea sus propios `Car` con `Car.builder()`. `@DataJpaTest` revierte la transaccion al finalizar; no se requiere limpieza manual.
- **CI/CD Integration**: `./mvnw test` ejecuta la suite completa. En Windows: `mvnw.cmd test`.
- **Coverage Requirements**: cobertura de linea minima 80% en `entity`, `model` y `repository`.
- **Performance Testing**: no aplica a esta especificacion.

## 7. Rationale & Context

- Ocho campos y cero relaciones responden al requisito de una unica agencia y un catalogo sencillo. Sucursal, seguro, kilometraje y fotos quedan fuera deliberadamente.
- La placa es el identificador de negocio natural del vehiculo y es unica por ley; por eso lleva `UNIQUE`, a diferencia del `name` de un producto generico.
- `year` se mapea a la columna `car_year` porque `year` es palabra clave en varios dialectos SQL y su uso como nombre de columna produce fallos dificiles de diagnosticar al cambiar de motor.
- `CarResponse` si expone `id`, a diferencia de `UserResponse` que lo oculta, porque el `id` del carro es un dato publico necesario para direccionar el recurso via URL. El `id` de usuario no lo es: viaja dentro del token.
- `CarResponse` expone `available` porque es el dato que un cliente de renta necesita antes de intentar reservar.
- `CarResponse` sirve a cuatro de las cinco operaciones: `read all`, `read by id`, `update` y `delete`. Multiplicar DTOs de salida por operacion no aportaria valor en esos casos, porque todos devuelven la ficha completa del carro.
- El alta es la excepcion y usa `CreateCarResponse`, con solo `id`. Devolver la ficha completa al crear repetiria datos que el cliente acaba de enviar; lo unico que no conoce es el identificador generado. Ver `spec-design-car-create.md` REQ-008.
- El sobre `ApiResponse` da al cliente una forma unica que parsear: siempre lee `result` para decidir el camino y `data` para el contenido, sin ramificar por endpoint ni inferir el exito del codigo HTTP. Esto importa especialmente en este proyecto, donde el patron de un solo `catch` hace que casi todos los errores lleguen como `500` (ver CON-003 de las specs de diseno): `result` es una senal explicita que no depende del codigo.
- El sobre se arma en el controller y no en el service para que la capa de negocio siga devolviendo tipos de dominio, testeables sin desenvolver. El controller conserva su papel de traduccion HTTP.
- `ApiResponse` es generico en lugar de tener un `data` de tipo `Object` para que el tipo del contenido quede declarado en la firma del controller y Swagger lo documente correctamente.
- `CreateCarRequest` y `UpdateCarRequest` son clases muy distintas y no comparten jerarquia: el alta recibe la identidad completa del vehiculo, la edicion solo los dos campos de gestion diaria. `available` aparece unicamente en la edicion, porque es el unico camino para reactivar un carro dado de baja.
- Los campos de identidad (`plate`, `brand`, `model`, `year`, `color`) son inmutables una vez creado el carro: cambiarlos convertiria la fila en otro vehiculo distinto. El razonamiento completo esta en `spec-design-car-update.md`, seccion 7.
- El borrado fisico mantiene limpia la semantica de `available`: un carro despublicado tiene fila con `available = false`; uno eliminado no tiene fila. Son estados distintos y no se confunden.
- **Riesgo documentado**: al no conservarse la fila, el `id` de un carro eliminado desaparece. Cuando exista la entidad `Rental`, un `DELETE` sobre un carro con rentas asociadas violara la clave foranea o dejara historico huerfano. Ver `spec-design-car-delete.md` seccion 7.

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**: PostgreSQL - base de datos relacional que aloja la tabla `cars`. Conexion JDBC con las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

### Third-Party Services

- Ninguno.

### Infrastructure Dependencies

- **INF-001**: Archivo `.env` en la raiz del proyecto con las variables obligatorias. `BackendProkectApplication.main` las carga con dotenv-java antes de arrancar Spring.

### Data Dependencies

- Ninguna. La tabla `cars` no se alimenta de fuentes externas.

### Technology Platform Dependencies

- **PLT-001**: Java 25 - version del proyecto, requerida para compilar.
- **PLT-002**: Spring Boot 4.1.1 con starters `data-jpa` y `validation`.
- **PLT-003**: Lombok - obligatorio; toda la base de codigo depende de sus anotaciones.

### Compliance Dependencies

- Ninguna.

## 9. Examples & Edge Cases

```java
// Construccion correcta: siempre con builder, sin id (lo genera la base de datos)
Car car = Car.builder()
        .plate("ABC123")
        .brand("Renault")
        .model("Logan")
        .year(2022)
        .color("Blanco")
        .pricePerDay(120000.0)
        .available(true)
        .build();

// Mapeo entity -> response. Nunca se devuelve la entity.
CarResponse response = CarResponse.builder()
        .id(car.getId())
        .plate(car.getPlate())
        .brand(car.getBrand())
        .model(car.getModel())
        .year(car.getYear())
        .color(car.getColor())
        .pricePerDay(car.getPricePerDay())
        .available(car.getAvailable())
        .build();

// Borde: id inexistente. El Optional vacio es el caso de negocio "no encontrado".
Car found = carRepository.findCarById(999)
        .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

// Borde: placa con espacios y minusculas. Se normaliza antes de consultar y persistir.
String plate = "  abc123 ".trim().toUpperCase(); // "ABC123"

// Borde: carro despublicado. La fila permanece; solo cambia el flag.
car.setAvailable(false);
carRepository.save(car);

// Borrado: la fila desaparece. No confundir con lo anterior.
carRepository.delete(car);

// Antipatron: no construir con new mas setters.
// Car c = new Car(); c.setPlate("ABC123");

// Antipatron: no borrar filas. El CRUD de carros no llama a delete ni deleteById.
// carRepository.delete(car);
```

## 10. Validation Criteria

1. Existen los seis archivos propios del recurso: `entity/Car.java`, `repository/CarRepository.java`, `model/Request/CreateCarRequest.java`, `model/Request/UpdateCarRequest.java`, `model/Response/CarResponse.java`, `model/Response/CreateCarResponse.java`. Existe ademas el transversal `model/Response/ApiResponse.java`.
2. `Car` declara exactamente los ocho campos de REQ-003, con `@Column(nullable = false)` en los siete no generados.
3. `Car` declara `uniqueConstraints` sobre la columna `plate`.
4. `CarRepository` no declara metodos ajenos a los de REQ-006 ni importa clases de `model`.
5. `CarRepository` no redeclara `delete` ni `deleteById`: el borrado usa el metodo heredado de `JpaRepository`.
6. Ningun archivo bajo `model/` importa `backend_project.backend_project.entity.Car`.
7. `ApiResponse` declara exactamente dos campos, `result` y `data`, y su parametro de tipo no esta acotado.
8. Ningun metodo de `CarService` declara `ApiResponse` en su firma: el sobre solo aparece en `CarController` y en `GlobalExceptionHandler`.
9. `./mvnw clean package` compila sin errores.
10. Tras arrancar la aplicacion, la tabla `cars` en PostgreSQL muestra las ocho columnas con los tipos de la seccion 4.

## 11. Related Specifications / Further Reading

- [spec-design-car-create.md](Car/spec-design-car-create.md)
- [spec-design-car-read-all.md](spec-design-car-read-all.md)
- [spec-design-car-read-by-id.md](spec-design-car-read-by-id.md)
- [spec-design-car-update.md](spec-design-car-update.md)
- [spec-design-car-delete.md](spec-design-car-delete.md)
- `../../CLAUDE.md` en la raiz del repositorio: arquitectura por capas y decisiones cerradas.
