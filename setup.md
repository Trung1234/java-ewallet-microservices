# 🛠️ Setup môi trường dev trên Windows 11

Hướng dẫn cài đặt công cụ để phát triển dự án ví điện tử (Java / Spring Boot / Kafka / Kubernetes).

---

## 1. Yêu cầu phần cứng

| Thành phần | Tối thiểu | Khuyến nghị |
|---|---|---|
| RAM | 8GB (bật từng phần) | 16GB trở lên |
| Ổ cứng | 30GB trống | SSD, 50GB trống |
| CPU | 4 nhân | 6–8 nhân |

Hệ thống chạy cùng lúc nhiều container (các service Spring Boot, PostgreSQL, MongoDB, Kafka, Redis), nên RAM là yếu tố quan trọng nhất.

---

## 2. Công cụ bắt buộc

| Công cụ | Mục đích |
|---|---|
| **WSL2** | Nền tảng để Docker chạy trên Windows |
| **JDK 21 hoặc 25** (LTS, Eclipse Temurin) | Biên dịch và chạy Java |
| **Git** | Quản lý mã nguồn |
| **IntelliJ IDEA** (Community là đủ) | IDE chính cho Spring Boot |
| **Docker Desktop** | Chạy Kafka, PostgreSQL, MongoDB, Redis bằng `docker compose` |

> Maven/Gradle **không cần cài riêng**: project tạo từ Spring Initializr có sẵn `mvnw` / `gradlew`.

### Cài WSL2

Mở **PowerShell với quyền Administrator**:

```powershell
wsl --install
```

Sau đó **khởi động lại máy**.

---

## 3. Công cụ nên có

| Công cụ | Mục đích |
|---|---|
| **Postman** hoặc **Bruno** | Test REST API |
| **DBeaver** | Xem và truy vấn PostgreSQL |
| **MongoDB Compass** | Xem dữ liệu MongoDB |
| **Windows Terminal** | Terminal tiện hơn CMD |
| **VS Code** | Sửa nhanh YAML, README, xem trước sơ đồ mermaid |

---

## 4. Công cụ cho Kubernetes (tuần 4)

| Công cụ | Mục đích |
|---|---|
| **kubectl** | Điều khiển cluster Kubernetes |
| **kind** | Chạy cluster Kubernetes bên trong Docker |
| **Helm** (tùy chọn) | Quản lý package cho Kubernetes |

> Cách đơn giản hơn: bật Kubernetes có sẵn trong **Docker Desktop → Settings → Kubernetes**.

---

## 5. Cài nhanh bằng winget

Mở PowerShell và chạy:

```powershell
# Bắt buộc
winget install EclipseAdoptium.Temurin.21.JDK
winget install Git.Git
winget install JetBrains.IntelliJIDEA.Community
winget install Docker.DockerDesktop

# Nên có
winget install Postman.Postman
winget install DBeaver.DBeaver
winget install MongoDB.Compass.Full
winget install Microsoft.WindowsTerminal
winget install Microsoft.VisualStudioCode

# Kubernetes
winget install Kubernetes.kubectl
winget install Kubernetes.kind
winget install Helm.Helm
```

> Nếu một ID báo không tìm thấy, chạy `winget search <tên>` để lấy ID đúng.

---

## 6. Giới hạn RAM cho Docker (quan trọng)

Docker trên WSL2 có thể chiếm rất nhiều RAM. Tạo file:

```
C:\Users\<tên bạn>\.wslconfig
```

với nội dung:

```ini
[wsl2]
memory=8GB
processors=4
```

Áp dụng thay đổi:

```powershell
wsl --shutdown
```

Sau đó mở lại Docker Desktop. Với máy 16GB, để 8GB cho WSL là hợp lý.

---

## 7. Kiểm tra sau khi cài

```powershell
java -version
git --version
docker run hello-world
kubectl version --client
```

✅ Cả bốn lệnh chạy thành công là môi trường đã sẵn sàng.

---

## 8. Checklist

- [ ] WSL2 đã cài và khởi động lại máy
- [ ] `java -version` hiển thị JDK 21 hoặc 25
- [ ] `git --version` chạy được
- [ ] Docker Desktop mở được, `docker run hello-world` thành công
- [ ] Đã tạo `.wslconfig` giới hạn RAM
- [ ] IntelliJ IDEA nhận đúng JDK
- [ ] Postman / DBeaver / MongoDB Compass đã cài
- [ ] `kubectl version --client` chạy được (cho tuần 4)

---

## 9. Bước tiếp theo

1. Clone repo dự án.
2. Chạy hạ tầng: `docker compose -f infra/docker-compose.yml up -d`
3. Mở thư mục `services/` trong IntelliJ và bắt đầu với `user-service`, `wallet-service` (tuần 1).
