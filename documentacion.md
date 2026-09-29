# Documentacion tecnica de TacoCloud

**Estado:** completado y verificado  
**Fecha:** 2026-09-29  
**Alcance:** TC-01 a TC-36

Esta es la version resumida y facil de consultar de la documentacion. El detalle historico se conserva en [`documentacion.txt`](documentacion.txt).

## Resultado en una mirada

| Indicador | Resultado |
| --- | --- |
| Retos funcionales | **36 de 36** |
| Pruebas ejecutadas | **68** |
| Fallos | **0** |
| Errores | **0** |
| Pruebas omitidas | **0** |
| Suites criticas | **11 presentes y ejecutadas** |
| Build Maven | **BUILD SUCCESS** |
| Persistencia de integracion | MongoDB 7.0.15 con replica set |
| Mensajeria de integracion | RabbitMQ 3.13.7 |
| Reportes XML | Validados por `scripts/verificar-reportes.py` |

## Que hace la aplicacion

1. Consulta ingredientes y tacos publicados.
2. Permite registrarse, iniciar sesion y crear tacos.
3. Valida ingredientes, cantidades, etiquetas, alergenos y reglas de diseno.
4. Calcula precios y descuentos en el servidor.
5. Tokeniza el metodo de pago sin guardar PAN ni CVV.
6. Crea ordenes con reserva de stock, transaccion Mongo y evento outbox.
7. Publica eventos en RabbitMQ y los procesa de forma idempotente.
8. Permite a cocina reclamar ordenes y avanzar su flujo de estados.
9. Protege historial, favoritos, votos y operaciones administrativas por identidad y rol.

## Indice rapido

