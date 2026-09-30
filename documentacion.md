# Verificacion de los 36 retos de TacoCloud

**Fecha:** 2026-09-27

**Carpeta verificada:** `/home/torres/NTT/Microservicios-NTTData`

**Estado final:** 36 de 36 retos aprobados al 100%

## Criterio usado para aprobar

Cada reto se marco como aprobado solamente cuando se encontro la solucion en el codigo y una prueba automatizada ejercito el comportamiento solicitado. La comprobacion completa uso Spring Boot 2.5.3, bytecode Java 11, JDK 21, MongoDB 7.0.15, RabbitMQ 3.13.7, Testcontainers 1.21.4 y Google Chrome.

Comando ejecutado desde la raiz:

```bash
cd /home/torres/NTT/Microservicios-NTTData
bash scripts/verificar-retos.sh
```

Salida final observada:

```text
Reactor Summary:
taco-cloud-parent ................ SUCCESS
tacocloud-messaging-contract ..... SUCCESS
tacocloud-domain ................. SUCCESS
tacocloud-data-mongodb ........... SUCCESS
tacocloud-security ............... SUCCESS
tacocloud-messaging-jms .......... SUCCESS
tacocloud-messaging-rabbitmq ..... SUCCESS
tacocloud-messaging-kafka ........ SUCCESS
tacocloud-messaging-noop ......... SUCCESS
tacocloud-api .................... SUCCESS
tacocloud-ui ..................... SUCCESS
tacocloud-jmx .................... SUCCESS
taco-cloud ....................... SUCCESS
tacocloud-kitchen ................ SUCCESS
BUILD SUCCESS
68 pruebas verificadas, cero fallos y cero omitidas. Suites criticas presentes.
```

Los reportes XML confirmaron 68 pruebas, 0 fallos, 0 errores y 0 omitidas. La ejecucion incluyo 12 suites:

| Suite | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| `FunctionalChallengesTest` | 6 | 0 | 0 | 0 |
| `BusinessRulesTest` | 10 | 0 | 0 | 0 |
| `EmailAndContractTest` | 3 | 0 | 0 | 0 |
| `SecurityAndRegistrationTest` | 3 | 0 | 0 | 0 |
| `TransportTest` | 2 | 0 | 0 | 0 |
| `CorrelationAndIdempotencyTest` | 3 | 0 | 0 | 0 |
| `OpenApiContractTest` | 2 | 0 | 0 | 0 |
| `RabbitRetryTest` | 3 | 0 | 0 | 0 |
| `IngredientMongoIT` | 1 | 0 | 0 | 0 |
| `TransactionalChallengesIT` | 26 | 0 | 0 | 0 |
| `RuntimeMvcIT` | 7 | 0 | 0 | 0 |
| `TacoControllerTest` | 2 | 0 | 0 | 0 |

## Verificacion reto por reto

### TC-01 - Actualizar un ingrediente sin perder el publisher

- **Problema:** PUT descartaba el publisher de `save` y podia responder sin escribir.
- **Solucion:** `IngredientController` devuelve la cadena `Mono`, valida el ID, busca primero y ejecuta `save` dentro de la suscripcion.
- **Verificacion:** `ingredientPutIsLazyAndCompletes`, `ingredientPutHttpContracts` y `createUpdateReadDeleteAgainstRealMongo`.
- **Salida:** `FunctionalChallengesTest` 6/0/0/0 e `IngredientMongoIT` 1/0/0/0.
- **Estado:** APROBADO 100%.

### TC-02 - Eliminar de verdad y responder con semantica HTTP

- **Problema:** DELETE podia terminar sin ejecutar el borrado reactivo.
- **Solucion:** se encadenan busqueda, `deleteById` y respuesta 204; una segunda eliminacion devuelve 404.
- **Verificacion:** `ingredientDeleteWaitsForEffectAndSecondCallIs404` y CRUD real de `IngredientMongoIT`.
- **Salida:** ambas suites terminaron sin fallos, errores ni omisiones.
- **Estado:** APROBADO 100%.

### TC-03 - Construir Location sin localhost ni rutas rotas

