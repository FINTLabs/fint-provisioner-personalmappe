FROM ghcr.io/fintlabs/fint-personalmappe-frontend:latest AS client

FROM gradle:9.3-jdk21 AS builder
USER root
COPY . .
COPY --from=client /src/dist/ src/main/resources/static/
RUN gradle --no-daemon build

FROM gcr.io/distroless/java21
ENV JAVA_TOOL_OPTIONS=-XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-provisioner-personalmappe-*.jar /app/fint-provisioner-personalmappe.jar
CMD ["/app/fint-provisioner-personalmappe.jar"]
