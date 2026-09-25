# Instalación nueva: La Fonda (escritorio + estructura web futura)

## IMPORTANTE

Este esquema es para una base VACÍA, no para actualizar tu Supabase actual.
No se ha conectado ni modificado tu base remota. Tampoco se han subido cambios a GitHub.
Guarda una copia del proyecto y de tus datos antes de cambiar de entorno.

El `menu_seed.sql` original borraba pagos, pedidos, detalles, platos y categorías.
La versión entregada elimina esas instrucciones de reset: conserva únicamente
la carta suministrada con sus precios y descripciones. No ejecutes el archivo original.

## Orden de instalación

1. Crea una base vacía en PostgreSQL 14+ o un proyecto Supabase nuevo. El script no
   contiene CREATE DATABASE ni elimina/reemplaza bases.
2. Ejecuta SOLO `00_instalacion_completa.sql`. En esta versión incluye el esquema base, la carta y al final la extensión de inventario, proveedores, platos con opciones y reportes.
   Se detiene si ya existe una tabla del esquema base; no usa DROP TABLE, TRUNCATE ni borra ventas.
   Alternativa para instalación por partes: `01_esquema_nuevo.sql`, luego `02_carta_inicial.sql` y finalmente `04_inventario_proveedores_reportes.sql`.
   No combines ambas rutas indiscriminadamente.
3. Opcional: `03_mesas_opcionales.sql` crea 25 mesas si no hay ninguna.
   Puedes omitirlo y crearlas desde administrador.
4. Configura la conexión JDBC en `application.properties` para la NUEVA base,
   con el servidor/usuario que te entregue PostgreSQL o Supabase. La clave sigue
   fuera del código, en DB_PASSWORD. El usuario debe ser un rol backend privado
   autorizado: las tablas tienen RLS sin políticas públicas. Para la primera
   prueba se puede usar el propietario del esquema, no una clave web pública.
5. Abre este proyecto actualizado con JDK 21. En la ejecución de IntelliJ define
   `ADMIN_INICIAL_DNI` (8 dígitos), `ADMIN_INICIAL_CLAVE` (mínimo 8 caracteres),
   `ADMIN_INICIAL_NOMBRE` y `ADMIN_INICIAL_APELLIDO`. No hay una contraseña real
   predefinida en el SQL ni un usuario administrador compartido.
6. Ejecuta la app SIN el perfil demo: si usuarios está vacía, crea el primer
   administrador y calcula su hash BCrypt. Entra con ese DNI y contraseña.
   Retira las variables ADMIN_INICIAL_* después del primer inicio.
7. Registra al personal desde administrador. Comprueba mozo, cocina, caja y ventas
   con datos de prueba ANTES de usar la base para operaciones reales.

`ddl-auto=validate` evita que Hibernate invente/cambie el esquema real. Si faltan
tablas/columnas, muestra un error de validación: aplica el esquema correcto, no
cambies a update para ocultar el problema. Esta entrega no es compatible sin
migración con la base antigua: añade columnas y cambia la política de contraseñas.

Para `psql`, con conexión configurada a la base nueva y clave solicitada fuera del comando:

```bash
psql -v ON_ERROR_STOP=1 -h SERVIDOR -U USUARIO -d BASE_NUEVA -f database/00_instalacion_completa.sql
```


## Si la base YA existe y tiene ventas

No vuelvas a ejecutar `00_instalacion_completa.sql` ni `01_esquema_nuevo.sql`. Haz respaldo y ejecuta solamente:

```text
database/04_inventario_proveedores_reportes.sql
```

La migración agrega inventario, recetas, proveedores, compras, gastos, stock diario y familias de platos; además instala los triggers que descuentan/reponen stock. Usa `CREATE TABLE IF NOT EXISTS` y no elimina pedidos ni pagos existentes.

`05_datos_ejemplo_inventario_OPCIONAL.sql` es solo para pruebas y no es necesario en una base real.

