FROM ghcr.io/fintlabs/fint-personalmappe-frontend:latest as client

FROM gradle:8.10-jdk21 as builder
USER root
COPY . .
COPY --from=client /src/build/ src/main/resources/static/
RUN gradle --no-daemon build

FROM gcr.io/distroless/java21
ENV JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-provisioner-personalmappe-*.jar /app/fint-provisioner-personalmappe.jar
CMD ["/app/fint-provisioner-personalmappe.jar"]
