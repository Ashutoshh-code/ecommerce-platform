# Cart Service — Full Codebase Guide (for a Data-Science → Backend intern)

This document explains **every file, every line, and the "why" behind it** in this
repository. It assumes you know how to code (e.g. Python/pandas-level programming)
but have never worked with Java, Spring Boot, REST APIs, or relational databases in
a production backend context. Read Part 1 once, then use Part 2 as a
file-by-file reference while you read the actual source next to it.

> Where this file lives: `docs/CODEBASE_GUIDE.md`. It is documentation only — it is
> never compiled or executed, so feel free to annotate/extend it.

---

## Part 0 — What is this project, in one paragraph?

This is **one microservice**, called the **Cart Service**, that is part of a bigger
e-commerce system. Its only job is to manage shopping carts: create them, add/remove
items, check item counts, and hand a finished cart off to checkout. It does **not**
know how to look up product names, check stock, or actually create an order — those
are the jobs of three *other* microservices (**Product Service**, **Merchant
Service**, **Order Service**) that are **owned by other teams and not built yet**.
This service talks to them over HTTP using a library called **Feign**, and if they
are unreachable, it fails safely instead of guessing.

```
                         ┌────────────────────┐
   Browser / Mobile App  │                    │
   ───────────────────►  │   CART SERVICE     │  (this repository)
      HTTP + JSON        │  (this codebase)   │
                         └─────────┬───────────┘
                                   │ HTTP calls (Feign clients)
                    ┌──────────────┼───────────────┐
                    ▼              ▼               ▼
            Product Service  Merchant Service  Order Service
            (not built yet)  (not built yet)   (not built yet)
```

---

## Part 1 — Concepts you need before reading Java/Spring code

Skim this once. Every concept here is used repeatedly in Part 2, and Part 2 will
link back to these definitions instead of re-explaining them each time.

### 1.1 Java basics that matter here
- **Class** = like a Python class, but every `.java` file has exactly one public
  top-level class/interface, and the file name must match that name exactly
  (e.g. `CartController.java` contains `class CartController`).