## Módulos administrativos nuevos

Desde Administrador → **GESTIÓN** se abren cuatro módulos:

- **Platos con opciones:** agrupa variantes. Ej.: Makis acevichados → x6/x12/x24; Pizza → Personal/Mediana/Familiar. Cada opción sigue siendo un `platos.id` normal para conservar cocina, caja e historial.
- **Inventario y recetas:** ingredientes en unidades base (`GRAMO`, `MILILITRO`, `UNIDAD`), stock mínimo, ajustes, receta por plato y stock diario.
- **Proveedores y compras:** proveedores, precios de referencia y compras que incrementan inventario y costo promedio.
- **Reportes y caja:** rangos de fecha, comparación de periodos, ventas, subtotal, IGV, descuentos, métodos de pago, compras, gastos y exportación PDF/XLSX.

El descuento real de ingredientes ocurre en PostgreSQL al insertar `pedido_detalles`, no solo en Java. Por eso los pedidos presenciales y cualquier backend web que inserte correctamente detalles comparten la misma regla de stock. Al cancelar una cuenta abierta, los detalles se cancelan antes que la cabecera y el stock consumido se repone en la misma transacción.

## Carta real conservada

| Categoría | Productos/variantes |
| --- | ---: |
| Alitas | 24 |
| Makis | 20 |
| Hamburguesas | 9 |
| Pizzas | 24 |
| Broaster | 7 |
| Bebidas | 15 |
| Total | 99 |

Cada sabor/tamaño/cantidad se mantiene como un plato vendible, compatible con tu
Java actual. `platos.categoria_id` es obligatorio y referencia categorías reales.
La carta del mozo muestra esas categorías automáticamente. No se cambió ningún precio.
Las seis categorías no se confunden con los cinco ejemplos del modo demo.

## Personal y acceso

| Rol | Acceso | Contraseña almacenada |
| --- | --- | --- |
| MOZO | Solo DNI, trabajador activo | NULL |
| CAJERO | DNI + contraseña, trabajador activo | BCrypt, costo 12 |
| ADMIN | DNI + contraseña, trabajador activo | BCrypt, costo 12 |
| COCINERO | Rol reservado; la pantalla cocina sigue con el acceso anterior | Hash si se registra una cuenta |

- NUEVO limpia la selección; un DNI duplicado no sobrescribe al empleado existente.
- Seleccionar un empleado permite editarlo, pero no cambiar su DNI.
- Nombre y apellido obligatorios, hasta 100 caracteres; DNI de exactamente 8 dígitos.
- Correo opcional para personal. Se normaliza y no se repite, aunque cambien las mayúsculas.
- Admin/caja requieren clave al crearse. Al editar, una clave vacía conserva el hash.
  Cambiar de mozo a otro rol exige contraseña. Cambiar a mozo elimina la clave.
- Contraseñas: mínimo 8 caracteres, máximo 72 bytes UTF-8 para BCrypt, sin recortar espacios.
- Los errores de formato, duplicados y conexión muestran mensajes comprensibles,
  no mensajes SQL ni trazas con detalles internos.
- BAJA / REACTIVAR cambia activo: no elimina pedidos ni nombres de empleados históricos.
- La app impide darse de baja a uno mismo o dejar sin administrador activo.
- Un mozo inactivo tampoco puede entrar aunque conozca su DNI.

La validación de roles de este escritorio NO sustituye autorización de un servidor.
El acceso solo con DNI no es autenticación segura para Internet; consérvalo únicamente
en la operación interna solicitada. No conectes el navegador usando credenciales JDBC.

La demo usa admin 11111111 / caja 22222222 con clave **demo12345**; mozo 33333333 solo DNI.
Si ya usaste una demo anterior con texto plano, cierra la app y renombra `.local-demo`
a una carpeta de respaldo: al reiniciar se generarán cuentas demo con hashes.

## Relaciones: esquema base + 14 tablas administrativas nuevas

