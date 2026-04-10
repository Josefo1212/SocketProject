-- ============================================================
-- SCRIPT DE CREACIÓN DE TABLAS - GARTIC PHONE HISTORIA
-- ============================================================

-- 1. TABLA: usuario
-- ------------------------------------------------------------
CREATE TABLE usuario (
                         id_usuario          SERIAL          PRIMARY KEY,
                         nombre              VARCHAR(50)     NOT NULL UNIQUE,
                         fecha_registro      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- 2. TABLA: sala
-- ------------------------------------------------------------
CREATE TABLE sala (
                      id_sala             SERIAL          PRIMARY KEY,
                      codigo              VARCHAR(10)     NOT NULL UNIQUE,
                      estado              VARCHAR(20)     DEFAULT 'esperando',
                      max_jugadores       INT             DEFAULT 8,
                      fecha_creacion      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- 3. TABLA: participacion
-- ------------------------------------------------------------
CREATE TABLE participacion (
                               id_participacion    SERIAL          PRIMARY KEY,
                               id_usuario          INT             NOT NULL REFERENCES usuario(id_usuario) ON DELETE CASCADE,
                               id_sala             INT             NOT NULL REFERENCES sala(id_sala) ON DELETE CASCADE,
                               es_host             BOOLEAN         DEFAULT FALSE,
                               orden_turno         INT             NOT NULL,
                               activo              BOOLEAN         DEFAULT TRUE,
                               fecha_union         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT unique_usuario_sala UNIQUE(id_usuario, id_sala),
                               CONSTRAINT unique_orden_sala UNIQUE(id_sala, orden_turno)
);

-- 4. TABLA: historia
-- ------------------------------------------------------------
CREATE TABLE historia (
                          id_historia         SERIAL          PRIMARY KEY,
                          id_sala             INT             NOT NULL REFERENCES sala(id_sala) ON DELETE CASCADE,
                          id_jugador_creador  INT             NOT NULL REFERENCES usuario(id_usuario),
                          titulo              VARCHAR(100)    DEFAULT 'Historia sin título',
                          estado              VARCHAR(20)     DEFAULT 'activa',
                          fecha_creacion      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
                          fecha_completada    TIMESTAMP,

                          CONSTRAINT unique_creador_sala UNIQUE(id_sala, id_jugador_creador)
);

-- 5. TABLA: ronda
-- ------------------------------------------------------------
CREATE TABLE ronda (
                       id_ronda            SERIAL          PRIMARY KEY,
                       id_sala             INT             NOT NULL REFERENCES sala(id_sala) ON DELETE CASCADE,
                       numero_ronda        INT             NOT NULL,
                       estado              VARCHAR(20)     DEFAULT 'en_progreso',
                       fecha_inicio        TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
                       fecha_fin           TIMESTAMP,

                       CONSTRAINT unique_ronda_sala UNIQUE(id_sala, numero_ronda)
);

-- 6. TABLA: asignacion_ronda
-- ------------------------------------------------------------
CREATE TABLE asignacion_ronda (
                                  id_asignacion       SERIAL          PRIMARY KEY,
                                  id_ronda            INT             NOT NULL REFERENCES ronda(id_ronda) ON DELETE CASCADE,
                                  id_historia         INT             NOT NULL REFERENCES historia(id_historia) ON DELETE CASCADE,
                                  id_jugador_asignado INT             NOT NULL REFERENCES usuario(id_usuario),
                                  estado              VARCHAR(20)     DEFAULT 'pendiente',
                                  texto_previo        TEXT            DEFAULT '',
                                  tiempo_asignacion   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
                                  tiempo_respuesta    TIMESTAMP,

                                  CONSTRAINT unique_asignacion_ronda_jugador UNIQUE(id_ronda, id_jugador_asignado),
                                  CONSTRAINT unique_asignacion_ronda_historia UNIQUE(id_ronda, id_historia)
);

-- 7. TABLA: fragmento_historia
-- ------------------------------------------------------------
CREATE TABLE fragmento_historia (
                                    id_fragmento        SERIAL          PRIMARY KEY,
                                    id_historia         INT             NOT NULL REFERENCES historia(id_historia) ON DELETE CASCADE,
                                    id_ronda            INT             NOT NULL REFERENCES ronda(id_ronda) ON DELETE CASCADE,
                                    id_jugador          INT             NOT NULL REFERENCES usuario(id_usuario),
                                    contenido           TEXT            NOT NULL,
                                    orden               INT             NOT NULL,
                                    fecha_envio         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT unique_fragmento_historia_orden UNIQUE(id_historia, orden)
);

-- ============================================================
-- 3. ÍNDICES (Optimización de consultas)
-- ============================================================

CREATE INDEX idx_sala_codigo ON sala(codigo);
CREATE INDEX idx_participacion_sala ON participacion(id_sala);
CREATE INDEX idx_participacion_usuario ON participacion(id_usuario);
CREATE INDEX idx_historia_sala ON historia(id_sala);
CREATE INDEX idx_historia_creador ON historia(id_jugador_creador);
CREATE INDEX idx_ronda_sala ON ronda(id_sala);
CREATE INDEX idx_asignacion_ronda_jugador ON asignacion_ronda(id_ronda, id_jugador_asignado);
CREATE INDEX idx_asignacion_ronda_historia ON asignacion_ronda(id_ronda, id_historia);
CREATE INDEX idx_fragmento_historia_compuesto ON fragmento_historia(id_historia, orden);
CREATE INDEX idx_fragmento_historia_ronda ON fragmento_historia(id_ronda);