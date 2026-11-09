/**
 * QR code do ingresso (Meus ingressos, 09/11): gerador pequeno, sem dependência nova, para o código do
 * ingresso em modo byte, correção M, versões 1 a 6 (até 106 bytes). Segue a norma ISO/IEC 18004
 * (mesma montagem do gerador de referência de Nayuki): padrões fixos, dados com Reed-Solomon
 * intercalados, máscara de menor penalidade e informação de formato. É ilustrativo na demo: nenhuma
 * catraca lê o código, mas ele é um QR válido (abre o texto do ingresso no leitor do celular).
 */

/** [total de codewords, codewords de correção por bloco, blocos] para a correção M, versões 1..6 */
const VERSIONS_M: [number, number, number][] = [
  [26, 10, 1],
  [44, 16, 1],
  [70, 26, 1],
  [100, 18, 2],
  [134, 24, 2],
  [172, 16, 4],
];

/** Centro dos padrões de alinhamento, versões 1..6 */
const ALIGN: number[][] = [[], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34]];

/** Matriz do QR: true = módulo escuro. Undefined quando o texto não cabe na versão 6. */
export function qrMatrix(text: string): boolean[][] | undefined {
  const bytes = Array.from(new TextEncoder().encode(text));
  let version = 0;
  for (let v = 1; v <= VERSIONS_M.length; v++) {
    const [total, ec, blocks] = VERSIONS_M[v - 1];
    if (4 + 8 + bytes.length * 8 <= (total - ec * blocks) * 8) {
      version = v;
      break;
    }
  }
  if (!version) {
    return undefined;
  }
  const [total, ecLen, blockCount] = VERSIONS_M[version - 1];
  const dataLen = total - ecLen * blockCount;

  // dados: modo byte (0100), tamanho em 8 bits, bytes, terminador e preenchimento
  const bits: number[] = [];
  const put = (value: number, n: number) => {
    for (let i = n - 1; i >= 0; i--) {
      bits.push((value >>> i) & 1);
    }
  };
  put(4, 4);
  put(bytes.length, 8);
  bytes.forEach((b) => put(b, 8));
  put(0, Math.min(4, dataLen * 8 - bits.length));
  put(0, (8 - (bits.length % 8)) % 8);
  const data: number[] = [];
  for (let i = 0; i < bits.length; i += 8) {
    data.push(bits.slice(i, i + 8).reduce((a, b) => (a << 1) | b, 0));
  }
  for (let pad = 0xec; data.length < dataLen; pad ^= 0xec ^ 0x11) {
    data.push(pad);
  }

  // blocos com Reed-Solomon, intercalados (nestas versões todos os blocos têm o mesmo tamanho)
  const per = dataLen / blockCount;
  const divisor = rsDivisor(ecLen);
  const blocks: number[][] = [];
  const ecs: number[][] = [];
  for (let b = 0; b < blockCount; b++) {
    const block = data.slice(b * per, (b + 1) * per);
    blocks.push(block);
    ecs.push(rsRemainder(block, divisor));
  }
  const codewords: number[] = [];
  for (let i = 0; i < per; i++) {
    blocks.forEach((bl) => codewords.push(bl[i]));
  }
  for (let i = 0; i < ecLen; i++) {
    ecs.forEach((e) => codewords.push(e[i]));
  }

  // padrões fixos
  const size = version * 4 + 17;
  const modules: boolean[][] = grid(size, false);
  const fixed: boolean[][] = grid(size, false);
  const set = (x: number, y: number, dark: boolean) => {
    modules[y][x] = dark;
    fixed[y][x] = true;
  };
  for (let i = 0; i < size; i++) {
    set(6, i, i % 2 === 0);
    set(i, 6, i % 2 === 0);
  }
  const finder = (cx: number, cy: number) => {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dx = -4; dx <= 4; dx++) {
        const x = cx + dx;
        const y = cy + dy;
        if (x >= 0 && x < size && y >= 0 && y < size) {
          const d = Math.max(Math.abs(dx), Math.abs(dy));
          set(x, y, d !== 2 && d !== 4);
        }
      }
    }
  };
  finder(3, 3);
  finder(size - 4, 3);
  finder(3, size - 4);
  const align = ALIGN[version - 1];
  align.forEach((ay, i) =>
    align.forEach((ax, j) => {
      const corner =
        (i === 0 && j === 0) ||
        (i === 0 && j === align.length - 1) ||
        (i === align.length - 1 && j === 0);
      if (!corner) {
        for (let dy = -2; dy <= 2; dy++) {
          for (let dx = -2; dx <= 2; dx++) {
            set(ax + dx, ay + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
          }
        }
      }
    })
  );
  drawFormat(set, size, 0); // reserva a área do formato

  // dados em zigue-zague, de baixo para cima, a partir da direita
  let k = 0;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) {
      right = 5;
    }
    for (let vert = 0; vert < size; vert++) {
      for (let j = 0; j < 2; j++) {
        const x = right - j;
        const upward = ((right + 1) & 2) === 0;
        const y = upward ? size - 1 - vert : vert;
        if (!fixed[y][x] && k < codewords.length * 8) {
          modules[y][x] = ((codewords[k >>> 3] >>> (7 - (k & 7))) & 1) === 1;
          k++;
        }
      }
    }
  }

  // máscara de menor penalidade
  let best: boolean[][] = modules;
  let bestScore = Infinity;
  for (let mask = 0; mask < 8; mask++) {
    const m = modules.map((row) => row.slice());
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        if (!fixed[y][x] && maskBit(mask, x, y)) {
          m[y][x] = !m[y][x];
        }
      }
    }
    drawFormat((x, y, d) => (m[y][x] = d), size, mask);
    const score = penalty(m);
    if (score < bestScore) {
      bestScore = score;
      best = m;
    }
  }
  return best;
}

