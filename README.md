# Order Processing Service

Микросервис обработки заказов на Java и Spring Boot.

Проект демонстрирует:

- REST API для работы с заказами;
- хранение данных в PostgreSQL через Spring Data JPA;
- миграции базы данных через Flyway;
- отправку и обработку сообщений через RabbitMQ;
- документацию API через Springdoc OpenAPI;
- unit- и integration-тесты.

## Стек

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- Flyway
- RabbitMQ
- Springdoc OpenAPI
- JUnit 5, Mockito, MockMvc, H2, Spring AMQP Test

## Быстрый старт

После клонирования репозитория нужно перейти в папку Spring Boot проекта:

```bash
git clone https://github.com/xludw1ng/test_parnas.git
cd test_parnas/test_parnas
```

Если проект уже открыт из папки, где находится `pom.xml`, команду `cd` выполнять не нужно.

## Запуск инфраструктуры

Для локального запуска нужны PostgreSQL и RabbitMQ. Они поднимаются через Docker Compose:

```bash
docker compose up -d
```

Проверить состояние контейнеров:

```bash
docker compose ps
```

Сервисы:

```text
PostgreSQL:
  host: localhost
  port: 5457
  database: orders_db
  user: orders_user
  password: orders_password

RabbitMQ:
  AMQP port: 5672
  Management UI: http://localhost:15672
  user: orders_user
  password: orders_password
```

Остановить инфраструктуру:

```bash
docker compose down
```

Если нужно удалить также volume с данными:

```bash
docker compose down -v
```

## Запуск приложения

После запуска Docker Compose:

```bash
./mvnw spring-boot:run
```

Приложение запускается на порту:

```text
http://localhost:8099
```

Swagger UI доступен по стандартному пути:

```text
http://localhost:8099/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8099/v3/api-docs
```

## REST API

### Создать заказ

```bash
curl -i -X POST http://localhost:8099/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Ivan Petrov",
    "items": [
      {
        "productName": "Coffee",
        "quantity": 2,
        "price": 120.50
      },
      {
        "productName": "Cake",
        "quantity": 1,
        "price": 250.00
      }
    ]
  }'
```

Ожидаемый статус:

```text
201 Created
```

После создания заказа приложение отправляет сообщение в очередь `order.created`.
`@RabbitListener` читает сообщение, логирует его и меняет статус заказа на `PROCESSING`.

### Получить список заказов

Без фильтра:

```bash
curl -i "http://localhost:8099/api/orders?page=0&size=10&sort=orderDate,desc"
```

С фильтром по статусу:

```bash
curl -i "http://localhost:8099/api/orders?status=PROCESSING&page=0&size=10&sort=orderDate,desc"
```

Ожидаемый статус:

```text
200 OK
```

### Получить заказ по id

```bash
curl -i http://localhost:8099/api/orders/{orderId}
```

Пример:

```bash
curl -i http://localhost:8099/api/orders/00000000-0000-0000-0000-000000000000
```

Если заказ не найден, вернется:

```text
404 Not Found
```

### Обновить статус заказа

```bash
curl -i -X PUT http://localhost:8099/api/orders/{orderId}/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "COMPLETED"
  }'
```

Доступные статусы:

```text
CREATED
PROCESSING
COMPLETED
CANCELED
```

Ожидаемый статус:

```text
200 OK
```

## Пример ошибки валидации

```bash
curl -i -X POST http://localhost:8099/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "",
    "items": []
  }'
```

Ожидаемый статус:

```text
400 Bad Request
```

Пример ответа:

```json
{
  "timestamp": "2026-06-14T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "customerName: Customer name is required; items: Order must contain at least one item",
  "path": "/api/orders"
}
```

## Архитектура

Проект разделен на слои:

```text
controller  - REST endpoints
service     - бизнес-логика заказов
repository  - доступ к базе данных через Spring Data JPA
models      - JPA-сущности и enum статусов
dto         - request/response DTO и сообщение RabbitMQ
messaging   - RabbitMQ listener
config      - RabbitMQ и OpenAPI конфигурация
exception   - глобальная обработка ошибок
```

Основной поток работы:

```text
REST request
  -> OrderController
  -> OrderService
  -> PostgreSQL
  -> RabbitMQ queue order.created
  -> OrderCreatedListener
  -> обновление статуса заказа на PROCESSING
```

## База данных и миграции

Схема создается через Flyway:

```text
src/main/resources/db/migration/V1__create_orders_schema.sql
```

Hibernate настроен в режиме:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Это значит, что Hibernate не создает таблицы сам, а только проверяет соответствие JPA-сущностей схеме, созданной Flyway.

В миграции созданы:

- таблица `orders`;
- таблица `order_items`;
- внешний ключ `order_items.order_id -> orders.id`;
- индекс по `orders.status`;
- индекс по `orders.customer_name`;
- индекс по `order_items.order_id`.

## Кастомный SQL-запрос

В `OrderRepository` реализован запрос для расчета общей суммы заказов клиента:

```sql
SELECT COALESCE(SUM(oi.price * oi.quantity), 0)
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.customer_name = :customerName
```

Запрос использует `JOIN` и агрегацию `SUM(price * quantity)`.

## Тесты

Запуск всех тестов:

```bash
./mvnw test
```

В проекте есть:

- unit-тест `OrderService` с mock `OrderRepository` и `RabbitTemplate`;
- integration-тест с `MockMvc`, H2 и `TestRabbitTemplate`;
- проверка `POST /api/orders`;
- проверка сохранения заказа и позиций в БД;
- проверка обработки сообщения listener-ом и смены статуса на `PROCESSING`.

## Полезные команды

Собрать проект без тестов:

```bash
./mvnw -DskipTests compile
```

Запустить тесты:

```bash
./mvnw test
```

Посмотреть логи контейнеров:

```bash
docker compose logs -f
```

Посмотреть очереди RabbitMQ через UI:

```text
http://localhost:15672
```
