from dataclasses import dataclass, asdict
from typing import List, Optional


@dataclass
class AccountInfo:
    address: str
    private_key: str
    public_key: str


@dataclass
class NodeInfo:
    port: int | str
    accounts: List[AccountInfo]
    pid: int
    uuid: str
    team: str
    seed: Optional[str] = None
    contract_addr: Optional[str] = None
    created_at: Optional[float] = None
    expires_at: Optional[float] = None
    status: Optional[str] = None  # "starting" | "ready" | "terminating"

    def __post_init__(self):
        if isinstance(self.accounts, list):
            self.accounts = [
                AccountInfo(**acc) if isinstance(acc, dict) else acc
                for acc in self.accounts
            ]

    def to_dict(self):
        return asdict(self)
