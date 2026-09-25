# La Fonda — integración V2 aplicada sobre tu proyecto

Este ZIP ya está modificado sobre la copia real entregada. No necesitas copiar el parche anterior.

## Qué quedó integrado

- `AdminView.fxml`: nueva pestaña **GESTIÓN** con accesos a Platos con opciones, Inventario y recetas, Proveedores y compras, Reportes y caja.
- `AdminController.java`: métodos que abren esas cuatro ventanas usando Spring como factory de controladores; la tabla tradicional de PLATOS oculta las variantes agrupadas para evitar editarlas por accidente. Compras y gastos conservan el `usuario_id` del administrador que abrió el módulo.
- `MozoController.java`: carta agrupada por familia, selector de variante, stock visible y productos agotados en gris; conserva la lógica de adicionales.
- `PedidoService.java`: validación amigable de stock antes de persistir; el descuento definitivo queda en PostgreSQL.
- Nuevos controladores/servicios/FXML para inventario, proveedores, familias de platos y reportes. Los FXML nuevos usan el mismo namespace JavaFX/FXML que el proyecto existente.
- `database/04_inventario_proveedores_reportes.sql`: migración para una BD existente.
- `database/00_instalacion_completa.sql`: para una BD nueva ya incorpora la extensión V2 al final.
- `database/05_datos_ejemplo_inventario_OPCIONAL.sql`: datos genéricos de prueba, opcionales.

## Base existente

Haz respaldo y ejecuta solo:

```text
database/04_inventario_proveedores_reportes.sql
```

No vuelvas a ejecutar el esquema base sobre una BD con ventas.

## Base nueva

Ejecuta:

```text
database/00_instalacion_completa.sql
```

Opcionalmente después:

```text
database/03_mesas_opcionales.sql
```

## Flujo recomendado de prueba

1. Inicia como administrador y abre la pestaña GESTIÓN.
2. En Inventario crea un ingrediente, por ejemplo `Alita de pollo`, unidad `UNIDAD`.
3. Registra un proveedor y una compra para cargar stock.
4. Crea `Alitas BBQ` en Platos con opciones y agrega `x6` y `x12`.
5. En Inventario asigna recetas diferentes a cada variante.
6. Configura stock diario de cada variante.
7. Como mozo, entra a la categoría: debe aparecer una sola tarjeta `Alitas BBQ` y al seleccionarla debe pedir `x6`/`x12`.
8. Envía un pedido y verifica que bajen stock diario e ingredientes.
9. Anula una cuenta de prueba antes de caja y verifica que el stock se reponga.
10. Cobra una venta y revisa Reportes y caja; prueba PDF y Excel.

## Verificaciones realizadas aquí

- Todos los FXML del proyecto se analizaron como XML válido.
- Se verificó que cada `onAction="#metodo"` de los FXML apunte a un método presente en el controlador correspondiente.
- Se hizo una pasada de `javac` sobre todas las fuentes para detectar errores de sintaxis; no aparecieron diagnósticos de sintaxis. La comprobación de tipos completa requiere las dependencias Maven/JavaFX.
- Los seis servicios nuevos y `PedidoService` se compilaron de forma aislada contra stubs de sus dependencias para comprobar firmas y tipos; pasaron esa verificación.
- El exportador `.xlsx` se ejecutó con un reporte de prueba y se validaron correctamente las entradas/XML internas del archivo generado. El PDF usa paginación automática para no cortar meses largos e incluye medios de pago y top de platos.
- Se intentó ejecutar `mvnw clean test`, pero este entorno no tiene acceso de red para descargar Maven/dependencias, por lo que la suite completa debe ejecutarse en tu PC.
- La migración se revisó para no usar `DROP TABLE`, `TRUNCATE` ni `DELETE FROM` masivos.
- Se corrigió la interacción de cancelación con el trigger `detalle_proteger` de tu esquema: la cancelación de detalles ocurre antes de cambiar la cabecera a `CANCELADO`, permitiendo reponer inventario sin violar la protección existente.
- Los triggers de inventario usan `SECURITY DEFINER` con `search_path` fijado para poder aplicar el descuento centralizado desde flujos backend/web autorizados sin exponer directamente las tablas administrativas.

## Comandos en tu PC

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run
```

Si el proyecto conecta a una BD existente, ejecuta primero la migración `04`.
