--liquibase formatted sql

--changeset student:001-create-animals-table
CREATE TABLE animals (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    species VARCHAR(100) NOT NULL,
    age INTEGER NOT NULL,
    habitat VARCHAR(150) NOT NULL
);

INSERT INTO animals (name, species, age, habitat) VALUES
('Сімба', 'Лев африканський', 5, 'Савана'),
('Шкіпер', 'Пінгвін імператорський', 3, 'Антарктида'),
('Балу', 'Ведмідь бурий', 8, 'Карпатський ліс');
