-- Verifica los casos de uso del modelo de datos. Todo se ejecuta en una transacción con ROLLBACK:
-- no deja datos. Uso: docker exec bancoias-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /db-tests/use_cases.sql'
\set ON_ERROR_STOP on
BEGIN;

UPDATE customer.customers SET current_approved_amount = 0;

-- Misma secuencia que ejecutará el adaptador R2DBC dentro de una transacción:
-- UPDATE condicional atómico del cupo + INSERT de la solicitud.
CREATE FUNCTION pg_temp.process(p_ref TEXT, p_customer TEXT, p_amount NUMERIC, p_term INT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows            INT;
    v_status          TEXT;
    v_reason          TEXT;
    v_customer_status TEXT;
BEGIN
    UPDATE customer.customers
       SET current_approved_amount = current_approved_amount + p_amount,
           updated_at = CURRENT_TIMESTAMP
     WHERE customer_id = p_customer
       AND status = 'ELIGIBLE'
       AND current_approved_amount + p_amount <= max_approved_amount;
    GET DIAGNOSTICS v_rows = ROW_COUNT;

    IF v_rows = 1 THEN
        v_status := 'APPROVED';
        v_reason := 'CREDIT_LIMIT_AVAILABLE';
    ELSE
        SELECT status INTO v_customer_status FROM customer.customers WHERE customer_id = p_customer;
        v_status := 'REJECTED';
        v_reason := CASE WHEN v_customer_status = 'ELIGIBLE' THEN 'CREDIT_LIMIT_EXCEEDED' ELSE 'CUSTOMER_NOT_ELIGIBLE' END;
    END IF;

    INSERT INTO credit.credit_applications
        (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
    VALUES (p_ref, p_customer, p_amount, p_term, v_status, v_reason, clock_timestamp());

    RETURN v_status;
END;
$$;

CREATE FUNCTION pg_temp.used(p_customer TEXT) RETURNS NUMERIC LANGUAGE sql AS $$
    SELECT current_approved_amount FROM customer.customers WHERE customer_id = p_customer;
$$;

DO $$
DECLARE
    v_ref TEXT;
BEGIN
    -- UC: aprobación dentro del cupo
    ASSERT pg_temp.process('T-REF-001', 'CLI-1001', 6000000, 24) = 'APPROVED', 'T-REF-001 debía aprobarse';
    ASSERT pg_temp.used('CLI-1001') = 6000000, 'El cupo de CLI-1001 debía ser 6M';

    -- UC: cupo insuficiente -> REJECTED persistido, sin consumo de cupo
    ASSERT pg_temp.process('T-REF-002', 'CLI-1001', 6000000, 24) = 'REJECTED', 'T-REF-002 debía rechazarse';
    ASSERT pg_temp.used('CLI-1001') = 6000000, 'El rechazo no debe consumir cupo';
    ASSERT (SELECT decision_reason FROM credit.credit_applications WHERE application_reference = 'T-REF-002')
           = 'CREDIT_LIMIT_EXCEEDED', 'Razón esperada CREDIT_LIMIT_EXCEEDED';

    -- UC: completar exactamente el cupo
    ASSERT pg_temp.process('T-REF-003', 'CLI-1001', 4000000, 12) = 'APPROVED', 'T-REF-003 debía aprobarse';
    ASSERT pg_temp.used('CLI-1001') = 10000000, 'El cupo de CLI-1001 debía ser 10M';

    -- UC: cliente BLOCKED -> REJECTED persistido, sin consumo de cupo
    ASSERT pg_temp.process('T-REF-004', 'CLI-1002', 1000000, 12) = 'REJECTED', 'T-REF-004 debía rechazarse';
    ASSERT pg_temp.used('CLI-1002') = 0, 'Cliente bloqueado no debe consumir cupo';
    ASSERT (SELECT decision_reason FROM credit.credit_applications WHERE application_reference = 'T-REF-004')
           = 'CUSTOMER_NOT_ELIGIBLE', 'Razón esperada CUSTOMER_NOT_ELIGIBLE';

    -- UC: referencia repetida -> UNIQUE aborta la transacción y revierte el incremento de cupo
    BEGIN
        PERFORM pg_temp.process('T-REF-001', 'CLI-2001', 1000000, 12);
        RAISE EXCEPTION 'Se esperaba unique_violation';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    ASSERT pg_temp.used('CLI-2001') = 0, 'El rollback debía revertir el cupo de CLI-2001';
    ASSERT (SELECT count(*) FROM credit.credit_applications WHERE application_reference = 'T-REF-001') = 1,
           'La solicitud original debe ser única';
    ASSERT (SELECT customer_id FROM credit.credit_applications WHERE application_reference = 'T-REF-001') = 'CLI-1001',
           'La solicitud original no debe modificarse';

    -- Invariantes de entrada (la aplicación responde 422 antes de llegar aquí)
    BEGIN
        INSERT INTO credit.credit_applications (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
        VALUES ('T-BAD-1', 'CLI-2001', 0, 24, 'REJECTED', 'CREDIT_LIMIT_EXCEEDED', now());
        RAISE EXCEPTION 'Se esperaba check_violation por amount <= 0';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    FOREACH v_ref IN ARRAY ARRAY['5', '61'] LOOP
        BEGIN
            INSERT INTO credit.credit_applications (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
            VALUES ('T-BAD-TERM', 'CLI-2001', 1000, v_ref::INT, 'REJECTED', 'CREDIT_LIMIT_EXCEEDED', now());
            RAISE EXCEPTION 'Se esperaba check_violation por plazo %', v_ref;
        EXCEPTION WHEN check_violation THEN NULL;
        END;
    END LOOP;

    BEGIN
        INSERT INTO credit.credit_applications (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
        VALUES ('T-BAD-2', 'CLI-9999', 1000, 24, 'REJECTED', 'CUSTOMER_NOT_ELIGIBLE', now());
        RAISE EXCEPTION 'Se esperaba foreign_key_violation por cliente inexistente';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO credit.credit_applications (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
        VALUES ('T-BAD-3', 'CLI-2001', 1000, 24, 'APPROVED', 'CREDIT_LIMIT_EXCEEDED', now());
        RAISE EXCEPTION 'Se esperaba check_violation por razón inconsistente';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- Invariante de cupo: nunca current > max
    BEGIN
        UPDATE customer.customers SET current_approved_amount = max_approved_amount + 1 WHERE customer_id = 'CLI-2001';
        RAISE EXCEPTION 'Se esperaba check_violation por cupo excedido';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- Inmutabilidad de la decisión
    BEGIN
        UPDATE credit.credit_applications SET status = 'REJECTED', decision_reason = 'CREDIT_LIMIT_EXCEEDED'
         WHERE application_reference = 'T-REF-001';
        RAISE EXCEPTION 'Se esperaba restrict_violation en UPDATE';
    EXCEPTION WHEN restrict_violation THEN NULL;
    END;

    BEGIN
        DELETE FROM credit.credit_applications WHERE application_reference = 'T-REF-001';
        RAISE EXCEPTION 'Se esperaba restrict_violation en DELETE';
    EXCEPTION WHEN restrict_violation THEN NULL;
    END;

    -- Consulta de recientes: processed_at DESC
    ASSERT (SELECT application_reference FROM credit.credit_applications
             ORDER BY processed_at DESC, id DESC LIMIT 1) = 'T-REF-004', 'La más reciente debía ser T-REF-004';

    RAISE NOTICE 'OK: todos los casos de uso del modelo de datos se cumplen';
END;
$$;

ROLLBACK;
