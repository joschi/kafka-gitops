FROM eclipse-temurin:21.0.1_12-jre-jammy
LABEL org.opencontainers.image.title="kafka-gitops"
LABEL org.opencontainers.image.description="GitOps for Apache Kafka"
LABEL org.opencontainers.image.url="https://github.com/joschi/kafka-gitops"
LABEL org.opencontainers.image.documentation="https://joschi.github.io/kafka-gitops/#/installation?id=docker"
LABEL org.opencontainers.image.source="https://github.com/joschi/kafka-gitops"
LABEL org.opencontainers.image.licenses="Apache-2.0"
COPY ./build/output/kafka-gitops /usr/local/bin/kafka-gitops
