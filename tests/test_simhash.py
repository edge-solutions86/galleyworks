from __future__ import annotations

import pytest

from lexdrift.errors import MatchError
from lexdrift.fingerprint.hashing import hash_token
from lexdrift.fingerprint.simhash import (
    simhash,
    simhash_distance,
    simhash_of_tokens,
    simhash_similarity,
    weighted_features,
)
from lexdrift.util.bitops import MASK64

CLAUSE = "the board shall publish a schedule of appropriations not later than sixty days".split()


def test_an_empty_feature_set_signs_as_zero() -> None:
    assert simhash([]) == 0
    assert simhash_of_tokens([]) == 0


def test_a_signature_fits_in_64_bits() -> None:
    assert 0 <= simhash_of_tokens(CLAUSE) <= MASK64


def test_signing_is_deterministic() -> None:
    assert simhash_of_tokens(CLAUSE) == simhash_of_tokens(CLAUSE)


def test_signing_is_order_independent() -> None:
    assert simhash_of_tokens(CLAUSE) == simhash_of_tokens(list(reversed(CLAUSE)))


def test_identical_texts_are_zero_apart() -> None:
    assert simhash_distance(simhash_of_tokens(CLAUSE), simhash_of_tokens(CLAUSE)) == 0
    assert simhash_similarity(simhash_of_tokens(CLAUSE), simhash_of_tokens(CLAUSE)) == 1.0


def test_one_changed_word_moves_only_a_few_bits() -> None:
    changed = [*CLAUSE[:-1], "months"]
    distance = simhash_distance(simhash_of_tokens(CLAUSE), simhash_of_tokens(changed))
    assert 0 < distance < 20


def test_unrelated_texts_are_far_apart() -> None:
    other = "an appropriation for the construction of a county fairground building".split()
    near = simhash_distance(simhash_of_tokens(CLAUSE), simhash_of_tokens([*CLAUSE[:-1], "weeks"]))
    far = simhash_distance(simhash_of_tokens(CLAUSE), simhash_of_tokens(other))
    assert far > near


def test_negative_weights_are_rejected() -> None:
    with pytest.raises(MatchError, match="must not be negative"):
        simhash([(hash_token("a"), -1.0)])


def test_a_zero_weight_feature_does_not_move_the_signature() -> None:
    base = [(hash_token(token), 1.0) for token in CLAUSE]
    padded = [*base, (hash_token("zzz"), 0.0)]
    assert simhash(base) == simhash(padded)


def test_default_weights_are_one() -> None:
    features = weighted_features(["board", "shall"])
    assert [weight for _, weight in features] == [1.0, 1.0]


def test_frequency_weighting_demotes_common_tokens() -> None:
    frequency = {"the": 100, "escheat": 1}
    features = dict(weighted_features(["the", "escheat"], frequency, corpus_size=100))
    assert features[hash_token("the")] < features[hash_token("escheat")]


def test_frequency_weighting_needs_a_corpus_size() -> None:
    features = weighted_features(["the"], {"the": 100}, corpus_size=0)
    assert features[0][1] == 1.0


def test_weighting_changes_which_bits_win() -> None:
    tokens = ["the", "board", "the", "escheat"]
    frequency = {"the": 500, "board": 200, "escheat": 1}
    plain = simhash(weighted_features(tokens))
    weighted = simhash(weighted_features(tokens, frequency, corpus_size=500))
    assert plain != weighted


def test_boilerplate_dominates_without_weights() -> None:
    boilerplate = ["notwithstanding", "any", "other", "provision", "of", "law"] * 4
    left = simhash_of_tokens([*boilerplate, "escheat"])
    right = simhash_of_tokens([*boilerplate, "appropriation"])
    assert simhash_distance(left, right) < 12
