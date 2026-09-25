-- =============================================================
-- LA FONDA - INVENTARIO INICIAL + RECETAS BASE
-- Ejecutar DESPUES de 04_inventario_proveedores_reportes.sql.
-- Idempotente: fija un piso de stock inicial, no suma en cada ejecución.
-- IMPORTANTE: NO habilita platos para venta. Cada día el stock de platos
-- comienza en 0 y el administrador debe habilitar la producción del día.
-- Aceite se deja como insumo MANUAL: no forma parte de recetas automáticas.
-- =============================================================
BEGIN;
SET LOCAL search_path=public;

INSERT INTO proveedores(nombre,telefono,direccion)
SELECT v.nombre,v.telefono,v.direccion FROM (VALUES
('Avícola La Fonda','999000101','Lima'),
('Mercado de Verduras','999000202','Lima'),
('Distribuidora de Lácteos y Pizzas','999000303','Lima'),
('Distribuidora de Bebidas','999000404','Lima'),
('Insumos Nikkei','999000505','Lima')
) AS v(nombre,telefono,direccion)
WHERE NOT EXISTS (SELECT 1 FROM proveedores p WHERE lower(p.nombre)=lower(v.nombre));

INSERT INTO ingredientes(nombre,categoria,unidad_base,stock_actual,stock_minimo,costo_promedio) VALUES
('Alitas de pollo','Pollo','UNIDAD',240,48,1.20),
('Papa','Verduras','GRAMO',30000,5000,0.0045),
('Carne hamburguesa','Carnes','UNIDAD',80,15,3.50),
('Pan hamburguesa','Panadería','UNIDAD',80,15,1.20),
('Masa pizza','Panadería','UNIDAD',50,10,3.00),
('Queso mozzarella','Lácteos','GRAMO',12000,2000,0.025),
('Jamón','Carnes','GRAMO',6000,1000,0.020),
('Pepperoni','Carnes','GRAMO',4000,700,0.035),
('Arroz sushi','Makis','GRAMO',15000,2500,0.010),
('Nori','Makis','UNIDAD',120,20,1.00),
('Kanikama','Makis','GRAMO',5000,800,0.030),
('Palta','Verduras','GRAMO',7000,1200,0.015),
('Salsa BBQ','Salsas','MILILITRO',5000,800,0.012),
('Salsa Buffalo','Salsas','MILILITRO',4000,700,0.014),
('Salsa Miel Mostaza','Salsas','MILILITRO',4000,700,0.014),
('Salsa Mango Habanero','Salsas','MILILITRO',3000,500,0.018),
('Salsa Acevichada','Salsas','MILILITRO',4000,700,0.018),
('Aceite de fritura','Insumo manual','MILILITRO',20000,4000,0.008),
('Gaseosa personal','Bebidas','UNIDAD',72,18,3.00),
('Gaseosa 1.5L','Bebidas','UNIDAD',36,8,6.00),
('Agua mineral personal','Bebidas','UNIDAD',36,8,2.00)
ON CONFLICT (nombre) DO UPDATE SET
  stock_actual=GREATEST(ingredientes.stock_actual,EXCLUDED.stock_actual),
  stock_minimo=EXCLUDED.stock_minimo,
  costo_promedio=CASE WHEN ingredientes.costo_promedio=0 THEN EXCLUDED.costo_promedio ELSE ingredientes.costo_promedio END,
  activo=true;

-- ALITAS: piezas + papa + salsa correspondiente. Aceite NO se descuenta automáticamente.
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x6$' THEN 6 WHEN p.nombre ~* 'x12$' THEN 12 WHEN p.nombre ~* 'x24$' THEN 24 WHEN p.nombre ~* 'x50$' THEN 50 END
FROM platos p JOIN ingredientes i ON i.nombre='Alitas de pollo'
WHERE p.nombre ~* '^Alitas .* x(6|12|24|50)$'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x6$' THEN 70 WHEN p.nombre ~* 'x12$' THEN 100 WHEN p.nombre ~* 'x24$' THEN 180 WHEN p.nombre ~* 'x50$' THEN 350 END
FROM platos p JOIN ingredientes i ON i.nombre='Papa'
WHERE p.nombre ~* '^Alitas .* x(6|12|24|50)$'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

