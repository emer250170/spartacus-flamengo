package br.com.flamengo.matchday.estudio.impl;

import br.com.flamengo.matchday.estudio.MengoEstampariaGateway;
import br.com.flamengo.matchday.estudio.MengoEstudioCartService;
import br.com.flamengo.matchday.estudio.MengoEstudioOrderService;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import br.com.flamengo.matchday.estudio.MengoPedidoSituacaoInterceptor;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Pós-venda do Mengo Estúdio (08/11, PR 4). Ver {@link MengoEstudioOrderService}.
 */
public class DefaultMengoEstudioOrderService implements MengoEstudioOrderService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMengoEstudioOrderService.class);
	private static final String ENUM = "MengoPedidoSituacao";
	private static final String RECUSA_PAGAMENTO = "Pagamento recusado pelo emissor do cartão. Nenhum valor foi cobrado e "
			+ "nenhum pedido foi criado; seus itens continuam no carrinho. Confira os dados ou use outro cartão e tente de novo.";

	private MengoEstudioService mengoEstudioService;
	private MengoEstudioCartService mengoEstudioCartService;
	private MengoEstampariaGateway mengoEstampariaGateway;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private EnumerationService enumerationService;

	@Override
	public boolean temEstudio(final AbstractOrderModel order)
	{
		return order != null && order.getEntries() != null
				&& order.getEntries().stream().anyMatch(mengoEstudioCartService::isEstudioEntry);
	}

	@Override
	public boolean soEstudio(final AbstractOrderModel order)
	{
		return temEstudio(order) && order.getEntries().stream().allMatch(mengoEstudioCartService::isEstudioEntry);
	}

	@Override
	public String situacao(final AbstractOrderModel order)
	{
		final Object v = attr(order, "mengoSituacao");
		return v instanceof HybrisEnumValue ? ((HybrisEnumValue) v).getCode() : null;
	}

	@Override
	public void iniciar(final OrderModel order)
	{
		if (!mengoEstudioService.isActive() || !temEstudio(order) || situacao(order) != null)
		{
			return;
		}
		mudar(order, RECEBIDO, "Pedido recebido. A estamparia começa a produção em breve.", "loja");
	}

	@Override
	public void mudar(final OrderModel order, final String situacao, final String nota, final String autor)
	{
		final HybrisEnumValue valor = enumerationService.getEnumerationValue(ENUM, situacao);
		MengoPedidoSituacaoInterceptor.comNota(nota, autor, () -> {
			modelService.setAttributeValue(order, "mengoSituacao", valor);
			modelService.save(order);
		});
	}

	@Override
	public OrderModel pedido(final CustomerModel customer, final String code)
	{
		if (customer == null || StringUtils.isBlank(code))
		{
			return null;
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {code} = ?c AND {user} = ?u AND {versionID} IS NULL");
		q.addQueryParameter("c", code.trim());
		q.addQueryParameter("u", customer);
		final List<OrderModel> list = flexibleSearchService.<OrderModel> search(q).getResult();
		return list.isEmpty() ? null : list.get(0);
	}

	@Override
	public List<Map<String, Object>> pedidos(final CustomerModel customer)
	{
		if (customer == null)
		{
			return Collections.emptyList();
		}
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user} = ?u AND {versionID} IS NULL ORDER BY {date} DESC, {creationtime} DESC");
		q.addQueryParameter("u", customer);
		q.setCount(200);
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final OrderModel o : flexibleSearchService.<OrderModel> search(q).getResult())
		{
			final boolean estudio = temEstudio(o);
			final List<String> origem = new ArrayList<>();
			if (!soEstudio(o))
			{
				origem.add("loja");
			}
			if (estudio)
			{
				origem.add("estudio");
			}
			final Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", o.getCode());
			final Date d = o.getDate() != null ? o.getDate() : o.getCreationtime();
			m.put("data", d == null ? null : Long.valueOf(d.getTime()));
			m.put("total", Double.valueOf(total(o)));
			m.put("status", o.getStatus() == null ? null : o.getStatus().getCode());
			m.put("statusDisplay", attr(o, "statusDisplay"));
			m.put("origem", origem);
			m.put("situacao", estudio ? StringUtils.defaultString(situacao(o), RECEBIDO) : null);
			list.add(m);
		}
		return list;
	}

	@Override
	public Map<String, Object> detalhe(final OrderModel order)
	{
		// pedido feito antes do PR 4: começa a linha do tempo agora
		if (situacao(order) == null && temEstudio(order) && mengoEstudioService.isActive())
		{
			try
			{
				iniciar(order);
			}
			catch (final RuntimeException e)
			{
				LOG.warn("Mengo Estúdio: situação inicial do pedido {} não gravada: {}", order.getCode(), e.getMessage());
			}
		}
		final String situacao = situacao(order);
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("code", order.getCode());
		m.put("temEstudio", Boolean.valueOf(temEstudio(order)));
		m.put("soEstudio", Boolean.valueOf(soEstudio(order)));
		m.put("situacao", situacao);
		m.put("eventos", eventos(order));
		m.put("rastreio", attr(order, "mengoRastreio"));
		m.put("itens", mengoEstudioCartService.itens(order));
		m.put("podeCancelar", Boolean.valueOf(mengoEstudioService.isActive() && RECEBIDO.equals(situacao)));
		final double valor = valorEstudio(order);
		m.put("valorEstudio", Double.valueOf(valor));
		m.put("valorEstudioTexto", dinheiro(valor));
		m.put("agora", Long.valueOf(mengoEstudioService.now()));
		return m;
	}

	@Override
	public Map<String, Object> cancelar(final OrderModel order, final String autor)
	{
		if (!mengoEstudioService.isActive() || !temEstudio(order))
		{
			throw new IllegalArgumentException("Este pedido não tem camiseta do Mengo Estúdio.");
		}
		final String situacao = StringUtils.defaultString(situacao(order), RECEBIDO);
		if (CANCELADO.equals(situacao))
		{
			throw new IllegalArgumentException("Este pedido já foi cancelado.");
		}
		if (!RECEBIDO.equals(situacao))
		{
			throw new IllegalArgumentException(
					"O pedido já entrou em produção e não pode mais ser cancelado. Depois da entrega, você pode pedir a devolução.");
		}
		final String resposta = mengoEstampariaGateway.cancelar(order);
		final Map<String, Object> r = new LinkedHashMap<>();
		r.put("resultado", resposta);
		final String valor = dinheiro(valorEstudio(order));
		if (MengoEstampariaGateway.FALHA.equals(resposta))
		{
			r.put("mensagem", "Não deu para falar com a estamparia e nada foi cancelado ainda. Tente de novo em instantes; "
					+ "se continuar, fale com a loja.");
			return r;
		}
		if (MengoEstampariaGateway.RECUSADO.equals(resposta))
		{
			mudar(order, EM_PRODUCAO, "A estamparia já tinha começado a produção quando o cancelamento chegou.",
					"estamparia (simulada)");
			r.put("mensagem", "Não foi possível cancelar: o pedido entrou em produção às " + hora(mengoEstudioService.now())
					+ ". Se precisar, fale com a loja ou peça a devolução depois da entrega.");
			return r;
		}
		mudar(order, CANCELADO, "Cancelado a pedido do torcedor. Estorno de " + valor
				+ " na mesma forma de pagamento (simulado; o prazo depende do banco).", autor);
		if (soEstudio(order))
		{
			order.setStatus(OrderStatus.CANCELLED);
			modelService.save(order);
		}
		r.put("mensagem", "Pedido cancelado. O estorno de " + valor
				+ " vai para a mesma forma de pagamento; o prazo depende do banco.");
		return r;
	}

	@Override
	public String recusaPagamento(final AbstractOrderModel cart)
	{
		if (!mengoEstudioService.isActive() || !temEstudio(cart))
		{
			return null;
		}
		final ItemModel demo = mengoEstudioService.demoConfig();
		final Object v = demo == null ? null : attr(demo, "nextPayment");
		final String proximo = v instanceof String ? StringUtils.upperCase(StringUtils.trimToEmpty((String) v)) : "";
		if ("RECUSAR_UMA_VEZ".equals(proximo))
		{
			modelService.setAttributeValue(demo, "nextPayment", "APROVAR");
			modelService.save(demo);
		}
		else if (!"RECUSAR".equals(proximo))
		{
			return null;
		}
		LOG.info("Mengo Estúdio: pagamento do carrinho {} recusado pela simulação ({})", cart.getCode(), proximo);
		return RECUSA_PAGAMENTO;
	}

	/** Linha do tempo, na ordem em que aconteceu */
	protected List<Map<String, Object>> eventos(final OrderModel order)
	{
		final List<Map<String, Object>> list = new ArrayList<>();
		try
		{
			final FlexibleSearchQuery q = new FlexibleSearchQuery(
					"SELECT {pk} FROM {MengoPedidoEvento} WHERE {order} = ?o ORDER BY {date} ASC, {creationtime} ASC");
			q.addQueryParameter("o", order);
			for (final ItemModel e : flexibleSearchService.<ItemModel> search(q).getResult())
			{
				final Map<String, Object> m = new LinkedHashMap<>();
				final Object s = attr(e, "situacao");
				m.put("situacao", s instanceof HybrisEnumValue ? ((HybrisEnumValue) s).getCode() : null);
				final Object d = attr(e, "date");
				m.put("data", d instanceof Date ? Long.valueOf(((Date) d).getTime()) : null);
				m.put("nota", attr(e, "note"));
				list.add(m);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Mengo Estúdio: linha do tempo do pedido {} não lida: {}", order.getCode(), e.getMessage());
		}
		return list;
	}

	/** Valor das camisetas do Estúdio no pedido (o que volta no cancelamento) */
	protected double valorEstudio(final AbstractOrderModel order)
	{
		double v = 0;
		if (order.getEntries() != null)
		{
			for (final AbstractOrderEntryModel e : order.getEntries())
			{
				if (mengoEstudioCartService.isEstudioEntry(e) && e.getTotalPrice() != null)
				{
					v += e.getTotalPrice().doubleValue();
				}
			}
		}
		// pedido só do Estúdio: o total pago (inclui frete e descontos no pedido)
		return soEstudio(order) ? total(order) : v;
	}

	protected double total(final AbstractOrderModel o)
	{
		final double total = o.getTotalPrice() == null ? 0 : o.getTotalPrice().doubleValue();
		final double tax = Boolean.TRUE.equals(o.getNet()) && o.getTotalTax() != null ? o.getTotalTax().doubleValue() : 0;
		return total + tax;
	}

	protected String dinheiro(final double v)
	{
		return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(v).replace(' ', ' ');
	}

	protected String hora(final long ms)
	{
		final SimpleDateFormat f = new SimpleDateFormat("HH'h'mm");
		f.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		return f.format(new Date(ms));
	}

	protected Object attr(final Object item, final String qualifier)
	{
		try
		{
			return item == null ? null : modelService.getAttributeValue(item, qualifier);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setMengoEstudioCartService(final MengoEstudioCartService mengoEstudioCartService)
	{
		this.mengoEstudioCartService = mengoEstudioCartService;
	}

	public void setMengoEstampariaGateway(final MengoEstampariaGateway mengoEstampariaGateway)
	{
		this.mengoEstampariaGateway = mengoEstampariaGateway;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}
}
