-- Opcional: crea 25 mesas SOLO si el catalogo esta vacio.
-- Ajusta 25 a la cantidad real ANTES de ejecutar. No modifica mesas existentes.
BEGIN;
INSERT INTO public.mesas(numero, capacidad, estado)
SELECT n, 4, 'DISPONIBLE' FROM generate_series(1,25) n
WHERE NOT EXISTS (SELECT 1 FROM public.mesas);
COMMIT;
