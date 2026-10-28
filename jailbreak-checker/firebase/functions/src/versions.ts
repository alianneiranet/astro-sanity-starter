// Same comparison rules as the Android app (Versions.kt):
//  - two-part "X.YY" versions compare the minor as a decimal fraction (9.5 == 9.50 > 9.03)
//  - anything else compares number by number (23.0.1, 10.0.25398.4909)

const parts = (v: string): string[] => v.match(/\d+/g) ?? [];

export function compareVersions(a: string, b: string): number {
  const pa = parts(a);
  const pb = parts(b);
  if (pa.length === 2 && pb.length === 2) {
    const major = Number(pa[0]) - Number(pb[0]);
    if (major !== 0) return Math.sign(major);
    return Math.sign(Number(`0.${pa[1]}`) - Number(`0.${pb[1]}`));
  }
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = BigInt(pa[i] ?? "0");
    const y = BigInt(pb[i] ?? "0");
    if (x !== y) return x < y ? -1 : 1;
  }
  return 0;
}
