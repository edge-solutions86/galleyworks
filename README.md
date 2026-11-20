# lexdrift

[![CI](https://github.com/rkaushik-dev/galleyworks/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/galleyworks/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Gradle](https://img.shields.io/badge/build-Gradle-02303A)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Passage-reuse and near-duplicate detection for legislative instruments.

Given a bill, an ordinance or a session law, lexdrift finds the passages that
already exist in other instruments, quotes both sides, and says which one
printed the language first.

It is built for one question that plain text search cannot answer: a clause
copied across a state line arrives re-styled. The citation is written to the
local reporter, the numbers are typeset the other way round, the subsection is
renumbered, and the enacting clause is different. None of that is redrafting,
and a tool that treats it as redrafting reports a copied clause as original.

## What it does

1. **Ingest.** Enrolled bill text, amendment drafts carrying `{-strike-}` and
   `{+insert+}` markup, or plain text. Structure comes out as a segment tree:
   section, subsection, paragraph, with the printed label chain preserved.
2. **Canonicalize.** Eleven ordered rules fold the differences that are
   typesetting rather than drafting: unicode, whitespace, defined terms,
   statutory citations, internal cross-references, doubled numerals, money,
   dates, enumeration labels, the enacting clause, case, variant spellings and
   possessives. Every rewrite keeps an offset map, so a match can always be
   quoted in the words the legislature actually printed.
3. **Fingerprint.** Winnowed w-shingles, a MinHash signature over an affine
   permutation family, and a stopword-weighted SimHash.
4. **Index.** LSH banding over the signature, plus an inverted index of the
   winnowed fingerprints, held in memory or in a sqlite file.
5. **Match.** Candidates from the banding, then containment scoring, then
   anchor chaining into passages with character spans on both sides.
6. **Report.** Text, JSON, CSV or markdown, each finding with the evidence and
   the argument for it.
7. **Diffusion.** A dated graph over instruments: which families exist, which
   member printed the text first, and how far it travelled.

## Build

Java 21 and the Gradle wrapper in this repository:

```
./gradlew build                 # compile, format check, tests, coverage
./gradlew installDist           # the command line in build/install/lexdrift
./gradlew :service:bootJar      # the HTTP service in service/build/libs
```

The repository holds two Gradle projects. The root project is the engine and
the `lexdrift` command line, with no web stack. `service/` is the read-only HTTP
service on Spring Boot; its jar runs the same command line, with `lexdrift
serve` able to start the server:

```
java -jar service/build/libs/lexdrift-service-*.jar serve --index corpus.sqlite
```

## Use it from the command line

```
lexdrift ingest bills/                       parse a corpus and report what came back
lexdrift index --index corpus.sqlite bills/  build or extend an index
lexdrift query --index corpus.sqlite bill.txt --jurisdiction mt
lexdrift explain --index corpus.sqlite bill.txt --jurisdiction mt
lexdrift report bills/ --out reports/        one report per instrument
lexdrift diffusion bills/ --timeline         which families exist and when
lexdrift diff before.txt after.txt --jurisdiction wy
lexdrift manifest bills/ --out corpus.yaml   write a corpus manifest
lexdrift stats --index corpus.sqlite         describe what an index holds
lexdrift check --index corpus.sqlite --repair
lexdrift config                              show the settings a run would use
lexdrift serve --index corpus.sqlite         the read-only HTTP service
lexdrift completion bash                     shell completion
```

Exit codes are part of the contract: `0` nothing to flag, `1` a finding, `2` bad
arguments, `3` a lexdrift error with its stable code on stderr. `query
--fail-over 0.4` is therefore a gate in a drafting pipeline.

`--log-level` (debug, info, warning, error) and `--log-format` (plain, json)
come before the command and decide what is written to stderr.

## Use it as a library

```java
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import java.nio.file.Path;

Engine engine = new Engine(new EngineConfig());
RunResult result = engine.run(Lexdrift.discover(Path.of("bills")));
for (InstrumentReport report : result.reports()) {
    if (!report.isEmpty()) {
        System.out.println(report.key().slug() + " " + report.summary().reuseRatio());
    }
}
```

`Lexdrift.discover`, `Lexdrift.loadManifest` and `Lexdrift.loadConfig` are the
entry points; every type a caller needs lives in the root package
`io.github.rkaushikdev.galleyworks`, apart from the report the engine returns.

## The HTTP service

| route | answers |
| --- | --- |
| `GET /health` | status, version, engine digest, instrument and segment counts |
| `GET /v1/instruments` | one page of the indexed corpus (`limit`, `offset`, `jurisdiction`) |
| `GET /v1/instruments/{jurisdiction}/{session}/{chamber}/{number}` | every indexed segment of one instrument |
| `POST /v1/query` | what a document reuses, without indexing it |

Bodies are JSON with snake_case fields. The API document is served at
`/openapi.json` and browsable at `/docs`. Set `LEXDRIFT_API_TOKEN` to require
`Authorization: Bearer <token>` on everything except `/health` and the
documentation. Every response carries `x-request-id` and `x-response-time-ms`.

## Configuration

Settings load from a YAML file, then from `LEXDRIFT__` environment variables:

```yaml
shingle:
  width: 5
  winnow_window: 4
lsh:
  permutations: 128
  bands: 32
canon:
  profile: default
match:
  min_containment: 0.35
  same_jurisdiction_containment: 0.6
report:
  min_confidence: weak
```

`lexdrift config` prints what a run would actually use, plus the numbers that
follow from it: the shortest shared passage the winnowing scheme guarantees to
find, the similarity the banding is tuned for, and the digest an index built
with those settings carries. An index refuses to open under a different digest,
because two indexes built with different shingle widths hold fingerprints that
cannot be compared.

## Corpus layout

Either name the files `<jurisdiction>_<session>_<number>.txt` and let discovery
read the identity from them, or write a manifest:

```yaml
root: .
documents:
  - path: wy_2023_HB0142.txt
    jurisdiction: wy
    session: '2023'
    number: HB0142
    chamber: lower
```

A file that does not follow the convention is reported as skipped rather than
loaded under a guessed key. A wrongly attributed instrument corrupts every later
finding about where language originated.

## What it does not do

It finds text reuse. It does not find two people independently drafting the same
rule in different words, and it does not decide whether reuse is copying,
compliance or a shared model. Direction comes from enactment dates and nothing
else; where the dates do not settle it, the edge is reported as undirected.

## Source and licence

Source: https://github.com/rkaushik-dev/galleyworks. Report problems at
https://github.com/rkaushik-dev/galleyworks/issues.

Licence: proprietary.