- **Interface** = a contract with method *signatures* but no bodies (similar to an
  Abstract Base Class in Python, but stricter — no implementation at all, unless it's
  a `default` method, which isn't used here). A `class` can `implement` an interface,
  promising to provide real code for every method the interface declares.
- **Package** = Java's namespace/folder mechanism, equivalent to a Python package.
  `com.example.ecommerceplatform.CartService.Controller` is both the Java package
  name *and* the folder path `com/example/ecommerceplatform/CartService/Controller/`.
- **Annotations** (`@Something`) = metadata attached to a class/field/method that a
  framework (Spring) reads at startup to decide what to do. They look like Python
  decorators but usually don't wrap the method with new logic themselves — instead,
  a separate framework component scans for them and reacts. E.g. `@GetMapping("/x")`
  doesn't run any code directly; Spring's web framework sees it and wires up a route.
- **Static typing**: every variable has a declared type (`UUID userId`, not `userId`).
  The compiler checks types before the program ever runs — this catches a whole
  category of bugs Python would only find at runtime.

### 1.2 What is Spring / Spring Boot?
**Spring** is a framework for building Java applications built around one big idea:
**Dependency Injection (DI)**, also called **Inversion of Control (IoC)**.

Instead of a class creating the objects it depends on (`new CartRepository()`), you
declare *what you need* and Spring hands you an already-built instance. Concretely
in this codebase:

```java
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;   // Spring "injects" this
    ...
}
```

Spring builds **one shared instance** of `CartRepository` (called a **bean**) at
startup, and hands it to `CartServiceImpl`'s constructor automatically. This is
useful because:
- You never wire objects together by hand — Spring resolves the whole dependency
  graph for you (repository → service → controller).
- Swapping implementations (e.g. for tests) is trivial: you provide a different bean.
- It's what "loose coupling" means in practice — `CartServiceImpl` never says `new
  CartRepositoryImpl()`, so it doesn't need to know how that class is implemented.

**Spring Boot** is a distribution of Spring that auto-configures almost everything
(embedded web server, JSON serialization, database connection pool, etc.) based on
which "starter" dependencies you put in `pom.xml`, so you write far less setup code
than raw Spring.

### 1.3 What is Maven / `pom.xml`?
Maven is Java's package manager + build tool (like `pip` + a Makefile combined).
`pom.xml` ("Project Object Model") lists:
- What your project is (name, version).
- What libraries ("dependencies") it needs, by `groupId:artifactId:version`.
- How to compile/package it (the `<build><plugins>` section).

Maven downloads dependencies from a central repository (like PyPI) into `~/.m2`,
then compiles your code against them.

### 1.4 Layered architecture (the shape of this whole codebase)
This project follows the extremely common **Controller → Service → Repository →
Entity** layering:

| Layer | Package | Responsibility |
|---|---|---|
| **Controller** | `CartService/Controller` | Accepts HTTP requests, returns HTTP responses. Zero business logic. |
| **Service** | `CartService/Service` | All business rules ("can this be added to the cart?", "is there enough stock?"). |
| **Repository** | `CartService/Repository` | Talks to the database. No business logic. |
| **Entity** | `CartService/Entity` | Java classes mapped 1:1 to database tables. |
| **DTO** | `CartService/Dto` | Plain data-carrying classes used for the JSON going in/out over HTTP — deliberately *not* the same classes as Entities. |
| **Client** | `CartService/Client` | Outbound HTTP calls to *other* microservices. |
| **Exception** | `CartService/Exception` | Custom error types + a global handler that turns them into HTTP error responses. |

Data flows in one direction per request: **HTTP request → Controller → Service →
Repository → Database**, and the response flows back up, being *transformed* at each
boundary (Entity → DTO → JSON).

### 1.5 Why DTOs exist, and why they're not the same as Entities
A **DTO (Data Transfer Object)** is a class whose only purpose is to hold data for
transport (e.g., JSON going over HTTP). An **Entity** is a class whose purpose is to
be mapped onto a database table.

Why not just return the Entity from the controller?
1. **You control your public API contract separately from your database schema.**
   You can rename a database column without breaking the JSON your frontend depends
   on, and vice versa.
2. **You avoid leaking internal fields** (e.g. you may not want to expose every DB
   column to the client).
3. **You avoid "lazy loading" crashes.** JPA entities can have fields that are only
   loaded from the DB on demand (`FetchType.LAZY`); serializing an entity directly to
   JSON after the database session has closed can throw exceptions. DTOs are plain
   data, so this problem doesn't exist.
4. **You can shape the response differently than storage** — e.g. `CartItemResponseDto`
   contains a computed `lineTotal` field and a live-fetched `productName`, neither of
   which exists as a column in the `cart_items` table.

### 1.6 What is Lombok, and why do all these classes look empty?
Writing getters, setters, constructors, and builders by hand for every class is
extremely repetitive in Java. **Lombok** is a code-generation library that reads
annotations and *generates* that boilerplate at compile time (you never see the
generated code, but it exists in the compiled `.class` file).

These four annotations appear on almost every DTO/Entity in this project:

| Annotation | Generates |
|---|---|
| `@Getter` | A `getX()` method for every field. |
| `@Setter` | A `setX(value)` method for every field. |
| `@NoArgsConstructor` | An empty constructor `ClassName()`. Required by JPA/Jackson, which construct objects reflectively before populating fields. |
| `@AllArgsConstructor` | A constructor taking every field as a parameter, in declaration order. |
| `@Builder` | A fluent "builder" API: `ClassName.builder().field(value).field2(value2).build()`. Used everywhere in this codebase instead of `new ClassName(...)` because it's readable when there are many fields and you don't have to remember argument order. |
| `@RequiredArgsConstructor` | A constructor for only the `final` fields. This is how dependency injection happens in `@Service`/`@RestController` classes — Spring calls this generated constructor. |

So when you see:
```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartResponseDto {
    private UUID cartId;
    ...
}
```
There is no visible code, but at compile time this class actually has getters,
setters, two constructors, and a builder — Lombok wrote them for you.

### 1.7 What is REST, and what do the HTTP annotations mean?
**REST** is a convention for designing HTTP APIs around **resources** (nouns, like
"a cart", "a cart item") manipulated with standard HTTP **verbs**:

| HTTP verb | Meaning | Used in this project for |
|---|---|---|
| `GET` | Read data, no side effects | Fetching a cart, fetching item count |
| `POST` | Create something / trigger an action | Adding an item, checking out |
| `PATCH` | Partially update something | Adjusting an item's quantity |
| `DELETE` | Remove something | Removing an item, clearing the cart |

Spring MVC annotations map HTTP requests to Java methods:
- `@RestController` — marks a class as a web controller whose method return values
  are serialized directly to the HTTP response body as JSON (instead of, say,
  resolving to an HTML template).
- `@RequestMapping("/api/cart")` — a base path prefix for every method in the class.
- `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping` — shorthand for
  `@RequestMapping(method = GET/POST/...)`, each taking the path suffix.
- `@PathVariable` — pulls a value out of the URL path, e.g. `{userId}` in
  `/api/cart/{userId}`.
- `@RequestBody` — deserializes the incoming HTTP JSON body into a Java object.
- `@Valid` — tells Spring to run Bean Validation (see 1.8) on that object before the
  method body executes.
- `ResponseEntity<T>` — a wrapper that lets you control the HTTP status code
  *and* the body together (`ResponseEntity.ok(body)` = 200, `.status(CREATED)` = 201,
  `.noContent()` = 204).

### 1.8 Bean Validation (`@NotNull`, `@Min`, `@Valid`)
`spring-boot-starter-validation` lets you put constraint annotations directly on DTO
fields (`@NotNull`, `@Min(1)`, etc.). When a controller method parameter is annotated
`@Valid`, Spring validates the incoming object against those constraints **before**
your code runs. If validation fails, Spring throws
`MethodArgumentNotValidException` — which this project catches globally (see 1.10)
and turns into a `400 Bad Request` with a helpful message, instead of letting bad
data reach the business logic.

### 1.9 Databases, JPA, Hibernate, and the Repository pattern
- **JPA (Jakarta Persistence API)** is a Java *specification* for mapping Java
  objects to relational database rows ("ORM" — Object-Relational Mapping).
- **Hibernate** is the library that actually implements JPA (Spring Boot wires it in
  automatically via `spring-boot-starter-data-jpa`).
- An `@Entity`-annotated class = one row-shape in one table. Each field with
  `@Column` = one column.
- **Spring Data JPA** goes one step further: instead of writing SQL by hand, you
  write a Java *interface* extending `JpaRepository<EntityType, IdType>`, and Spring
  generates the implementation at runtime — including turning method names like
  `findByUserIdAndStatus(...)` into real SQL, by parsing the method name
  ("**derived query methods**"). You only write custom SQL/JPQL when the naming
  convention can't express the query (see `@Query` in `CartItemRepository`).
- **JPQL** (Java Persistence Query Language) looks like SQL but refers to *entity
  and field names* (Java-side names), not table/column names — Hibernate translates
  it to real SQL for whatever database you're using.

### 1.10 Centralized exception handling (`@RestControllerAdvice`)
Instead of writing `try/catch` in every controller method, Spring lets you define
one class annotated `@RestControllerAdvice` with methods annotated
`@ExceptionHandler(SomeException.class)`. Whenever *any* controller method (in the
targeted package) throws that exception, Spring intercepts it and routes it to the
matching handler method instead of returning a raw 500 error page. This project uses
it to turn each domain-specific exception (cart not found, insufficient stock, etc.)
into the right HTTP status code with a consistent JSON error body.

### 1.11 Feign clients, microservice calls, and circuit breakers
**Feign** lets you describe an HTTP API as a Java *interface* with Spring MVC-style
annotations (`@GetMapping`, `@PostMapping`, etc.) — but here they describe an
**outgoing** call instead of an incoming route. Spring Cloud OpenFeign generates a
real implementation of that interface at startup (a dynamic proxy) that actually
performs the HTTP call when you invoke the method.

```java
@FeignClient(name = "product-service", url = "${clients.product-service.url}",
             fallback = ProductServiceClientFallback.class)
public interface ProductServiceClient {
    @GetMapping("/api/products/{productId}")
    ProductDetailsResponse getProductDetails(@PathVariable UUID productId, ...);
}
```
Calling `productServiceClient.getProductDetails(id, null)` anywhere in the code
actually fires an HTTP `GET` request to `http://localhost:8082/api/products/{id}`
and deserializes the JSON response into `ProductDetailsResponse`.

A **circuit breaker** (Resilience4j, wired in via
`spring-cloud-starter-circuitbreaker-resilience4j` +
`spring.cloud.openfeign.circuitbreaker.enabled=true`) watches calls through a Feign
client. If calls keep failing (timeouts, connection refused, etc.), it "opens the
circuit" and starts short-circuiting to a **fallback** implementation immediately,
without even attempting the network call — this protects Cart Service from hanging
or crashing just because another team's service is down. The `fallback` class listed
in `@FeignClient` is what runs in that case. In this codebase, every fallback simply
throws `DownstreamServiceUnavailableException`, converting "the network call failed"
into a clean, typed error the rest of the app already knows how to handle.

### 1.12 `BigDecimal` for money
Java's `double`/`float` use binary floating point, which cannot represent many
decimal fractions exactly (`0.1 + 0.2 != 0.3` in floating point). For money, this
project uses `java.math.BigDecimal` everywhere, which represents decimal numbers
exactly and provides `.add()`, `.multiply()`, etc. This is why you see
`totalPrice = totalPrice.add(lineTotal)` instead of `totalPrice += lineTotal` — 
`BigDecimal` is immutable, so arithmetic methods return a *new* value rather than
mutating in place.

### 1.13 `UUID` as a primary key
Instead of auto-incrementing integers (`1, 2, 3, ...`), this project uses
**UUIDs** (Universally Unique Identifiers, 128-bit random-looking IDs like
`3fa85f64-5717-4562-b3fc-2c963f66afa6`) as primary keys. Common reasons: IDs can be
generated before the row is even inserted, they don't leak information about how
many rows exist (e.g. a competitor can't infer "you have 40,000 orders" from seeing
order id `40001`), and they're safe to use across multiple independent services/
databases without collisions.

### 1.14 `@Transactional`
A **transaction** is a group of database operations that either *all* succeed
("commit") or *all* fail and are undone ("rollback") — this guarantees the database
never ends up in a half-updated, inconsistent state. `@Transactional` on a Spring
Bean method wraps the method body in one database transaction automatically. If an
exception propagates out of the method, Spring rolls back everything the method
wrote. `@Transactional(readOnly = true)` is a hint used on read-only methods that
lets Hibernate skip some bookkeeping (dirty-checking) for a small performance gain,
and documents intent.

---

## Part 2 — File-by-file walkthrough

### 2.1 `pom.xml` (project root)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
</parent>
```
Inheriting from `spring-boot-starter-parent` gives this project sane default plugin
configuration and, crucially, **manages the version numbers** of every Spring
dependency below so you don't have to pin each one yourself (avoids version
mismatch bugs).

```xml
<groupId>com.example</groupId>
<artifactId>ecommerce-platform</artifactId>
<version>0.0.1-SNAPSHOT</version>
```
This project's own coordinates. `-SNAPSHOT` is Maven convention for "still under
active development, not a released version."

```xml
<properties>
    <java.version>21</java.version>
    <spring-cloud.version>2025.1.2</spring-cloud.version>
</properties>
```
`java.version=21` tells the compiler plugin to target Java 21 language features and
bytecode. `spring-cloud.version` is used below to pick a compatible Spring Cloud
release train.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
This imports Spring Cloud's own **Bill of Materials (BOM)** — a big list of
compatible versions for every Spring Cloud module (like OpenFeign, Resilience4j
integration) — so the Feign/circuit-breaker dependencies below don't need explicit
version numbers either.

The `<dependencies>` block, one by one:

| Dependency | Why it's here |
|---|---|
| `spring-boot-starter-data-jpa` | Pulls in Hibernate + Spring Data JPA + a connection-pool (HikariCP) so `@Entity`/`JpaRepository` work. |
| `spring-boot-starter-webmvc` | Pulls in an embedded Tomcat server + Spring MVC, so `@RestController` works and the app can listen on an HTTP port. |
| `spring-boot-starter-validation` | Adds Hibernate Validator, enabling `@NotNull`/`@Min`/`@Valid`. |
| `spring-cloud-starter-openfeign` | Enables declarative HTTP clients (`@FeignClient`). |
| `spring-cloud-starter-circuitbreaker-resilience4j` | Adds the Resilience4j circuit-breaker engine that backs Feign's `fallback` mechanism. |
| `spring-boot-devtools` (`runtime`, `optional`) | Developer convenience: auto-restarts the app when you recompile. Never shipped to production (`optional=true` means it doesn't propagate to projects depending on this one). |
| `postgresql` (`runtime`) | The JDBC driver Hibernate uses to actually talk to a PostgreSQL database. `runtime` scope = needed only when running, not when compiling your code (you never import PostgreSQL classes directly). |
| `lombok` (`optional`) | The annotation processor described in 1.6. `optional=true` because it's a compile-time tool, not something consumers of this artifact need. |
| `spring-boot-starter-data-jpa-test` / `spring-boot-starter-webmvc-test` (`test`) | Testing utilities (in-memory test support, MockMvc, etc.), only present when running tests. |

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
```
This plugin is what lets `mvn spring-boot:run` start the app, and what packages the
final build into a single runnable "fat jar" (all dependencies bundled in) via
`mvn package`.

```xml
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
                <execution>
                    <id>default-compile</id>
                    ...
                    <annotationProcessorPaths>
                        <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
                    </annotationProcessorPaths>
                </execution>
                <execution><id>default-testCompile</id> ... (same, for test sources) </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
This explicitly tells the Java compiler to run Lombok's annotation processor during
both the main compile step and the test compile step — this is what actually
*generates* the getters/setters/builders described in 1.6. Without this wiring,
`@Getter` etc. would just be inert metadata.

---

### 2.2 `src/main/resources/application.properties`

Spring Boot loads this file at startup and injects the values wherever
`${property.name}` placeholders appear in code (see the `@FeignClient(url = ...)`
usages).

```properties
spring.application.name=ecommerce-platform
```
Names the app (shows up in logs, and would matter for service discovery/metrics in
a bigger deployment).

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerceproject
spring.datasource.username=postgres
spring.datasource.password=postgres
```
The JDBC connection string, username, and password for the PostgreSQL database this
service reads/writes. `localhost:5432` is Postgres's default host/port. You need a
real Postgres server running with a database named `ecommerceproject` for this app
to start successfully.

```properties
spring.jpa.hibernate.ddl-auto=update
```
Tells Hibernate to automatically create/alter database tables to match your
`@Entity` classes on startup. Convenient in development (you never hand-write
`CREATE TABLE` statements), but **dangerous in production** — `update` can silently
apply unreviewed schema changes; real production systems typically use a migration
tool (Flyway/Liquibase) and set this to `validate` or `none` instead.

```properties
# --- Downstream service clients (Product/Merchant not up yet: calls fall back until pointed at real instances) ---
clients.product-service.url=http://localhost:8082
clients.merchant-service.url=http://localhost:8083
clients.order-service.url=http://localhost:8084
```
Base URLs for the three Feign clients described in 1.11. The comment is an explicit
admission that these are placeholder ports — nothing is actually listening there
yet, which is exactly why the fallback classes exist and will be triggered whenever
those calls are attempted.

```properties
spring.cloud.openfeign.circuitbreaker.enabled=true
```
Turns on the integration between Feign and the circuit breaker, which is what makes
the `fallback = XyzFallback.class` attribute on `@FeignClient` actually get invoked.
Without this line, a failed Feign call would just throw a raw network exception
instead of routing to the fallback bean.

```properties
feign.client.config.default.connectTimeout=2000
feign.client.config.default.readTimeout=2000
```
Applies a default 2-second timeout (connect and read) to **every** Feign client
unless overridden per-client. This prevents a slow/hanging downstream service from
blocking a Cart Service request indefinitely — after 2s it gives up and (with the
circuit breaker enabled) routes to the fallback.

---

### 2.3 `EcommercePlatformApplication.java` — the entry point

```java
package com.example.ecommerceplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class EcommercePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommercePlatformApplication.class, args);
    }
}
```
- `@SpringBootApplication` is a shortcut for three annotations at once:
  `@Configuration` (this class can define beans), `@EnableAutoConfiguration`
  (Spring Boot should guess and wire up sensible defaults based on the
  dependencies on the classpath — e.g. "I see `spring-boot-starter-webmvc`, so
  start an embedded Tomcat"), and `@ComponentScan` (scan this package and all
  sub-packages for classes annotated `@Component`, `@Service`, `@Repository`,
  `@RestController`, etc., and register them as beans automatically). This is why
  every class in `com.example.ecommerceplatform.CartService.*` gets picked up
  without any manual registration.
- `@EnableFeignClients` specifically tells Spring to also scan for interfaces
  annotated `@FeignClient` and generate real HTTP-calling implementations for them.
- `main` is the plain Java entry point (`public static void main(String[] args)`
  is what the JVM looks for to start any program). `SpringApplication.run(...)`
  bootstraps the entire Spring container: reads `application.properties`, creates
  all beans in dependency order, starts the embedded web server, and blocks,
  serving requests until the process is killed.

---

### 2.4 Entity layer — `CartService/Entity/`

These three classes are the database schema, expressed as Java. Whatever fields
exist here (with `ddl-auto=update`) become actual Postgres columns.

#### `CartStatus.java`
```java
public enum CartStatus {
    ACTIVE,
    CHECKED_OUT,
    ABANDONED,
    EXPIRED
}
```
A plain Java `enum` — a fixed, closed set of named constants (like a Python `Enum`).
It models a cart's lifecycle. Only `ACTIVE` and `CHECKED_OUT` are actually assigned
anywhere in the current code; `ABANDONED`/`EXPIRED` are modeled for the future
(e.g. a scheduled cleanup job that marks old untouched carts as `ABANDONED`), but no
such job exists yet in this codebase.

#### `Carts.java`
```java
@Entity
@Table(name = "carts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Carts {
```
- `@Entity` — marks this class as JPA-managed; Hibernate will map it to a table.
- `@Table(name = "carts")` — explicitly names the table `carts` (otherwise
  Hibernate would derive a name from the class name).
- The Lombok annotations are exactly as described in 1.6.

```java
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cart_id")
    private UUID cartId;
```
- `@Id` — marks this field as the table's primary key.
- `@GeneratedValue(strategy = GenerationType.UUID)` — Hibernate auto-generates a
  random UUID for this field when a new row is inserted; you never set it yourself
  (see 1.13 for why UUIDs).
- `@Column(name = "cart_id")` — maps this Java field to a column literally named
  `cart_id` (Java conventionally uses `camelCase`; SQL conventionally uses
  `snake_case` — this annotation bridges the naming styles).

```java
    @Column(name = "user_id")
    private UUID userId;
```
Which user this cart belongs to. Note there's no foreign-key annotation here — this
service doesn't have (and doesn't need) a `Users` entity; it just stores the raw ID
of a user that presumably lives in some other user-management service.

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status = CartStatus.ACTIVE;
```
- `@Enumerated(EnumType.STRING)` — stores the enum as its **name** in the database
  (`"ACTIVE"`, `"CHECKED_OUT"`, ...) rather than its ordinal position (`0`, `1`, ...).
  This is the safer choice: if you ever reorder the enum constants, ordinal storage
  would silently corrupt existing data, whereas string storage is immune to that.
- `nullable = false` — the database column gets a `NOT NULL` constraint.
- `= CartStatus.ACTIVE` — a plain Java field initializer; every newly-constructed
  `Carts` object defaults to `ACTIVE` status before it's even saved.

```java
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```
`createdAt`/`updatedAt` are timestamp audit columns. `@PrePersist` and `@PreUpdate`
are **JPA lifecycle callbacks** — Hibernate automatically invokes `onCreate()`
right before the *first* `INSERT` of this entity, and `onUpdate()` right before any
subsequent `UPDATE`. This guarantees these timestamps are always accurate without
any calling code having to remember to set them manually.

#### `CartItems.java`
```java
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"cart_id", "product_id", "variant_id", "merchant_id"}
                )
        }
)
```
Each row is one line item in a cart. The `uniqueConstraints` clause tells the
database: **no two rows can share the same combination** of `(cart_id, product_id,
variant_id, merchant_id)`. This is the schema-level enforcement of "a cart can only
have one line per distinct product+variant+merchant" — it's *why* the service layer
(`CartServiceImpl.addItemToCart`) looks for an existing matching line and *updates
its quantity* instead of blindly inserting a new row (which would violate this
constraint and throw a database error).

```java
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
```
Same pattern as `Carts.cartId` — an auto-generated UUID primary key for this row,
here just called `id` (and no `@Column(name=...)` override, so the column is
literally named `id`).

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Carts cart;
```
This is the actual **foreign key relationship**: many `CartItems` rows can belong to
one `Carts` row (`@ManyToOne`). `@JoinColumn(name = "cart_id")` is the physical
foreign-key column in `cart_items` pointing back at `carts.cart_id`.
`fetch = FetchType.LAZY` means: when Hibernate loads a `CartItems` row, it does
**not** eagerly load the full parent `Carts` object from the database — it only
loads it the first time code actually calls `item.getCart()`. This avoids
unnecessary joins/queries when you only need the item's own fields (an important
performance habit — the opposite, `FetchType.EAGER`, is JPA's default for
`@ManyToOne` and is a very common source of accidental N+1 query bugs).

