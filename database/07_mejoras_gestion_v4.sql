-- La Fonda V4 - mejoras de inventario, stock, reportes y rendimiento
-- Ejecutar UNA VEZ en Supabase SQL Editor sobre una BD que ya tenga 04_inventario_proveedores_reportes.sql.

BEGIN;

ALTER TABLE public.ingredientes
    ADD COLUMN IF NOT EXISTS descuento_automatico boolean NOT NULL DEFAULT true;

UPDATE public.ingredientes SET descuento_automatico=false
WHERE lower(nombre) LIKE '%aceite%';

ALTER TABLE public.ingredientes DROP CONSTRAINT IF EXISTS ingredientes_unidad_base_check;
ALTER TABLE public.ingredientes
    ADD CONSTRAINT ingredientes_unidad_base_check
    CHECK (unidad_base IN ('UNIDAD','GRAMO','KILOGRAMO','MILILITRO','LITRO'));

-- La función de stock usa ON CONFLICT(plato_id,fecha): garantizamos esa clave.
-- Si una versión anterior dejó duplicados, movemos su historial al registro más reciente y los limpiamos.
WITH mapa AS (
    SELECT id AS duplicado, max(id) OVER (PARTITION BY plato_id,fecha) AS conservar
    FROM public.plato_stock_diario
)
UPDATE public.plato_stock_movimientos m
   SET plato_stock_id=mapa.conservar
  FROM mapa
 WHERE m.plato_stock_id=mapa.duplicado AND mapa.duplicado<>mapa.conservar;

DELETE FROM public.plato_stock_diario a
USING public.plato_stock_diario b
WHERE a.plato_id=b.plato_id AND a.fecha=b.fecha AND a.id<b.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_plato_stock_diario_plato_fecha
    ON public.plato_stock_diario(plato_id,fecha);

