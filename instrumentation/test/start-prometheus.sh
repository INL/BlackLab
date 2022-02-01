CONFIG_FILE=`readlink -f ./prometheus.yml`

# This should works if BlackLab is running locally (i.e. not in Docker)
docker run -p 9090:9090 --add-host host.docker.internal:host-gateway -v $CONFIG_FILE:/etc/prometheus/prometheus.yml prom/prometheus