```java
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
```
- `productId` — which product (required).
- `variantId` — an optional sub-selection of the product (e.g. "size M, color red");
  nullable because not every product has variants.
- `merchantId` — which seller/store is fulfilling this line (this is a
  multi-merchant marketplace, so the same product could theoretically be sold by
  different merchants at different prices).

```java
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
```
- `quantity` — how many units of this line.
- `unitPrice` — the price *at the time it was added/last priced*, stored as
  `BigDecimal` (see 1.12). `precision = 12, scale = 2` tells the database to store
  this as a fixed-point decimal with up to 12 total digits, 2 of them after the
  decimal point (e.g. up to `9999999999.99`) — this is the standard way to model
  currency columns in SQL, avoiding floating-point rounding entirely at the storage
  layer too.

```java
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        addedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```
Same audit-timestamp pattern as `Carts`.

---

### 2.5 Repository layer — `CartService/Repository/`

#### `CartRepository.java`
```java
public interface CartRepository extends JpaRepository<Carts, UUID> {

    Carts findByUserIdAndStatus(UUID userId, CartStatus status);

    List<Carts> findAllByUserId(UUID userId);
}
```
- `extends JpaRepository<Carts, UUID>` — instantly grants this interface (with zero
  written code) methods like `save(entity)`, `findById(id)`, `findAll()`,
  `delete(entity)`, `count()`, etc., all implemented by Spring Data at runtime,
  where `Carts` is the entity type and `UUID` is its primary-key type.
