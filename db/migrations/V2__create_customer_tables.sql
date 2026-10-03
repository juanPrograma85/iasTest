CREATE TABLE customer.customers (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id             VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    max_approved_amount     NUMERIC(18,2) NOT NULL,
    current_approved_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_customers_customer_id      UNIQUE (customer_id),
    CONSTRAINT ck_customers_status           CHECK (status IN ('ELIGIBLE', 'BLOCKED')),
    CONSTRAINT ck_customers_max_non_negative CHECK (max_approved_amount >= 0),
    CONSTRAINT ck_customers_current_non_neg  CHECK (current_approved_amount >= 0),
    -- Última barrera: el cupo nunca puede quedar sobregirado, aun si la aplicación fallara.
    CONSTRAINT ck_customers_current_le_max   CHECK (current_approved_amount <= max_approved_amount)
);
