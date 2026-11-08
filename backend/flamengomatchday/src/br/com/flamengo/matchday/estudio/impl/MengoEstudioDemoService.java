package br.com.flamengo.matchday.estudio.impl;

import br.com.flamengo.matchday.estudio.MengoEstudioOrderService;
import br.com.flamengo.matchday.estudio.MengoEstudioService;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Painel da demonstração do Mengo Estúdio (08/11, PR 5): o apresentador comanda as simulações (relógio, prazo de uma
 * estampa, próximo pagamento, resposta da estamparia ao cancelamento), avança os pedidos de teste dele como faria a
 * estamparia, simula o relançamento para os avisos e restaura tudo. Só para os usuários de teste
 * (flamengo.estudio.demo.usuarios, expressão regular sobre o e-mail; padrão torcedorN@example.com).
 * Tudo grava em Simulações da demo (MengoDemoConfig "default"), o mesmo que o Backoffice mostra.
 */
public class MengoEstudioDemoService
{
	private static final Logger LOG = LoggerFactory.getLogger(MengoEstudioDemoService.class);
	private static final long HOUR = 3_600_000L;

	private MengoEstudioService mengoEstudioService;
	private MengoEstudioOrderService mengoEstudioOrderService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;

	/** O usuário pode usar o painel (usuário de teste da demo) */
	public boolean permitido(final CustomerModel customer)
	{
		final String regex = configurationService.getConfiguration().getString("flamengo.estudio.demo.usuarios",
				"torcedor\\d+@example\\.com");
		return customer != null && mengoEstudioService.isActive() && StringUtils.isNotBlank(customer.getUid())
				&& customer.getUid().toLowerCase().matches(regex);
	}

	/** Estado do painel: relógio, simulações, estampas com o prazo, pedidos do apresentador e avisos */
	public Map<String, Object> estado(final CustomerModel customer)
	{
		final ItemModel demo = demo();
		final long agora = mengoEstudioService.now();
		final Map<String, Object> m = new LinkedHashMap<>();
		m.put("agora", Long.valueOf(agora));
		final Object off = modelService.getAttributeValue(demo, "clockOffsetMinutes");
		m.put("relogioMinutos", Long.valueOf(off instanceof Number ? ((Number) off).longValue() : 0L));
		m.put("pagamento", StringUtils.defaultIfBlank(str(demo, "nextPayment"), "APROVAR"));
		m.put("cancelamento", StringUtils.defaultIfBlank(str(demo, "cancelResponse"), "ACEITO"));
		final Map<String, String> forcados = forcados(demo);
		final List<Map<String, Object>> estampas = new ArrayList<>();
		for (final ItemModel e : mengoEstudioService.estampas())
		{
			final Map<String, Object> x = new LinkedHashMap<>();
			final String code = str(e, "code");
			x.put("code", code);
			x.put("nome", str(e, "name"));
			x.put("situacao", mengoEstudioService.situacao(e, agora));
			x.put("fim", Long.valueOf(mengoEstudioService.end(e)));
			x.put("simulado", Boolean.valueOf(forcados.containsKey(code)));
			estampas.add(x);
		}
		m.put("estampas", estampas);
		final List<Map<String, Object>> pedidos = new ArrayList<>();
		for (final OrderModel o : pedidosDoEstudio(customer))
		{
			final Map<String, Object> x = new LinkedHashMap<>();
			x.put("code", o.getCode());
			x.put("situacao", StringUtils.defaultString(mengoEstudioOrderService.situacao(o),
					MengoEstudioOrderService.RECEBIDO));
			final Map<String, Object> dev = mengoEstudioOrderService.devolucao(o);
			x.put("devolucao", dev == null ? null : dev.get("etapa"));
			x.put("proximo", mengoEstudioOrderService.proximoPasso(o));
			pedidos.add(x);
		}
		m.put("pedidos", pedidos);
		final List<ItemModel> avisos = search("SELECT {pk} FROM {MengoAvisoRetorno}", Collections.emptyMap());
		m.put("avisos", Integer.valueOf(avisos.size()));
		m.put("avisosPendentes", Long.valueOf(avisos.stream()
				.filter(a -> modelService.getAttributeValue(a, "notifiedDate") == null).count()));
		return m;
	}

