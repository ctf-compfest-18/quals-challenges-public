from sandbox.type import AccountInfo, NodeInfo


def test_node_info_roundtrip_with_lease_fields():
    acc = AccountInfo(address="0x1", private_key="k", public_key="0x1")
    node = NodeInfo(
        port=9000,
        accounts=[acc],
        pid=123,
        uuid="00000000-0000-0000-0000-000000000001",
        team="team-a",
        seed="{}",
        created_at=100.0,
        expires_at=700.0,
        status="ready",
    )
    d = node.to_dict()
    rebuilt = NodeInfo(**d)
    assert rebuilt.expires_at == 700.0
    assert rebuilt.status == "ready"
