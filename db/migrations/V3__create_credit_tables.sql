CREATE TABLE credit.credit_applications (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_reference VARCHAR(64)   NOT NULL,
    customer_id           VARCHAR(20)   NOT NULL,
    amount                NUMERIC(18,2) NOT NULL,
    term_months           INTEGER       NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    decision_reason       VARCHAR(50)   NOT NULL,
    processed_at          TIMESTAMPTZ   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Protección final de idempotencia frente a requests concurrentes con la misma referencia.
    CONSTRAINT uk_credit_applications_reference UNIQUE (application_reference),
    CONSTRAINT fk_credit_applications_customer
        FOREIGN KEY (customer_id) REFERENCES customer.customers (customer_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_credit_applications_reference_not_blank CHECK (btrim(application_reference) <> ''),
    CONSTRAINT ck_credit_applications_amount_positive     CHECK (amount > 0),
    CONSTRAINT ck_credit_applications_term_range          CHECK (term_months BETWEEN 6 AND 60),
    CONSTRAINT ck_credit_applications_status              CHECK (status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_credit_applications_decision_reason     CHECK (
           (status = 'APPROVED' AND decision_reason = 'CREDIT_LIMIT_AVAILABLE')
        OR (status = 'REJECTED' AND decision_reason IN ('CUSTOMER_NOT_ELIGIBLE', 'CREDIT_LIMIT_EXCEEDED'))
    )
);

CREATE INDEX ix_credit_applications_customer_processed
    ON credit.credit_applications (customer_id, processed_at DESC);

CREATE INDEX ix_credit_applications_processed
    ON credit.credit_applications (processed_at DESC, id DESC);

-- Una solicitud procesada no cambia de decisión: la tabla es append-only.
CREATE FUNCTION credit.prevent_credit_application_changes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'credit.credit_applications is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_credit_applications_immutable
    BEFORE UPDATE OR DELETE ON credit.credit_applications
    FOR EACH ROW EXECUTE FUNCTION credit.prevent_credit_application_changes();
