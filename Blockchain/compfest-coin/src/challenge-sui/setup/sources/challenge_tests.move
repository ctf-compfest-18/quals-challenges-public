#[test_only]
module challenge::challenge_tests;

use challenge::setup::{Self, Setup, AdminCap};
use challenge::registry::{Self, RouteRegistry, RouteStrategy};
use challenge::pool::{Self, RoutePool, RoutePosition};
use challenge::vault::{Self, IncentiveVault, OperatorAccount};
use challenge::config::{Self, GlobalConfig};
use challenge::oracle::{Self, PriceOracle};
use challenge::assets::{SUIX, USDC, CFX};
use sui::test_scenario;

const ADMIN: address = @0xAD;
const PLAYER: address = @0xB0B;

fun provision(sc: &mut test_scenario::Scenario) {
    setup::init_for_testing(sc.ctx());
    sc.next_tx(ADMIN);
    let cap = sc.take_from_sender<AdminCap>();
    setup::initialize(&cap, PLAYER, sc.ctx());
    sc.return_to_sender(cap);
}

#[test]
fun setup_starts_unsolved() {
    let mut sc = test_scenario::begin(ADMIN);
    provision(&mut sc);
    sc.next_tx(PLAYER);
    let s = sc.take_shared<Setup>();
    assert!(!setup::is_solved(&s), 0);
    test_scenario::return_shared(s);
    sc.end();
}

#[test]
#[expected_failure(abort_code = setup::ENotQualified)]
fun solve_rejects_unqualified_account() {
    let mut sc = test_scenario::begin(ADMIN);
    provision(&mut sc);
    sc.next_tx(PLAYER);
    let mut s = sc.take_shared<Setup>();
    let acct = sc.take_from_sender<OperatorAccount<CFX>>();
    let cfg = sc.take_shared<GlobalConfig>();
    assert!(vault::earned(&acct) == 0, 1);
    setup::solve(&mut s, &acct, &cfg);
    sc.return_to_sender(acct);
    test_scenario::return_shared(s);
    test_scenario::return_shared(cfg);
    sc.end();
}

#[test]
fun honest_bluechip_claim_falls_far_short_of_target() {
    let mut sc = test_scenario::begin(ADMIN);
    provision(&mut sc);
    sc.next_tx(PLAYER);

    let mut registry_obj = sc.take_shared<RouteRegistry>();
    let mut vault_obj = sc.take_shared<IncentiveVault<CFX>>();
    let mut pool_obj = sc.take_shared<RoutePool<SUIX, USDC>>();
    let mut acct = sc.take_from_sender<OperatorAccount<CFX>>();
    let cfg = sc.take_shared<GlobalConfig>();
    let oracle_obj = sc.take_shared<PriceOracle>();

    pool::open_position<SUIX, USDC>(&pool_obj, sc.ctx());
    sc.next_tx(PLAYER);
    let mut pos = sc.take_from_sender<RoutePosition<SUIX, USDC>>();
    pool::add_liquidity<SUIX, USDC>(&mut pool_obj, &mut pos, 500, &cfg);

    registry::register_route_strategy<SUIX, USDC, challenge::registry::RouteStrategy<challenge::registry::RouteStrategy<challenge::assets::SUIX>>>(
        &mut registry_obj, challenge::registry::RouteStrategy { id: object::new(sc.ctx()), market: registry::canonical_market<SUIX, USDC>(), strategy_type: b"" }, sc.ctx()
    );
    sc.next_tx(PLAYER);
    let strat = sc.take_from_sender<RouteStrategy<challenge::assets::SUIX>>();

    vault::claim_route_incentives<SUIX, USDC, challenge::assets::SUIX>(
        &mut vault_obj, &mut pool_obj, &strat, &pos, &mut acct, &oracle_obj, &cfg, sc.ctx()
    );

    assert!(vault::earned(&acct) < 500, 2);

    sc.return_to_sender(pos);
    sc.return_to_sender(strat);
    sc.return_to_sender(acct);
    test_scenario::return_shared(registry_obj);
    test_scenario::return_shared(vault_obj);
    test_scenario::return_shared(pool_obj);
    test_scenario::return_shared(cfg);
    test_scenario::return_shared(oracle_obj);
    sc.end();
}

#[test]
fun reversed_market_pool_exploit_clears_bounty_and_solves() {
    let mut sc = test_scenario::begin(ADMIN);
    provision(&mut sc);
    sc.next_tx(PLAYER);

    let mut registry_obj = sc.take_shared<RouteRegistry>();
    let mut vault_obj = sc.take_shared<IncentiveVault<CFX>>();
    let mut acct = sc.take_from_sender<OperatorAccount<CFX>>();
    let cfg = sc.take_shared<GlobalConfig>();
    let oracle_obj = sc.take_shared<PriceOracle>();

    registry::register_route_strategy<USDC, SUIX, challenge::assets::SUIX>(
        &mut registry_obj, challenge::registry::RouteStrategy { id: object::new(sc.ctx()), market: registry::canonical_market<USDC, SUIX>(), strategy_type: b"" }, sc.ctx()
    );
    sc.next_tx(PLAYER);
    let strat = sc.take_from_sender<RouteStrategy<challenge::assets::SUIX>>();

    registry::create_route_pool<USDC, SUIX>(&mut registry_obj, &cfg, 1, 1_000_000, sc.ctx());
    sc.next_tx(PLAYER);
    let mut evil_pool = sc.take_shared<RoutePool<USDC, SUIX>>();

    pool::open_position<USDC, SUIX>(&evil_pool, sc.ctx());
    sc.next_tx(PLAYER);
    let mut pos = sc.take_from_sender<RoutePosition<USDC, SUIX>>();

    pool::add_liquidity<USDC, SUIX>(&mut evil_pool, &mut pos, 500, &cfg);

    vault::claim_route_incentives<USDC, SUIX, challenge::assets::SUIX>(
        &mut vault_obj, &mut evil_pool, &strat, &pos, &mut acct, &oracle_obj, &cfg, sc.ctx()
    );
    assert!(vault::earned(&acct) >= 500, 3);

    let mut setup_obj = sc.take_shared<Setup>();
    setup::solve(&mut setup_obj, &acct, &cfg);
    assert!(setup::is_solved(&setup_obj), 4);

    sc.return_to_sender(pos);
    sc.return_to_sender(strat);
    sc.return_to_sender(acct);
    test_scenario::return_shared(registry_obj);
    test_scenario::return_shared(vault_obj);
    test_scenario::return_shared(evil_pool);
    test_scenario::return_shared(setup_obj);
    test_scenario::return_shared(cfg);
    test_scenario::return_shared(oracle_obj);
    sc.end();
}
