/*
 * Loja Flamengo (SAP Commerce Cloud) como ferramentas para um agente de IA (MCP).
 *
 * Núcleo sem dependências: recebe um `http` (adaptador de rede) e devolve as
 * ferramentas da persona. Quatro personas, cada uma com o próprio usuário do
 * Commerce e só as ferramentas do seu papel:
 *   - torcedor: API da loja (OCC) com o login do torcedor
 *   - socio:    API de integração (OData) com o gestor.socio  (planos, chamados, cupons)
 *   - jogos:    API de integração (OData) com o gestor.jogos  (jogos e venda de ingressos)
 *   - loja:     API de integração (OData) com o gestor.loja   (produtos, preço, estoque, vendas,
 *               pedidos, promoções, cupons e experiências)
 * Quem decide o que cada usuário pode fazer é o próprio Commerce (permissões por
 * tipo); o MCP só expõe a lista fechada de ações. Ações que alteram dados exigem
 * confirmar=true (depois de o usuário concordar).
 */

const TZ = 'America/Sao_Paulo';
const SOCIO_CATEGORY = 'socio_torcedor';
const MATCHES_CATEGORY = 'flamengo_jogos_casa';
const SERVICES_CATEGORY = 'flamengo_matchday';
const PLAN_MARKER = /<!--\s*socio-plan\s*([\s\S]*?)-->/i;
const CANCEL_SUBJECT = 'Cancelamento do plano';
/** Grupo de quem está com os benefícios suspensos (mensalidade em atraso) */
const SUSPENDED_GROUP = 'socio-suspenso';
const CANCEL_REASONS = {
  price: 'O valor ficou alto',
  games: 'Não estou indo aos jogos',
  moving: 'Vou me mudar',
  service: 'Insatisfeito com o atendimento',
  other: 'Outro motivo',
};
const REASON_KEYS = {
  'o valor ficou alto': 'price', 'it got too expensive': 'price',
  'não estou indo aos jogos': 'games', 'i am not going to the games': 'games',
  'vou me mudar': 'moving', 'i am moving': 'moving',
  'insatisfeito com o atendimento': 'service', 'unhappy with the service': 'service',
  'outro motivo': 'other', 'other reason': 'other',
};
const SECTORS = { NORTE: 'priceNorte', SUL: 'priceSul', LESTE: 'priceLeste', OESTE: 'priceOeste' };

// ---------------------------------------------------------------- utilidades

class UserError extends Error {}

