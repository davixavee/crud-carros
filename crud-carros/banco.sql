
CREATE DATABASE IF NOT EXISTS carros_db CHARACTER SET utf8mb4;
USE carros_db;

CREATE TABLE IF NOT EXISTS carros (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    marca  VARCHAR(50)    NOT NULL,
    modelo VARCHAR(80)    NOT NULL,
    ano    INT            NOT NULL,
    cor    VARCHAR(30),
    preco  DECIMAL(12,2)  NOT NULL DEFAULT 0
);

ALTER TABLE carros ADD COLUMN IF NOT EXISTS imagem VARCHAR(255);

INSERT INTO carros (marca, modelo, ano, cor, preco)
SELECT * FROM (
    SELECT 'Porsche' AS marca, '911 Carrera' AS modelo, 2022 AS ano, 'Cinza' AS cor, 780000.00 AS preco
    UNION ALL
    SELECT 'Chevrolet', 'Camaro SS', 2021, 'Vermelho', 420000.00
) AS exemplos
WHERE NOT EXISTS (SELECT 1 FROM carros);