- `findByUserIdAndStatus(UUID, CartStatus)` — a **derived query method**. Spring
  Data parses the method name (`findBy` + `UserId` + `And` + `Status`) and generates
  the equivalent of `SELECT * FROM carts WHERE user_id = ? AND status = ?`. This is
  how the service layer finds "the current active cart for this user" — since a
  user could have multiple *historical* carts (old checked-out ones), this query
  narrows to exactly the one that matters, `ACTIVE`.
- `findAllByUserId(UUID)` — returns every cart (any status) ever created for a
  user; not currently used by the service layer but available for future features
  like "order history"/"past carts".

#### `CartItemRepository.java`
```java
public interface CartItemRepository extends JpaRepository<CartItems, UUID> {

    List<CartItems> findByCartCartId(UUID cartId);
```
`findByCartCartId` — note there's no underscore/camel hint here, so Spring Data
resolves it by walking the property path: `CartItems` has a `cart` property (a
`Carts` object), and `Carts` has a `cartId` property — so this generates
`SELECT * FROM cart_items WHERE cart_id = ?` by traversing the relationship. This
is how the service loads all line items belonging to one cart.

```java
    @Query("""
            SELECT ci FROM CartItems ci
            WHERE ci.cart.cartId = :cartId
              AND ci.productId = :productId
              AND ci.merchantId = :merchantId
              AND (:variantId IS NULL AND ci.variantId IS NULL OR ci.variantId = :variantId)
            """)
    Optional<CartItems> findMatchingLine(
            @Param("cartId") UUID cartId,
            @Param("productId") UUID productId,
            @Param("variantId") UUID variantId,
            @Param("merchantId") UUID merchantId
    );
```
This query can't be expressed by simple method-name parsing, so it's written
explicitly in **JPQL** (see 1.9 — note it says `FROM CartItems` and `ci.cart.cartId`,
i.e. entity/field names, not table/column names; Hibernate translates this to real
SQL). `@Param("cartId")` binds each named `:cartId`-style placeholder to a method
parameter, preventing SQL injection (never string-concatenate user input into a
query).

The tricky part is the `variantId` condition:
`(:variantId IS NULL AND ci.variantId IS NULL OR ci.variantId = :variantId)`.
In SQL, `NULL = NULL` evaluates to unknown/false, not true — so a naive
`ci.variantId = :variantId` would never match two rows that are *both* missing a
variant. This condition explicitly handles that case: either both sides are `NULL`
(no-variant product matching another no-variant line), or they're equal
non-`NULL` values. This is exactly the row this method needs to find: "does this
cart already have a line for this exact product + variant + merchant combination?"
— the same triple that the entity's unique constraint protects.
Returning `Optional<CartItems>` (instead of a raw, possibly-`null` `CartItems`)
forces callers to explicitly handle the "not found" case (`.orElse(null)` /
`.orElseThrow(...)`) rather than risking a silent `NullPointerException`.

```java
    long countByCartCartId(UUID cartId);

    void deleteByCartCartId(UUID cartId);
}
```
- `countByCartCartId` — derived query generating `SELECT COUNT(*) FROM cart_items
  WHERE cart_id = ?`. (Not currently called anywhere in the service — the service
  computes counts itself from an already-loaded list instead — but it's available.)
- `deleteByCartCartId` — a derived **delete** query, used by `clearCart` in the
  service to remove every line item for a cart in one operation.

---

### 2.6 DTO layer — `CartService/Dto/`

All classes here are pure data holders (see 1.5–1.6). None contain business logic.

#### `AddCartItemRequestDto.java` — body of `POST /api/cart/{userId}/items`
```java
@NotNull(message = "productId is required")
private UUID productId;

private UUID variantId;

@NotNull(message = "merchantId is required")
private UUID merchantId;

@NotNull(message = "quantity is required")
@Min(value = 1, message = "quantity must be at least 1")
private Integer quantity;
```
`productId` and `merchantId` are mandatory (client must always say *which* product
from *which* merchant). `variantId` is intentionally left unconstrained/optional —
not every product has variants. `quantity` must be present and at least `1` — you
can't "add zero" or a negative amount of an item; that's what the *patch* endpoint
is for. The `message = "..."` text is exactly what ends up in the `400` error body
if validation fails (see `CartExceptionHandler.handleValidation`).

#### `CartItemCountResponseDto.java` — body of `GET /api/cart/{userId}/count`
```java
private UUID cartId;
private UUID userId;
private long distinctItemCount;
private long totalQuantity;
```
`distinctItemCount` = number of distinct line items (rows); `totalQuantity` = sum of
all `quantity` values across those rows. E.g. 2 units of Shirt A + 3 units of Shirt B
→ `distinctItemCount = 2`, `totalQuantity = 5`. A typical UI cart-icon badge uses one
or the other depending on design.

#### `CartItemResponseDto.java` — one line item, as returned to a client
```java
private UUID cartItemId;
private UUID productId;
private UUID variantId;
private UUID merchantId;

private String productName;
private String productImage;
private boolean available;

private Integer quantity;
private BigDecimal unitPrice;
private BigDecimal lineTotal;
```
The first four fields are IDs copied straight from the entity. `productName`,
`productImage`, `available` are **not stored in this service's database at all** —
they're fetched live from Product Service at read time (see
`CartServiceImpl.toCartItemResponse`), which is why this is a DTO and not the
entity itself: the entity has no such columns. `lineTotal` is a computed value
(`unitPrice * quantity`), also not a stored column.

