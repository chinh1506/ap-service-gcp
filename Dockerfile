# Giai đoạn 1: Build file JAR bằng Maven
FROM maven:3.9.5-eclipse-temurin-17 as builder
WORKDIR /app

# Copy file cấu hình Maven và tải trước dependency để tối ưu cache
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ code và build
COPY src ./src
RUN mvn clean package -DskipTests

# Giai đoạn 2: Tạo Runtime Image (nhẹ và bảo mật)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Cài đặt các thư viện hệ thống cần thiết cho XChart (Vẽ biểu đồ)
# libharfbuzz0b và fontconfig là bắt buộc để tránh lỗi libfontmanager.so
RUN apt-get update && apt-get install -y \
    fontconfig \
    libfreetype6 \
    libharfbuzz0b \
    libgraphite2-3 \
    && rm -rf /var/lib/apt/lists/*

# Copy file JAR từ giai đoạn builder sang
COPY --from=builder /app/target/*.jar app.jar

# Tạo thư mục secrets nếu bạn cần mount file JSON từ ngoài vào
RUN mkdir /secrets

# Thiết lập chế độ Headless cho Java để XChart vẽ biểu đồ không cần màn hình
ENV JAVA_OPTS="-Djava.awt.headless=true -Dfile.encoding=UTF-8"

# Lắng nghe cổng PORT do Cloud Run cấp (mặc định 8080)
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]