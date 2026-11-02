/**
 * Padrão ilustrativo de QR Code (25 x 25, com os três quadrados de canto): Pix
 * na confirmação do pedido (item 7) e carteirinha digital (item 8). Só imagem:
 * o código de verdade viria do banco ou do sistema de acesso ao estádio.
 */
export function qrPattern(seed: string): boolean[][] {
  const modulus = 2147483647;
  let h = 1;
  for (const ch of seed) {
    h = (h * 31 + ch.charCodeAt(0)) % modulus;
  }
  h = h || 1;
  const size = 25;
  const finder = (r: number, c: number) => {
    const inBox = (r0: number, c0: number) =>
      r >= r0 && r < r0 + 7 && c >= c0 && c < c0 + 7;
    if (!(inBox(0, 0) || inBox(0, size - 7) || inBox(size - 7, 0))) {
      return undefined;
    }
    const rr = r < 7 ? r : r - (size - 7);
    const cc = c < 7 ? c : c - (size - 7);
    return (
      rr === 0 ||
      rr === 6 ||
      cc === 0 ||
      cc === 6 ||
      (rr >= 2 && rr <= 4 && cc >= 2 && cc <= 4)
    );
  };
  return Array.from({ length: size }, (_, r) =>
    Array.from({ length: size }, (_, c) => {
      const f = finder(r, c);
      if (f !== undefined) {
        return f;
      }
      h = (h * 48271) % modulus;
      return Math.floor(h / 65536) % 2 === 0;
    })
  );
}
