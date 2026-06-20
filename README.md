# lexdrift

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
   winnowed fingerprints, held in memory or in sqlite.
5. **Match.** Candidates from the banding, then containment scoring, then
   anchor chaining into passages with character spans on both sides.
6. **Report.** Text, JSON, CSV or markdown, each finding with the evidence and
   the argument for it.
7. **Diffusion.** A dated graph over instruments: which families exist, which
   member printed the text first, and how far it travelled.

## Install

```
pip install .            # the engine and the CLI
pip install '.[service]' # and the HTTP service
```

Python 3.12 or newer.

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

## Use it as a library

```python
from lexdrift import Engine, EngineConfig, discover

engine = Engine(EngineConfig())
result = engine.run(discover("bills/"))
for report in result.reports:
    if not report.is_empty:
        print(report.key.slug, report.summary.reuse_ratio)
```

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
