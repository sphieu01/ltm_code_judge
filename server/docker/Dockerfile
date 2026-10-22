FROM alpine:3.19

LABEL maintainer="LTM Auto Grader Team"
LABEL description="Sandbox runner container for C, C++, Python, and Java grading"

# Cài đặt các trình biên dịch: gcc, g++, python3, openjdk21
RUN apk update && \
    apk add --no-cache \
    build-base \
    gcc \
    g++ \
    python3 \
    openjdk21-jdk \
    bash

# Tạo user không có quyền root 'sandbox' (UID 1000) để chạy code an toàn
RUN addgroup -S sandbox && adduser -S sandbox -G sandbox

WORKDIR /app

# Phân quyền thư mục làm việc cho user sandbox
RUN chown -R sandbox:sandbox /app

# Mặc định chạy dưới quyền sandbox user
USER sandbox

CMD ["bash"]
