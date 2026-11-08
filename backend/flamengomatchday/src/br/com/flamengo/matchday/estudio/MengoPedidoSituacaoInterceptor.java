package br.com.flamengo.matchday.estudio;

import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PersistenceOperation;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.user.UserService;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mengo Estúdio (08/11, PR 4): quando a situação do Estúdio de um pedido muda (pela loja, pela estamparia simulada ou
 * pelo gestor no Backoffice), grava o evento da linha do tempo ({@code MengoPedidoEvento}) e:
 * <ul>
 * <li>Em produção (ou depois): número de série de cada camiseta, ME-&lt;linha&gt;-&lt;estampa&gt;-&lt;sequência&gt;
 * (ex.: ME-URU-001-000419; linha com mais de uma unidade: "ME-URU-001-000419 a 000420");</li>
 * <li>Enviado (ou Entregue): código de rastreio demonstrativo, se ainda não houver.</li>
 * </ul>
 * O texto e o autor do evento vêm de {@link #comNota}; sem eles, um texto padrão e o usuário da sessão.
 * Mapeado no tipo Order pelo nome; lê e grava os atributos pelo ModelService.
 */
public class MengoPedidoSituacaoInterceptor implements PrepareInterceptor<ItemModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(MengoPedidoSituacaoInterceptor.class);
	private static final ThreadLocal<String[]> NOTA = new ThreadLocal<>();

	private MengoEstudioService mengoEstudioService;
	private UserService userService;
	private FlexibleSearchService flexibleSearchService;

	/** Executa a gravação com o texto e o autor do evento da linha do tempo */
	public static void comNota(final String nota, final String autor, final Runnable gravar)
	{
		NOTA.set(new String[] { nota, autor });
		try
		{
			gravar.run();
		}
		finally
		{
			NOTA.remove();
		}
	}

	@Override
	public void onPrepare(final ItemModel order, final InterceptorContext ctx) throws InterceptorException
	{
		if (!(order instanceof AbstractOrderModel) || !ctx.isModified(order, "mengoSituacao"))
		{
			return;
		}
		final ModelService ms = ctx.getModelService();
		final Object value = ms.getAttributeValue(order, "mengoSituacao");
		if (!(value instanceof HybrisEnumValue))
		{
			return;
		}
		final String situacao = ((HybrisEnumValue) value).getCode();
		final AbstractOrderModel o = (AbstractOrderModel) order;
		try
		{
			if (MengoEstudioOrderService.EM_PRODUCAO.equals(situacao) || MengoEstudioOrderService.ENVIADO.equals(situacao)
					|| MengoEstudioOrderService.ENTREGUE.equals(situacao))
			{
				series(o, ctx);
			}
			if ((MengoEstudioOrderService.ENVIADO.equals(situacao) || MengoEstudioOrderService.ENTREGUE.equals(situacao))
					&& StringUtils.isBlank((String) ms.getAttributeValue(order, "mengoRastreio")))
			{
				ms.setAttributeValue(order, "mengoRastreio", rastreio(o));
			}
			final String[] nota = NOTA.get();
			final ItemModel evento = ms.create("MengoPedidoEvento");
			ms.setAttributeValue(evento, "order", order);
			ms.setAttributeValue(evento, "situacao", value);
			ms.setAttributeValue(evento, "date", new Date(mengoEstudioService.now()));
			ms.setAttributeValue(evento, "note",
					nota != null && StringUtils.isNotBlank(nota[0]) ? nota[0] : notaPadrao(situacao, o, ms));
			ms.setAttributeValue(evento, "author", nota != null && StringUtils.isNotBlank(nota[1]) ? nota[1] : autor());
			ctx.registerElementFor(evento, PersistenceOperation.SAVE);
			LOG.info("Mengo Estúdio: pedido {} -> {}", o.getCode(), situacao);
		}
		catch (final RuntimeException e)
		{
			// a mudança da situação vale mesmo se a linha do tempo falhar
			LOG.error("Mengo Estúdio: falha ao registrar a situação {} do pedido {}", situacao, o.getCode(), e);
		}
	}

	/** Número de série das camisetas que ainda não têm (a estamparia imprime na etiqueta ao produzir) */
	protected void series(final AbstractOrderModel order, final InterceptorContext ctx)
	{
		if (order.getEntries() == null)
		{
			return;
		}
		final ModelService ms = ctx.getModelService();
		final Map<Object, Long> usados = new HashMap<>();
		for (final AbstractOrderEntryModel e : order.getEntries())
		{
			final Object estampa = ms.getAttributeValue(e, "mengoEstampa");
			if (!(estampa instanceof ItemModel) || StringUtils.isNotBlank((String) ms.getAttributeValue(e, "mengoNumeroSerie")))
			{
				continue;
			}
			final ItemModel est = (ItemModel) estampa;
			final long qtd = Math.max(1L, e.getQuantity() == null ? 1L : e.getQuantity().longValue());
			final long inicio = usados.computeIfAbsent(est.getPk(), k -> Long.valueOf(proximo(est))).longValue();
			usados.put(est.getPk(), Long.valueOf(inicio + qtd));
			final String base = "ME-" + prefixo(est, ms) + "-" + String.format("%03d", numero(est, ms)) + "-";
			final String serie = base + String.format("%06d", inicio)
					+ (qtd > 1 ? " a " + String.format("%06d", inicio + qtd - 1) : "");
			ms.setAttributeValue(e, "mengoNumeroSerie", serie);
			ctx.registerElementFor(e, PersistenceOperation.SAVE);
		}
	}

	/** Próximo número da sequência da estampa: unidades já numeradas nos pedidos + 1 */
	protected long proximo(final ItemModel estampa)
	{
		try
		{
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {quantity} FROM {OrderEntry} WHERE {mengoEstampa} = ?e AND {mengoNumeroSerie} IS NOT NULL");
			q.addQueryParameter("e", estampa);
			q.setResultClassList(Collections.singletonList(Long.class));
			final List<Long> qtds = flexibleSearchService.<Long> search(q).getResult();
			long total = 0;
			for (final Long n : qtds)
			{
				total += n == null ? 1 : Math.max(1, n.longValue());
			}
			return total + 1;
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Mengo Estúdio: sequência da estampa não lida: {}", e.getMessage());
			return 1;
		}
	}

	/** HIS, URU, JOG: as três primeiras letras do código da linha */
	protected String prefixo(final ItemModel estampa, final ModelService ms)
	{
		final Object linha = ms.getAttributeValue(estampa, "linha");
		final String code = linha instanceof ItemModel ? (String) ms.getAttributeValue(linha, "code") : null;
		final String letras = StringUtils.upperCase(StringUtils.stripAccents(StringUtils.defaultString(code, "EST")))
				.replaceAll("[^A-Z]", "");
		return StringUtils.rightPad(StringUtils.left(letras, 3), 3, 'X');
	}

	protected int numero(final ItemModel estampa, final ModelService ms)
	{
		final Object n = ms.getAttributeValue(estampa, "number");
		return n instanceof Number ? ((Number) n).intValue() : 0;
	}

	/** Código demonstrativo no formato dos Correios (BR + 9 dígitos + BR), estável por pedido */
	protected String rastreio(final AbstractOrderModel order)
	{
		final long n = Math.abs((long) StringUtils.defaultString(order.getCode()).hashCode()) % 1_000_000_000L;
		return "BR" + String.format("%09d", n) + "BR";
	}

	protected String notaPadrao(final String situacao, final AbstractOrderModel order, final ModelService ms)
	{
		switch (situacao)
		{
			case MengoEstudioOrderService.RECEBIDO:
				return "Pedido recebido. A estamparia começa a produção em breve.";
			case MengoEstudioOrderService.EM_PRODUCAO:
				return "A estamparia começou a produção.";
			case MengoEstudioOrderService.ENVIADO:
				return "Pedido enviado. Rastreio " + ms.getAttributeValue(order, "mengoRastreio") + ".";
			case MengoEstudioOrderService.ENTREGUE:
				return "Pedido entregue.";
			case MengoEstudioOrderService.CANCELADO:
				return "Pedido cancelado.";
			case "EM_DEVOLUCAO":
				return "Devolução em andamento.";
			case "REEMBOLSADO":
				return "Valor reembolsado.";
			default:
				return situacao;
		}
	}

	protected String autor()
	{
		try
		{
			final UserModel u = userService.getCurrentUser();
			return u == null ? "sistema" : u.getUid();
		}
		catch (final RuntimeException e)
		{
			return "sistema";
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