-- Salsas de alitas (solo las que se pueden medir por porción).
WITH mapa(patron, ingrediente) AS (VALUES
 ('BBQ','Salsa BBQ'),('Picante Buffalo','Salsa Buffalo'),('Miel Mostaza','Salsa Miel Mostaza'),('Mango Habanero','Salsa Mango Habanero')
)
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x6$' THEN 35 WHEN p.nombre ~* 'x12$' THEN 60 WHEN p.nombre ~* 'x24$' THEN 110 WHEN p.nombre ~* 'x50$' THEN 220 END
FROM mapa m JOIN platos p ON p.nombre ILIKE 'Alitas '||m.patron||' x%'
JOIN ingredientes i ON i.nombre=m.ingrediente
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

-- MAKIS: receta base controlable por cantidad de piezas.
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x10$' THEN 180 WHEN p.nombre ~* 'x20$' THEN 360 WHEN p.nombre ~* 'x30$' THEN 540 WHEN p.nombre ~* 'x40$' THEN 720 END
FROM platos p JOIN ingredientes i ON i.nombre='Arroz sushi'
WHERE p.nombre ~* '^Maki .* x(10|20|30|40)$'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x10$' THEN 1 WHEN p.nombre ~* 'x20$' THEN 2 WHEN p.nombre ~* 'x30$' THEN 3 WHEN p.nombre ~* 'x40$' THEN 4 END
FROM platos p JOIN ingredientes i ON i.nombre='Nori'
WHERE p.nombre ~* '^Maki .* x(10|20|30|40)$'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ~* 'x10$' THEN 35 WHEN p.nombre ~* 'x20$' THEN 70 WHEN p.nombre ~* 'x30$' THEN 105 WHEN p.nombre ~* 'x40$' THEN 140 END
FROM platos p JOIN ingredientes i ON i.nombre='Salsa Acevichada'
WHERE p.nombre ILIKE 'Maki Acevichado x%'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

-- PIZZAS: 1 masa por pizza + mozzarella según tamaño.
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,1 FROM platos p JOIN ingredientes i ON i.nombre='Masa pizza'
WHERE p.nombre ILIKE 'Pizza %'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,
 CASE WHEN p.nombre ILIKE '% Personal' THEN 100 WHEN p.nombre ILIKE '% Mediana' THEN 180 WHEN p.nombre ILIKE '% Grande' THEN 260 WHEN p.nombre ILIKE '% Familiar' THEN 350 END
FROM platos p JOIN ingredientes i ON i.nombre='Queso mozzarella'
WHERE p.nombre ILIKE 'Pizza %' AND (p.nombre ILIKE '% Personal' OR p.nombre ILIKE '% Mediana' OR p.nombre ILIKE '% Grande' OR p.nombre ILIKE '% Familiar')
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

-- HAMBURGUESAS: pan + carne; dobles consumen dos carnes.
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,1 FROM platos p JOIN ingredientes i ON i.nombre='Pan hamburguesa'
WHERE p.nombre ILIKE 'Hamburguesa %'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,CASE WHEN p.nombre ILIKE '% Doble%' OR p.nombre ILIKE '% Doble' THEN 2 ELSE 1 END
FROM platos p JOIN ingredientes i ON i.nombre='Carne hamburguesa'
WHERE p.nombre ILIKE 'Hamburguesa %' AND p.nombre NOT ILIKE '%Vegetariana%'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

-- Bebidas embotelladas: control por unidad.
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,1 FROM platos p JOIN ingredientes i ON i.nombre='Gaseosa personal'
WHERE p.nombre ILIKE 'Gaseosa % Personal'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,1 FROM platos p JOIN ingredientes i ON i.nombre='Gaseosa 1.5L'
WHERE p.nombre ILIKE 'Gaseosa % 1.5L'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;
INSERT INTO plato_ingredientes(plato_id,ingrediente_id,cantidad_base)
SELECT p.id,i.id,1 FROM platos p JOIN ingredientes i ON i.nombre='Agua mineral personal'
WHERE p.nombre ILIKE 'Agua Mineral Personal'
ON CONFLICT(plato_id,ingrediente_id) DO UPDATE SET cantidad_base=EXCLUDED.cantidad_base;

COMMIT;

-- Verificación rápida
SELECT nombre,categoria,unidad_base,stock_actual,stock_minimo FROM ingredientes ORDER BY categoria,nombre;
SELECT p.nombre AS plato,i.nombre AS ingrediente,pi.cantidad_base,i.unidad_base
FROM plato_ingredientes pi JOIN platos p ON p.id=pi.plato_id JOIN ingredientes i ON i.id=pi.ingrediente_id
ORDER BY p.nombre,i.nombre;
