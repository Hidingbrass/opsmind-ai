"""Dependency-free BM25 scoring for English and Chinese Runbook text."""

import math
import re
from collections import Counter


_TOKEN_PATTERN = re.compile(r"[a-z0-9]+|[\u4e00-\u9fff]+")
_HAN_PATTERN = re.compile(r"^[\u4e00-\u9fff]+$")


def tokenize(text: str) -> list[str]:
    """Split English words and represent Chinese text with uni/bi-grams."""
    if not isinstance(text, str):
        return []

    tokens: list[str] = []
    for segment in _TOKEN_PATTERN.findall(text.lower()):
        if not _HAN_PATTERN.fullmatch(segment):
            tokens.append(segment)
            continue

        # Character uni/bi-grams avoid a tokenizer dependency while retaining
        # phrase-level matches for Chinese operational terminology.
        tokens.extend(segment)
        tokens.extend(
            segment[index:index + 2]
            for index in range(len(segment) - 1)
        )
        if len(segment) > 2:
            tokens.append(segment)

    return tokens


class BM25Index:
    """Small in-memory BM25 index rebuilt from the current Chroma corpus."""

    def __init__(
        self,
        documents: list[str],
        k1: float = 1.5,
        b: float = 0.75,
    ) -> None:
        self.k1 = k1
        self.b = b
        self.term_frequencies = [Counter(tokenize(text)) for text in documents]
        self.document_lengths = [sum(counts.values()) for counts in self.term_frequencies]
        self.document_count = len(documents)
        self.average_document_length = (
            sum(self.document_lengths) / self.document_count
            if self.document_count
            else 0.0
        )

        self.document_frequencies: Counter[str] = Counter()
        for counts in self.term_frequencies:
            self.document_frequencies.update(counts.keys())

    def score(self, query: str) -> list[float]:
        """Return one BM25 score per indexed document."""
        query_terms = set(tokenize(query))
        if not query_terms or not self.document_count:
            return [0.0] * self.document_count

        scores: list[float] = []
        for counts, document_length in zip(
            self.term_frequencies,
            self.document_lengths,
        ):
            score = 0.0
            length_ratio = (
                document_length / self.average_document_length
                if self.average_document_length
                else 0.0
            )
            for term in query_terms:
                frequency = counts.get(term, 0)
                if not frequency:
                    continue

                document_frequency = self.document_frequencies[term]
                inverse_document_frequency = math.log(
                    1.0
                    + (
                        self.document_count - document_frequency + 0.5
                    ) / (document_frequency + 0.5)
                )
                denominator = frequency + self.k1 * (
                    1.0 - self.b + self.b * length_ratio
                )
                score += inverse_document_frequency * (
                    frequency * (self.k1 + 1.0) / denominator
                )
            scores.append(score)

        return scores