CREATE INDEX IF NOT EXISTS ix_plato_stock_diario_fecha_activo ON public.plato_stock_diario(fecha, activo, plato_id);
CREATE INDEX IF NOT EXISTS ix_plato_ingredientes_plato ON public.plato_ingredientes(plato_id, ingrediente_id);
CREATE INDEX IF NOT EXISTS ix_inventario_movimientos_fecha ON public.inventario_movimientos(created_at DESC);
CREATE INDEX IF NOT EXISTS ix_pedidos_origen_estado_fecha ON public.pedidos(origen, estado, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_pedidos_mesa_estado_fecha ON public.pedidos(mesa_id, estado, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_pedido_detalles_pedido_estado ON public.pedido_detalles(pedido_id, estado);
CREATE INDEX IF NOT EXISTS ix_pagos_fecha ON public.pagos(created_at DESC);
CREATE INDEX IF NOT EXISTS ix_compras_estado_fecha ON public.compras(estado, fecha DESC);
CREATE INDEX IF NOT EXISTS ix_gastos_activo_fecha ON public.gastos(activo, fecha DESC);

-- Se recrean en este orden para evitar el error de PostgreSQL
-- "cannot drop columns from view" al actualizar vistas dependientes.
DROP VIEW IF EXISTS public.vw_carta_stock_publico;
DROP VIEW IF EXISTS public.vw_platos_stock_hoy;

CREATE VIEW public.vw_platos_stock_hoy AS
SELECT p.id AS plato_id,
       COALESCE(d.stock_actual,0)::integer AS stock_diario,
       COALESCE(d.stock_actual,0)::integer AS stock_disponible
FROM public.platos p
LEFT JOIN public.plato_stock_diario d
  ON d.plato_id=p.id
 AND d.fecha=(now() AT TIME ZONE 'America/Lima')::date
 AND d.activo;

CREATE OR REPLACE FUNCTION public.configurar_stock_diario(
    p_plato_id bigint, p_fecha date, p_stock_inicial integer
) RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public
AS $$
DECLARE
    v_hoy date := (now() AT TIME ZONE 'America/Lima')::date;
    v_anterior_inicial integer := 0;
    v_anterior_actual integer := 0;
    v_vendido integer := 0;
    v_delta integer := 0;
    v_nuevo_actual integer := 0;
    v_reservado boolean := false;
    v_rec record;
    v_stock numeric;
    v_requerido numeric;
BEGIN
    IF p_fecha IS NULL THEN p_fecha := v_hoy; END IF;
    IF p_fecha <> v_hoy THEN RAISE EXCEPTION 'El stock operativo solo se configura para hoy (%)',v_hoy; END IF;
    IF p_stock_inicial IS NULL OR p_stock_inicial < 0 THEN RAISE EXCEPTION 'El stock inicial debe ser 0 o mayor'; END IF;
    IF NOT EXISTS(SELECT 1 FROM public.platos WHERE id=p_plato_id AND disponible) THEN
        RAISE EXCEPTION 'El plato no existe o está deshabilitado';
    END IF;

    SELECT stock_inicial,stock_actual,inventario_reservado
      INTO v_anterior_inicial,v_anterior_actual,v_reservado
      FROM public.plato_stock_diario
     WHERE plato_id=p_plato_id AND fecha=p_fecha
     FOR UPDATE;

    IF NOT FOUND THEN
        v_anterior_inicial:=0; v_anterior_actual:=0; v_reservado:=true;
    END IF;

    v_vendido:=GREATEST(v_anterior_inicial-v_anterior_actual,0);
    IF p_stock_inicial < v_vendido THEN
        RAISE EXCEPTION 'No puedes bajar el stock inicial por debajo de lo ya vendido (% porciones)',v_vendido;
    END IF;

    v_nuevo_actual:=p_stock_inicial-v_vendido;
    v_delta:=CASE WHEN v_reservado THEN p_stock_inicial-v_anterior_inicial ELSE v_nuevo_actual END;

    IF v_delta > 0 AND NOT EXISTS (
        SELECT 1
          FROM public.plato_ingredientes pi
          JOIN public.ingredientes i ON i.id=pi.ingrediente_id
         WHERE pi.plato_id=p_plato_id
           AND i.activo
           AND COALESCE(i.descuento_automatico,true)
    ) THEN
        RAISE EXCEPTION 'Configura al menos un ingrediente automático en la receta antes de preparar stock';
    END IF;

    FOR v_rec IN
        SELECT pi.ingrediente_id,pi.cantidad_base,i.nombre,i.activo
          FROM public.plato_ingredientes pi
          JOIN public.ingredientes i ON i.id=pi.ingrediente_id
         WHERE pi.plato_id=p_plato_id
           AND COALESCE(i.descuento_automatico,true)
         ORDER BY pi.ingrediente_id
    LOOP
        IF NOT v_rec.activo THEN RAISE EXCEPTION 'Ingrediente % está inactivo',v_rec.nombre; END IF;
        SELECT stock_actual INTO v_stock FROM public.ingredientes WHERE id=v_rec.ingrediente_id FOR UPDATE;
        v_requerido:=v_rec.cantidad_base*abs(v_delta);

        IF v_delta > 0 THEN
            IF v_stock < v_requerido THEN
                RAISE EXCEPTION 'No alcanza %: disponible %, requerido % para preparar % porciones adicionales',v_rec.nombre,v_stock,v_requerido,v_delta;
            END IF;
            UPDATE public.ingredientes SET stock_actual=stock_actual-v_requerido,updated_at=now() WHERE id=v_rec.ingrediente_id;
            INSERT INTO public.inventario_movimientos(ingrediente_id,tipo,cantidad,stock_anterior,stock_nuevo,motivo)
            VALUES(v_rec.ingrediente_id,'AJUSTE_SALIDA',v_requerido,v_stock,v_stock-v_requerido,'Producción diaria del plato '||p_plato_id);
        ELSIF v_delta < 0 THEN
            UPDATE public.ingredientes SET stock_actual=stock_actual+v_requerido,updated_at=now() WHERE id=v_rec.ingrediente_id;
            INSERT INTO public.inventario_movimientos(ingrediente_id,tipo,cantidad,stock_anterior,stock_nuevo,motivo)
            VALUES(v_rec.ingrediente_id,'AJUSTE_ENTRADA',v_requerido,v_stock,v_stock+v_requerido,'Reducción de producción diaria del plato '||p_plato_id);
        END IF;
    END LOOP;

    INSERT INTO public.plato_stock_diario(plato_id,fecha,stock_inicial,stock_actual,activo,inventario_reservado,updated_at)
    VALUES(p_plato_id,p_fecha,p_stock_inicial,v_nuevo_actual,true,true,now())
    ON CONFLICT(plato_id,fecha) DO UPDATE SET
        stock_inicial=excluded.stock_inicial,stock_actual=excluded.stock_actual,
        activo=true,inventario_reservado=true,updated_at=now();

    RETURN v_nuevo_actual;
END;
$$;

CREATE VIEW public.vw_carta_stock_publico AS
SELECT p.id AS plato_id,
       p.disponible AS habilitado_catalogo,
       COALESCE(v.stock_disponible,0) AS stock_hoy,
       (p.disponible AND COALESCE(v.stock_disponible,0)>0) AS disponible_hoy
FROM public.platos p
LEFT JOIN public.vw_platos_stock_hoy v ON v.plato_id=p.id;

REVOKE ALL ON TABLE public.vw_platos_stock_hoy FROM PUBLIC;
REVOKE ALL ON FUNCTION public.configurar_stock_diario(bigint,date,integer) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
        REVOKE ALL ON TABLE public.vw_platos_stock_hoy FROM anon;
        GRANT SELECT ON public.vw_carta_stock_publico TO anon;
    END IF;
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
        REVOKE ALL ON TABLE public.vw_platos_stock_hoy FROM authenticated;
        GRANT SELECT ON public.vw_carta_stock_publico TO authenticated;
    END IF;
END $$;

COMMIT;

-- Verificación opcional
SELECT p.nombre,v.stock_hoy,v.disponible_hoy
FROM public.vw_carta_stock_publico v JOIN public.platos p ON p.id=v.plato_id
ORDER BY p.nombre;
