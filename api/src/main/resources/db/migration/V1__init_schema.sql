-- =====================================================================
-- MediCitas Anglo - Esquema inicial para PostgreSQL 15
-- Fuente: docs/diagrams/03-entity-relationship-diagram.puml
-- docker-compose lo ejecuta al crear el contenedor postgres-db por primera vez.
-- La API usa este esquema con spring.jpa.hibernate.ddl-auto=validate (perfil postgres).
-- =====================================================================

SET TIME ZONE 'America/Lima';

-- ---------------------------------------------------------------------
-- Catálogo de la clínica
-- ---------------------------------------------------------------------
CREATE TABLE location (
    id        BIGSERIAL    PRIMARY KEY,
    code      VARCHAR(10)  NOT NULL,
    name      VARCHAR(100) NOT NULL,
    address   VARCHAR(200) NOT NULL,
    district  VARCHAR(60)  NOT NULL,
    phone     VARCHAR(20),
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_location_code UNIQUE (code)
);

CREATE TABLE specialty (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    description         TEXT,
    appointment_minutes SMALLINT     NOT NULL DEFAULT 20,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_specialty_name UNIQUE (name),
    CONSTRAINT ck_specialty_minutes CHECK (appointment_minutes > 0)
);

CREATE TABLE location_specialty (
    location_id   BIGINT  NOT NULL REFERENCES location (id),
    specialty_id  BIGINT  NOT NULL REFERENCES specialty (id),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (location_id, specialty_id)
);

CREATE TABLE doctor (
    id                   BIGSERIAL    PRIMARY KEY,
    license              VARCHAR(10)  NOT NULL,
    specialist_registry  VARCHAR(10),
    first_names          VARCHAR(100) NOT NULL,
    last_names           VARCHAR(100) NOT NULL,
    specialty_id         BIGINT       NOT NULL REFERENCES specialty (id),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_doctor_license UNIQUE (license)
);

-- ---------------------------------------------------------------------
-- Pacientes y seguros
-- ---------------------------------------------------------------------
CREATE TABLE patient (
    id                     BIGSERIAL    PRIMARY KEY,
    document_type          VARCHAR(3)   NOT NULL,
    document_number        VARCHAR(12)  NOT NULL,
    first_names            VARCHAR(100) NOT NULL,
    last_names             VARCHAR(100) NOT NULL,
    birth_date             DATE         NOT NULL,
    sex                    CHAR(1),
    phone                  VARCHAR(15)  NOT NULL,
    email                  VARCHAR(120) NOT NULL,
    accepts_notifications  BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_patient_document UNIQUE (document_type, document_number),
    CONSTRAINT ck_patient_document_type CHECK (document_type IN ('DNI', 'CE', 'PAS')),
    CONSTRAINT ck_patient_sex CHECK (sex IS NULL OR sex IN ('M', 'F'))
);

CREATE TABLE insurer (
    id      BIGSERIAL    PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    ruc     CHAR(11)     NOT NULL,
    type    VARCHAR(15)  NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_insurer_name UNIQUE (name),
    CONSTRAINT uk_insurer_ruc UNIQUE (ruc)
);

CREATE TABLE insurance_plan (
    id                  BIGSERIAL    PRIMARY KEY,
    insurer_id          BIGINT       NOT NULL REFERENCES insurer (id),
    name                VARCHAR(80)  NOT NULL,
    consultation_copay  NUMERIC(8,2),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_plan_insurer_name UNIQUE (insurer_id, name)
);

