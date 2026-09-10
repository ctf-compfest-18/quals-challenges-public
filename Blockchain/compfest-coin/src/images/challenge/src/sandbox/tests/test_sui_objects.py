from sandbox import sui_helper

PKG = "0xaaa"
TX = {
    "objectChanges": [
        {"objectType": f"{PKG}::setup::Setup", "objectId": "0x1", "owner": {"Shared": {}}},
        {"objectType": f"{PKG}::challenge::IncentiveVault<{PKG}::assets::CFX>",
         "objectId": "0x2", "owner": {"Shared": {}}},
        {"objectType": f"{PKG}::challenge::OperatorAccount<{PKG}::assets::CFX>",
         "objectId": "0x3", "owner": {"AddressOwner": "0xplayer"}},
    ]
}


def test_find_object_matches_plain_type():
    assert sui_helper.find_object(TX, PKG, "setup", "Setup", shared=True) == "0x1"


def test_find_object_matches_generic_type():
    assert sui_helper.find_object(TX, PKG, "challenge", "IncentiveVault", shared=True) == "0x2"


def test_find_object_respects_ownership():
    assert sui_helper.find_object(TX, PKG, "challenge", "OperatorAccount", owned=True) == "0x3"
    assert sui_helper.find_object(TX, PKG, "challenge", "OperatorAccount", shared=True) is None


def test_find_object_returns_none_when_absent():
    assert sui_helper.find_object(TX, PKG, "setup", "Nope", shared=True) is None
