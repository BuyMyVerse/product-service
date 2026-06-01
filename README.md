# Spring Boot Demo API     

A simple Spring Boot 3 REST API (Java 17) with in-memory sample data — no database required.

---

## 📦 Project Structure

```
springboot-demo/
├── src/
│   └── main/
│       ├── java/com/demo/api/
│       │   ├── DemoApiApplication.java
│       │   ├── controller/
│       │   │   ├── UserController.java
│       │   │   └── ProductController.java
│       │   ├── model/
│       │   │   ├── User.java
│       │   │   ├── Product.java
│       │   │   └── ApiResponse.java
│       │   └── service/
│       │       ├── UserService.java
│       │       └── ProductService.java
│       └── resources/
│           └── application.properties
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 🚀 Running the Application

### Option 1 — Maven (local Java 17 required)
```bash
mvn clean spring-boot:run
```

### Option 2 — Docker Compose (recommended)
```bash
docker compose up --build
```

### Option 3 — Docker manually
```bash
# Build the image
docker build -t demo-api .

# Run the container
docker run -p 8080:8080 demo-api
```

---

## 🔗 API Endpoints

### Users
| Method | URL                          | Description        |
|--------|------------------------------|--------------------|
| GET    | /api/v1/users                | List all users     |
| GET    | /api/v1/users/{id}           | Get user by ID     |

### Products
| Method | URL                                      | Description                  |
|--------|------------------------------------------|------------------------------|
| GET    | /api/v1/products                         | List all products             |
| GET    | /api/v1/products/{id}                    | Get product by ID             |
| GET    | /api/v1/products/category/{category}     | Filter products by category   |

---

## 📖 Swagger UI

Once running, visit:
```
http://localhost:8080/swagger-ui.html
```

---

## 🧪 Sample curl Commands

```bash
# Get all users
curl http://localhost:8080/api/v1/users

# Get user by ID
curl http://localhost:8080/api/v1/users/1

# Get all products
curl http://localhost:8080/api/v1/products

# Get product by ID
curl http://localhost:8080/api/v1/products/2

# Get products by category
curl http://localhost:8080/api/v1/products/category/Electronics
```

---

## ✅ Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Web MVC
- SpringDoc OpenAPI (Swagger UI)
- Maven
- Docker (multi-stage build)