	/**
	 * Ações: relogio {minutos} (soma; 0 = hora real), prazo {estampa, modo: cadastrado | 47h | encerrar}, pagamento
	 * {valor: APROVAR | RECUSAR_UMA_VEZ | RECUSAR}, cancelamento {valor: ACEITO | RECUSADO | FALHA}, avancar {pedido},
	 * avisos (simula o relançamento: marca os avisos pendentes como enviados), restaurar.
	 */
	public String executar(final CustomerModel customer, final String acao, final Map<String, Object> req)
	{
		final ItemModel demo = demo();
		switch (StringUtils.defaultString(acao))
		{
			case "relogio":
			{
				final long minutos = numero(req.get("minutos"));
				final Object off = modelService.getAttributeValue(demo, "clockOffsetMinutes");
				final long atual = off instanceof Number ? ((Number) off).longValue() : 0L;
				modelService.setAttributeValue(demo, "clockOffsetMinutes", minutos == 0 ? null : Long.valueOf(atual + minutos));
				modelService.save(demo);
				return minutos == 0 ? "Relógio na hora real." : "Relógio adiantado.";
			}
			case "prazo":
				return prazo(demo, texto(req.get("estampa")), texto(req.get("modo")));
			case "pagamento":
			{
				final String v = StringUtils.upperCase(texto(req.get("valor")));
				if (!"APROVAR".equals(v) && !"RECUSAR_UMA_VEZ".equals(v) && !"RECUSAR".equals(v))
				{
					throw new IllegalArgumentException("Opção de pagamento inválida.");
				}
				modelService.setAttributeValue(demo, "nextPayment", v);
				modelService.save(demo);
				return "Próximo pagamento: " + v + ".";
			}
			case "cancelamento":
			{
				final String v = StringUtils.upperCase(texto(req.get("valor")));
				if (!"ACEITO".equals(v) && !"RECUSADO".equals(v) && !"FALHA".equals(v))
				{
					throw new IllegalArgumentException("Resposta inválida.");
				}
				modelService.setAttributeValue(demo, "cancelResponse", "ACEITO".equals(v) ? null : v);
				modelService.save(demo);
				return "Resposta da estamparia: " + v + ".";
			}
			case "avancar":
			{
				final OrderModel o = mengoEstudioOrderService.pedido(customer, texto(req.get("pedido")));
				if (o == null || !mengoEstudioOrderService.temEstudio(o))
				{
					throw new IllegalArgumentException("Pedido do Mengo Estúdio não encontrado.");
				}
				final String nova = mengoEstudioOrderService.avancar(o, "estamparia (simulada)");
				if (nova == null)
				{
					throw new IllegalArgumentException("Este pedido não tem próximo passo.");
				}
				return "Pedido " + o.getCode() + " atualizado.";
			}
			case "avisos":
			{
				int n = 0;
				for (final ItemModel a : search("SELECT {pk} FROM {MengoAvisoRetorno} WHERE {notifiedDate} IS NULL",
						Collections.emptyMap()))
				{
					modelService.setAttributeValue(a, "notifiedDate", new Date(mengoEstudioService.now()));
					modelService.save(a);
					n++;
				}
				return n == 0 ? "Nenhum aviso pendente." : n + " aviso(s) marcados como enviados (relançamento simulado).";
			}
			case "restaurar":
			{
				modelService.setAttributeValue(demo, "clockOffsetMinutes", null);
				modelService.setAttributeValue(demo, "forcedEnds", null);
				modelService.setAttributeValue(demo, "nextPayment", "APROVAR");
				modelService.setAttributeValue(demo, "cancelResponse", null);
				modelService.save(demo);
				LOG.info("Mengo Estúdio: simulações da demo restauradas por {}", customer.getUid());
				return "Simulações restauradas: relógio na hora real, prazos cadastrados, pagamento aprovado e "
						+ "cancelamento aceito.";
			}
			default:
				throw new IllegalArgumentException("Ação desconhecida.");
		}
	}