- **Problema:** POST construia `Location` con host, contexto o ID incorrectos.
- **Solucion:** la URI se crea desde la peticion y el ID persistido; soporta headers forwarded y contexto.
- **Verificacion:** `locationUsesRequestAndPersistedId`, `locationSupportsForwardedHeaders` e `IngredientMongoIT`.
- **Salida:** 3 escenarios aprobados, incluido MongoDB real.
- **Estado:** APROBADO 100%.

### TC-04 - PATCH de ordenes con lista blanca y ZIP correcto

- **Problema:** PATCH mezclaba ZIP con estado y permitia modificar campos internos.
- **Solucion:** `OrderPatchRequest` acepta solo direccion; se validan propietario, estado y campos desconocidos.
- **Verificacion:** `patchZipOwnershipPutIdentityAndDeleteRemainProtected` y `fullOrderOutboxBrokerRedeliveryAndSafeContract`.
- **Salida:** `TransactionalChallengesIT` 26/0/0/0 y `RuntimeMvcIT` 7/0/0/0.
- **Estado:** APROBADO 100%.

### TC-05 - PUT y DELETE con identidad consistente

- **Problema:** PUT podia cambiar identidad o pago y DELETE podia dejar efectos incompletos.
- **Solucion:** PUT conserva ID, usuario, fecha y pago; DELETE valida propiedad y estado, libera reserva y registra cancelacion.
- **Verificacion:** `replacementReusesItsOwnStockAndCannotChangeIdentityOrPayment`, `patchZipOwnershipPutIdentityAndDeleteRemainProtected` y prueba HTTP completa.
- **Salida:** pruebas transaccionales y HTTP aprobadas.
- **Estado:** APROBADO 100%.

### TC-06 - Convertir ordenes de correo sin carreras

- **Problema:** callbacks anidados podian producir tacos con ingredientes incompletos.
- **Solucion:** usuario, pago, tacos e ingredientes se resuelven en una sola cadena con `concatMap` y `collectList`.
- **Verificacion:** `emailWaitsForAllLookupsAndSubscribesOnce`, `missingUserPaymentAndIngredientAreControlled` y `emailPlacementSubscribesOnceAndFailuresDoNotPublish`.
- **Salida:** `EmailAndContractTest` 3/0/0/0 y caso transaccional aprobado.
- **Estado:** APROBADO 100%.

### TC-07 - Una sola suscripcion para guardar y publicar

- **Problema:** una suscripcion manual podia duplicar ordenes o publicar antes del commit.
- **Solucion:** la compra guarda orden, inventario y outbox en una transaccion; el publisher trabaja despues del commit.
- **Verificacion:** `emailPlacementSubscribesOnceAndFailuresDoNotPublish`, `placePersistsPriceSnapshotInventoryAndExactlyOneOutbox` y rollback forzado.
- **Salida:** una orden y un evento; el fallo deja cero ordenes y cero eventos.
- **Estado:** APROBADO 100%.

### TC-08 - Separar DTOs de entrada, respuesta y persistencia

- **Problema:** las entidades Mongo se exponian como contrato HTTP y aceptaban mass assignment.
- **Solucion:** `Requests`, `Responses` y `ApiMapper` separan entrada, salida y dominio; los campos del servidor no son asignables.
- **Verificacion:** `moneyFieldsAndIdentityCannotBeAssigned`, `responseAndEventNeverSerializeSensitiveFieldsAndV1AcceptsExtensions` y contrato OpenAPI.
- **Salida:** campos internos y sensibles rechazados o ausentes.
- **Estado:** APROBADO 100%.

### TC-09 - Validacion y errores Problem Details

- **Problema:** los errores no tenian formato uniforme y podian filtrar detalles internos.
- **Solucion:** `ApiProblemHandler` produce `type`, `title`, `status`, `detail`, `instance`, `code`, `correlationId` y `violations`.
- **Verificacion:** `errorsAreSafeAndMassAssignmentIsRejected`, `orderAndProblemSchemasRejectIncompatibleFields` y pruebas HTTP.
- **Salida:** errores seguros 400, 401, 403, 404, 409, 422 y 500 cubiertos.
- **Estado:** APROBADO 100%.

### TC-10 - Registro reactivo y contrasenas protegidas