- [Cambios por reto](#cambios-por-reto)
- [Cambios tecnicos principales](#cambios-tecnicos-principales)
- [Como verificar](#como-verificar)
- [Estructura relevante](#estructura-relevante)
- [Accesos locales](#accesos-locales)
- [Notas de seguridad](#notas-de-seguridad)

## Cambios por reto

### API y contratos: TC-01 a TC-09

| Reto | Cambio realizado | Estado |
| --- | --- | --- |
| TC-01 | PUT reactivo de ingredientes: busca antes de guardar y conserva el publisher. | OK |
| TC-02 | DELETE reactivo: espera el borrado, responde 204 y devuelve 404 si ya no existe. | OK |
| TC-03 | `Location` se construye desde la peticion, contexto, headers forwarded e ID persistido. | OK |
| TC-04 | PATCH de ordenes con lista blanca; solo permite actualizar direccion y ZIP valido. | OK |
| TC-05 | PUT y DELETE de ordenes conservan identidad, ownership, estado y efectos transaccionales. | OK |
| TC-06 | Conversion de orden por correo con una sola cadena reactiva, sin `block` ni `subscribe` manual. | OK |
| TC-07 | Guardado y publicacion pasan por el mismo caso de uso y outbox transaccional. | OK |
| TC-08 | DTOs de entrada, respuesta y persistencia separados; se ocultan datos internos y sensibles. | OK |
| TC-09 | Errores uniformes tipo Problem Details con validaciones, codigo, correlacion y violaciones. | OK |

### Seguridad, catalogo y negocio: TC-10 a TC-18

| Reto | Cambio realizado | Estado |
| --- | --- | --- |
| TC-10 | Registro reactivo, normalizacion de datos, BCrypt, indices unicos y respuesta sin hash. | OK |
| TC-11 | Autorizacion deny-by-default, roles USER/ADMIN/KITCHEN, ownership y CSRF activo. | OK |
| TC-12 | Pago sintetico tokenizado; el dominio solo conserva token, marca y ultimos cuatro digitos. | OK |
| TC-13 | Catalogo con precio, disponibilidad, stock, version y ajustes atomicos. | OK |
| TC-14 | Precios, cantidades, subtotales y moneda calculados en servidor con `BigDecimal`. | OK |
| TC-15 | Cupones con tipo, vigencia, minimo, limite y reloj configurable. | OK |
| TC-16 | Reserva y liberacion de inventario dentro de transaccion; no permite sobreventa. | OK |
| TC-17 | Tags por interseccion, alergenos por union y picante por maximo. | OK |
| TC-18 | Reglas componibles para tortilla, cantidades, duplicados, disponibilidad, veganismo y picante. | OK |

### Catalogo social y ordenes: TC-19 a TC-26

| Reto | Cambio realizado | Estado |
| --- | --- | --- |
| TC-19 | Busqueda, filtros, ordenamiento con lista blanca y paginacion estable. | OK |
| TC-20 | Taco del dia determinista usando candidatos validos, fecha y `Clock` configurable. | OK |
| TC-21 | Favoritos privados por usuario, upsert idempotente e indice unico usuario-taco. | OK |
| TC-22 | Votos 1..5, ranking con promedio, distribucion, minimo de votos y desempate estable. | OK |
| TC-23 | Historial privado, paginado y ordenado; ADMIN tiene consulta operativa separada. | OK |
| TC-24 | Reorden crea una compra nueva con reglas, precios, cupon e inventario actuales. | OK |
| TC-25 | Flujo centralizado de estados, version optimista, actor, motivo y eventos de cambio. | OK |
| TC-26 | Cola FIFO, claim atomico, estaciones de cocina protegidas y ETA calculada. | OK |

### Mensajeria y operacion: TC-27 a TC-36

| Reto | Cambio realizado | Estado |
| --- | --- | --- |
| TC-27 | Contrato unico de eventos con version, correlacion y snapshot minimo de cocina. | OK |
| TC-28 | Broker seleccionable en runtime: noop, JMS, RabbitMQ o Kafka, con validacion de configuracion. | OK |
| TC-29 | Outbox transaccional con estados, lease, reintentos, backoff y recuperacion de claims vencidos. | OK |
| TC-30 | Consumidor idempotente, retry limitado, DLQ segura y replay administrativo controlado. | OK |
| TC-31 | `X-Correlation-Id` desde HTTP hasta outbox, broker, DLQ y logs; MDC se limpia siempre. | OK |
| TC-32 | Metricas de negocio, gauges operativos y health checks de Mongo, outbox y RabbitMQ. | OK |
| TC-33 | Notes reemplazado por anuncios persistentes, validados, expirables y limitados concurrentemente. | OK |
| TC-34 | `Idempotency-Key` por usuario, hash canonico, respuesta durable y rollback seguro. | OK |
| TC-35 | API versionada en `/api/v1`, alias legado con deprecation y contrato OpenAPI publicado. | OK |
| TC-36 | Integracion real con JUnit, MockMvc, Mongo, RabbitMQ, HTTP MVC y navegador. | OK |

## Detalle de implementacion

Todos los retos tienen el mismo formato para facilitar la revision: **problema**, **solucion**,
**contrato**, **pruebas** y **archivos principales**.

### TC-01 - Actualizar ingredientes sin perder el publisher

- **Problema:** el PUT descartaba el publisher de guardado.
- **Solucion:** `Mono.defer` valida el ID, `findById` confirma que existe y despues se ejecuta `save`.
- **Contrato:** 200 actualizado; 400 por ID contradictorio o cuerpo invalido; 404 si no existe.
- **Pruebas:** `FunctionalChallengesTest`, `IngredientMongoIT`.
- **Archivos y ejemplo:** `IngredientController.java`; `PUT /api/v1/ingredients/COTO`.

### TC-02 - Eliminar ingredientes con semantica HTTP

- **Problema:** DELETE devolvia respuesta sin esperar el borrado reactivo.
- **Solucion:** busqueda, error si no existe, `deleteById` y respuesta vacia en una sola cadena.
- **Contrato:** 204 sin cuerpo; segunda eliminacion y GET posterior devuelven 404.
- **Pruebas:** `FunctionalChallengesTest`, `IngredientMongoIT`.
- **Archivos y ejemplo:** `IngredientController.java`; `DELETE /api/v1/ingredients/TEST-WRAP`.

### TC-03 - Construir `Location` desde la peticion

- **Problema:** `Location` podia contener localhost o rutas incorrectas.
- **Solucion:** `ServletUriComponentsBuilder` usa la peticion, contexto, headers forwarded e ID persistido.
- **Contrato:** 201 con URL consultable; 400 sin escritura si el cuerpo es invalido.
- **Pruebas:** contratos de ingredientes, forwarded headers, errores seguros y `IngredientMongoIT`.
- **Archivos y ejemplo:** `IngredientController.java`; `POST /api/v1/ingredients`.

### TC-04 - PATCH de ordenes con lista blanca

- **Problema:** PATCH mezclaba ZIP con estado y aceptaba campos internos.
- **Solucion:** `OrderPatchRequest` solo permite direccion; se valida propietario, estado `CREATED` y una unica escritura.
- **Contrato:** 200; 400 entrada invalida; 403 ajena; 404 ausente; 409 estado no editable.
- **Pruebas:** `TransactionalChallengesIT`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `OrderPatchRequest.java`, `OrderApplicationService.java`; `PATCH /api/v1/orders/{id}`.

### TC-05 - PUT y DELETE de ordenes con identidad consistente

- **Problema:** PUT podia cambiar el ID y DELETE no esperaba sus efectos.
- **Solucion:** PUT conserva identidad, propietario, fecha y pago; DELETE libera reserva y registra `CANCELLED` en outbox.
- **Contrato:** 200/204; 400 identidad o pago; 403 ajena; 404 ausente; 409 estado incompatible.
- **Pruebas:** ownership, version optimista, FIFO y transacciones en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `OrderApplicationService.java`, `WorkflowService.java`, `OrderApiController.java`.

### TC-06 - Convertir ordenes de correo sin carreras

- **Problema:** la conversion tenia callbacks anidados y listas incompletas.
- **Solucion:** una cadena con `concatMap` y `collectList` resuelve usuario, pago, tacos e ingredientes en orden.
- **Contrato:** error explicito si falta usuario, pago o ingrediente; nunca se emite una orden parcial.
- **Pruebas:** `EmailAndContractTest`, `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `EmailOrderService.java`, `EmailOrder.java`; `POST /api/v1/orders/fromEmail`.

### TC-07 - Guardar y publicar una sola vez

- **Problema:** suscripciones manuales podian duplicar guardado y publicacion.
- **Solucion:** correo y compra usan el mismo caso de uso; orden, reserva y outbox se guardan en transaccion.
- **Contrato:** un fallo no deja orden ni evento; el broker entrega al menos una vez.
- **Pruebas:** rollback, publicacion y suscripcion unica en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `OrderApplicationService.java`, `OutboxService.java`; evento `ORDER_CREATED`.

### TC-08 - Separar DTOs de entrada, salida y persistencia

- **Problema:** el dominio Mongo era tambien el contrato HTTP y exponia datos internos.
- **Solucion:** `Requests`, `Responses` y `ApiMapper` separan responsabilidades y rechazan mass assignment.
- **Contrato:** precio, usuario, fecha, estado e identidad no se asignan desde POST; respuestas no exponen secretos.
- **Pruebas:** `BusinessRulesTest`, `EmailAndContractTest`, `OpenApiContractTest`.
- **Archivos y ejemplo:** `api/dto/Requests.java`, `Responses.java`, `ApiMapper.java`; total o `userId` extra devuelve 400.

### TC-09 - Validacion y errores Problem Details

- **Problema:** los errores no tenian estructura comun y podian filtrar excepciones internas.
- **Solucion:** `ApiProblemHandler` devuelve type, title, status, detail, instance, code, correlacion y violations.
- **Contrato:** 400, 401, 403, 404, 409, 422 y 500 seguros, sin stacktrace ni driver.
- **Pruebas:** `FunctionalChallengesTest`, `OpenApiContractTest`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `ApiProblemHandler.java`, `ApiException.java`; nombre vacio devuelve `violations`.

### TC-10 - Registro reactivo y contrasenas protegidas

- **Problema:** el registro podia descartar `save` y guardar contrasenas sin proteccion.
- **Solucion:** normalizacion, `DelegatingPasswordEncoder`, `boundedElastic` e indices unicos contra carreras.
- **Contrato:** 201 despues de persistir; 409 duplicado; hash `{bcrypt}` y nunca en respuestas.
- **Pruebas:** `SecurityAndRegistrationTest`, `TransactionalChallengesIT`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `RegistrationService.java`, `RegistrationForm.java`, `AuthController.java`, `UserRegistrationApi.java`.

### TC-11 - Autorizacion deny-by-default

- **Problema:** `permitAll` global dejaba rutas sensibles abiertas.
- **Solucion:** catalogo publico; USER/ADMIN para compras y social; ADMIN para operaciones; KITCHEN para cocina; resto denegado.
- **Contrato:** 401 sin credenciales y 403 por rol o ruta no listada; identidad desde autenticacion.
- **Pruebas:** `SecurityAndRegistrationTest`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `SecurityConfig.java`, `IdentityService.java`; USER en `/api/v1/kitchen/queue` recibe 403.

### TC-12 - Tokenizar pagos y eliminar PAN/CVV

- **Problema:** se persistian tarjeta, expiracion y CVV.
- **Solucion:** gateway sintetico acepta `test_` mas cuatro digitos; dominio conserva token, marca y last4.
- **Contrato:** 201 sintetico; 422 no sintetico; eventos sin pago; migracion elimina campos heredados.
- **Pruebas:** `BusinessRulesTest`, `EmailAndContractTest`, migracion en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `PaymentGateway.java`, `FakePaymentGateway.java`, `PaymentMethod.java`, `migrate-payment.js`.

### TC-13 - Catalogo con precio, disponibilidad y stock

- **Problema:** faltaban precio, existencias y control concurrente.
- **Solucion:** precio decimal, disponibilidad, stock, umbral, version y ajuste atomico solo para ADMIN.
- **Contrato:** precio no negativo; stock nunca negativo; 409 por version obsoleta o ajuste imposible.
- **Pruebas:** locking y stock negativo en `TransactionalChallengesIT`; matriz de seguridad.
- **Archivos y ejemplo:** `CatalogService.java`, `Requests.java`, `Ingredient.java`; ajuste `delta: 10`.

### TC-14 - Calcular precios y cantidades en servidor

- **Problema:** el carrito perdia cantidades y el cliente podia imponer precios.
- **Solucion:** `PricingService` consulta ingredientes, suma `BigDecimal`, redondea HALF_UP y persiste snapshot historico.
- **Contrato:** quantity 1..maximo, limite HTTP 50; quote no reserva stock.
- **Pruebas:** precios, cantidades y navegador en `BusinessRulesTest` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `PricingService.java`, `OrderLine.java`, `cart-service.ts`; quote con quantity 2.

### TC-15 - Motor de cupones

- **Problema:** los cupones estaban fuera del runtime de compras.
- **Solucion:** tipos fijo/porcentaje, fechas inclusivas/exclusivas, minimo, tope, normalizacion y `Clock` configurable.
- **Contrato:** descuento entre cero y subtotal; codigo no aplicable para desconocido, vencido, futuro o minimo insuficiente.
- **Pruebas:** expiracion, tope, minimo e inicio en `BusinessRulesTest`.
- **Archivos y ejemplo:** `BusinessConfig.java`, `CouponService.java`, `PricingService.java`.

### TC-16 - Reservar y liberar inventario sin sobreventa

- **Problema:** compras concurrentes podian descontar stock de forma insegura.
- **Solucion:** agrupacion por ingrediente, IDs ordenados, `findAndModify` con stock suficiente y transaccion Mongo.
- **Contrato:** fallo parcial revierte todo; dos compradores no venden mas de lo disponible; liberar es idempotente.
- **Pruebas:** concurrencia, rollback y cancelacion en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `InventoryService.java`, `OrderApplicationService.java`, `PersistenceConfig.java`.

### TC-17 - Etiquetas, alergenos y picante

- **Problema:** no existia clasificacion verificable por ingredientes.
- **Solucion:** tags por interseccion, alergenos por union y picante por maximo; el cliente no puede imponerlos.
- **Contrato:** metadata academica con aviso de que no sustituye control de contaminacion cruzada.
- **Pruebas:** clasificacion en `BusinessRulesTest` y consultas en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `DesignService.java`, `CatalogService.java`, `Ingredient.java`, `Taco.java`.

### TC-18 - Reglas componibles de diseno

- **Problema:** cualquier combinacion podia guardarse o cotizarse.
- **Solucion:** `DesignRule` acumula violaciones: tortilla, 2..12 ingredientes, sin duplicados y disponibles.
- **Contrato:** 422 con todas las violaciones; crear y cotizar usan el mismo servicio antes de persistir o reservar.
- **Pruebas:** reglas extensibles, veganismo, picante y limites en `BusinessRulesTest`.
- **Archivos y ejemplo:** `DesignRule.java`, `DesignRules.java`, `DesignService.java`; `POST /api/v1/tacos/validate`.

### TC-19 - Buscar, filtrar, ordenar y paginar tacos

- **Problema:** la UI usaba una ruta errada y el catalogo no se paginaba.
- **Solucion:** filtros Mongo, texto escapado, lista blanca de sort, desempate por ID y pagina limitada.
- **Contrato:** `content`, `page`, `size`, `totalElements`; size maximo 100; recent hasta 12.
- **Pruebas:** `TransactionalChallengesIT`, `TacoControllerTest`.
- **Archivos y ejemplo:** `CatalogService.java`, `TacoController.java`, `recents.component.ts`.

### TC-20 - Taco del dia determinista

- **Problema:** no existia seleccion diaria reproducible.
- **Solucion:** candidatos publicados ordenados por ID, ingredientes actuales, descarte de invalidos e indice por fecha.
- **Contrato:** 200 con taco, fecha y explicacion; 404 sin candidatos; mismo catalogo y fecha dan el mismo resultado.
- **Pruebas:** `TransactionalChallengesIT#queryFiltersPagingAndDailySelectionAreStable`.
- **Archivos y ejemplo:** `CatalogService.java`, `BusinessConfig.java`; `GET /api/v1/tacos/today`.

### TC-21 - Favoritos privados por usuario

- **Problema:** no habia favoritos privados ni proteccion contra duplicados.
- **Solucion:** usuario autenticado, taco publicado, upsert e indice unico `userId+tacoId`; DELETE idempotente.
- **Contrato:** PUT/DELETE 204; taco inexistente 404; un usuario no ve favoritos ajenos.
- **Pruebas:** social e2e en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `SocialService.java`, `Favorite.java`, `AccountController.java`.

### TC-22 - Calificaciones y ranking

- **Problema:** no existia voto unico ni ranking consistente.
- **Solucion:** voto 1..5 con upsert e indice unico; agregacion para promedio, cantidad y distribucion.
- **Contrato:** 204 al votar; 400 fuera de rango; 404 no publicado; ranking con minimo y desempate estable.
- **Pruebas:** favoritos y ratings en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `SocialService.java`, `TacoController.java`, `TacoRating.java`.

### TC-23 - Historial privado y paginado

- **Problema:** USER podia acceder a una lista global de ordenes.
- **Solucion:** rutas `/users/me`, filtro por identidad, orden `placedAt DESC, ID ASC` y consulta ADMIN separada.
- **Contrato:** solo datos propios; 403 al detalle ajeno; pagina fuera de rango vacia con metadata.
- **Pruebas:** historial, outbox y contrato seguro en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `AccountController.java`, `OrderApplicationService.java`, `PersistenceConfig.java`.

### TC-24 - Reorden con reglas actuales

- **Problema:** clonar una orden podia reutilizar identidad o precios obsoletos.
- **Solucion:** nueva compra con reglas, precios, cupon e inventario actuales; confirma cambios de precio.
- **Contrato:** 201 nueva; 409 `PRICE_CHANGED_CONFIRM_REQUIRED`; reintento idempotente; original intacta.
- **Pruebas:** `TransactionalChallengesIT#historyIsPrivateAndReorderUsesCurrentPriceAndIdempotentKey`.
- **Archivos y ejemplo:** `OrderApplicationService.java`, `PriceChangedException.java`, `ReorderRecord.java`.

### TC-25 - Flujo de estados de una orden

- **Problema:** el estado podia cambiar sin reglas ni historial.
- **Solucion:** flujo centralizado, version optimista, actor, motivo, reloj y outbox.
- **Contrato:** `CREATED -> ACCEPTED -> PREPARING -> READY -> OUT_FOR_DELIVERY -> DELIVERED`; cancelacion antes de preparar.
- **Pruebas:** matriz de workflow, version obsoleta y cancelacion en `BusinessRulesTest` y `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `WorkflowService.java`, `TacoOrder.java`, `StatusChange.java`.

### TC-26 - Cola de cocina y claim atomico

- **Problema:** cocina no tenia FIFO ni claim protegido.
- **Solucion:** `findAndModify` por estado y fecha, estacion ocupada protegida, reintentos y ETA por carga.
- **Contrato:** solo KITCHEN; 200 claim; 404 cola vacia; 409 estacion ocupada; DTO sin direccion ni pago.
- **Pruebas:** claim atomico, FIFO, estacion y version en `TransactionalChallengesIT`.
- **Archivos y ejemplo:** `KitchenService.java`, `StationSlot.java`, `OperationsController.java`, `kitchen.html`.

### TC-27 - Contrato unico de eventos

- **Problema:** transportes duplicaban contratos y serializaban dominio.
- **Solucion:** modulo comun con evento versionado, tipo, payload, UUID, correlacion y snapshot minimo.
- **Contrato:** sin dependencias de Spring, Mongo o broker; V1 acepta campos adicionales; sin usuario, direccion ni pago.
- **Pruebas:** `EmailAndContractTest`, `TransportTest`.
- **Archivos y ejemplo:** `OrderEvent.java`, `OrderEventPayload.java`, `OrderEventType.java`, `EventFactory.java`.

### TC-28 - Broker configurable en runtime

- **Problema:** cambiar broker exigia editar POM y habia clases duplicadas.
- **Solucion:** adaptadores comunmente compilables y `TransportGuard` exige exactamente uno segun propiedad.
- **Contrato:** noop solo en dev/test; produccion exige broker real; destino y conexion por entorno.
- **Pruebas:** seleccion de noop/JMS/Kafka/Rabbit en `TransportTest` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `TransportGuard.java`, modulos `messaging-*`; `TACO_TRANSPORT=rabbit`.

### TC-29 - Outbox transaccional

- **Problema:** una orden podia guardarse sin evento o publicarse sin orden.
- **Solucion:** reserva, orden y outbox en replica set; publisher con lease, token, confirmacion y backoff.
- **Contrato:** `NEW/PUBLISHING/PUBLISHED/FAILED`; maximo 8, lease 60 s, timeout 20 s, backoff hasta 300 s.
- **Pruebas:** rollback, reintentos, claims exclusivos y recuperacion en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `OutboxService.java`, `OutboxPublisher.java`, `OutboxEvent.java`.

### TC-30 - Consumidor idempotente, retry y DLQ

- **Problema:** redelivery repetia negocio y podia reintentarse indefinidamente.
- **Solucion:** `ProcessedEvent` y `KitchenTicket` en transaccion; errores permanentes van a DLQ segura.
- **Contrato:** invalidos sin retry; maximo configurable; DLQ confirmada antes del ack; replay conserva eventId.
- **Pruebas:** `RabbitRetryTest`, `TransactionalChallengesIT`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `IdempotentEventConsumer.java`, `DeadLetterService.java`, `ReplayController.java`.

### TC-31 - Correlation ID de HTTP a eventos

- **Problema:** no habia identificador comun entre HTTP y procesos asincronos.
- **Solucion:** filtro valida o genera ID, MDC se limpia, Reactor Context lo conserva y brokers/DLQ lo propagan.
- **Contrato:** no se usa orderId como correlacion; no se registran cuerpos sensibles ni IDs como tags metricos.
- **Pruebas:** `CorrelationAndIdempotencyTest`, `TransactionalChallengesIT`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** `CorrelationFilter.java`, `ReactiveCorrelation.java`, `OutboxService.java`.

### TC-32 - Metricas y salud operativa

- **Problema:** metricas no explicaban negocio y habia consultas bloqueantes en informacion.
- **Solucion:** counters, timers, gauges asincronos y health de Mongo, outbox y RabbitMQ.
- **Contrato:** metricas y detalles solo ADMIN; health publico sin informacion sensible; outbox FAILED degrada salud.
- **Pruebas:** metricas, gauges y health en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `BusinessMetrics.java`, `OperationalSnapshot.java`, `OutboxHealth.java`.

### TC-33 - Anuncios operativos persistentes

- **Problema:** Notes usaba indices de una lista en memoria y perdia datos al reiniciar.
- **Solucion:** anuncios con UUID, texto, severidad, fechas, autor, expiracion y limite concurrente transaccional.
- **Contrato:** GET publico sin autor; POST/DELETE ADMIN; 400/422 validacion; 409 limite; 404 ID inexistente.
- **Pruebas:** persistencia, expiracion y limite en `TransactionalChallengesIT` y `RuntimeMvcIT`.
- **Archivos y ejemplo:** `Announcement.java`, `AnnouncementService.java`, `AnnouncementController.java`.

### TC-34 - Idempotency-Key en ordenes

- **Problema:** reintentar POST podia duplicar compras y consumir stock dos veces.
- **Solucion:** key ASCII 8..100, hash canonico, registro `IN_PROGRESS`, resultado durable y TTL minimo de 168 horas.
- **Contrato:** misma key y cuerpo devuelve el mismo snapshot; cuerpo distinto 409; rollback permite reintentar.
- **Pruebas:** concurrencia, alcance por usuario y rollback en `TransactionalChallengesIT` y `CorrelationAndIdempotencyTest`.
- **Archivos y ejemplo:** `IdempotentOrders.java`, `IdempotencyRecord.java`, `TransactionRetry.java`.

### TC-35 - API versionada y OpenAPI

- **Problema:** no existia version ni contrato verificable.
- **Solucion:** `/api/v1`, alias `/api` con `Deprecation` y `Link`, OpenAPI con DTOs, seguridad, CSRF, correlacion e idempotencia.
- **Contrato:** Swagger Parser y JSON Schema validan el contrato y respuestas reales.
- **Pruebas:** `OpenApiContractTest`, alias en `CorrelationAndIdempotencyTest`, `RuntimeMvcIT`.
- **Archivos y ejemplo:** controladores, `CorrelationFilter.java`, endpoint `GET /openapi.yaml`.

### TC-36 - Suite de integracion contra regresiones

- **Problema:** las pruebas dependian del equipo y omitian seguridad o persistencia real.
- **Solucion:** JUnit/StepVerifier, MockMvc, Mongo replica set, RabbitMQ, MVC RANDOM_PORT y prueba de navegador.
- **Contrato:** Mongo 7.0.15, RabbitMQ 3.13.7, Testcontainers 1.21.4; bytecode Java 11 probado con JDK 21.
- **Pruebas:** todas las suites TC-01..TC-35; CI ejecuta `clean verify` y valida XML.
- **Archivos y ejemplo:** `scripts/verificar-retos.sh`, `scripts/verificar-reportes.py`, `RuntimeMvcIT.java`, workflow CI.

## Cambios tecnicos principales

### Persistencia y consistencia

- MongoDB se usa con replica set para soportar transacciones.
- Stock, orden, outbox e idempotencia se coordinan en la misma transaccion.
- Indices unicos protegen registro, favoritos, votos y eventos procesados.
- Las actualizaciones de stock usan operaciones atomicas y nunca permiten valores negativos.

### Seguridad y privacidad

- La seguridad termina en `denyAll` para rutas no declaradas.
- La identidad se obtiene de la sesion o autenticacion, nunca de un `userId` enviado por el cliente.
- Las respuestas no exponen contrasenas, tokens, PAN, CVV ni detalles internos.
- Los errores no devuelven stacktrace, consultas Mongo ni informacion del driver.

### Mensajeria

- Todos los adaptadores usan el contrato de `tacocloud-messaging-contract`.
- La publicacion ocurre despues del commit mediante outbox.
- Los consumidores confirman el mensaje despues de persistir el efecto.
- Los mensajes invalidos se llevan a DLQ sin reintentos infinitos ni exposicion de datos sensibles.

### API y frontend

- Las rutas nuevas se publican bajo `/api/v1`; `/api` queda como alias documentado.
- OpenAPI esta disponible en el endpoint `/openapi.yaml` cuando la aplicacion esta levantada.
- La UI usa la API versionada y envia cantidades de carrito al servidor.
- La compra desde navegador se prueba con autenticacion, favoritos y `quantity: 2`.

## Como verificar

Desde la raiz del repositorio:

```bash
bash scripts/verificar-retos.sh
```

El script comprueba Docker, ejecuta Maven con integracion, levanta MongoDB y RabbitMQ mediante Testcontainers y valida los reportes XML.

Para validar solo los reportes ya generados:

```bash
python3 scripts/verificar-reportes.py
```

Resultado de la ultima ejecucion:

```text
BUILD SUCCESS
68 pruebas verificadas, cero fallos y cero omitidas.
Suites criticas presentes.
```

### Suites criticas

- `FunctionalChallengesTest`
- `BusinessRulesTest`
- `EmailAndContractTest`
- `SecurityAndRegistrationTest`
- `TransportTest`
- `CorrelationAndIdempotencyTest`
- `OpenApiContractTest`
- `RabbitRetryTest`
- `IngredientMongoIT`
- `TransactionalChallengesIT`
- `RuntimeMvcIT`

## Estructura relevante

- `tacocloud/tacocloud-api`: controladores, DTOs, reglas de negocio y pruebas.
- `tacocloud/tacocloud-domain-mongodb`: entidades y documentos Mongo.
- `tacocloud/tacocloud-data-mongodb`: repositorios y configuracion de persistencia.
- `tacocloud/tacocloud-security`: autenticacion, registro, roles y correlacion.
- `tacocloud/tacocloud-messaging-contract`: contrato comun de eventos.
- `tacocloud/tacocloud-messaging-*`: adaptadores de transporte.
- `tacocloud/tacocloud-ui`: frontend Angular.
- `tacocloud/tacocloud/src/test`: prueba HTTP MVC de extremo a extremo.
- `scripts`: arranque, parada y verificacion del laboratorio.

## Accesos locales

Para levantar el entorno:

```bash
bash scripts/levantar-tacocloud.sh
```

| Servicio | URL |
| --- | --- |
| Aplicacion | http://localhost:8080/ui/home |
| Login y registro | http://localhost:8080/ui/login |
| Cocina | http://localhost:8080/kitchen.html |
| OpenAPI | http://localhost:8080/openapi.yaml |
| RabbitMQ | http://localhost:15678 |

Para detenerlo:

```bash
bash scripts/bajar-tacocloud.sh
```

La primera ejecucion genera `.runtime/lab.env` con credenciales locales. No se deben subir claves reales al repositorio.

## Postman y autenticacion

Coleccion disponible en [`postman/TacoCloud-36-Retos.postman_collection.json`](postman/TacoCloud-36-Retos.postman_collection.json).

1. Ejecutar primero la peticion CSRF para obtener token y cookie.
2. Usar `X-XSRF-TOKEN` y la cookie `XSRF-TOKEN` en POST, PUT, PATCH y DELETE.
3. Iniciar sesion con el rol correspondiente: USER, ADMIN o KITCHEN.
4. Configurar `adminPassword` y `kitchenPassword` con la clave local de `.runtime/lab.env`.

La suite de navegador requiere Google Chrome. Para indicar otra instalacion:

```bash
TACO_BROWSER_EXECUTABLE=/ruta/a/chrome bash scripts/verificar-retos.sh
```

## Metricas y salud

| Tipo | Nombres principales |
| --- | --- |
| Counters | `tacocloud.orders.created`, `orders.failed`, `orders.cancelled`, `coupons.applied`, `stock.rejected`, `events.dlq` |
| Timers | `tacocloud.orders.placement`, `tacocloud.kitchen.latency` |
| Gauges | `tacocloud.outbox.pending`, `outbox.failed`, `kitchen.queue`, `dlq.pending` |

Los snapshots se actualizan de forma asincrona cada cinco segundos. La salud publica esta disponible en
`/actuator/health`, `/actuator/health/readiness` y `/actuator/health/liveness`; los detalles y metricas requieren ADMIN.

## Replay controlado de DLQ

ADMIN consulta `GET /api/v1/admin/dead-letters` y puede repetir un evento valido con:

```text
POST /api/v1/admin/dead-letters/{id}/replay
```

El replay conserva `eventId` y un segundo intento devuelve `applied:false` si ya fue procesado. Mensajes con JSON o
version invalidos no se convierten ni se reenvian.

## Notas de seguridad

- Las operaciones de escritura requieren CSRF cuando se usa sesion o Basic Auth.
- `ADMIN` puede consultar metricas, detalles de salud y DLQ.
- `KITCHEN` puede reclamar y avanzar ordenes de cocina.
- Los mensajes invalidos no se convierten ni se reenvian como eventos validos.
- La tokenizacion de pago es sintetica para el laboratorio y no representa un proveedor de pagos real.

## Fuente y respaldo

- Manual tecnico: [`Manual_Tecnico_TacoCloud.docx`](Manual_Tecnico_TacoCloud.docx)
- Especificacion funcional: [`TacoCloud_Retos_Funcionales_Estudiantes.docx`](TacoCloud_Retos_Funcionales_Estudiantes.docx)
- Documentacion detallada previa: [`documentacion.txt`](documentacion.txt)