function brl(v) {
  const n = Number(v);
  if (!isFinite(n)) return null;
  return 'R$ ' + n.toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function toDate(v) {
  if (v == null || v === '') return null;
  if (v instanceof Date) return v;
  const m = /\/Date\((-?\d+)/.exec(String(v));
  if (m) return new Date(Number(m[1]));
  const d = new Date(v);
  return isNaN(d.getTime()) ? null : d;
}

function fmtDate(v, withTime = true) {
  const d = toDate(v);
  if (!d) return null;
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('pt-BR', {
      timeZone: TZ, day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).formatToParts(d).map((x) => [x.type, x.value])
  );
  const date = `${p.day}/${p.month}/${p.year}`;
  return withTime ? `${date} ${p.hour}:${p.minute}` : date;
}

/** "2026-11-20T21:30" (horário de Brasília se não tiver fuso) → Date */
function parseLocal(s) {
  if (!s) return null;
  const str = String(s).trim();
  const br = /^(\d{2})\/(\d{2})\/(\d{4})(?:[ T](\d{2}):(\d{2}))?$/.exec(str);
  let iso = br ? `${br[3]}-${br[2]}-${br[1]}T${br[4] ?? '00'}:${br[5] ?? '00'}` : str;
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) iso += 'T00:00';
  if (!/([zZ]|[+-]\d{2}:?\d{2})$/.test(iso)) iso += '-03:00';
  const d = new Date(iso);
  if (isNaN(d.getTime())) throw new UserError(`Data inválida: "${s}". Use AAAA-MM-DD HH:MM (horário de Brasília).`);
  return d;
}

const odataDate = (d) => `/Date(${d.getTime()})/`;
const clean = (s) => String(s ?? '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
/** Texto (HTML ou com quebras) → uma linha por item */
const lines = (html) => String(html ?? '').replace(/<\/?(li|p|br|ul|ol|div)[^>]*>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ')
  .split(/\n/).map((x) => x.replace(/\s+/g, ' ').trim()).filter(Boolean);
const num = (v) => (v == null || v === '' ? null : Number(v));
const enc = encodeURIComponent;
const lastDigits = (s) => String(s ?? '').replace(/\D/g, '').slice(-4);

function needConfirm(args, summary) {
  if (args?.confirmar === true) return null;
  return {
    executado: false,
    aviso: 'Ação NÃO executada. Mostre o resumo ao usuário e, se ele concordar, chame de novo com confirmar=true.',
    resumo: summary,
  };
}

function parseCancelMessage(text) {
  const lines = String(text ?? '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  let reason = null;
  let first = 0;
  if (lines[0] && /^(motivo|reason)\s*:/i.test(lines[0])) {
    reason = lines[0].replace(/^[^:]*:\s*/, '');
    first = 1;
  }
  let last = lines.length;
  if (last > first && lines[last - 1].includes('·')) last--;
  const key = reason ? REASON_KEYS[reason.toLowerCase()] ?? null : null;
  return {
    motivo: key ? CANCEL_REASONS[key] : reason,
    motivoChave: key ?? (reason ? 'outro' : null),
    comentario: last > first ? lines.slice(first, last).join(' ') : null,
  };
}

// ---------------------------------------------------------------- ferramentas

export function buildTools(ctx) {
  const { persona, cfg, http } = ctx;
  if (persona === 'torcedor') return torcedorTools(cfg, http);
  if (persona === 'socio') return socioTools(cfg, http);
  if (persona === 'jogos') return jogosTools(cfg, http);
  if (persona === 'loja') return lojaTools(cfg, http);
  throw new Error(`Persona desconhecida: ${persona} (use torcedor, socio, jogos ou loja)`);
}

const S = {
  str: (description) => ({ type: 'string', description }),
  int: (description) => ({ type: 'integer', description }),
  num: (description) => ({ type: 'number', description }),
  bool: (description) => ({ type: 'boolean', description }),
  confirm: { type: 'boolean', description: 'true só depois de o usuário concordar com o resumo da ação' },
};
const obj = (properties = {}, required = []) => ({ type: 'object', properties, required, additionalProperties: false });

// ================================================================ TORCEDOR (OCC)

function torcedorTools(cfg, http) {
  let session = { email: cfg.torcedorEmail, password: cfg.torcedorSenha, token: null, uid: null };
  let plansCache = null;

  async function login() {
    if (!session.email || !session.password) {
      throw new UserError('Nenhum torcedor logado. Use a ferramenta entrar_como (e-mail e senha da loja).');
    }
    const r = await http({
      base: 'oauth', method: 'POST',
      form: { grant_type: 'password', client_id: cfg.clientId, client_secret: cfg.clientSecret, username: session.email, password: session.password },
    });
    if (r.status !== 200 || !r.json?.access_token) {
      session.token = null;
      throw new UserError('Login recusado pela loja para ' + session.email + '. Confira e-mail e senha.');
    }
    session.token = r.json.access_token;
    session.uid = session.email.toLowerCase();
  }

  async function occ(method, path, { query, body, auth = true } = {}) {
    if (auth && !session.token) await login();
    const call = () => http({
      base: 'occ', method, path, body,
      query: { lang: cfg.lang ?? 'pt', curr: cfg.curr ?? 'BRL', ...(query ?? {}) },
      headers: auth ? { Authorization: 'Bearer ' + session.token } : {},
    });
    let r = await call();
    if (auth && r.status === 401) {
      await login();
      r = await call();
    }
    if (r.status >= 400) {
      const msg = r.json?.errors?.map((e) => e.message).filter(Boolean).join('; ') || r.text?.slice(0, 300);
      throw new UserError(`A loja recusou (${r.status}): ${msg}`);
    }
    return r.json ?? {};
  }

  async function plans() {
    if (plansCache) return plansCache;
    const res = await occ('GET', '/products/search', {
      auth: false,
      query: { query: `:relevance:allCategories:${SOCIO_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevel,stockLevelStatus))' },
    });
    const products = res.products ?? [];
    const out = [];
    for (const p of products) {
      const det = await occ('GET', `/products/${enc(p.code)}`, { auth: false, query: { fields: 'description' } });
      const desc = det.description ?? '';
      const m = PLAN_MARKER.exec(desc);
      if (!m) continue;
      let r;
      try { r = JSON.parse(m[1]); } catch { continue; }
      if (!r.group) continue;
      const guest = r.guest ? products.find((g) => g.code === r.guest) : null;
      out.push({
        code: p.code,
        nome: clean(p.name),
        resumo: clean(p.summary),
        programa: r.family || null,
        mensalidade: p.price?.value ?? null,
        mensalidadeTexto: brl(p.price?.value),
        onda: r.wave > 0 ? r.wave : null,
        prioridade: r.wave > 0 ? `Onda ${r.wave}` : 'Venda geral (sem prioridade)',
        descontoIngresso: Number(r.discount ?? 0),
        descontoLoja: Number(r.storeDiscount ?? 0),
        maxConvidados: Number(r.maxGuests ?? 0),
        convidado: guest ? { code: guest.code, mensalidade: brl(guest.price?.value) } : null,
        esgotado: p.stock?.stockLevelStatus === 'outOfStock' || p.stock?.stockLevel === 0,
        beneficios: lines(desc.replace(PLAN_MARKER, '')),
        _group: r.group,
        _pending: r.pending || null,
        _order: Number(r.order ?? 9999),
      });
    }
    out.sort((a, b) => a._order - b._order);
    plansCache = out;
    return out;
  }

  async function membership() {
    if (!session.token) await login();
    const groups = (await occ('GET', `/users/${enc(session.uid)}/customergroups`, { query: { fields: 'userGroups(uid)', pageSize: 100 } }))
      .userGroups?.map((g) => g.uid) ?? [];
    const all = await plans();
    const member = all.find((p) => groups.includes(p._group) && !(p._pending && groups.includes(p._pending))) ?? null;
    const inCart = all.find((p) => p._pending && groups.includes(p._pending)) ?? null;
    return { member, inCart, suspended: !!member && groups.includes(SUSPENDED_GROUP) };
  }

  const publicPlan = (p) => p && Object.fromEntries(Object.entries(p).filter(([k]) => !k.startsWith('_')));

  async function cartId(create = true) {
    const res = await occ('GET', '/users/current/carts', { query: { fields: 'carts(code,saveTime,totalItems)', savedCartsOnly: false, pageSize: 20 } });
    const open = (res.carts ?? []).filter((c) => !c.saveTime);
    if (open.length) return open[0].code;
    if (!create) return null;
    const c = await occ('POST', '/users/current/carts', { query: { fields: 'code' } });
    return c.code;
  }

  async function cartView(id) {
    const c = await occ('GET', `/users/current/carts/${enc(id)}`, { query: { fields: 'FULL' } });
    return {
      carrinho: c.code,
      itens: (c.entries ?? []).map((e) => ({
        entrada: e.entryNumber,
        produto: clean(e.product?.name),
        codigo: e.product?.code,
        quantidade: e.quantity,
        precoUnitario: e.basePrice?.formattedValue ?? brl(e.basePrice?.value),
        total: e.totalPrice?.formattedValue ?? brl(e.totalPrice?.value),
      })),
      cupons: (c.appliedVouchers ?? []).map((v) => v.voucherCode || v.code),
      promocoes: [...(c.appliedOrderPromotions ?? []), ...(c.appliedProductPromotions ?? [])].map((p) => clean(p.description)).filter(Boolean),
      subtotal: c.subTotal?.formattedValue,
      descontos: c.totalDiscounts?.formattedValue,
      total: c.totalPriceWithTax?.formattedValue ?? c.totalPrice?.formattedValue,
      linkCarrinho: `${cfg.storeUrl}/cart`,
    };
  }

  async function addEntry(id, code, quantity) {
    const r = await occ('POST', `/users/current/carts/${enc(id)}/entries`, { body: { product: { code }, quantity }, query: { fields: 'statusCode,statusMessage,quantityAdded' } });
    return r;
  }

  async function matchesList() {
    const res = await occ('GET', '/products/search', {
      auth: false,
      query: { query: `:relevance:allCategories:${MATCHES_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevelStatus))' },
    });
    return (res.products ?? []).map((p) => {
      const [title = '', dia = '', hora = ''] = clean(p.name).split(' · ');
      return { code: p.code, jogo: title, data: [dia, hora].filter(Boolean).join(' '), detalhes: clean(p.summary), aPartirDe: brl(p.price?.value), esgotado: p.stock?.stockLevelStatus === 'outOfStock' };
    }).sort((a, b) => a.code.localeCompare(b.code));
  }

  async function matchDetail(code) {
    const p = await occ('GET', `/products/${enc(code)}`, { auth: false, query: { fields: 'FULL' } });
    const sectors = (p.variantMatrix ?? []).filter((el) => el.variantOption?.code).map((el) => {
      const seats = (el.elements ?? []).filter((c) => c.variantOption?.code).map((c) => ({
        code: c.variantOption.code,
        poltrona: c.variantValueCategory?.name,
        disponivel: c.variantOption?.stock?.stockLevelStatus !== 'outOfStock' && (c.variantOption?.stock?.stockLevel ?? 1) > 0,
        preco: c.variantOption?.priceData?.value,
      }));
      const key = (seats[0]?.code ?? el.variantOption.code).split('-').slice(-2, -1)[0] ?? '';
      return { setor: el.variantValueCategory?.name ?? key, chave: key, preco: seats[0]?.preco ?? p.price?.value, poltronas: seats };
    });
    const [title = '', dia = '', hora = ''] = clean(p.name).split(' · ');
    return { code: p.code, jogo: title, data: [dia, hora].filter(Boolean).join(' '), detalhes: clean(p.summary), setores: sectors };
  }

  return [
    {
      name: 'entrar_como',
      description: 'Entra na loja como outro torcedor (e-mail e senha da conta da loja). A sessão anterior é descartada.',
      inputSchema: obj({ email: S.str('E-mail da conta na loja'), senha: S.str('Senha da conta na loja') }, ['email', 'senha']),
      handler: async ({ email, senha }) => {
        session = { email, password: senha, token: null, uid: null };
        await login();
        const me = await occ('GET', '/users/current', { query: { fields: 'name,uid' } });
        return { ok: true, torcedor: me.name, email: me.uid };
      },
    },
    {
      name: 'minha_conta',
      description: 'Dados do torcedor logado: nome, e-mail e situação no sócio-torcedor (plano, onda de compra, descontos, benefícios suspensos por mensalidade em atraso).',
      inputSchema: obj(),
      handler: async () => {
        const me = await occ('GET', '/users/current', { query: { fields: 'name,uid' } });
        const m = await membership();
        const tickets = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id),createdAt)', pageSize: 50 } }).catch(() => ({}));
        const cancel = (tickets.tickets ?? []).filter((t) => (t.subject ?? '').startsWith(CANCEL_SUBJECT)).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))[0];
        const situacao = m.member
          ? (cancel && cancel.status?.id !== 'CLOSED' ? 'Ativo · cancelamento solicitado'
            : m.suspended ? 'Benefícios suspensos (mensalidade em atraso): compra na venda geral, sem desconto de sócio' : 'Ativo')
          : cancel?.status?.id === 'CLOSED' ? 'Inativo (plano cancelado)' : m.inCart ? 'Plano no carrinho (ainda não é sócio)' : 'Não é sócio';
        return {
          torcedor: me.name, email: me.uid,
          socioTorcedor: {
            situacao, plano: publicPlan(m.member), planoNoCarrinho: m.inCart?.nome ?? null,
            chamadoCancelamento: cancel ? { id: cancel.id, situacao: cancel.status?.id, aberto: fmtDate(cancel.createdAt) } : null,
            ...(m.suspended ? { comoRegularizar: 'Em Minha conta › Meu sócio-torcedor, botão "Regularizar agora": cadastrar um cartão novo cobra a mensalidade em atraso e devolve os benefícios na hora.' } : {}),
          },
          areaDoSocio: `${cfg.storeUrl}/my-account/socio-torcedor`,
        };
      },
    },
    {
      name: 'planos_socio',
      description: 'Planos de sócio-torcedor à venda: mensalidade, onda de prioridade, % de desconto no ingresso e na loja, convidados e benefícios.',
      inputSchema: obj(),
      handler: async () => ({ planos: (await plans()).map(publicPlan) }),
    },
    {
      name: 'assinar_plano',
      description: 'Coloca um plano de sócio (e convidados) no carrinho, trocando qualquer outro plano que esteja lá. Depois use finalizar_compra.',
      inputSchema: obj({ plano: S.str('Código do plano (de planos_socio)'), convidados: S.int('Quantos convidados (0 se nenhum)'), confirmar: S.confirm }, ['plano']),
      handler: async (a) => {
        const plan = (await plans()).find((p) => p.code === a.plano);
        if (!plan) throw new UserError('Plano não encontrado. Veja os códigos em planos_socio.');
        const guests = Math.max(0, Math.min(Number(a.convidados ?? 0), plan.maxConvidados));
        const c = needConfirm(a, `Colocar no carrinho o plano ${plan.nome} (${plan.mensalidadeTexto}/mês)` + (guests ? ` com ${guests} convidado(s) (${plan.convidado?.mensalidade}/mês cada)` : ''));
        if (c) return c;
        const id = await cartId();
        const cart = await occ('GET', `/users/current/carts/${enc(id)}`, { query: { fields: 'entries(entryNumber,product(code))' } });
        const socioCodes = new Set((await plans()).flatMap((p) => [p.code, p.convidado?.code].filter(Boolean)));
        for (const e of [...(cart.entries ?? [])].sort((x, y) => y.entryNumber - x.entryNumber)) {
          if (socioCodes.has(e.product?.code)) await occ('DELETE', `/users/current/carts/${enc(id)}/entries/${e.entryNumber}`);
        }
        await addEntry(id, plan.code, 1);
        if (guests && plan.convidado) await addEntry(id, plan.convidado.code, guests);
        plansCache = null;
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'pedir_cancelamento_plano',
      description: 'Pede o cancelamento do plano de sócio: abre um chamado para o atendimento, que confirma o cancelamento. Motivos: price (valor alto), games (não vai aos jogos), moving (mudança), service (atendimento), other.',
      inputSchema: obj({ motivo: { type: 'string', enum: Object.keys(CANCEL_REASONS) }, comentario: S.str('Comentário opcional do torcedor'), confirmar: S.confirm }, ['motivo']),
      handler: async (a) => {
        const m = await membership();
        if (!m.member) throw new UserError('O torcedor não é sócio ativo, não há plano para cancelar.');
        const tickets = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id))', pageSize: 50 } });
        const open = (tickets.tickets ?? []).find((t) => (t.subject ?? '').startsWith(CANCEL_SUBJECT) && t.status?.id !== 'CLOSED');
        if (open) throw new UserError(`Já existe um pedido de cancelamento em aberto (chamado ${open.id}).`);
        const c = needConfirm(a, `Pedir o cancelamento do plano ${m.member.nome}. Motivo: ${CANCEL_REASONS[a.motivo]}` + (a.comentario ? `. Comentário: ${a.comentario}` : ''));
        if (c) return c;
        const orders = await occ('GET', '/users/current/orders', { query: { fields: 'orders(code,placed)', pageSize: 20 } });
        let since = null;
        for (const o of orders.orders ?? []) {
          const d = await occ('GET', `/users/current/orders/${enc(o.code)}`, { query: { fields: 'created,entries(product(code))' } });
          if ((d.entries ?? []).some((e) => e.product?.code === m.member.code)) since = d.created;
        }
        const message = [`Motivo: ${CANCEL_REASONS[a.motivo]}`, a.comentario || null, `${m.member.nome} (${m.member.code}) · Sócio desde ${fmtDate(since, false) ?? '—'}`].filter(Boolean).join('\n');
        const t = await occ('POST', '/users/current/tickets', { body: { subject: `${CANCEL_SUBJECT} ${m.member.nome}`, message, ticketCategory: { id: 'ENQUIRY' } } });
        return { executado: true, chamado: t.id, situacao: 'Cancelamento solicitado: o atendimento vai confirmar. Até lá o torcedor continua sócio.', acompanhar: `${cfg.storeUrl}/my-account/support-tickets` };
      },
    },
    {
      name: 'proximos_jogos',
      description: 'Próximos jogos do Flamengo em casa com ingressos à venda (data, competição, preço a partir de).',
      inputSchema: obj(),
      handler: async () => ({ jogos: await matchesList() }),
    },
    {
      name: 'detalhes_jogo',
      description: 'Setores de um jogo com preço da inteira, preço com o desconto de sócio do torcedor logado e poltronas livres.',
      inputSchema: obj({ jogo: S.str('Código do jogo (de proximos_jogos)') }, ['jogo']),
      handler: async ({ jogo }) => {
        const d = await matchDetail(jogo);
        const m = await membership().catch(() => ({}));
        const disc = m.member?.descontoIngresso ?? 0;
        return {
          ...d,
          setores: d.setores.map((s) => {
            const livres = s.poltronas.filter((x) => x.disponivel);
            return { setor: s.setor, chave: s.chave, inteira: brl(s.preco), comDescontoDeSocio: disc ? `${brl(s.preco * (1 - disc / 100))} (${disc}% ${m.member.nome})` : null, poltronasLivres: livres.length, exemplos: livres.slice(0, 6).map((x) => x.poltrona) };
          }),
          observacao: disc ? 'O desconto de sócio é aplicado no carrinho.' : 'O torcedor não tem desconto de sócio.',
          comprarNaLoja: `${cfg.storeUrl}/matchday`,
        };
      },
    },
    {
      name: 'comprar_ingressos',
      description: 'Coloca ingressos de um jogo no carrinho: escolhe poltronas livres do setor (ou as poltronas informadas). Depois use finalizar_compra.',
      inputSchema: obj({ jogo: S.str('Código do jogo'), setor: S.str('Setor: NORTE, SUL, LESTE ou OESTE'), quantidade: S.int('Quantos ingressos (1 a 6)'), poltronas: { type: 'array', items: { type: 'string' }, description: 'Opcional: poltronas desejadas, ex. ["D04","D05"]' }, confirmar: S.confirm }, ['jogo', 'setor', 'quantidade']),
      handler: async (a) => {
        const d = await matchDetail(a.jogo);
        const sector = d.setores.find((s) => s.chave.toUpperCase() === String(a.setor).toUpperCase() || clean(s.setor).toUpperCase() === String(a.setor).toUpperCase());
        if (!sector) throw new UserError(`Setor não encontrado. Setores: ${d.setores.map((s) => s.chave).join(', ')}`);
        const qty = Math.max(1, Math.min(6, Number(a.quantidade)));
        const free = sector.poltronas.filter((x) => x.disponivel);
        let chosen = a.poltronas?.length ? free.filter((x) => a.poltronas.map((p) => p.toUpperCase()).includes(String(x.poltrona).toUpperCase())) : free.slice(0, qty);
        if (chosen.length < qty) throw new UserError(`Só há ${chosen.length} poltrona(s) disponível(is) com esse critério no setor ${sector.chave}.`);
        chosen = chosen.slice(0, qty);
        const c = needConfirm(a, `${qty} ingresso(s) para ${d.jogo} (${d.data}), setor ${sector.setor}, poltronas ${chosen.map((x) => x.poltrona).join(', ')} — ${brl(sector.preco)} cada na inteira (desconto de sócio aplicado no carrinho).`);
        if (c) return c;
        const id = await cartId();
        const results = [];
        for (const seat of chosen) {
          const r = await addEntry(id, seat.code, 1);
          results.push({ poltrona: seat.poltrona, status: r.statusCode });
        }
        return { executado: true, adicionados: results, ...(await cartView(id)) };
      },
    },
    {
      name: 'servicos_matchday',
      description: 'Serviços do dia de jogo à venda (estacionamento, combos de comida e bebida, experiências).',
      inputSchema: obj(),
      handler: async () => {
        const res = await occ('GET', '/products/search', { auth: false, query: { query: `:relevance:allCategories:${SERVICES_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevelStatus))' } });
        return { servicos: (res.products ?? []).map((p) => ({ code: p.code, nome: clean(p.name), descricao: clean(p.summary), preco: brl(p.price?.value), esgotado: p.stock?.stockLevelStatus === 'outOfStock' })) };
      },
    },
    {
      name: 'buscar_produtos',
      description: 'Busca produtos na loja oficial (mantos, roupas, acessórios...) com preço.',
      inputSchema: obj({ texto: S.str('O que procurar, ex. "manto jogo 1"'), quantidade: S.int('Máximo de resultados (padrão 10)') }, ['texto']),
      handler: async ({ texto, quantidade }) => {
        const res = await occ('GET', '/products/search', { auth: false, query: { query: texto, pageSize: Math.min(20, quantidade ?? 10), fields: 'products(code,name,price(value),stock(stockLevelStatus)),pagination(totalResults)' } });
        return { total: res.pagination?.totalResults, produtos: (res.products ?? []).map((p) => ({ code: p.code, nome: clean(p.name), preco: brl(p.price?.value), disponivel: p.stock?.stockLevelStatus !== 'outOfStock', link: `${cfg.storeUrl}/p/${p.code}` })) };
      },
    },
    {
      name: 'detalhes_produto',
      description: 'Detalhes de um produto: descrição, preço, estoque e opções (tamanhos) com o código de cada uma para colocar no carrinho.',
      inputSchema: obj({ codigo: S.str('Código do produto') }, ['codigo']),
      handler: async ({ codigo }) => {
        const p = await occ('GET', `/products/${enc(codigo)}`, { auth: false, query: { fields: 'code,name,summary,description,price(value),stock(stockLevel,stockLevelStatus),baseOptions(options(code,stock(stockLevelStatus),priceData(value),variantOptionQualifiers(name,value))),variantOptions(code,stock(stockLevelStatus),priceData(value),variantOptionQualifiers(name,value)),purchasable' } });
        const opts = [...(p.variantOptions ?? []), ...(p.baseOptions ?? []).flatMap((b) => b.options ?? [])];
        return {
          code: p.code, nome: clean(p.name), resumo: clean(p.summary), descricao: clean(p.description).slice(0, 600),
          preco: brl(p.price?.value), compravel: p.purchasable !== false, estoque: p.stock?.stockLevelStatus,
          opcoes: opts.map((o) => ({ code: o.code, opcao: (o.variantOptionQualifiers ?? []).map((q) => `${q.name}: ${q.value}`).join(', '), preco: brl(o.priceData?.value), disponivel: o.stock?.stockLevelStatus !== 'outOfStock' })),
          link: `${cfg.storeUrl}/p/${p.code}`,
        };
      },
    },
    {
      name: 'ver_carrinho',
      description: 'Carrinho atual do torcedor: itens, cupons, promoções (descontos de sócio) e total.',
      inputSchema: obj(),
      handler: async () => {
        const id = await cartId(false);
        return id ? cartView(id) : { carrinho: null, itens: [], aviso: 'Carrinho vazio.' };
      },
    },
    {
      name: 'adicionar_ao_carrinho',
      description: 'Coloca um produto da loja ou serviço do matchday no carrinho (para ingresso use comprar_ingressos).',
      inputSchema: obj({ codigo: S.str('Código do produto (ou da opção/tamanho)'), quantidade: S.int('Quantidade'), confirmar: S.confirm }, ['codigo', 'quantidade']),
      handler: async (a) => {
        const c = needConfirm(a, `Colocar ${a.quantidade}x ${a.codigo} no carrinho.`);
        if (c) return c;
        const id = await cartId();
        const r = await addEntry(id, a.codigo, Math.max(1, Number(a.quantidade)));
        return { executado: true, status: r.statusCode, mensagem: r.statusMessage ?? null, ...(await cartView(id)) };
      },
    },
    {
      name: 'alterar_quantidade',
      description: 'Muda a quantidade de um item do carrinho (0 remove o item).',
      inputSchema: obj({ entrada: S.int('Número da entrada (de ver_carrinho)'), quantidade: S.int('Nova quantidade; 0 remove'), confirmar: S.confirm }, ['entrada', 'quantidade']),
      handler: async (a) => {
        const c = needConfirm(a, a.quantidade > 0 ? `Mudar a entrada ${a.entrada} para ${a.quantidade} unidade(s).` : `Remover a entrada ${a.entrada} do carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        if (a.quantidade > 0) await occ('PATCH', `/users/current/carts/${enc(id)}/entries/${a.entrada}`, { body: { quantity: a.quantidade } });
        else await occ('DELETE', `/users/current/carts/${enc(id)}/entries/${a.entrada}`);
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'aplicar_cupom',
      description: 'Aplica um cupom de desconto no carrinho (ex.: mengao).',
      inputSchema: obj({ cupom: S.str('Código do cupom'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const c = needConfirm(a, `Aplicar o cupom ${a.cupom} no carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        await occ('POST', `/users/current/carts/${enc(id)}/vouchers`, { query: { voucherId: a.cupom } });
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'remover_cupom',
      description: 'Tira um cupom do carrinho.',
      inputSchema: obj({ cupom: S.str('Código do cupom'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const c = needConfirm(a, `Remover o cupom ${a.cupom} do carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        await occ('DELETE', `/users/current/carts/${enc(id)}/vouchers/${enc(a.cupom)}`);
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'finalizar_compra',
      description: 'Finaliza a compra do carrinho com o endereço e o cartão padrão do torcedor (sem pedir dados de cartão). Mostra o resumo antes; só compra com confirmar=true.',
      inputSchema: obj({ confirmar: S.confirm }),
      handler: async (a) => {
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        const view = await cartView(id);
        if (!view.itens.length) throw new UserError('Carrinho vazio.');
        const addresses = (await occ('GET', '/users/current/addresses', { query: { fields: 'addresses(id,defaultAddress,town,line1)' } })).addresses ?? [];
        const address = addresses.find((x) => x.defaultAddress) ?? addresses[0];
        const cards = (await occ('GET', '/users/current/paymentdetails', { query: { saved: true, fields: 'payments(id,defaultPayment,cardNumber,cardType(name))' } })).payments ?? [];
        const card = cards.find((x) => x.defaultPayment) ?? cards[0];
        if (!address) throw new UserError('O torcedor não tem endereço cadastrado.');
        if (!card) throw new UserError('O torcedor não tem cartão salvo.');
        // plano no carrinho: a adesão exige o aceite do Termo do Nação (versão atual)
        const planCodes = new Set((await plans()).map((p) => p.code));
        const raw = await occ('GET', `/users/current/carts/${enc(id)}`, { query: { fields: 'entries(product(code))' } });
        const hasPlan = (raw.entries ?? []).some((e) => planCodes.has(e.product?.code));
        let term = null;
        if (hasPlan) {
          try {
            const t = await occ('GET', '/users/current/socioterm');
            if (t?.enabled && t.version) term = t;
          } catch { /* servidor sem o Termo: segue como antes */ }
        }
        const c = needConfirm(a, {
          itens: view.itens.map((i) => `${i.quantidade}x ${i.produto} (${i.total})`), total: view.total, entrega: `${address.line1 ?? ''}, ${address.town ?? ''}`, cartao: `${card.cardType?.name ?? 'Cartão'} final ${lastDigits(card.cardNumber)}`,
          ...(term ? { termo: `Ao confirmar, o torcedor aceita o ${term.title ?? 'Termo do Nação'} ${term.version}. Texto completo em ${cfg.storeUrl}/socio-torcedor/termo` } : {}),
        });
        if (c) return c;
        if (term) await occ('POST', '/users/current/socioterm/acceptance', { body: { cartId: id } });
        await occ('PUT', `/users/current/carts/${enc(id)}/addresses/delivery`, { query: { addressId: address.id } });
        const modes = (await occ('GET', `/users/current/carts/${enc(id)}/deliverymodes`, { query: { fields: 'deliveryModes(code,deliveryCost(value))' } })).deliveryModes ?? [];
        if (modes.length) {
          const mode = [...modes].sort((x, y) => (x.deliveryCost?.value ?? 0) - (y.deliveryCost?.value ?? 0))[0];
          await occ('PUT', `/users/current/carts/${enc(id)}/deliverymode`, { query: { deliveryModeId: mode.code } });
        }
        await occ('PUT', `/users/current/carts/${enc(id)}/paymentdetails`, { query: { paymentDetailsId: card.id } });
        const order = await occ('POST', '/users/current/orders', { query: { cartId: id, termsChecked: true, fields: 'code,totalPriceWithTax(formattedValue),statusDisplay' } });
        plansCache = null;
        return { executado: true, pedido: order.code, total: order.totalPriceWithTax?.formattedValue, situacao: order.statusDisplay, verNaLoja: `${cfg.storeUrl}/my-account/order/${order.code}` };
      },
    },
    {
      name: 'meus_pedidos',
      description: 'Pedidos do torcedor (código, data, situação, total).',
      inputSchema: obj(),
      handler: async () => {
        const r = await occ('GET', '/users/current/orders', { query: { fields: 'orders(code,placed,statusDisplay,total(formattedValue))', pageSize: 20 } });
        return { pedidos: (r.orders ?? []).map((o) => ({ pedido: o.code, data: fmtDate(o.placed), situacao: o.statusDisplay, total: o.total?.formattedValue })) };
      },
    },
    {
      name: 'detalhe_pedido',
      description: 'Itens, descontos, entrega e pagamento de um pedido do torcedor.',
      inputSchema: obj({ pedido: S.str('Código do pedido') }, ['pedido']),
      handler: async ({ pedido }) => {
        const o = await occ('GET', `/users/current/orders/${enc(pedido)}`, { query: { fields: 'FULL' } });
        return {
          pedido: o.code, data: fmtDate(o.created), situacao: o.statusDisplay,
          itens: (o.entries ?? []).map((e) => ({ produto: clean(e.product?.name), codigo: e.product?.code, quantidade: e.quantity, total: e.totalPrice?.formattedValue })),
          descontos: o.totalDiscounts?.formattedValue, total: o.totalPriceWithTax?.formattedValue,
          cupons: (o.appliedVouchers ?? []).map((v) => v.voucherCode || v.code),
          pagamento: o.paymentInfo ? `${o.paymentInfo.cardType?.name ?? 'Cartão'} final ${lastDigits(o.paymentInfo.cardNumber)}` : null,
          verNaLoja: `${cfg.storeUrl}/my-account/order/${o.code}`,
        };
      },
    },
    {
      name: 'meus_chamados',
      description: 'Chamados de atendimento do torcedor e a conversa de cada um.',
      inputSchema: obj(),
      handler: async () => {
        const r = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id,name),createdAt,modifiedAt,ticketCategory(id))', pageSize: 50 } });
        const list = [];
        for (const t of r.tickets ?? []) {
          const d = await occ('GET', `/users/current/tickets/${enc(t.id)}`, { query: { fields: 'ticketEvents(message,createdAt,author)' } }).catch(() => ({}));
          list.push({ chamado: t.id, assunto: t.subject, situacao: t.status?.name ?? t.status?.id, aberto: fmtDate(t.createdAt), mensagens: (d.ticketEvents ?? []).map((e) => ({ data: fmtDate(e.createdAt), autor: e.author, texto: e.message })) });
        }
        return { chamados: list };
      },
    },
    {
      name: 'abrir_chamado',
      description: 'Abre um chamado de atendimento (dúvida, reclamação ou problema). Para cancelar o plano use pedir_cancelamento_plano.',
      inputSchema: obj({ assunto: S.str('Assunto'), mensagem: S.str('Mensagem'), categoria: { type: 'string', enum: ['ENQUIRY', 'COMPLAINT', 'PROBLEM'], description: 'ENQUIRY dúvida, COMPLAINT reclamação, PROBLEM problema' }, confirmar: S.confirm }, ['assunto', 'mensagem']),
      handler: async (a) => {
        const c = needConfirm(a, `Abrir chamado "${a.assunto}": ${a.mensagem}`);
        if (c) return c;
        const t = await occ('POST', '/users/current/tickets', { body: { subject: a.assunto, message: a.mensagem, ticketCategory: { id: a.categoria ?? 'ENQUIRY' } } });
        return { executado: true, chamado: t.id };
      },
    },
    {
      name: 'meus_cartoes_e_enderecos',
      description: 'Cartões salvos (só os 4 últimos dígitos) e endereços do torcedor.',
      inputSchema: obj(),
      handler: async () => {
        const cards = (await occ('GET', '/users/current/paymentdetails', { query: { saved: true, fields: 'payments(defaultPayment,cardNumber,cardType(name),expiryMonth,expiryYear)' } })).payments ?? [];
        const addresses = (await occ('GET', '/users/current/addresses', { query: { fields: 'addresses(defaultAddress,town,region(isocodeShort),postalCode)' } })).addresses ?? [];
        return {
          cartoes: cards.map((c) => ({ cartao: `${c.cardType?.name ?? 'Cartão'} final ${lastDigits(c.cardNumber)}`, validade: `${c.expiryMonth}/${c.expiryYear}`, padrao: !!c.defaultPayment })),
          enderecos: addresses.map((x) => ({ cidade: x.town, uf: x.region?.isocodeShort, cep: x.postalCode, padrao: !!x.defaultAddress })),
        };
      },
    },
    {
      name: 'meus_pontos',
      description: 'Pontos do sócio-torcedor: saldo, como ganha (ingressos e compras na loja desde a adesão), pedidos que deram pontos, resgates com voucher e a vitrine de experiências que pode resgatar.',
      inputSchema: obj(),
      handler: async () => pointsView(await occ('GET', '/users/current/sociopoints')),
    },
    {
      name: 'resgatar_experiencia',
      description: 'Resgata uma experiência da vitrine com os pontos do sócio (só sócio em dia). Gera um voucher. Códigos em meus_pontos.',
      inputSchema: obj({ experiencia: S.str('Código da experiência (de meus_pontos)'), confirmar: S.confirm }, ['experiencia']),
      handler: async (a) => {
        const p = await occ('GET', '/users/current/sociopoints');
        const x = (p.experiences ?? []).find((e) => e.code === a.experiencia);
        if (!x) throw new UserError('Experiência não encontrada. Veja os códigos em meus_pontos.');
        if (!x.redeemable) throw new UserError(`${x.name}: ${x.reason}`);
        const c = needConfirm(a, `Resgatar "${x.name}" por ${pts(x.points)} pontos. Saldo depois: ${pts(p.balance - x.points)} pontos.`);
        if (c) return c;
        const r = await occ('POST', '/users/current/sociopoints/redemptions', { body: { experience: x.code, origin: 'Claude' } });
        return { executado: true, experiencia: x.name, voucher: r.voucher, pontosUsados: r.points, saldo: r.status?.balance, verNaLoja: `${cfg.storeUrl}/my-account/socio-torcedor` };
      },
    },
  ];

  function pts(n) {
    return Number(n ?? 0).toLocaleString('pt-BR');
  }

  function pointsView(p) {
    if (!p.enabled) return { pontos: 'Os pontos do sócio estão desligados na loja.' };
    return {
      saldo: p.balance,
      ganhos: p.earned,
      resgatados: p.redeemed,
      regra: `${pts(p.perTicket)} pontos por ingresso e ${pts(p.perReal)} por real em produtos (plano e convidados não contam), nos pedidos desde ${fmtDate(p.since, false) ?? 'a adesão'}` + (p.bonus ? `, mais ${pts(p.bonus)} de bônus de adesão.` : '.'),
      podeResgatar: p.canRedeem,
      ...(p.reason ? { motivo: p.reason } : {}),
      pedidosQueDeramPontos: (p.earnings ?? []).map((e) => (e.bonus ? { bonusDeAdesao: true, data: fmtDate(e.date, false), pontos: e.points } : { pedido: e.orderCode, data: fmtDate(e.date, false), ingressos: e.tickets, produtos: brl(e.productsAmount), pontos: e.points })),
      resgates: (p.redemptions ?? []).map((r) => ({ experiencia: r.experienceName, pontos: r.points, voucher: r.voucher, data: fmtDate(r.date), origem: r.origin, ...(r.cancelledAt ? { cancelado: fmtDate(r.cancelledAt, false) } : {}) })),
      vitrine: (p.experiences ?? []).map((e) => ({ code: e.code, nome: e.name, descricao: e.description, pontos: e.points, vagas: e.available ?? 'sem limite', planoMinimo: e.minPlanName ?? 'qualquer plano', podeResgatar: e.redeemable, ...(e.reason ? { motivo: e.reason } : {}) })),
      areaDoSocio: `${cfg.storeUrl}/my-account/socio-torcedor`,
    };
  }
}

// ================================================================ GESTORES (OData)

function odataClient(cfg, http) {
  const auth = 'Basic ' + btoa(`${cfg.gestorUsuario}:${cfg.gestorSenha}`);
  return async function odata(method, path, { body, query } = {}) {
    if (!cfg.gestorUsuario || !cfg.gestorSenha) throw new UserError('Usuário e senha do gestor não configurados no MCP.');
    const r = await http({
      base: 'odata', method, path, query, body,
      headers: { Authorization: auth, Accept: 'application/json', 'Accept-Language': 'pt', 'Content-Language': 'pt', ...(body ? { Prefer: 'return=representation' } : {}) },
    });
    if (r.status === 401) throw new UserError('O Commerce recusou o login do gestor (usuário ou senha).');
    if (r.status === 403) throw new UserError('O Commerce negou: este gestor não tem permissão para essa operação.');
    if (r.status >= 400) {
      const m = r.json?.error?.message?.value ?? r.text?.slice(0, 300);
      throw new UserError(`O Commerce recusou (${r.status}): ${m}`);
    }
    const d = r.json?.d;
    return d?.results ?? d ?? r.json ?? {};
  };
}

const results = (x) => (Array.isArray(x) ? x : x?.results ?? []);

function socioTools(cfg, http) {
  const odata = odataClient(cfg, http);

  async function planList() {
    const ps = await odata('GET', '/FlamengoPlano/SocioPlans', { query: { $expand: 'status,customerGroup/members,pendingGroup/members', $top: 100 } });
    return results(ps).map((p) => {
      const members = results(p.customerGroup?.members).map((m) => m.uid);
      const pending = new Set(results(p.pendingGroup?.members).map((m) => m.uid));
      const real = members.filter((u) => !pending.has(u));
      return {
        code: p.code, nome: p.name, programa: p.family, situacao: p.status?.code,
        mensalidade: brl(p.monthlyPrice), descontoIngresso: p.ticketDiscount, descontoLoja: p.storeDiscount,
        onda: p.wave > 0 ? p.wave : 'venda geral', maxConvidados: p.maxGuests, mensalidadeConvidado: brl(p.guestPrice),
        vagas: p.slots ?? 'sem limite', sociosNaLoja: real.length, vagasRestantes: p.slots ? Math.max(0, p.slots - real.length) : null,
        planoNoCarrinho: pending.size, baseSimuladaDemo: p.demoBase, avisoPublicacao: p.statusMessage || null,
        _members: real,
      };
    });
  }

  async function tickets() {
    const ts = await odata('GET', '/FlamengoChamado/CsTickets', { query: { $expand: 'state,customer,category', $top: 200 } });
    return results(ts).map((t) => ({ id: t.ticketID, assunto: t.headline, situacao: t.state?.code, categoria: t.category?.code, torcedor: t.customer?.name, email: t.customer?.uid, aberto: toDate(t.creationtime), atualizado: toDate(t.modifiedtime) }))
      .sort((a, b) => (b.aberto?.getTime() ?? 0) - (a.aberto?.getTime() ?? 0));
  }

  async function message(id) {
    const ev = await odata('GET', '/FlamengoMensagem/CsTicketEvents', { query: { $filter: `code eq '${id}'` } });
    return results(ev)[0]?.text ?? null;
  }

  async function cancellations() {
    const list = (await tickets()).filter((t) => (t.assunto ?? '').startsWith(CANCEL_SUBJECT));
    for (const t of list) Object.assign(t, parseCancelMessage(await message(t.id)), { plano: t.assunto.slice(CANCEL_SUBJECT.length).trim() });
    return list;
  }

  const typeName = { ADESAO: 'Adesão', SUSPENSAO: 'Suspensão', REGULARIZACAO: 'Regularização', CANCELAMENTO: 'Cancelamento', OBSERVACAO: 'Observação', ACEITE_TERMO: 'Aceite do Termo', RENOVACAO: 'Renovação', FIM_VIGENCIA: 'Fim de vigência', AGUARDANDO_PAGAMENTO: 'Aguardando pagamento', PAGAMENTO_CONFIRMADO: 'Pagamento confirmado' };
  // Pix ou boleto do plano ainda não pago (item 7): o último "Aguardando pagamento" sem confirmação depois dele
  function pendingPayment(evs) {
    for (let i = evs.length - 1; i >= 0; i--) {
      if (evs[i].tipo === 'Pagamento confirmado') return null;
      if (evs[i].tipo === 'Aguardando pagamento') return { desde: evs[i].data, plano: evs[i].plano, pedido: evs[i].motivo };
    }
    return null;
  }

  /** Contrato gravado (FlamengoContrato): vigência, renovação automática e mudança agendada; null sem contrato */
  async function contract(uid) {
    const cs = results(await odata('GET', '/FlamengoContrato/SocioContracts', { query: { $filter: `customer/uid eq '${uid.replace(/'/g, "''")}'`, $expand: 'plan,nextPlan' } }));
    return cs[0] ?? null;
  }

  /** Vigência para mostrar: do contrato gravado ou, sem ele, do pedido mais novo do plano (+12 meses) */
  async function term(c, plan) {
    const k = await contract(c.uid).catch(() => null);
    if (k && !k.endedAt && k.plan?.code === plan?.code) {
      return {
        inicio: fmtDate(toDate(k.start), false), fim: fmtDate(toDate(k.end), false),
        renovacaoAutomatica: k.autoRenew !== false, mudancaAgendada: k.nextPlan?.name ?? k.nextPlan?.code ?? null,
        origem: k.origin ?? null, renovacoes: k.renewals ?? 0, _fim: toDate(k.end), _auto: k.autoRenew !== false, _proximo: k.nextPlan?.name ?? null,
      };
    }
    const o = (await orders()).filter((x) => x.email === c.uid && x.itens.some((i) => i.codigo === plan?.code)).sort((a, b) => b.data - a.data)[0];
    if (!o) return { observacao: 'Sem contrato gravado e sem pedido do plano: vigência desconhecida.' };
    const fim = new Date(o.data); fim.setMonth(fim.getMonth() + 12);
    return { inicio: fmtDate(o.data, false), fim: fmtDate(fim, false), renovacaoAutomatica: true, mudancaAgendada: null, origem: `Pedido ${o.pedido} (sem contrato gravado)`, renovacoes: 0, _fim: fim, _auto: true, _proximo: null };
  }

  const publicTerm = (t) => Object.fromEntries(Object.entries(t).filter(([k]) => !k.startsWith('_')));

  async function customer(email) {
    const cs = results(await odata('GET', '/FlamengoTorcedor/Customers', { query: { $filter: `uid eq '${String(email).toLowerCase().replace(/'/g, "''")}'`, $expand: 'groups' } }));
    if (!cs.length) throw new UserError('Torcedor não encontrado.');
    return cs[0];
  }

  async function events(uid) {
    const es = await odata('GET', '/FlamengoEventoSocio/SocioEvents', { query: { $filter: `customer/uid eq '${uid.replace(/'/g, "''")}'`, $expand: 'type,plan', $top: 200 } });
    return results(es).map((e) => ({ data: toDate(e.date), tipo: typeName[e.type?.code] ?? e.type?.code, plano: e.plan?.name ?? e.plan?.code ?? null, origem: e.origin, registradoPor: e.author, motivo: e.note, resultado: e.result }))
      .sort((a, b) => (a.data?.getTime() ?? 0) - (b.data?.getTime() ?? 0))
      .map((e) => ({ ...e, data: fmtDate(e.data) }));
  }

  /** Cria Suspensão ou Regularização: o Commerce aplica na hora (SocioEventInterceptor) */
  async function statusEvent(a, type) {
    const c = await customer(a.email);
    const plan = (await planList()).find((p) => p._members.includes(c.uid));
    if (!plan) throw new UserError(`${c.uid} não é sócio.`);
    const suspended = results(c.groups).some((g) => g.uid === SUSPENDED_GROUP);
    if (type === 'SUSPENSAO' && suspended) throw new UserError(`${c.name} já está com os benefícios suspensos.`);
    if (type === 'REGULARIZACAO' && !suspended) throw new UserError(`${c.name} não está suspenso.`);
    const summary = type === 'SUSPENSAO'
      ? `Suspender os benefícios de ${c.name} (${c.uid}), sócio ${plan.nome}: perde a onda de sócio e os descontos na hora (continua sócio e mantém a vaga). Motivo: ${a.motivo ?? 'mensalidade em atraso'}.`
      : `Regularizar ${c.name} (${c.uid}), sócio ${plan.nome}: onda de sócio e descontos voltam na hora. Motivo: ${a.motivo ?? 'pagamento confirmado'}.`;
    const conf = needConfirm(a, summary);
    if (conf) return conf;
    const ev = await odata('POST', '/FlamengoEventoSocio/SocioEvents', {
      body: { customer: { uid: c.uid }, date: `/Date(${Date.now()})/`, type: { code: type }, origin: 'Claude', note: a.motivo ?? (type === 'SUSPENSAO' ? 'Mensalidade em atraso' : 'Pagamento confirmado') },
    });
    return { executado: true, torcedor: c.name, email: c.uid, plano: plan.nome, resultado: ev?.result ?? null, linhaDoTempo: await events(c.uid) };
  }

  /** Tentativas de pôr ingresso no carrinho (SocioEligibilityDecision), mais nova primeiro */
  async function decisions(uid) {
    const query = { $expand: 'customer,product,plan', $top: 200 };
    if (uid) query.$filter = `customer/uid eq '${uid.replace(/'/g, "''")}'`;
    const ds = await odata('GET', '/FlamengoElegibilidade/SocioEligibilityDecisions', { query });
    const lines = (t) => String(t ?? '').split('\n').map((x) => x.trim()).filter(Boolean);
    return results(ds)
      .map((d) => ({
        data: toDate(d.date), torcedor: d.customer?.name, email: d.customer?.uid, jogo: d.gameCode,
        poltrona: d.product?.name ?? d.product?.code ?? null,
        decisao: d.eligible ? 'aprovada' : d.blocked ? 'negada e recusada no carrinho' : 'negada (só observação: a compra seguiu)',
        motivos: lines(d.reasons), condicoes: lines(d.checks), plano: d.plan?.name ?? null,
        ingressosQueJaTinha: d.tickets, limitePorJogo: d.ticketLimit,
        _negada: !d.eligible,
      }))
      .sort((a, b) => (b.data?.getTime() ?? 0) - (a.data?.getTime() ?? 0))
      .map((d) => ({ ...d, data: fmtDate(d.data) }));
  }

  /** Convidados nominais (FlamengoConvidado) e ingressos marcados para eles (FlamengoPedido, ticketHolder) */
  async function guests(uid) {
    const query = { $expand: 'customer,plan', $top: 500 };
    if (uid) query.$filter = `customer/uid eq '${uid.replace(/'/g, "''")}'`;
    const gs = results(await odata('GET', '/FlamengoConvidado/SocioGuests', { query }));
    const mask = (cpf) => (String(cpf ?? '').length === 11 ? `***.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-**` : '***');
    return gs.map((g) => ({
      convidado: g.name, cpf: mask(g.cpf), titular: g.customer?.name, email: g.customer?.uid,
      situacao: g.active ? 'ativo' : `encerrado em ${fmtDate(toDate(g.until))} (${g.endReason ?? 'sem motivo'})`,
      desde: fmtDate(toDate(g.since)), plano: g.plan?.name ?? null, _ativo: !!g.active,
    }));
  }

  async function guestTickets(uid) {
    const query = { $expand: 'user,status,entries/product', $top: 500 };
    if (uid) query.$filter = `user/uid eq '${uid.replace(/'/g, "''")}'`;
    const os = results(await odata('GET', '/FlamengoPedido/Orders', { query })).filter((o) => o.versionID == null && !/CANCEL/.test(o.status?.code ?? ''));
    const out = [];
    for (const o of os) {
      for (const e of results(o.entries)) {
        const code = e.product?.code ?? '';
        if (!/^JOGO-\d{8}-/.test(code)) continue;
        out.push({
          jogo: code.split('-').slice(0, 3).join('-'), poltrona: e.product?.name ?? code, codigoPoltrona: code,
          quemVai: e.ticketHolder || `${o.user?.name ?? o.user?.uid} (titular)`, convidado: !!e.ticketHolder,
          pedido: o.code, data: fmtDate(toDate(o.date)), titular: o.user?.name, email: o.user?.uid,
        });
      }
    }
    return out;
  }

  /** Versões do Termo do Nação e aceites (FlamengoTermo) */
  async function termAcceptances() {
    const as = results(await odata('GET', '/FlamengoTermo/SocioTermAcceptances', { query: { $expand: 'customer,termVersion,plan', $top: 1000 } }));
    return as.map((x) => ({ uid: x.customer?.uid, nome: x.customer?.name, versao: x.termVersion?.version, data: toDate(x.date), origem: x.origin, pedido: x.orderCode ?? null, plano: x.plan?.name ?? x.plan?.code ?? null }));
  }

  async function termVersions() {
    const vs = results(await odata('GET', '/FlamengoTermo/SocioTermVersions', { query: { $top: 100 } }));
    return vs.map((v) => ({ versao: v.version, publicadoEm: toDate(v.publishedAt), mudou: v.summary ?? null }))
      .sort((x, y) => (y.publicadoEm?.getTime() ?? 0) - (x.publicadoEm?.getTime() ?? 0));
  }

  const showTicket = (t) => ({ ...t, aberto: fmtDate(t.aberto), atualizado: fmtDate(t.atualizado), situacao: t.situacao === 'Closed' ? 'Fechado' : t.situacao === 'Open' ? 'Em atendimento' : 'Novo' });

  async function orders() {
    const os = await odata('GET', '/FlamengoPedido/Orders', { query: { $expand: 'status,user,entries/product', $top: 500 } });
    return results(os).filter((o) => o.versionID == null).map((o) => ({ pedido: o.code, data: toDate(o.date), torcedor: o.user?.name, email: o.user?.uid, total: num(o.totalPrice), descontos: num(o.totalDiscounts), situacao: o.status?.code, itens: results(o.entries).map((e) => ({ codigo: e.product?.code, produto: e.product?.name, quantidade: num(e.quantity), total: num(e.totalPrice) })) }));
  }

  return [
    {
      name: 'planos',
      description: 'Planos de sócio-torcedor: situação (à venda/rascunho/fora da loja), mensalidade, descontos, onda, convidados, vagas e quantos sócios reais cada plano tem.',
      inputSchema: obj(),
      handler: async () => ({ planos: (await planList()).map(({ _members, ...p }) => p) }),
    },
    {
      name: 'socios_do_plano',
      description: 'Lista os sócios (torcedores) de um plano.',
      inputSchema: obj({ plano: S.str('Código do plano') }, ['plano']),
      handler: async ({ plano }) => {
        const p = (await planList()).find((x) => x.code === plano);
        if (!p) throw new UserError('Plano não encontrado.');
        return { plano: p.nome, socios: p._members };
      },
    },
    {
      name: 'atualizar_plano',
      description: 'Altera um plano (mensalidade, descontos, onda, convidados, vagas, situação). Ao salvar, o Commerce atualiza o produto e as promoções da loja sozinho.',
      inputSchema: obj({
        plano: S.str('Código do plano'), mensalidade: S.num('Nova mensalidade em R$'), descontoIngresso: S.int('% de desconto no ingresso (0-100)'),
        descontoLoja: S.int('% de desconto na loja (0-100)'), onda: S.int('Onda de compra (1, 2, 3; 0 = venda geral)'), maxConvidados: S.int('Máximo de convidados'),
        mensalidadeConvidado: S.num('Mensalidade de cada convidado em R$'), vagas: S.int('Vagas do plano (0 = sem limite)'),
        situacao: { type: 'string', enum: ['ON_SALE', 'DRAFT', 'OFF_SALE'], description: 'ON_SALE à venda, DRAFT rascunho, OFF_SALE fora da loja' }, confirmar: S.confirm,
      }, ['plano']),
      handler: async (a) => {
        const p = (await planList()).find((x) => x.code === a.plano);
        if (!p) throw new UserError('Plano não encontrado.');
        const body = { code: a.plano };
        const changes = [];
        const set = (field, value, label, show = value) => { if (value != null) { body[field] = value; changes.push(`${label}: ${show}`); } };
        set('monthlyPrice', a.mensalidade != null ? String(a.mensalidade) : null, 'mensalidade', brl(a.mensalidade));
        set('ticketDiscount', a.descontoIngresso, 'desconto no ingresso', `${a.descontoIngresso}%`);
        set('storeDiscount', a.descontoLoja, 'desconto na loja', `${a.descontoLoja}%`);
        set('wave', a.onda, 'onda');
        set('maxGuests', a.maxConvidados, 'máximo de convidados');
        set('guestPrice', a.mensalidadeConvidado != null ? String(a.mensalidadeConvidado) : null, 'mensalidade do convidado', brl(a.mensalidadeConvidado));
        set('slots', a.vagas, 'vagas');
        if (a.situacao) { body.status = { code: a.situacao }; changes.push(`situação: ${a.situacao}`); }
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Alterar o plano ${p.nome}: ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoPlano/SocioPlans('${enc(a.plano)}')`, { body });
        const after = (await planList()).find((x) => x.code === a.plano);
        return { executado: true, plano: after && (({ _members, ...x }) => x)(after), observacao: 'O Commerce republica produto e promoções em segundo plano; a loja reflete em instantes.' };
      },
    },
    {
      name: 'buscar_torcedor',
      description: 'Ficha de um torcedor: plano de sócio, pedidos e chamados.',
      inputSchema: obj({ email: S.str('E-mail do torcedor') }, ['email']),
      handler: async ({ email }) => {
        const cs = results(await odata('GET', '/FlamengoTorcedor/Customers', { query: { $filter: `uid eq '${String(email).toLowerCase().replace(/'/g, "''")}'`, $expand: 'groups' } }));
        if (!cs.length) throw new UserError('Torcedor não encontrado.');
        const c = cs[0];
        const groups = results(c.groups).map((g) => g.uid);
        const plans = await planList();
        const plan = plans.find((p) => p._members.includes(c.uid));
        const ts = (await tickets()).filter((t) => t.email === c.uid).map(showTicket);
        const os = (await orders()).filter((o) => o.email === c.uid).map((o) => ({ pedido: o.pedido, data: fmtDate(o.data), total: brl(o.total), itens: o.itens.map((i) => `${i.quantidade}x ${i.produto}`) }));
        const suspenso = !!plan && groups.includes(SUSPENDED_GROUP);
        return { torcedor: c.name, email: c.uid, clienteDesde: fmtDate(c.creationtime, false), plano: plan ? plan.nome : 'não é sócio', situacao: plan ? (suspenso ? 'Benefícios suspensos (mensalidade em atraso)' : 'Ativo') : null, grupos: groups, pedidos: os, chamados: ts };
      },
    },
    {
      name: 'situacao_socio',
      description: 'Situação de um sócio (ativo ou com benefícios suspensos por mensalidade em atraso) e a linha do tempo: adesão, suspensões, regularizações, cancelamento, com data, origem, quem registrou e motivo.',
      inputSchema: obj({ email: S.str('E-mail do torcedor') }, ['email']),
      handler: async ({ email }) => {
        const c = await customer(email);
        const plan = (await planList()).find((p) => p._members.includes(c.uid));
        const suspenso = !!plan && results(c.groups).some((g) => g.uid === SUSPENDED_GROUP);
        const evs = await events(c.uid);
        const pendente = pendingPayment(evs);
        return { torcedor: c.name, email: c.uid, plano: plan ? plan.nome : 'não é sócio', situacao: plan ? (suspenso ? 'Benefícios suspensos (mensalidade em atraso): sem onda de sócio e sem descontos; mantém a vaga' : 'Ativo') : pendente ? `Aguardando pagamento do ${pendente.plano}: vira sócio quando o pagamento for confirmado (confirmar_pagamento)` : null, pagamentoPendente: pendente, vigencia: plan ? publicTerm(await term(c, plan)) : null, linhaDoTempo: evs };
      },
    },
    {
      name: 'virar_vigencia',
      description: 'Vira a vigência de um sócio agora (simula o aniversário do contrato de 12 meses): renova por 12 meses a partir de hoje, aplicando a mudança para um plano menor que o sócio agendou; se ele desligou a renovação automática, o plano termina sem multa e os convidados são encerrados. Grava o evento Renovação ou Fim de vigência na linha do tempo.',
      inputSchema: obj({ email: S.str('E-mail do sócio'), motivo: S.str('Observação (opcional)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => {
        const c = await customer(a.email);
        const plan = (await planList()).find((p) => p._members.includes(c.uid));
        if (!plan) throw new UserError(`${c.uid} não é sócio: não há vigência para virar.`);
        const t = await term(c, plan);
        const what = !t._auto ? `a renovação automática está desligada: o ${plan.nome} termina hoje, sem multa, e ${c.name} deixa de ser sócio (convidados encerrados)`
          : t._proximo ? `renova por 12 meses a partir de hoje com a mudança agendada: ${plan.nome} → ${t._proximo}`
            : `renova o ${plan.nome} por 12 meses a partir de hoje`;
        const conf = needConfirm(a, `Virar a vigência de ${c.name} (${c.uid}), vigência atual ${t.inicio ?? '?'} a ${t.fim ?? '?'}: ${what}.`);
        if (conf) return conf;
        const ev = await odata('POST', '/FlamengoEventoSocio/SocioEvents', {
          body: { customer: { uid: c.uid }, date: `/Date(${Date.now()})/`, type: { code: 'RENOVACAO' }, origin: 'Claude', note: a.motivo ?? 'Virada da vigência pelo Claude' },
        });
        const plan2 = (await planList()).find((p) => p._members.includes(c.uid));
        return { executado: true, torcedor: c.name, email: c.uid, resultado: ev?.result ?? null, plano: plan2 ? plan2.nome : 'não é mais sócio', vigencia: plan2 ? publicTerm(await term(c, plan2)) : null, linhaDoTempo: await events(c.uid) };
      },
    },
    {
      name: 'confirmar_pagamento',
      description: 'Confirma o pagamento de um Pix ou boleto (simula a confirmação do banco): o status do pedido muda de "Aguardando pagamento" para "Pagamento confirmado" e o pedido segue sozinho (remessa e envio dos produtos físicos, fechamento do resto). Se o pedido tem plano, o torcedor vira sócio na hora. Vale para qualquer pedido (produto, ingresso ou plano); para pedido sem plano, informe o código do pedido. Sem pedido informado, vale o Pix/boleto mais novo de um plano aguardando. Grava o evento Pagamento confirmado na linha do tempo do torcedor.',
      inputSchema: obj({ email: S.str('E-mail do torcedor'), pedido: S.str('Código do pedido (opcional, 8 dígitos)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => {
        const c = await customer(a.email);
        const pendente = pendingPayment(await events(c.uid));
        if (!pendente && !a.pedido) throw new UserError(`${c.uid} não tem Pix nem boleto aguardando pagamento.`);
        const conf = needConfirm(a, `Confirmar o pagamento de ${c.name} (${c.uid})${a.pedido ? `, pedido ${a.pedido}` : pendente ? `: ${pendente.pedido}` : ''}: o pedido passa para "Pagamento confirmado" e segue${pendente?.plano && !a.pedido ? `; ${c.name} vira sócio do ${pendente.plano}` : ''}.`);
        if (conf) return conf;
        const ev = await odata('POST', '/FlamengoEventoSocio/SocioEvents', {
          body: { customer: { uid: c.uid }, date: `/Date(${Date.now()})/`, type: { code: 'PAGAMENTO_CONFIRMADO' }, origin: 'Claude', note: a.pedido ? `Pagamento do pedido ${a.pedido} confirmado pelo Claude` : 'Pagamento confirmado pelo Claude' },
        });
        const plan = (await planList()).find((p) => p._members.includes(c.uid));
        return { executado: true, torcedor: c.name, email: c.uid, resultado: ev?.result ?? null, plano: plan ? plan.nome : 'não é sócio', linhaDoTempo: await events(c.uid) };
      },
    },
    {
      name: 'suspender_socio',
      description: 'Suspende os benefícios de um sócio por mensalidade em atraso: perde a onda de sócio e os descontos na hora, continua sócio e mantém a vaga. Ingressos de jogos que ainda não abriram para a venda geral saem do carrinho dele.',
      inputSchema: obj({ email: S.str('E-mail do torcedor'), motivo: S.str('Motivo (ex.: mensalidade de outubro em atraso)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => statusEvent(a, 'SUSPENSAO'),
    },
    {
      name: 'regularizar_socio',
      description: 'Regulariza um sócio suspenso (pagamento confirmado): onda de sócio e descontos voltam na hora.',
      inputSchema: obj({ email: S.str('E-mail do torcedor'), motivo: S.str('Observação (ex.: pagamento confirmado pelo financeiro)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => statusEvent(a, 'REGULARIZACAO'),
    },
    {
      name: 'elegibilidade_torcedor',
      description: 'Por que um torcedor conseguiu ou não comprar ingresso: as tentativas de pôr poltrona no carrinho, com a decisão (aprovada ou negada), o motivo e as condições conferidas (plano, situação, janela da onda, biometria e limite por jogo). Sem e-mail, lista as últimas tentativas negadas de todos os torcedores.',
      inputSchema: obj({ email: S.str('E-mail do torcedor (opcional)'), jogo: S.str('Código do jogo, ex.: JOGO-20261115-BAH (opcional)'), soNegadas: S.bool('Só as tentativas negadas') }),
      handler: async ({ email, jogo, soNegadas = false }) => {
        let uid;
        if (email) uid = (await customer(email)).uid;
        let list = await decisions(uid);
        if (jogo) list = list.filter((d) => d.jogo === jogo);
        if (soNegadas || !email) list = list.filter((d) => d._negada);
        const show = list.slice(0, 20).map(({ _negada, ...d }) => d);
        const ultimaNegada = list.find((d) => d._negada);
        return {
          torcedor: email ? uid : 'todos',
          tentativas: show.length,
          resumo: !show.length
            ? 'Nenhuma tentativa registrada com esse filtro.'
            : ultimaNegada
              ? `Última negativa em ${ultimaNegada.data} (${ultimaNegada.jogo}): ${ultimaNegada.motivos.join(' ')}`
              : 'Todas as tentativas foram aprovadas.',
          lista: show,
        };
      },
    },
    {
      name: 'convidados_torcedor',
      description: 'Convidados nominais de um sócio (nome, CPF mascarado, situação e desde quando) e os ingressos comprados para eles, com quem vai em cada poltrona. Com o código do jogo, mostra quem vai no jogo (titular e convidados). Sem e-mail, lista os ingressos de convidados de todos os sócios (filtrável por jogo).',
      inputSchema: obj({ email: S.str('E-mail do sócio (opcional)'), jogo: S.str('Código do jogo, ex.: JOGO-20261115-BAH (opcional)') }),
      handler: async ({ email, jogo }) => {
        let uid;
        if (email) uid = (await customer(email)).uid;
        const lista = uid ? await guests(uid) : [];
        let ingressos = await guestTickets(uid);
        if (jogo) ingressos = ingressos.filter((t) => t.jogo === jogo);
        if (!uid) ingressos = ingressos.filter((t) => t.convidado);
        return {
          torcedor: uid ?? 'todos',
          convidados: lista.map(({ _ativo, ...g }) => g),
          ativos: lista.filter((g) => g._ativo).length,
          ingressos: ingressos.slice(0, 50).map(({ convidado, ...t }) => t),
          resumo: !ingressos.length
            ? 'Nenhum ingresso encontrado com esse filtro.'
            : `${ingressos.length} ingresso(s); para convidados: ${ingressos.filter((t) => t.convidado).map((t) => `${t.quemVai} (${t.poltrona})`).join(', ') || 'nenhum'}.`,
        };
      },
    },
    {
      name: 'termo_aceites',
      description: 'Termo do Nação (termo de adesão versionado): versão atual e rascunhos, quem já aceitou a versão atual (onde e quando) e quais sócios ainda não aceitaram. Com e-mail, mostra o histórico de aceites de um torcedor.',
      inputSchema: obj({ email: S.str('E-mail do torcedor (opcional)'), plano: S.str('Código do plano para filtrar os sócios (opcional)') }),
      handler: async ({ email, plano }) => {
        const versions = await termVersions();
        const now = Date.now();
        const current = versions.find((v) => v.publicadoEm && v.publicadoEm.getTime() <= now);
        const drafts = versions.filter((v) => !v.publicadoEm || v.publicadoEm.getTime() > now).map((v) => v.versao);
        const accepts = await termAcceptances();
        const fmt = (x) => ({ ...x, data: fmtDate(x.data) });
        if (email) {
          const c = await customer(email);
          const mine = accepts.filter((x) => x.uid === c.uid).sort((x, y) => (y.data?.getTime() ?? 0) - (x.data?.getTime() ?? 0));
          return {
            torcedor: c.uid, versaoAtual: current?.versao ?? null,
            aceitouAVersaoAtual: !!current && mine.some((x) => x.versao === current.versao),
            aceites: mine.map(({ uid, nome, ...x }) => fmt(x)),
          };
        }
        if (!current) return { versaoAtual: null, rascunhos: drafts, resumo: 'Nenhuma versão do Termo publicada.' };
        let plansList = await planList();
        if (plano) plansList = plansList.filter((p) => p.code === plano);
        const members = new Map();
        for (const p of plansList) for (const uid of p._members) if (!members.has(uid)) members.set(uid, p.nome);
        const accepted = new Map(accepts.filter((x) => x.versao === current.versao).map((x) => [x.uid, x]));
        const pending = [...members].filter(([uid]) => !accepted.has(uid)).map(([uid, p]) => ({ email: uid, plano: p }));
        const done = [...members].filter(([uid]) => accepted.has(uid)).map(([uid]) => accepted.get(uid))
          .sort((x, y) => (y.data?.getTime() ?? 0) - (x.data?.getTime() ?? 0)).map(fmt);
        return {
          versaoAtual: current.versao, publicadaEm: fmtDate(current.publicadoEm), oQueMudou: current.mudou, rascunhos: drafts,
          socios: members.size, aceitaram: done.length, pendentes: pending.length,
          pendentesLista: pending.slice(0, 100),
          aceitesRecentes: done.slice(0, 30).map(({ uid, ...x }) => ({ email: uid, ...x })),
          resumo: pending.length ? `${pending.length} de ${members.size} sócio(s) ainda não aceitaram o Termo ${current.versao}.` : `Todos os ${members.size} sócio(s) aceitaram o Termo ${current.versao}.`,
        };
      },
    },
    {
      name: 'chamados',
      description: 'Chamados de atendimento (filtro opcional por situação e só cancelamentos), com o motivo dos pedidos de cancelamento.',
      inputSchema: obj({ situacao: { type: 'string', enum: ['abertos', 'fechados', 'todos'], description: 'padrão: abertos' }, soCancelamentos: S.bool('Só pedidos de cancelamento de plano') }),
      handler: async ({ situacao = 'abertos', soCancelamentos = false }) => {
        let list = soCancelamentos ? await cancellations() : await tickets();
        if (situacao === 'abertos') list = list.filter((t) => t.situacao !== 'Closed');
        if (situacao === 'fechados') list = list.filter((t) => t.situacao === 'Closed');
        return { total: list.length, chamados: list.map(showTicket) };
      },
    },
    {
      name: 'detalhe_chamado',
      description: 'Mensagem completa de um chamado.',
      inputSchema: obj({ chamado: S.str('Número do chamado') }, ['chamado']),
      handler: async ({ chamado }) => {
        const t = (await tickets()).find((x) => x.id === chamado);
        if (!t) throw new UserError('Chamado não encontrado.');
        const text = await message(chamado);
        return { ...showTicket(t), mensagem: text, ...((t.assunto ?? '').startsWith(CANCEL_SUBJECT) ? parseCancelMessage(text) : {}) };
      },
    },
    {
      name: 'confirmar_cancelamento',
      description: 'Confirma um pedido de cancelamento de plano: fecha o chamado e o torcedor deixa de ser sócio na hora (perde selo, onda e descontos; a vaga volta ao plano).',
      inputSchema: obj({ chamado: S.str('Número do chamado de cancelamento'), confirmar: S.confirm }, ['chamado']),
      handler: async (a) => {
        const t = (await cancellations()).find((x) => x.id === a.chamado);
        if (!t) throw new UserError('Chamado de cancelamento não encontrado.');
        if (t.situacao === 'Closed') throw new UserError('Esse chamado já está fechado (plano já encerrado).');
        const c = needConfirm(a, `Confirmar o cancelamento de ${t.torcedor} (${t.email}) no plano ${t.plano}. Motivo: ${t.motivo ?? '—'}. O torcedor perde os benefícios na hora.`);
        if (c) return c;
        await odata('PATCH', `/FlamengoChamado/CsTickets('${enc(a.chamado)}')`, { body: { ticketID: a.chamado, state: { code: 'Closed' } } });
        const still = (await planList()).some((p) => p._members.includes(t.email));
        return { executado: true, chamado: a.chamado, situacao: 'Fechado', torcedorAindaSocio: still, resultado: still ? 'Atenção: o torcedor continua no grupo do plano.' : `${t.torcedor} deixou de ser sócio do ${t.plano}.` };
      },
    },
    {
      name: 'relatorio_cancelamentos',
      description: 'Cancelamentos pedidos na loja: aguardando atendimento, no mês, total, por motivo e por plano, com os últimos pedidos (dados reais).',
      inputSchema: obj(),
      handler: async () => {
        const list = await cancellations();
        const now = new Date();
        const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1, 3));
        const count = (key) => list.reduce((acc, t) => ((acc[t[key] ?? '—'] = (acc[t[key] ?? '—'] ?? 0) + 1), acc), {});
        return {
          aguardandoAtendimento: list.filter((t) => t.situacao !== 'Closed').length,
          noMes: list.filter((t) => t.aberto && t.aberto >= monthStart).length,
          total: list.length,
          porMotivo: count('motivo'),
          porPlano: count('plano'),
          ultimos: list.slice(0, 10).map(showTicket),
        };
      },
    },
    {
      name: 'relatorio_adesoes',
      description: 'Adesões a planos de sócio pela loja (pedidos com plano) nos últimos N dias: por plano, receita da 1ª mensalidade e os pedidos.',
      inputSchema: obj({ dias: S.int('Janela em dias (padrão 30)') }),
      handler: async ({ dias = 30 }) => {
        const plans = await planList();
        const codes = new Set(plans.map((p) => p.code));
        const since = new Date(Date.now() - dias * 86400000);
        const os = (await orders()).filter((o) => o.data && o.data >= since && o.itens.some((i) => codes.has(i.codigo)));
        const byPlan = {};
        let revenue = 0;
        for (const o of os) for (const i of o.itens) if (codes.has(i.codigo)) { byPlan[i.produto] = (byPlan[i.produto] ?? 0) + 1; revenue += i.total ?? 0; }
        return { periodo: `últimos ${dias} dias`, adesoes: os.length, porPlano: byPlan, receitaPrimeiraMensalidade: brl(revenue), pedidos: os.map((o) => ({ pedido: o.pedido, data: fmtDate(o.data), torcedor: o.torcedor, plano: o.itens.filter((i) => codes.has(i.codigo)).map((i) => i.produto).join(', '), total: brl(o.total) })) };
      },
    },
    ...couponTools(odata),
  ];
}

/** Cupons (FlamengoCupom, FlamengoCupomUso): gestor do sócio e gestor da loja */
function couponTools(odata) {
  return [
    {
      name: 'cupons',
      description: 'Cupons de desconto da loja: ativo, limites de uso e quantas vezes cada um já foi usado.',
      inputSchema: obj(),
      handler: async () => {
        const cs = results(await odata('GET', '/FlamengoCupom/SingleCodeCoupons', { query: { $top: 200 } }));
        const uses = results(await odata('GET', '/FlamengoCupomUso/CouponRedemptions', { query: { $top: 1000, $expand: 'user,order' } }));
        const byCode = uses.reduce((acc, u) => ((acc[String(u.couponCode).toLowerCase()] = (acc[String(u.couponCode).toLowerCase()] ?? 0) + 1), acc), {});
        return {
          cupons: cs.map((c) => ({ cupom: c.couponId, ativo: c.active, usos: byCode[String(c.couponId).toLowerCase()] ?? 0, limiteTotal: c.maxTotalRedemptions, limitePorCliente: c.maxRedemptionsPerCustomer, inicio: fmtDate(c.startDate, false), fim: fmtDate(c.endDate, false) })),
          ultimosUsos: uses.slice(-10).reverse().map((u) => ({ cupom: u.couponCode, torcedor: u.user?.name, pedido: u.order?.code, data: fmtDate(u.creationtime), totalPedido: brl(u.order?.totalPrice) })),
        };
      },
    },
    {
      name: 'atualizar_cupom',
      description: 'Ativa/desativa um cupom ou muda os limites de uso e a data de fim.',
      inputSchema: obj({ cupom: S.str('Código do cupom'), ativo: S.bool('Ativo?'), limiteTotal: S.int('Máximo de usos no total'), limitePorCliente: S.int('Máximo de usos por cliente'), fim: S.str('Data de fim (AAAA-MM-DD)'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const body = { couponId: a.cupom };
        const changes = [];
        if (a.ativo != null) { body.active = a.ativo; changes.push(a.ativo ? 'ativar' : 'desativar'); }
        if (a.limiteTotal != null) { body.maxTotalRedemptions = a.limiteTotal; changes.push(`limite total ${a.limiteTotal}`); }
        if (a.limitePorCliente != null) { body.maxRedemptionsPerCustomer = a.limitePorCliente; changes.push(`limite por cliente ${a.limitePorCliente}`); }
        if (a.fim) { body.endDate = odataDate(parseLocal(a.fim + (a.fim.length <= 10 ? 'T23:59' : ''))); changes.push(`fim ${a.fim}`); }
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Cupom ${a.cupom}: ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoCupom/SingleCodeCoupons('${enc(a.cupom)}')`, { body });
        return { executado: true, cupom: a.cupom, alteracoes: changes };
      },
    },
  ];
}

function jogosTools(cfg, http) {
  const odata = odataClient(cfg, http);

  const show = (g) => ({
    code: g.code, adversario: g.opponent, jogo: `Flamengo x ${g.opponent}`, data: fmtDate(g.kickoff), competicao: g.competition, estadio: g.venue,
    situacao: g.status?.code, prevendaSocio: g.presale,
    precos: Object.fromEntries(Object.entries(SECTORS).map(([s, f]) => [s, brl(g[f])])),
    poltronas: { total: g.seatsTotal, vendidas: g.seatsSold, livres: g.seatsAvailable },
    ondas: { onda1: fmtDate(g.wave1Start), onda2: fmtDate(g.wave2Start), onda3: fmtDate(g.wave3Start), vendaGeral: fmtDate(g.generalSaleStart) },
    aviso: g.statusMessage || null,
  });

  async function games() {
    const gs = await odata('GET', '/FlamengoJogo/MatchdayGames', { query: { $expand: 'status', $top: 200 } });
    return results(gs).sort((a, b) => (toDate(a.kickoff)?.getTime() ?? 0) - (toDate(b.kickoff)?.getTime() ?? 0));
  }

  async function game(code) {
    const g = (await games()).find((x) => x.code === code);
    if (!g) throw new UserError('Jogo não encontrado. Veja os códigos com a ferramenta jogos.');
    return g;
  }

  function gameBody(a, body, changes) {
    if (a.adversario) { body.opponent = a.adversario; changes.push(`adversário ${a.adversario}`); }
    if (a.dataHora) { body.kickoff = odataDate(parseLocal(a.dataHora)); changes.push(`data ${fmtDate(parseLocal(a.dataHora))}`); }
    if (a.competicao) { body.competition = a.competicao; changes.push(`competição ${a.competicao}`); }
    if (a.estadio) { body.venue = a.estadio; changes.push(`estádio ${a.estadio}`); }
    if (a.prevendaSocio != null) { body.presale = a.prevendaSocio; changes.push(a.prevendaSocio ? 'com pré-venda de sócio' : 'sem pré-venda'); }
    for (const [s, f] of Object.entries(SECTORS)) {
      const v = a[`preco${s[0]}${s.slice(1).toLowerCase()}`];
      if (v != null) { body[f] = String(v); changes.push(`${s} ${brl(v)}`); }
    }
    for (const [k, f, label] of [['onda1', 'wave1Start', 'onda 1'], ['onda2', 'wave2Start', 'onda 2'], ['onda3', 'wave3Start', 'onda 3'], ['vendaGeral', 'generalSaleStart', 'venda geral']]) {
      if (a[k]) { body[f] = odataDate(parseLocal(a[k])); changes.push(`${label} ${fmtDate(parseLocal(a[k]))}`); }
    }
    if (a.situacao) { body.status = { code: a.situacao }; changes.push(`situação ${a.situacao}`); }
  }

  // ---- ingressos vendidos (item 10)
  const TICKET_STATUS = { VALIDO: 'válido', AGUARDANDO_PAGAMENTO: 'aguardando pagamento', CANCELADO: 'cancelado', UTILIZADO: 'utilizado' };
  const EVENT_TYPES = { VENDA: 'venda', TROCA_POLTRONA: 'troca de poltrona', TROCA_TITULAR: 'troca de quem vai usar', CANCELAMENTO: 'cancelamento', UTILIZACAO: 'entrada no estádio', OBSERVACAO: 'observação' };
  /** Regra padrão do servidor (flamengo.matchday.ingressos.reembolso.*): integral até 48 h antes, depois 80 % até o início */
  const REFUND = { hours: 48, percent: 80 };

  const showTicket = (t) => ({
    ingresso: t.code, situacao: TICKET_STATUS[t.status?.code] ?? t.status?.code,
    jogo: t.game ? `Flamengo x ${t.game.opponent}` : null, codigoJogo: t.game?.code, dataJogo: fmtDate(t.game?.kickoff),
    setor: t.sector, poltrona: t.seatLabel, quemVaiUsar: t.holder,
    comprador: t.customer ? { nome: t.customer.name, email: t.customer.uid } : null,
    pedido: t.orderCode, valorPago: brl(num(t.price)), descontoSocio: t.memberDiscount != null ? brl(num(t.memberDiscount)) : null,
    vendidoEm: fmtDate(t.soldAt), canceladoEm: fmtDate(t.cancelledAt), reembolso: t.refundAmount != null ? brl(num(t.refundAmount)) : null,
    utilizadoEm: fmtDate(t.usedAt),
  });

  async function ticketList() {
    const ts = await odata('GET', '/FlamengoIngresso/MatchdayTickets', { query: { $expand: 'status,game,customer', $top: 1000 } });
    return results(ts).sort((a, b) => (toDate(b.soldAt)?.getTime() ?? 0) - (toDate(a.soldAt)?.getTime() ?? 0));
  }

  async function ticket(code) {
    const ts = results(await odata('GET', '/FlamengoIngresso/MatchdayTickets', { query: { $filter: `code eq '${String(code).trim().replace(/'/g, "''")}'`, $expand: 'status,game,customer' } }));
    if (!ts.length) throw new UserError(`Ingresso ${code} não encontrado. Veja os códigos com a ferramenta ingressos (código = pedido-linha).`);
    return ts[0];
  }

  async function ticketHistory(code) {
    const es = results(await odata('GET', '/FlamengoIngressoEvento/MatchdayTicketEvents', { query: { $filter: `ticket/code eq '${String(code).replace(/'/g, "''")}'`, $expand: 'type', $top: 200 } }));
    return es.sort((a, b) => (toDate(b.date)?.getTime() ?? 0) - (toDate(a.date)?.getTime() ?? 0)).map((e) => ({
      data: fmtDate(e.date), evento: EVENT_TYPES[e.type?.code] ?? e.type?.code, de: e.fromValue || null, para: e.toValue || null,
      valor: e.amount != null ? brl(num(e.amount)) : null, observacao: e.note || null, por: e.author || null,
    }));
  }

  /** Poltronas livres do mesmo jogo e setor (catálogo público da loja) */
  async function freeSeats(t) {
    if (!t.game?.code || !t.sector) return [];
    const r = await http({ base: 'occ', method: 'GET', path: `/products/${enc(t.game.code)}`, query: { lang: cfg.lang ?? 'pt', curr: cfg.curr ?? 'BRL', fields: 'FULL' } });
    if (r.status >= 400) return [];
    const out = [];
    for (const el of r.json?.variantMatrix ?? []) for (const c of el.elements ?? []) {
      const code = c.variantOption?.code ?? '';
      if (!code.startsWith(`${t.game.code}-${t.sector}-`)) continue;
      const stock = c.variantOption?.stock;
      if (stock?.stockLevelStatus === 'outOfStock' || (stock?.stockLevel ?? 1) <= 0) continue;
      out.push({ codigo: code, poltrona: c.variantValueCategory?.name ?? code });
    }
    return out;
  }

  function refundRule(t) {
    if (t.status?.code !== 'VALIDO') return { podeCancelar: false, motivo: `O ingresso está ${TICKET_STATUS[t.status?.code] ?? t.status?.code}.` };
    const kickoff = toDate(t.game?.kickoff);
    const paid = num(t.price) ?? 0;
    if (kickoff && Date.now() >= kickoff.getTime()) return { podeCancelar: false, motivo: 'O jogo já começou: não cancela mais.' };
    const full = !kickoff || kickoff.getTime() - Date.now() >= REFUND.hours * 3600e3;
    const value = full ? paid : Math.round(paid * REFUND.percent) / 100;
    return { podeCancelar: true, reembolsoSeCancelarAgora: brl(value), regra: `integral até ${REFUND.hours} h antes do jogo, depois ${REFUND.percent} % até o início (o valor final é calculado pelo Commerce)` };
  }

  const gameFields = {
    adversario: S.str('Adversário'), dataHora: S.str('Data e hora do jogo, ex. 2026-11-29 16:00 (Brasília)'), competicao: S.str('Competição, ex. "Brasileirão · Rodada 36"'),
    estadio: S.str('Estádio (padrão Maracanã)'), prevendaSocio: S.bool('Pré-venda para sócios'),
    precoNorte: S.num('Preço da inteira no setor Norte'), precoSul: S.num('Preço da inteira no setor Sul'), precoLeste: S.num('Preço da inteira no setor Leste'), precoOeste: S.num('Preço da inteira no setor Oeste'),
    onda1: S.str('Início da onda 1 (data e hora)'), onda2: S.str('Início da onda 2'), onda3: S.str('Início da onda 3'), vendaGeral: S.str('Início da venda geral'),
    situacao: { type: 'string', enum: ['ON_SALE', 'DRAFT', 'CLOSED'], description: 'ON_SALE à venda, DRAFT pausado/rascunho, CLOSED encerrado' },
  };

  return [
    {
      name: 'jogos',
      description: 'Jogos cadastrados: data, competição, situação, preços por setor, poltronas vendidas/livres e datas das ondas de venda.',
      inputSchema: obj(),
      handler: async () => ({ jogos: (await games()).map(show) }),
    },
    {
      name: 'detalhe_jogo',
      description: 'Dados completos de um jogo.',
      inputSchema: obj({ jogo: S.str('Código do jogo (JOGO-AAAAMMDD-ADV)') }, ['jogo']),
      handler: async ({ jogo }) => show(await game(jogo)),
    },
    {
      name: 'criar_jogo',
      description: 'Cadastra um jogo novo e abre a venda: o Commerce cria o produto, as poltronas e as ondas (se não informadas: 7, 6, 5 e 4 dias antes, às 10h; preços do modelo se não informados).',
      inputSchema: obj({ ...gameFields, confirmar: S.confirm }, ['adversario', 'dataHora', 'competicao']),
      handler: async (a) => {
        const kickoff = parseLocal(a.dataHora);
        const letters = String(a.adversario).normalize('NFD').replace(/[^A-Za-z]/g, '').toUpperCase();
        const ymd = new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(kickoff).replace(/-/g, '');
        const code = `JOGO-${ymd}-${(letters || 'ADV').slice(0, 3)}`;
        if ((await games()).some((g) => g.code === code)) throw new UserError(`Já existe o jogo ${code}. Mude a data ou o adversário.`);
        const body = { code };
        const changes = [];
        gameBody(a, body, changes);
        if (!body.status) body.status = { code: 'ON_SALE' };
        if (!body.venue) body.venue = 'Maracanã';
        if (body.presale == null) body.presale = false;
        const c = needConfirm(a, `Criar o jogo ${code} (à venda assim que criado, salvo situação informada): ${changes.join('; ')}`);
        if (c) return c;
        await odata('POST', '/FlamengoJogo/MatchdayGames', { body });
        const g = (await games()).find((x) => x.code === code);
        return { executado: true, jogo: g ? show(g) : code, loja: `${cfg.storeUrl}/matchday` };
      },
    },
    {
      name: 'atualizar_jogo',
      description: 'Altera um jogo: preços por setor, data, ondas de venda, pré-venda ou situação (DRAFT pausa a venda, ON_SALE retoma, CLOSED encerra e libera as poltronas não vendidas).',
      inputSchema: obj({ jogo: S.str('Código do jogo'), ...gameFields, confirmar: S.confirm }, ['jogo']),
      handler: async (a) => {
        const g = await game(a.jogo);
        const body = { code: g.code };
        const changes = [];
        gameBody(a, body, changes);
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Alterar ${g.code} (Flamengo x ${g.opponent}): ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoJogo/MatchdayGames('${enc(g.code)}')`, { body });
        return { executado: true, jogo: show(await game(a.jogo)) };
      },
    },
    {
      name: 'vendas_ingressos',
      description: 'Venda de ingressos pela loja: por jogo e por setor (ingressos, receita e pedidos). Sem jogo informado, resume todos.',
      inputSchema: obj({ jogo: S.str('Código do jogo (opcional)') }),
      handler: async ({ jogo }) => {
        const gs = await games();
        const target = jogo ? gs.filter((g) => g.code === jogo) : gs;
        if (jogo && !target.length) throw new UserError('Jogo não encontrado.');
        const os = results(await odata('GET', '/FlamengoPedido/Orders', { query: { $expand: 'entries/product,user', $top: 500 } })).filter((o) => o.versionID == null);
        return {
          jogos: target.map((g) => {
            const bySector = {};
            let tickets = 0;
            let revenue = 0;
            const orders = new Set();
            for (const o of os) for (const e of results(o.entries)) {
              const code = e.product?.code ?? '';
              if (!code.startsWith(g.code + '-')) continue;
              const sector = code.slice(g.code.length + 1).split('-')[0];
              const q = num(e.quantity) ?? 0;
              bySector[sector] = bySector[sector] ?? { ingressos: 0, receita: 0 };
              bySector[sector].ingressos += q;
              bySector[sector].receita += num(e.totalPrice) ?? 0;
              tickets += q;
              revenue += num(e.totalPrice) ?? 0;
              orders.add(o.code);
            }
            return {
              jogo: `Flamengo x ${g.opponent}`, code: g.code, data: fmtDate(g.kickoff),
              vendidosNaLoja: tickets, receita: brl(revenue), pedidos: orders.size,
              porSetor: Object.fromEntries(Object.entries(bySector).map(([s, v]) => [s, { ingressos: v.ingressos, receita: brl(v.receita) }])),
              ocupacao: { total: g.seatsTotal, vendidas: g.seatsSold, livres: g.seatsAvailable },
            };
          }),
          observacao: '"vendidosNaLoja" conta só pedidos reais da loja; "ocupacao" inclui a carga simulada da demo.',
        };
      },
    },
    {
      name: 'ingressos',
      description: 'Ingressos vendidos (uma linha por poltrona), mais novos primeiro: jogo, setor, poltrona, quem vai usar, comprador, valor pago e situação (válido, cancelado, utilizado). Filtros opcionais.',
      inputSchema: obj({
        jogo: S.str('Código do jogo (opcional)'),
        setor: { type: 'string', enum: Object.keys(SECTORS), description: 'Setor (opcional)' },
        situacao: { type: 'string', enum: Object.keys(TICKET_STATUS), description: 'VALIDO, AGUARDANDO_PAGAMENTO, CANCELADO ou UTILIZADO (opcional)' },
        texto: S.str('Parte do código, do pedido, do e-mail ou do nome do comprador ou de quem vai usar (opcional)'),
      }),
      handler: async (a) => {
        const needle = String(a.texto ?? '').trim().toLowerCase();
        const ts = (await ticketList()).filter((t) => (!a.jogo || t.game?.code === a.jogo) && (!a.setor || t.sector === a.setor)
          && (!a.situacao || t.status?.code === a.situacao)
          && (!needle || [t.code, t.orderCode, t.holder, t.seatLabel, t.customer?.uid, t.customer?.name].some((v) => String(v ?? '').toLowerCase().includes(needle))));
        return { total: ts.length, ingressos: ts.slice(0, 100).map(showTicket), observacao: ts.length > 100 ? 'Mostrando os 100 mais novos; use os filtros.' : undefined };
      },
    },
    {
      name: 'detalhe_ingresso',
      description: 'Ficha de um ingresso: dados, histórico (venda, trocas, cancelamento, entrada, observações), poltronas livres no mesmo setor e quanto volta se cancelar agora.',
      inputSchema: obj({ ingresso: S.str('Código do ingresso (pedido-linha, ex. 00021000-0)') }, ['ingresso']),
      handler: async ({ ingresso }) => {
        const t = await ticket(ingresso);
        const seats = t.status?.code === 'VALIDO' ? await freeSeats(t) : [];
        return { ...showTicket(t), cancelamento: refundRule(t), poltronasLivresNoSetor: seats.slice(0, 60), totalPoltronasLivresNoSetor: seats.length, historico: await ticketHistory(t.code) };
      },
    },
    {
      name: 'acao_ingresso',
      description: 'Ação do gestor de jogos num ingresso, com as regras do Commerce: trocar_poltrona (mesmo jogo e setor; a antiga volta para a venda), trocar_quem_vai_usar (nome de um convidado do sócio ou outro nome; vazio = o titular), cancelar (com reembolso: integral até 48 h antes, depois 80 %; não cancela depois do início), marcar_utilizado (entrada no estádio; depois não troca nem cancela) e observacao (anota no histórico). Tudo fica no histórico do ingresso.',
      inputSchema: obj({
        ingresso: S.str('Código do ingresso (pedido-linha)'),
        acao: { type: 'string', enum: ['trocar_poltrona', 'trocar_quem_vai_usar', 'cancelar', 'marcar_utilizado', 'observacao'], description: 'O que fazer' },
        novaPoltrona: S.str('trocar_poltrona: código da poltrona livre (veja detalhe_ingresso)'),
        quemVaiUsar: S.str('trocar_quem_vai_usar: nome do convidado ou de quem vai usar (vazio = o titular)'),
        motivo: S.str('cancelar: motivo (obrigatório)'),
        observacao: S.str('observacao: texto para o histórico'),
        confirmar: S.confirm,
      }, ['ingresso', 'acao']),
      handler: async (a) => {
        const t = await ticket(a.ingresso);
        const label = `${t.code} (Flamengo x ${t.game?.opponent ?? '?'}, ${t.seatLabel ?? t.sector}, ${t.holder ?? '-'})`;
        let type;
        let toValue;
        let note;
        let summary;
        switch (a.acao) {
          case 'trocar_poltrona': {
            if (!a.novaPoltrona) throw new UserError('Informe novaPoltrona (veja as livres com detalhe_ingresso).');
            const seat = (await freeSeats(t)).find((x) => x.codigo === a.novaPoltrona);
            if (!seat) throw new UserError(`A poltrona ${a.novaPoltrona} não está livre no setor ${t.sector} deste jogo.`);
            type = 'TROCA_POLTRONA'; toValue = seat.codigo;
            summary = `Trocar a poltrona do ingresso ${label} para ${seat.poltrona}. A poltrona atual volta para a venda.`;
            break;
          }
          case 'trocar_quem_vai_usar':
            type = 'TROCA_TITULAR'; toValue = a.quemVaiUsar?.trim() || undefined;
            summary = `Trocar quem vai usar o ingresso ${label} para ${toValue ?? 'o titular (' + (t.customer?.name ?? t.customer?.uid) + ')'}.`;
            break;
          case 'cancelar': {
            if (!a.motivo) throw new UserError('Informe o motivo do cancelamento.');
            const rule = refundRule(t);
            if (!rule.podeCancelar) throw new UserError(rule.motivo);
            type = 'CANCELAMENTO'; note = a.motivo;
            summary = `Cancelar o ingresso ${label}. Reembolso estimado ${rule.reembolsoSeCancelarAgora} (pago ${brl(num(t.price))}); a poltrona volta para a venda e o ingresso deixa de dar pontos. Motivo: ${a.motivo}.`;
            break;
          }
          case 'marcar_utilizado':
            type = 'UTILIZACAO';
            summary = `Marcar o ingresso ${label} como utilizado (entrada no estádio). Depois disso ele não troca nem cancela.`;
            break;
          case 'observacao':
            if (!a.observacao) throw new UserError('Informe a observação.');
            type = 'OBSERVACAO'; note = a.observacao;
            summary = `Anotar no histórico do ingresso ${label}: ${a.observacao}`;
            break;
          default:
            throw new UserError('Ação desconhecida.');
        }
        const c = needConfirm(a, summary);
        if (c) return c;
        await odata('POST', '/FlamengoIngressoEvento/MatchdayTicketEvents', {
          body: { ticket: { code: t.code }, date: `/Date(${Date.now()})/`, type: { code: type }, author: `${cfg.gestorUsuario} (Claude)`, ...(toValue ? { toValue } : {}), ...(note ? { note } : {}) },
        });
        const after = await ticket(t.code);
        return { executado: true, ingresso: showTicket(after), historico: (await ticketHistory(t.code)).slice(0, 5) };
      },
    },
  ];
}

// ================================================================ GESTOR DA LOJA (OData)

/** Grupo de promoções do site electronics-spa (as regras dos outros sites do servidor ficam de fora) */
const STORE_PROMO_GROUP = 'electronicsPromoGrp';
const MAIN_WAREHOUSE = 'warehouse_s';
/** Produtos cuidados por outra persona ou tela: preço e estoque não mudam por aqui */
const MANAGED_ELSEWHERE = [
  [/^JOGO-/, 'ingresso de jogo: preço por setor e poltronas são do gestor de jogos (criar_jogo / atualizar_jogo)'],
  [/^NACAO-/, 'plano de sócio: mensalidade e vagas são do gestor do sócio-torcedor (atualizar_plano)'],
  [/^EXP-/, 'experiência do sócio: preço e vagas mudam em atualizar_experiencia (o Commerce republica o produto)'],
];
/** Mesmas regras do painel de vendas do Backoffice (DefaultSummaryReportService) */
const SALE_CATEGORIES = [
  [/^JOGO-/, 'Ingressos'], [/^NACAO-/, 'Sócio-torcedor'], [/^EXP-/, 'Experiências'],
  [/^FLA-EST-/, 'Estacionamento'], [/^FLA-FOOD-/, 'Alimentação'], [/^FLA-PERSONALIZACAO/, 'Personalização'],
];
const saleCategory = (code) => SALE_CATEGORIES.find(([re]) => re.test(code ?? ''))?.[1] ?? 'Loja (produtos)';
const EXCLUDED_STATUS = new Set(['CANCELLED', 'CHECKED_INVALID']);
const ORDER_STATUS = {
  CREATED: 'Criado', PAYMENT_NOT_CAPTURED: 'Aguardando pagamento', PAYMENT_CAPTURED: 'Pagamento confirmado', CHECKED_VALID: 'Verificado',
  CHECKED_INVALID: 'Inválido', READY: 'Pronto para envio', ON_VALIDATION: 'Em validação', SUSPENDED: 'Suspenso', PROCESSING_ERROR: 'Erro no processamento',
  CANCELLING: 'Cancelando', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', WAIT_FRAUD_MANUAL_CHECK: 'Análise de fraude', FRAUD_CHECKED: 'Fraude verificada',
  READY_FOR_PICKUP: 'Pronto para retirada', PICKUP_COMPLETE: 'Retirado',
};
const RULE_STATUS = { PUBLISHED: 'publicada (vale na loja)', UNPUBLISHED: 'não publicada', MODIFIED: 'alterada, falta publicar', INACTIVE: 'inativa', ARCHIVED: 'arquivada' };

/** Códigos de cupom do pedido: coleção de texto na OData ({ results: [{ value }] }), texto solto ou lista */
function couponCodes(v) {
  if (v == null) return [];
  if (typeof v === 'string') return v.split(/[,;\s]+/).filter(Boolean);
  const list = Array.isArray(v) ? v : Array.isArray(v.results) ? v.results : [];
  return list.map((x) => (typeof x === 'string' ? x : x?.value ?? x?.string ?? null)).filter(Boolean);
}

function lojaTools(cfg, http) {
  const odata = odataClient(cfg, http);
  const q = (s) => String(s ?? '').trim().replace(/'/g, "''");

  function managedElsewhere(code) {
    const hit = MANAGED_ELSEWHERE.find(([re]) => re.test(String(code ?? '').toUpperCase()));
    if (hit) throw new UserError(`${code} é ${hit[1]}.`);
  }

  /** Todas as páginas de uma lista OData */
  async function all(path, query = {}, page = 500) {
    const out = [];
    for (let skip = 0; ; skip += page) {
      const r = results(await odata('GET', path, { query: { ...query, $top: page, $skip: skip } }));
      out.push(...r);
      if (r.length < page || out.length >= 10000) return out;
    }
  }

  // ---- produtos, preço e estoque
  async function products(code) {
    return results(await odata('GET', '/FlamengoProduto/Products', { query: { $filter: `code eq '${q(code)}'`, $expand: 'catalogVersion/catalog,approvalStatus,supercategories' } }))
      .filter((p) => p.catalogVersion?.catalog?.id === 'flamengoProductCatalog');
  }
  async function prices(code) {
    return results(await odata('GET', '/FlamengoPreco/PriceRows', { query: { $filter: `productId eq '${q(code)}'`, $expand: 'currency,unit' } }));
  }
  async function stocks(code) {
    return results(await odata('GET', '/FlamengoEstoque/StockLevels', { query: { $filter: `productCode eq '${q(code)}'`, $expand: 'warehouse,inStockStatus' } }));
  }
  const showStock = (s) => ({
    deposito: s.warehouse?.code, nome: s.warehouse?.name ?? (s.warehouse?.code === MAIN_WAREHOUSE ? 'Loja online' : null), disponivel: num(s.available), reservado: num(s.reserved),
    livreParaVenda: (num(s.available) ?? 0) - (num(s.reserved) ?? 0), ...(num(s.overSelling) ? { vendaAlemDoEstoque: num(s.overSelling) } : {}),
    ...(s.inStockStatus?.code && s.inStockStatus.code !== 'notSpecified' ? { regra: s.inStockStatus.code === 'forceInStock' ? 'sempre em estoque' : s.inStockStatus.code === 'forceOutOfStock' ? 'sempre esgotado' : s.inStockStatus.code } : {}),
  });
  const brlPrice = (rows) => rows.find((r) => r.currency?.isocode === 'BRL');

  /** Opções (tamanhos) do produto na loja publicada (catálogo Online, OCC público) */
  async function storeView(code) {
    const r = await http({ base: 'occ', method: 'GET', path: `/products/${enc(code)}`, query: { lang: cfg.lang ?? 'pt', curr: cfg.curr ?? 'BRL', fields: 'code,name,price(value),stock(stockLevel,stockLevelStatus),purchasable,baseProduct,variantOptions(code,stock(stockLevel,stockLevelStatus),priceData(value),variantOptionQualifiers(name,value)),baseOptions(options(code,stock(stockLevel,stockLevelStatus),priceData(value),variantOptionQualifiers(name,value)))' } });
    return r.status < 400 ? r.json : null;
  }

  // ---- pedidos
  async function orders() {
    const os = await all('/FlamengoPedido/Orders', { $expand: 'status,user,entries/product,appliedCouponCodes' });
    return os.filter((o) => o.versionID == null).map((o) => {
      const itens = results(o.entries).sort((a, b) => num(a.entryNumber) - num(b.entryNumber)).map((e) => ({
        codigo: e.product?.code, produto: e.product?.name ?? e.product?.code, quantidade: num(e.quantity) ?? 0,
        unitario: num(e.basePrice), total: num(e.totalPrice) ?? 0, categoria: saleCategory(e.product?.code), ...(e.ticketHolder ? { quemVaiUsar: e.ticketHolder } : {}),
      }));
      const linhas = itens.reduce((s, i) => s + i.total, 0);
      const frete = num(o.deliveryCost) ?? 0;
      const total = num(o.totalPrice) ?? 0;
      return {
        pedido: o.code, data: toDate(o.date), torcedor: o.user?.name, email: o.user?.uid, situacaoCodigo: o.status?.code,
        situacao: ORDER_STATUS[o.status?.code] ?? o.status?.code, total, frete, descontos: Math.max(0, Math.round((linhas + frete - total) * 100) / 100),
        cupons: couponCodes(o.appliedCouponCodes), itens,
      };
    }).sort((a, b) => (b.data?.getTime() ?? 0) - (a.data?.getTime() ?? 0));
  }

  function period(a) {
    const fim = a.fim ? parseLocal(a.fim.length <= 10 ? a.fim + 'T23:59' : a.fim) : new Date();
    const inicio = a.inicio ? parseLocal(a.inicio) : new Date(fim.getTime() - (a.dias ?? 30) * 86400000);
    if (inicio > fim) throw new UserError('O início do período é depois do fim.');
    return { inicio, fim, texto: `${fmtDate(inicio, false)} a ${fmtDate(fim, false)}` };
  }

  const showOrder = (o) => ({ pedido: o.pedido, data: fmtDate(o.data), torcedor: o.torcedor, email: o.email, situacao: o.situacao, total: brl(o.total), itens: o.itens.map((i) => `${i.quantidade}x ${i.produto}`), ...(o.cupons.length ? { cupons: o.cupons } : {}) });

  // ---- experiências
  async function experiences() {
    return (await all('/FlamengoExperiencia/SocioExperiences', { $expand: 'minPlan' }))
      .sort((a, b) => (num(a.sortOrder) ?? 999) - (num(b.sortOrder) ?? 999) || num(a.points) - num(b.points));
  }
  async function redemptions() {
    return (await all('/FlamengoResgate/SocioRedemptions', { $expand: 'customer,experience,plan' }))
      .map((r) => ({
        voucher: r.voucher, experiencia: r.experience?.code, nomeExperiencia: r.experience?.name, torcedor: r.customer?.name, email: r.customer?.uid,
        plano: r.plan?.name ?? r.plan?.code ?? null, pontos: num(r.points), data: toDate(r.date), origem: r.origin ?? null,
        ...(r.orderCode ? { pedido: r.orderCode, valor: brl(num(r.amount)) } : {}),
        forma: r.orderCode ? 'compra na loja' : 'resgate com pontos',
        situacao: r.cancelledAt ? `cancelado em ${fmtDate(r.cancelledAt)}${r.cancelReason ? ` (${r.cancelReason})` : ''}` : r.usedAt ? `utilizado em ${fmtDate(r.usedAt)}` : 'válido',
        _ativo: !r.cancelledAt, _pontos: !r.orderCode,
      }))
      .sort((a, b) => (b.data?.getTime() ?? 0) - (a.data?.getTime() ?? 0));
  }
  function showExperience(x, rs, os) {
    const mine = rs.filter((r) => r.experiencia === x.code && r._ativo && r._pontos);
    const sold = os.filter((o) => !EXCLUDED_STATUS.has(o.situacaoCodigo)).flatMap((o) => o.itens).filter((i) => i.codigo === `EXP-${x.code}`);
    const stock = num(x.stock);
    return {
      code: x.code, nome: x.name, descricao: clean(x.description) || null, ativa: x.active !== false,
      pontos: num(x.points), preco: x.price != null ? brl(num(x.price)) : 'só por pontos', planoMinimo: x.minPlan?.name ?? x.minPlan?.code ?? 'qualquer plano',
      vagas: stock == null ? 'sem limite' : stock, resgatesComPontos: mine.length, pontosResgatados: mine.reduce((s, r) => s + (r.pontos ?? 0), 0), vendidasNaLoja: sold.reduce((s, i) => s + i.quantidade, 0),
      receitaNaLoja: brl(sold.reduce((s, i) => s + i.total, 0)), ordem: num(x.sortOrder), produtoNaLoja: `EXP-${x.code}`,
      ...(x.statusMessage ? { avisoPublicacao: x.statusMessage } : {}),
    };
  }
  const expFields = {
    nome: S.str('Nome da experiência'), descricao: S.str('Descrição para o torcedor'), pontos: S.int('Pontos para resgatar'),
    preco: S.num('Preço em R$ para comprar na loja (produto EXP-<código>)'), vagas: S.int('Vagas (estoque); sem valor = sem limite'),
    planoMinimo: S.str('Código do plano mínimo para resgatar (sem valor ao criar = qualquer plano)'), ativa: S.bool('Ativa na vitrine e na loja'), ordem: S.int('Ordem na vitrine'),
  };
  function expBody(a, body, changes) {
    if (a.nome) { body.name = a.nome; changes.push(`nome "${a.nome}"`); }
    if (a.descricao) { body.description = a.descricao; changes.push('descrição'); }
    if (a.pontos != null) { body.points = a.pontos; changes.push(`${a.pontos} pontos`); }
    if (a.preco != null) { body.price = String(a.preco); changes.push(`preço ${brl(a.preco)}`); }
    if (a.vagas != null) { body.stock = a.vagas; changes.push(`${a.vagas} vaga(s)`); }
    if (a.planoMinimo) { body.minPlan = { code: a.planoMinimo }; changes.push(`plano mínimo ${a.planoMinimo}`); }
    if (a.ativa != null) { body.active = a.ativa; changes.push(a.ativa ? 'ativa' : 'inativa'); }
    if (a.ordem != null) { body.sortOrder = a.ordem; changes.push(`ordem ${a.ordem}`); }
  }

  return [
    {
      name: 'buscar_produtos',
      description: 'Busca produtos na loja publicada (texto livre ou código): preço, situação do estoque e link. Para dados completos, preço e estoque por depósito use detalhe_produto.',
      inputSchema: obj({ texto: S.str('O que procurar, ex. "manto jogo 1" ou um código'), quantidade: S.int('Máximo de resultados (padrão 10, até 30)') }, ['texto']),
      handler: async ({ texto, quantidade }) => {
        const r = await http({ base: 'occ', method: 'GET', path: '/products/search', query: { lang: cfg.lang ?? 'pt', curr: cfg.curr ?? 'BRL', query: texto, pageSize: Math.min(30, quantidade ?? 10), fields: 'products(code,name,price(value),stock(stockLevel,stockLevelStatus)),pagination(totalResults)' } });
        if (r.status >= 400) throw new UserError(`A loja recusou a busca (${r.status}).`);
        const st = { inStock: 'em estoque', lowStock: 'estoque baixo', outOfStock: 'esgotado' };
        return { total: r.json?.pagination?.totalResults ?? 0, produtos: (r.json?.products ?? []).map((p) => ({ code: p.code, nome: clean(p.name), preco: brl(p.price?.value), estoque: st[p.stock?.stockLevelStatus] ?? p.stock?.stockLevelStatus, quantidade: p.stock?.stockLevel ?? null, link: `${cfg.storeUrl}/p/${p.code}` })) };
      },
    },
    {
      name: 'detalhe_produto',
      description: 'Ficha de um produto para o gestor: nome e aprovação no catálogo (Staged e Online), categorias, preço em vigor, estoque por depósito (disponível, reservado, livre) e as opções (tamanhos) com o código de cada uma, que é onde fica o estoque das roupas.',
      inputSchema: obj({ codigo: S.str('Código do produto (de buscar_produtos)') }, ['codigo']),
      handler: async ({ codigo }) => {
        const [ps, pr, st, view] = await Promise.all([products(codigo), prices(codigo), stocks(codigo), storeView(codigo)]);
        if (!ps.length && !view) throw new UserError(`Produto ${codigo} não encontrado no catálogo do Flamengo.`);
        const online = ps.find((p) => p.catalogVersion?.version === 'Online');
        const staged = ps.find((p) => p.catalogVersion?.version === 'Staged');
        const base = online ?? staged;
        const price = brlPrice(pr);
        const opts = [...(view?.variantOptions ?? []), ...(view?.baseOptions ?? []).flatMap((b) => b.options ?? [])].filter((o) => o.code !== codigo);
        const managed = MANAGED_ELSEWHERE.find(([re]) => re.test(codigo.toUpperCase()));
        return {
          code: codigo, nome: base?.name ?? clean(view?.name), descricao: clean(base?.description).slice(0, 500) || null,
          catalogo: { staged: staged ? staged.approvalStatus?.code ?? 'sem aprovação' : 'não existe', online: online ? online.approvalStatus?.code ?? 'sem aprovação' : 'não existe no Online (não aparece na loja)' },
          categorias: results(base?.supercategories).map((c) => c.name ?? c.code).filter((v, i, a) => a.indexOf(v) === i),
          categoriaNasVendas: saleCategory(codigo),
          preco: price ? brl(num(price.price)) : view?.price?.value != null ? `${brl(view.price.value)} (herdado do produto base)` : 'sem preço',
          linhasDePreco: pr.map((r) => ({ moeda: r.currency?.isocode, preco: brl(num(r.price)), unidade: r.unit?.code, aPartirDe: num(r.minqtd) })),
          estoque: st.map(showStock).sort((a, b) => (a.deposito === MAIN_WAREHOUSE ? -1 : b.deposito === MAIN_WAREHOUSE ? 1 : 0)),
          ...(!st.length && opts.length ? { observacaoEstoque: 'O estoque deste produto fica nas opções (tamanhos): use detalhe_produto com o código da opção.' } : {}),
          opcoes: opts.map((o) => ({ code: o.code, opcao: (o.variantOptionQualifiers ?? []).map((x) => `${x.name}: ${x.value}`).join(', '), preco: brl(o.priceData?.value), estoqueNaLoja: o.stock?.stockLevel ?? o.stock?.stockLevelStatus })),
          naLoja: view ? { situacao: view.stock?.stockLevelStatus, compravel: view.purchasable !== false, link: `${cfg.storeUrl}/p/${codigo}` } : 'não aparece na loja publicada',
          ...(managed ? { quemCuida: managed[1] } : {}),
        };
      },
    },
    {
      name: 'alterar_preco',
      description: 'Muda o preço (R$) de um produto da loja. A linha de preço vale para Staged e Online ao mesmo tempo, sem sincronizar; página do produto e carrinho mudam na hora, busca e listagem na próxima indexação. Ingressos, planos de sócio e experiências têm ferramenta própria.',
      inputSchema: obj({ codigo: S.str('Código do produto (ou da opção/tamanho, se o preço for dela)'), preco: S.num('Preço novo em R$'), confirmar: S.confirm }, ['codigo', 'preco']),
      handler: async (a) => {
        managedElsewhere(a.codigo);
        if (!(a.preco > 0)) throw new UserError('Informe um preço maior que zero.');
        const rows = (await prices(a.codigo)).filter((r) => r.currency?.isocode === 'BRL');
        if (!rows.length) throw new UserError(`${a.codigo} não tem linha de preço própria em R$ (o preço pode vir do produto base). Confira com detalhe_produto.`);
        if (rows.length > 1) throw new UserError(`${a.codigo} tem ${rows.length} linhas de preço em R$ (por quantidade ou grupo): altere pelo Backoffice.`);
        const old = num(rows[0].price);
        const pct = old ? Math.round(((a.preco - old) / old) * 1000) / 10 : null;
        const c = needConfirm(a, `Mudar o preço de ${a.codigo} de ${brl(old)} para ${brl(a.preco)}${pct != null ? ` (${pct > 0 ? '+' : ''}${String(pct).replace('.', ',')}%)` : ''}. Vale na loja em instantes (Staged e Online).`);
        if (c) return c;
        await odata('POST', '/FlamengoPreco/PriceRows', { body: { productId: a.codigo, currency: { isocode: 'BRL' }, price: String(a.preco) } });
        const after = brlPrice(await prices(a.codigo));
        return { executado: true, produto: a.codigo, antes: brl(old), agora: brl(num(after?.price)), link: `${cfg.storeUrl}/p/${a.codigo}`, observacao: 'A página do produto e o carrinho já usam o preço novo. A busca e a listagem de categorias mostram o preço do índice de busca até a próxima indexação.' };
      },
    },
    {
      name: 'alterar_estoque',
      description: 'Ajusta o estoque de um produto (ou opção/tamanho) num depósito: valor novo (disponivel) ou entrada/saída (ajuste, ex. +20 ou -5). Depósito padrão: warehouse_s (o da loja online). O reservado (pedidos em andamento) não muda.',
      inputSchema: obj({ codigo: S.str('Código do produto ou da opção (tamanho)'), disponivel: S.int('Quantidade disponível nova'), ajuste: S.int('Entrada (+) ou saída (-) sobre o disponível atual'), deposito: S.str('Código do depósito (padrão warehouse_s; lojas físicas: barrastore, copacabanastore, maracanastore)'), confirmar: S.confirm }, ['codigo']),
      handler: async (a) => {
        managedElsewhere(a.codigo);
        if ((a.disponivel == null) === (a.ajuste == null)) throw new UserError('Informe disponivel (valor novo) ou ajuste (+/-), um dos dois.');
        const wh = a.deposito || MAIN_WAREHOUSE;
        const rows = await stocks(a.codigo);
        const row = rows.find((s) => s.warehouse?.code === wh);
        if (!row) throw new UserError(`${a.codigo} não tem estoque no depósito ${wh}${rows.length ? ` (tem em: ${rows.map((s) => s.warehouse?.code).join(', ')})` : '; se for roupa, o estoque fica nas opções (tamanhos), veja detalhe_produto'}.`);
        const old = num(row.available) ?? 0;
        const novo = a.disponivel != null ? a.disponivel : old + a.ajuste;
        if (novo < 0) throw new UserError(`O disponível ficaria negativo (${old} ${a.ajuste}).`);
        const c = needConfirm(a, `Estoque de ${a.codigo} no depósito ${wh}: disponível de ${old} para ${novo} (reservado ${num(row.reserved) ?? 0}, livre para venda ${novo - (num(row.reserved) ?? 0)}).`);
        if (c) return c;
        await odata('POST', '/FlamengoEstoque/StockLevels', { body: { productCode: a.codigo, warehouse: { code: wh }, available: novo } });
        const after = (await stocks(a.codigo)).find((s) => s.warehouse?.code === wh);
        return { executado: true, produto: a.codigo, antes: old, estoque: after ? showStock(after) : null };
      },
    },
    {
      name: 'resumo_vendas',
      description: 'Resumo de vendas da loja num período (padrão: últimos 30 dias), com as regras do painel do Backoffice: receita, pedidos, ticket médio, frete e descontos; por categoria (ingressos, sócio-torcedor, experiências, estacionamento, alimentação, personalização, produtos da loja); produtos mais vendidos; cupons usados; pedidos aguardando pagamento. Cancelados e inválidos ficam de fora.',
      inputSchema: obj({ dias: S.int('Últimos N dias (padrão 30)'), inicio: S.str('Início (AAAA-MM-DD), em vez de dias'), fim: S.str('Fim (AAAA-MM-DD), padrão hoje') }),
      handler: async (a) => {
        const p = period(a);
        const all = (await orders()).filter((o) => o.data && o.data >= p.inicio && o.data <= p.fim);
        const valid = all.filter((o) => !EXCLUDED_STATUS.has(o.situacaoCodigo));
        const sum = (xs, f) => xs.reduce((s, x) => s + (f(x) ?? 0), 0);
        const receita = sum(valid, (o) => o.total);
        const cats = {};
        const prods = {};
        for (const o of valid) for (const i of o.itens) {
          const c = (cats[i.categoria] ??= { itens: 0, receita: 0, pedidos: new Set() });
          c.itens += i.quantidade; c.receita += i.total; c.pedidos.add(o.pedido);
          if (i.categoria === 'Loja (produtos)') { const k = (prods[i.codigo] ??= { produto: i.produto, codigo: i.codigo, quantidade: 0, receita: 0 }); k.quantidade += i.quantidade; k.receita += i.total; }
        }
        const coupons = {};
        for (const o of valid) for (const cp of o.cupons) coupons[cp] = (coupons[cp] ?? 0) + 1;
        const waiting = valid.filter((o) => o.situacaoCodigo === 'PAYMENT_NOT_CAPTURED');
        return {
          periodo: p.texto,
          pedidos: valid.length, receita: brl(receita), ticketMedio: valid.length ? brl(receita / valid.length) : null,
          frete: brl(sum(valid, (o) => o.frete)), descontos: brl(sum(valid, (o) => o.descontos)),
          porCategoria: Object.entries(cats).sort((x, y) => y[1].receita - x[1].receita).map(([k, v]) => ({ categoria: k, itens: v.itens, pedidos: v.pedidos.size, receita: brl(v.receita) })),
          maisVendidosDaLoja: Object.values(prods).sort((x, y) => y.quantidade - x.quantidade || y.receita - x.receita).slice(0, 10).map((x) => ({ ...x, receita: brl(x.receita) })),
          cuponsUsados: coupons,
          aguardandoPagamento: { pedidos: waiting.length, valor: brl(sum(waiting, (o) => o.total)), lista: waiting.slice(0, 10).map(showOrder) },
          foraDoResumo: { cancelados: all.filter((o) => o.situacaoCodigo === 'CANCELLED').length, invalidos: all.filter((o) => o.situacaoCodigo === 'CHECKED_INVALID').length },
          observacao: 'Receita = total pago dos pedidos (com frete e já com descontos). Pedidos aguardando pagamento (Pix/boleto) entram na receita, como no painel do Backoffice.',
        };
      },
    },
    {
      name: 'pedidos',
      description: 'Pedidos da loja, mais novos primeiro, com filtros: texto (código do pedido, e-mail ou nome do cliente, código ou nome de produto), situação, categoria e período.',
      inputSchema: obj({
        texto: S.str('Parte do código do pedido, do e-mail ou nome do cliente, ou do código/nome de um produto (opcional)'),
        situacao: { type: 'string', enum: Object.keys(ORDER_STATUS), description: 'Situação do pedido (opcional), ex. PAYMENT_NOT_CAPTURED aguardando pagamento' },
        categoria: { type: 'string', enum: [...SALE_CATEGORIES.map(([, c]) => c), 'Loja (produtos)'], description: 'Só pedidos com itens dessa categoria (opcional)' },
        dias: S.int('Últimos N dias (opcional)'),
      }),
      handler: async (a) => {
        const needle = String(a.texto ?? '').trim().toLowerCase();
        const since = a.dias ? new Date(Date.now() - a.dias * 86400000) : null;
        const list = (await orders()).filter((o) => (!since || (o.data && o.data >= since)) && (!a.situacao || o.situacaoCodigo === a.situacao)
          && (!a.categoria || o.itens.some((i) => i.categoria === a.categoria))
          && (!needle || [o.pedido, o.email, o.torcedor, ...o.itens.flatMap((i) => [i.codigo, i.produto])].some((v) => String(v ?? '').toLowerCase().includes(needle))));
        return { total: list.length, valor: brl(list.reduce((s, o) => s + o.total, 0)), pedidos: list.slice(0, 50).map(showOrder), ...(list.length > 50 ? { observacao: 'Mostrando os 50 mais novos; use os filtros.' } : {}) };
      },
    },
    {
      name: 'detalhe_pedido',
      description: 'Um pedido completo: cliente, situação, itens com categoria e preço, frete, descontos e cupons.',
      inputSchema: obj({ pedido: S.str('Código do pedido') }, ['pedido']),
      handler: async ({ pedido }) => {
        const o = (await orders()).find((x) => x.pedido === String(pedido).trim());
        if (!o) throw new UserError(`Pedido ${pedido} não encontrado.`);
        return {
          pedido: o.pedido, data: fmtDate(o.data), torcedor: o.torcedor, email: o.email, situacao: o.situacao,
          itens: o.itens.map((i) => ({ ...i, unitario: i.unitario != null ? brl(i.unitario) : null, total: brl(i.total) })),
          subtotal: brl(o.itens.reduce((s, i) => s + i.total, 0)), frete: brl(o.frete), descontos: brl(o.descontos), total: brl(o.total), cupons: o.cupons,
        };
      },
    },
    {
      name: 'promocoes',
      description: 'Promoções da loja (regras do motor de promoções do site): nome, descrição, prioridade, vigência e situação. Só leitura: criar, ativar ou desativar uma promoção exige republicar as regras, o que se faz no Backoffice (Marketing › Promoções).',
      inputSchema: obj({ situacao: { type: 'string', enum: ['publicadas', 'nao_publicadas', 'inativas', 'todas'], description: 'Padrão: publicadas (as que valem na loja)' }, texto: S.str('Parte do nome, código ou descrição (opcional)') }),
      handler: async ({ situacao = 'publicadas', texto }) => {
        const want = { publicadas: ['PUBLISHED'], nao_publicadas: ['UNPUBLISHED', 'MODIFIED'], inativas: ['INACTIVE', 'ARCHIVED'], todas: null }[situacao];
        const needle = String(texto ?? '').trim().toLowerCase();
        const rules = (await all('/FlamengoPromocao/PromotionSourceRules', { $expand: 'status,website' }))
          .filter((r) => r.website?.Identifier === STORE_PROMO_GROUP || r.website?.identifier === STORE_PROMO_GROUP)
          .filter((r) => !want || want.includes(r.status?.code))
          .filter((r) => !needle || [r.code, r.name, r.description].some((v) => clean(v).toLowerCase().includes(needle)))
          .sort((a, b) => (num(b.priority) ?? 0) - (num(a.priority) ?? 0));
        const now = Date.now();
        return {
          total: rules.length,
          promocoes: rules.map((r) => {
            const ini = toDate(r.startDate); const fim = toDate(r.endDate);
            return {
              code: r.code, nome: r.name || r.code, descricao: clean(r.description) || null, prioridade: num(r.priority), situacao: RULE_STATUS[r.status?.code] ?? r.status?.code,
              vigencia: ini && fim ? `${fmtDate(ini, false)} a ${fmtDate(fim, false)}` : fim ? `até ${fmtDate(fim, false)}` : ini ? `a partir de ${fmtDate(ini, false)}` : 'sem prazo',
              ...(fim && fim.getTime() < now ? { aviso: 'vencida' } : ini && ini.getTime() > now ? { aviso: 'ainda não começou' } : {}),
            };
          }),
          comoMudar: 'Backoffice › Marketing › Promoções: editar a regra e publicar (Publicar). O MCP só consulta.',
        };
      },
    },
    ...couponTools(odata),
    {
      name: 'experiencias',
      description: 'Vitrine de experiências do sócio-torcedor (tour, treino, manto autografado...): pontos, preço na loja, plano mínimo, vagas, resgates com pontos e vendas na loja (produto EXP-<código>).',
      inputSchema: obj(),
      handler: async () => {
        const [xs, rs, os] = await Promise.all([experiences(), redemptions(), orders()]);
        return { experiencias: xs.map((x) => showExperience(x, rs, os)) };
      },
    },
    {
      name: 'resgates_experiencias',
      description: 'Vouchers de experiências, mais novos primeiro: resgates com pontos e compras na loja (produto EXP-), com torcedor, plano, pontos ou pedido, data e situação. Filtros opcionais por experiência e por e-mail.',
      inputSchema: obj({ experiencia: S.str('Código da experiência (opcional)'), email: S.str('E-mail do torcedor (opcional)'), soValidos: S.bool('Só os não cancelados') }),
      handler: async (a) => {
        const list = (await redemptions()).filter((r) => (!a.experiencia || r.experiencia === a.experiencia) && (!a.email || r.email === String(a.email).toLowerCase()) && (!a.soValidos || r._ativo));
        return { total: list.length, comPontos: list.filter((r) => r._pontos).length, compradosNaLoja: list.filter((r) => !r._pontos).length, resgates: list.slice(0, 100).map(({ _ativo, _pontos, ...r }) => ({ ...r, ...(_pontos ? {} : { pontos: undefined }), data: fmtDate(r.data) })) };
      },
    },
    {
      name: 'criar_experiencia',
      description: 'Cadastra uma experiência nova na vitrine do sócio. Ao salvar, o Commerce cria o produto EXP-<código> na loja (preço e vagas como estoque) sozinho.',
      inputSchema: obj({ codigo: S.str('Código em maiúsculas, ex. VISITA-MUSEU'), ...expFields, confirmar: S.confirm }, ['codigo', 'nome', 'pontos']),
      handler: async (a) => {
        const code = String(a.codigo).trim().toUpperCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Z0-9]+/g, '-').replace(/^-|-$/g, '');
        if (!code) throw new UserError('Código inválido.');
        if ((await experiences()).some((x) => x.code === code)) throw new UserError(`Já existe a experiência ${code}; use atualizar_experiencia.`);
        const body = { code };
        const changes = [];
        expBody(a, body, changes);
        if (body.active == null) body.active = true;
        const c = needConfirm(a, `Criar a experiência ${code}: ${changes.join('; ')}${body.active ? ' (ativa: aparece na vitrine e na loja como EXP-' + code + ')' : ''}.`);
        if (c) return c;
        await odata('POST', '/FlamengoExperiencia/SocioExperiences', { body });
        const [xs, rs, os] = await Promise.all([experiences(), redemptions(), orders()]);
        const x = xs.find((e) => e.code === code);
        return { executado: true, experiencia: x ? showExperience(x, rs, os) : code, loja: `${cfg.storeUrl}/p/EXP-${code}` };
      },
    },
    {
      name: 'atualizar_experiencia',
      description: 'Altera uma experiência (nome, descrição, pontos, preço, vagas, plano mínimo, ordem) ou tira/volta da vitrine (ativa). O Commerce atualiza o produto EXP-<código> da loja sozinho.',
      inputSchema: obj({ experiencia: S.str('Código da experiência'), ...expFields, confirmar: S.confirm }, ['experiencia']),
      handler: async (a) => {
        const x = (await experiences()).find((e) => e.code === a.experiencia);
        if (!x) throw new UserError('Experiência não encontrada. Veja os códigos em experiencias.');
        const body = { code: x.code };
        const changes = [];
        expBody(a, body, changes);
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Alterar a experiência ${x.name} (${x.code}): ${changes.join('; ')}.`);
        if (c) return c;
        await odata('PATCH', `/FlamengoExperiencia/SocioExperiences('${enc(x.code)}')`, { body });
        const [xs, rs, os] = await Promise.all([experiences(), redemptions(), orders()]);
        return { executado: true, experiencia: showExperience(xs.find((e) => e.code === x.code), rs, os) };
      },
    },
  ];
}

export const _test = { saleCategory, couponCodes, parseCancelMessage, parseLocal, fmtDate, brl, toDate, UserError };