- **Problema:** el registro descartaba `save` y almacenaba contrasenas sin proteccion.
- **Solucion:** `RegistrationService` normaliza, usa `DelegatingPasswordEncoder`, espera persistencia y resuelve carreras con indices unicos.
- **Verificacion:** `registrationHashesPasswordAndPersistsOnlyOnSubscription`, `uniqueIndexRaceReturnsConflict`, `concurrentRegistrationUsesUniqueIndexesAndPasswordRemainsPrivate` y runtime HTTP.
- **Salida:** hash bcrypt, 201 al persistir y 409 para duplicado.
- **Estado:** APROBADO 100%.

### TC-11 - Autorizacion deny-by-default y roles

- **Problema:** `permitAll` dejaba rutas sensibles abiertas.
- **Solucion:** `SecurityConfig` termina en `denyAll`; USER, ADMIN y KITCHEN tienen permisos separados y ownership en servicios.
- **Verificacion:** `authorizationMatrixAndDefaultDenial` y `mvcHttpSecurityRegistrationAndAliasContracts`.
- **Salida:** 401 sin identidad, 403 por rol o ruta no listada y acceso correcto por rol.
- **Estado:** APROBADO 100%.

### TC-12 - Tokenizar pago y eliminar PAN/CVV

- **Problema:** el dominio persistia numero de tarjeta, expiracion y CVV.
- **Solucion:** el gateway sintetico genera token; solo se conservan token, marca y ultimos cuatro digitos. La migracion elimina campos heredados.
- **Verificacion:** `fakePaymentAcceptsSyntheticDataOnlyAndHidesToken`, serializacion segura y `paymentMigrationRemovesLegacySensitiveFieldsFromMongo`.
- **Salida:** pago sintetico aceptado, entrada real rechazada y secretos ausentes.
- **Estado:** APROBADO 100%.

### TC-13 - Catalogo con precio, disponibilidad y stock

- **Problema:** faltaban precio, stock, disponibilidad y control concurrente.
- **Solucion:** `Ingredient` usa `BigDecimal`, stock, umbral y version; `CatalogService` ajusta stock de forma atomica.
- **Verificacion:** `optimisticLockingAndNegativeStockAreEnforced` y pruebas de seguridad administrativa.
- **Salida:** version obsoleta y stock negativo son rechazados sin modificar inventario.
- **Estado:** APROBADO 100%.

### TC-14 - Precios y cantidades calculados en servidor

- **Problema:** el cliente podia imponer cantidades o totales incorrectos.
- **Solucion:** `PricingService` consulta precios actuales, calcula con `BigDecimal` y persiste snapshots por linea.
- **Verificacion:** `pricesUseDecimalQuantitiesAndHistoricalSnapshot`, `quantityBoundsFailBeforeIngredientLookup` y compra de navegador con cantidad 2.
- **Salida:** total, subtotal, limites y snapshot historico aprobados.
- **Estado:** APROBADO 100%.

### TC-15 - Motor de cupones

- **Problema:** no habia reglas de vigencia, minimo, porcentaje, monto fijo o limite.
- **Solucion:** `CouponService` usa reloj inyectado, fechas inclusiva/exclusiva, minimo y tope sin permitir total negativo.
- **Verificacion:** `couponsHaveExclusiveExpiryAndCapAtZero`, `couponMinimumAndStartAreEnforced` y metricas de cupon.
- **Salida:** cupon valido aplicado; cupon futuro, vencido o insuficiente rechazado.
- **Estado:** APROBADO 100%.

### TC-16 - Reserva y liberacion de inventario

- **Problema:** compras concurrentes podian sobre-vender o dejar descuentos parciales.
- **Solucion:** `InventoryService` usa actualizacion atomica y transaccion Mongo; la liberacion es idempotente.
- **Verificacion:** `concurrentBuyersCannotOversell`, `transactionRollsBackPartialStockAndOrderOnFailure` y `cancellationReleasesOnceAndForbiddenTransitionDoesNotWrite`.
- **Salida:** un solo comprador consume stock 1; rollback y liberacion restauran el valor correcto.
- **Estado:** APROBADO 100%.

### TC-17 - Etiquetas, alergenos y picante

- **Problema:** no existia clasificacion derivada de ingredientes.
- **Solucion:** tags usan interseccion, alergenos union y picante el maximo; el cliente no puede imponer resultados.
- **Verificacion:** `classificationIntersectsTagsUnionsAllergensAndUsesMaximumSpice` y consulta real de catalogo.
- **Salida:** clasificacion calculada y filtros aprobados.
- **Estado:** APROBADO 100%.