	protected String prazo(final ItemModel demo, final String estampa, final String modo)
	{
		final ItemModel e = mengoEstudioService.estampa(estampa);
		if (e == null)
		{
			throw new IllegalArgumentException("Estampa não encontrada.");
		}
		final Map<String, String> forcados = forcados(demo);
		final long agora = mengoEstudioService.now();
		final String code = str(e, "code");
		switch (StringUtils.defaultString(modo))
		{
			case "cadastrado":
				forcados.remove(code);
				break;
			case "47h":
				forcados.put(code, String.valueOf(agora + 47 * HOUR));
				break;
			case "encerrar":
				forcados.put(code, String.valueOf(agora - 1000L));
				break;
			default:
				throw new IllegalArgumentException("Prazo inválido.");
		}
		final StringBuilder sb = new StringBuilder();
		forcados.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
		modelService.setAttributeValue(demo, "forcedEnds", sb.length() == 0 ? null : sb.toString().trim());
		modelService.save(demo);
		return "Prazo da estampa " + str(e, "name") + " atualizado.";
	}

	/** Pedidos com camiseta do Estúdio do apresentador, mais novos primeiro (até 10) */
	protected List<OrderModel> pedidosDoEstudio(final CustomerModel customer)
	{
		final List<OrderModel> list = new ArrayList<>();
		final FlexibleSearchQuery q = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Order} WHERE {user} = ?u AND {versionID} IS NULL AND {mengoSituacao} IS NOT NULL "
						+ "ORDER BY {date} DESC");
		q.addQueryParameter("u", customer);
		q.setCount(10);
		list.addAll(flexibleSearchService.<OrderModel> search(q).getResult());
		return list;
	}

	/** Linha "default" das Simulações da demo (criada se não existir) */
	protected ItemModel demo()
	{
		ItemModel demo = mengoEstudioService.demoConfig();
		if (demo == null)
		{
			demo = modelService.create("MengoDemoConfig");
			modelService.setAttributeValue(demo, "code", "default");
			modelService.save(demo);
		}
		return demo;
	}

	protected Map<String, String> forcados(final ItemModel demo)
	{
		final Map<String, String> map = new LinkedHashMap<>();
		final String text = str(demo, "forcedEnds");
		if (text != null)
		{
			for (final String line : text.split("\\r?\\n"))
			{
				final int eq = line.indexOf('=');
				if (eq > 0)
				{
					map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
				}
			}
		}
		return map;
	}

	protected List<ItemModel> search(final String query, final Map<String, Object> params)
	{
		final FlexibleSearchQuery q = new FlexibleSearchQuery(query);
		q.addQueryParameters(params);
		return flexibleSearchService.<ItemModel> search(q).getResult();
	}

	protected String str(final ItemModel item, final String qualifier)
	{
		final Object v = item == null ? null : modelService.getAttributeValue(item, qualifier);
		return v instanceof String && StringUtils.isNotBlank((String) v) ? (String) v : null;
	}

	protected static String texto(final Object v)
	{
		return v == null ? null : String.valueOf(v).trim();
	}

	protected static long numero(final Object v)
	{
		if (v instanceof Number)
		{
			return ((Number) v).longValue();
		}
		try
		{
			return v == null ? 0L : Long.parseLong(String.valueOf(v).trim());
		}
		catch (final NumberFormatException e)
		{
			return 0L;
		}
	}

	public void setMengoEstudioService(final MengoEstudioService mengoEstudioService)
	{
		this.mengoEstudioService = mengoEstudioService;
	}

	public void setMengoEstudioOrderService(final MengoEstudioOrderService mengoEstudioOrderService)
	{
		this.mengoEstudioOrderService = mengoEstudioOrderService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
