-- PROTOTYPE ONLY (branch prototype/full-slice, database ums_proto). Never merged into main.
-- Numbered V900+ so it can never collide with production migrations V3+.

CREATE TABLE app_user (
  id              BIGINT GENERATED ALWAYS AS IDENTITY,
  username        VARCHAR(64)  NOT NULL,
  password_hash   VARCHAR(72)  NOT NULL,
  role            VARCHAR(16)  NOT NULL,
  full_name       VARCHAR(128) NOT NULL,
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uq_app_user_username UNIQUE (username),
  CONSTRAINT ck_app_user_role CHECK (role IN ('STUDENT','FACULTY','ADMIN','DEV')),
  CONSTRAINT ck_app_user_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE department (
  id   BIGINT GENERATED ALWAYS AS IDENTITY,
  code VARCHAR(16)  NOT NULL,
  name VARCHAR(128) NOT NULL,
  CONSTRAINT pk_department PRIMARY KEY (id),
  CONSTRAINT uq_department_code UNIQUE (code)
);

CREATE TABLE programme (
  id            BIGINT GENERATED ALWAYS AS IDENTITY,
  code          VARCHAR(16)  NOT NULL,
  name          VARCHAR(128) NOT NULL,
  department_id BIGINT       NOT NULL,
  CONSTRAINT pk_programme PRIMARY KEY (id),
  CONSTRAINT uq_programme_code UNIQUE (code),
  CONSTRAINT fk_programme_department FOREIGN KEY (department_id) REFERENCES department (id)
);

CREATE TABLE course (
  id            BIGINT GENERATED ALWAYS AS IDENTITY,
  code          VARCHAR(16)  NOT NULL,
  title         VARCHAR(128) NOT NULL,
  credits       INT          NOT NULL,
  department_id BIGINT       NOT NULL,
  CONSTRAINT pk_course PRIMARY KEY (id),
  CONSTRAINT uq_course_code UNIQUE (code),
  CONSTRAINT ck_course_credits CHECK (credits BETWEEN 1 AND 6),
  CONSTRAINT fk_course_department FOREIGN KEY (department_id) REFERENCES department (id)
);

CREATE TABLE course_prereq (
  course_id BIGINT NOT NULL,
  prereq_id BIGINT NOT NULL,
  CONSTRAINT pk_course_prereq PRIMARY KEY (course_id, prereq_id),
  CONSTRAINT fk_course_prereq_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_course_prereq_prereq FOREIGN KEY (prereq_id) REFERENCES course (id)
);

CREATE TABLE student (
  user_id      BIGINT      NOT NULL,
  programme_id BIGINT      NOT NULL,
  roll_no      VARCHAR(32) NOT NULL,
  CONSTRAINT pk_student PRIMARY KEY (user_id),
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_student_programme FOREIGN KEY (programme_id) REFERENCES programme (id),
  CONSTRAINT uq_student_roll_no UNIQUE (roll_no)
);

CREATE TABLE faculty (
  user_id       BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  CONSTRAINT pk_faculty PRIMARY KEY (user_id),
  CONSTRAINT fk_faculty_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_faculty_department FOREIGN KEY (department_id) REFERENCES department (id)
);

CREATE TABLE term (
  id         BIGINT GENERATED ALWAYS AS IDENTITY,
  name       VARCHAR(64) NOT NULL,
  start_date DATE        NOT NULL,
  end_date   DATE        NOT NULL,
  reg_open   DATE        NOT NULL,
  reg_close  DATE        NOT NULL,
  state      VARCHAR(20) NOT NULL,
  version    BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT pk_term PRIMARY KEY (id),
  CONSTRAINT ck_term_state CHECK (state IN ('REGISTRATION_OPEN','IN_PROGRESS','GRADING','RESULTS_PUBLISHED')),
  CONSTRAINT ck_term_dates CHECK (start_date <= end_date AND reg_open <= reg_close)
);

CREATE TABLE section (
  id             BIGINT GENERATED ALWAYS AS IDENTITY,
  term_id        BIGINT      NOT NULL,
  course_id      BIGINT      NOT NULL,
  code           VARCHAR(16) NOT NULL,
  capacity       INT         NOT NULL,
  enrolled_count INT         NOT NULL DEFAULT 0,
  faculty_id     BIGINT      NOT NULL,
  day            INT         NOT NULL,
  start_min      INT         NOT NULL,
  end_min        INT         NOT NULL,
  room           VARCHAR(32) NOT NULL,
  version        BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT pk_section PRIMARY KEY (id),
  CONSTRAINT uq_section_term_course_code UNIQUE (term_id, course_id, code),
  CONSTRAINT fk_section_term FOREIGN KEY (term_id) REFERENCES term (id),
  CONSTRAINT fk_section_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_section_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (user_id),
  CONSTRAINT ck_section_capacity CHECK (enrolled_count BETWEEN 0 AND capacity),
  CONSTRAINT ck_section_day CHECK (day BETWEEN 1 AND 6),
  CONSTRAINT ck_section_time CHECK (start_min < end_min)
);
CREATE INDEX idx_section_term ON section (term_id);
CREATE INDEX idx_section_faculty_term ON section (faculty_id, term_id);

CREATE TABLE assessment_component (
  id         BIGINT GENERATED ALWAYS AS IDENTITY,
  section_id BIGINT       NOT NULL,
  name       VARCHAR(32)  NOT NULL,
  max_marks  NUMERIC(6,2) NOT NULL,
  weight_pct NUMERIC(5,2) NOT NULL,
  CONSTRAINT pk_assessment_component PRIMARY KEY (id),
  CONSTRAINT fk_assessment_component_section FOREIGN KEY (section_id) REFERENCES section (id),
  CONSTRAINT uq_assessment_component_name UNIQUE (section_id, name),
  CONSTRAINT ck_assessment_component_max CHECK (max_marks > 0),
  CONSTRAINT ck_assessment_component_weight CHECK (weight_pct > 0 AND weight_pct <= 100)
);

CREATE TABLE enrolment (
  id         BIGINT GENERATED ALWAYS AS IDENTITY,
  student_id BIGINT      NOT NULL,
  section_id BIGINT      NOT NULL,
  term_id    BIGINT      NOT NULL,
  course_id  BIGINT      NOT NULL,
  status     VARCHAR(10) NOT NULL DEFAULT 'ENROLLED',
  version    BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT pk_enrolment PRIMARY KEY (id),
  CONSTRAINT fk_enrolment_student FOREIGN KEY (student_id) REFERENCES student (user_id),
  CONSTRAINT fk_enrolment_section FOREIGN KEY (section_id) REFERENCES section (id),
  CONSTRAINT fk_enrolment_term FOREIGN KEY (term_id) REFERENCES term (id),
  CONSTRAINT fk_enrolment_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT ck_enrolment_status CHECK (status IN ('ENROLLED','DROPPED','COMPLETED'))
);
CREATE UNIQUE INDEX uq_enrolment_live ON enrolment (student_id, term_id, course_id) WHERE status = 'ENROLLED';
CREATE INDEX idx_enrolment_student_term ON enrolment (student_id, term_id);
CREATE INDEX idx_enrolment_section_status ON enrolment (section_id, status);

CREATE TABLE class_session (
  id           BIGINT GENERATED ALWAYS AS IDENTITY,
  section_id   BIGINT      NOT NULL,
  session_date DATE        NOT NULL,
  recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_class_session PRIMARY KEY (id),
  CONSTRAINT fk_class_session_section FOREIGN KEY (section_id) REFERENCES section (id),
  CONSTRAINT uq_class_session_section_date UNIQUE (section_id, session_date)
);

CREATE TABLE attendance (
  session_id   BIGINT     NOT NULL,
  enrolment_id BIGINT     NOT NULL,
  status       VARCHAR(8) NOT NULL,
  CONSTRAINT pk_attendance PRIMARY KEY (session_id, enrolment_id),
  CONSTRAINT fk_attendance_session FOREIGN KEY (session_id) REFERENCES class_session (id),
  CONSTRAINT fk_attendance_enrolment FOREIGN KEY (enrolment_id) REFERENCES enrolment (id),
  CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT','ABSENT','EXCUSED'))
);
CREATE INDEX idx_attendance_enrolment ON attendance (enrolment_id);

CREATE TABLE mark_sheet (
  id         BIGINT GENERATED ALWAYS AS IDENTITY,
  section_id BIGINT      NOT NULL,
  state      VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
  version    BIGINT      NOT NULL DEFAULT 0,
  CONSTRAINT pk_mark_sheet PRIMARY KEY (id),
  CONSTRAINT uq_mark_sheet_section UNIQUE (section_id),
  CONSTRAINT fk_mark_sheet_section FOREIGN KEY (section_id) REFERENCES section (id),
  CONSTRAINT ck_mark_sheet_state CHECK (state IN ('DRAFT','SUBMITTED','APPROVED','PUBLISHED'))
);

CREATE TABLE mark (
  enrolment_id BIGINT       NOT NULL,
  component_id BIGINT       NOT NULL,
  value        NUMERIC(6,2),
  absent       BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_mark PRIMARY KEY (enrolment_id, component_id),
  CONSTRAINT fk_mark_enrolment FOREIGN KEY (enrolment_id) REFERENCES enrolment (id),
  CONSTRAINT fk_mark_component FOREIGN KEY (component_id) REFERENCES assessment_component (id),
  CONSTRAINT ck_mark_nonneg CHECK (value IS NULL OR value >= 0)
);

CREATE TABLE result (
  id           BIGINT GENERATED ALWAYS AS IDENTITY,
  enrolment_id BIGINT       NOT NULL,
  total_pct    NUMERIC(5,2) NOT NULL,
  grade        VARCHAR(2)   NOT NULL,
  grade_point  NUMERIC(4,2) NOT NULL,
  CONSTRAINT pk_result PRIMARY KEY (id),
  CONSTRAINT uq_result_enrolment UNIQUE (enrolment_id),
  CONSTRAINT fk_result_enrolment FOREIGN KEY (enrolment_id) REFERENCES enrolment (id)
);

CREATE TABLE term_result (
  student_id     BIGINT       NOT NULL,
  term_id        BIGINT       NOT NULL,
  sgpa           NUMERIC(4,2) NOT NULL,
  cgpa           NUMERIC(4,2) NOT NULL,
  credits_earned INT          NOT NULL,
  CONSTRAINT pk_term_result PRIMARY KEY (student_id, term_id),
  CONSTRAINT fk_term_result_student FOREIGN KEY (student_id) REFERENCES student (user_id),
  CONSTRAINT fk_term_result_term FOREIGN KEY (term_id) REFERENCES term (id)
);

CREATE TABLE grade_scale (
  grade       VARCHAR(2)   NOT NULL,
  min_percent NUMERIC(5,2) NOT NULL,
  grade_point NUMERIC(4,2) NOT NULL,
  CONSTRAINT pk_grade_scale PRIMARY KEY (grade),
  CONSTRAINT uq_grade_scale_min UNIQUE (min_percent)
);

ALTER TABLE activity_log ADD CONSTRAINT fk_activity_log_user FOREIGN KEY (user_id) REFERENCES app_user (id);

-- the console role must never read password hashes
REVOKE SELECT ON app_user FROM ums_dev_console;
GRANT SELECT (id, username, role, full_name, active, failed_attempts, locked_until, created_at)
  ON app_user TO ums_dev_console;
