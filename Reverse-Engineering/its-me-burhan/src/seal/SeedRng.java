package seal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SeedRng {

    private static String dec(int... x) {
        char[] c = new char[x.length];
        for (int i = 0; i < x.length; i++) {
            c[i] = (char) (x[i] ^ 0x5A);
        }
        return new String(c);
    }

    private final byte[] seed;

    public SeedRng(String seed) {
        this.seed = (seed == null ? "" : seed).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance(dec(9,18,27,119,104,111,108)).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                dec(9,18,27,119,104,111,108,122,47,52,59,44,59,51,54,59,56,54,63), e);
        }
    }

    private byte[] digest(String label) {
        byte[] suffix = (":" + label).getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[seed.length + suffix.length];
        System.arraycopy(seed, 0, combined, 0, seed.length);
        System.arraycopy(suffix, 0, combined, seed.length, suffix.length);
        return sha256(combined);
    }

    public long digestLong(String label) {
        return beLong(digest(label));
    }

    public int bounded(String label, int lo, int hi) {
        long span = (long) hi - (long) lo + 1L;
        return lo + (int) (digestLong(label) % span);
    }

    public String hexToken(String label, int nBytes) {
        byte[] h = digest(label);
        StringBuilder sb = new StringBuilder(nBytes * 2);
        for (int i = 0; i < nBytes; i++) {
            sb.append(String.format("%02x", h[i] & 0xff));
        }
        return sb.toString();
    }

    public static long beLong(byte[] digest) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (digest[i] & 0xffL);
        }
        return v & Long.MAX_VALUE;
    }

    public static byte[] be4(int v) {
        return new byte[] { (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v };
    }

    public static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static byte[] chain(byte[] h, byte[] next) {
        return sha256(concat(h, next));
    }

    // ---- Transform table (7 invertible bijections), byte domain -------------
    // Used only by InstanceConfig.encryptedFlag(). Parallel in meaning to
    // applyTransform32 so a player who reverses one understands the other.
    //   0 identity | 1 reverse | 2 complement | 3 rol1(8) | 4 +index
    //   5 *3 mod256 | 6 prefix-sum mod256
    public static byte[] applyTransform(int which, byte[] b) {
        int n = b.length;
        byte[] o = new byte[n];
        switch (which) {
            case 0:
                System.arraycopy(b, 0, o, 0, n);
                return o;
            case 1:
                for (int i = 0; i < n; i++) {
                    o[i] = b[n - 1 - i];
                }
                return o;
            case 2:
                for (int i = 0; i < n; i++) {
                    o[i] = (byte) (~b[i]);
                }
                return o;
            case 3:
                for (int i = 0; i < n; i++) {
                    int x = b[i] & 0xFF;
                    o[i] = (byte) (((x << 1) | (x >> 7)) & 0xFF);
                }
                return o;
            case 4:
                for (int i = 0; i < n; i++) {
                    o[i] = (byte) (b[i] + i);
                }
                return o;
            case 5:
                for (int i = 0; i < n; i++) {
                    o[i] = (byte) ((b[i] & 0xFF) * 3);
                }
                return o;
            case 6: {
                int a = 0;
                for (int i = 0; i < n; i++) {
                    a = (a + (b[i] & 0xFF)) & 0xFF;
                    o[i] = (byte) a;
                }
                return o;
            }
            default:
                throw new IllegalArgumentException();
        }
    }

    // ---- Transform table (7 invertible bijections), 5-bit domain (0..31) ----
    // This is the chain a solver must invert to recover the admin password.
    //   0 identity | 1 reverse | 2 xor31 | 3 rol1(5) | 4 +index
    //   5 *3 mod32 | 6 prefix-sum mod32
    public static int[] applyTransform32(int which, int[] v) {
        int n = v.length;
        int[] o = new int[n];
        switch (which) {
            case 0:
                System.arraycopy(v, 0, o, 0, n);
                return o;
            case 1:
                for (int i = 0; i < n; i++) {
                    o[i] = v[n - 1 - i];
                }
                return o;
            case 2:
                for (int i = 0; i < n; i++) {
                    o[i] = v[i] ^ 31;
                }
                return o;
            case 3:
                for (int i = 0; i < n; i++) {
                    o[i] = ((v[i] << 1) | (v[i] >> 4)) & 31;
                }
                return o;
            case 4:
                for (int i = 0; i < n; i++) {
                    o[i] = Math.floorMod(v[i] + i, 32);
                }
                return o;
            case 5:
                for (int i = 0; i < n; i++) {
                    o[i] = Math.floorMod(v[i] * 3, 32);
                }
                return o;
            case 6: {
                int a = 0;
                for (int i = 0; i < n; i++) {
                    a = Math.floorMod(a + v[i], 32);
                    o[i] = a;
                }
                return o;
            }
            default:
                throw new IllegalArgumentException();
        }
    }

    public static int[] fiveBitGroups(byte[] digest, int n) {
        int[] out = new int[n];
        int p = 0;
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : digest) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5 && p < n) {
                out[p++] = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
            }
        }
        return out;
    }

    public static String alphabetFor(int rot) {
        return alphabet(rot);
    }

    public static int[] kthPermutation(long n, int k, int r) {
        int[] elems = new int[k];
        for (int i = 0; i < k; i++) {
            elems[i] = i;
        }
        int len = k;
        int[] out = new int[r];
        for (int i = 0; i < r; i++) {
            long f = 1;
            for (int j = 0; j < r - i - 1; j++) {
                f *= (k - i - 1 - j);
            }
            int idx = (int) (n / f);
            n %= f;
            out[i] = elems[idx];
            for (int j = idx; j < len - 1; j++) {
                elems[j] = elems[j + 1];
            }
            len--;
        }
        return out;
    }

    private static String alphabet(int rot) {
        StringBuilder sb = new StringBuilder(32);
        for (char c = 'A'; c <= 'Z'; c++) {
            sb.append(c);
        }
        for (char c = '2'; c <= '7'; c++) {
            sb.append(c);
        }
        String base = sb.toString();
        int r = Math.floorMod(rot, 32);
        return base.substring(r) + base.substring(0, r);
    }
}