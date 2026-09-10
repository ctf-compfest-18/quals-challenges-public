import os

import sandbox
import sandbox.sui_helper as helper

PACKAGE_PATH = os.getenv("SUI_CHALLENGE_PACKAGE", "/home/ctf/setup")


def deploy(rpc_url, admin_config, admin_address, player_address, package_id=None):
    if package_id is None:
        package_id = helper.publish(admin_config, PACKAGE_PATH)

    cap = helper.admin_cap(admin_config, package_id, admin_address)
    result = helper.call(
        admin_config, package_id, "setup", "initialize", args=[cap, player_address]
    )

    setup_id = helper.find_object(result, package_id, "setup", "Setup", shared=True)
    objects = {
        "REGISTRY": helper.find_object(result, package_id, "registry", "RouteRegistry", shared=True),
        "VAULT": helper.find_object(result, package_id, "vault", "IncentiveVault", shared=True),
        "POOL": helper.find_object(result, package_id, "pool", "RoutePool", shared=True),
        "ACCOUNT": helper.find_object(result, package_id, "vault", "OperatorAccount", owned=True),
        "CONFIG": helper.find_object(result, package_id, "config", "GlobalConfig", shared=True),
        "ORACLE": helper.find_object(result, package_id, "oracle", "PriceOracle", shared=True),
    }

    missing = [name for name, value in {"SETUP": setup_id, **objects}.items() if not value]
    if missing:
        raise RuntimeError(f"initialize did not create: {', '.join(missing)}")

    return {"package_id": package_id, "setup": setup_id, "objects": objects}


app = sandbox.run_launcher(deploy)
