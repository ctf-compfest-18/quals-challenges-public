// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {BlockJail} from "../contracts/BlockJail.sol";
import {PalaceVault} from "../contracts/PalaceVault.sol";
import {Setup} from "../contracts/Setup.sol";

interface Vm {
    function deal(address, uint256) external;
    function expectRevert(bytes4) external;
    function prank(address) external;
    function prank(address, address) external;
    function store(address, bytes32, bytes32) external;
}

contract BadAgent {}

contract BlockJailTest {
    Vm private constant vm = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));
    bytes private constant CARD = hex"0001030001";

    Setup private setup;
    BlockJail private target;
    PalaceVault private palace;

    function setUp() public {
        setup = new Setup{value: 200 ether}();
        target = setup.TARGET();
        palace = PalaceVault(payable(setup.PALACE()));
    }

    function testInitialStateIsFundedAndUnsolved() public view {
        _assertEq(address(target).balance, 100 ether);
        _assertEq(address(palace).balance, 100 ether);
        _assertEq(palace.distortions(address(target)), 1);
        _assertFalse(palace.heartStolen());
        _assertFalse(setup.isSolved());
    }

    function testOnlyBlockJailCanSubmitCard() public {
        vm.expectRevert(PalaceVault.BadForwarder.selector);
        palace.beginInfiltration(CARD);
    }

    function testRejectsOldDirectStorageWriteCard() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target));
        palace.beginInfiltration(hex"01000103");
    }

    function testRejectsWrongStoreSlot() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target));
        palace.beginInfiltration(hex"0101030001");
    }

    function testRejectsWrongStoreValue() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target));
        palace.beginInfiltration(hex"0002030001");
    }

    function testRejectsOutOfRangeSuccessor() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target));
        palace.beginInfiltration(hex"0001040001");
    }

    function testCycleRevertsAtomically() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target));
        palace.beginInfiltration(hex"0001020001");
        _assertFalse(palace.heartStolen());
    }

    function testCannotDrainBeforeClearingDistortion() public {
        vm.expectRevert(PalaceVault.WrongOrder.selector);
        vm.prank(address(target), address(this));
        palace.beginInfiltration(hex"0001000001");
    }

    function testEarlyStopRevertsAtomically() public {
        vm.expectRevert(PalaceVault.BadCard.selector);
        vm.prank(address(target), address(this));
        palace.beginInfiltration(hex"0001010001");
        _assertFalse(palace.heartStolen());
        _assertEq(palace.distortions(address(target)), 1);
        _assertEq(address(palace).balance, 100 ether);
    }

    function testJopChainCompletesAllPalaceOperations() public {
        uint256 before = address(this).balance;
        vm.prank(address(target), address(this));
        palace.beginInfiltration(CARD);
        _assertEq(address(this).balance, before + 100 ether);
        _assertTrue(palace.heartStolen());
        _assertEq(palace.distortions(address(target)), 0);
        _assertTrue(palace.isSolved());
    }

    function testRejectsUnrestrictedAgent() public {
        BadAgent bad = new BadAgent();
        vm.expectRevert(BlockJail.InvalidAgent.selector);
        vm.prank(address(bad));
        target.enter();
    }

    function testSetupRequiresJailAndPalaceCompletion() public {
        vm.prank(address(target), address(this));
        palace.beginInfiltration(CARD);
        _assertFalse(setup.isSolved());

        vm.deal(address(target), 0);
        vm.store(address(target), bytes32(uint256(1)), bytes32(uint256(1) << 160));
        _assertTrue(setup.isSolved());
    }

    function _assertTrue(bool value) private pure {
        require(value, "assert true");
    }

    function _assertFalse(bool value) private pure {
        require(!value, "assert false");
    }

    function _assertEq(uint256 left, uint256 right) private pure {
        require(left == right, "assert eq");
    }

    receive() external payable {}
}
