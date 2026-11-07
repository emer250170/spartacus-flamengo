/**
 * Telefone do torcedor (07/11, obrigatório no cadastro para o ClearSale): DDD + número. Celular com 11
 * números e o 9 depois do DDD; fixo com 10, começando por 2 a 5; DDD sem zero. As mesmas regras do
 * servidor (br.com.flamengo.matchday.customer.Telefone).
 */
export function telefoneDigits(value: string): string {
  let d = String(value ?? '').replace(/\D/g, '');
  if ((d.length === 12 || d.length === 13) && d.startsWith('55')) {
    d = d.slice(2);
  }
  return d;
}

export function isValidTelefone(value: string): boolean {
  const d = telefoneDigits(value);
  if (d.length !== 10 && d.length !== 11) {
    return false;
  }
  if (d[0] === '0' || d[1] === '0') {
    return false;
  }
  return d.length === 11 ? d[2] === '9' : d[2] >= '2' && d[2] <= '5';
}

/** (21) 98765-4321 ou (21) 3333-4444 enquanto digita */
export function maskTelefone(value: string): string {
  const d = String(value ?? '')
    .replace(/\D/g, '')
    .slice(0, 11);
  if (d.length <= 2) {
    return d ? '(' + d : '';
  }
  const n = d.slice(2);
  const cut = d.length === 11 ? 5 : 4;
  return n.length <= cut
    ? `(${d.slice(0, 2)}) ${n}`
    : `(${d.slice(0, 2)}) ${n.slice(0, cut)}-${n.slice(cut)}`;
}
