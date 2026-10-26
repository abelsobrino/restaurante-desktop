LA FONDA - DESKTOP

La Fonda (escritorio) es un sistema de gestión interna para restaurantes que permite administrar las operaciones de atención, cocina, caja, inventario y administración. Su objetivo es centralizar la información, optimizar los procesos operativos y facilitar el control de los recursos y las actividades del establecimiento.

Funcionalidades
- Gestión de pedidos: registro, seguimiento y administración de pedidos realizados por los clientes.
- Atención y cocina: gestión de mesas, visualización de pedidos y seguimiento de su preparación.
- Caja y cobros: registro de pagos, control de movimientos financieros y cierre de caja individual por usuario.
- Inventario: administración de ingredientes, control de existencias, movimientos de almacén y alertas de stock bajo.
- Compras y proveedores: registro de adquisiciones, administración de proveedores y seguimiento de abastecimiento.
- Recetas y producción: gestión de ingredientes por plato y control de procesos de producción.
- Administración: gestión de usuarios, control de accesos y supervisión de operaciones.
- Reportes: generación de informes personalizados mediante filtros y exportación de documentos en PDF y Excel.

Tecnologías
- Java 21: lenguaje principal de desarrollo.
- JavaFX y FXML: construcción de interfaces gráficas de escritorio.
- Spring Boot: configuración de la aplicación y gestión de componentes.
- Spring Data JPA e Hibernate: persistencia de información y acceso a datos.
- PostgreSQL y Supabase: almacenamiento centralizado de la información del restaurante.
- Maven: administración de dependencias, compilación y ejecución del proyecto.

Estructura del proyecto
- src/main/java/: código fuente de la aplicación.
- controller/: controladores de las interfaces y gestión de eventos.
- model/: entidades y modelos de datos.
- service/: lógica de negocio y operaciones del sistema.
- repository/: acceso y persistencia de información.
- config/: configuración general y componentes de la aplicación.
- util/: clases y funciones auxiliares.
- src/main/resources/fxml/: interfaces gráficas desarrolladas con FXML.
- application.properties: configuración de Spring Boot y conexión a PostgreSQL.
- pom.xml: dependencias y configuración de Maven.

Instalación y ejecución
1. Clona o descarga el repositorio y abre la carpeta del proyecto en IntelliJ IDEA: https://github.com/abelsobrino/restaurante-desktop.git
2. Verifica que tengas instalado Java 21 y que Maven esté disponible mediante el proyecto.
3. Configura las variables de entorno LAFONDA_DB_URL, LAFONDA_DB_USER y LAFONDA_DB_PASSWORD con los datos de conexión a Supabase.
4. Comprueba que la base de datos PostgreSQL contenga las tablas y funciones necesarias para el funcionamiento del sistema.
5. Ejecuta el proyecto desde IntelliJ IDEA o mediante el comando `.\mvnw.cmd javafx:run` en Windows.
6. Inicia sesión con una cuenta autorizada para acceder a los módulos correspondientes.

Arquitectura
El sistema utiliza una arquitectura por capas basada en el patrón MVC, separando las interfaces gráficas, los controladores, la lógica de negocio y el acceso a datos. Spring Boot administra los componentes y dependencias, mientras que JPA e Hibernate permiten interactuar con PostgreSQL.

La aplicación comparte una base de datos centralizada con la plataforma web de La Fonda, permitiendo mantener la información de pedidos, pagos e inventario entre ambos sistemas.

Seguridad
La Fonda incorpora autenticación de usuarios, protección de contraseñas mediante BCrypt y control de acceso según roles. La conexión a la base de datos admite variables de entorno para evitar exponer credenciales en el código fuente. Asimismo, incorpora mecanismos de auditoría para determinadas operaciones administrativas.

Estado
Proyecto académico en desarrollo, correspondiente a la versión 2.0.1. Incluye funcionalidades de gestión operativa, inventario, caja y reportes. Antes de su implementación en un entorno de producción, se requiere completar las pruebas integrales de funcionamiento, seguridad y operaciones compartidas con la plataforma web.