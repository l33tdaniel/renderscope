# RenderScope

RenderScope is an experimental client-side profiler for block-entity renderers
on NeoForge 1.21.1. It measures conventional block-entity renderer calls
without replacing the renderer or Minecraft's terrain pipeline.

The first milestone records:

- inclusive render-thread elapsed time;
- current-thread CPU time when the JVM exposes it;
- invocation and failed-invocation counts;
- emitted vertex counts;
- estimated vertex bytes based on each requested render type's vertex format;
- render-type request counts; and
- block-entity registry IDs and renderer class names.

Estimated vertex bytes are not GPU upload bytes. RenderScope measures the
geometry submitted by the renderer before downstream batching, deduplication,
or upload behavior.

## Usage

Install the mod in a NeoForge 1.21.1 client, enter a representative scene, and
run:

```text
/renderscope start
/renderscope stop
/renderscope top
/renderscope export json
```

CSV export is available with `/renderscope export csv`. Reports are written to
`renderscope-reports` inside the Minecraft game directory.

`/renderscope start` and `/renderscope start full` collect all metrics.
`/renderscope start cpu` preserves the original buffer objects and collects
timing only; use it when a renderer is incompatible with wrapped vertex
consumers.

Profiling is disabled by default. Full-mode vertex interception adds overhead
while a capture is active, so compare relative offenders within the same
capture environment rather than treating the recorded time as zero-overhead.

## Privacy

RenderScope has no telemetry and makes no network requests. Reports do not
include player names, world names, coordinates, server addresses, chat,
hardware identifiers, or file-system paths.

Reports do contain installed content namespaces, block-entity registry IDs,
and renderer class names. These can reveal which mods contributed the sampled
block entities, so inspect a report before publishing it if modpack composition
is sensitive.

## Development

Java 21 is required.

```bash
./gradlew build
```

The output JAR is created under `build/libs`.

## Scope

This prototype intentionally does not provide a new renderer, GPU backend,
automatic mesh retention, or telemetry service. The next planned layer is a
deterministic visual-regression harness so renderer optimizations can be tested
against color and depth output before adoption.

## License

RenderScope is available under the MIT License. The NeoForge MDK template
license is retained separately in `TEMPLATE_LICENSE.txt`.