| Área | Tablas y relaciones |
| --- | --- |
| Personal | usuarios → pedidos.usuario_id (mozo), pagos.usuario_id (cajero) |
| Carta | categorias → platos → platos_imagenes |
| Operación | mesas → pedidos → pedido_detalles → platos; pedidos → pagos |
| Cuentas web | clientes → clientes_identidades / clientes_direcciones / clientes_tokens |
| Pedidos web | clientes → pedidos; pedidos → pedidos_entregas / pagos_intentos / pedido_eventos |
| Opiniones | clientes + pedidos + platos → resenas → resenas_imagenes |
| Inventario | ingredientes ↔ plato_ingredientes; plato_stock_diario; inventario_movimientos |
| Abastecimiento | proveedores ↔ proveedor_ingredientes; compras → compra_detalles |
| Variantes | plato_familias → plato_familia_variantes → platos |
| Gestión | gastos, mermas y caja_cierres |

Los FK impiden borrar personal, platos o clientes con historial comercial.
Para retirar un plato de la carta, usa disponible=false. No borres ventas para
resolver una restricción. Las eliminaciones en cascada se limitan a metadatos
dependientes como imágenes/direcciones, no a pedidos o pagos.

## Preparación para la futura web (NO implementada todavía)

- Correo de cliente único, incluyendo Gmail u otros proveedores. `clientes.password`
  contiene un hash de la clave de TU sistema, nunca la contraseña de Gmail.
- Google/Supabase OAuth se vincula por (proveedor, sujeto); no por confiar en un
  correo recibido del navegador. Una cuenta externa puede tener password=NULL.
  La verificación del token y la vinculación de identidades serán responsabilidad del backend.
- Direcciones múltiples y copia de la dirección de entrega en cada pedido.
- Pedidos WEB de RECOJO/DELIVERY pueden existir sin mesa y sin mozo, vinculados
  a cliente y a una clave UUID de idempotencia que genera/controla el backend.
- Un intento repetido con la misma clave no duplica el pedido. El backend deberá
  recuperar el resultado anterior y comprobar que el contenido coincide.
- `pagos` representa un pago confirmado por pedido (modelo actual de cobro completo).
  `pagos_intentos` admite intentos fallidos, pendientes o aprobados y referencias de proveedor.
  No almacenes tarjetas, CVV ni secretos de pasarela. Aún no hay pasarela ni reembolsos.
- Por compatibilidad, el estado de pedido ENTREGADO sigue significando cerrado/cobrado
  en escritorio. El avance logístico de delivery usa `pedidos_entregas.estado` por separado.
- La confirmación de un pago futuro debe ser una transacción en el backend, como
  CobroService: insert pago y cierre del pedido; no confiar en un botón o callback del navegador.
- Imágenes: storage_key/URL para Storage u otro almacén de archivos, no fotos binarias
  ni URLs firmadas de corta duración dentro de PostgreSQL. El backend debe validar
  propietario, formato/tamaño y permisos de acceso antes de aceptar el archivo.
- Reseñas: 1 a 5 estrellas, moderación; requieren un pedido pagado del mismo cliente
  y, si se indica plato, ese plato debe pertenecer al pedido.
- Tokens: solo hashes SHA-256 de tokens aleatorios de alta entropía, expiración/uso.
  No se implementaron envío de correos, recuperación, sesiones ni OAuth.
- RLS habilitado sin políticas para anon/authenticated y permisos retirados sobre
  estas tablas. La futura publicación requiere políticas o una API autorizada.
  No otorgues lectura pública sobre usuarios/clientes/pagos para que la web funcione.

## Importes, auditoría y compatibilidad

Los precios de la carta incluyen el 18% solicitado. `pedido_detalles.subtotal` es
el importe bruto de cada línea; `pedidos.subtotal` es la base sin IGV.
Un trigger normaliza subtotal/IGV de cabecera incluso si el escritorio manda el total
en ambos campos, como hacía antes. Total final = suma de líneas + envío - descuento;
el trigger de pagos verifica esa igualdad al cobrar.