/** Caminho SVG (um quadrado por módulo escuro), com margem de 4 módulos; viewBox 0 0 n n */
export function qrSvgPath(matrix: boolean[][]): { d: string; size: number } {
  const quiet = 4;
  let d = '';
  matrix.forEach((row, y) =>
    row.forEach((dark, x) => {
      if (dark) {
        d += `M${x + quiet} ${y + quiet}h1v1h-1z`;
      }
    })
  );
  return { d, size: matrix.length + quiet * 2 };
}

function grid(size: number, value: boolean): boolean[][] {
  return Array.from({ length: size }, () =>
    new Array<boolean>(size).fill(value)
  );
}

/** Informação de formato (correção M = 00 + máscara), BCH 0x537 e XOR 0x5412, nas duas cópias */
function drawFormat(
  set: (x: number, y: number, dark: boolean) => void,
  size: number,
  mask: number
): void {
  const data = (0 << 3) | mask;
  let rem = data;
  for (let i = 0; i < 10; i++) {
    rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  }
  const bits = ((data << 10) | rem) ^ 0x5412;
  const bit = (i: number) => ((bits >>> i) & 1) === 1;
  for (let i = 0; i <= 5; i++) {
    set(8, i, bit(i));
  }
  set(8, 7, bit(6));
  set(8, 8, bit(7));
  set(7, 8, bit(8));
  for (let i = 9; i < 15; i++) {
    set(14 - i, 8, bit(i));
  }
  for (let i = 0; i < 8; i++) {
    set(size - 1 - i, 8, bit(i));
  }
  for (let i = 8; i < 15; i++) {
    set(8, size - 15 + i, bit(i));
  }
  set(8, size - 8, true); // módulo escuro fixo
}

function maskBit(mask: number, x: number, y: number): boolean {
  switch (mask) {
    case 0:
      return (x + y) % 2 === 0;
    case 1:
      return y % 2 === 0;
    case 2:
      return x % 3 === 0;
    case 3:
      return (x + y) % 3 === 0;
    case 4:
      return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0;
    case 5:
      return ((x * y) % 2) + ((x * y) % 3) === 0;
    case 6:
      return (((x * y) % 2) + ((x * y) % 3)) % 2 === 0;
    default:
      return (((x + y) % 2) + ((x * y) % 3)) % 2 === 0;
  }
}

/** Penalidade da norma: sequências, blocos 2x2, padrões parecidos com o localizador e equilíbrio */
function penalty(m: boolean[][]): number {
  const size = m.length;
  let score = 0;
  const line = (get: (i: number) => boolean) => {
    let run = 1;
    for (let i = 1; i <= size; i++) {
      if (i < size && get(i) === get(i - 1)) {
        run++;
      } else {
        if (run >= 5) {
          score += run - 2;
        }
        run = 1;
      }
    }
    for (let i = 0; i + 10 < size; i++) {
      const p = [true, false, true, true, true, false, true];
      const seven = p.every((v, j) => get(i + j) === v);
      const seven2 = p.every((v, j) => get(i + 4 + j) === v);
      if (
        (seven && [7, 8, 9, 10].every((j) => !get(i + j))) ||
        (seven2 && [0, 1, 2, 3].every((j) => !get(i + j)))
      ) {
        score += 40;
      }
    }
  };
  for (let y = 0; y < size; y++) {
    line((x) => m[y][x]);
  }
  for (let x = 0; x < size; x++) {
    line((y) => m[y][x]);
  }
  let dark = 0;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (m[y][x]) {
        dark++;
      }
      if (
        x < size - 1 &&
        y < size - 1 &&
        m[y][x] === m[y][x + 1] &&
        m[y][x] === m[y + 1][x] &&
        m[y][x] === m[y + 1][x + 1]
      ) {
        score += 3;
      }
    }
  }
  const total = size * size;
  score += Math.floor(Math.abs(dark * 20 - total * 10) / total) * 10;
  return score;
}

// ---------- Reed-Solomon em GF(256), polinômio 0x11d ----------

function gfMul(x: number, y: number): number {
  let z = 0;
  for (let i = 7; i >= 0; i--) {
    z = (z << 1) ^ ((z >>> 7) * 0x11d);
    z ^= ((y >>> i) & 1) * x;
  }
  return z & 0xff;
}

function rsDivisor(degree: number): number[] {
  const result = new Array<number>(degree).fill(0);
  result[degree - 1] = 1;
  let root = 1;
  for (let i = 0; i < degree; i++) {
    for (let j = 0; j < result.length; j++) {
      result[j] = gfMul(result[j], root);
      if (j + 1 < result.length) {
        result[j] ^= result[j + 1];
      }
    }
    root = gfMul(root, 0x02);
  }
  return result;
}

function rsRemainder(data: number[], divisor: number[]): number[] {
  const result = new Array<number>(divisor.length).fill(0);
  for (const b of data) {
    const factor = b ^ (result.shift() as number);
    result.push(0);
    divisor.forEach((coef, i) => (result[i] ^= gfMul(coef, factor)));
  }
  return result;
}
