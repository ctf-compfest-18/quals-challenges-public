bits 32
org 0
%define REQP 0
%define RESP 4
%define S0   8
%define S1   16
_start:
    push ebx
    push esi
    push edi
    push ebp
    xor  eax, eax
    mov  ax, cs
    add  eax, 0x10
    push eax
    call g1
g1:
    add  dword [esp], (stageA - g1)
    retf

bits 64
stageA:
    lea    rax, [rel scratch]
    mov    [rax + REQP], ecx
    mov    [rax + RESP], edx
    mov    rax, 0x46662DE2AE713EE0
    mov    rcx, 0xD1B54A32D192ED03
    imul   rax, rcx
    rol    rax, 17
    mov    rcx, 0x6E7A5380F8318187
    xor    rax, rcx
    mov    r8, rax
    lea    rdx, [rel b_start]
    xor    ecx, ecx
.decB:
    cmp    ecx, (b_end - b_start)
    jge    .init
    mov    rax, 0x9E6C63C6A3C4B1D1
    imul   r8, rax
    mov    rax, 0x2545F4914F6CDD1D
    add    r8, rax
    mov    rax, r8
    shr    rax, 56
    movzx  r9d, byte [rdx + rcx]
    xor    r9b, al
    mov    [rdx + rcx], r9b
    inc    ecx
    jmp    .decB
.init:
    lea    rax, [rel scratch]
    mov    ecx, [rax + REQP]
    mov    r8, [rcx]
    mov    r9, [rcx + 8]
    mov    rax, 0xA6F1C0D93B5E2748
    xor    r8, rax
    lea    rax, [rel scratch]
    mov    [rax + S0], r8
    mov    [rax + S1], r9
    call g2
g2:
    xor    eax, eax
    mov    ax, cs
    sub    eax, 0x10
    mov    dword [rsp + 4], eax
    add    dword [rsp], (b_start - g2)
    retf

bits 32
b_start:
    call getipB
getipB:
    pop  esi
    sub  esi, getipB
    mov  eax, [esi + scratch + S0]
    mov  ecx, [esi + scratch + S1]
    mul  ecx
    add  eax, [esi + scratch + S0]
    mov  edi, edx
    mov  edx, [esi + scratch + S0 + 4]
    adc  edx, edi
    mov  [esi + scratch + S0], eax
    mov  [esi + scratch + S0 + 4], edx
    mov  eax, [esi + scratch + S1]
    mov  edx, [esi + scratch + S1 + 4]
    mov  ecx, eax
    shld ecx, edx, 13
    shld edx, eax, 13
    mov  [esi + scratch + S1], ecx
    mov  [esi + scratch + S1 + 4], edx
    mov  eax, [esi + scratch + S0]
    xor  eax, [esi + scratch + S1]
    mov  [esi + scratch + S0], eax
    mov  eax, [esi + scratch + S0 + 4]
    xor  eax, [esi + scratch + S1 + 4]
    mov  [esi + scratch + S0 + 4], eax
    mov  edi, 0x5F3A19C7
    xor  ecx, ecx
.decC:
    cmp  ecx, (c_end - c_start)
    jge  .goC
    imul edi, edi, 0x2C9277B5
    add  edi, 0xAC564B05
    mov  eax, edi
    shr  eax, 24
    mov  dl, [esi + c_start + ecx]
    xor  dl, al
    mov  [esi + c_start + ecx], dl
    inc  ecx
    jmp  .decC
.goC:
    xor  eax, eax
    mov  ax, cs
    add  eax, 0x10
    push eax
    call g3
g3:
    add  dword [esp], (c_start - g3)
    retf
b_end:

bits 64
c_start:
    lea    rax, [rel scratch]
    mov    r8, [rax + S0]
    mov    r9, [rax + S1]
    add    r9, r8
    rol    r9, 29
    mov    rax, 0xFF51AFD7ED558CCD
    imul   r9, rax
    add    r8, r9
    rol    r8, 17
    lea    rax, [rel scratch]
    mov    [rax + S0], r8
    mov    [rax + S1], r9
    mov    r8, 0xB5297A4D2C1F60E9
    lea    rdx, [rel d_start]
    xor    ecx, ecx
.decD:
    cmp    ecx, (d_end - d_start)
    jge    .goD
    mov    rax, 0x2545F4914F6CDD1D
    imul   r8, rax
    mov    rax, 0x9E6C63C6A3C4B1D1
    add    r8, rax
    mov    rax, r8
    shr    rax, 56
    movzx  r9d, byte [rdx + rcx]
    xor    r9b, al
    mov    [rdx + rcx], r9b
    inc    ecx
    jmp    .decD
.goD:
    call g4
g4:
    xor    eax, eax
    mov    ax, cs
    sub    eax, 0x10
    mov    dword [rsp + 4], eax
    add    dword [rsp], (d_start - g4)
    retf
c_end:

bits 32
d_start:
    call getipD
getipD:
    pop  esi
    sub  esi, getipD
    mov  ecx, [esi + scratch + RESP]
    mov  eax, [esi + scratch + S0]
    xor  eax, [esi + scratch + S1]
    mov  [ecx], eax
    mov  eax, [esi + scratch + S0 + 4]
    xor  eax, [esi + scratch + S1 + 4]
    mov  [ecx + 4], eax
    mov  eax, [esi + scratch + S0]
    add  eax, [esi + scratch + S1]
    mov  edx, [esi + scratch + S0 + 4]
    adc  edx, [esi + scratch + S1 + 4]
    mov  [ecx + 8], eax
    mov  [ecx + 12], edx
    pop  ebp
    pop  edi
    pop  esi
    pop  ebx
    ret
d_end:

scratch:
    times 32 db 0

foot:
    dd b_start
    dd b_end
    dd c_start
    dd c_end
    dd d_start
    dd d_end