### TC-18 - Reglas componibles de diseno

- **Problema:** cualquier taco podia guardarse o cotizarse.
- **Solucion:** `DesignRule` acumula violaciones para tortilla, cantidad, duplicados, disponibilidad, veganismo y picante.
- **Verificacion:** `rulesAccumulateAndCanBeExtended`, `configuredHotAndVeganRulesApply` y limites de cantidad.
- **Salida:** todas las violaciones se devuelven juntas y las reglas son extensibles.
- **Estado:** APROBADO 100%.

### TC-19 - Buscar, filtrar, ordenar y paginar tacos

- **Problema:** el catalogo no tenia consulta estable y la UI usaba una ruta incorrecta.
- **Solucion:** `CatalogService` aplica filtros Mongo, sort en lista blanca, desempate por ID y limite de pagina.
- **Verificacion:** `queryFiltersPagingAndDailySelectionAreStable`, `shouldReturnRecentTacos` y UI runtime.
- **Salida:** pagina, filtros, orden y limite de 12 recientes aprobados.
- **Estado:** APROBADO 100%.

### TC-20 - Taco del dia determinista

- **Problema:** faltaba una seleccion diaria reproducible y valida.
- **Solucion:** candidatos ordenados se validan y el indice depende de fecha y `Clock` configurado.
- **Verificacion:** `queryFiltersPagingAndDailySelectionAreStable` y `dailySelectionChangesPredictablyAndRankingBreaksTiesById`.
- **Salida:** misma fecha produce el mismo taco; cambio de fecha es predecible; sin candidatos devuelve error controlado.
- **Estado:** APROBADO 100%.

### TC-21 - Favoritos privados por usuario

- **Problema:** no habia favoritos privados ni proteccion contra duplicados.
- **Solucion:** la identidad procede de autenticacion; upsert e indice unico hacen idempotente el favorito.
- **Verificacion:** `favoritesAndRatingsAreIdempotentAndPrivate` y flujo de navegador con recarga.
- **Salida:** favorito unico, privado y eliminacion idempotente.
- **Estado:** APROBADO 100%.

### TC-22 - Calificaciones y ranking

- **Problema:** faltaban voto unico, promedio, distribucion y desempate estable.
- **Solucion:** voto 1..5 usa upsert por usuario/taco; agregacion calcula ranking y desempata por ID.
- **Verificacion:** `favoritesAndRatingsAreIdempotentAndPrivate` y `dailySelectionChangesPredictablyAndRankingBreaksTiesById`.
- **Salida:** actualizacion de voto no duplica; promedio, cantidad y desempate correctos.
- **Estado:** APROBADO 100%.

### TC-23 - Historial privado y paginado

- **Problema:** un USER podia acceder a ordenes globales o ajenas.
- **Solucion:** rutas `/users/me` filtran por identidad y ADMIN usa consulta operativa separada.
- **Verificacion:** `historyIsPrivateAndReorderUsesCurrentPriceAndIdempotentKey` y comprobacion HTTP de orden ajena.
- **Salida:** propietario obtiene la orden y otro usuario recibe 403.
- **Estado:** APROBADO 100%.

### TC-24 - Reorden con reglas actuales

- **Problema:** clonar una orden podia reutilizar ID, precio o inventario obsoleto.
- **Solucion:** reorden crea una compra nueva y vuelve a evaluar reglas, precio, cupon e inventario.
- **Verificacion:** `historyIsPrivateAndReorderUsesCurrentPriceAndIdempotentKey` y `concurrentReorderCannotDuplicateTheNewOrder`.
- **Salida:** nueva identidad, precio actual, confirmacion de cambio y concurrencia idempotente.
- **Estado:** APROBADO 100%.

### TC-25 - Flujo de estados de una orden

- **Problema:** el estado podia cambiar sin matriz, version, actor o historial.
- **Solucion:** `WorkflowService` centraliza transiciones, version optimista, motivo, actor, historial y evento.
- **Verificacion:** `entireWorkflowMatrixIsExplicit`, `staleStatusVersionAndFifoClaimAreEnforced`, cancelacion y flujo HTTP de cocina.
- **Salida:** transiciones validas aprobadas; saltos, rol incorrecto y version obsoleta rechazados.
- **Estado:** APROBADO 100%.

