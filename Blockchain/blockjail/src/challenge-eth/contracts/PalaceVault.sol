// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

contract PalaceVault {
    error BadForwarder();
    error BadCard();
    error WrongOrder();
    error TransferFailed();

    bool public heartStolen;
    uint256 public heistActive;
    mapping(address => uint256) public distortions;
    uint256 private phantomIdx;
    uint256 private storeSlot;
    uint256 private storeValue;
    uint256 private storeNext;
    uint256 private clearNext;
    uint256 private drainNext;
    address public immutable BLOCK_JAIL;

    modifier onlyBlockJail() {
        if (msg.sender != BLOCK_JAIL) revert BadForwarder();
        _;
    }

    constructor(address blockJail) payable {
        BLOCK_JAIL = blockJail;
        distortions[blockJail] = 1;
    }

    function beginInfiltration(bytes calldata card) external onlyBlockJail {
        if (card.length != 5 || heistActive != 0) revert BadCard();

        storeSlot = uint8(card[0]);
        storeValue = uint8(card[1]);
        storeNext = uint8(card[2]);
        clearNext = uint8(card[3]);
        drainNext = uint8(card[4]);

        if (storeSlot != 0 || storeValue != 1) revert BadCard();
        if (storeNext > 3 || clearNext > 3 || drainNext > 3) revert BadCard();

        phantomIdx = 2;
        heistActive = 1;
        _dispatch();
        heistActive = 0;
    }

    function _dispatch() private {
        function()[] memory funcs = _gadgets();
        for (uint256 steps; steps < funcs.length; ++steps) {
            uint256 idx = phantomIdx;
            if (idx == funcs.length) {
                if (!heartStolen || distortions[BLOCK_JAIL] != 0 || address(this).balance != 0) {
                    revert BadCard();
                }
                return;
            }
            if (idx > funcs.length) revert BadCard();
            funcs[idx]();
        }
        if (phantomIdx != funcs.length) revert BadCard();
    }

    function _gadgets() private pure returns (function()[] memory funcs) {
        funcs = new function()[](4);
        funcs[0] = _gDrain;
        funcs[1] = _gStop;
        funcs[2] = _gSStore;
        funcs[3] = _gClear;
    }

    function _gSStore() private {
        uint256 slot = storeSlot;
        uint256 value = storeValue;
        assembly {
            sstore(slot, value)
        }
        phantomIdx = storeNext;
    }

    function _gClear() private {
        if (!heartStolen) revert WrongOrder();
        distortions[BLOCK_JAIL] = 0;
        phantomIdx = clearNext;
    }

    function _gDrain() private {
        if (!heartStolen || distortions[BLOCK_JAIL] != 0) revert WrongOrder();
        (bool ok,) = tx.origin.call{value: address(this).balance}("");
        if (!ok) revert TransferFailed();
        phantomIdx = drainNext;
    }

    function _gStop() private {
        phantomIdx = 4;
    }

    function isSolved() external view returns (bool) {
        return heartStolen && distortions[BLOCK_JAIL] == 0 && address(this).balance == 0;
    }

    receive() external payable {}
}
