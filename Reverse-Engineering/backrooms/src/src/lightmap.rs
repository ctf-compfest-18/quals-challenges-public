// Precomputed ambient occlusion probe grid for scene lighting.
// Generated offline — do not modify.

pub const PROBE_GRID_COLS: usize = 120;
pub const PROBE_GRID_ROWS: usize = 4;

// AO bake lookup table (packed probe visibility samples)
const AO_BAKE_LUT: [u8; 60] = [
    0x4A, 0xBF, 0xE0, 0x17, 0xBA, 0x9A, 0x05, 0x1B, 0x4A, 0xCA, 0xAB, 0xDC,
    0x34, 0xFF, 0xA4, 0x30, 0xE0, 0x83, 0x8B, 0xD4, 0x72, 0x75, 0x0B, 0x0F,
    0x60, 0xBA, 0xBB, 0x7B, 0x13, 0xD1, 0x3E, 0x00, 0xE8, 0x2B, 0xE1, 0x99,
    0xA9, 0xCB, 0xA3, 0xAA, 0x95, 0xB5, 0xDF, 0x39, 0xD4, 0xE3, 0x1B, 0x74,
    0xAD, 0x40, 0x9B, 0xF6, 0x6E, 0x1E, 0xFF, 0xE1, 0x64, 0x5A, 0x85, 0x6F,
];

/// Unpack the baked AO probe visibility into per-sample weights.
pub fn unpack_probe_weights() -> [u8; 480] {
    let mut out = [0u8; 480];
    let mut s: u32 = 0xA3F1_924D;
    let mut p = 0usize;
    let mut i = 0usize;
    while i < 60 {
        s = s.wrapping_mul(1_103_515_245).wrapping_add(12345);
        let k = ((s >> 16) & 0xFF) as u8;
        let rot = (i % 7) + 1;
        let mut v = AO_BAKE_LUT[i].wrapping_sub((i as u8).wrapping_mul(13).wrapping_add(37));
        v = (v >> rot as u32) | (v << (8 - rot) as u32);
        v ^= k;
        let mut b: i32 = 7;
        while b >= 0 {
            out[p] = (v >> b as u32) & 1;
            p += 1;
            b -= 1;
        }
        i += 1;
    }
    out
}