#### `CartResponseDto.java` — the whole-cart payload most endpoints return
```java
private UUID cartId;
private UUID userId;
private CartStatus status;
private List<CartItemResponseDto> items;
private Integer totalItemCount;
private BigDecimal totalPrice;
```
A cart's identity, its status, its list of items (as DTOs, not entities), and two
aggregates computed by the service layer (`totalItemCount` = number of distinct
lines, `totalPrice` = sum of every line's `lineTotal`).

#### `CheckoutResponseDto.java` — body returned by `POST /api/cart/{userId}/checkout`
```java
private UUID orderId;
private UUID cartId;
private UUID userId;
private Integer itemCount;
private BigDecimal totalPrice;
```
`orderId` is the identifier handed back by Order Service after checkout succeeds
(everything else summarizes what was purchased).

#### `ErrorResponseDto.java` — the standard shape of every error response
```java
private LocalDateTime timestamp;
private int status;
private String error;
private String message;
private String path;
```
Every error this API returns (see `CartExceptionHandler`) has this exact shape:
when it happened, the numeric HTTP status, the status's text reason (`"Not
Found"`), a human-readable message, and which URL path was hit. This consistency
means any client code can parse errors from this API uniformly, regardless of which
specific exception caused them.

#### `PatchCartItemQuantityRequestDto.java` — body of `PATCH /api/cart/{userId}/items/{cartItemId}`
```java
@NotNull(message = "delta is required")
private Integer delta;
```
Only one field: `delta`, a signed integer *change* to apply to the current
quantity — e.g. `+1` to increment, `-2` to decrement by two. This is a deliberate
API design choice over sending an absolute new quantity: the client (e.g. a "+"/"-"
button in a UI) naturally thinks in relative steps, and the server is the sole
source of truth for "what is the quantity now", so relative adjustment avoids the
client needing to already know (and stay in sync with) the current value before
sending a request. (Design note for later: this still isn't safe against two
concurrent requests racing on the *same* item unless a database-level lock or
optimistic-locking version column is added — worth knowing as a caveat, not a bug
that's been "fixed" here.)

#### `Dto/request/CreateOrderRequest.java` and `OrderLineItemRequest.java`
```java
// CreateOrderRequest
private UUID cartId;
private UUID userId;
private List<OrderLineItemRequest> items;
private BigDecimal totalPrice;

// OrderLineItemRequest
private UUID productId;
private UUID variantId;
private UUID merchantId;
private Integer quantity;
private BigDecimal unitPrice;
private BigDecimal lineTotal;
```
These live in a `request` sub-package because they're **outbound** — this is the
JSON body Cart Service *sends* to Order Service's (not-yet-built)
`POST /api/orders` endpoint at checkout. They're separate classes from
`CartResponseDto`/`CartItemResponseDto` on purpose: this is the contract between
*two specific services*, and it may need to diverge from what Cart Service shows
its own clients (e.g. Order Service might not care about `productName`/`available`,
which are UI-display-only fields).

#### `Dto/response/OrderCreatedResponse.java`, `ProductDetailsResponse.java`, `StockPriceResponse.java`
```java
// OrderCreatedResponse — what Order Service is expected to send back
private UUID orderId;
private String status;
private BigDecimal totalPrice;

// ProductDetailsResponse — what Product Service is expected to send back
private UUID productId;
private UUID variantId;
private String name;
private String imageUrl;
private boolean available;

// StockPriceResponse — what Merchant Service is expected to send back
private UUID productId;
private UUID variantId;
private UUID merchantId;
private BigDecimal price;
private Integer availableStock;
private boolean inStock;
```
These are **inbound** deserialization targets — the exact JSON shape Cart Service
*expects* from each of the three other (currently unimplemented) services. They
double as living documentation of the contract Cart Service is assuming; if the
real Product/Merchant/Order Service teams build endpoints returning a different
shape, these classes (and the Feign interfaces using them) are what would need
updating.

---

### 2.7 Exception layer — `CartService/Exception/`

#### The five domain exceptions
Each is a small `RuntimeException` subclass whose constructor builds a descriptive
message from context, so the message text lives in one place (the exception class)
instead of being duplicated at every `throw` site.

```java
public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(UUID userId) {
        super("No active cart found for userId: " + userId);
    }
}
```
Thrown when an operation requires an existing `ACTIVE` cart (patch/remove/clear/
checkout) but none exists for that user.

```java
public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(UUID cartItemId) {
        super("Cart item not found: " + cartItemId);
    }
}
```
Thrown when a specific line item ID doesn't exist, *or* exists but belongs to a
different cart than the one being operated on (an ownership check — see 2.9).

```java
public class EmptyCartException extends RuntimeException {
    public EmptyCartException(UUID userId) {
        super("Cannot checkout: cart for userId " + userId + " has no items");
    }
}
```
Thrown at checkout time if the cart has zero line items — you can't check out
nothing.

```java
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(UUID productId) {
        super("Product " + productId + " is not available");
    }
}
```
Thrown when Product Service reports `available = false` for a product being
added/re-validated (e.g. the listing was discontinued).

```java
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID productId, int requestedQuantity, int availableStock) {
        super("Insufficient stock for product " + productId + ": requested " + requestedQuantity
                + " but only " + availableStock + " available");
    }
}
```
Thrown when Merchant Service reports either `inStock = false` or not enough units
available to satisfy the requested quantity. Carries both numbers in the message so
the caller/UI can explain exactly why.

```java
public class DownstreamServiceUnavailableException extends RuntimeException {
    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " is unavailable, please try again later", cause);
    }
}
```
Thrown by every Feign **fallback** class (2.8) when the real network call couldn't
be completed. `super(message, cause)` uses Java's built-in **exception chaining** —
it passes through the original underlying exception (e.g. a
`java.net.ConnectException`) as the `cause`, so nothing about the original failure
is lost even though it's now being reported as this more meaningful, typed error
(useful for debugging via stack traces/logs, even though `null` is passed as the
cause everywhere it's currently thrown in this codebase, since Feign's own fallback
mechanism doesn't hand the fallback the original exception by default).

#### `CartExceptionHandler.java` — the global error translator
```java
@RestControllerAdvice(basePackages = "com.example.ecommerceplatform.CartService")
public class CartExceptionHandler {
```
As explained in 1.10, this class intercepts exceptions thrown from controllers.
`basePackages = "com.example.ecommerceplatform.CartService"` scopes it to only
this feature's controllers — deliberate, since this codebase's package structure
suggests it's meant to eventually host multiple services/features side by side
(each with its own advice), not just this one.

```java
    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleCartNotFound(CartNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }
```
Each handler method follows the identical shape: catch one exception type, delegate
to the shared `build(...)` helper with the right HTTP status code. The full mapping:

| Exception | HTTP Status | Meaning |
|---|---|---|
| `CartNotFoundException` | 404 Not Found | No such cart exists |
| `CartItemNotFoundException` | 404 Not Found | No such item exists (or not yours) |
| `ProductUnavailableException` | 409 Conflict | Request conflicts with product's current state |
| `InsufficientStockException` | 409 Conflict | Request conflicts with current stock levels |
| `EmptyCartException` | 400 Bad Request | The request itself is invalid given cart's current (empty) state |
| `DownstreamServiceUnavailableException` | 503 Service Unavailable | A dependency Cart Service needs is down |
| `MethodArgumentNotValidException` | 400 Bad Request | The request body failed `@Valid` field constraints |

`HttpServletRequest request` is injected by Spring into any `@ExceptionHandler`
method that declares it as a parameter — it gives access to the raw incoming HTTP
request (used here only to read `request.getRequestURI()` for the error body's
`path` field).

```java
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }
```
When `@Valid` fails (1.8), Spring throws `MethodArgumentNotValidException`, which
carries a `BindingResult` listing every failed field constraint. This code:
`getFieldErrors()` → a list of individual failures → `.stream().map(...)` builds a
string like `"fieldName: message"` for each → `.collect(Collectors.joining(", "))`
joins them all with commas into one combined message, e.g.
`"quantity: quantity must be at least 1, merchantId: merchantId is required"`. (This
is a Java **Stream** pipeline — conceptually equivalent to a Python
`", ".join(f"{f.field}: {f.message}" for f in errors)`.)

```java
    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponseDto body = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
```
The shared helper: builds an `ErrorResponseDto` via Lombok's builder (1.6), filling
in the current time, the numeric status (`status.value()`, e.g. `404`), its English
phrase (`status.getReasonPhrase()`, e.g. `"Not Found"`), the specific message, and
the request path — then wraps it in a `ResponseEntity` set to that same status code.
This is the single place that guarantees every error this API returns has an
identical, predictable JSON shape.

---

### 2.8 Client layer — `CartService/Client/`

Each of the three downstream services gets exactly two files: the Feign interface
(the "real" call) and a fallback (`@Component` implementing the same interface,
used automatically when the real call fails — see 1.11).

#### `ProductServiceClient.java` / `ProductServiceClientFallback.java`
```java
@FeignClient(
        name = "product-service",
        url = "${clients.product-service.url}",
        fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @GetMapping("/api/products/{productId}")
    ProductDetailsResponse getProductDetails(
            @PathVariable("productId") UUID productId,
            @RequestParam(value = "variantId", required = false) UUID variantId
    );
}
```
- `name = "product-service"` — a logical identifier Resilience4j/Feign use
  internally (for the circuit breaker's name/metrics), independent of the URL.
- `url = "${clients.product-service.url}"` — resolves to
  `http://localhost:8082` from `application.properties`. Because a fixed URL is
  given directly (rather than a service-discovery name resolved via something like
  Eureka/Consul), Feign calls that address directly — appropriate for a project
  with just one instance of each service and no discovery infrastructure yet.
- `getProductDetails(...)` — calling this method fires `GET
  /api/products/{productId}?variantId=...` (only appended if non-null, because
  `required = false`) and deserializes the JSON response body into
  `ProductDetailsResponse`.
```java
@Component
public class ProductServiceClientFallback implements ProductServiceClient {
    @Override
    public ProductDetailsResponse getProductDetails(UUID productId, UUID variantId) {
        throw new DownstreamServiceUnavailableException("Product Service", null);
    }
}
```
`@Component` registers this as a normal Spring bean; Feign/Resilience4j find it by
matching it against the `fallback = ...` reference and invoke it whenever the real
HTTP call to Product Service fails or times out. Rather than returning a fake/empty
`ProductDetailsResponse` (which could be silently misleading — e.g. pretending a
product is unavailable when really the service is just down), it deliberately
**throws**, escalating the failure into a clear, typed error the rest of the app
already knows how to turn into a `503` (2.7).

#### `MerchantServiceClient.java` / `MerchantServiceClientFallback.java`
```java
@GetMapping("/api/merchants/{merchantId}/products/{productId}/stock")
StockPriceResponse getStockAndPrice(
        @PathVariable("merchantId") UUID merchantId,
        @PathVariable("productId") UUID productId,
        @RequestParam(value = "variantId", required = false) UUID variantId
);
```
Same pattern, asking a specific merchant for the current stock level and price of a
specific product(+variant). This is the *authoritative, real-time* source for price
and availability — the service layer never trusts a stale price stored in
`cart_items.unit_price` for stock/availability decisions; it always re-asks this
client (see 2.9). The fallback again just throws
`DownstreamServiceUnavailableException("Merchant Service", null)`.

#### `OrderServiceClient.java` / `OrderServiceClientFallback.java`
```java
@PostMapping("/api/orders")
OrderCreatedResponse createOrder(@RequestBody CreateOrderRequest request);
```
A `POST` (not `GET`) since it's *creating* something (an order) — `@RequestBody`
means the `CreateOrderRequest` object is serialized to JSON and sent as the HTTP
request body. This is the very last external call `checkout()` makes; its fallback
likewise throws `DownstreamServiceUnavailableException("Order Service", null)`.

---

### 2.9 Service layer — `CartService/Service/`

#### `CartService.java` — the interface (the contract)
```java
public interface CartService {
    CartResponseDto getCart(UUID userId);
    CartResponseDto addItemToCart(UUID userId, AddCartItemRequestDto request);
    CartResponseDto patchItemQuantity(UUID userId, UUID cartItemId, PatchCartItemQuantityRequestDto request);
    CartResponseDto removeItem(UUID userId, UUID cartItemId);
    void clearCart(UUID userId);
    CartItemCountResponseDto getItemCount(UUID userId);
    CheckoutResponseDto checkout(UUID userId);
}
```
This defines *what* the cart feature can do without saying *how*. The controller
depends only on this interface (not on `CartServiceImpl` directly) — this is the
**Dependency Inversion Principle**: high-level code (the controller) depends on an
abstraction, and the concrete implementation can be swapped (e.g. for a test double)
without touching the controller at all.

#### `CartServiceImpl.java` — where all the business logic actually lives

```java
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;
    private final MerchantServiceClient merchantServiceClient;
    private final OrderServiceClient orderServiceClient;
```
`@Service` marks this as a Spring-managed bean specifically in the "service" layer
(functionally identical to `@Component`, but the distinct name documents intent and
is what Spring auto-detects via `@ComponentScan`). `@RequiredArgsConstructor`
(Lombok, 1.6) generates a constructor taking all five `final` fields — Spring calls
this constructor automatically, injecting each already-built bean
(the repositories and the three Feign client proxies). Constructor injection (over
field injection with `@Autowired` on each field) is preferred because: the class is
impossible to construct in an incomplete state (all dependencies are mandatory,
enforced by the compiler), the fields can be `final` (guaranteeing they're never
reassigned after construction), and it makes unit testing trivial (you can call
`new CartServiceImpl(mockRepo, mockRepo2, ...)` directly without needing a full
Spring context).

**`getCart`**
```java
    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            return emptyCartResponse(userId);
        }
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }
```
Read-only transaction (1.14). Look up the user's active cart; if there isn't one
yet, **don't throw an error** — a brand-new user browsing "my cart" for the first
time should just see an empty cart, not a 404. Otherwise, load its items and map
everything to a `CartResponseDto` (2.6) via the private `toCartResponse` helper.

**`addItemToCart`** — the most involved method; walked through step by step:
```java
    @Override
    @Transactional
    public CartResponseDto addItemToCart(UUID userId, AddCartItemRequestDto request) {
        Carts cart = getOrCreateActiveCart(userId);
```
A writing transaction this time (any exception thrown later rolls back everything).
`getOrCreateActiveCart` (a private helper, see below) either finds the existing
`ACTIVE` cart or **lazily creates one** — a cart row doesn't need to exist until the
first item is added.

```java
        ProductDetailsResponse product = productServiceClient.getProductDetails(
                request.getProductId(), request.getVariantId());
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(request.getProductId());
        }
```
Calls out to Product Service (a real network call, or the fallback if it's down —
which, per current config, it always is right now, so this line currently always
throws `DownstreamServiceUnavailableException` in practice until Product Service is
actually stood up). If the product itself is marked unavailable (e.g.
discontinued), reject the add.

```java
        CartItems existingLine = cartItemRepository.findMatchingLine(
                cart.getCartId(), request.getProductId(), request.getVariantId(), request.getMerchantId()
        ).orElse(null);

        int alreadyInCart = existingLine == null ? 0 : existingLine.getQuantity();
        int requestedTotalQuantity = alreadyInCart + request.getQuantity();
```
Check whether this exact product+variant+merchant is already a line in this cart
(2.5's `findMatchingLine`). If yes, the *new total* quantity would be however many
are already there plus the newly requested amount — this matters because the stock
check right below needs to validate against the **combined** quantity, not just the
newly-requested delta (you can't add 3 more of something if you already have 8 and
only 10 are in stock, even though "3" alone looks fine).

```java
        StockPriceResponse stock = merchantServiceClient.getStockAndPrice(
                request.getMerchantId(), request.getProductId(), request.getVariantId());
        if (!stock.isInStock() || stock.getAvailableStock() < requestedTotalQuantity) {
            int available = stock.getAvailableStock() == null ? 0 : stock.getAvailableStock();
            throw new InsufficientStockException(request.getProductId(), requestedTotalQuantity, available);
        }
```
Ask Merchant Service for the *live*, authoritative stock level and price (never
trust anything cached locally for this decision). Reject if the merchant reports
out of stock, or reports fewer units available than the cart would need in total.
The `available = ... == null ? 0 : ...` guard defends against a merchant response
that omits `availableStock` (`null`) — treats "unknown" as "zero available" rather
than risking a `NullPointerException` on the `<` comparison above it (note the
comparison line itself, `stock.getAvailableStock() < requestedTotalQuantity`, would
actually already NPE first if `availableStock` were null, since Java auto-unboxes
the `Integer` to compare it — this fallback variable is really just for building
the exception message safely; worth noticing as a subtlety, not something to "fix"
unprompted).

```java
        if (existingLine != null) {
            existingLine.setQuantity(requestedTotalQuantity);
            existingLine.setUnitPrice(stock.getPrice());
            cartItemRepository.save(existingLine);
        } else {
            CartItems newLine = CartItems.builder()
                    .cart(cart)
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .unitPrice(stock.getPrice())
                    .build();
            cartItemRepository.save(newLine);
        }
```
If a matching line already existed, bump its quantity to the new combined total and
refresh its stored price to the latest merchant price, then `save()` (which, for
JPA, performs an `UPDATE` since the entity already has an ID). Otherwise, build a
brand-new `CartItems` row via Lombok's builder (1.6) and `save()` it (an `INSERT`
this time, since it has no ID yet).

```java
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }
```
Reload the full, current list of items for this cart and map to the response DTO —
so the client always gets back the complete, up-to-date cart state after any
mutation, not just the one line that changed.

**`patchItemQuantity`**
```java
    @Override
    @Transactional
    public CartResponseDto patchItemQuantity(UUID userId, UUID cartItemId, PatchCartItemQuantityRequestDto request) {
        Carts cart = requireActiveCart(userId);

        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }
```
Unlike `getCart`, this uses `requireActiveCart` (2.9 helper below), which **throws**
`CartNotFoundException` if there's no active cart — you can't patch an item in a
cart that doesn't exist. `findById(cartItemId).orElseThrow(...)` looks the item up
by its own primary key across the *whole table* (not scoped to this cart), so the
very next check —
`if (!item.getCart().getCartId().equals(cart.getCartId()))` — is a deliberate
**ownership/authorization check**: it confirms the item actually belongs to *this
user's* cart. Without it, any authenticated user could guess/pass another user's
`cartItemId` in the URL and modify someone else's cart (an
"Insecure Direct Object Reference" vulnerability). Both the "doesn't exist" and
"exists but isn't yours" cases deliberately return the exact same
`CartItemNotFoundException` (404) rather than a different error — this avoids
leaking to an attacker whether a given ID exists at all in the system.

```java
        int delta = request.getDelta();
        int newQuantity = item.getQuantity() + delta;

        if (newQuantity <= 0) {
            cartItemRepository.delete(item);
        } else if (delta > 0) {
            ProductDetailsResponse product = productServiceClient.getProductDetails(
                    item.getProductId(), item.getVariantId());
            if (!product.isAvailable()) {
                throw new ProductUnavailableException(item.getProductId());
            }

            StockPriceResponse stock = merchantServiceClient.getStockAndPrice(
                    item.getMerchantId(), item.getProductId(), item.getVariantId());
            if (!stock.isInStock() || stock.getAvailableStock() < newQuantity) {
                int available = stock.getAvailableStock() == null ? 0 : stock.getAvailableStock();
                throw new InsufficientStockException(item.getProductId(), newQuantity, available);
            }

            item.setQuantity(newQuantity);
            item.setUnitPrice(stock.getPrice());
            cartItemRepository.save(item);
        } else {
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        }
```
Three branches, based on the sign of the resulting quantity/delta:
1. **`newQuantity <= 0`** — the adjustment brings quantity to zero or below (e.g.
   `delta = -5` on a quantity of `3`): interpret that as "remove the line entirely"
   and `delete()` it — a natural, forgiving UX rather than erroring on "negative
   quantity."
2. **`delta > 0`** (increasing) — re-runs the *exact same* availability + stock
   validation as `addItemToCart`, since increasing quantity can newly violate stock
   limits that weren't a problem before, and re-fetches the current price.
3. **`else` (`delta < 0` but `newQuantity` still positive)** — just updates the
   number directly, **skipping** the two network calls entirely. The reasoning:
   decreasing a quantity can never cause an insufficient-stock problem (you're
   asking for *less*, not more), so there's no need to pay the cost (and risk) of
   two extra outbound HTTP calls for a change that can't fail that check anyway.
   Note it also does **not** refresh `unitPrice` in this branch — the price is only
   ever refreshed when a quantity increase or new-add path recomputes it, or at
   final checkout.

**`removeItem`**
```java
    @Override
    @Transactional
    public CartResponseDto removeItem(UUID userId, UUID cartItemId) {
        Carts cart = requireActiveCart(userId);
        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }
        cartItemRepository.delete(item);
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }
```
Same ownership-checked lookup pattern as `patchItemQuantity`, followed by an
unconditional delete and a fresh reload of the (now-shorter) cart.

**`clearCart`**
```java
    @Override
    @Transactional
    public void clearCart(UUID userId) {
        Carts cart = requireActiveCart(userId);
        cartItemRepository.deleteByCartCartId(cart.getCartId());
    }
```
Removes every line item for the cart in one bulk delete (2.5). Returns `void` — the
controller responds `204 No Content` (there's nothing meaningful left to show).

**`getItemCount`**
```java
    @Override
    @Transactional(readOnly = true)
    public CartItemCountResponseDto getItemCount(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            return CartItemCountResponseDto.builder()
                    .userId(userId)
                    .distinctItemCount(0)
                    .totalQuantity(0)
                    .build();
        }
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        long totalQuantity = items.stream().mapToLong(CartItems::getQuantity).sum();

        return CartItemCountResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .distinctItemCount(items.size())
                .totalQuantity(totalQuantity)
                .build();
    }
```
Same "no cart yet ⇒ zero, not an error" philosophy as `getCart`. A lightweight
endpoint intended for something like a frequently-polled cart badge in a UI header,
so it deliberately avoids the heavier per-item Product Service enrichment that
`getCart`/`toCartItemResponse` does. `items.stream().mapToLong(CartItems::getQuantity).sum()`
is a Java Stream pipeline: convert the list of items into a stream of their
`quantity` values (`mapToLong` + a **method reference** `CartItems::getQuantity`,
shorthand for `item -> item.getQuantity()`), then `.sum()` them — equivalent to
Python's `sum(item.quantity for item in items)`.

**`checkout`**
```java
    @Override
    @Transactional
    public CheckoutResponseDto checkout(UUID userId) {
        Carts cart = requireActiveCart(userId);

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        if (items.isEmpty()) {
            throw new EmptyCartException(userId);
        }
```
Must have an active cart, and it must have at least one item — otherwise reject
up-front before doing any of the (more expensive) per-item work below.

```java
        List<OrderLineItemRequest> orderLines = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (CartItems item : items) {
            ProductDetailsResponse product = productServiceClient.getProductDetails(
                    item.getProductId(), item.getVariantId());
            if (!product.isAvailable()) {
                throw new ProductUnavailableException(item.getProductId());
            }

            StockPriceResponse stock = merchantServiceClient.getStockAndPrice(
                    item.getMerchantId(), item.getProductId(), item.getVariantId());
            if (!stock.isInStock() || stock.getAvailableStock() < item.getQuantity()) {
                int available = stock.getAvailableStock() == null ? 0 : stock.getAvailableStock();
                throw new InsufficientStockException(item.getProductId(), item.getQuantity(), available);
            }

            // Re-priced at checkout time in case the merchant changed price since it was added to the cart.
            item.setUnitPrice(stock.getPrice());
            cartItemRepository.save(item);

            BigDecimal lineTotal = stock.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalPrice = totalPrice.add(lineTotal);

            orderLines.add(OrderLineItemRequest.builder()
                    .productId(item.getProductId())
                    .variantId(item.getVariantId())
                    .merchantId(item.getMerchantId())
                    .quantity(item.getQuantity())
                    .unitPrice(stock.getPrice())
                    .lineTotal(lineTotal)
                    .build());
        }
```
This is the **final, authoritative re-check** — every line item gets re-validated
against Product Service and Merchant Service *right before purchase*, even if it
was already checked when originally added. This matters because time has passed:
another customer could have bought the last units, or the merchant could have
changed the price, since this item sat in the cart. The comment in the code makes
this explicit: `item.setUnitPrice(stock.getPrice())` re-prices using the freshest
number available, and that fresh price (not whatever was stored before) is what's
used to compute `lineTotal` and accumulated into `totalPrice`. Using
`BigDecimal.ZERO` as the starting accumulator and `.add(...)`/`.multiply(...)`
(rather than `+=`/`*`) is the `BigDecimal` arithmetic pattern from 1.12. Each
line also gets converted into an `OrderLineItemRequest` (2.6) to eventually send to
Order Service.

```java
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .items(orderLines)
                .totalPrice(totalPrice)
                .build();

        OrderCreatedResponse orderResponse = orderServiceClient.createOrder(orderRequest);

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return CheckoutResponseDto.builder()
                .orderId(orderResponse.getOrderId())
                .cartId(cart.getCartId())
                .userId(userId)
                .itemCount(items.size())
                .totalPrice(totalPrice)
                .build();
    }
```
Bundle everything into one `CreateOrderRequest` and hand it to Order Service — Cart
Service does **not** create the actual order record itself; that's explicitly
another service's responsibility (separation of concerns across microservice
boundaries — Cart Service only owns "what's in the cart," not "what constitutes a
placed order"). Only *after* that call succeeds does this method flip the local
cart's `status` to `CHECKED_OUT` and save it — this ordering matters: if
`createOrder` throws (e.g. Order Service is down, triggering the fallback's
`DownstreamServiceUnavailableException`), the whole method's exception propagates
out, the `@Transactional` wrapper rolls back any DB writes made so far in this
method (including the re-pricing `save()` calls above), and the cart is left
untouched — `ACTIVE`, unchanged — so the user can simply retry checkout later
rather than being left in a broken, half-checked-out state. Marking status
`CHECKED_OUT` also means a subsequent `getCart`/`addItemToCart` call for this user
will no longer find this cart via `findByUserIdAndStatus(userId, ACTIVE)` — it will
transparently start a fresh one (see `getOrCreateActiveCart` below), which is
exactly the right behavior after a completed purchase.

**Private helper methods**
```java
    private Carts getOrCreateActiveCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart != null) {
            return cart;
        }
        Carts newCart = Carts.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();
        return cartRepository.save(newCart);
    }
```
"Find the active cart, or create+save+return a brand-new one" — used only by
`addItemToCart`, since that's the only operation that should ever cause a cart to
spring into existence.

```java
    private Carts requireActiveCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            throw new CartNotFoundException(userId);
        }
        return cart;
    }
```
"Find the active cart, or throw" — used by every mutating/reading operation
*except* `addItemToCart` (which creates one) and `getCart`/`getItemCount` (which
tolerate "no cart" as a valid, empty state rather than an error).

```java
    private CartResponseDto emptyCartResponse(UUID userId) {
        return CartResponseDto.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .items(List.of())
                .totalItemCount(0)
                .totalPrice(BigDecimal.ZERO)
                .build();
    }
```
Builds a synthetic "empty cart" DTO for a user who has no cart row in the database
at all yet — note `cartId` is left `null` here since no row/UUID actually exists.
`List.of()` is Java's idiom for an immutable empty list.

```java
    private CartResponseDto toCartResponse(Carts cart, List<CartItems> items) {
        List<CartItemResponseDto> itemDtos = items.stream()
                .map(this::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = itemDtos.stream()
                .map(CartItemResponseDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .items(itemDtos)
                .totalItemCount(itemDtos.size())
                .totalPrice(totalPrice)
                .build();
    }
```
The **entity-list → response-DTO** aggregate mapper, used by every method that
returns a full cart. `items.stream().map(this::toCartItemResponse).toList()` — for
every `CartItems` entity, call the other helper below to enrich+convert it into a
`CartItemResponseDto`, collecting the results into a new `List`. Then
`.reduce(BigDecimal.ZERO, BigDecimal::add)` sums every DTO's `lineTotal` starting
from zero — Java's `Stream.reduce(identity, accumulator)` is the general
"fold"/"aggregate" operation (equivalent to Python's `functools.reduce` or just
`sum(...)`), used here instead of a manual loop.

```java
    private CartItemResponseDto toCartItemResponse(CartItems item) {
        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        String productName = "Unavailable";
        String productImage = null;
        boolean available = false;
        try {
            ProductDetailsResponse product = productServiceClient.getProductDetails(
                    item.getProductId(), item.getVariantId());
            productName = product.getName();
            productImage = product.getImageUrl();
            available = product.isAvailable();
        } catch (DownstreamServiceUnavailableException ignored) {
            // Product Service is down/unreachable: show the cart with stored data, degrade display fields only.
        }

        return CartItemResponseDto.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantId(item.getMerchantId())
                .productName(productName)
                .productImage(productImage)
                .available(available)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(lineTotal)
                .build();
    }
```
The **single-entity → response-DTO** mapper, and the clearest example of
**graceful degradation** in this codebase. `lineTotal` is computed from the item's
*already-stored* `unitPrice` (not a fresh Merchant Service call — reading a cart is
not the moment to re-verify live pricing; that only happens at `checkout`).

The three display-only fields (`productName`, `productImage`, `available`) default
to placeholder values (`"Unavailable"`, `null`, `false`) *before* even attempting
the network call. Then it tries to enrich them with a live call to Product Service,
but wraps that call in `try { ... } catch (DownstreamServiceUnavailableException
ignored) { ... }`. If Product Service (or its fallback) throws — which today, with
nothing actually running at port 8082, it always does — this method **does not let
that exception propagate**. It swallows it and simply keeps the placeholder values.
This means `GET /api/cart/{userId}` still succeeds and returns the cart's real IDs,
quantities, and prices even while Product Service is completely unreachable — only
the cosmetic name/image/availability fields degrade. This is the practical payoff
of the whole circuit-breaker/fallback design: a failure in one dependency doesn't
have to cascade into a failure of the entire feature.

---

### 2.10 Controller layer — `CartService/Controller/CartController.java`

```java
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
```
As covered in 1.7 and 1.6/2.9: `@RestController` + `@RequestMapping("/api/cart")`
means every method below handles some `HTTP verb + /api/cart/...` combination and
returns JSON. `@RequiredArgsConstructor` injects the `CartService` **interface**
(not `CartServiceImpl` directly) — the controller has no idea, and doesn't need to
know, which implementation is actually wired in.

Every method in this class follows the same shape: extract inputs via
`@PathVariable`/`@RequestBody`, call exactly one `cartService.xyz(...)` method, wrap
the result in a `ResponseEntity` with the right status code. **There is no business
logic here at all** — that's intentional; the controller's only job is translating
between "HTTP request" and "a plain Java method call," and back.

```java
    @GetMapping("/{userId}")
    public ResponseEntity<CartResponseDto> getCart(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }
```
`GET /api/cart/{userId}` → `200 OK` with the cart (or a synthetic empty cart —
never a 404, per 2.9's `getCart`).

```java
    @PostMapping("/{userId}/items")
    public ResponseEntity<CartResponseDto> addItem(
            @PathVariable UUID userId,
            @Valid @RequestBody AddCartItemRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItemToCart(userId, request));
    }
```
`POST /api/cart/{userId}/items` → validates the body (1.8) → `201 Created` (the
REST convention for "a new resource was created," even though here it's really
"the cart now reflects this addition," possibly merged into an existing line) with
the full updated cart.

```java
    @PatchMapping("/{userId}/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> patchItemQuantity(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody PatchCartItemQuantityRequestDto request
    ) {
        return ResponseEntity.ok(cartService.patchItemQuantity(userId, cartItemId, request));
    }
```
`PATCH /api/cart/{userId}/items/{cartItemId}` → `200 OK` with the updated cart
(`PATCH` is the correct REST verb for a *partial* update, as opposed to `PUT`,
which implies replacing the whole resource — not used anywhere in this API).

```java
    @DeleteMapping("/{userId}/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> removeItem(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId
    ) {
        return ResponseEntity.ok(cartService.removeItem(userId, cartItemId));
    }
```
`DELETE /api/cart/{userId}/items/{cartItemId}` → `200 OK` with the resulting (now
smaller) cart.

```java
    @DeleteMapping("/{userId}/items")
    public ResponseEntity<Void> clearCart(@PathVariable UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
```
`DELETE /api/cart/{userId}/items` (note: no `{cartItemId}` — this is the "clear
everything" variant, distinguished from the one above purely by not having that
extra path segment) → `204 No Content`, meaning "it worked, and there is
deliberately no body to return" (`ResponseEntity<Void>` — the type parameter itself
says "this endpoint never has a body").

```java
    @GetMapping("/{userId}/count")
    public ResponseEntity<CartItemCountResponseDto> getItemCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.getItemCount(userId));
    }
```
`GET /api/cart/{userId}/count` → `200 OK` with just the counts (2.6/2.9).

```java
    @PostMapping("/{userId}/checkout")
    public ResponseEntity<CheckoutResponseDto> checkout(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.checkout(userId));
    }
}
```
`POST /api/cart/{userId}/checkout` → `200 OK` with the checkout summary (`POST`
because this triggers an action/side effect — placing an order — not just fetching
a resource).

---

### 2.11 Test — `src/test/java/.../EcommercePlatformApplicationTests.java`

```java
@SpringBootTest
class EcommercePlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}
```
This is a minimal but genuinely useful **smoke test**. `@SpringBootTest` tells the
test runner to boot the *entire* Spring application context — every bean, every
`@Configuration`, every property binding — exactly like starting the real app, just
without actually opening a listening port for external traffic (details depend on
web-environment settings, none of which are customized here). The test method body
is empty on purpose: `contextLoads` isn't testing any business logic — it's testing
that the wiring itself doesn't blow up. If a required bean is missing, a
circular dependency exists, or `application.properties` has a typo referenced by
`@Value`/`${...}`, this test fails immediately, before you'd otherwise discover it
by trying to actually run the app. There are currently no other tests in this
project (no controller tests, no service unit tests) — worth noting as a gap, not
something this document is asked to fix.

---

## Part 3 — API Reference (all 7 endpoints)

Base path: `/api/cart`

| # | Method | Path | Request Body | Success | Key Failure Responses |
|---|---|---|---|---|---|
| 1 | GET | `/{userId}` | — | `200` `CartResponseDto` | — |
| 2 | POST | `/{userId}/items` | `AddCartItemRequestDto` | `201` `CartResponseDto` | `400` invalid body · `409` product unavailable / insufficient stock · `503` downstream service down |
| 3 | PATCH | `/{userId}/items/{cartItemId}` | `PatchCartItemQuantityRequestDto` | `200` `CartResponseDto` | `400` invalid body · `404` no cart / no such item · `409` product unavailable / insufficient stock · `503` downstream service down |
| 4 | DELETE | `/{userId}/items/{cartItemId}` | — | `200` `CartResponseDto` | `404` no cart / no such item |
| 5 | DELETE | `/{userId}/items` | — | `204` (no body) | `404` no cart |
| 6 | GET | `/{userId}/count` | — | `200` `CartItemCountResponseDto` | — |
| 7 | POST | `/{userId}/checkout` | — | `200` `CheckoutResponseDto` | `400` empty cart · `404` no cart · `409` product unavailable / insufficient stock · `503` downstream service down |

Every error response (any non-2xx) has this identical shape (`ErrorResponseDto`):
```json
{
  "timestamp": "2026-08-24T10:15:30",
  "status": 404,
  "error": "Not Found",
  "message": "No active cart found for userId: ...",
  "path": "/api/cart/.../items/..."
}
```

---

## Part 4 — End-to-end trace: `POST /api/cart/{userId}/items`

Following one real request through every layer, to see how all the pieces
described above connect:

1. **HTTP arrives**: `POST /api/cart/3fa8.../items` with JSON body
   `{"productId": "...", "merchantId": "...", "quantity": 2}`.
2. **Spring MVC routing**: matches `CartController.addItem`. The JSON body is
   deserialized into `AddCartItemRequestDto`.
3. **Validation** (`@Valid`): `productId`/`merchantId`/`quantity` constraints are
   checked. If any fail → `MethodArgumentNotValidException` → intercepted by
   `CartExceptionHandler.handleValidation` → `400` response, controller code never
   runs.
4. **Controller** calls `cartService.addItemToCart(userId, request)`.
5. **Service, `@Transactional` begins**: a DB transaction opens.
   - `getOrCreateActiveCart` queries `CartRepository` (`SELECT ... WHERE
     user_id=? AND status='ACTIVE'`); if none, inserts a new `carts` row.
   - `productServiceClient.getProductDetails(...)` fires (or is short-circuited by
     the circuit breaker straight to) `ProductServiceClientFallback`, which throws
     `DownstreamServiceUnavailableException("Product Service", null)` (since
     nothing is really listening on port 8082 yet).
6. **Exception propagates** up through the service method, out of the
   `@Transactional` boundary (rolling back the earlier cart-creation `INSERT` if it
   happened), through the controller, and is caught by
   `CartExceptionHandler.handleDownstreamUnavailable`.
7. **Response**: `503 Service Unavailable` with an `ErrorResponseDto` body like
   `"Product Service is unavailable, please try again later"`.

Once Product Service and Merchant Service are actually running and reachable, the
same trace instead continues past step 5 into the stock/duplicate-line logic
described in 2.9, ultimately returning `201 Created` with the updated
`CartResponseDto`.

---

## Part 5 — Glossary

| Term | Meaning |
|---|---|
| **Microservice** | A small, independently deployable service owning one part of a larger system (here: just carts) and talking to sibling services over HTTP. |
| **REST / RESTful API** | An HTTP API convention built around resources + verbs (GET/POST/PATCH/DELETE). |
| **DTO** | Data Transfer Object — a plain class shaping data for the wire, distinct from the DB entity. |
| **Entity** | A class mapped 1:1 to a database table via JPA. |
| **ORM** | Object-Relational Mapping — translating between objects and DB rows automatically. |
| **JPA / Hibernate** | The Java spec for ORM (JPA) and the library that implements it (Hibernate). |
| **Repository pattern** | An interface abstracting data access; Spring Data JPA auto-implements it. |
| **Dependency Injection / IoC** | The framework constructs and hands you your dependencies instead of you constructing them. |
| **Bean** | An object whose lifecycle (creation, wiring) is managed by the Spring container. |
| **Annotation** | `@Something` metadata read by a framework at startup/runtime to change behavior. |
| **Lombok** | Compile-time code generator for getters/setters/constructors/builders. |
| **Builder pattern** | `Class.builder().field(x).build()` — a readable way to construct objects with many fields. |
| **Bean Validation** | `@NotNull`/`@Min`/etc. + `@Valid`, declarative input validation. |
| **Feign** | A declarative HTTP client: describe a call as a Java interface, get a real implementation for free. |
| **Circuit breaker** | A safety mechanism that stops calling a failing dependency and routes to a fallback instead. |
| **Fallback** | The alternate code path used when the real (Feign) call fails. |
| **Transaction / `@Transactional`** | A group of DB operations that all succeed or all roll back together. |
| **UUID** | A 128-bit random-looking unique identifier, used here as every primary key. |
| **BigDecimal** | An exact decimal number type, used for all money values to avoid floating-point rounding errors. |
| **JDBC** | Java's standard API for connecting to and querying relational databases. |
| **Maven / `pom.xml`** | Java's dependency manager + build tool, and its project-description file. |
| **BOM (Bill of Materials)** | A `pom.xml` you `import` purely to pin a compatible set of dependency versions. |
| **HTTP status codes used here** | `200` OK, `201` Created, `204` No Content, `400` Bad Request, `404` Not Found, `409` Conflict, `503` Service Unavailable. |
