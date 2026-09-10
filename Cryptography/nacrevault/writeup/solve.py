#!/usr/bin/env python3
from __future__ import annotations

import argparse
import collections
import hashlib
import itertools
import json
import math
import zipfile
from pathlib import Path

import numpy as np

DOMAIN = b"NACREVAULT/V3"
SHELL_SIZE = 8


def inv_mod(value: int, modulus: int) -> int:
    return pow(int(value) % modulus, -1, modulus)


def solve_square(matrix: np.ndarray, vector: np.ndarray, modulus: int) -> np.ndarray:
    matrix = np.asarray(matrix, dtype=np.int64) % modulus
    vector = np.asarray(vector, dtype=np.int64) % modulus
    size = matrix.shape[0]
    augmented = np.concatenate([matrix, vector.reshape(-1, 1)], axis=1) % modulus

    for column in range(size):
        pivot = next(
            row for row in range(column, size)
            if augmented[row, column] % modulus
        )
        if pivot != column:
            augmented[[column, pivot]] = augmented[[pivot, column]]

        augmented[column] = (
            augmented[column] * inv_mod(augmented[column, column], modulus)
        ) % modulus

        for row in range(size):
            if row != column and augmented[row, column]:
                augmented[row] = (
                    augmented[row]
                    - augmented[row, column] * augmented[column]
                ) % modulus

    return augmented[:, -1]


def span_support(matrix: np.ndarray, first: int, second: int, modulus: int):
    left = matrix[:, first]
    right = matrix[:, second]
    row_a = row_b = determinant = None

    for a in range(matrix.shape[0]):
        for b in range(a + 1, matrix.shape[0]):
            value = (
                int(left[a]) * int(right[b])
                - int(left[b]) * int(right[a])
            ) % modulus
            if value:
                row_a, row_b, determinant = a, b, value
                break
        if row_a is not None:
            break

    if row_a is None:
        return None

    inverse = inv_mod(determinant, modulus)
    values_a = matrix[row_a]
    values_b = matrix[row_b]
    alpha = (
        (values_a * int(right[row_b]) - values_b * int(right[row_a]))
        * inverse
    ) % modulus
    beta = (
        (int(left[row_a]) * values_b - int(left[row_b]) * values_a)
        * inverse
    ) % modulus
    reconstructed = (
        left[:, None] * alpha[None, :]
        + right[:, None] * beta[None, :]
    ) % modulus
    return set(np.nonzero(np.all(reconstructed == matrix, axis=0))[0].tolist())


def recover_shells(matrix: np.ndarray, modulus: int) -> list[list[int]]:
    remaining = set(range(matrix.shape[1]))
    shells = []

    while remaining:
        first = min(remaining)
        found = None
        for second in sorted(remaining):
            if second == first:
                continue
            support = span_support(matrix, first, second, modulus)
            if (
                support is not None
                and len(support) == SHELL_SIZE
                and support <= remaining
            ):
                found = support
                break

        if found is None:
            raise RuntimeError(f"failed to recover shell containing column {first}")

        shells.append(sorted(found))
        remaining -= found

    return shells


def shell_components(
    matrix: np.ndarray,
    target: np.ndarray,
    shells: list[list[int]],
    modulus: int,
) -> list[np.ndarray]:
    basis_columns = []
    pairs = []

    for shell in shells:
        pair = None
        for first, second in itertools.combinations(shell, 2):
            if span_support(matrix, first, second, modulus) is not None:
                pair = (first, second)
                break
        if pair is None:
            raise RuntimeError("degenerate shell")
        pairs.append(pair)
        basis_columns.extend(pair)

    coefficients = solve_square(matrix[:, basis_columns], target, modulus)
    components = []
    for index, (first, second) in enumerate(pairs):
        components.append(
            (
                coefficients[2*index] * matrix[:, first]
                + coefficients[2*index + 1] * matrix[:, second]
            ) % modulus
        )
    return components


def shell_patterns() -> list[np.ndarray]:
    patterns = []
    for positive in itertools.combinations(range(SHELL_SIZE), 2):
        remaining = [index for index in range(SHELL_SIZE) if index not in positive]
        for negative in itertools.combinations(remaining, 2):
            value = np.zeros(SHELL_SIZE, dtype=np.int64)
            value[list(positive)] = 1
            value[list(negative)] = -1
            patterns.append(value)
    return patterns


def recover_shell_candidates(
    matrix: np.ndarray,
    components: list[np.ndarray],
    shells: list[list[int]],
    modulus: int,
) -> list[list[np.ndarray]]:
    patterns = shell_patterns()
    result = []

    for shell, component in zip(shells, components):
        local = matrix[:, shell]
        candidates = [
            pattern.copy()
            for pattern in patterns
            if np.array_equal(local @ pattern % modulus, component)
        ]
        if not candidates:
            raise RuntimeError("empty candidate shell")
        result.append(candidates)

    return result


def ring_vector(seed: bytes, size: int, modulus: int) -> np.ndarray:
    raw = hashlib.shake_256(DOMAIN + b"/ring/" + seed).digest(size * 4)
    return np.array(
        [int.from_bytes(raw[4*i:4*i+4], "little") % modulus for i in range(size)],
        dtype=np.int64,
    )


def negacyclic_matrix(vector: np.ndarray, modulus: int) -> np.ndarray:
    size = len(vector)
    matrix = np.zeros((size, size), dtype=np.int64)
    for row in range(size):
        for column in range(size):
            difference = row - column
            value = int(vector[difference % size])
            if difference < 0:
                value = (-value) % modulus
            matrix[row, column] = value
    return matrix


