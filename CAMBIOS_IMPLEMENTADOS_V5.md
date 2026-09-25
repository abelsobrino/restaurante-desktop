# La Fonda V5

- Stock de platos acumulativo con `agregar_stock_diario(...)`: cada cantidad ingresada se suma a Preparado y Disponible.
- Las ventas realizadas no cambian al agregar nueva producción.
- El inventario descuenta únicamente los ingredientes automáticos de las porciones nuevas.
- Cada adición de producción queda registrada en `plato_stock_movimientos`.
- Pantallas de Stock e Opciones actualizadas para indicar "Cantidad a agregar".
- PDF de tablas rediseñado con A4 horizontal, encabezados, bordes, filas y paginación real.
- Excel de tablas mejorado con título, encabezados destacados, bordes, anchos de columna, filtros y encabezado congelado.
- Migración incremental: `database/08_stock_acumulativo_v5.sql`.