CREATE TABLE patient_insurance (
    id               BIGSERIAL   PRIMARY KEY,
    patient_id       BIGINT      NOT NULL REFERENCES patient (id),
    plan_id          BIGINT      NOT NULL REFERENCES insurance_plan (id),
    policy_number    VARCHAR(30),
    coverage_status  VARCHAR(12) NOT NULL,
    valid_until      DATE,
    CONSTRAINT uk_patient_insurance_plan UNIQUE (patient_id, plan_id),
    CONSTRAINT ck_patient_insurance_status CHECK (coverage_status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED'))
);

-- ---------------------------------------------------------------------
-- Agenda y citas
-- ---------------------------------------------------------------------
CREATE TABLE slot (
    id           BIGSERIAL   PRIMARY KEY,
    doctor_id    BIGINT      NOT NULL REFERENCES doctor (id),
    location_id  BIGINT      NOT NULL REFERENCES location (id),
    date         DATE        NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    room         VARCHAR(20),
    status       VARCHAR(10) NOT NULL DEFAULT 'FREE',
    version      INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_slot_doctor_schedule UNIQUE (doctor_id, date, start_time),
    CONSTRAINT ck_slot_times CHECK (end_time > start_time),
    CONSTRAINT ck_slot_status CHECK (status IN ('FREE', 'BOOKED', 'BLOCKED'))
);

CREATE INDEX ix_slot_availability ON slot (location_id, date, status);

CREATE TABLE appointment (
    id                    BIGSERIAL    PRIMARY KEY,
    code                  VARCHAR(12)  NOT NULL,
    patient_id            BIGINT       NOT NULL REFERENCES patient (id),
    slot_id               BIGINT       NOT NULL REFERENCES slot (id),
    patient_insurance_id  BIGINT       REFERENCES patient_insurance (id),
    visit_reason          VARCHAR(250) NOT NULL,
    status                VARCHAR(12)  NOT NULL DEFAULT 'BOOKED',
    booking_channel       VARCHAR(10)  NOT NULL,
    idempotency_key       UUID         NOT NULL,
    reschedule_count      SMALLINT     NOT NULL DEFAULT 0,
    booked_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_appointment_code UNIQUE (code),
    CONSTRAINT uk_appointment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_appointment_status CHECK (status IN ('BOOKED', 'CANCELLED', 'ATTENDED', 'NO_SHOW')),
    CONSTRAINT ck_appointment_channel CHECK (booking_channel IN ('KIOSK', 'FRONT_DESK')),
    CONSTRAINT ck_appointment_reschedules CHECK (reschedule_count >= 0)
);

CREATE INDEX ix_appointment_patient_status ON appointment (patient_id, status);
-- Refuerzo de RF-07 en la base: un cupo no puede tener dos citas BOOKED a la vez.
CREATE UNIQUE INDEX uk_appointment_slot_booked ON appointment (slot_id) WHERE status = 'BOOKED';

-- ---------------------------------------------------------------------
-- Seguridad y auditoría (Ley 29733)
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id             BIGSERIAL   PRIMARY KEY,
    username       VARCHAR(50) NOT NULL,
    password_hash  VARCHAR(72) NOT NULL,
    role           VARCHAR(15) NOT NULL,
    doctor_id      BIGINT      REFERENCES doctor (id),
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    last_access    TIMESTAMPTZ,
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT uk_app_user_doctor UNIQUE (doctor_id),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'FRONT_DESK', 'DOCTOR'))
);

CREATE TABLE audit_log (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      REFERENCES app_user (id),
    action         VARCHAR(60) NOT NULL,
    entity         VARCHAR(40) NOT NULL,
    entity_id      BIGINT,
    detail         TEXT,
    source_ip      VARCHAR(45),
    registered_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appointment_history (
    id                BIGSERIAL    PRIMARY KEY,
    appointment_id    BIGINT       NOT NULL REFERENCES appointment (id),
    event             VARCHAR(15)  NOT NULL,
    previous_status   VARCHAR(12),
    new_status        VARCHAR(12)  NOT NULL,
    previous_slot_id  BIGINT       REFERENCES slot (id),
    new_slot_id       BIGINT       REFERENCES slot (id),
    user_id           BIGINT       REFERENCES app_user (id),
    detail            VARCHAR(250),
    event_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_appointment_history_event CHECK (event IN ('BOOKING', 'RESCHEDULE', 'CANCELLATION', 'ATTENDANCE'))
);

CREATE INDEX ix_appointment_history_appointment ON appointment_history (appointment_id);

-- ---------------------------------------------------------------------
-- Notificaciones
-- ---------------------------------------------------------------------
CREATE TABLE notification (
    id                   BIGSERIAL    PRIMARY KEY,
    appointment_id       BIGINT       NOT NULL REFERENCES appointment (id),
    type                 VARCHAR(15)  NOT NULL,
    channel              VARCHAR(5)   NOT NULL,
    destination          VARCHAR(120) NOT NULL,
    scheduled_for        TIMESTAMPTZ  NOT NULL,
    sent_at              TIMESTAMPTZ,
    status               VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts             SMALLINT     NOT NULL DEFAULT 0,
    provider_message_id  VARCHAR(80),
    error_detail         VARCHAR(250),
    CONSTRAINT ck_notification_type CHECK (type IN ('CONFIRMATION', 'REMINDER', 'RESCHEDULE', 'CANCELLATION')),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notification_attempts CHECK (attempts >= 0)
);

-- Consulta de la tarea programada (proceso 07)
CREATE INDEX ix_notification_pending ON notification (status, scheduled_for);