def centered(values: np.ndarray, modulus: int) -> np.ndarray:
    values = np.asarray(values, dtype=np.int64) % modulus
    return np.where(values > modulus // 2, values - modulus, values)


def balanced_split(candidate_lists: list[list[np.ndarray]]):
    order = sorted(
        range(len(candidate_lists)),
        key=lambda index: math.log(len(candidate_lists[index])),
        reverse=True,
    )
    left, right = [], []
    left_size = right_size = 1

    for index in order:
        if left_size <= right_size:
            left.append(index)
            left_size *= len(candidate_lists[index])
        else:
            right.append(index)
            right_size *= len(candidate_lists[index])

    return left, right


def iterate_half(
    group_ids: list[int],
    candidate_lists: list[list[np.ndarray]],
    contributions: list[list[np.ndarray]],
    size: int,
    modulus: int,
):
    ranges = [range(len(candidate_lists[index])) for index in group_ids]
    for choices in itertools.product(*ranges):
        total = np.zeros(size, dtype=np.int64)
        for group, choice in zip(group_ids, choices):
            total = (total + contributions[group][choice]) % modulus
        yield choices, total


def circular_distance(first: int, second: int, modulus: int) -> int:
    difference = (int(first) - int(second)) % modulus
    if difference > modulus // 2:
        difference -= modulus
    return abs(difference)


def combine_candidates(
    ring_matrix: np.ndarray,
    ring_target: np.ndarray,
    shells: list[list[int]],
    candidate_lists: list[list[np.ndarray]],
    modulus: int,
    error_bound: int,
) -> np.ndarray:
    size = len(ring_target)
    contributions = []
    for shell, candidates in zip(shells, candidate_lists):
        contributions.append([
            ring_matrix[:, shell] @ candidate % modulus
            for candidate in candidates
        ])

    left_groups, right_groups = balanced_split(candidate_lists)
    width = 2 * error_bound + 1
    probe_rows = (0, 1, 2)

    bin_options = []
    for target in range(modulus):
        bins = {
            ((target + offset) % modulus) // width
            for offset in range(-error_bound, error_bound + 1)
        }
        bin_options.append(tuple(sorted(bins)))

    buckets = collections.defaultdict(list)
    for choices, contribution in iterate_half(
        left_groups, candidate_lists, contributions, size, modulus
    ):
        key = tuple(int(contribution[row]) // width for row in probe_rows)
        first_values = tuple(int(contribution[row]) for row in probe_rows)
        buckets[key].append((first_values, choices))

    for right_choices, right_contribution in iterate_half(
        right_groups, candidate_lists, contributions, size, modulus
    ):
        wanted = (ring_target - right_contribution) % modulus
        possible_bins = [bin_options[int(wanted[row])] for row in probe_rows]

        for key in itertools.product(*possible_bins):
            for first_values, left_choices in buckets.get(tuple(key), []):
                if not all(
                    circular_distance(first_values[index], wanted[row], modulus)
                    <= error_bound
                    for index, row in enumerate(probe_rows)
                ):
                    continue

                left_contribution = np.zeros(size, dtype=np.int64)
                for group, choice in zip(left_groups, left_choices):
                    left_contribution = (
                        left_contribution + contributions[group][choice]
                    ) % modulus

                residual = centered(
                    ring_target - left_contribution - right_contribution,
                    modulus,
                )
                if int(np.max(np.abs(residual))) > error_bound:
                    continue

                secret = np.zeros(size, dtype=np.int64)
                for group, choice in zip(left_groups, left_choices):
                    secret[shells[group]] = candidate_lists[group][choice]
                for group, choice in zip(right_groups, right_choices):
                    secret[shells[group]] = candidate_lists[group][choice]
                return secret

    raise RuntimeError("no secret found")


def main() -> None:
    repository = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--archive",
        type=Path,
        default=repository / "public" / "nacrevault.zip",
    )
    args = parser.parse_args()

    with zipfile.ZipFile(args.archive) as archive:
        transcript = json.loads(archive.read("transcript.json"))
        packed = bytes.fromhex(archive.read("sample.hex").decode())

    size = int(transcript["n"])
    p = int(transcript["p"])
    q = int(transcript["q"])
    error_bound = int(transcript["e"])
    shell_matrix = np.array(transcript["M"], dtype=np.int64)
    shell_target = np.array(transcript["u"], dtype=np.int64)
    ring_seed = packed[:32]
    salt = packed[32:48]
    ring_target = np.array(
        [
            int.from_bytes(packed[offset:offset + 2], "little")
            for offset in range(48, len(packed), 2)
        ],
        dtype=np.int64,
    )

    shells = recover_shells(shell_matrix, p)
    components = shell_components(shell_matrix, shell_target, shells, p)
    candidates = recover_shell_candidates(
        shell_matrix, components, shells, p
    )

    ring = ring_vector(ring_seed, size, q)
    ring_matrix = negacyclic_matrix(ring, q)
    secret = combine_candidates(
        ring_matrix,
        ring_target,
        shells,
        candidates,
        q,
        error_bound,
    )

    encoded = bytes(int(value) + 1 for value in secret)
    digest = hashlib.sha256(
        DOMAIN + b"/flag/" + salt + encoded
    ).hexdigest()
    print(f"COMPFEST18{{{digest}}}")


if __name__ == "__main__":
    main()
