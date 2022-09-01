readonly TAG=${1:-v10}
readonly DOCKER_IMAGE=nachocode/storage-node
sbt assembly && docker build -t "$DOCKER_IMAGE:$TAG" .
