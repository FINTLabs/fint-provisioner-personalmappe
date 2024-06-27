FROM ghcr.io/fintlabs/fint-personalmappe-frontend:vilvivite-b812e2d as client

FROM gradle:6.3.0-jdk11 as builder
USER root
COPY . .
COPY --from=client /app/build/ src/main/resources/static/
RUN gradle --no-daemon build

FROM gcr.io/distroless/java
ENV JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-provisioner-personalmappe-*.jar /app/fint-provisioner-personalmappe.jar
CMD ["/app/fint-provisioner-personalmappe.jar"]
