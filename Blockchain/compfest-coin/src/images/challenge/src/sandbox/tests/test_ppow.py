from sandbox.ppow import Challenge, check


def test_accepts_redpwn_v012_solution() -> None:
    challenge = Challenge.from_string(
        "s.AAAnEA==.ZY3opNSf9x9b6f5Pi+Vp+A=="
    )
    solution = (
        "s.GwMq5q+/BP2WDUPeBjO9qGyTQACzqnNTaDGE8gRCxjnTciTzT29tfpj3Cuz/"
        "sOXULpIrrKVYe5aEG3KI0N8PudC/rmMJSWRBjRUsc0eVbJoniKeodPDCoEE6OjIB"
        "i343GRxIiHox93FPkoTch/fNYmVxE1UfDVkhllwEEP96I5nrS3tFy7MCkXyufmxU"
        "XuW0zPr9SRHwgJkIZolJano2Tw=="
    )

    assert check(challenge, solution)


def test_generated_solution_round_trip() -> None:
    challenge = Challenge(7, 0x123456789ABCDEF)

    assert check(challenge, challenge.solve())
