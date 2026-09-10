#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path

import numpy as np


MONOMIALS = [mask for mask in range(256) if mask.bit_count() <= 2]
MONOMIAL_INDEX = {mask: index for index, mask in enumerate(MONOMIALS)}


def load_stage(archive_path: Path):
    with zipfile.ZipFile(archive_path) as archive:
        source = archive.read("chall.py").decode()
        records = archive.read("records.bin")
        metadata = json.loads(archive.read("records.json"))
        sealed = json.loads(archive.read("sealed.json"))

    module = {"__name__": "chall"}
    exec(compile(source, "chall.py", "exec"), module)
    return module, records, metadata, sealed


def apply_matrix(rows, value):
    return sum(
        (((rows[row] & value).bit_count() & 1) << row)
        for row in range(8)
    )


def invert_matrix(rows):
    augmented = [rows[row] | (1 << (8 + row)) for row in range(8)]

    for column in range(8):
        pivot = next(
            row for row in range(column, 8)
            if (augmented[row] >> column) & 1
        )
        augmented[column], augmented[pivot] = (
            augmented[pivot],
            augmented[column],
        )

        for row in range(8):
            if row != column and ((augmented[row] >> column) & 1):
                augmented[row] ^= augmented[column]

    return [(augmented[row] >> 8) & 0xFF for row in range(8)]


def row_reduce(vectors):
    basis = {}

    for vector in vectors:
        value = int(vector)

        while value:
            pivot = value.bit_length() - 1

            if pivot in basis:
                value ^= basis[pivot]
                continue

            basis[pivot] = value

            for other in list(basis):
                if other != pivot and ((basis[other] >> pivot) & 1):
                    basis[other] ^= value
            break

    return tuple(basis[pivot] for pivot in sorted(basis, reverse=True))


def nullspace(rows, width):
    matrix = [int(row) for row in rows if row]
    pivots = []
    rank = 0

    for column in range(width):
        pivot = next(
            (
                row for row in range(rank, len(matrix))
                if (matrix[row] >> column) & 1
            ),
            None,
        )

        if pivot is None:
            continue

        matrix[rank], matrix[pivot] = matrix[pivot], matrix[rank]

        for row in range(len(matrix)):
            if row != rank and ((matrix[row] >> column) & 1):
                matrix[row] ^= matrix[rank]

        pivots.append(column)
        rank += 1

    matrix = matrix[:rank]
    free_columns = [column for column in range(width) if column not in pivots]
    vectors = []

    for free in free_columns:
        vector = 1 << free

        for row, pivot in reversed(list(zip(matrix, pivots))):
            if (row & vector).bit_count() & 1:
                vector |= 1 << pivot

        vectors.append(vector)

    return row_reduce(vectors)


def observation(values):
    parity = np.bincount(values, minlength=256) & 1
    inputs = np.arange(256)
    vector = 0

    for monomial in MONOMIALS:
        selected = parity[(inputs & monomial) == monomial]
        if int(selected.sum() & 1):
            vector |= 1 << MONOMIAL_INDEX[monomial]

    return vector


def inverse_q(value, h_function):
    right = value & 0x0F
    top = value >> 4
    return (top ^ h_function(right)) | (right << 4)


def fingerprint(index, matrix_function, h_function):
    inverse = invert_matrix(matrix_function(index))
    truth_table = [
        inverse_q(apply_matrix(inverse, value), h_function)
        for value in range(256)
    ]

    # Möbius transform: truth table -> ANF coefficients.
    for bit in range(8):
        for mask in range(256):
            if mask & (1 << bit):
                truth_table[mask] ^= truth_table[mask ^ (1 << bit)]

    vectors = [1]

    for output_bit in range(8):
        vector = 0

        for monomial in range(256):
            if (truth_table[monomial] >> output_bit) & 1:
                if monomial.bit_count() > 2:
                    raise ValueError("unexpected algebraic degree")
                vector |= 1 << MONOMIAL_INDEX[monomial]

        vectors.append(vector)

    return row_reduce(vectors)


def main():
    repo_root = Path(__file__).resolve().parents[1]

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--archive",
        type=Path,
        default=repo_root / "public" / "astergate.zip",
    )
    args = parser.parse_args()

    gate, records_data, metadata, sealed = load_stage(args.archive)

    records = np.frombuffer(records_data, dtype=np.uint8).reshape(-1, 12)
    equations = [[] for _ in range(12)]
    valid_sets = []

    for entry in metadata["sets"]:
        if entry["d"] <= 8:
            continue

        blocks = records[entry["offset"]:entry["offset"] + entry["count"]]
        valid_sets.append((entry, blocks))

        for position in range(12):
            equations[position].append(observation(blocks[:, position]))

    cancellation_spaces = [
        nullspace(rows, len(MONOMIALS))
        for rows in equations
    ]

    candidate_fingerprints = [
        fingerprint(index, gate["matrix"], gate["_h"])
        for index in range(4096)
    ]

    matrix_indices = []

    for position, space in enumerate(cancellation_spaces):
        matches = []

        for index, candidate in enumerate(candidate_fingerprints):
            inside_space = all(
                all(
                    ((row & vector).bit_count() & 1) == 0
                    for row in equations[position]
                )
                for vector in candidate
            )

            if inside_space:
                matches.append(index)

        if len(matches) != 1:
            raise SystemExit(
                f"byte {position}: expected one candidate, got {matches}"
            )

        matrix_indices.append(matches[0])

    # The first block of a valid affine set is its base point.
    entry, ciphertexts = valid_sets[0]
    plaintext = bytes.fromhex(entry["base"])
    captured = bytes(ciphertexts[0])

    zero_translation_key = [index << 8 for index in matrix_indices]
    predicted = gate["encrypt_block"](plaintext, zero_translation_key)

    key = [
        (matrix_indices[position] << 8)
        | (captured[position] ^ predicted[position])
        for position in range(12)
    ]

    payload = gate["open_sealed"](sealed, key)
    print(f"COMPFEST18{{{payload.hex()}}}")


if __name__ == "__main__":
    main()
