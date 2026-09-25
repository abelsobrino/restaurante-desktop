# La Fonda — integración V3

## Regla de stock diario
- Cada día un plato comienza con **stock vendible 0**. Una fila de ayer nunca habilita ventas hoy.
- El administrador debe entrar a **Gestión → Inventario y recetas → Stock diario de platos** y habilitar las porciones preparadas para ese día.
- Sin stock de hoy, Mozo muestra el plato/variante agotado y la BD también rechaza un detalle presencial o web. La protección no depende solo de la pantalla.

## Receta e inventario
- Cada plato/variante puede tener ingredientes controlables. Ejemplo: Alitas BBQ x12 = 12 alitas + papa + salsa.
- Al **aumentar el stock diario** se descuentan inmediatamente del almacén los ingredientes de la receta: se entiende que esas porciones fueron preparadas/habilitadas.
- Al vender, solo baja la cantidad de porciones del stock diario; los ingredientes no se descuentan dos veces.
- Al reducir porciones no vendidas, el sistema devuelve al almacén la diferencia de receta.
- Insumos de uso general que no conviene calcular por plato (por ejemplo aceite de freidora) se dejan fuera de la receta y se registran con **Ajuste manual** cuando corresponda.

## Proveedores / almacén
- Una compra permite registrar cantidad en KG, G, L, ML o UNIDAD según el ingrediente.
- Ejemplo: 20 KG de papa se convierten internamente en 20 000 g.
- Se conserva la cantidad/unidad original de compra y también la cantidad base para recetas.
- Registrar una compra incrementa el inventario y actualiza el costo promedio y el precio de referencia del proveedor.

## Alerta de stock bajo
Al entrar al Administrador aparece una advertencia si hay ingredientes con `stock_actual <= stock_minimo`, mostrando hasta 8 de ellos y cuántos adicionales existen.

## Pedidos web
- Cocina diferencia `WEB DELIVERY` y `WEB RECOJO` de una mesa presencial.
- Para delivery muestra teléfono y dirección de entrega; para recojo muestra teléfono.
- Caja tiene un selector **PEDIDOS WEB PAGADOS**. Solo lista pedidos web que ya tienen un registro en `pagos`, por lo que el botón COBRAR queda deshabilitado y se usa `PDF / IMPRIMIR`.
- El comprobante de un pedido web incluye tipo, cliente, teléfono y dirección si es delivery.
- El esquema ya tenía `cliente_telefono`, `direccion_entrega`, `tipo`, `origen` y pagos `ONLINE`; no se inventó una segunda estructura paralela.

## PDF de reportes
Después de guardar un reporte PDF el sistema intenta abrirlo con la aplicación PDF predeterminada del equipo. Si Java Desktop no está disponible, en Windows intenta abrirlo mediante el sistema.

## SQL para una BD que YA existe
Ejecutar en este orden:
1. `database/04_inventario_proveedores_reportes.sql` (se puede ejecutar sobre la versión anterior; actualiza funciones/vistas y agrega columnas faltantes).
2. `database/06_inventario_inicial_y_recetas.sql` si quieres la carga inicial propuesta de almacén y recetas.

`06` **no habilita platos para venta**. Después debes definir el stock diario que realmente prepararás hoy.

## SQL para una BD totalmente nueva
Usa `database/00_instalacion_completa.sql`.

## Verificación recomendada
1. Entrar como Admin.
2. Abrir Inventario: verificar existencias y recetas.
3. Intentar pedir desde Mozo sin stock diario: debe estar agotado.
4. Habilitar, por ejemplo, 5 porciones de un plato con receta: el inventario debe bajar según 5 recetas.
5. Vender 1: el stock diario debe bajar a 4, sin volver a descontar ingredientes.
6. Registrar 20 KG de papa desde Proveedores: el almacén debe aumentar 20 000 g.
7. Bajar un ingrediente hasta su mínimo, salir y volver a entrar como Admin: debe aparecer la alerta.
8. Generar un PDF desde Reportes: debe guardarse e intentar abrirse.
9. Crear/usar un pedido WEB pagado: debe aparecer en Cocina y en Caja → Pedidos web pagados.
