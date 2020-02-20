FROM gradle:4.10.2-jdk8-alpine as builder
USER root
COPY . .
RUN gradle --no-daemon build

FROM gcr.io/distroless/java
ENV JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-provisioner-personalmappe-*.jar /data/fint-provisioner-personalmappe.jar
CMD ["/data/fint-provisioner-personalmappe.jar"]