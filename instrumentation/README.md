# Monitoring BlackLab (e.g. using Prometheus)

These two modules (contributed by Esteban Ginez of Lexion AI) add the ability to monitor any BlackLab server using [Prometheus](https://prometheus.io/) (or any other monitoring system supported by [Micrometer](https://micrometer.io/)).

## Try it out

To try it out for a BlackLab Server instance running on your local machine:

- install Docker (we will run Prometheus in a Docker container)
- add a setting `debug.metricsProvider` to `blacklab-server.yaml` with the metric provider class to use (`PrometheusMetricsProvider` is included in `instrumentation-impl` and works with Prometheus)
- restart BlackLab Server
- check BLS' metrics response that will be used by Prometheus at http://localhost:8080/blacklab-server/ 
- Go to the `test` subdirectory and run the `start-prometheus.sh` script to run Prometheus and start collecting metrics (Ctrl+C stops the container again).
- Open the Prometheus webapp at http://localhost:9090/
- Try graphing a property such as `jvm_memory_used_bytes`

Note that with this testing setup, the data is stored only temporarily inside the container. When you remove the Prometheus container, the data is lost. A named volume would be better if you want to keep the data.

## More information

For more information on Prometheus, see [here](https://prometheus.io/docs/introduction/overview/). Another useful article is [this one](https://wbassler23.medium.com/getting-started-with-prometheus-pt-1-8f95eef417ed), which also links to more resources.

**TODO**: currently, only basic JVM metrics are collected. We want to get BlackLab-specific information as well, including cache status and information about what queries were handled, how long they took, how many hits they returned, etc.