### TC-26 - Cola de cocina, claim atomico y ETA

- **Problema:** cocina no tenia FIFO ni proteccion contra doble claim.
- **Solucion:** `KitchenService` usa `findAndModify`, estacion unica y ETA por carga y complejidad.
- **Verificacion:** `kitchenClaimIsAtomicAndStationCannotClaimTwice`, `staleStatusVersionAndFifoClaimAreEnforced` y `kitchenHttpQueueClaimAndWorkflowUseSafeDtos`.
- **Salida:** dos cocineros reclaman ordenes distintas y los DTOs no exponen direccion o pago.
- **Estado:** APROBADO 100%.

### TC-27 - Contrato unico de eventos

- **Problema:** cada transporte tenia un contrato distinto y podia serializar el dominio completo.
- **Solucion:** `tacocloud-messaging-contract` define evento versionado, correlacion y snapshot minimo.
- **Verificacion:** `responseAndEventNeverSerializeSensitiveFieldsAndV1AcceptsExtensions` y `TransportTest`.
- **Salida:** contrato compatible y sin usuario, direccion, password o pago.
- **Estado:** APROBADO 100%.

### TC-28 - Elegir broker en runtime

- **Problema:** cambiar broker requeria editar POM o activaba mas de un adaptador.
- **Solucion:** `ConditionalOnProperty` selecciona noop, JMS, RabbitMQ o Kafka y `TransportGuard` valida exactamente uno.
- **Verificacion:** `exactlyOneAdapterForEveryTransport`, `invalidValueAndProductionNoopFail` y runtime RabbitMQ.
- **Salida:** `TransportTest` 2/0/0/0 y broker real aprobado.
- **Estado:** APROBADO 100%.

### TC-29 - Outbox transaccional

- **Problema:** una orden podia confirmarse sin evento o publicarse sin orden.
- **Solucion:** reserva, orden y outbox comparten transaccion; publisher usa claim, lease, timeout, retry y backoff.
- **Verificacion:** `placePersistsPriceSnapshotInventoryAndExactlyOneOutbox`, `outboxRetriesThenPublishesAndClaimsAreExclusive`, `expiredClaimResumesAndConsumerDeduplicatesDurably` y caida real del broker.
- **Salida:** rollback completo, recuperacion de lease y publicacion posterior a la recuperacion de RabbitMQ.
- **Estado:** APROBADO 100%.

### TC-30 - Consumidor idempotente, retry y DLQ

- **Problema:** redelivery duplicaba efectos y los errores podian reintentarse sin limite.
- **Solucion:** `ProcessedEvent` deduplica; errores transitorios tienen limite y permanentes van a DLQ sanitizada antes del ack.
- **Verificacion:** las 3 pruebas de `RabbitRetryTest`, consumidor transaccional y `rabbitPermanentMessageGoesToSanitizedDlqWithCorrelation`.
- **Salida:** retry exacto, deduplicacion durable, DLQ sin cuerpo sensible y replay administrativo controlado.
- **Estado:** APROBADO 100%.

### TC-31 - Correlation ID de HTTP a evento y logs

- **Problema:** no habia un ID comun entre HTTP, Reactor, broker, DLQ y logs.
- **Solucion:** `CorrelationFilter` valida o genera el ID, lo devuelve, usa MDC y lo propaga por Reactor y outbox.
- **Verificacion:** `correlationIsValidatedReturnedAndMdcIsCleanedEvenOnFailure`, `correlationCrossesReactiveThreadAndRemainsInOutbox` y runtime HTTP/DLQ.
- **Salida:** el mismo ID aparece en respuesta, outbox y DLQ; MDC queda limpio.
- **Estado:** APROBADO 100%.

### TC-32 - Metricas y salud de negocio

