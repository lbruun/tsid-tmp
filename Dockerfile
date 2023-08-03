FROM gcr.io/distroless/base-debian11
#FROM ubuntu:latest

COPY target/tsid-k8s-instance-id app
ENTRYPOINT ["/app"]