Snapshots de nombres de plato/mozo y número de mesa permiten reimprimir aunque
se renombren los catálogos después del pago. Los importes se conservan en el detalle.
La app usa estos campos nuevos para el PDF. No es emisión electrónica SUNAT.

Se impiden cambios de pedido/detalle finalizado y edición/borrado de pagos confirmados.
Cambios de estado quedan en pedido_eventos. El índice parcial impide dos pedidos
locales abiertos simultáneamente en una mesa. Los índices cubren carta por categoría,
colas por estado, pedidos de cliente/mesa, ventas por fecha y relaciones principales.

Se mantienen timestamp sin zona en las 8 entidades Java actuales para compatibilidad
con LocalDateTime. Define America/Lima en la aplicación y sesión backend; las nuevas
tablas web usan timestamptz. Una política UTC uniforme para todas las entidades requerirá
una migración explícita cuando se implemente el backend web.

Esto prepara estructura e integridad; no garantiza una cifra de usuarios concurrentes.
La capacidad real dependerá de consultas, pool de conexiones, servidor y pruebas de carga.
La interfaz de caja aún es por mesas; gestionar los cobros web sin mesa será parte de la futura integración.

## Verificación de esta entrega

- SQL ejecutado en PostgreSQL embebido (PGlite), NO en Supabase.
- Esquema, 99 productos y 25 mesas opcionales cargados correctamente.
- Probados pedidos local/web, pago, cálculo IGV, snapshots y reseña válida.
- 13 pruebas negativas pasaron: DNI inválido, clave en texto plano, correo duplicado,
  mesa con dos pedidos, monto incorrecto, cobro duplicado, alteración de detalle pagado,
  reapertura de pedido pagado, borrado de mozo con historial, web sin idempotencia,
  reseña ajena, plato no comprado y calificación fuera de rango.
- Sintaxis Java/FXML revisada. Se añadieron pruebas JUnit del registro/autenticación.
- Compilación completa, suite JUnit y ejecución visual de JavaFX siguen pendientes
  en JDK 21 con Maven disponible. Las pruebas SQL embebidas no equivalen a validar
  la configuración particular, permisos y conexión de tu instalación Supabase.

## V4 - gestión de inventario, stock y caja

Si tu base ya estaba funcionando con la migración 04, ejecuta **solo** `07_mejoras_gestion_v4.sql` en Supabase SQL Editor.

La V4 agrega `ingredientes.descuento_automatico`, permite usar unidades base UNIDAD/GRAMO/KILOGRAMO/MILILITRO/LITRO, recrea de forma segura las vistas de stock, optimiza índices y reemplaza `configurar_stock_diario(...)`. Los ingredientes automáticos se descuentan cuando se prepara/aumenta el stock de un plato; los ingredientes manuales (por ejemplo aceite) se descuentan con ajustes de inventario cuando corresponda.

Para una base nueva puedes ejecutar `00_instalacion_completa.sql`, que ya incluye la V4 al final.

No se eliminan columnas compartidas con la web. Campos como `plato_nombre`, `mesa_numero`, `mozo_nombre` e `inventario_reservado` se conservan porque sirven como snapshots operativos/auditoría y para evitar romper compatibilidad entre escritorio y web.

## V5 - Stock acumulativo

Si ya tenías instalada la V4, ejecuta `08_stock_acumulativo_v5.sql` una sola vez.

Desde V5, el sistema usa `agregar_stock_diario(...)` y el valor escrito en **Stock Platos** es una cantidad adicional. Ejemplo: Preparado 5 / Disponible 3 / Vendido 2; al ingresar 4 pasa a Preparado 9 / Disponible 7 / Vendido 2. Solo se descuentan del inventario los insumos automáticos necesarios para las 4 porciones agregadas.