- **Problema:** las metricas no explicaban compras, inventario, cocina o backlog.
- **Solucion:** `BusinessMetrics`, `OperationalSnapshot` y `OutboxHealth` exponen counters, timers, gauges y health sin consultas bloqueantes al leer.
- **Verificacion:** `businessMetricsCountCommitsNotReplaysAndGaugeAndHealthReflectStorage`, `metricsIncludeCouponStockFailureAndKitchenLatency` y seguridad Actuator en runtime.
- **Salida:** commits cuentan una vez, replay no duplica, gauges reflejan Mongo y detalles requieren ADMIN.
- **Estado:** APROBADO 100%.

### TC-33 - Anuncios operativos seguros

- **Problema:** Notes era una lista en memoria con indices inestables.
- **Solucion:** anuncios Mongo usan UUID, expiracion, limite concurrente, autor privado y operaciones ADMIN.
- **Verificacion:** `announcementsPersistExpireAndEnforceConcurrentActiveLimit` y `announcementsUseValidatedDurableIdsAndPrivateAuthors`.
- **Salida:** persistencia, vencimiento, limite, autorizacion y ocultamiento de autor aprobados.
- **Estado:** APROBADO 100%. 

### TC-34 - Idempotency-Key en ordenes

- **Problema:** doble clic o retry podia crear dos ordenes y reservar dos veces.
- **Solucion:** key por usuario, hash canonico, registro IN_PROGRESS/COMPLETED y orden se coordinan en transaccion con TTL.
- **Verificacion:** `httpIdempotencyIsScopedCanonicalConcurrentAndAtomic`, `failedIdempotentPlacementRollsBackItsKeyAndCanBeRetried`, fingerprint canonico y runtime HTTP.
- **Salida:** misma key/body conserva orden; body distinto devuelve 409; concurrencia crea una sola orden.
- **Estado:** APROBADO 100%.

### TC-35 - API v1 y contrato OpenAPI

- **Problema:** no habia version publica ni contrato que detectara cambios incompatibles.
- **Solucion:** `/api/v1` es la ruta actual; `/api` queda como alias con deprecacion; `openapi.yaml` documenta DTOs, seguridad, headers y errores.
- **Verificacion:** las 2 pruebas de `OpenApiContractTest`, `legacyAliasAdvertisesDeprecation` y validacion HTTP de `/openapi.yaml`.
- **Salida:** documento valido, schemas rechazan campos incompatibles y alias anuncia deprecacion.
- **Estado:** APROBADO 100%.

### TC-36 - Suite de integracion contra regresiones reales

- **Problema:** las pruebas no cubrian runtime real, seguridad, persistencia, broker o regresiones asincronas.
- **Solucion:** suite con JUnit, StepVerifier, MockMvc, WebTestClient, MongoDB y RabbitMQ Testcontainers y Chrome/Playwright; CI valida reportes y omisiones.
- **Verificacion:** `bash scripts/verificar-retos.sh` y `python3 scripts/verificar-reportes.py`.
- **Salida:** `BUILD SUCCESS`; 68 pruebas; 0 fallos; 0 errores; 0 omitidas; 11 suites criticas.
- **Estado:** APROBADO 100%.

## Archivos principales de evidencia

- API y negocio: `tacocloud/tacocloud-api/src/main/java/tacos/`
- Dominio: `tacocloud/tacocloud-domain-mongodb/src/main/java/tacos/`
- Seguridad: `tacocloud/tacocloud-security/src/main/java/tacos/security/`
- Contrato de eventos: `tacocloud/tacocloud-messaging-contract/`
- OpenAPI: `tacocloud/tacocloud-api/src/main/resources/static/openapi.yaml`
- Pruebas API: `tacocloud/tacocloud-api/src/test/java/`
- Pruebas runtime: `tacocloud/tacocloud/src/test/java/tacos/RuntimeMvcIT.java`
- Verificacion: `scripts/verificar-retos.sh` y `scripts/verificar-reportes.py`
- CI: `.github/workflows/retos.yml`
- Postman: `postman/TacoCloud-36-Retos.postman_collection.json`

## Conclusion de entrega

Los 36 retos estan implementados y aprobados al 100% en el estado actual de la carpeta. La conclusion se basa en codigo inspeccionado, pruebas unitarias, integracion MongoDB, integracion RabbitMQ, runtime HTTP, seguridad, contrato OpenAPI y flujo de navegador. El comando oficial de comprobacion termina con `BUILD SUCCESS` y el validador de reportes confirma 68 pruebas sin fallos, errores ni omisiones.
