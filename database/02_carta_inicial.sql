-- Carta proporcionada por el usuario: 6 categorias, 99 productos.
-- SOLO para esquema nuevo. No borra pedidos, pagos ni carta existente.
BEGIN;
SET LOCAL search_path = public;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM categorias) OR EXISTS (SELECT 1 FROM platos) THEN
        RAISE EXCEPTION 'La carta no esta vacia. No ejecutar este seed sobre datos existentes';
    END IF;
END $$;
INSERT INTO categorias (nombre, descripcion) VALUES
('Alitas', 'Alitas de pollo apanadas o a la parrilla, por cantidad de piezas'),
('Makis', 'Rollos de sushi, por cantidad de piezas'),
('Hamburguesas', 'Hamburguesas simples y dobles'),
('Pizzas', 'Pizzas artesanales por tamano'),
('Broaster', 'Pollo broaster por presa y combos'),
('Bebidas', 'Gaseosas, refrescos y bebidas frias');

-- =============================================
-- 4. ALITAS
-- Sabores: BBQ, Picante Buffalo, Miel Mostaza, Parmesano Ajo,
--          Broaster Natural, Mango Habanero
-- Tamanos: x6, x12, x24, x50
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
-- BBQ
('Alitas BBQ x6',  'Alitas bañadas en salsa BBQ, 6 piezas', 18.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas BBQ x12', 'Alitas bañadas en salsa BBQ, 12 piezas', 34.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas BBQ x24', 'Alitas bañadas en salsa BBQ, 24 piezas', 66.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas BBQ x50', 'Alitas bañadas en salsa BBQ, 50 piezas (para compartir)', 130.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true),
-- Picante Buffalo
('Alitas Picante Buffalo x6',  'Alitas en salsa buffalo picante, 6 piezas', 18.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas Picante Buffalo x12', 'Alitas en salsa buffalo picante, 12 piezas', 34.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas Picante Buffalo x24', 'Alitas en salsa buffalo picante, 24 piezas', 66.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas Picante Buffalo x50', 'Alitas en salsa buffalo picante, 50 piezas (para compartir)', 130.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true),
-- Miel Mostaza
('Alitas Miel Mostaza x6',  'Alitas en salsa de miel y mostaza, 6 piezas', 18.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas Miel Mostaza x12', 'Alitas en salsa de miel y mostaza, 12 piezas', 34.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas Miel Mostaza x24', 'Alitas en salsa de miel y mostaza, 24 piezas', 66.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas Miel Mostaza x50', 'Alitas en salsa de miel y mostaza, 50 piezas (para compartir)', 130.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true),
-- Parmesano Ajo
('Alitas Parmesano Ajo x6',  'Alitas bañadas en salsa de parmesano y ajo, 6 piezas', 19.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas Parmesano Ajo x12', 'Alitas bañadas en salsa de parmesano y ajo, 12 piezas', 36.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas Parmesano Ajo x24', 'Alitas bañadas en salsa de parmesano y ajo, 24 piezas', 70.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas Parmesano Ajo x50', 'Alitas bañadas en salsa de parmesano y ajo, 50 piezas (para compartir)', 138.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true),
-- Broaster Natural (apanada, sin salsa)
('Alitas Broaster Natural x6',  'Alitas apanadas estilo broaster, 6 piezas', 17.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas Broaster Natural x12', 'Alitas apanadas estilo broaster, 12 piezas', 32.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas Broaster Natural x24', 'Alitas apanadas estilo broaster, 24 piezas', 62.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas Broaster Natural x50', 'Alitas apanadas estilo broaster, 50 piezas (para compartir)', 122.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true),
-- Mango Habanero
('Alitas Mango Habanero x6',  'Alitas en salsa de mango con habanero, 6 piezas', 19.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 15, true),
('Alitas Mango Habanero x12', 'Alitas en salsa de mango con habanero, 12 piezas', 36.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 18, true),
('Alitas Mango Habanero x24', 'Alitas en salsa de mango con habanero, 24 piezas', 70.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 22, true),
('Alitas Mango Habanero x50', 'Alitas en salsa de mango con habanero, 50 piezas (para compartir)', 138.00, (SELECT id FROM categorias WHERE nombre='Alitas'), 30, true);

-- =============================================
-- 5. MAKIS
-- Tipos: California, Acevichado, Crispy, Tempura, Filadelfia
-- Tamanos: x10, x20, x30, x40
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
-- California
('Maki California x10', 'Maki california (palta, kanikama, pepino), 10 piezas', 20.00, (SELECT id FROM categorias WHERE nombre='Makis'), 15, true),
('Maki California x20', 'Maki california (palta, kanikama, pepino), 20 piezas', 38.00, (SELECT id FROM categorias WHERE nombre='Makis'), 20, true),
('Maki California x30', 'Maki california (palta, kanikama, pepino), 30 piezas', 54.00, (SELECT id FROM categorias WHERE nombre='Makis'), 25, true),
('Maki California x40', 'Maki california (palta, kanikama, pepino), 40 piezas', 70.00, (SELECT id FROM categorias WHERE nombre='Makis'), 30, true),
-- Acevichado
('Maki Acevichado x10', 'Maki bañado en salsa acevichada, 10 piezas', 22.00, (SELECT id FROM categorias WHERE nombre='Makis'), 15, true),
('Maki Acevichado x20', 'Maki bañado en salsa acevichada, 20 piezas', 42.00, (SELECT id FROM categorias WHERE nombre='Makis'), 20, true),
('Maki Acevichado x30', 'Maki bañado en salsa acevichada, 30 piezas', 60.00, (SELECT id FROM categorias WHERE nombre='Makis'), 25, true),
('Maki Acevichado x40', 'Maki bañado en salsa acevichada, 40 piezas', 78.00, (SELECT id FROM categorias WHERE nombre='Makis'), 30, true),
-- Crispy
('Maki Crispy x10', 'Maki apanado crocante, 10 piezas', 21.00, (SELECT id FROM categorias WHERE nombre='Makis'), 15, true),
('Maki Crispy x20', 'Maki apanado crocante, 20 piezas', 40.00, (SELECT id FROM categorias WHERE nombre='Makis'), 20, true),
('Maki Crispy x30', 'Maki apanado crocante, 30 piezas', 57.00, (SELECT id FROM categorias WHERE nombre='Makis'), 25, true),
('Maki Crispy x40', 'Maki apanado crocante, 40 piezas', 74.00, (SELECT id FROM categorias WHERE nombre='Makis'), 30, true),
-- Tempura
('Maki Tempura x10', 'Maki con camaron tempura, 10 piezas', 23.00, (SELECT id FROM categorias WHERE nombre='Makis'), 18, true),
('Maki Tempura x20', 'Maki con camaron tempura, 20 piezas', 44.00, (SELECT id FROM categorias WHERE nombre='Makis'), 22, true),
('Maki Tempura x30', 'Maki con camaron tempura, 30 piezas', 63.00, (SELECT id FROM categorias WHERE nombre='Makis'), 27, true),
('Maki Tempura x40', 'Maki con camaron tempura, 40 piezas', 82.00, (SELECT id FROM categorias WHERE nombre='Makis'), 32, true),
-- Filadelfia
('Maki Filadelfia x10', 'Maki con queso filadelfia, 10 piezas', 22.00, (SELECT id FROM categorias WHERE nombre='Makis'), 15, true),
('Maki Filadelfia x20', 'Maki con queso filadelfia, 20 piezas', 42.00, (SELECT id FROM categorias WHERE nombre='Makis'), 20, true),
('Maki Filadelfia x30', 'Maki con queso filadelfia, 30 piezas', 60.00, (SELECT id FROM categorias WHERE nombre='Makis'), 25, true),
('Maki Filadelfia x40', 'Maki con queso filadelfia, 40 piezas', 78.00, (SELECT id FROM categorias WHERE nombre='Makis'), 30, true);

-- =============================================
-- 6. HAMBURGUESAS
-- Tipos: Clasica, Doble Carne, BBQ Bacon, Cheddar Especial, Hawaiana
-- Tamanos: Simple / Doble
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
('Hamburguesa Clasica Simple', 'Carne, lechuga, tomate, cebolla y salsas de la casa', 15.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 12, true),
('Hamburguesa Clasica Doble', 'Doble carne, lechuga, tomate, cebolla y salsas de la casa', 22.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 14, true),
('Hamburguesa BBQ Bacon Simple', 'Carne, tocino, queso cheddar y salsa BBQ', 19.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 14, true),
('Hamburguesa BBQ Bacon Doble', 'Doble carne, tocino, queso cheddar y salsa BBQ', 27.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 16, true),
('Hamburguesa Cheddar Especial Simple', 'Carne, doble queso cheddar y cebolla caramelizada', 18.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 13, true),
('Hamburguesa Cheddar Especial Doble', 'Doble carne, doble queso cheddar y cebolla caramelizada', 25.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 15, true),
('Hamburguesa Hawaiana Simple', 'Carne, piña grillada, jamon y queso', 17.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 13, true),
('Hamburguesa Hawaiana Doble', 'Doble carne, piña grillada, jamon y queso', 24.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 15, true),
('Hamburguesa Vegetariana', 'Hamburguesa de vegetales, lechuga, tomate y palta', 17.00, (SELECT id FROM categorias WHERE nombre='Hamburguesas'), 13, true);

-- =============================================
-- 7. PIZZAS
-- Sabores: Americana, Hawaiana, Pepperoni, Vegetariana,
--          Cuatro Quesos, Especial de la Casa
-- Tamanos: Personal, Mediana, Grande, Familiar
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
-- Americana
('Pizza Americana Personal', 'Jamon, salchicha y queso mozzarella - tamaño personal', 15.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 18, true),
('Pizza Americana Mediana',  'Jamon, salchicha y queso mozzarella - tamaño mediano', 25.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Americana Grande',   'Jamon, salchicha y queso mozzarella - tamaño grande', 35.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Americana Familiar', 'Jamon, salchicha y queso mozzarella - tamaño familiar', 45.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 25, true),
-- Hawaiana
('Pizza Hawaiana Personal', 'Jamon, piña y queso mozzarella - tamaño personal', 15.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 18, true),
('Pizza Hawaiana Mediana',  'Jamon, piña y queso mozzarella - tamaño mediano', 25.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Hawaiana Grande',   'Jamon, piña y queso mozzarella - tamaño grande', 35.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Hawaiana Familiar', 'Jamon, piña y queso mozzarella - tamaño familiar', 45.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 25, true),
-- Pepperoni
('Pizza Pepperoni Personal', 'Pepperoni y queso mozzarella - tamaño personal', 16.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 18, true),
('Pizza Pepperoni Mediana',  'Pepperoni y queso mozzarella - tamaño mediano', 27.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Pepperoni Grande',   'Pepperoni y queso mozzarella - tamaño grande', 38.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Pepperoni Familiar', 'Pepperoni y queso mozzarella - tamaño familiar', 49.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 25, true),
-- Vegetariana
('Pizza Vegetariana Personal', 'Pimiento, champiñon, cebolla, aceituna y mozzarella - tamaño personal', 15.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 18, true),
('Pizza Vegetariana Mediana',  'Pimiento, champiñon, cebolla, aceituna y mozzarella - tamaño mediano', 25.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Vegetariana Grande',   'Pimiento, champiñon, cebolla, aceituna y mozzarella - tamaño grande', 35.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Vegetariana Familiar', 'Pimiento, champiñon, cebolla, aceituna y mozzarella - tamaño familiar', 45.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 25, true),
-- Cuatro Quesos
('Pizza Cuatro Quesos Personal', 'Mozzarella, parmesano, queso azul y edam - tamaño personal', 17.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 18, true),
('Pizza Cuatro Quesos Mediana',  'Mozzarella, parmesano, queso azul y edam - tamaño mediano', 29.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Cuatro Quesos Grande',   'Mozzarella, parmesano, queso azul y edam - tamaño grande', 41.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Cuatro Quesos Familiar', 'Mozzarella, parmesano, queso azul y edam - tamaño familiar', 53.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 25, true),
-- Especial de la Casa
('Pizza Especial de la Casa Personal', 'Combinado de carnes, vegetales y mozzarella - tamaño personal', 18.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 20, true),
('Pizza Especial de la Casa Mediana',  'Combinado de carnes, vegetales y mozzarella - tamaño mediano', 30.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 22, true),
('Pizza Especial de la Casa Grande',   'Combinado de carnes, vegetales y mozzarella - tamaño grande', 42.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 24, true),
('Pizza Especial de la Casa Familiar', 'Combinado de carnes, vegetales y mozzarella - tamaño familiar', 55.00, (SELECT id FROM categorias WHERE nombre='Pizzas'), 27, true);

-- =============================================
-- 8. BROASTER
-- Por presa: Pierna, Pecho, Encuentro, Ala
-- Combos: 2, 4 (familiar) y 8 (balde) presas con papas
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
('Broaster 1 Presa - Pierna',    '1 presa de pierna apanada estilo broaster', 14.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 15, true),
('Broaster 1 Presa - Pecho',     '1 presa de pecho apanada estilo broaster', 15.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 15, true),
('Broaster 1 Presa - Encuentro', '1 presa de encuentro apanada estilo broaster', 14.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 15, true),
('Broaster 1 Presa - Ala',       '1 presa de ala apanada estilo broaster', 10.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 12, true),
('Broaster 2 Presas + Papas',    'Combo de 2 presas a elegir con papas fritas y ensalada', 26.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 18, true),
('Broaster 4 Presas Familiar + Papas', 'Combo familiar de 4 presas con papas fritas y ensalada', 48.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 22, true),
('Broaster 8 Presas Balde + Papas',    'Balde de 8 presas con papas fritas y ensalada, para compartir', 90.00, (SELECT id FROM categorias WHERE nombre='Broaster'), 28, true);

-- =============================================
-- 9. BEBIDAS
-- Gaseosas, chicha morada, limonada (normal y frozen),
-- refresco de maracuya, agua, cerveza
-- =============================================
INSERT INTO platos (nombre, descripcion, precio, categoria_id, tiempo_preparacion, disponible) VALUES
-- Gaseosas personales
('Gaseosa Inca Kola Personal', 'Botella personal 500ml', 6.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
('Gaseosa Coca Cola Personal', 'Botella personal 500ml', 6.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
('Gaseosa Pepsi Personal',     'Botella personal 500ml', 6.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
('Gaseosa Guarana Personal',   'Botella personal 500ml', 6.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
-- Gaseosas 1.5L
('Gaseosa Inca Kola 1.5L', 'Botella familiar 1.5 litros', 12.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
('Gaseosa Coca Cola 1.5L', 'Botella familiar 1.5 litros', 12.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 2, true),
-- Chicha morada
('Chicha Morada Vaso',   'Vaso de chicha morada casera', 5.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 3, true),
('Jarra de Chicha Morada 1L', 'Jarra de chicha morada casera, 1 litro', 15.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 4, true),
-- Limonada
('Limonada Vaso',        'Vaso de limonada clasica', 5.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 3, true),
('Jarra de Limonada 1L', 'Jarra de limonada clasica, 1 litro', 15.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 4, true),
('Limonada Frozen Vaso', 'Limonada frozen (frappe), vaso', 8.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 5, true),
-- Refresco de maracuya
('Refresco de Maracuya Vaso',   'Vaso de refresco de maracuya', 5.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 3, true),
('Jarra de Maracuya 1L',        'Jarra de refresco de maracuya, 1 litro', 15.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 4, true),
-- Otras
('Agua Mineral Personal', 'Botella de agua mineral 500ml, con o sin gas', 4.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 1, true),
('Cerveza Personal',      'Botella de cerveza personal', 10.00, (SELECT id FROM categorias WHERE nombre='Bebidas'), 1, true);

-- =============================================
-- 10. VERIFICACION
-- =============================================
SELECT c.nombre AS categoria, COUNT(p.id) AS cantidad_platos
FROM categorias c
LEFT JOIN platos p ON p.categoria_id = c.id
GROUP BY c.nombre
ORDER BY c.nombre;

SELECT nombre, precio, disponible FROM platos ORDER BY categoria_id, precio;

COMMIT;

