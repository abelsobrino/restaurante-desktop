# La Fonda V4

## Administrador > Gestión

1. **Ingredientes / recetas**: alta, edición, activación/desactivación, unidad, stock mínimo y modo de consumo automático/manual. Recetas editables por plato con agregar/actualizar/eliminar ingrediente.
2. **Inventario / almacén**: stock actual, unidad, mínimo, alertas, ajustes manuales y trazabilidad de movimientos. Exporta PDF y Excel.
3. **Stock platos / producción**: todos los días parte de 0. Al preparar stock se descuentan ingredientes automáticos de la receta. Sin stock el plato no aparece al mozo. Muestra producción e insumos preparados/en cocina. Exporta PDF y Excel.
4. **Proveedores / compras**: proveedores editables, referencias de abastecimiento, compra de varios productos, historial y voucher PDF/Excel. Finalizar compra incrementa inventario y el monto aparece como egreso en caja/reportes.
5. **Reportes / caja**: día, mes, año, rango de fechas y comparación. Ventas, compras, gastos, resultado del periodo, saldo acumulado, medios de pago, platos vendidos y movimientos de ingresos/egresos. PDF/Excel.

## Operación

- Cocina: cabecera corregida para no repetir Código/Mesa/Fecha/Total.
- Caja: pedidos web pagados solo se consultan/imprimen; no se vuelven a cobrar. Se redujeron consultas repetidas.
- Mozo: los platos con stock 0 no aparecen; consulta de stock por lote y caché de catálogo.
- Login: botón **Regresar**.
- Rendimiento: SQL de Hibernate desactivado en producción, pool Hikari ajustado e índices nuevos.

## Base de datos

- Base existente: ejecutar `database/07_mejoras_gestion_v4.sql`.
- Base nueva: ejecutar `database/00_instalacion_completa.sql`.
- No ejecutar `04` nuevamente encima de una base V4.
