-- La Fonda V5 - stock diario acumulativo
-- Ejecutar después de 07_mejoras_gestion_v4.sql.
-- Crea agregar_stock_diario(plato, fecha, cantidad) para sumar producción sin reemplazar el stock existente.

BEGIN;

CREATE OR REPLACE FUNCTION public.agregar_stock_diario(
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
    v_nuevo_inicial integer := 0;
    v_nuevo_actual integer := 0;
    v_stock_id bigint;
    v_rec record;
    v_stock numeric;
    v_requerido numeric;
BEGIN
    IF p_fecha IS NULL THEN p_fecha := v_hoy; END IF;
    IF p_fecha <> v_hoy THEN
        RAISE EXCEPTION 'El stock operativo solo se puede agregar para hoy (%)', v_hoy;
    END IF;
    IF p_stock_inicial IS NULL OR p_stock_inicial <= 0 THEN
        RAISE EXCEPTION 'La cantidad a agregar debe ser mayor que 0';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM public.platos WHERE id=p_plato_id AND disponible) THEN
        RAISE EXCEPTION 'El plato no existe o está deshabilitado';
    END IF;

    PERFORM pg_advisory_xact_lock(p_plato_id);

    SELECT stock_inicial, stock_actual
      INTO v_anterior_inicial, v_anterior_actual
      FROM public.plato_stock_diario
     WHERE plato_id=p_plato_id AND fecha=p_fecha
     FOR UPDATE;

    IF NOT FOUND THEN
        v_anterior_inicial := 0;
        v_anterior_actual := 0;
    END IF;

    IF NOT EXISTS (
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
        SELECT pi.ingrediente_id, pi.cantidad_base, i.nombre, i.activo
          FROM public.plato_ingredientes pi
          JOIN public.ingredientes i ON i.id=pi.ingrediente_id
         WHERE pi.plato_id=p_plato_id
           AND COALESCE(i.descuento_automatico,true)
         ORDER BY pi.ingrediente_id
    LOOP
        IF NOT v_rec.activo THEN
            RAISE EXCEPTION 'Ingrediente % está inactivo', v_rec.nombre;
        END IF;

        SELECT stock_actual
          INTO v_stock
          FROM public.ingredientes
         WHERE id=v_rec.ingrediente_id
         FOR UPDATE;

        v_requerido := v_rec.cantidad_base * p_stock_inicial;
        IF v_stock < v_requerido THEN
            RAISE EXCEPTION 'No alcanza %: disponible %, requerido % para agregar % porciones',
                v_rec.nombre, v_stock, v_requerido, p_stock_inicial;
        END IF;

        UPDATE public.ingredientes
           SET stock_actual=stock_actual-v_requerido, updated_at=now()
         WHERE id=v_rec.ingrediente_id;

        INSERT INTO public.inventario_movimientos(
            ingrediente_id,tipo,cantidad,stock_anterior,stock_nuevo,motivo
        ) VALUES (
            v_rec.ingrediente_id,'AJUSTE_SALIDA',v_requerido,v_stock,v_stock-v_requerido,
            'Producción adicional de '||p_stock_inicial||' porciones del plato '||p_plato_id
        );
    END LOOP;

    v_nuevo_inicial := v_anterior_inicial + p_stock_inicial;
    v_nuevo_actual := v_anterior_actual + p_stock_inicial;

    INSERT INTO public.plato_stock_diario(
        plato_id,fecha,stock_inicial,stock_actual,activo,inventario_reservado,updated_at
    ) VALUES (
        p_plato_id,p_fecha,v_nuevo_inicial,v_nuevo_actual,true,true,now()
    )
    ON CONFLICT(plato_id,fecha) DO UPDATE SET
        stock_inicial=excluded.stock_inicial,
        stock_actual=excluded.stock_actual,
        activo=true,
        inventario_reservado=true,
        updated_at=now()
    RETURNING id INTO v_stock_id;

    INSERT INTO public.plato_stock_movimientos(
        plato_stock_id,pedido_detalle_id,tipo,cantidad,stock_anterior,stock_nuevo,reversado
    ) VALUES (
        v_stock_id,NULL,'AJUSTE_ENTRADA',p_stock_inicial,v_anterior_actual,v_nuevo_actual,false
    );

    RETURN v_nuevo_actual;
END;
$$;

REVOKE ALL ON FUNCTION public.agregar_stock_diario(bigint,date,integer) FROM PUBLIC;

COMMIT;

-- Prueba esperada:
-- Si Preparado=5, Disponible=3, Vendido=2 y ejecutas:
-- SELECT agregar_stock_diario(<plato_id>, (now() AT TIME ZONE 'America/Lima')::date, 4);
-- El resultado será Preparado=9, Disponible=7, Vendido=2.
